package com.example.network

import android.util.Log
import com.example.model.AppNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

class WorkerRealtimeClient(
    private val apiClient: WorkerApiClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var shouldStayConnected = false
    private var activeDeviceId: String = ""
    private var reconnectAttempt = 0
    private var watchedUids: Set<String> = emptySet()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private val _presenceMap = MutableStateFlow<Map<String, PresenceStatus>>(emptyMap())
    val presenceMap: StateFlow<Map<String, PresenceStatus>> = _presenceMap.asStateFlow()

    private val _typingConversations = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val typingConversations: StateFlow<Map<String, Set<String>>> = _typingConversations.asStateFlow()

    fun connect(deviceId: String) {
        activeDeviceId = deviceId
        shouldStayConnected = true
        if (_isConnected.value || webSocket != null) return
        openSocket()
    }

    fun disconnect() {
        shouldStayConnected = false
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "client_disconnect")
        webSocket = null
        _isConnected.value = false
    }

    private fun openSocket(forceTokenRefresh: Boolean = false) {
        scope.launch {
            val config = apiClient.getOrFetchConfig(forceRefresh = false)
            if (config != null && !config.features.realtimeHub) {
                // Worker realtime hub DO not yet enabled; back off gently without battery drain
                scheduleReconnect(slowBackoffMs = 90_000L)
                return@launch
            }
            val token = apiClient.getFreshIdToken(forceTokenRefresh)
            if (token.isNullOrBlank()) {
                scheduleReconnect()
                return@launch
            }
            val url = "${WorkerConstants.WS_BASE_URL}/v1/realtime?deviceId=$activeDeviceId"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Sec-WebSocket-Protocol", WorkerConstants.WS_PROTOCOL)
                .build()

            webSocket = apiClient.httpClient.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (this@WorkerRealtimeClient.webSocket !== webSocket) return
                    reconnectAttempt = 0
                    _isConnected.value = true
                    startHeartbeat()
                    if (watchedUids.isNotEmpty()) {
                        watchUsers(watchedUids.toList())
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (this@WorkerRealtimeClient.webSocket !== webSocket) return
                    if (text == "pong") return
                    handleIncomingJson(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (this@WorkerRealtimeClient.webSocket !== webSocket) return
                    _isConnected.value = false
                    heartbeatJob?.cancel()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (this@WorkerRealtimeClient.webSocket !== webSocket) return
                    this@WorkerRealtimeClient.webSocket = null
                    _isConnected.value = false
                    heartbeatJob?.cancel()
                    if (shouldStayConnected && code != 4000) scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (this@WorkerRealtimeClient.webSocket !== webSocket) return
                    Log.w(TAG, "Realtime WS failure (${response?.code}): ${t.message}")
                    this@WorkerRealtimeClient.webSocket = null
                    _isConnected.value = false
                    heartbeatJob?.cancel()
                    if (shouldStayConnected) {
                        if (response?.code == 401 && !forceTokenRefresh) {
                            openSocket(forceTokenRefresh = true)
                        } else if (response?.code == 503) {
                            scheduleReconnect(slowBackoffMs = 90_000L)
                        } else {
                            scheduleReconnect()
                        }
                    }
                }
            })
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _isConnected.value) {
                delay(25_000L)
                webSocket?.send("ping")
            }
        }
    }

    private fun scheduleReconnect(slowBackoffMs: Long? = null) {
        if (!shouldStayConnected) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempt++
            val backoff = slowBackoffMs ?: minOf(30_000L, 1500L * (1 shl minOf(reconnectAttempt, 4)))
            delay(backoff)
            if (shouldStayConnected) openSocket()
        }
    }

    fun sendTyping(toUid: String, conversationId: String, isTyping: Boolean) {
        val payload = JSONObject().apply {
            put("type", "typing")
            put("to", toUid)
            put("conversationId", conversationId)
            put("isTyping", isTyping)
        }
        webSocket?.send(payload.toString())
    }

    fun sendReceipt(toUid: String, conversationId: String, messageId: String, status: String) {
        val payload = JSONObject().apply {
            put("type", "receipt")
            put("to", toUid)
            put("conversationId", conversationId)
            put("messageId", messageId)
            put("status", status)
        }
        webSocket?.send(payload.toString())
    }

    fun watchUsers(uids: List<String>) {
        val filtered = uids.filter { it.isNotBlank() }.take(100)
        if (filtered.isEmpty()) return
        watchedUids = (watchedUids + filtered).take(100).toSet()
        val payload = JSONObject().apply {
            put("type", "watch")
            put("uids", JSONArray(filtered))
        }
        webSocket?.send(payload.toString())
    }

    private fun handleIncomingJson(raw: String) {
        try {
            val j = JSONObject(raw)
            when (j.optString("type")) {
                "pong" -> Unit
                "ready" -> {
                    _events.tryEmit(
                        RealtimeEvent.Ready(
                            uid = j.optString("uid"),
                            deviceId = j.optString("deviceId"),
                            connections = j.optInt("connections", 1)
                        )
                    )
                }
                "presence_snapshot" -> {
                    val arr = j.optJSONArray("users") ?: JSONArray()
                    val list = (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        PresenceStatus(
                            uid = o.optString("uid"),
                            online = o.optBoolean("online", false),
                            lastSeen = o.optLong("lastSeen", 0L)
                        )
                    }
                    val updated = _presenceMap.value.toMutableMap()
                    list.forEach { updated[it.uid] = it }
                    _presenceMap.value = updated
                    _events.tryEmit(RealtimeEvent.PresenceSnapshot(list))
                }
                "presence" -> {
                    val uid = j.optString("uid")
                    val online = j.optBoolean("online", false)
                    val lastSeen = j.optLong("lastSeen", 0L)
                    _presenceMap.value = _presenceMap.value + (uid to PresenceStatus(uid, online, lastSeen))
                    _events.tryEmit(RealtimeEvent.PresenceChanged(uid, online, lastSeen))
                }
                "typing" -> {
                    val cid = j.optString("conversationId")
                    val from = j.optString("from")
                    val isTyping = j.optBoolean("isTyping", false)
                    val map = _typingConversations.value.toMutableMap()
                    val set = (map[cid] ?: emptySet()).toMutableSet()
                    if (isTyping) set.add(from) else set.remove(from)
                    map[cid] = set
                    _typingConversations.value = map
                    _events.tryEmit(RealtimeEvent.Typing(cid, from, isTyping))
                }
                "receipt" -> {
                    _events.tryEmit(
                        RealtimeEvent.Receipt(
                            conversationId = j.optString("conversationId"),
                            messageId = j.optString("messageId"),
                            status = j.optString("status"),
                            fromUid = j.optString("from")
                        )
                    )
                }
                "message" -> {
                    _events.tryEmit(
                        RealtimeEvent.IncomingMessage(
                            conversationId = j.optString("conversationId"),
                            messageId = j.optString("messageId"),
                            senderId = j.optString("senderId"),
                            senderName = j.optString("senderName"),
                            messageType = j.optString("messageType", "text"),
                            preview = j.optString("preview")
                        )
                    )
                }
                "incoming_call" -> {
                    _events.tryEmit(
                        RealtimeEvent.IncomingCall(
                            callId = j.optString("callId"),
                            callType = j.optString("callType", "audio"),
                            callerId = j.optString("callerId"),
                            callerName = j.optString("callerName"),
                            sentAt = j.optLong("sentAt", 0L)
                        )
                    )
                }
                "call_ended" -> {
                    _events.tryEmit(
                        RealtimeEvent.CallEnded(
                            callId = j.optString("callId"),
                            reason = j.optString("reason", "ended")
                        )
                    )
                }
                "missed_call" -> {
                    _events.tryEmit(
                        RealtimeEvent.MissedCall(
                            callId = j.optString("callId"),
                            callerId = j.optString("callerId"),
                            callerName = j.optString("callerName"),
                            callType = j.optString("callType", "audio")
                        )
                    )
                }
                "notification" -> {
                    val o = j.optJSONObject("notification")
                    if (o != null) {
                        _events.tryEmit(
                            RealtimeEvent.NotificationReceived(
                                AppNotification(
                                    id = o.optString("id"),
                                    userId = o.optString("userId"),
                                    senderUid = o.optString("senderUid"),
                                    senderName = o.optString("senderName"),
                                    senderAvatar = o.optString("senderAvatar"),
                                    category = o.optString("category", "messages"),
                                    type = o.optString("type", "event"),
                                    title = o.optString("title"),
                                    body = o.optString("body"),
                                    refId = o.optString("refId"),
                                    refType = o.optString("refType"),
                                    subRefId = o.optString("subRefId"),
                                    dedupKey = o.optString("dedupKey"),
                                    priority = o.optString("priority", "normal"),
                                    isRead = o.optBoolean("isRead", false),
                                    readAtMs = o.optLong("readAtMs", 0L),
                                    createdAtMs = o.optLong("createdAtMs", 0L)
                                )
                            )
                        )
                    }
                }
                "notifications_read" -> {
                    val arr = j.optJSONArray("ids") ?: JSONArray()
                    val ids = (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
                    _events.tryEmit(
                        RealtimeEvent.NotificationsRead(
                            ids = ids,
                            refId = j.optString("refId"),
                            readAtMs = j.optLong("readAtMs", System.currentTimeMillis())
                        )
                    )
                }
                "notifications_read_all" -> {
                    _events.tryEmit(
                        RealtimeEvent.NotificationsReadAll(
                            category = j.optString("category"),
                            readAtMs = j.optLong("readAtMs", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse WS message: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "WorkerRealtimeClient"
    }
}
