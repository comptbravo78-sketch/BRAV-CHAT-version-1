package com.example.telecom

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Android CallScreeningService that identifies both incoming AND outgoing phone numbers
 * reliably with saved contacts and exact fixed city/district localization.
 */
class BravCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val rawNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
        val telecomDisplayName = callDetails.callerDisplayName.orEmpty().ifBlank {
            callDetails.contactDisplayName.orEmpty()
        }
        val direction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (callDetails.callDirection) {
                Call.Details.DIRECTION_INCOMING -> CallDirection.INCOMING
                Call.Details.DIRECTION_OUTGOING -> CallDirection.OUTGOING
                else -> CallDirection.UNKNOWN
            }
        } else {
            CallDirection.UNKNOWN
        }

        if (rawNumber.isNotBlank()) {
            val identity = CallerIdResolver.resolveWithSystemContacts(
                context = this,
                rawNumber = rawNumber,
                telecomDisplayName = telecomDisplayName,
                direction = direction
            )
            CarrierCallController.onCallScreened(identity)
            postCallerIdNotification(identity)
        }

        respondToCall(callDetails, CallResponse.Builder().build())
    }

    private fun postCallerIdNotification(identity: CallerIdentity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Identification des appels",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Affiche la ville fixe, la zone et l'opérateur des appels entrants et sortants"
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }

        val cityPart = identity.cityBadge.ifBlank { "Localisation internationale" }
        val shownName = if (identity.isUnknownCaller) "Numéro inconnu" else identity.displayName
        val title = "${identity.direction.labelFr} · $shownName"
        val subtitle = listOfNotNull(
            identity.formattedNumber.takeIf { it.isNotBlank() && it != shownName },
            identity.sourceLabel.takeIf { it.isNotBlank() },
            identity.fullLocationSummary.ifBlank { cityPart }
        ).distinct().joinToString(" • ")

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            902,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setTimeoutAfter(25_000L)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val CHANNEL_ID = "brav_caller_id_channel"
        private const val NOTIFICATION_ID = 4402
    }
}
