package com.example.network

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.model.AppNotification
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class WorkerApiClient {

    private val auth = Firebase.auth

    private val _cachedConfig = MutableStateFlow<WorkerConfig?>(null)
    val cachedConfig: StateFlow<WorkerConfig?> = _cachedConfig.asStateFlow()
    @Volatile
    private var lastConfigFetchMs: Long = 0L

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    val mediaAuthorizedClient: OkHttpClient = httpClient.newBuilder()
        .addInterceptor { chain ->
            val original = chain.request()
            val url = original.url.toString()
            if (url.startsWith(WorkerConstants.BASE_URL)) {
                val token = fetchBlockingToken(forceRefresh = false)
                val req = if (!token.isNullOrBlank()) {
                    original.newBuilder().header("Authorization", "Bearer $token").build()
                } else {
                    original
                }
                val response = chain.proceed(req)
                if (response.code == 401) {
                    val refreshed = fetchBlockingToken(forceRefresh = true)
                    if (!refreshed.isNullOrBlank()) {
                        response.close()
                        val retryReq = original.newBuilder()
                            .header("Authorization", "Bearer $refreshed")
                            .build()
                        return@addInterceptor chain.proceed(retryReq)
                    }
                }
                return@addInterceptor response
            }
            chain.proceed(original)
        }
        .build()

    private fun fetchBlockingToken(forceRefresh: Boolean): String? {
        return runCatching {
            val user = auth.currentUser ?: return@runCatching null
            com.google.android.gms.tasks.Tasks.await(user.getIdToken(forceRefresh)).token
        }.getOrNull()
    }

    fun createCoilImageLoader(context: Context): ImageLoader {
        val appCtx = context.applicationContext
        return ImageLoader.Builder(appCtx)
            .okHttpClient(mediaAuthorizedClient)
            .memoryCache {
                MemoryCache.Builder(appCtx)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appCtx.cacheDir, "brav_image_cache"))
                    .maxSizeBytes(64L * 1024L * 1024L)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    suspend fun getFreshIdToken(forceRefresh: Boolean = false): String? {
        val user = auth.currentUser ?: return null
        return try {
            user.getIdToken(forceRefresh).await().token
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Firebase ID token: ${e.message}")
            null
        }
    }

    private suspend fun authorizedRequest(
        path: String,
        method: String = "GET",
        jsonBody: JSONObject? = null
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            var token = getFreshIdToken(false)
                ?: return@withContext Result.failure(IllegalStateException("Non authentifié"))
            var response = executeJsonRequest(path, method, jsonBody, token)
            if (response.first == 401) {
                token = getFreshIdToken(true) ?: token
                response = executeJsonRequest(path, method, jsonBody, token)
            }
            val (code, bodyText) = response
            val json = runCatching { JSONObject(bodyText) }.getOrElse { JSONObject() }
            if (code in 200..299) {
                Result.success(json)
            } else {
                val rawErr = json.optString("error", "HTTP_$code")
                Result.failure(IOException(formatWorkerError(rawErr, code)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatWorkerError(errorCode: String, statusCode: Int): String {
        return when (errorCode) {
            "MEDIA_NOT_CONFIGURED" -> "Stockage R2 (bravchat) non activé sur le Worker Cloudflare."
            "USER_HUBS_BINDING_MISSING",
            "CALL_ROOMS_BINDING_MISSING" -> "Signalisation Worker non activée (bascule automatique active)."
            "SFU_NOT_CONFIGURED" -> "Cloudflare SFU non activé sur le Worker."
            "FILE_TOO_LARGE" -> "Fichier trop volumineux pour l'envoi."
            "UNSUPPORTED_MEDIA_TYPE" -> "Format de fichier non pris en charge."
            else -> "Worker ($statusCode): $errorCode"
        }
    }

    private fun executeJsonRequest(
        path: String,
        method: String,
        jsonBody: JSONObject?,
        token: String
    ): Pair<Int, String> {
        val url = if (path.startsWith("http")) path else "${WorkerConstants.BASE_URL}$path"
        val body: RequestBody? = when {
            method == "GET" || method == "HEAD" -> null
            jsonBody != null -> jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            else -> "".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        }
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .method(method, body)
            .build()
        httpClient.newCall(req).execute().use { res ->
            return res.code to (res.body?.string() ?: "{}")
        }
    }

    suspend fun getOrFetchConfig(forceRefresh: Boolean = false): WorkerConfig? {
        val current = _cachedConfig.value
        val fresh = System.currentTimeMillis() - lastConfigFetchMs < 60_000L
        if (!forceRefresh && current != null && fresh) return current
        return fetchConfig().getOrNull() ?: current
    }

    suspend fun fetchConfig(): Result<WorkerConfig> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("${WorkerConstants.BASE_URL}/v1/config").get().build()
            httpClient.newCall(req).execute().use { res ->
                val raw = res.body?.string() ?: "{}"
                if (!res.isSuccessful) return@withContext Result.failure(IOException("Config HTTP ${res.code}"))
                val j = JSONObject(raw)
                val hb = j.optJSONObject("heartbeat")
                val lim = j.optJSONObject("limits")
                val feat = j.optJSONObject("features")
                val mTypesArr = j.optJSONArray("mediaTypes")
                val mediaTypes = mutableListOf<String>()
                if (mTypesArr != null) {
                    for (i in 0 until mTypesArr.length()) mediaTypes.add(mTypesArr.optString(i))
                }
                val config = WorkerConfig(
                    version = j.optString("version", "3.0.0"),
                    baseUrl = j.optString("baseUrl", WorkerConstants.BASE_URL),
                    realtimeUrl = j.optString("realtimeUrl", "${WorkerConstants.WS_BASE_URL}/v1/realtime"),
                    callSignalUrlTemplate = j.optString("callSignalUrlTemplate", "${WorkerConstants.WS_BASE_URL}/v1/calls/{callId}/signal"),
                    websocketSubprotocol = j.optString("websocketSubprotocol", WorkerConstants.WS_PROTOCOL),
                    heartbeatEveryMs = hb?.optLong("everyMs", 25_000L) ?: 25_000L,
                    sfuAppId = j.optString("sfuAppId", WorkerConstants.SFU_APP_ID).ifBlank { WorkerConstants.SFU_APP_ID },
                    limits = WorkerLimits(
                        signalBytes = lim?.optInt("signalBytes", 32768) ?: 32768,
                        uploadMB = lim?.optInt("uploadMB", 25) ?: 25,
                        presenceUidsPerQuery = lim?.optInt("presenceUidsPerQuery", 100) ?: 100,
                        ringTimeoutMs = lim?.optLong("ringTimeoutMs", 45_000L) ?: 45_000L
                    ),
                    mediaTypes = mediaTypes,
                    features = WorkerFeatures(
                        auth = feat?.optBoolean("auth", true) ?: true,
                        realtimeHub = feat?.optBoolean("realtimeHub", false) ?: false,
                        callSignaling = feat?.optBoolean("callSignaling", false) ?: false,
                        turn = feat?.opt("turn")?.toString() ?: "false",
                        sfu = feat?.optBoolean("sfu", false) ?: false,
                        media = feat?.optBoolean("media", false) ?: false,
                        push = feat?.optBoolean("push", false) ?: false,
                        presenceSync = feat?.optBoolean("presenceSync", false) ?: false,
                        missedCalls = feat?.optBoolean("missedCalls", false) ?: false,
                        search = feat?.optBoolean("search", true) ?: true,
                        ai = feat?.optBoolean("ai", false) ?: false
                    )
                )
                _cachedConfig.value = config
                lastConfigFetchMs = System.currentTimeMillis()
                Result.success(config)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun whoami(): Result<JSONObject> = authorizedRequest("/v1/whoami")

    suspend fun fetchTurnCredentials(ttlSeconds: Int = 14400): Result<TurnCredentialsResponse> {
        return authorizedRequest("/v1/turn-credentials?ttl=$ttlSeconds").map { j ->
            val arr = j.optJSONArray("iceServers") ?: JSONArray()
            val servers = mutableListOf<IceServerSpec>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val urlsAny = item.opt("urls")
                val urls = when (urlsAny) {
                    is JSONArray -> (0 until urlsAny.length()).map { urlsAny.optString(it) }.filter { it.isNotBlank() }
                    is String -> listOf(urlsAny)
                    else -> emptyList()
                }
                if (urls.isNotEmpty()) {
                    servers.add(
                        IceServerSpec(
                            urls = urls,
                            username = item.optString("username").takeIf { it.isNotBlank() },
                            credential = item.optString("credential").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            TurnCredentialsResponse(
                provider = j.optString("provider", "stun-only"),
                relay = j.optBoolean("relay", false),
                iceServers = servers,
                expiresAt = j.optLong("expiresAt", 0L),
                warning = j.optString("warning").takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun createSfuSession(callId: String, offerSdp: String): Result<SfuSessionResponse> {
        val body = JSONObject().apply {
            put("appId", _cachedConfig.value?.sfuAppId ?: WorkerConstants.SFU_APP_ID)
            put("sessionDescription", JSONObject().apply {
                put("type", "offer")
                put("sdp", offerSdp)
            })
        }
        return authorizedRequest("/v1/sfu/calls/$callId/sessions", method = "POST", jsonBody = body).map { j ->
            parseSfuSessionResponse(j)
        }
    }

    suspend fun pushSfuTracks(
        callId: String,
        sessionId: String,
        tracks: List<SfuTrackSpec>,
        offerSdp: String? = null
    ): Result<SfuSessionResponse> {
        val arr = JSONArray()
        tracks.forEach { t ->
            arr.put(JSONObject().apply {
                put("location", t.location)
                put("trackName", t.trackName)
                if (!t.sessionId.isNullOrBlank()) put("sessionId", t.sessionId)
                if (!t.mid.isNullOrBlank()) put("mid", t.mid)
            })
        }
        val body = JSONObject().apply {
            put("tracks", arr)
            if (!offerSdp.isNullOrBlank()) {
                put("sessionDescription", JSONObject().apply {
                    put("type", "offer")
                    put("sdp", offerSdp)
                })
            }
        }
        return authorizedRequest("/v1/sfu/calls/$callId/sessions/$sessionId/tracks", method = "POST", jsonBody = body).map { j ->
            parseSfuSessionResponse(j, fallbackSessionId = sessionId)
        }
    }

    suspend fun renegotiateSfuSession(callId: String, sessionId: String, answerSdp: String): Result<Unit> {
        val body = JSONObject().apply {
            put("sessionDescription", JSONObject().apply {
                put("type", "answer")
                put("sdp", answerSdp)
            })
        }
        return authorizedRequest("/v1/sfu/calls/$callId/sessions/$sessionId/renegotiate", method = "PUT", jsonBody = body).map { }
    }

    suspend fun listCallSfuTracks(callId: String): Result<List<SfuTrackSpec>> {
        return authorizedRequest("/v1/sfu/calls/$callId/tracks").map { j ->
            parseSfuTracks(j.optJSONArray("tracks"))
        }
    }

    private fun parseSfuSessionResponse(j: JSONObject, fallbackSessionId: String = ""): SfuSessionResponse {
        val sd = j.optJSONObject("sessionDescription")
        return SfuSessionResponse(
            sessionId = j.optString("sessionId", fallbackSessionId),
            sdpType = sd?.optString("type", "answer") ?: "answer",
            sdp = sd?.optString("sdp", "").orEmpty(),
            requiresImmediateRenegotiation = j.optBoolean("requiresImmediateRenegotiation", false),
            tracks = parseSfuTracks(j.optJSONArray("tracks"))
        )
    }

    private fun parseSfuTracks(arr: JSONArray?): List<SfuTrackSpec> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            SfuTrackSpec(
                location = o.optString("location", "remote"),
                trackName = o.optString("trackName"),
                sessionId = o.optString("sessionId").takeIf { it.isNotBlank() },
                mid = o.optString("mid").takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun ringCall(
        callId: String,
        receiverId: String = "",
        callerName: String = "",
        callType: String = "audio"
    ): Result<JSONObject> =
        authorizedRequest(
            "/v1/calls/$callId/ring",
            method = "POST",
            jsonBody = JSONObject().apply {
                if (receiverId.isNotBlank()) put("receiverId", receiverId)
                if (callerName.isNotBlank()) put("callerName", callerName)
                put("callType", callType)
            }
        )

    suspend fun notifyChatMessage(
        conversationId: String,
        messageId: String,
        recipientIds: List<String> = emptyList(),
        senderName: String = "",
        messageType: String = "text",
        preview: String = ""
    ): Result<JSONObject> =
        authorizedRequest(
            "/v1/chat/notify",
            method = "POST",
            jsonBody = JSONObject().apply {
                put("conversationId", conversationId)
                put("messageId", messageId)
                if (recipientIds.isNotEmpty()) put("recipientIds", JSONArray(recipientIds))
                if (senderName.isNotBlank()) put("senderName", senderName)
                put("messageType", messageType)
                if (preview.isNotBlank()) put("preview", preview.take(160))
            }
        )

    suspend fun registerDeviceToken(fcmToken: String, platform: String = "android"): Result<JSONObject> =
        authorizedRequest(
            "/v1/devices",
            method = "POST",
            jsonBody = JSONObject().apply {
                put("token", fcmToken)
                put("platform", platform)
            }
        )

    suspend fun unregisterDeviceToken(fcmToken: String): Result<JSONObject> =
        authorizedRequest(
            "/v1/devices",
            method = "DELETE",
            jsonBody = JSONObject().apply { put("token", fcmToken) }
        )

    suspend fun queryPresence(uids: List<String>): Result<List<PresenceStatus>> {
        if (uids.isEmpty()) return Result.success(emptyList())
        val joined = URLEncoder.encode(uids.take(100).joinToString(","), "UTF-8")
        return authorizedRequest("/v1/presence?uids=$joined").map { j ->
            val arr = j.optJSONArray("users") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                PresenceStatus(
                    uid = o.optString("uid"),
                    online = o.optBoolean("online", false),
                    lastSeen = o.optLong("lastSeen", 0L)
                )
            }
        }
    }

    suspend fun uploadMedia(
        conversationId: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String = "",
        onProgress: (Float) -> Unit = {}
    ): Result<MediaUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val maxBytes = (_cachedConfig.value?.limits?.uploadMB ?: 25).toLong() * 1024L * 1024L
            if (bytes.size.toLong() > maxBytes) {
                return@withContext Result.failure(
                    IOException("Fichier trop volumineux (max ${maxBytes / (1024L * 1024L)} Mo).")
                )
            }
            var token = getFreshIdToken(false)
                ?: return@withContext Result.failure(IllegalStateException("Non authentifié"))
            val url = buildString {
                append("${WorkerConstants.BASE_URL}/v1/media?conversationId=")
                append(URLEncoder.encode(conversationId, "UTF-8"))
                if (fileName.isNotBlank()) {
                    append("&fileName=").append(URLEncoder.encode(fileName.take(120), "UTF-8"))
                }
            }
            val mediaType = mimeType.toMediaTypeOrNull()

            fun executeUpload(bearer: String): Pair<Int, String> {
                val body = object : RequestBody() {
                    override fun contentType() = mediaType
                    override fun contentLength() = bytes.size.toLong()
                    override fun writeTo(sink: BufferedSink) {
                        val chunkSize = 8192
                        var offset = 0
                        while (offset < bytes.size) {
                            val len = minOf(chunkSize, bytes.size - offset)
                            sink.write(bytes, offset, len)
                            offset += len
                            onProgress(offset.toFloat() / bytes.size.coerceAtLeast(1))
                        }
                    }
                }
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $bearer")
                    .header("X-Brav-Bucket", WorkerConstants.R2_BUCKET_NAME)
                    .post(body)
                    .build()
                return httpClient.newCall(req).execute().use { res ->
                    res.code to (res.body?.string() ?: "{}")
                }
            }

            var (code, text) = executeUpload(token)
            if (code == 401) {
                token = getFreshIdToken(true) ?: token
                val retried = executeUpload(token)
                code = retried.first
                text = retried.second
            }
            val j = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code in 200..299) {
                val rawUrl = j.optString("url")
                val resolvedUrl = if (rawUrl.startsWith("/")) "${WorkerConstants.BASE_URL}$rawUrl" else rawUrl
                Result.success(
                    MediaUploadResponse(
                        ok = j.optBoolean("ok", true),
                        key = j.optString("key"),
                        url = resolvedUrl,
                        size = j.optLong("size", bytes.size.toLong()),
                        contentType = j.optString("contentType", mimeType)
                    )
                )
            } else {
                val errCode = j.optString("error", "UPLOAD_FAILED_$code")
                Result.failure(IOException(formatWorkerError(errCode, code)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadMediaFile(
        conversationId: String,
        file: File,
        mimeType: String,
        fileName: String = file.name,
        onProgress: (Float) -> Unit = {}
    ): Result<MediaUploadResponse> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() <= 0L) {
                return@withContext Result.failure(IOException("Fichier média introuvable ou vide."))
            }
            val maxBytes = (_cachedConfig.value?.limits?.uploadMB ?: 25).toLong() * 1024L * 1024L
            val totalBytes = file.length()
            if (totalBytes > maxBytes) {
                return@withContext Result.failure(
                    IOException("Fichier trop volumineux (max ${maxBytes / (1024L * 1024L)} Mo).")
                )
            }
            var token = getFreshIdToken(false)
                ?: return@withContext Result.failure(IllegalStateException("Non authentifié"))
            val url = buildString {
                append("${WorkerConstants.BASE_URL}/v1/media?conversationId=")
                append(URLEncoder.encode(conversationId, "UTF-8"))
                if (fileName.isNotBlank()) {
                    append("&fileName=").append(URLEncoder.encode(fileName.take(120), "UTF-8"))
                }
            }
            val mediaType = mimeType.toMediaTypeOrNull()

            fun executeFileUpload(bearer: String): Pair<Int, String> {
                val body = object : RequestBody() {
                    override fun contentType() = mediaType
                    override fun contentLength() = totalBytes
                    override fun writeTo(sink: BufferedSink) {
                        val buffer = ByteArray(16 * 1024)
                        var uploaded = 0L
                        file.inputStream().use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                sink.write(buffer, 0, read)
                                uploaded += read
                                onProgress((uploaded.toFloat() / totalBytes.coerceAtLeast(1L)).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $bearer")
                    .header("X-Brav-Bucket", WorkerConstants.R2_BUCKET_NAME)
                    .post(body)
                    .build()
                return httpClient.newCall(req).execute().use { res ->
                    res.code to (res.body?.string() ?: "{}")
                }
            }

            var (code, text) = executeFileUpload(token)
            if (code == 401) {
                token = getFreshIdToken(true) ?: token
                val retried = executeFileUpload(token)
                code = retried.first
                text = retried.second
            }
            val j = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code in 200..299) {
                val rawUrl = j.optString("url")
                val resolvedUrl = if (rawUrl.startsWith("/")) "${WorkerConstants.BASE_URL}$rawUrl" else rawUrl
                Result.success(
                    MediaUploadResponse(
                        ok = j.optBoolean("ok", true),
                        key = j.optString("key"),
                        url = resolvedUrl,
                        size = j.optLong("size", totalBytes),
                        contentType = j.optString("contentType", mimeType)
                    )
                )
            } else {
                val errCode = j.optString("error", "UPLOAD_FAILED_$code")
                Result.failure(IOException(formatWorkerError(errCode, code)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMedia(conversationId: String, fileName: String): Result<JSONObject> =
        authorizedRequest("/v1/media/c/$conversationId/$fileName", method = "DELETE")

    suspend fun deleteMediaByUrl(mediaUrl: String): Result<JSONObject> {
        val marker = "/v1/media/"
        val idx = mediaUrl.indexOf(marker)
        if (idx < 0) return Result.success(JSONObject())
        val objectKey = mediaUrl.substring(idx + marker.length).substringBefore('?').trimStart('/')
        if (objectKey.isBlank()) return Result.success(JSONObject())
        return authorizedRequest("/v1/media/$objectKey", method = "DELETE")
    }

    suspend fun downloadAuthorizedBytes(mediaUrl: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            var token = getFreshIdToken(false)
                ?: return@withContext Result.failure(IllegalStateException("Non authentifié"))
            val resolvedUrl = if (mediaUrl.startsWith("/")) "${WorkerConstants.BASE_URL}$mediaUrl" else mediaUrl

            fun executeGet(bearer: String): Pair<Int, ByteArray?> {
                val req = Request.Builder()
                    .url(resolvedUrl)
                    .apply {
                        if (resolvedUrl.startsWith(WorkerConstants.BASE_URL)) {
                            header("Authorization", "Bearer $bearer")
                        }
                    }
                    .get()
                    .build()
                return httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) res.code to (res.body?.bytes() ?: ByteArray(0))
                    else res.code to null
                }
            }

            var (code, data) = executeGet(token)
            if (code == 401 && resolvedUrl.startsWith(WorkerConstants.BASE_URL)) {
                token = getFreshIdToken(true) ?: token
                val retried = executeGet(token)
                code = retried.first
                data = retried.second
            }
            if (code in 200..299 && data != null) {
                Result.success(data)
            } else {
                Result.failure(IOException("HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadMediaToCache(
        context: Context,
        mediaUrl: String,
        fileNameHint: String = ""
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (mediaUrl.startsWith("file://") || mediaUrl.startsWith("/data/")) {
                return@withContext Result.success(File(mediaUrl.removePrefix("file://")))
            }
            val cacheDir = File(context.cacheDir, "brav_media_cache").apply { mkdirs() }
            val ext = fileNameHint.substringAfterLast('.', "")
                .ifBlank { mediaUrl.substringBefore('?').substringAfterLast('.', "") }
                .lowercase()
                .replace(Regex("[^a-z0-9]"), "")
                .take(8)
                .ifBlank { "bin" }
            val key = MessageDigest.getInstance("SHA-256")
                .digest(mediaUrl.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(28)
            val targetFile = File(cacheDir, "m_$key.$ext")
            if (targetFile.exists() && targetFile.length() > 0L) {
                return@withContext Result.success(targetFile)
            }
            val bytes = downloadAuthorizedBytes(mediaUrl).getOrElse { return@withContext Result.failure(it) }
            if (bytes.isEmpty()) return@withContext Result.failure(IOException("Fichier vide"))
            val tmpFile = File(cacheDir, "m_$key.tmp")
            tmpFile.writeBytes(bytes)
            if (!tmpFile.renameTo(targetFile)) {
                targetFile.writeBytes(bytes)
                tmpFile.delete()
            }
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncUserDirectoryToD1(
        userId: String,
        displayName: String,
        username: String,
        city: String,
        avatarUrl: String
    ) {
        val body = JSONObject().apply {
            put("userId", userId)
            put("displayName", displayName)
            put("username", username)
            put("locationCity", city)
            put("avatarUrl", avatarUrl)
        }
        runCatching { authorizedRequest("/v1/users/sync", method = "POST", jsonBody = body) }
    }

    suspend fun syncOfferToD1(
        offerId: String,
        userId: String,
        title: String,
        category: String,
        description: String,
        city: String,
        priceOrSalary: String,
        authorName: String
    ) {
        val body = JSONObject().apply {
            put("id", offerId)
            put("userId", userId)
            put("title", title)
            put("category", category)
            put("description", description)
            put("locationCity", city)
            put("priceOrSalary", priceOrSalary)
            put("authorName", authorName)
            put("status", "published")
            put("paymentStatus", "free")
        }
        runCatching { authorizedRequest("/v1/offers/sync", method = "POST", jsonBody = body) }
    }

    suspend fun search(
        query: String,
        type: String = "all",
        city: String = "",
        category: String = "",
        limit: Int = 20
    ): Result<WorkerSearchResult> {
        val params = buildString {
            append("?q=").append(URLEncoder.encode(query, "UTF-8"))
            append("&type=").append(URLEncoder.encode(type, "UTF-8"))
            if (city.isNotBlank()) append("&city=").append(URLEncoder.encode(city, "UTF-8"))
            if (category.isNotBlank()) append("&category=").append(URLEncoder.encode(category, "UTF-8"))
            append("&limit=").append(limit)
        }
        return authorizedRequest("/v1/search$params").map { j ->
            val usersArr = j.optJSONArray("users") ?: JSONArray()
            val offersArr = j.optJSONArray("offers") ?: JSONArray()
            WorkerSearchResult(
                users = jsonArrayToList(usersArr),
                offers = jsonArrayToList(offersArr)
            )
        }
    }

    suspend fun fetchAiModels(): Result<List<WorkerAiModel>> {
        return authorizedRequest("/v1/ai/models").map { j ->
            val arr = j.optJSONArray("models") ?: JSONArray()
            (0 until arr.length()).mapNotNull { idx ->
                val o = arr.optJSONObject(idx) ?: return@mapNotNull null
                val id = o.optString("id")
                val label = o.optString("label", id)
                val category = o.optString("category", "Général").ifBlank { "Général" }
                if (id.isNotBlank()) WorkerAiModel(id, label, category) else null
            }
        }
    }

    suspend fun aiChat(
        prompt: String,
        history: List<Pair<String, String>> = emptyList(),
        model: String = "@cf/meta/llama-3.1-8b-instruct",
        systemPrompt: String = "",
        intent: String = ""
    ): Result<WorkerAiReply> {
        val histArr = JSONArray()
        history.takeLast(8).forEach { (role, content) ->
            histArr.put(JSONObject().apply {
                put("role", role)
                put("content", content)
            })
        }
        val enforcedSystem = if (systemPrompt.isNotBlank()) {
            "$STRICT_FRENCH_RULE $systemPrompt"
        } else {
            "$STRICT_FRENCH_RULE $MATH_PEDAGOGY_RULE"
        }
        val body = JSONObject().apply {
            put("prompt", prompt)
            if (model.isNotBlank()) put("model", model)
            put("systemPrompt", enforcedSystem)
            if (intent.isNotBlank()) put("intent", intent)
            if (histArr.length() > 0) put("history", histArr)
        }
        return authorizedRequest("/v1/ai/chat", method = "POST", jsonBody = body).map { j ->
            WorkerAiReply(
                model = j.optString("model", model),
                reply = j.optString("reply"),
                cached = j.optBoolean("cached", false)
            )
        }
    }

    suspend fun aiAutoReply(
        channel: String,
        senderName: String,
        incomingMessage: String,
        instruction: String,
        model: String = "@cf/meta/llama-3.1-8b-instruct",
        tone: String = "Professionnel",
        recentReplies: List<String> = emptyList(),
        history: List<Pair<String, String>> = emptyList()
    ): Result<WorkerAiReply> {
        val body = JSONObject().apply {
            put("channel", channel)
            put("senderName", senderName)
            put("incomingMessage", incomingMessage)
            put("instruction", instruction)
            put("tone", tone)
            if (recentReplies.isNotEmpty()) put("recentReplies", JSONArray(recentReplies.takeLast(5)))
            if (history.isNotEmpty()) {
                val arr = JSONArray()
                history.takeLast(4).forEach { (r, c) ->
                    arr.put(JSONObject().apply { put("role", r); put("content", c) })
                }
                put("history", arr)
            }
            if (model.isNotBlank()) put("model", model)
        }
        val primary = authorizedRequest("/v1/ai/auto-reply", method = "POST", jsonBody = body).map { j ->
            WorkerAiReply(
                model = j.optString("model", model),
                reply = j.optString("reply"),
                cached = false
            )
        }
        if (primary.isSuccess) return primary

        val limitHint = if (channel == "sms") "Format SMS strict : 1 à 2 phrases courtes (<260 caractères)." else "Réponse naturelle et concise (<400 caractères)."
        val sys = "$STRICT_FRENCH_RULE Tu rédiges une réponse automatique ($tone) pour l'utilisateur sur ${if (channel == "sms") "SMS" else "BRAV Chat"}. Consigne : $instruction. $limitHint Rédige uniquement en français sans te répéter."
        return aiChat(
            prompt = "Message reçu de $senderName : \"$incomingMessage\"\nRédige uniquement en français le texte exact à envoyer.",
            model = model,
            systemPrompt = sys,
            intent = "auto_reply"
        )
    }

    suspend fun aiMathExplain(
        topic: String,
        expression: String,
        localSummary: String = "",
        model: String = "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b"
    ): Result<WorkerAiReply> {
        val body = JSONObject().apply {
            put("topic", topic)
            put("expression", expression)
            if (localSummary.isNotBlank()) put("localSummary", localSummary)
            if (model.isNotBlank()) put("model", model)
        }
        val primary = authorizedRequest("/v1/ai/math", method = "POST", jsonBody = body).map { j ->
            WorkerAiReply(
                model = j.optString("model", model),
                reply = j.optString("reply"),
                cached = false
            )
        }
        if (primary.isSuccess) return primary

        val sys = "$STRICT_FRENCH_RULE $MATH_PEDAGOGY_RULE Tu es le professeur de mathématiques universel de BRAV Chat. Structure clairement la solution (Énoncé, Données, Méthode, Résolution détaillée étape par étape, Résultat final, Interprétation). N'affiche aucune commande LaTeX brute hors des délimiteurs $...$ ou $$...$$ et aucun code informatique."
        val promptText = buildString {
            append("Domaine : ").append(topic).append("\n")
            append("Expression LaTeX / Équation : ").append(expression)
            if (localSummary.isNotBlank()) {
                append("\nRésultat vérifié localement : ").append(localSummary)
            }
        }
        return aiChat(prompt = promptText, model = model, systemPrompt = sys, intent = "math")
    }

    suspend fun aiGenerateDocument(
        prompt: String,
        templateType: String = "RAPPORT",
        model: String = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
    ): Result<WorkerDocumentResponse> {
        val body = JSONObject().apply {
            put("prompt", prompt)
            put("templateType", templateType)
            if (model.isNotBlank()) put("model", model)
        }
        return authorizedRequest("/v1/ai/document", method = "POST", jsonBody = body).map { j ->
            val formulasArr = j.optJSONArray("keyFormulasLatex") ?: JSONArray()
            val formulas = (0 until formulasArr.length()).mapNotNull { formulasArr.optString(it).takeIf { s -> s.isNotBlank() } }
            val rowsArr = j.optJSONArray("tableRows") ?: JSONArray()
            val tableRows = (0 until rowsArr.length()).mapNotNull { rIdx ->
                val row = rowsArr.optJSONArray(rIdx) ?: return@mapNotNull null
                (0 until row.length()).map { cIdx -> row.optString(cIdx) }
            }
            WorkerDocumentResponse(
                model = j.optString("model", model),
                title = j.optString("title", "Document BRAV Chat"),
                subtitle = j.optString("subtitle", templateType),
                templateType = j.optString("templateType", templateType),
                bodyMarkdown = j.optString("bodyMarkdown", ""),
                keyFormulasLatex = formulas,
                tableRows = tableRows
            )
        }
    }

    suspend fun fetchAutoReplyRules(): Result<List<AutoReplyRuleDto>> {
        return authorizedRequest("/v1/auto-reply/rules").map { j ->
            val arr = j.optJSONArray("rules") ?: JSONArray()
            (0 until arr.length()).mapNotNull { idx ->
                val o = arr.optJSONObject(idx) ?: return@mapNotNull null
                val key = o.optString("contactKey")
                if (key.isBlank()) null
                else AutoReplyRuleDto(
                    contactKey = key,
                    contactLabel = o.optString("contactLabel", key),
                    mode = o.optString("mode", "AUTO"),
                    toneOverride = o.optString("toneOverride", "")
                )
            }
        }
    }

    suspend fun upsertAutoReplyRule(rule: AutoReplyRuleDto): Result<Unit> {
        val body = JSONObject().apply {
            put("contactKey", rule.contactKey)
            put("contactLabel", rule.contactLabel)
            put("mode", rule.mode)
            put("toneOverride", rule.toneOverride)
        }
        return authorizedRequest("/v1/auto-reply/rules", method = "POST", jsonBody = body).map { }
    }

    suspend fun deleteAutoReplyRule(contactKey: String): Result<Unit> {
        val enc = URLEncoder.encode(contactKey, "UTF-8")
        return authorizedRequest("/v1/auto-reply/rules?contactKey=$enc", method = "DELETE").map { }
    }

    suspend fun syncDocumentMeta(
        docId: String,
        title: String,
        format: String,
        templateType: String,
        sizeBytes: Long
    ): Result<Unit> {
        val body = JSONObject().apply {
            put("id", docId)
            put("title", title)
            put("format", format)
            put("templateType", templateType)
            put("sizeBytes", sizeBytes)
        }
        return authorizedRequest("/v1/documents", method = "POST", jsonBody = body).map { }
    }

    suspend fun saveLauncherProfile(
        themeId: String,
        iconShape: String,
        iconSizeDp: Int,
        configJson: String
    ): Result<Unit> {
        val body = JSONObject().apply {
            put("themeId", themeId)
            put("iconShape", iconShape)
            put("iconSizeDp", iconSizeDp)
            put("configJson", configJson)
        }
        return authorizedRequest("/v1/launcher/profile", method = "POST", jsonBody = body).map { }
    }

    suspend fun aiTranslate(
        text: String,
        targetLanguage: String,
        model: String = "@cf/meta/llama-3.1-8b-instruct"
    ): Result<String> {
        val body = JSONObject().apply {
            put("text", text)
            put("targetLanguage", targetLanguage)
            if (model.isNotBlank()) put("model", model)
        }
        return authorizedRequest("/v1/ai/translate", method = "POST", jsonBody = body).map { j ->
            j.optString("translatedText")
        }
    }

    suspend fun syncBlockUser(targetUid: String, reason: String = ""): Result<Unit> {
        val body = JSONObject().apply {
            put("targetUid", targetUid)
            put("reason", reason)
        }
        return authorizedRequest("/v1/blocks", method = "POST", jsonBody = body).map { }
    }

    suspend fun syncUnblockUser(targetUid: String): Result<Unit> {
        val enc = URLEncoder.encode(targetUid, "UTF-8")
        return authorizedRequest("/v1/blocks?targetUid=$enc", method = "DELETE").map { }
    }

    suspend fun syncMessageDeletion(conversationId: String, messageId: String, scope: String): Result<Unit> {
        val body = JSONObject().apply {
            put("conversationId", conversationId)
            put("messageId", messageId)
            put("scope", scope)
        }
        return authorizedRequest("/v1/messages/delete", method = "POST", jsonBody = body).map { }
    }

    suspend fun syncConversationState(
        conversationId: String,
        archived: Boolean,
        pinned: Boolean,
        muted: Boolean,
        locked: Boolean,
        markedUnread: Boolean,
        hiddenLocally: Boolean,
        lastReadAtMs: Long? = null
    ): Result<Unit> {
        val body = JSONObject().apply {
            put("conversationId", conversationId)
            put("archived", archived)
            put("pinned", pinned)
            put("muted", muted)
            put("locked", locked)
            put("markedUnread", markedUnread)
            put("hiddenLocally", hiddenLocally)
            if (lastReadAtMs != null) put("lastReadAtMs", lastReadAtMs)
        }
        return authorizedRequest("/v1/conversations/state", method = "POST", jsonBody = body).map { }
    }

    suspend fun recordLoginSession(
        deviceId: String,
        deviceName: String,
        countryCode: String = "",
        city: String = ""
    ): Result<LoginSessionEntry> {
        val body = JSONObject().apply {
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            if (countryCode.isNotBlank()) put("countryCode", countryCode)
            if (city.isNotBlank()) put("city", city)
        }
        return authorizedRequest("/v1/sessions/history", method = "POST", jsonBody = body).map { j ->
            LoginSessionEntry(
                id = j.optString("id"),
                deviceId = deviceId,
                deviceName = deviceName,
                countryCode = j.optString("countryCode", countryCode),
                city = j.optString("city", city),
                createdAtMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun fetchLoginHistory(): Result<List<LoginSessionEntry>> {
        return authorizedRequest("/v1/sessions/history").map { j ->
            val arr = j.optJSONArray("history") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                LoginSessionEntry(
                    id = o.optString("id"),
                    deviceId = o.optString("deviceId"),
                    deviceName = o.optString("deviceName"),
                    countryCode = o.optString("countryCode"),
                    city = o.optString("city"),
                    createdAtMs = o.optLong("createdAtMs", 0L)
                )
            }
        }
    }

    suspend fun toggleFollowWorker(
        targetUid: String,
        follow: Boolean,
        isPrivate: Boolean = false
    ): Result<FollowToggleResult> {
        val body = JSONObject().apply {
            put("targetUid", targetUid)
            put("follow", follow)
            put("isPrivate", isPrivate)
        }
        return authorizedRequest("/v1/follows/toggle", method = "POST", jsonBody = body).map { j ->
            val following = j.optBoolean("following", follow && !isPrivate)
            val requested = j.optBoolean("requested", follow && isPrivate)
            val state = j.optString(
                "relationState",
                when {
                    requested -> "requested"
                    following -> "following"
                    else -> "not_following"
                }
            )
            FollowToggleResult(
                targetUid = j.optString("targetUid", targetUid),
                following = following,
                followersCount = j.optInt("followersCount", 0),
                requested = requested,
                relationState = state
            )
        }
    }

    suspend fun respondFollowRequestWorker(
        fromUid: String,
        accept: Boolean
    ): Result<FollowRequestRespondResult> {
        val body = JSONObject().apply {
            put("fromUid", fromUid)
            put("accept", accept)
        }
        return authorizedRequest("/v1/follows/request/respond", method = "POST", jsonBody = body).map { j ->
            FollowRequestRespondResult(
                ok = j.optBoolean("ok", true),
                accepted = j.optBoolean("accepted", accept),
                followersCount = j.optInt("followersCount", 0)
            )
        }
    }

    suspend fun createInviteWorker(
        code: String = "",
        inviterDisplayName: String = "",
        channel: String = "share"
    ): Result<WorkerInviteCreateResult> {
        val body = JSONObject().apply {
            if (code.isNotBlank()) put("code", code)
            if (inviterDisplayName.isNotBlank()) put("inviterDisplayName", inviterDisplayName)
            put("channel", channel)
        }
        return authorizedRequest("/v1/invites/create", method = "POST", jsonBody = body).map { j ->
            WorkerInviteCreateResult(
                code = j.optString("code"),
                shareUrl = j.optString("shareUrl")
            )
        }
    }

    suspend fun trackInviteWorker(code: String, isSignup: Boolean = false): Result<Unit> {
        val body = JSONObject().apply {
            put("code", code)
            put("event", if (isSignup) "signup" else "click")
        }
        return authorizedRequest("/v1/invites/track", method = "POST", jsonBody = body).map { }
    }

    suspend fun lookupCallerDirectoryWorker(phoneHash: String): Result<WorkerCallerLookupResult> {
        val enc = URLEncoder.encode(phoneHash, "UTF-8")
        return authorizedRequest("/v1/caller/lookup?hash=$enc").map { j ->
            val found = j.optBoolean("found", false)
            val entry = j.optJSONObject("entry")
            if (found && entry != null) {
                WorkerCallerLookupResult(
                    found = true,
                    phoneLookupHash = entry.optString("phoneLookupHash", phoneHash),
                    businessName = entry.optString("businessName"),
                    category = entry.optString("category", "business"),
                    city = entry.optString("city"),
                    countryIso = entry.optString("countryIso"),
                    verified = entry.optBoolean("verified", false)
                )
            } else {
                WorkerCallerLookupResult(found = false, phoneLookupHash = phoneHash)
            }
        }
    }

    suspend fun fetchFollowerCount(targetUid: String): Result<Int> =
        authorizedRequest("/v1/follows/count/$targetUid").map { it.optInt("followersCount", 0) }

    suspend fun fetchCertificationEligibility(): Result<WorkerCertificationEligibility> =
        authorizedRequest("/v1/verification/eligibility").map { j ->
            WorkerCertificationEligibility(
                exemptDesigner = j.optBoolean("exemptDesigner", false),
                eligible = j.optBoolean("eligible", false),
                status = j.optString("status", "none"),
                caseNumber = j.optString("caseNumber", ""),
                followersCount = j.optInt("followersCount", 0),
                requiredFollowers = j.optInt("requiredFollowers", 5000),
                missingFollowers = j.optInt("missingFollowers", 5000),
                feeXof = j.optInt("feeXof", 3600),
                feeUsd = j.optString("feeUsd", "5,99 USD"),
                receiptNumber = j.optString("receiptNumber", ""),
                paymentTxRef = j.optString("paymentTxRef", "")
            )
        }

    suspend fun submitCertificationSelfie(
        category: String,
        faceCount: Int,
        livenessScore: Double,
        sharpnessScore: Double,
        luminance: Double,
        spoofRisk: String,
        termsAccepted: Boolean,
        selfieConsent: Boolean,
        imageBytes: ByteArray
    ): Result<WorkerSelfieSubmitResult> {
        val b64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("category", category)
            put("faceCount", faceCount)
            put("livenessScore", livenessScore)
            put("sharpnessScore", sharpnessScore)
            put("luminance", luminance)
            put("spoofRisk", spoofRisk)
            put("termsAccepted", termsAccepted)
            put("selfieConsent", selfieConsent)
            put("liveCameraOnly", true)
            put("galleryImportBlocked", true)
            put("imageBase64", b64)
        }
        return authorizedRequest("/v1/verification/selfie", method = "POST", jsonBody = body).map { j ->
            WorkerSelfieSubmitResult(
                ok = j.optBoolean("ok", true),
                caseNumber = j.optString("caseNumber", ""),
                status = j.optString("status", "awaiting_payment"),
                selfieKey = j.optString("selfieKey", "")
            )
        }
    }

    suspend fun initiateCertificationPayment(
        idempotencyKey: String,
        currency: String = "XOF",
        provider: String = "brav_mobile_money"
    ): Result<WorkerPaymentInitiateResult> {
        val body = JSONObject().apply {
            put("idempotencyKey", idempotencyKey)
            put("currency", currency)
            put("provider", provider)
        }
        return authorizedRequest("/v1/verification/payment/initiate", method = "POST", jsonBody = body).map { j ->
            WorkerPaymentInitiateResult(
                txRef = j.optString("txRef", ""),
                caseNumber = j.optString("caseNumber", ""),
                amountXof = j.optInt("amountXof", 3600),
                amountUsd = j.optString("amountUsd", "5,99 USD"),
                currency = j.optString("currency", currency),
                status = j.optString("status", "pending")
            )
        }
    }

    suspend fun confirmCertificationPayment(
        txRef: String,
        outcome: String = "confirmed",
        currency: String = "XOF"
    ): Result<WorkerPaymentConfirmResult> {
        val body = JSONObject().apply {
            put("txRef", txRef)
            put("outcome", outcome)
            put("currency", currency)
            put("amountXof", 3600)
            put("amountUsdCents", 599)
        }
        return authorizedRequest("/v1/verification/payment/confirm", method = "POST", jsonBody = body).map { j ->
            val rec = j.optJSONObject("receipt")
            WorkerPaymentConfirmResult(
                ok = j.optBoolean("ok", true),
                status = j.optString("status", "verified"),
                badgeGranted = j.optBoolean("badgeGranted", false),
                followersCount = j.optInt("followersCount", 0),
                receiptNumber = rec?.optString("receiptNumber", "").orEmpty(),
                txRef = rec?.optString("txRef", txRef).orEmpty(),
                caseNumber = rec?.optString("caseNumber", "").orEmpty(),
                amountXof = rec?.optInt("amountXof", 3600) ?: 3600,
                amountUsd = rec?.optString("amountUsd", "5,99 USD").orEmpty(),
                currency = rec?.optString("currency", currency).orEmpty(),
                verifiedAtMs = rec?.optLong("verifiedAtMs", System.currentTimeMillis()) ?: System.currentTimeMillis()
            )
        }
    }

    suspend fun submitAdminVerificationDecision(
        targetUid: String,
        status: String,
        reviewerNote: String
    ): Result<JSONObject> = authorizedRequest(
        "/v1/verification/admin-decision",
        method = "POST",
        jsonBody = JSONObject().apply {
            put("targetUid", targetUid)
            put("status", status)
            put("reviewerNote", reviewerNote)
        }
    )

    suspend fun fetchNotifications(
        category: String = "",
        limit: Int = 60
    ): Result<List<AppNotification>> {
        val qCat = if (category.isNotBlank() && category != "all") "&category=${URLEncoder.encode(category, "UTF-8")}" else ""
        return authorizedRequest("/v1/notifications?limit=$limit$qCat").map { j ->
            val arr = j.optJSONArray("notifications") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { parseAppNotification(it) }
            }
        }
    }

    suspend fun emitNotification(
        targetUid: String,
        category: String,
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
    ): Result<AppNotification?> {
        val payload = JSONObject().apply {
            put("targetUid", targetUid)
            put("category", category)
            put("type", type)
            put("title", title.take(160))
            put("body", body.take(500))
            if (refId.isNotBlank()) put("refId", refId)
            if (refType.isNotBlank()) put("refType", refType)
            if (subRefId.isNotBlank()) put("subRefId", subRefId)
            if (dedupKey.isNotBlank()) put("dedupKey", dedupKey)
            if (senderUid.isNotBlank()) put("senderUid", senderUid)
            if (senderName.isNotBlank()) put("senderName", senderName)
            if (senderAvatar.isNotBlank()) put("senderAvatar", senderAvatar)
            put("priority", priority)
        }
        return authorizedRequest("/v1/notifications/emit", method = "POST", jsonBody = payload).map { j ->
            j.optJSONObject("notification")?.let { parseAppNotification(it) }
        }
    }

    suspend fun markNotificationsRead(
        ids: List<String> = emptyList(),
        refId: String = ""
    ): Result<Unit> {
        val payload = JSONObject().apply {
            if (ids.isNotEmpty()) put("ids", JSONArray(ids))
            if (refId.isNotBlank()) put("refId", refId)
        }
        return authorizedRequest("/v1/notifications/read", method = "POST", jsonBody = payload).map { }
    }

    suspend fun markAllNotificationsRead(category: String = ""): Result<Unit> {
        val payload = JSONObject().apply {
            if (category.isNotBlank() && category != "all") put("category", category)
        }
        return authorizedRequest("/v1/notifications/read-all", method = "POST", jsonBody = payload).map { }
    }

    suspend fun deleteNotification(notifId: String): Result<Unit> {
        if (notifId.isBlank()) return Result.success(Unit)
        val enc = URLEncoder.encode(notifId, "UTF-8")
        return authorizedRequest("/v1/notifications/$enc", method = "DELETE").map { }
    }

    private fun parseAppNotification(o: JSONObject): AppNotification = AppNotification(
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

    private fun jsonArrayToList(arr: JSONArray): List<Map<String, Any?>> {
        val list = mutableListOf<Map<String, Any?>>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val map = mutableMapOf<String, Any?>()
            obj.keys().forEach { k -> map[k] = obj.opt(k) }
            list.add(map)
        }
        return list
    }

    companion object {
        private const val TAG = "WorkerApiClient"
        private const val STRICT_FRENCH_RULE =
            "RÈGLE ABSOLUE ET IMPÉRATIVE : Tu dois OBLIGATOIREMENT répondre uniquement en français. Il est formellement interdit de répondre en anglais ou d'écrire des phrases en anglais."
        private const val MATH_PEDAGOGY_RULE =
            "DIRECTIVE MATHÉMATIQUE PÉDAGOGIQUE : Explique chaque calcul étape par étape comme un professeur clair et patient (Énoncé, Données, Méthode, Résolution détaillée, Résultat final, Interprétation). Pour les vecteurs, présente d'abord les vecteurs et leurs coordonnées avant d'additionner coordonnée par coordonnée. Encadre toutes les formules avec $...$ ou $$...$$ pour le rendu visuel et n'affiche jamais de code LaTeX brut ou de code informatique non expliqué."
    }
}
