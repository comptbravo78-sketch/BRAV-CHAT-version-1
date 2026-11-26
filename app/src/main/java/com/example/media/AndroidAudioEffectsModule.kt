package com.example.media

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Snapshot of active Android OS audio effects and WebRTC capture/render processing verification.
 */
data class AudioEffectsStatus(
    val hwAecAvailable: Boolean = false,
    val hwNsAvailable: Boolean = false,
    val hwAgcAvailable: Boolean = false,
    val hwAecAttached: Boolean = false,
    val hwAecEnabled: Boolean = false,
    val hwNsAttached: Boolean = false,
    val hwNsEnabled: Boolean = false,
    val hwAgcAttached: Boolean = false,
    val hwAgcEnabled: Boolean = false,
    val captureSessionId: Int = -1,
    val webrtcCaptureActive: Boolean = false,
    val osDescriptorEffects: List<String> = emptyList(),
    val capturePostProcessingActive: Boolean = false,
    val transmittedFramesProcessed: Long = 0L,
    val renderPreProcessingActive: Boolean = false,
    val echoCancellationConfigured: Boolean = true,
    val noiseSuppressionConfigured: Boolean = true
) {
    val isAecEffective: Boolean
        get() = hwAecEnabled || (echoCancellationConfigured && (capturePostProcessingActive || webrtcCaptureActive))

    val isNsEffective: Boolean
        get() = hwNsEnabled || (noiseSuppressionConfigured && capturePostProcessingActive)
}

/**
 * Lightweight handle abstraction over [android.media.audiofx.AudioEffect] subclasses for clean lifecycle & testing.
 */
interface AudioEffectHandle {
    val isEnabled: Boolean
    val descriptorName: String
    fun setEnabled(enabled: Boolean): Boolean
    fun release()
}

data class ActiveCaptureSessionInfo(
    val sessionId: Int,
    val audioSource: Int,
    val descriptorEffects: List<String> = emptyList()
)

interface AudioEffectsBackend {
    fun isAecAvailable(): Boolean
    fun isNsAvailable(): Boolean
    fun isAgcAvailable(): Boolean
    fun createAec(sessionId: Int): AudioEffectHandle?
    fun createNs(sessionId: Int): AudioEffectHandle?
    fun createAgc(sessionId: Int): AudioEffectHandle?
    fun queryActiveCaptureSession(): ActiveCaptureSessionInfo?
    fun registerRecordingCallback(onChanged: (ActiveCaptureSessionInfo?) -> Unit)
    fun unregisterRecordingCallback()
}

internal class AndroidAudioEffectsBackend(
    context: Context
) : AudioEffectsBackend {

    private val appContext = context.applicationContext
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null

    override fun isAecAvailable(): Boolean =
        runCatching { AcousticEchoCanceler.isAvailable() }.getOrDefault(false)

    override fun isNsAvailable(): Boolean =
        runCatching { NoiseSuppressor.isAvailable() }.getOrDefault(false)

    override fun isAgcAvailable(): Boolean =
        runCatching { AutomaticGainControl.isAvailable() }.getOrDefault(false)

    override fun createAec(sessionId: Int): AudioEffectHandle? {
        if (!isAecAvailable() || sessionId <= 0) return null
        return runCatching {
            AcousticEchoCanceler.create(sessionId)?.let { wrapEffect(it, "AEC") }
        }.getOrNull()
    }

    override fun createNs(sessionId: Int): AudioEffectHandle? {
        if (!isNsAvailable() || sessionId <= 0) return null
        return runCatching {
            NoiseSuppressor.create(sessionId)?.let { wrapEffect(it, "NS") }
        }.getOrNull()
    }

    override fun createAgc(sessionId: Int): AudioEffectHandle? {
        if (!isAgcAvailable() || sessionId <= 0) return null
        return runCatching {
            AutomaticGainControl.create(sessionId)?.let { wrapEffect(it, "AGC") }
        }.getOrNull()
    }

    override fun queryActiveCaptureSession(): ActiveCaptureSessionInfo? {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return runCatching {
            selectBestCaptureSession(am.activeRecordingConfigurations)
        }.getOrNull()
    }

    override fun registerRecordingCallback(onChanged: (ActiveCaptureSessionInfo?) -> Unit) {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        unregisterRecordingCallback()
        val cb = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                onChanged(selectBestCaptureSession(configs))
            }
        }
        recordingCallback = cb
        runCatching {
            am.registerAudioRecordingCallback(cb, Handler(Looper.getMainLooper()))
        }.onFailure {
            Log.w(TAG, "Unable to register AudioRecordingCallback: ${it.message}")
        }
    }

    override fun unregisterRecordingCallback() {
        val cb = recordingCallback ?: return
        recordingCallback = null
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching { am.unregisterAudioRecordingCallback(cb) }
    }

    private fun selectBestCaptureSession(
        configs: List<AudioRecordingConfiguration>?
    ): ActiveCaptureSessionInfo? {
        if (configs.isNullOrEmpty()) return null
        val eligible = configs.filter { cfg ->
            val silenced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { cfg.isClientSilenced }.getOrDefault(false)
            } else {
                false
            }
            !silenced && cfg.clientAudioSessionId > 0
        }
        val preferred = eligible.firstOrNull {
            it.clientAudioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION
        } ?: eligible.firstOrNull {
            it.clientAudioSource == MediaRecorder.AudioSource.MIC ||
                it.clientAudioSource == MediaRecorder.AudioSource.VOICE_RECOGNITION
        } ?: eligible.firstOrNull() ?: return null

        val osEffects = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                preferred.effects.mapNotNull { desc ->
                    when (desc.type) {
                        AudioEffect.EFFECT_TYPE_AEC -> "AEC:${desc.name}"
                        AudioEffect.EFFECT_TYPE_NS -> "NS:${desc.name}"
                        AudioEffect.EFFECT_TYPE_AGC -> "AGC:${desc.name}"
                        else -> desc.name?.takeIf { it.isNotBlank() }
                    }
                }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        return ActiveCaptureSessionInfo(
            sessionId = preferred.clientAudioSessionId,
            audioSource = preferred.clientAudioSource,
            descriptorEffects = osEffects
        )
    }

    private fun wrapEffect(effect: AudioEffect, fallbackLabel: String): AudioEffectHandle {
        return object : AudioEffectHandle {
            override val isEnabled: Boolean
                get() = runCatching { effect.enabled }.getOrDefault(false)

            override val descriptorName: String
                get() = runCatching { effect.descriptor?.name?.takeIf { it.isNotBlank() } }
                    .getOrNull() ?: fallbackLabel

            override fun setEnabled(enabled: Boolean): Boolean {
                return runCatching {
                    val code = effect.setEnabled(enabled)
                    code == AudioEffect.SUCCESS || effect.enabled == enabled
                }.getOrDefault(false)
            }

            override fun release() {
                runCatching { effect.release() }
            }
        }
    }

    companion object {
        private const val TAG = "AndroidAudioEffects"
    }
}

/**
 * Dedicated Android Audio Effects module managing hardware/OS [AcousticEchoCanceler],
 * [NoiseSuppressor], and [AutomaticGainControl] directly on WebRTC's live AudioRecord session ID,
 * plus capture/render verification telemetry.
 */
class AndroidAudioEffectsModule internal constructor(
    private val backend: AudioEffectsBackend
) {
    constructor(context: Context) : this(AndroidAudioEffectsBackend(context.applicationContext))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Any()

    private var currentConfig = VoiceProcessingConfig()
    private var boundSessionId: Int = -1
    private var webrtcCaptureActive: Boolean = false
    private var callbackRegistered: Boolean = false
    private var retryBindJob: Job? = null

    private var aecHandle: AudioEffectHandle? = null
    private var nsHandle: AudioEffectHandle? = null
    private var agcHandle: AudioEffectHandle? = null
    private var lastOsDescriptors: List<String> = emptyList()
    private var lastCapturePostActive: Boolean = false
    private var lastRenderPreActive: Boolean = false
    private var lastTransmittedFrames: Long = 0L

    private val _status = MutableStateFlow(
        AudioEffectsStatus(
            hwAecAvailable = backend.isAecAvailable(),
            hwNsAvailable = backend.isNsAvailable(),
            hwAgcAvailable = backend.isAgcAvailable()
        )
    )
    val status: StateFlow<AudioEffectsStatus> = _status.asStateFlow()

    fun startCaptureBinding(config: VoiceProcessingConfig) {
        synchronized(lock) {
            currentConfig = config
            if (!callbackRegistered) {
                backend.registerRecordingCallback { info ->
                    if (info != null) {
                        bindToAudioSession(
                            sessionId = info.sessionId,
                            config = currentConfig,
                            osEffects = info.descriptorEffects
                        )
                    }
                }
                callbackRegistered = true
            }
        }
        backend.queryActiveCaptureSession()?.let { info ->
            bindToAudioSession(info.sessionId, config, info.descriptorEffects)
        } ?: refreshStatusLockedSafe()
    }

    fun onWebRtcAudioRecordStart() {
        synchronized(lock) {
            webrtcCaptureActive = true
        }
        val info = backend.queryActiveCaptureSession()
        if (info != null) {
            bindToAudioSession(info.sessionId, currentConfig, info.descriptorEffects)
        } else {
            refreshStatusLockedSafe()
            scheduleAsyncSessionScan()
        }
    }

    fun onWebRtcAudioRecordStop() {
        retryBindJob?.cancel()
        retryBindJob = null
        synchronized(lock) {
            webrtcCaptureActive = false
            releaseEffectHandlesLocked()
            boundSessionId = -1
            lastOsDescriptors = emptyList()
            publishStatusLocked()
        }
    }

    fun bindToAudioSession(
        sessionId: Int,
        config: VoiceProcessingConfig = currentConfig,
        osEffects: List<String> = emptyList()
    ) {
        if (sessionId <= 0) return
        synchronized(lock) {
            currentConfig = config
            if (osEffects.isNotEmpty()) {
                lastOsDescriptors = osEffects
            }
            if (sessionId == boundSessionId && (aecHandle != null || nsHandle != null || agcHandle != null)) {
                applyConfigLocked(config)
                return
            }

            releaseEffectHandlesLocked()
            boundSessionId = sessionId
            retryBindJob?.cancel()
            retryBindJob = null

            aecHandle = backend.createAec(sessionId)
            nsHandle = backend.createNs(sessionId)
            agcHandle = backend.createAgc(sessionId)

            applyConfigLocked(config)
        }
    }

    fun applyConfig(config: VoiceProcessingConfig) {
        synchronized(lock) {
            currentConfig = config
            applyConfigLocked(config)
        }
    }

    fun updatePipelineVerification(
        capturePostProcessingActive: Boolean,
        renderPreProcessingActive: Boolean,
        transmittedFramesProcessed: Long
    ) {
        synchronized(lock) {
            lastCapturePostActive = capturePostProcessingActive
            lastRenderPreActive = renderPreProcessingActive
            lastTransmittedFrames = transmittedFramesProcessed
            publishStatusLocked()
        }
    }

    fun release() {
        retryBindJob?.cancel()
        retryBindJob = null
        synchronized(lock) {
            if (callbackRegistered) {
                backend.unregisterRecordingCallback()
                callbackRegistered = false
            }
            webrtcCaptureActive = false
            releaseEffectHandlesLocked()
            boundSessionId = -1
            lastOsDescriptors = emptyList()
            lastCapturePostActive = false
            lastRenderPreActive = false
            lastTransmittedFrames = 0L
            publishStatusLocked()
        }
    }

    private fun scheduleAsyncSessionScan() {
        retryBindJob?.cancel()
        retryBindJob = scope.launch {
            for (delayMs in listOf(120L, 350L, 750L)) {
                delay(delayMs)
                val alreadyBound = synchronized(lock) { boundSessionId > 0 || !webrtcCaptureActive }
                if (alreadyBound) break
                val info = backend.queryActiveCaptureSession()
                if (info != null) {
                    bindToAudioSession(info.sessionId, currentConfig, info.descriptorEffects)
                    break
                }
            }
        }
    }

    private fun applyConfigLocked(config: VoiceProcessingConfig) {
        aecHandle?.setEnabled(config.echoCancellationEnabled)
        nsHandle?.setEnabled(config.noiseSuppressionEnabled)
        agcHandle?.setEnabled(config.autoGainControlEnabled || config.voicePriorityEnabled)
        publishStatusLocked()
    }

    private fun releaseEffectHandlesLocked() {
        aecHandle?.release()
        aecHandle = null
        nsHandle?.release()
        nsHandle = null
        agcHandle?.release()
        agcHandle = null
    }

    private fun refreshStatusLockedSafe() {
        synchronized(lock) {
            publishStatusLocked()
        }
    }

    private fun publishStatusLocked() {
        val attachedNames = buildList {
            addAll(lastOsDescriptors)
            aecHandle?.let { h ->
                val tag = "AEC:${h.descriptorName}"
                if (none { it.startsWith("AEC:") }) add(tag)
            }
            nsHandle?.let { h ->
                val tag = "NS:${h.descriptorName}"
                if (none { it.startsWith("NS:") }) add(tag)
            }
            agcHandle?.let { h ->
                val tag = "AGC:${h.descriptorName}"
                if (none { it.startsWith("AGC:") }) add(tag)
            }
        }.distinct()

        _status.value = AudioEffectsStatus(
            hwAecAvailable = backend.isAecAvailable(),
            hwNsAvailable = backend.isNsAvailable(),
            hwAgcAvailable = backend.isAgcAvailable(),
            hwAecAttached = aecHandle != null,
            hwAecEnabled = aecHandle?.isEnabled == true,
            hwNsAttached = nsHandle != null,
            hwNsEnabled = nsHandle?.isEnabled == true,
            hwAgcAttached = agcHandle != null,
            hwAgcEnabled = agcHandle?.isEnabled == true,
            captureSessionId = boundSessionId,
            webrtcCaptureActive = webrtcCaptureActive,
            osDescriptorEffects = attachedNames,
            capturePostProcessingActive = lastCapturePostActive,
            transmittedFramesProcessed = lastTransmittedFrames,
            renderPreProcessingActive = lastRenderPreActive,
            echoCancellationConfigured = currentConfig.echoCancellationEnabled,
            noiseSuppressionConfigured = currentConfig.noiseSuppressionEnabled
        )
    }
}
