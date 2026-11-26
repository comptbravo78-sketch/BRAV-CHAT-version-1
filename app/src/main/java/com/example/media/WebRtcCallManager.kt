package com.example.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.CallAudioRoute
import com.example.model.CallLifecycleState
import com.example.network.WorkerApiClient
import com.example.network.WorkerConstants
import com.example.repository.CallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.ExternalAudioProcessingFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

enum class WebRtcConnectionState {
    IDLE,
    SIGNALING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ENDED,
    FAILED
}

data class WebRtcSessionState(
    val callId: String = "",
    val isVideoCall: Boolean = false,
    val isCaller: Boolean = false,
    val connectionState: WebRtcConnectionState = WebRtcConnectionState.IDLE,
    val lifecycleState: CallLifecycleState = CallLifecycleState.LAUNCHING,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val audioRoute: CallAudioRoute = CallAudioRoute.EARPIECE,
    val availableAudioRoutes: Set<CallAudioRoute> = setOf(CallAudioRoute.EARPIECE, CallAudioRoute.SPEAKER),
    val bluetoothDeviceName: String = "",
    val isOnHold: Boolean = false,
    val isRemoteOnHold: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val hasRemoteVideo: Boolean = false,
    val isVideoSwapped: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val usingRelayTurn: Boolean = false,
    val errorMessage: String? = null
)

class WebRtcCallManager(
    private val context: Context,
    private val workerApiClient: WorkerApiClient,
    private val callRepository: CallRepository? = null
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val eglBase: EglBase by lazy { EglBase.create() }
    val voiceAudioProcessor = VoiceAudioProcessor()
    val androidAudioEffects = AndroidAudioEffectsModule(appContext)
    val audioEffectsStatus: StateFlow<AudioEffectsStatus> = androidAudioEffects.status
    private var externalApm: ExternalAudioProcessingFactory? = null
    private var processingConfig = VoiceProcessingConfig()
    private var configuredHardwareAec: Boolean? = null
    private var configuredHardwareNs: Boolean? = null

    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var signalSocket: WebSocket? = null
    private var firestoreSignalJob: Job? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private val initializedRenderers = java.util.Collections.newSetFromMap(java.util.WeakHashMap<SurfaceViewRenderer, Boolean>())

    private val iceLock = Any()
    private val pendingRemoteIceCandidates = mutableListOf<IceCandidate>()
    private val processedSignalIds = mutableSetOf<String>()
    private val processedSignalContentHashes = mutableSetOf<String>()
    private var peerAccepted = false
    private var currentCallId: String = ""
    private var currentUserUid: String = ""
    private var hasCreatedOffer = false
    private var hasSetRemoteOffer = false
    private var timerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var telemetryJob: Job? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL

    private val _session = MutableStateFlow(WebRtcSessionState())
    val session: StateFlow<WebRtcSessionState> = _session.asStateFlow()

    private val _audioTelemetry = MutableStateFlow(
        VoiceAudioTelemetry(
            hardwareNsSupported = runCatching { JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported() }.getOrDefault(false),
            hardwareAecSupported = runCatching { JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported() }.getOrDefault(false),
            hardwareAgcSupported = androidAudioEffects.status.value.hwAgcAvailable
        )
    )
    val audioTelemetry: StateFlow<VoiceAudioTelemetry> = _audioTelemetry.asStateFlow()

    fun updateProcessingConfig(config: VoiceProcessingConfig) {
        processingConfig = config
        voiceAudioProcessor.updateConfig(config)
        androidAudioEffects.applyConfig(config)
        runCatching {
            externalApm?.setBypassFlagForCapturePost(
                !config.noiseSuppressionEnabled && !config.voicePriorityEnabled && !config.echoCancellationEnabled
            )
            externalApm?.setBypassFlagForRenderPre(!config.echoCancellationEnabled)
        }
        runCatching {
            val hwNs = config.noiseSuppressionEnabled && JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()
            audioDeviceModule?.setNoiseSuppressorEnabled(hwNs)
        }
        syncCombinedTelemetry()
    }

    private fun syncCombinedTelemetry() {
        val cfg = processingConfig
        val capturePostActive = externalApm != null &&
            (cfg.noiseSuppressionEnabled || cfg.voicePriorityEnabled || cfg.echoCancellationEnabled)
        val renderPreActive = externalApm != null && cfg.echoCancellationEnabled
        val frames = voiceAudioProcessor.processedCaptureFrames()
        androidAudioEffects.updatePipelineVerification(
            capturePostProcessingActive = capturePostActive,
            renderPreProcessingActive = renderPreActive,
            transmittedFramesProcessed = frames
        )
        val effects = androidAudioEffects.status.value
        val hwNsSupported = runCatching { JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported() }.getOrDefault(effects.hwNsAvailable)
        val hwAecSupported = runCatching { JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported() }.getOrDefault(effects.hwAecAvailable)
        _audioTelemetry.value = voiceAudioProcessor.currentTelemetry().copy(
            hardwareNsSupported = hwNsSupported || effects.hwNsAvailable,
            hardwareAecSupported = hwAecSupported || effects.hwAecAvailable,
            hardwareAgcSupported = effects.hwAgcAvailable,
            hwAecEnabled = effects.hwAecEnabled,
            hwNsEnabled = effects.hwNsEnabled,
            hwAgcEnabled = effects.hwAgcEnabled,
            captureSessionId = effects.captureSessionId,
            webrtcCaptureActive = effects.webrtcCaptureActive,
            processedCaptureFrames = frames
        )
    }

    fun attachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        localRenderer = local
        remoteRenderer = remote
        local?.let { renderer ->
            runCatching {
                if (initializedRenderers.add(renderer)) {
                    renderer.init(eglBase.eglBaseContext, null)
                }
            }
        }
        remote?.let { renderer ->
            runCatching {
                if (initializedRenderers.add(renderer)) {
                    renderer.init(eglBase.eglBaseContext, null)
                }
            }
        }
        rebindVideoRenderers()
    }

    fun setVideoSwapped(swapped: Boolean) {
        scope.launch {
            if (_session.value.isVideoSwapped != swapped) {
                _session.value = _session.value.copy(isVideoSwapped = swapped)
                rebindVideoRenderers()
            }
        }
    }

    private fun rebindVideoRenderers() {
        val pipRenderer = localRenderer
        val mainRenderer = remoteRenderer
        pipRenderer?.let {
            runCatching { localVideoTrack?.removeSink(it) }
            runCatching { remoteVideoTrack?.removeSink(it) }
        }
        mainRenderer?.let {
            runCatching { localVideoTrack?.removeSink(it) }
            runCatching { remoteVideoTrack?.removeSink(it) }
        }

        val swapped = _session.value.isVideoSwapped
        val mirrorLocal = _session.value.isFrontCamera
        if (swapped) {
            mainRenderer?.let { r ->
                runCatching {
                    r.setMirror(mirrorLocal)
                    localVideoTrack?.addSink(r)
                }
            }
            pipRenderer?.let { r ->
                runCatching {
                    r.setMirror(false)
                    remoteVideoTrack?.addSink(r)
                }
            }
        } else {
            pipRenderer?.let { r ->
                runCatching {
                    r.setMirror(mirrorLocal)
                    localVideoTrack?.addSink(r)
                }
            }
            mainRenderer?.let { r ->
                runCatching {
                    r.setMirror(false)
                    remoteVideoTrack?.addSink(r)
                }
            }
        }
    }

    fun detachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        local?.let { r ->
            runCatching {
                localVideoTrack?.removeSink(r)
                remoteVideoTrack?.removeSink(r)
                initializedRenderers.remove(r)
                r.release()
            }
        }
        remote?.let { r ->
            runCatching {
                localVideoTrack?.removeSink(r)
                remoteVideoTrack?.removeSink(r)
                initializedRenderers.remove(r)
                r.release()
            }
        }
        if (localRenderer === local) localRenderer = null
        if (remoteRenderer === remote) remoteRenderer = null
    }

    fun startSession(
        callId: String,
        isCaller: Boolean,
        isVideo: Boolean,
        myUid: String = "",
        processingConfig: VoiceProcessingConfig = this.processingConfig,
        defaultSpeakerOn: Boolean? = null,
        startMuted: Boolean = false,
        onRemoteHangup: () -> Unit = {}
    ) {
        endSession(sendHangup = false)
        updateProcessingConfig(processingConfig)
        hasCreatedOffer = false
        hasSetRemoteOffer = false
        peerAccepted = !isCaller
        currentCallId = callId
        currentUserUid = myUid
        processedSignalIds.clear()
        processedSignalContentHashes.clear()
        synchronized(iceLock) { pendingRemoteIceCandidates.clear() }

        val effectiveSpeakerOn = defaultSpeakerOn ?: isVideo
        val initialRoute = if (effectiveSpeakerOn) CallAudioRoute.SPEAKER else CallAudioRoute.EARPIECE
        _session.value = WebRtcSessionState(
            callId = callId,
            isVideoCall = isVideo,
            isCaller = isCaller,
            connectionState = WebRtcConnectionState.SIGNALING,
            lifecycleState = if (isCaller) CallLifecycleState.LAUNCHING else CallLifecycleState.ACCEPTED,
            isMuted = startMuted,
            isSpeakerOn = effectiveSpeakerOn,
            audioRoute = initialRoute,
            isCameraEnabled = isVideo
        )

        configureAudioRouting(route = initialRoute)
        androidAudioEffects.startCaptureBinding(processingConfig)
        startTelemetryPolling()

        scope.launch {
            val workerConfig = withTimeoutOrNull(2500L) { workerApiClient.getOrFetchConfig() }
            val turnResult = withTimeoutOrNull(2500L) { workerApiClient.fetchTurnCredentials() }
            val turnData = turnResult?.getOrNull()
            val iceServers = mutableListOf<PeerConnection.IceServer>()
            if (turnData != null && turnData.iceServers.isNotEmpty()) {
                turnData.iceServers.forEach { spec ->
                    val builder = PeerConnection.IceServer.builder(spec.urls)
                    if (!spec.username.isNullOrBlank()) builder.setUsername(spec.username)
                    if (!spec.credential.isNullOrBlank()) builder.setPassword(spec.credential)
                    iceServers.add(builder.createIceServer())
                }
            } else {
                iceServers.add(
                    PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
                )
                iceServers.add(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
                )
            }

            _session.value = _session.value.copy(usingRelayTurn = turnData?.relay == true)
            initFactoryAndPeerConnection(iceServers, isVideo)

            // Always attach Firestore persistent signaling so zero SDP/ICE messages are dropped
            activateFirestoreSignaling(callId, isCaller, onRemoteHangup)

            // Also attach Cloudflare CallRoom WebSocket for sub-50ms signaling when available
            if (workerConfig?.features?.callSignaling == true) {
                connectSignalingWebSocket(callId, isCaller, onRemoteHangup)
            }

            if (isCaller && peerAccepted && !hasCreatedOffer) {
                createAndSendOffer(iceRestart = false)
            }
        }
    }

    fun notifyPeerAccepted() {
        scope.launch {
            peerAccepted = true
            if (_session.value.isCaller && !hasCreatedOffer && peerConnection != null) {
                createAndSendOffer(iceRestart = false)
            }
        }
    }

    private fun initFactoryAndPeerConnection(
        iceServers: List<PeerConnection.IceServer>,
        isVideo: Boolean
    ) {
        val wantHwAec = processingConfig.echoCancellationEnabled &&
            runCatching { JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported() }.getOrDefault(false)
        val wantHwNs = processingConfig.noiseSuppressionEnabled &&
            runCatching { JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported() }.getOrDefault(false)

        if (peerConnectionFactory != null && (configuredHardwareAec != wantHwAec || configuredHardwareNs != wantHwNs)) {
            runCatching { peerConnectionFactory?.dispose() }
            peerConnectionFactory = null
            runCatching { audioDeviceModule?.release() }
            audioDeviceModule = null
            runCatching { externalApm?.destroy() }
            externalApm = null
        }

        if (peerConnectionFactory == null) {
            if (!factoryInitialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
                factoryInitialized = true
            }
            val voiceAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val adm = JavaAudioDeviceModule.builder(appContext)
                .setAudioAttributes(voiceAttributes)
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setUseHardwareAcousticEchoCanceler(wantHwAec)
                .setUseHardwareNoiseSuppressor(wantHwNs)
                .setAudioRecordStateCallback(object : JavaAudioDeviceModule.AudioRecordStateCallback {
                    override fun onWebRtcAudioRecordStart() {
                        scope.launch { androidAudioEffects.onWebRtcAudioRecordStart() }
                    }
                    override fun onWebRtcAudioRecordStop() {
                        scope.launch { androidAudioEffects.onWebRtcAudioRecordStop() }
                    }
                })
                .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                    override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                        Log.w(TAG, "AudioRecord init error: $errorMessage")
                    }
                    override fun onWebRtcAudioRecordStartError(
                        errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
                        errorMessage: String?
                    ) {
                        Log.w(TAG, "AudioRecord start error ($errorCode): $errorMessage")
                    }
                    override fun onWebRtcAudioRecordError(errorMessage: String?) {
                        Log.w(TAG, "AudioRecord error: $errorMessage")
                    }
                })
                .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                    override fun onWebRtcAudioTrackInitError(errorMessage: String?) {
                        Log.w(TAG, "AudioTrack init error: $errorMessage")
                    }
                    override fun onWebRtcAudioTrackStartError(
                        errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode?,
                        errorMessage: String?
                    ) {
                        Log.w(TAG, "AudioTrack start error ($errorCode): $errorMessage")
                    }
                    override fun onWebRtcAudioTrackError(errorMessage: String?) {
                        Log.w(TAG, "AudioTrack error: $errorMessage")
                    }
                })
                .createAudioDeviceModule()
            audioDeviceModule = adm
            configuredHardwareAec = wantHwAec
            configuredHardwareNs = wantHwNs

            val apm = ExternalAudioProcessingFactory().apply {
                setCapturePostProcessing(voiceAudioProcessor)
                setRenderPreProcessing(voiceAudioProcessor.renderPreProcessor)
                setBypassFlagForCapturePost(
                    !processingConfig.noiseSuppressionEnabled &&
                        !processingConfig.voicePriorityEnabled &&
                        !processingConfig.echoCancellationEnabled
                )
                setBypassFlagForRenderPre(!processingConfig.echoCancellationEnabled)
            }
            externalApm = apm

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setAudioProcessingFactory(apm)
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
                .createPeerConnectionFactory()
        }
        val effectiveHeld = _session.value.isOnHold || _session.value.isRemoteOnHold
        audioDeviceModule?.setSpeakerMute(effectiveHeld)
        audioDeviceModule?.setMicrophoneMute(_session.value.isMuted || effectiveHeld)

        val factory = peerConnectionFactory ?: return
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                scope.launch {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            _session.value = _session.value.copy(
                                connectionState = WebRtcConnectionState.CONNECTED,
                                lifecycleState = if (_session.value.isOnHold) CallLifecycleState.ON_HOLD else CallLifecycleState.ACTIVE
                            )
                            ensureActiveCallAudio()
                            startTimerIfNeeded()
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            _session.value = _session.value.copy(
                                connectionState = WebRtcConnectionState.RECONNECTING,
                                lifecycleState = CallLifecycleState.CONNECTION_LOST
                            )
                            if (_session.value.isCaller) {
                                triggerIceRestart()
                            }
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            _session.value = _session.value.copy(
                                connectionState = WebRtcConnectionState.FAILED,
                                lifecycleState = CallLifecycleState.FAILED,
                                errorMessage = "Connexion média interrompue"
                            )
                        }
                        PeerConnection.IceConnectionState.CLOSED -> {
                            _session.value = _session.value.copy(
                                connectionState = WebRtcConnectionState.ENDED,
                                lifecycleState = CallLifecycleState.ENDED
                            )
                        }
                        else -> Unit
                    }
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                scope.launch {
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            _session.value = _session.value.copy(
                                connectionState = WebRtcConnectionState.CONNECTED,
                                lifecycleState = if (_session.value.isOnHold) CallLifecycleState.ON_HOLD else CallLifecycleState.ACTIVE
                            )
                            ensureActiveCallAudio()
                            startTimerIfNeeded()
                        }
                        PeerConnection.PeerConnectionState.DISCONNECTED -> {
                            _session.value = _session.value.copy(
                                connectionState = WebRtcConnectionState.RECONNECTING,
                                lifecycleState = CallLifecycleState.CONNECTION_LOST
                            )
                        }
                        PeerConnection.PeerConnectionState.FAILED -> {
                            _session.value = _session.value.copy(
                                connectionState = WebRtcConnectionState.FAILED,
                                lifecycleState = CallLifecycleState.FAILED,
                                errorMessage = "Connexion média interrompue"
                            )
                        }
                        PeerConnection.PeerConnectionState.CLOSED -> {
                            _session.value = _session.value.copy(
                                connectionState = WebRtcConnectionState.ENDED,
                                lifecycleState = CallLifecycleState.ENDED
                            )
                        }
                        else -> Unit
                    }
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                val payload = JSONObject().apply {
                    put("type", "ice-candidate")
                    put("candidate", JSONObject().apply {
                        put("sdpMid", candidate.sdpMid)
                        put("sdpMLineIndex", candidate.sdpMLineIndex)
                        put("candidate", candidate.sdp)
                    })
                }
                sendSignalPayload("ice-candidate", payload)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                stream?.audioTracks?.firstOrNull()?.let { attachRemoteAudio(it) }
                val track = stream?.videoTracks?.firstOrNull()
                if (track != null) attachRemoteVideo(track)
            }
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                when (track) {
                    is AudioTrack -> attachRemoteAudio(track)
                    is VideoTrack -> attachRemoteVideo(track)
                }
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                when (track) {
                    is AudioTrack -> attachRemoteAudio(track)
                    is VideoTrack -> attachRemoteVideo(track)
                }
            }
        })

        attachLocalTracksIfPermitted(isVideo)
    }

    fun refreshLocalMediaAfterPermissions() {
        val current = _session.value
        if (current.connectionState == WebRtcConnectionState.IDLE || current.connectionState == WebRtcConnectionState.ENDED) return
        val added = attachLocalTracksIfPermitted(current.isVideoCall)
        configureAudioRouting(current.isSpeakerOn)
        audioDeviceModule?.setMicrophoneMute(current.isMuted)
        if (added && current.isCaller && hasCreatedOffer) {
            createAndSendOffer(iceRestart = false)
        }
    }

    private fun attachLocalTracksIfPermitted(isVideo: Boolean): Boolean {
        val factory = peerConnectionFactory ?: return false
        val pc = peerConnection ?: return false
        var addedTrack = false

        val hasAudioPerm = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasAudioPerm && localAudioTrack == null) {
            runCatching {
                val cfg = processingConfig
                val audioConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", cfg.echoCancellationEnabled.toString()))
                    mandatory.add(MediaConstraints.KeyValuePair("googDAEchoCancellation", cfg.echoCancellationEnabled.toString()))
                    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", cfg.noiseSuppressionEnabled.toString()))
                    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", cfg.noiseSuppressionEnabled.toString()))
                    mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", (cfg.noiseSuppressionEnabled || cfg.voicePriorityEnabled).toString()))
                    mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", (cfg.autoGainControlEnabled || cfg.voicePriorityEnabled).toString()))
                    mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", cfg.noiseSuppressionEnabled.toString()))
                }
                audioSource = factory.createAudioSource(audioConstraints)
                localAudioTrack = factory.createAudioTrack("brav_audio_track", audioSource).apply {
                    val effectiveHeld = _session.value.isOnHold || _session.value.isRemoteOnHold
                    setEnabled(!_session.value.isMuted && !effectiveHeld)
                }
                pc.addTrack(localAudioTrack, listOf("brav_stream"))
                addedTrack = true
            }.onFailure { Log.w(TAG, "Audio track init skipped: ${it.message}") }
        }

        val hasCameraPerm = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (isVideo && hasCameraPerm && localVideoTrack == null) {
            runCatching {
                val capturer = createCameraCapturer(front = _session.value.isFrontCamera)
                if (capturer != null) {
                    videoCapturer = capturer
                    surfaceTextureHelper = SurfaceTextureHelper.create("BravCaptureThread", eglBase.eglBaseContext)
                    videoSource = factory.createVideoSource(capturer.isScreencast)
                    capturer.initialize(surfaceTextureHelper, appContext, videoSource?.capturerObserver)
                    capturer.startCapture(720, 1280, 24)
                    localVideoTrack = factory.createVideoTrack("brav_video_track", videoSource).apply {
                        setEnabled(_session.value.isCameraEnabled)
                    }
                    rebindVideoRenderers()
                    pc.addTrack(localVideoTrack, listOf("brav_stream"))
                    addedTrack = true
                }
            }.onFailure { Log.w(TAG, "Video track init skipped: ${it.message}") }
        }
        return addedTrack
    }

    private fun attachRemoteVideo(track: VideoTrack) {
        scope.launch {
            remoteVideoTrack = track
            track.setEnabled(true)
            _session.value = _session.value.copy(hasRemoteVideo = true)
            rebindVideoRenderers()
        }
    }

    private fun attachRemoteAudio(track: AudioTrack) {
        scope.launch {
            remoteAudioTrack = track
            runCatching {
                track.setEnabled(true)
                track.setVolume(1.0)
            }
            ensureActiveCallAudio()
        }
    }

    private fun ensureActiveCallAudio() {
        val current = _session.value
        val effectiveHold = current.isOnHold || current.isRemoteOnHold
        configureAudioRouting(speakerOn = current.isSpeakerOn)
        audioDeviceModule?.setSpeakerMute(effectiveHold)
        audioDeviceModule?.setMicrophoneMute(current.isMuted || effectiveHold)
        runCatching { localAudioTrack?.setEnabled(!current.isMuted && !effectiveHold) }
        runCatching { localVideoTrack?.setEnabled(current.isCameraEnabled && !effectiveHold) }
        runCatching {
            remoteAudioTrack?.setEnabled(!effectiveHold)
            remoteAudioTrack?.setVolume(if (effectiveHold) 0.0 else 1.0)
        }
    }

    private fun sendSignalPayload(type: String, payload: JSONObject) {
        val raw = payload.toString()
        // Send over CallRoom WebSocket for low-latency delivery when open
        runCatching { signalSocket?.send(raw) }
        // Mirror onto Firestore /calls/{callId}/signals so signals are never lost before peer joins
        val repo = callRepository
        val callId = currentCallId
        val uid = currentUserUid
        if (repo != null && callId.isNotBlank() && uid.isNotBlank()) {
            scope.launch {
                repo.sendCallSignal(callId, uid, type, raw)
            }
        }
    }

    private fun activateFirestoreSignaling(
        callId: String,
        isCaller: Boolean,
        onRemoteHangup: () -> Unit
    ) {
        val repo = callRepository ?: return
        if (currentUserUid.isBlank()) return
        firestoreSignalJob?.cancel()
        firestoreSignalJob = scope.launch {
            repo.observeCallSignals(callId, currentUserUid)
                .catch { Log.w(TAG, "Firestore signaling error: ${it.message}") }
                .collect { packets ->
                    for (pkt in packets) {
                        if (processedSignalIds.add(pkt.id)) {
                            handleSignalMessage(pkt.payload, isCaller, onRemoteHangup)
                        }
                    }
                }
        }
    }

    private fun connectSignalingWebSocket(
        callId: String,
        isCaller: Boolean,
        onRemoteHangup: () -> Unit,
        forceTokenRefresh: Boolean = false
    ) {
        scope.launch(Dispatchers.IO) {
            val token = workerApiClient.getFreshIdToken(forceTokenRefresh)
            if (token.isNullOrBlank()) return@launch
            val url = "${WorkerConstants.WS_BASE_URL}/v1/calls/$callId/signal"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Sec-WebSocket-Protocol", WorkerConstants.WS_PROTOCOL)
                .build()

            signalSocket = workerApiClient.httpClient.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (signalSocket !== webSocket) return
                    startHeartbeat()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (signalSocket !== webSocket) return
                    if (text == "pong") return
                    scope.launch {
                        handleSignalMessage(text, isCaller, onRemoteHangup)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (signalSocket !== webSocket) return
                    heartbeatJob?.cancel()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (signalSocket !== webSocket) return
                    Log.w(TAG, "Signal WS failed (${response?.code}): ${t.message}")
                    if (response?.code == 401 && !forceTokenRefresh) {
                        connectSignalingWebSocket(callId, isCaller, onRemoteHangup, forceTokenRefresh = true)
                    }
                }
            })
        }
    }

    private fun handleSignalMessage(
        raw: String,
        isCaller: Boolean,
        onRemoteHangup: () -> Unit
    ) {
        try {
            val j = JSONObject(raw)
            val msgType = j.optString("type")
            // Deduplicate signals arriving over both WebSocket and Firestore
            val dedupKey = when (msgType) {
                "offer", "answer", "ice-candidate" -> "$msgType:${raw.hashCode()}"
                else -> null
            }
            if (dedupKey != null && !processedSignalContentHashes.add(dedupKey)) {
                return
            }

            when (msgType) {
                "room-ready" -> {
                    val peers = j.optInt("peers", 1)
                    if (isCaller && peers >= 2 && !hasCreatedOffer) {
                        peerAccepted = true
                        createAndSendOffer(iceRestart = false)
                    }
                }
                "peer-joined" -> {
                    if (isCaller && !hasCreatedOffer) {
                        peerAccepted = true
                        createAndSendOffer(iceRestart = false)
                    }
                }
                "offer" -> {
                    val sdpObj = j.optJSONObject("sdp")
                    val sdpStr = sdpObj?.optString("sdp") ?: j.optString("sdp")
                    if (sdpStr.isNotBlank() && !isCaller) {
                        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdpStr)
                        _session.value = _session.value.copy(connectionState = WebRtcConnectionState.CONNECTING)
                        peerConnection?.setRemoteDescription(SimpleSdpObserver("setRemoteOffer") {
                            scope.launch {
                                hasSetRemoteOffer = true
                                flushPendingIceCandidates()
                                createAndSendAnswer()
                            }
                        }, remoteSdp)
                    }
                }
                "answer" -> {
                    val sdpObj = j.optJSONObject("sdp")
                    val sdpStr = sdpObj?.optString("sdp") ?: j.optString("sdp")
                    if (sdpStr.isNotBlank() && isCaller) {
                        val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
                        _session.value = _session.value.copy(connectionState = WebRtcConnectionState.CONNECTING)
                        peerConnection?.setRemoteDescription(SimpleSdpObserver("setRemoteAnswer") {
                            scope.launch { flushPendingIceCandidates() }
                        }, remoteSdp)
                    }
                }
                "ice-candidate" -> {
                    val cObj = j.optJSONObject("candidate") ?: j
                    val sdpMid = cObj.optString("sdpMid")
                    val sdpMLineIndex = cObj.optInt("sdpMLineIndex", 0)
                    val sdp = cObj.optString("candidate")
                    if (sdp.isNotBlank()) {
                        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                        val pc = peerConnection
                        if (pc?.remoteDescription != null) {
                            pc.addIceCandidate(candidate)
                        } else {
                            synchronized(iceLock) { pendingRemoteIceCandidates.add(candidate) }
                        }
                    }
                }
                "hold" -> {
                    setRemoteHold(true)
                }
                "resume" -> {
                    setRemoteHold(false)
                }
                "hangup", "peer-left", "ended", "rejected" -> {
                    _session.value = _session.value.copy(connectionState = WebRtcConnectionState.ENDED)
                    onRemoteHangup()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Signal parse error: ${e.message}")
        }
    }

    private fun createAndSendOffer(iceRestart: Boolean) {
        val pc = peerConnection ?: return
        hasCreatedOffer = true
        _session.value = _session.value.copy(connectionState = WebRtcConnectionState.CONNECTING)
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (_session.value.isVideoCall) "true" else "false"))
            if (iceRestart) mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc ?: return
                pc.setLocalDescription(SimpleSdpObserver("setLocalOffer") {
                    val payload = JSONObject().apply {
                        put("type", "offer")
                        put("sdp", JSONObject().apply {
                            put("type", "offer")
                            put("sdp", desc.description)
                        })
                    }
                    sendSignalPayload("offer", payload)
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) { Log.w(TAG, "createOffer err: $err") }
            override fun onSetFailure(err: String?) {}
        }, constraints)
    }

    private fun createAndSendAnswer() {
        val pc = peerConnection ?: return
        attachLocalTracksIfPermitted(_session.value.isVideoCall)
        configureAudioRouting(_session.value.isSpeakerOn)
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (_session.value.isVideoCall) "true" else "false"))
        }
        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc ?: return
                pc.setLocalDescription(SimpleSdpObserver("setLocalAnswer") {
                    val payload = JSONObject().apply {
                        put("type", "answer")
                        put("sdp", JSONObject().apply {
                            put("type", "answer")
                            put("sdp", desc.description)
                        })
                    }
                    sendSignalPayload("answer", payload)
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) { Log.w(TAG, "createAnswer err: $err") }
            override fun onSetFailure(err: String?) {}
        }, constraints)
    }

    private fun triggerIceRestart() {
        if (peerConnection != null && hasCreatedOffer) {
            createAndSendOffer(iceRestart = true)
        }
    }

    private fun flushPendingIceCandidates() {
        val pc = peerConnection ?: return
        val toAdd = synchronized(iceLock) {
            val copy = pendingRemoteIceCandidates.toList()
            pendingRemoteIceCandidates.clear()
            copy
        }
        toAdd.forEach { pc.addIceCandidate(it) }
    }

    fun toggleMute(): Boolean {
        val next = !_session.value.isMuted
        _session.value = _session.value.copy(isMuted = next)
        ensureActiveCallAudio()
        return next
    }

    fun toggleSpeaker(): Boolean {
        val cur = _session.value
        val nextRoute = if (cur.audioRoute == CallAudioRoute.SPEAKER) {
            if (CallAudioRoute.BLUETOOTH in cur.availableAudioRoutes) CallAudioRoute.BLUETOOTH
            else CallAudioRoute.EARPIECE
        } else {
            CallAudioRoute.SPEAKER
        }
        selectAudioRoute(nextRoute)
        return nextRoute == CallAudioRoute.SPEAKER
    }

    fun selectAudioRoute(route: CallAudioRoute) {
        configureAudioRouting(route = route)
        _session.value = _session.value.copy(
            audioRoute = route,
            isSpeakerOn = route == CallAudioRoute.SPEAKER
        )
    }

    fun setHold(held: Boolean) {
        if (_session.value.isOnHold == held) return
        val nextLifecycle = when {
            held -> CallLifecycleState.ON_HOLD
            _session.value.connectionState == WebRtcConnectionState.CONNECTED -> CallLifecycleState.ACTIVE
            else -> CallLifecycleState.ACCEPTED
        }
        _session.value = _session.value.copy(
            isOnHold = held,
            lifecycleState = nextLifecycle
        )
        ensureActiveCallAudio()
        val type = if (held) "hold" else "resume"
        sendSignalPayload(type, JSONObject().put("type", type))
    }

    fun setRemoteHold(held: Boolean) {
        if (_session.value.isRemoteOnHold == held) return
        _session.value = _session.value.copy(isRemoteOnHold = held)
        ensureActiveCallAudio()
    }

    fun toggleCamera(): Boolean {
        val next = !_session.value.isCameraEnabled
        _session.value = _session.value.copy(isCameraEnabled = next)
        ensureActiveCallAudio()
        return next
    }

    fun switchCamera() {
        val capturer = videoCapturer ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                scope.launch {
                    _session.value = _session.value.copy(isFrontCamera = isFrontCamera)
                    rebindVideoRenderers()
                }
            }
            override fun onCameraSwitchError(errorDescription: String?) {}
        })
    }

    fun endSession(sendHangup: Boolean = true) {
        timerJob?.cancel()
        telemetryJob?.cancel()
        heartbeatJob?.cancel()
        firestoreSignalJob?.cancel()
        if (sendHangup) {
            runCatching {
                sendSignalPayload("hangup", JSONObject().put("type", "hangup"))
            }
        }
        runCatching { signalSocket?.close(1000, "call_ended") }
        signalSocket = null

        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null

        runCatching { localVideoTrack?.dispose() }
        localVideoTrack = null
        remoteVideoTrack = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null

        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        remoteAudioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        runCatching {
            audioDeviceModule?.setMicrophoneMute(false)
            audioDeviceModule?.setSpeakerMute(false)
        }

        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null

        androidAudioEffects.release()
        restoreAudioRouting()
        _session.value = WebRtcSessionState(connectionState = WebRtcConnectionState.IDLE)
    }

    private fun startTimerIfNeeded() {
        if (timerJob?.isActive == true) return
        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val cur = _session.value
                if (!cur.isOnHold && !cur.isRemoteOnHold) {
                    _session.value = cur.copy(elapsedSeconds = cur.elapsedSeconds + 1L)
                }
            }
        }
    }

    private fun startTelemetryPolling() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive) {
                syncCombinedTelemetry()
                delay(240L)
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(25_000L)
                signalSocket?.send("ping")
            }
        }
    }

    private fun createCameraCapturer(front: Boolean): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(appContext)
        val devices = enumerator.deviceNames
        val preferred = devices.firstOrNull { name ->
            if (front) enumerator.isFrontFacing(name) else enumerator.isBackFacing(name)
        } ?: devices.firstOrNull() ?: return null
        return enumerator.createCapturer(preferred, null)
    }

    private fun hasBluetoothAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun configureAudioRouting(speakerOn: Boolean) {
        configureAudioRouting(if (speakerOn) CallAudioRoute.SPEAKER else CallAudioRoute.EARPIECE)
    }

    private fun configureAudioRouting(route: CallAudioRoute) {
        val am = (appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager) ?: return
        if (audioManager == null) {
            audioManager = am
            previousAudioMode = am.mode
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest == null) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            audioFocusRequest = req
            runCatching { am.requestAudioFocus(req) }
        }
        runCatching { am.mode = AudioManager.MODE_IN_COMMUNICATION }
        runCatching { am.isMicrophoneMute = false }

        val routes = linkedSetOf(CallAudioRoute.EARPIECE, CallAudioRoute.SPEAKER)
        var btName = ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val devices = am.availableCommunicationDevices
                val btDevice = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
                if (btDevice != null) {
                    routes.add(CallAudioRoute.BLUETOOTH)
                    if (hasBluetoothAudioPermission()) {
                        btName = btDevice.productName?.toString()?.trim().orEmpty()
                    }
                }
                when (route) {
                    CallAudioRoute.BLUETOOTH -> {
                        if (btDevice != null) {
                            am.setCommunicationDevice(btDevice)
                        }
                    }
                    CallAudioRoute.SPEAKER -> {
                        val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        if (speaker != null) {
                            am.setCommunicationDevice(speaker)
                        }
                    }
                    CallAudioRoute.EARPIECE -> {
                        val earpiece = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
                            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
                            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
                            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                        if (earpiece != null) {
                            am.setCommunicationDevice(earpiece)
                        } else {
                            am.clearCommunicationDevice()
                        }
                    }
                }
            }
        } else {
            val outputDevices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            @Suppress("DEPRECATION")
            val btLegacy = outputDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
            if (btLegacy != null) {
                routes.add(CallAudioRoute.BLUETOOTH)
                if (hasBluetoothAudioPermission()) {
                    btName = btLegacy.productName?.toString()?.trim().orEmpty()
                }
            }
            @Suppress("DEPRECATION")
            when (route) {
                CallAudioRoute.BLUETOOTH -> {
                    runCatching {
                        am.isSpeakerphoneOn = false
                        if (hasBluetoothAudioPermission()) {
                            am.startBluetoothSco()
                            am.isBluetoothScoOn = true
                        }
                    }
                }
                CallAudioRoute.SPEAKER -> {
                    runCatching {
                        if (am.isBluetoothScoOn) {
                            am.isBluetoothScoOn = false
                            am.stopBluetoothSco()
                        }
                        am.isSpeakerphoneOn = true
                    }
                }
                CallAudioRoute.EARPIECE -> {
                    runCatching {
                        if (am.isBluetoothScoOn) {
                            am.isBluetoothScoOn = false
                            am.stopBluetoothSco()
                        }
                        am.isSpeakerphoneOn = false
                    }
                }
            }
        }

        _session.value = _session.value.copy(
            audioRoute = route,
            isSpeakerOn = route == CallAudioRoute.SPEAKER,
            availableAudioRoutes = routes,
            bluetoothDeviceName = btName
        )
    }

    private fun restoreAudioRouting() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { am.clearCommunicationDevice() }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { req ->
                runCatching { am.abandonAudioFocusRequest(req) }
            }
            audioFocusRequest = null
        }
        @Suppress("DEPRECATION")
        runCatching {
            if (am.isBluetoothScoOn) {
                am.isBluetoothScoOn = false
                am.stopBluetoothSco()
            }
            am.isSpeakerphoneOn = false
        }
        runCatching { am.isMicrophoneMute = false }
        runCatching { am.mode = previousAudioMode }
        audioManager = null
    }

    private class SimpleSdpObserver(
        private val tag: String,
        private val onSuccess: () -> Unit
    ) : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() { onSuccess() }
        override fun onCreateFailure(err: String?) { Log.w(TAG, "$tag create fail: $err") }
        override fun onSetFailure(err: String?) { Log.w(TAG, "$tag set fail: $err") }
    }

    companion object {
        private const val TAG = "WebRtcCallManager"
        @Volatile
        private var factoryInitialized = false
    }
}
