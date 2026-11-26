package com.example.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.util.PhoneLocalityResolver
import com.example.util.PhoneUtils

data class DeviceCallLogEntry(
    val id: String,
    val number: String,
    val formattedNumber: String,
    val displayName: String,
    val direction: CallDirection,
    val isMissed: Boolean,
    val timestampMs: Long,
    val durationSeconds: Long,
    val localityBadge: String,
    val isKnownContact: Boolean,
    val cachedPhotoUri: String = ""
)

object DeviceCallLogRepository {

    fun hasCallLogPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun loadCallLogs(
        context: Context,
        defaultCountryIso: String = "CI",
        limit: Int = 120
    ): List<DeviceCallLogEntry> {
        if (!hasCallLogPermission(context)) return emptyList()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_PHOTO_URI
        )
        val result = mutableListOf<DeviceCallLogEntry>()
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val photoIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)

                while (cursor.moveToNext() && result.size < limit) {
                    val rowId = if (idIdx >= 0) cursor.getLong(idIdx).toString() else "${result.size}"
                    val rawNumber = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().trim() else ""
                    val cachedName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                    val callType = if (typeIdx >= 0) cursor.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                    val dateMs = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val durationSec = if (durIdx >= 0) cursor.getLong(durIdx).coerceAtLeast(0L) else 0L
                    val photoUri = if (photoIdx >= 0) cursor.getString(photoIdx).orEmpty() else ""

                    val (direction, missed) = mapCallType(callType)
                    val formatted = if (rawNumber.isNotBlank()) {
                        PhoneUtils.formatDisplay(rawNumber, defaultCountryIso)
                    } else {
                        "Numéro masqué"
                    }
                    val locality = if (rawNumber.isNotBlank()) {
                        PhoneLocalityResolver.resolve(rawNumber, defaultCountryIso).compactBadge
                    } else {
                        ""
                    }
                    val title = cachedName.ifBlank { formatted }

                    result.add(
                        DeviceCallLogEntry(
                            id = "sys_$rowId",
                            number = rawNumber,
                            formattedNumber = formatted,
                            displayName = title,
                            direction = direction,
                            isMissed = missed,
                            timestampMs = dateMs,
                            durationSeconds = durationSec,
                            localityBadge = locality,
                            isKnownContact = cachedName.isNotBlank(),
                            cachedPhotoUri = photoUri
                        )
                    )
                }
            }
        }
        return result
    }

    internal fun mapCallType(type: Int): Pair<CallDirection, Boolean> {
        return when (type) {
            CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING to false
            CallLog.Calls.MISSED_TYPE,
            CallLog.Calls.REJECTED_TYPE,
            CallLog.Calls.BLOCKED_TYPE -> CallDirection.INCOMING to true
            else -> CallDirection.INCOMING to false
        }
    }

    fun formatDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hours = safe / 3600L
        val mins = (safe % 3600L) / 60L
        val secs = safe % 60L
        return when {
            hours > 0L -> "${hours}h ${mins}m"
            mins > 0L -> "${mins}m ${secs}s"
            else -> "${secs}s"
        }
    }
}
