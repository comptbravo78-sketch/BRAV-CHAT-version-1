package com.example.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.local.AppPreferencesRepository
import com.example.local.AppPreferencesState
import com.example.model.AppNotification
import com.example.model.NotificationCategory
import com.example.model.VerificationStatus
import com.example.network.WorkerApiClient
import com.example.network.WorkerConstants
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Request
import java.util.Calendar

class BravFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        if (Firebase.auth.currentUser != null) {
            serviceScope.launch {
                runCatching { WorkerApiClient().registerDeviceToken(token) }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        serviceScope.launch {
            val prefsRepo = AppPreferencesRepository(applicationContext)
            val prefs = runCatching { prefsRepo.state.first() }.getOrNull()
            val type = message.data["type"].orEmpty()
            val categoryRaw = message.data["category"].orEmpty()
            val isCriticalSecurity = isCriticalSecurityType(type, categoryRaw)

            if (prefs?.notificationsEnabled == false && !isCriticalSecurity) return@launch

            val title = message.notification?.title ?: message.data["title"] ?: "BRAV Chat"
            val bodyRaw = message.notification?.body ?: message.data["body"] ?: "Nouvelle notification"

            if (!shouldNotifyForType(type, categoryRaw, prefs)) return@launch

            val inQuietHours = prefs?.isInQuietHours(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) == true
            val soundOn = (prefs?.notifySoundEnabled ?: true) && (!inQuietHours || type == "incoming_call" || isCriticalSecurity)
            val vibOn = (prefs?.notifyVibrationEnabled ?: true) && (!inQuietHours || type == "incoming_call" || isCriticalSecurity)
            val avatarUrl = message.data["callerAvatar"].orEmpty()
                .ifBlank { message.data["avatarUrl"].orEmpty() }

            if (type == "incoming_call") {
                val callId = message.data["callId"].orEmpty()
                if (callId.isNotBlank()) {
                    showIncomingCallNotification(
                        context = applicationContext,
                        callId = callId,
                        callerName = message.data["callerName"].orEmpty().ifBlank { title },
                        callerAvatarUrl = avatarUrl,
                        isVideo = message.data["callType"] == "video",
                        soundEnabled = soundOn,
                        vibrationEnabled = vibOn
                    )
                    return@launch
                }
            }

            val body = if (prefs?.hideNotificationContent == true && !isCriticalSecurity) {
                "Nouveau contenu chiffré — appuyez pour ouvrir"
            } else {
                bodyRaw
            }

            val largeIcon = if (avatarUrl.isNotBlank()) {
                fetchCircularAvatarBitmap(avatarUrl)
            } else null

            val conversationId = message.data["conversationId"]?.takeIf { it.isNotBlank() }
                ?: message.data["refId"]?.takeIf { it.isNotBlank() && message.data["refType"] == "conversation" }
            val callId = message.data["callId"]?.takeIf { it.isNotBlank() }
            val openNotificationsCenter = conversationId == null && callId == null

            showNotification(
                context = applicationContext,
                channelId = channelForType(type, categoryRaw),
                title = title,
                body = body,
                conversationId = conversationId,
                callId = callId,
                openNotificationsCenter = openNotificationsCenter,
                isCall = type == "incoming_call",
                soundEnabled = soundOn,
                vibrationEnabled = vibOn,
                largeIcon = largeIcon
            )
        }
    }

    companion object {
        private const val TAG = "BravFCM"
        const val CHANNEL_MESSAGES = "brav_messages_channel"
        const val CHANNEL_CALLS = "brav_calls_channel"
        const val CHANNEL_SOCIAL = "brav_social_channel"
        private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private fun isCriticalSecurityType(type: String, categoryRaw: String): Boolean {
            return categoryRaw.equals(NotificationCategory.ACCOUNT_SECURITY.raw, ignoreCase = true) ||
                type.startsWith("certification", ignoreCase = true) ||
                type.equals("account_security", ignoreCase = true) ||
                type.equals("verification_decision", ignoreCase = true)
        }

        private fun shouldNotifyForType(
            type: String,
            categoryRaw: String,
            prefs: AppPreferencesState?
        ): Boolean {
            if (prefs == null) return true
            if (isCriticalSecurityType(type, categoryRaw)) return true
            if (!prefs.notificationsEnabled) return false
            return when {
                type == "incoming_call" -> prefs.notifyCalls
                type == "missed_call" || categoryRaw == NotificationCategory.CALLS.raw -> prefs.notifyMissedCalls || prefs.notifyCalls
                type == "group_message" -> prefs.notifyGroups
                type == "contact_request" || type == "contact_accepted" || categoryRaw == NotificationCategory.CONTACTS.raw ->
                    prefs.notifyContacts
                type == "actus_post" || type == "status" -> prefs.notifyActusPosts && prefs.notifyStatuses
                type == "actus_like" || type == "actus_comment" -> prefs.notifyActusLikesComments && prefs.notifyStatuses
                categoryRaw == NotificationCategory.ACTUS.raw -> prefs.notifyStatuses
                type == "offer_published" || type == "job_alert" || categoryRaw == NotificationCategory.OFFERS_JOBS.raw ->
                    prefs.notifyOffersJobs
                else -> prefs.notifyMessages
            }
        }

        private fun channelForType(type: String, categoryRaw: String): String {
            return when {
                type == "incoming_call" || type == "missed_call" || categoryRaw == NotificationCategory.CALLS.raw -> CHANNEL_CALLS
                type in listOf("status", "actus_post", "actus_like", "actus_comment", "contact_request", "contact_accepted", "offer_published", "job_alert") ||
                    categoryRaw in listOf(
                        NotificationCategory.ACTUS.raw,
                        NotificationCategory.CONTACTS.raw,
                        NotificationCategory.OFFERS_JOBS.raw,
                        NotificationCategory.ACCOUNT_SECURITY.raw
                    ) -> CHANNEL_SOCIAL
                else -> CHANNEL_MESSAGES
            }
        }

        fun showCategorizedNotification(
            context: Context,
            notification: AppNotification,
            prefs: AppPreferencesState?
        ) {
            val isCritical = isCriticalSecurityType(notification.type, notification.category)
            if (!shouldNotifyForType(notification.type, notification.category, prefs)) return
            val inQuietHours = prefs?.isInQuietHours(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) == true
            val soundOn = (prefs?.notifySoundEnabled ?: true) && (!inQuietHours || isCritical)
            val vibOn = (prefs?.notifyVibrationEnabled ?: true) && (!inQuietHours || isCritical)
            val displayBody = if (prefs?.hideNotificationContent == true && !isCritical) {
                "Nouveau contenu chiffré — appuyez pour ouvrir"
            } else {
                notification.body
            }
            val convId = if (notification.refType == "conversation" && notification.refId.isNotBlank()) {
                notification.refId
            } else null
            val callId = if (notification.refType == "call" && notification.refId.isNotBlank()) {
                notification.refId
            } else null

            notificationScope.launch {
                val avatarBmp = if (notification.senderAvatar.isNotBlank()) {
                    fetchCircularAvatarBitmap(notification.senderAvatar)
                } else null
                showNotification(
                    context = context.applicationContext,
                    channelId = channelForType(notification.type, notification.category),
                    title = notification.title.ifBlank { "BRAV Chat" },
                    body = displayBody.ifBlank { "Nouvelle notification" },
                    conversationId = convId,
                    callId = callId,
                    openNotificationsCenter = convId == null && callId == null,
                    isCall = false,
                    soundEnabled = soundOn,
                    vibrationEnabled = vibOn,
                    largeIcon = avatarBmp
                )
            }
        }

        private fun showNotification(
            context: Context,
            channelId: String,
            title: String,
            body: String,
            conversationId: String?,
            callId: String?,
            openNotificationsCenter: Boolean = false,
            isCall: Boolean,
            soundEnabled: Boolean,
            vibrationEnabled: Boolean,
            largeIcon: Bitmap? = null
        ) {
            val appCtx = context.applicationContext
            ensureChannels(appCtx)
            val intent = Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (!conversationId.isNullOrBlank()) putExtra("extra_conversation_id", conversationId)
                if (!callId.isNullOrBlank()) putExtra("extra_call_id", callId)
                if (openNotificationsCenter) putExtra("extra_open_notifications", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                appCtx,
                (conversationId ?: callId ?: title).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(appCtx, channelId)
                .setSmallIcon(R.drawable.ic_brav_logo)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(if (isCall) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .apply {
                    if (largeIcon != null) setLargeIcon(largeIcon)
                    if (!soundEnabled && !vibrationEnabled) {
                        setSilent(true)
                    } else {
                        var defaults = 0
                        if (soundEnabled) defaults = defaults or NotificationCompat.DEFAULT_SOUND
                        if (vibrationEnabled) defaults = defaults or NotificationCompat.DEFAULT_VIBRATE
                        setDefaults(defaults)
                        if (!vibrationEnabled) setVibrate(longArrayOf(0L))
                    }
                }

            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build()) }
        }

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_MESSAGES,
                    context.getString(R.string.notification_channel_messages_name),
                    NotificationManager.IMPORTANCE_HIGH
                ),
                NotificationChannel(
                    CHANNEL_CALLS,
                    context.getString(R.string.notification_channel_calls_name),
                    NotificationManager.IMPORTANCE_HIGH
                ),
                NotificationChannel(
                    CHANNEL_SOCIAL,
                    context.getString(R.string.notification_channel_social_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
            nm.createNotificationChannels(channels)
        }

        fun showIncomingCallNotification(
            context: Context,
            callId: String,
            callerName: String,
            callerAvatarUrl: String = "",
            isVideo: Boolean,
            soundEnabled: Boolean = true,
            vibrationEnabled: Boolean = true
        ) {
            ensureChannels(context)
            val appCtx = context.applicationContext

            fun postWithAvatar(avatarBitmap: Bitmap?) {
                val baseFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val contentIntent = Intent(appCtx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("extra_call_id", callId)
                }
                val contentPi = PendingIntent.getActivity(
                    appCtx,
                    callId.hashCode(),
                    contentIntent,
                    baseFlags
                )
                val acceptIntent = Intent(appCtx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("extra_call_id", callId)
                    putExtra("extra_call_action", "accept")
                }
                val acceptPi = PendingIntent.getActivity(
                    appCtx,
                    ("accept_$callId").hashCode(),
                    acceptIntent,
                    baseFlags
                )
                val rejectIntent = Intent(appCtx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("extra_call_id", callId)
                    putExtra("extra_call_action", "reject")
                }
                val rejectPi = PendingIntent.getActivity(
                    appCtx,
                    ("reject_$callId").hashCode(),
                    rejectIntent,
                    baseFlags
                )

                val displayCaller = callerName.ifBlank { "Membre BRAV" }
                val title = if (isVideo) "Appel vidéo BRAV entrant" else "Appel audio BRAV entrant"
                val body = "$displayCaller vous appelle..."
                val builder = NotificationCompat.Builder(appCtx, CHANNEL_CALLS)
                    .setSmallIcon(R.drawable.ic_brav_logo)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(true)
                    .setAutoCancel(true)
                    .setContentIntent(contentPi)
                    .setFullScreenIntent(contentPi, true)
                    .apply {
                        if (avatarBitmap != null) setLargeIcon(avatarBitmap)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val callerPerson = Person.Builder()
                                .setName(displayCaller)
                                .setImportant(true)
                                .apply {
                                    if (avatarBitmap != null) {
                                        setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(avatarBitmap))
                                    }
                                }
                                .build()
                            setStyle(
                                NotificationCompat.CallStyle.forIncomingCall(callerPerson, rejectPi, acceptPi)
                                    .setIsVideo(isVideo)
                            )
                        } else {
                            addAction(android.R.drawable.ic_menu_close_clear_cancel, "Refuser", rejectPi)
                            addAction(android.R.drawable.ic_menu_call, "Décrocher", acceptPi)
                        }
                        if (!soundEnabled && !vibrationEnabled) {
                            setSilent(true)
                        } else if (!vibrationEnabled) {
                            setVibrate(longArrayOf(0L))
                        }
                    }

                val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                runCatching { nm.notify(callId.hashCode(), builder.build()) }
            }

            postWithAvatar(null)
            if (callerAvatarUrl.isNotBlank()) {
                notificationScope.launch {
                    val bmp = fetchCircularAvatarBitmap(callerAvatarUrl)
                    if (bmp != null) {
                        postWithAvatar(bmp)
                    }
                }
            }
        }

        fun showMissedCallNotification(
            context: Context,
            callId: String,
            callerName: String,
            callerAvatarUrl: String = "",
            isVideo: Boolean,
            soundEnabled: Boolean = true,
            vibrationEnabled: Boolean = true
        ) {
            ensureChannels(context)
            val appCtx = context.applicationContext
            notificationScope.launch {
                val bmp = if (callerAvatarUrl.isNotBlank()) fetchCircularAvatarBitmap(callerAvatarUrl) else null
                val intent = Intent(appCtx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    appCtx,
                    ("missed_$callId").hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val title = if (isVideo) "Appel vidéo manqué" else "Appel audio manqué"
                val body = callerName.ifBlank { "Membre BRAV" }
                val builder = NotificationCompat.Builder(appCtx, CHANNEL_CALLS)
                    .setSmallIcon(R.drawable.ic_brav_logo)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .apply {
                        if (bmp != null) setLargeIcon(bmp)
                        if (!soundEnabled && !vibrationEnabled) setSilent(true)
                    }
                val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                runCatching { nm.notify(("missed_$callId").hashCode(), builder.build()) }
            }
        }

        fun cancelIncomingCallNotification(context: Context, callId: String) {
            if (callId.isBlank()) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { nm.cancel(callId.hashCode()) }
        }

        fun showCertificationDecisionNotification(
            context: Context,
            status: VerificationStatus,
            caseNumber: String = "",
            note: String = ""
        ) {
            ensureChannels(context)
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("extra_open_notifications", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                appCtx,
                910,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val dossierPrefix = if (caseNumber.isNotBlank()) "Dossier $caseNumber · " else ""
            val (title, defaultBody) = when (status) {
                VerificationStatus.VERIFIED ->
                    "CERTIFICATION — Badge actif" to "Votre compte est officiellement certifié. Votre badge bleu est désormais visible."
                VerificationStatus.RETAKE_REQUIRED ->
                    "CERTIFICATION — Selfie à reprendre" to "${dossierPrefix}Veuillez reprendre votre selfie en direct dans un endroit bien éclairé."
                VerificationStatus.AWAITING_PAYMENT ->
                    "CERTIFICATION — Paiement requis" to "${dossierPrefix}Finalisez votre certification en réglant les frais de 3 600 F CFA (5,99 USD)."
                VerificationStatus.IN_REVIEW ->
                    "CERTIFICATION — En cours d'examen" to "${dossierPrefix}Un administrateur examine votre dossier."
                VerificationStatus.INFO_REQUIRED ->
                    "CERTIFICATION — Compléments requis" to "${dossierPrefix}Des informations complémentaires sont demandées pour finaliser votre dossier."
                VerificationStatus.REJECTED ->
                    "CERTIFICATION — Demande refusée" to "${dossierPrefix}Votre demande n'a pas été retenue pour le moment."
                VerificationStatus.SUSPENDED ->
                    "CERTIFICATION — Certification suspendue" to "${dossierPrefix}Votre badge bleu est temporairement suspendu."
                VerificationStatus.REVOKED ->
                    "CERTIFICATION — Certification révoquée" to "${dossierPrefix}Votre certification a été révoquée par l'administration."
                VerificationStatus.EXPIRED ->
                    "CERTIFICATION — Renouvellement requis" to "${dossierPrefix}Votre certification a expiré. Soumettez un renouvellement."
                else ->
                    "CERTIFICATION — Mise à jour" to "${dossierPrefix}Statut : ${status.labelFr}"
            }
            val body = note.ifBlank { defaultBody }
            val builder = NotificationCompat.Builder(appCtx, CHANNEL_SOCIAL)
                .setSmallIcon(R.drawable.ic_brav_logo)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { nm.notify(910, builder.build()) }
        }

        private fun fetchCircularAvatarBitmap(rawUrl: String): Bitmap? {
            return runCatching {
                val resolvedUrl = if (rawUrl.startsWith("/")) "${WorkerConstants.BASE_URL}$rawUrl" else rawUrl
                val client = WorkerApiClient().mediaAuthorizedClient
                val req = Request.Builder().url(resolvedUrl).get().build()
                val bytes = client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return null
                    res.body?.bytes()
                } ?: return null
                if (bytes.isEmpty()) return null

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) {
                    sample *= 2
                }
                val decoded = BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                ) ?: return null

                val targetSize = 128
                val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val rect = Rect(0, 0, decoded.width, decoded.height)
                val side = minOf(decoded.width, decoded.height)
                val srcLeft = (decoded.width - side) / 2
                val srcTop = (decoded.height - side) / 2
                val cropSrc = Rect(srcLeft, srcTop, srcLeft + side, srcTop + side)
                val dstRect = RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())
                canvas.drawOval(dstRect, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(decoded, cropSrc, dstRect, paint)
                if (decoded != output) decoded.recycle()
                output
            }.getOrNull()
        }
    }
}
