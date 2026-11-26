package com.example.repository

import android.content.Context
import com.example.R
import com.example.model.AppNotification
import com.example.model.NotificationCategory
import com.example.model.NotificationCounters
import com.example.network.WorkerApiClient
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class NotificationRepository(
    private val db: FirebaseFirestore,
    private val workerApiClient: WorkerApiClient? = null
) {
    constructor(context: Context, workerApiClient: WorkerApiClient? = null) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        workerApiClient = workerApiClient
    )

    fun observeNotifications(uid: String): Flow<List<AppNotification>> {
        return db.collection("users").document(uid)
            .collection("notifications")
            .orderBy("createdAtMs", Query.Direction.DESCENDING)
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.copy(id = doc.id)
                }
            }
    }

    suspend fun fetchFromWorker(category: String = "", limit: Int = 60): List<AppNotification> {
        return runCatching {
            workerApiClient?.fetchNotifications(category = category, limit = limit)?.getOrNull().orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun emitNotification(
        targetUid: String,
        category: NotificationCategory,
        type: String,
        title: String,
        body: String,
        refId: String = "",
        refType: String = "",
        subRefId: String = "",
        dedupKey: String = "",
        senderUid: String = "",
        senderName: String = "",
        senderAvatar: String = "",
        priority: String = "normal"
    ): Result<String> {
        if (targetUid.isBlank() || title.isBlank() || body.isBlank()) {
            return Result.success("")
        }
        val authUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull().orEmpty()
        val effectiveSenderUid = if (targetUid != authUid && authUid.isNotBlank()) {
            authUid
        } else {
            senderUid.ifBlank { authUid }.take(128)
        }
        val cleanType = type.trim().take(64).let { if (it.length < 2) "event" else it }
        val cleanTitle = title.trim().take(160)
        val cleanBody = body.trim().take(500)
        val nowMs = System.currentTimeMillis()
        val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(20)}"

        val payload = hashMapOf<String, Any>(
            "userId" to targetUid.take(128),
            "senderUid" to effectiveSenderUid.take(128),
            "senderName" to senderName.trim().take(120),
            "senderAvatar" to senderAvatar.trim().take(1000),
            "category" to category.raw,
            "type" to cleanType,
            "title" to cleanTitle,
            "body" to cleanBody,
            "refId" to refId.trim().take(160),
            "refType" to refType.trim().take(64),
            "subRefId" to subRefId.trim().take(160),
            "dedupKey" to dedupKey.trim().take(180),
            "priority" to if (priority == "high" || priority == "critical") priority else "normal",
            "isRead" to false,
            "readAtMs" to 0L,
            "createdAtMs" to nowMs,
            "createdAt" to FieldValue.serverTimestamp()
        )

        var firestoreOk = false
        runCatching {
            db.collection("users").document(targetUid)
                .collection("notifications").document(notifId)
                .set(payload)
                .await()
            firestoreOk = true
        }

        var workerId = ""
        runCatching {
            val workerRes = workerApiClient?.emitNotification(
                targetUid = targetUid,
                category = category.raw,
                type = cleanType,
                title = cleanTitle,
                body = cleanBody,
                refId = refId,
                refType = refType,
                subRefId = subRefId,
                dedupKey = dedupKey,
                senderUid = effectiveSenderUid,
                senderName = senderName,
                senderAvatar = senderAvatar,
                priority = priority
            )?.getOrNull()
            if (workerRes != null) workerId = workerRes.id
        }

        return Result.success(if (firestoreOk) notifId else workerId)
    }

    suspend fun markAsRead(uid: String, notifId: String, refId: String = "") {
        val nowMs = System.currentTimeMillis()
        if (notifId.isNotBlank()) {
            runCatching {
                db.collection("users").document(uid)
                    .collection("notifications").document(notifId)
                    .update(mapOf("isRead" to true, "readAtMs" to nowMs))
                    .await()
            }
        }
        runCatching {
            workerApiClient?.markNotificationsRead(
                ids = if (notifId.isNotBlank()) listOf(notifId) else emptyList(),
                refId = refId
            )
        }
    }

    suspend fun markByRefIdRead(uid: String, refId: String) {
        if (refId.isBlank() || uid.isBlank()) return
        val nowMs = System.currentTimeMillis()
        runCatching {
            val snap = db.collection("users").document(uid)
                .collection("notifications")
                .whereEqualTo("refId", refId)
                .whereEqualTo("isRead", false)
                .get()
                .await()
            if (!snap.isEmpty) {
                snap.documents.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { doc ->
                        batch.update(doc.reference, mapOf("isRead" to true, "readAtMs" to nowMs))
                    }
                    batch.commit().await()
                }
            }
        }
        runCatching {
            workerApiClient?.markNotificationsRead(refId = refId)
        }
    }

    suspend fun markAllAsRead(uid: String, category: NotificationCategory = NotificationCategory.ALL) {
        val nowMs = System.currentTimeMillis()
        runCatching {
            val base = db.collection("users").document(uid).collection("notifications")
            val query = if (category == NotificationCategory.ALL) {
                base.whereEqualTo("isRead", false)
            } else {
                base.whereEqualTo("category", category.raw).whereEqualTo("isRead", false)
            }
            val snap = query.get().await()
            if (!snap.isEmpty) {
                snap.documents.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { doc ->
                        batch.update(doc.reference, mapOf("isRead" to true, "readAtMs" to nowMs))
                    }
                    batch.commit().await()
                }
            }
        }
        runCatching {
            val catArg = if (category == NotificationCategory.ALL) "" else category.raw
            workerApiClient?.markAllNotificationsRead(catArg)
        }
    }

    suspend fun deleteNotification(uid: String, notifId: String) {
        if (notifId.isBlank()) return
        runCatching {
            db.collection("users").document(uid)
                .collection("notifications").document(notifId)
                .delete()
                .await()
        }
        runCatching {
            workerApiClient?.deleteNotification(notifId)
        }
    }

    companion object {
        fun mergeNotifications(
            firestoreList: List<AppNotification>,
            workerList: List<AppNotification>,
            locallyReadIds: Set<String> = emptySet(),
            locallyDeletedIds: Set<String> = emptySet()
        ): List<AppNotification> {
            val byKey = linkedMapOf<String, AppNotification>()
            (firestoreList + workerList).forEach { item ->
                if (item.id in locallyDeletedIds) return@forEach
                val dedup = item.dedupKey.trim().takeIf { it.isNotBlank() } ?: item.id
                if (dedup.isBlank()) return@forEach
                val isMarkedLocally = item.id in locallyReadIds || dedup in locallyReadIds
                val normalized = if (isMarkedLocally && !item.isRead) {
                    item.copy(isRead = true, readAtMs = maxOf(item.readAtMs, System.currentTimeMillis()))
                } else {
                    item
                }
                val existing = byKey[dedup]
                if (existing == null) {
                    byKey[dedup] = normalized
                } else {
                    val combinedRead = existing.isRead || normalized.isRead
                    val combinedReadAt = maxOf(existing.readAtMs, normalized.readAtMs)
                    val preferred = if (normalized.effectiveTimestampMs >= existing.effectiveTimestampMs) normalized else existing
                    byKey[dedup] = preferred.copy(isRead = combinedRead, readAtMs = combinedReadAt)
                }
            }
            return byKey.values.sortedByDescending { it.effectiveTimestampMs }
        }

        fun computeCounters(items: List<AppNotification>): NotificationCounters {
            var messages = 0
            var contacts = 0
            var actus = 0
            var calls = 0
            var offersJobs = 0
            var accountSecurity = 0
            items.forEach { n ->
                if (!n.isRead) {
                    when (n.categoryEnum) {
                        NotificationCategory.MESSAGES -> messages++
                        NotificationCategory.CONTACTS -> contacts++
                        NotificationCategory.ACTUS -> actus++
                        NotificationCategory.CALLS -> calls++
                        NotificationCategory.OFFERS_JOBS -> offersJobs++
                        NotificationCategory.ACCOUNT_SECURITY -> accountSecurity++
                        NotificationCategory.ALL -> Unit
                    }
                }
            }
            return NotificationCounters(
                totalUnread = messages + contacts + actus + calls + offersJobs + accountSecurity,
                messagesUnread = messages,
                contactsUnread = contacts,
                actusUnread = actus,
                callsUnread = calls,
                offersJobsUnread = offersJobs,
                accountSecurityUnread = accountSecurity
            )
        }
    }
}
