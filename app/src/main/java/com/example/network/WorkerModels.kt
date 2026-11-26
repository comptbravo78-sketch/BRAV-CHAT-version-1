package com.example.network

import com.example.model.AppNotification

data class WorkerFeatures(
    val auth: Boolean = true,
    val realtimeHub: Boolean = true,
    val callSignaling: Boolean = true,
    val turn: String = "false",
    val sfu: Boolean = false,
    val media: Boolean = true,
    val push: Boolean = false,
    val presenceSync: Boolean = false,
    val missedCalls: Boolean = false,
    val search: Boolean = true,
    val ai: Boolean = true
)

data class WorkerAiModel(
    val id: String,
    val label: String,
    val category: String = "Général"
)

data class WorkerAiReply(
    val model: String,
    val reply: String,
    val cached: Boolean = false
)

data class WorkerDocumentResponse(
    val model: String,
    val title: String,
    val subtitle: String,
    val templateType: String,
    val bodyMarkdown: String,
    val keyFormulasLatex: List<String> = emptyList(),
    val tableRows: List<List<String>> = emptyList()
)

data class AutoReplyRuleDto(
    val contactKey: String,
    val contactLabel: String,
    val mode: String, // "AUTO", "VALIDATE", "NEVER"
    val toneOverride: String = ""
)

data class LoginSessionEntry(
    val id: String,
    val deviceId: String,
    val deviceName: String,
    val countryCode: String = "",
    val city: String = "",
    val createdAtMs: Long = 0L
)

data class WorkerLimits(
    val signalBytes: Int = 32 * 1024,
    val uploadMB: Int = 25,
    val presenceUidsPerQuery: Int = 100,
    val ringTimeoutMs: Long = 45_000L
)

data class WorkerConfig(
    val version: String = "3.0.0",
    val baseUrl: String = WorkerConstants.BASE_URL,
    val realtimeUrl: String = WorkerConstants.WS_BASE_URL + "/v1/realtime",
    val callSignalUrlTemplate: String = WorkerConstants.WS_BASE_URL + "/v1/calls/{callId}/signal",
    val websocketSubprotocol: String = "brav.v1",
    val heartbeatEveryMs: Long = 25_000L,
    val sfuAppId: String = WorkerConstants.SFU_APP_ID,
    val limits: WorkerLimits = WorkerLimits(),
    val mediaTypes: List<String> = emptyList(),
    val features: WorkerFeatures = WorkerFeatures()
)

data class IceServerSpec(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

data class TurnCredentialsResponse(
    val provider: String,
    val relay: Boolean,
    val iceServers: List<IceServerSpec>,
    val expiresAt: Long,
    val warning: String? = null
)

data class SfuTrackSpec(
    val location: String,
    val trackName: String,
    val sessionId: String? = null,
    val mid: String? = null
)

data class SfuSessionResponse(
    val sessionId: String,
    val sdpType: String,
    val sdp: String,
    val requiresImmediateRenegotiation: Boolean = false,
    val tracks: List<SfuTrackSpec> = emptyList()
)

data class MediaUploadResponse(
    val ok: Boolean,
    val key: String,
    val url: String,
    val size: Long,
    val contentType: String
)

data class PresenceStatus(
    val uid: String,
    val online: Boolean,
    val lastSeen: Long
)

data class WorkerSearchResult(
    val users: List<Map<String, Any?>> = emptyList(),
    val offers: List<Map<String, Any?>> = emptyList()
)

data class FollowToggleResult(
    val targetUid: String,
    val following: Boolean,
    val followersCount: Int,
    val requested: Boolean = false,
    val relationState: String = if (following) "following" else "not_following"
)

data class FollowRequestRespondResult(
    val ok: Boolean = true,
    val accepted: Boolean = false,
    val followersCount: Int = 0
)

data class WorkerInviteCreateResult(
    val code: String = "",
    val shareUrl: String = ""
)

data class WorkerCallerLookupResult(
    val found: Boolean = false,
    val phoneLookupHash: String = "",
    val businessName: String = "",
    val category: String = "business",
    val city: String = "",
    val countryIso: String = "",
    val verified: Boolean = false
)

data class WorkerCertificationEligibility(
    val exemptDesigner: Boolean = false,
    val eligible: Boolean = false,
    val status: String = "none",
    val caseNumber: String = "",
    val followersCount: Int = 0,
    val requiredFollowers: Int = 5000,
    val missingFollowers: Int = 5000,
    val feeXof: Int = 3600,
    val feeUsd: String = "5,99 USD",
    val receiptNumber: String = "",
    val paymentTxRef: String = ""
)

data class WorkerSelfieSubmitResult(
    val ok: Boolean,
    val caseNumber: String = "",
    val status: String = "awaiting_payment",
    val selfieKey: String = ""
)

data class WorkerPaymentInitiateResult(
    val txRef: String,
    val caseNumber: String = "",
    val amountXof: Int = 3600,
    val amountUsd: String = "5,99 USD",
    val currency: String = "XOF",
    val status: String = "pending"
)

data class WorkerPaymentConfirmResult(
    val ok: Boolean,
    val status: String = "verified",
    val badgeGranted: Boolean = false,
    val followersCount: Int = 0,
    val receiptNumber: String = "",
    val txRef: String = "",
    val caseNumber: String = "",
    val amountXof: Int = 3600,
    val amountUsd: String = "5,99 USD",
    val currency: String = "XOF",
    val verifiedAtMs: Long = 0L
)

sealed interface RealtimeEvent {
    data class Ready(val uid: String, val deviceId: String, val connections: Int) : RealtimeEvent
    data class PresenceSnapshot(val users: List<PresenceStatus>) : RealtimeEvent
    data class PresenceChanged(val uid: String, val online: Boolean, val lastSeen: Long) : RealtimeEvent
    data class Typing(val conversationId: String, val fromUid: String, val isTyping: Boolean) : RealtimeEvent
    data class Receipt(val conversationId: String, val messageId: String, val status: String, val fromUid: String) : RealtimeEvent
    data class IncomingMessage(
        val conversationId: String,
        val messageId: String,
        val senderId: String,
        val senderName: String,
        val messageType: String,
        val preview: String
    ) : RealtimeEvent
    data class IncomingCall(
        val callId: String,
        val callType: String,
        val callerId: String,
        val callerName: String,
        val sentAt: Long
    ) : RealtimeEvent
    data class CallEnded(val callId: String, val reason: String) : RealtimeEvent
    data class MissedCall(val callId: String, val callerId: String, val callerName: String, val callType: String) : RealtimeEvent
    data class NotificationReceived(val notification: AppNotification) : RealtimeEvent
    data class NotificationsRead(val ids: List<String>, val refId: String, val readAtMs: Long) : RealtimeEvent
    data class NotificationsReadAll(val category: String, val readAtMs: Long) : RealtimeEvent
}

object WorkerConstants {
    const val BASE_URL = "https://bold-darkness-7957.kolopay-ci.workers.dev"
    const val WS_BASE_URL = "wss://bold-darkness-7957.kolopay-ci.workers.dev"
    const val WS_PROTOCOL = "brav.v1"

    // Cloudflare Managed Infrastructure Identifiers
    const val R2_BUCKET_NAME = "bravchat"
    const val R2_REGION = "WEUR"
    const val D1_DATABASE_NAME = "brav_chat"
    const val D1_DATABASE_ID = "005a0119-2bb4-40ce-a50c-b3c3a461bc19"
    const val SFU_APP_ID = "00e84148168d52e644ef50bc3824ff3b"
}
