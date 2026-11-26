package com.example.repository

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import com.example.model.Contact
import com.example.util.DefaultSmsHelper
import com.example.util.PhoneLocalityResolver
import com.example.util.PhoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

data class SmsMessageItem(
    val id: String,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestampMs: Long,
    val isIncoming: Boolean,
    val isRead: Boolean,
    val isMms: Boolean = false
)

data class SmsThreadSummary(
    val threadKey: String,
    val threadId: Long,
    val address: String,
    val normalizedAddress: String,
    val displayName: String,
    val photoUri: String,
    val localityHint: String,
    val lastBody: String,
    val timestampMs: Long,
    val unreadCount: Int,
    val messages: List<SmsMessageItem>
)

class SmsRepository(private val appContext: Context) {

    fun observeSmsChanges(): Flow<Long> = callbackFlow {
        if (!DefaultSmsHelper.hasSmsReadPermission(appContext)) {
            trySend(0L)
            close()
            return@callbackFlow
        }
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(System.currentTimeMillis())
            }
        }
        runCatching {
            appContext.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        }
        trySend(System.currentTimeMillis())
        awaitClose {
            runCatching { appContext.contentResolver.unregisterContentObserver(observer) }
        }
    }

    suspend fun loadSmsThreads(phoneContacts: List<Contact>): List<SmsThreadSummary> = withContext(Dispatchers.IO) {
        if (!DefaultSmsHelper.hasSmsReadPermission(appContext)) return@withContext emptyList()

        val contactByNormalized = phoneContacts.associateBy { it.normalizedPhoneNumber }
        val items = mutableListOf<SmsMessageItem>()

        runCatching {
            appContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val threadIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
                val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)

                var count = 0
                while (cursor.moveToNext() && count < 600) {
                    count++
                    val id = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
                    val threadId = if (threadIdx >= 0) cursor.getLong(threadIdx) else 0L
                    val address = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty() else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    val read = if (readIdx >= 0) cursor.getInt(readIdx) == 1 else true
                    if (address.isNotBlank()) {
                        items.add(
                            SmsMessageItem(
                                id = id,
                                threadId = threadId,
                                address = address,
                                body = body,
                                timestampMs = date,
                                isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                                isRead = read
                            )
                        )
                    }
                }
            }
        }

        val grouped = items.groupBy { msg ->
            PhoneUtils.normalize(msg.address).ifBlank { msg.address }
        }

        grouped.entries.map { (normKey, threadMsgs) ->
            val sortedMsgs = threadMsgs.sortedBy { it.timestampMs }
            val latest = sortedMsgs.last()
            val matched = contactByNormalized[normKey]
                ?: phoneContacts.firstOrNull { PhoneUtils.matches(it.rawPhoneNumber, latest.address) }
            val lookupName = matched?.preferredDisplayName
                ?: resolveContactNameViaLookup(latest.address)
                ?: latest.address
            val locality = PhoneLocalityResolver.resolve(normKey).compactBadge

            SmsThreadSummary(
                threadKey = normKey,
                threadId = latest.threadId,
                address = latest.address,
                normalizedAddress = normKey,
                displayName = lookupName,
                photoUri = matched?.effectiveAvatarUrl.orEmpty(),
                localityHint = locality,
                lastBody = latest.body,
                timestampMs = latest.timestampMs,
                unreadCount = threadMsgs.count { it.isIncoming && !it.isRead },
                messages = sortedMsgs
            )
        }.sortedByDescending { it.timestampMs }
    }

    private fun resolveContactNameViaLookup(phoneNumber: String): String? {
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            appContext.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
                }
        }.getOrNull()
    }

    suspend fun sendSms(destinationAddress: String, messageBody: String): Result<Unit> = withContext(Dispatchers.IO) {
        val target = destinationAddress.trim()
        val body = messageBody.trim()
        if (target.isBlank() || body.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Numéro ou message vide"))
        }
        if (!DefaultSmsHelper.hasSmsSendPermission(appContext)) {
            return@withContext Result.failure(SecurityException("Permission SEND_SMS requise"))
        }
        return@withContext runCatching {
            val smsManager = appContext.getSystemService(SmsManager::class.java)
            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(target, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(target, null, body, null, null)
            }
            if (DefaultSmsHelper.isBravDefaultSms(appContext)) {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, target)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                }
                appContext.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            }
            appContext.contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)
        }
    }

    suspend fun markThreadAsRead(threadId: Long) = withContext(Dispatchers.IO) {
        if (threadId <= 0L || !DefaultSmsHelper.isBravDefaultSms(appContext)) return@withContext
        runCatching {
            val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
            appContext.contentResolver.update(
                Telephony.Sms.Inbox.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
            appContext.contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)
        }
    }

    suspend fun deleteThread(threadId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        if (!DefaultSmsHelper.isBravDefaultSms(appContext)) {
            return@withContext Result.failure(IllegalStateException("Définissez BRAV Chat comme application SMS par défaut pour supprimer un fil SMS."))
        }
        runCatching {
            appContext.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )
            appContext.contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)
        }.map { }
    }

    suspend fun deleteSmsMessage(messageId: String, isMms: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = messageId.trim()
        if (cleanId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Identifiant du message introuvable"))
        }
        if (!DefaultSmsHelper.isBravDefaultSms(appContext)) {
            return@withContext Result.failure(IllegalStateException("Définissez BRAV Chat comme application SMS par défaut pour supprimer ce message."))
        }
        runCatching {
            val uri = if (isMms) Telephony.Mms.CONTENT_URI else Telephony.Sms.CONTENT_URI
            val idCol = if (isMms) Telephony.Mms._ID else Telephony.Sms._ID
            val deleted = appContext.contentResolver.delete(uri, "$idCol = ?", arrayOf(cleanId))
            if (deleted <= 0) {
                throw IllegalStateException("Message introuvable dans la base SMS locale")
            }
            appContext.contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)
        }.map { }
    }
}
