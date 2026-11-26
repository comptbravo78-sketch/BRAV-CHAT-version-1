package com.example.messaging

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ai.AiAutoReplyManager
import com.example.ai.AutoReplyLogEntry
import com.example.ai.SmsAutoReplyOutcome
import com.example.util.DefaultSmsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class BravSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) return

        val sender = msgs.firstOrNull()?.displayOriginatingAddress.orEmpty().ifBlank { "SMS" }
        val fullBody = msgs.joinToString("") { it.displayMessageBody.orEmpty() }
        val timestamp = msgs.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

        if (DefaultSmsHelper.isBravDefaultSms(context)) {
            runCatching {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, sender)
                    put(Telephony.Sms.BODY, fullBody)
                    put(Telephony.Sms.DATE, timestamp)
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.SEEN, 0)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                }
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            }
        }

        val hasNotifPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        if (hasNotifPerm) {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_SENDTO
                data = Uri.parse("smsto:${Uri.encode(sender)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pi = PendingIntent.getActivity(
                context,
                sender.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, context.getString(R.string.default_notification_channel_id))
                .setSmallIcon(R.drawable.ic_brav_logo)
                .setContentTitle(sender)
                .setContentText(fullBody)
                .setStyle(NotificationCompat.BigTextStyle().bigText(fullBody))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(sender.hashCode(), notif)
        }

        val appCtx = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outcome = withTimeoutOrNull(12_000L) {
                    AiAutoReplyManager.handleIncomingSmsAutoReply(appCtx, sender, fullBody)
                }
                if (outcome != null && hasNotifPerm) {
                    val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    when (outcome) {
                        is SmsAutoReplyOutcome.Sent -> {
                            val replyNotif = NotificationCompat.Builder(
                                appCtx,
                                appCtx.getString(R.string.default_notification_channel_id)
                            )
                                .setSmallIcon(R.drawable.ic_brav_logo)
                                .setContentTitle("Auto-réponse IA SMS envoyée à $sender")
                                .setContentText(outcome.entry.replyText)
                                .setStyle(NotificationCompat.BigTextStyle().bigText(outcome.entry.replyText))
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .setAutoCancel(true)
                                .build()
                            nm?.notify(("ai_sms_$sender").hashCode(), replyNotif)
                        }
                        is SmsAutoReplyOutcome.Queued -> {
                            val pending = outcome.pending
                            val approveIntent = Intent(appCtx, BravAutoReplyActionReceiver::class.java).apply {
                                action = BravAutoReplyActionReceiver.ACTION_APPROVE_SMS
                                putExtra(BravAutoReplyActionReceiver.EXTRA_PENDING_ID, pending.id)
                            }
                            val approvePi = PendingIntent.getBroadcast(
                                appCtx,
                                pending.id.hashCode(),
                                approveIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            val openAiIntent = Intent(appCtx, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra("extra_open_ai_validation", true)
                            }
                            val openPi = PendingIntent.getActivity(
                                appCtx,
                                ("open_${pending.id}").hashCode(),
                                openAiIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            val validateNotif = NotificationCompat.Builder(
                                appCtx,
                                appCtx.getString(R.string.default_notification_channel_id)
                            )
                                .setSmallIcon(R.drawable.ic_brav_logo)
                                .setContentTitle("Validation requise • SMS vers $sender")
                                .setContentText(pending.suggestedReply)
                                .setStyle(NotificationCompat.BigTextStyle().bigText(pending.suggestedReply))
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setAutoCancel(true)
                                .setContentIntent(openPi)
                                .addAction(0, "Envoyer", approvePi)
                                .build()
                            nm?.notify(pending.id.hashCode(), validateNotif)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class BravAutoReplyActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_APPROVE_SMS) return
        val pendingId = intent.getStringExtra(EXTRA_PENDING_ID).orEmpty()
        val pending = AiAutoReplyManager.removePendingReply(pendingId) ?: return
        if (pending.channel == "SMS" && DefaultSmsHelper.hasSmsSendPermission(context)) {
            if (AiAutoReplyManager.sendSmsDirect(context, pending.target, pending.suggestedReply)) {
                AiAutoReplyManager.recordLog(
                    AutoReplyLogEntry(
                        channel = "SMS",
                        target = pending.target,
                        incomingPreview = pending.incomingPreview,
                        replyText = pending.suggestedReply,
                        model = pending.model
                    )
                )
            }
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(pendingId.hashCode())
    }

    companion object {
        const val ACTION_APPROVE_SMS = "bravchat.com.ACTION_APPROVE_AUTO_SMS"
        const val EXTRA_PENDING_ID = "extra_pending_id"
    }
}

class BravMmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        // Required receiver component for ROLE_SMS eligibility; refreshes SMS/MMS inbox observers
        runCatching {
            context.contentResolver.notifyChange(Telephony.MmsSms.CONTENT_URI, null)
        }
    }
}

class BravHeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManagerAction.ACTION_RESPOND_VIA_MESSAGE) {
            val target = DefaultSmsHelper.extractSmsTargetFromIntent(intent)
            if (target != null && target.first.isNotBlank() && target.second.isNotBlank() &&
                DefaultSmsHelper.hasSmsSendPermission(this)
            ) {
                runCatching {
                    val smsMgr = getSystemService(SmsManager::class.java)
                    val parts = smsMgr.divideMessage(target.second)
                    if (parts.size > 1) {
                        smsMgr.sendMultipartTextMessage(target.first, null, parts, null, null)
                    } else {
                        smsMgr.sendTextMessage(target.first, null, target.second, null, null)
                    }
                }
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private object TelephonyManagerAction {
        const val ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE"
    }
}
