package com.example.telecom

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import java.util.concurrent.ConcurrentHashMap

class BravInCallService : InCallService() {

    private data class TrackedIncomingCall(
        val rawNumber: String,
        val displayName: String,
        var reachedActive: Boolean = false
    )

    private val trackedIncoming = ConcurrentHashMap<Call, TrackedIncomingCall>()
    private val trackedCallbacks = ConcurrentHashMap<Call, Call.Callback>()

    override fun onCreate() {
        super.onCreate()
        CarrierCallController.attachService(this)
        ensureChannel()
    }

    override fun onDestroy() {
        trackedCallbacks.forEach { (call, cb) ->
            runCatching { call.unregisterCallback(cb) }
        }
        trackedCallbacks.clear()
        trackedIncoming.clear()
        CarrierCallController.detachService(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CarrierCallController.onCallAdded(call)
        val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            @Suppress("DEPRECATION")
            call.state
        }
        if (state == Call.STATE_RINGING) {
            val number = call.details?.handle?.schemeSpecificPart.orEmpty()
            val rawName = call.details?.callerDisplayName.orEmpty().ifBlank {
                call.details?.contactDisplayName.orEmpty()
            }
            val identity = CallerIdResolver.resolveWithSystemContacts(
                context = this,
                rawNumber = number,
                telecomDisplayName = rawName,
                direction = CallDirection.INCOMING
            )
            trackedIncoming[call] = TrackedIncomingCall(
                rawNumber = number,
                displayName = identity.displayName.ifBlank { number.ifBlank { "Numéro inconnu" } },
                reachedActive = false
            )
            val stateCb = object : Call.Callback() {
                override fun onStateChanged(c: Call, newState: Int) {
                    if (newState == Call.STATE_ACTIVE || newState == Call.STATE_HOLDING) {
                        trackedIncoming[c]?.reachedActive = true
                    }
                }
            }
            trackedCallbacks[call] = stateCb
            call.registerCallback(stateCb)
            postCallNotification(call, CallDirection.INCOMING)
            launchFullScreenCallUi()
        } else if (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
            postCallNotification(call, CallDirection.OUTGOING)
            launchFullScreenCallUi()
        }
    }

    private fun launchFullScreenCallUi() {
        runCatching {
            val intent = Intent(this, CallActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            }
            startActivity(intent)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        trackedCallbacks.remove(call)?.let { cb ->
            runCatching { call.unregisterCallback(cb) }
        }
        val incomingMeta = trackedIncoming.remove(call)
        val disconnectCause = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            call.details?.disconnectCause?.code
        } else {
            null
        }
        if (incomingMeta != null && !incomingMeta.reachedActive &&
            disconnectCause != android.telecom.DisconnectCause.REJECTED &&
            disconnectCause != android.telecom.DisconnectCause.CANCELED
        ) {
            postMissedCarrierCallNotification(incomingMeta.rawNumber, incomingMeta.displayName)
        }
        CarrierCallController.onCallRemoved(call)
        val nm = getSystemService(NotificationManager::class.java)
        nm?.cancel(NOTIFICATION_ID)
    }

    @Deprecated("Deprecated in Android API 34")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        @Suppress("DEPRECATION")
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            val btName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val canReadBt = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
                if (canReadBt) {
                    runCatching { audioState.activeBluetoothDevice?.name.orEmpty() }.getOrDefault("")
                } else {
                    ""
                }
            } else {
                ""
            }
            CarrierCallController.onAudioStateChanged(
                isMuted = audioState.isMuted,
                route = audioState.route,
                supportedRouteMask = audioState.supportedRouteMask,
                bluetoothName = btName
            )
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Appels téléphoniques",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Appels GSM entrants et sortants lorsque BRAV Chat est compositeur par défaut"
                    val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    setSound(
                        ringtoneUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }
            if (nm.getNotificationChannel(MISSED_CHANNEL_ID) == null) {
                val missedChannel = NotificationChannel(
                    MISSED_CHANNEL_ID,
                    "Appels manqués",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications d'appels GSM manqués avec rappel direct"
                }
                nm.createNotificationChannel(missedChannel)
            }
        }
    }

    private fun postMissedCarrierCallNotification(rawNumber: String, displayName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            this,
            903,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, MISSED_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Appel manqué")
            .setContentText(displayName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(openPi)

        if (rawNumber.isNotBlank()) {
            val callbackIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$rawNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val callbackPi = PendingIntent.getActivity(
                this,
                rawNumber.hashCode(),
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_call,
                "Rappeler",
                callbackPi
            )
        }

        runCatching {
            nm.notify(MISSED_NOTIFICATION_BASE_ID + (rawNumber.hashCode() and 0xFF), builder.build())
        }
    }

    private fun postCallNotification(call: Call, direction: CallDirection) {
        val number = call.details?.handle?.schemeSpecificPart.orEmpty().ifBlank { "Numéro inconnu" }
        val rawName = call.details?.callerDisplayName.orEmpty().ifBlank {
            call.details?.contactDisplayName.orEmpty()
        }
        val identity = CallerIdResolver.resolveWithSystemContacts(
            context = this,
            rawNumber = number,
            telecomDisplayName = rawName,
            direction = direction
        )

        val locationText = identity.fullLocationSummary.ifBlank { identity.cityBadge }
        val title = if (identity.cityBadge.isNotBlank()) {
            "${identity.displayName} • ${identity.cityBadge}"
        } else {
            identity.displayName
        }
        val contentText = buildString {
            append(direction.labelFr)
            if (identity.formattedNumber.isNotBlank() && identity.formattedNumber != identity.displayName) {
                append(" • ").append(identity.formattedNumber)
            }
            if (locationText.isNotBlank()) {
                append(" • ").append(locationText)
            }
            if (identity.safetyCategory.isWarning) {
                append(" • ").append(identity.safetyCategory.labelFr)
            }
        }

        val launchIntent = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            901,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)

        if (direction != CallDirection.INCOMING) {
            builder.setSilent(true)
        }

        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, builder.build())
        }
    }

    companion object {
        private const val CHANNEL_ID = "brav_carrier_calls"
        private const val MISSED_CHANNEL_ID = "brav_missed_carrier_calls"
        private const val NOTIFICATION_ID = 4401
        private const val MISSED_NOTIFICATION_BASE_ID = 4500
    }
}
