package com.example.media

import org.webrtc.ExternalAudioProcessingFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Runtime audio DSP preferences applied directly to the outgoing WebRTC microphone pipeline.
 */
data class VoiceProcessingConfig(
    val noiseSuppressionEnabled: Boolean = true,
    val echoCancellationEnabled: Boolean = true,
    val autoGainControlEnabled: Boolean = true,
    val voicePriorityEnabled: Boolean = true
)

/**
 * Real-time telemetry reported by the audio processing chain to the UI.
 */
data class VoiceAudioTelemetry(
    val hardwareNsSupported: Boolean = false,
    val hardwareAecSupported: Boolean = false,
    val hardwareAgcSupported: Boolean = false,
    val hwAecEnabled: Boolean = false,
    val hwNsEnabled: Boolean = false,
    val hwAgcEnabled: Boolean = false,
    val captureSessionId: Int = -1,
    val webrtcCaptureActive: Boolean = false,
    val noiseSuppressionActive: Boolean = true,
    val echoCancellationActive: Boolean = true,
    val voicePriorityActive: Boolean = true,
    val speechDetected: Boolean = false,
    val farEndPlaybackActive: Boolean = false,
    val inputLevel: Float = 0f,
    val noiseReductionDb: Float = 0f,
    val echoReductionDb: Float = 0f,
    val processedCaptureFrames: Long = 0L
)

/**
 * Pure-Kotlin real-time speech-priority & stationary ambient noise-reduction DSP processor.
 *
 * Implements WebRTC's [ExternalAudioProcessingFactory.AudioProcessing] capture post-processing hook:
 * 1. 2nd-order Butterworth high-pass filter (~115 Hz) removing traffic rumble, wind thumps, and 50/60 Hz hum.
 * 2. Vocal-presence band emphasis (~2.2 kHz peaking filter) prioritizing voice intelligibility when active.
 * 3. Adaptive noise-floor tracker with fast-attack / slow-release smooth expander (~18 dB ambient reduction)
 *    that preserves initial consonants without harsh word chopping.
 * 4. Soft knee gain boost + tanh limiter when voice-priority / AGC is enabled.
 * 5. Attenuates upper sub-bands (>8 kHz hiss/static) during non-speech gaps when split-band frames arrive.
 */
class VoiceAudioProcessor(
    initialConfig: VoiceProcessingConfig = VoiceProcessingConfig()
) : ExternalAudioProcessingFactory.AudioProcessing {

    private val configRef = AtomicReference(initialConfig)
    private val initialized = AtomicBoolean(false)

    @Volatile
    private var sampleRateHz: Int = 16000

    @Volatile
    private var channels: Int = 1

    private val _telemetry = AtomicReference(
        VoiceAudioTelemetry(
            noiseSuppressionActive = initialConfig.noiseSuppressionEnabled,
            echoCancellationActive = initialConfig.echoCancellationEnabled,
            voicePriorityActive = initialConfig.voicePriorityEnabled
        )
    )

    fun currentTelemetry(): VoiceAudioTelemetry = _telemetry.get()

    fun updateConfig(config: VoiceProcessingConfig) {
        configRef.set(config)
        val prev = _telemetry.get()
        _telemetry.set(
            prev.copy(
                noiseSuppressionActive = config.noiseSuppressionEnabled,
                echoCancellationActive = config.echoCancellationEnabled,
                voicePriorityActive = config.voicePriorityEnabled
            )
        )
    }

    fun getConfig(): VoiceProcessingConfig = configRef.get()

    // Biquad filter states per channel (up to 2 channels supported)
    private val hpX1 = FloatArray(2)
    private val hpX2 = FloatArray(2)
    private val hpY1 = FloatArray(2)
    private val hpY2 = FloatArray(2)

    private val pkX1 = FloatArray(2)
    private val pkX2 = FloatArray(2)
    private val pkY1 = FloatArray(2)
    private val pkY2 = FloatArray(2)

    // Biquad coefficients (Butterworth HPF @ ~115 Hz and vocal clarity peaking @ ~2.2 kHz)
    private var hpB0 = 1f
    private var hpB1 = 0f
    private var hpB2 = 0f
    private var hpA1 = 0f
    private var hpA2 = 0f

    private var pkB0 = 1f
    private var pkB1 = 0f
    private var pkB2 = 0f
    private var pkA1 = 0f
    private var pkA2 = 0f

    // Adaptive noise-floor & gain envelope state (normalized amplitude 0..1)
    private var noiseFloorRms = 0.008f
    private var smoothedGain = 1.0f
    private var smoothedEchoGain = 1.0f
    private var speechHoldFrames = 0

    // Far-end speaker reference tracking (populated via renderPreProcessor)
    private val processedCaptureFrameCount = AtomicLong(0L)
    private val processedRenderFrameCount = AtomicLong(0L)

    @Volatile
    private var farEndPlaybackRms = 0f

    @Volatile
    private var farEndHoldFrames = 0

    @Volatile
    private var lastRenderTimestampMs = 0L

    @Volatile
    private var renderChannels: Int = 1

    /**
     * WebRTC render pre-processing hook observing far-end decoded playback audio (read-only)
     * so residual acoustic echo bleeding back from the loudspeaker into the mic can be attenuated
     * without clipping local double-talk speech.
     */
    val renderPreProcessor: ExternalAudioProcessingFactory.AudioProcessing =
        object : ExternalAudioProcessingFactory.AudioProcessing {
            override fun initialize(sampleRate: Int, channels: Int) {
                renderChannels = channels.coerceIn(1, 2)
            }

            override fun reset(newRate: Int) {}

            override fun process(numBands: Int, numFrames: Int, buffer: ByteBuffer?) {
                if (buffer == null || numFrames <= 0) return
                val dup = buffer.duplicate().order(ByteOrder.nativeOrder())
                dup.clear()
                val cap = dup.capacity()
                val ch = renderChannels.coerceIn(1, 2)
                val bands = numBands.coerceAtLeast(1)

                val expectedShortSingle = numFrames * ch * 2
                val expectedShortMulti = bands * numFrames * ch * 2
                val expectedFloatSingle = numFrames * ch * 4
                val expectedFloatMulti = bands * numFrames * ch * 4

                val rms = when {
                    cap == expectedShortMulti || cap == expectedShortSingle -> {
                        val sb = dup.asShortBuffer()
                        val band0 = (numFrames * ch).coerceAtMost(sb.remaining())
                        if (band0 <= 0) 0f else {
                            var sumSq = 0.0
                            for (i in 0 until band0) {
                                val v = sb.get(i) / 32768.0
                                sumSq += v * v
                            }
                            sqrt(sumSq / band0).toFloat()
                        }
                    }
                    cap == expectedFloatMulti || cap == expectedFloatSingle -> {
                        val fb = dup.asFloatBuffer()
                        val band0 = (numFrames * ch).coerceAtMost(fb.remaining())
                        if (band0 <= 0) 0f else {
                            var maxAbs = 0f
                            var sumSq = 0.0
                            for (i in 0 until band0) {
                                val s = fb.get(i)
                                val a = abs(s)
                                if (a > maxAbs) maxAbs = a
                                sumSq += (s * s).toDouble()
                            }
                            val rawRms = sqrt(sumSq / band0).toFloat()
                            if (maxAbs > 2.5f) rawRms / 32768f else rawRms
                        }
                    }
                    else -> 0f
                }
                recordRenderRms(rms)
            }
        }

    fun processedCaptureFrames(): Long = processedCaptureFrameCount.get()

    fun processedRenderFrames(): Long = processedRenderFrameCount.get()

    fun processRenderPcm16(samples: ShortArray, sampleCount: Int = samples.size, numChannels: Int = 1) {
        if (sampleCount <= 0) return
        val count = sampleCount.coerceAtMost(samples.size)
        var sumSq = 0.0
        for (i in 0 until count) {
            val v = samples[i] / 32768.0
            sumSq += v * v
        }
        recordRenderRms(sqrt(sumSq / count.coerceAtLeast(1)).toFloat())
    }

    private fun recordRenderRms(rms: Float) {
        processedRenderFrameCount.incrementAndGet()
        val clamped = rms.coerceIn(0f, 1f)
        farEndPlaybackRms = farEndPlaybackRms * 0.45f + clamped * 0.55f
        if (clamped > 0.015f || farEndPlaybackRms > 0.015f) {
            farEndHoldFrames = 18 // ~180 ms acoustic room tail window
            lastRenderTimestampMs = System.currentTimeMillis()
        }
    }

    override fun initialize(sampleRate: Int, channels: Int) {
        this.sampleRateHz = sampleRate.coerceAtLeast(8000)
        this.channels = channels.coerceIn(1, 2)
        recomputeFilterCoefficients(this.sampleRateHz)
        resetInternalState()
        initialized.set(true)
    }

    override fun reset(newRate: Int) {
        this.sampleRateHz = newRate.coerceAtLeast(8000)
        recomputeFilterCoefficients(this.sampleRateHz)
        resetInternalState()
    }

    private fun resetInternalState() {
        hpX1.fill(0f)
        hpX2.fill(0f)
        hpY1.fill(0f)
        hpY2.fill(0f)
        pkX1.fill(0f)
        pkX2.fill(0f)
        pkY1.fill(0f)
        pkY2.fill(0f)
        noiseFloorRms = 0.008f
        smoothedGain = 1.0f
        smoothedEchoGain = 1.0f
        speechHoldFrames = 0
        farEndPlaybackRms = 0f
        farEndHoldFrames = 0
        lastRenderTimestampMs = 0L
    }

    private fun recomputeFilterCoefficients(fs: Int) {
        val effectiveFs = fs.coerceAtLeast(8000).toDouble()

        // 2nd-order Butterworth High-Pass Filter at 115 Hz
        val fHp = 115.0.coerceAtMost(effectiveFs * 0.25)
        val w0Hp = 2.0 * PI * fHp / effectiveFs
        val cosHp = cos(w0Hp)
        val alphaHp = sin(w0Hp) / sqrt(2.0) // Q = 0.7071 Butterworth
        val a0Hp = 1.0 + alphaHp
        hpB0 = ((1.0 + cosHp) / 2.0 / a0Hp).toFloat()
        hpB1 = (-(1.0 + cosHp) / a0Hp).toFloat()
        hpB2 = ((1.0 + cosHp) / 2.0 / a0Hp).toFloat()
        hpA1 = (-2.0 * cosHp / a0Hp).toFloat()
        hpA2 = ((1.0 - alphaHp) / a0Hp).toFloat()

        // Gentle vocal presence peaking EQ (+2.8 dB @ 2200 Hz, Q = 1.0)
        val fPk = 2200.0.coerceAtMost(effectiveFs * 0.42)
        val gainDb = 2.8
        val aAmp = Math.pow(10.0, gainDb / 40.0)
        val w0Pk = 2.0 * PI * fPk / effectiveFs
        val cosPk = cos(w0Pk)
        val alphaPk = sin(w0Pk) / (2.0 * 1.0)
        val a0Pk = 1.0 + alphaPk / aAmp
        pkB0 = ((1.0 + alphaPk * aAmp) / a0Pk).toFloat()
        pkB1 = (-2.0 * cosPk / a0Pk).toFloat()
        pkB2 = ((1.0 - alphaPk * aAmp) / a0Pk).toFloat()
        pkA1 = (-2.0 * cosPk / a0Pk).toFloat()
        pkA2 = ((1.0 - alphaPk / aAmp) / a0Pk).toFloat()
    }

    override fun process(numBands: Int, numFrames: Int, buffer: ByteBuffer?) {
        if (buffer == null || numFrames <= 0) return
        val cfg = configRef.get()
        if (!cfg.noiseSuppressionEnabled && !cfg.voicePriorityEnabled && !cfg.echoCancellationEnabled) {
            val framesCount = processedCaptureFrameCount.incrementAndGet()
            val prev = _telemetry.get()
            _telemetry.set(
                prev.copy(
                    noiseSuppressionActive = false,
                    echoCancellationActive = false,
                    voicePriorityActive = false,
                    speechDetected = false,
                    farEndPlaybackActive = false,
                    noiseReductionDb = 0f,
                    echoReductionDb = 0f,
                    processedCaptureFrames = framesCount
                )
            )
            return
        }
        if (!initialized.get()) {
            initialize(sampleRateHz, channels)
        }

        val dup = buffer.duplicate().order(ByteOrder.nativeOrder())
        dup.clear()
        val cap = dup.capacity()
        val ch = channels.coerceIn(1, 2)
        val bands = numBands.coerceAtLeast(1)

        val expectedShortSingle = numFrames * ch * 2
        val expectedShortMulti = bands * numFrames * ch * 2
        val expectedFloatSingle = numFrames * ch * 4
        val expectedFloatMulti = bands * numFrames * ch * 4

        when {
            cap == expectedShortMulti || cap == expectedShortSingle -> {
                val sb = dup.asShortBuffer()
                val band0Samples = (numFrames * ch).coerceAtMost(sb.remaining())
                val work = FloatArray(band0Samples)
                for (i in 0 until band0Samples) {
                    work[i] = sb.get(i) / 32768f
                }
                val voiceActive = processNormalizedFrameInPlace(work, numFrames, ch, cfg)
                for (i in 0 until band0Samples) {
                    val v = (work[i] * 32767f).coerceIn(-32768f, 32767f).toInt()
                    sb.put(i, v.toShort())
                }
                // Attenuate high-frequency bands (>8 kHz wind/hiss) when speech is inactive
                if (cap == expectedShortMulti && bands > 1 && cfg.noiseSuppressionEnabled && !voiceActive) {
                    val extraTotal = (bands * numFrames * ch).coerceAtMost(sb.remaining())
                    for (i in band0Samples until extraTotal) {
                        sb.put(i, (sb.get(i) * 0.18f).toInt().toShort())
                    }
                }
            }
            cap == expectedFloatMulti || cap == expectedFloatSingle -> {
                val fb = dup.asFloatBuffer()
                val band0Samples = (numFrames * ch).coerceAtMost(fb.remaining())
                val work = FloatArray(band0Samples)
                var maxAbs = 0f
                for (i in 0 until band0Samples) {
                    val s = fb.get(i)
                    work[i] = s
                    val a = abs(s)
                    if (a > maxAbs) maxAbs = a
                }
                // Detect whether WebRTC float buffer is scaled to [-32768, 32767] vs [-1, 1]
                val scaleToUnit = maxAbs > 2.5f
                if (scaleToUnit) {
                    for (i in 0 until band0Samples) work[i] = work[i] / 32768f
                }
                val voiceActive = processNormalizedFrameInPlace(work, numFrames, ch, cfg)
                val backScale = if (scaleToUnit) 32768f else 1f
                for (i in 0 until band0Samples) {
                    fb.put(i, (work[i] * backScale).coerceIn(-backScale, backScale))
                }
                if (cap == expectedFloatMulti && bands > 1 && cfg.noiseSuppressionEnabled && !voiceActive) {
                    val extraTotal = (bands * numFrames * ch).coerceAtMost(fb.remaining())
                    for (i in band0Samples until extraTotal) {
                        fb.put(i, fb.get(i) * 0.18f)
                    }
                }
            }
        }
    }

    /**
     * Processes a 16-bit PCM frame in-place (interleaved channels). Exposed cleanly for JVM unit tests
     * and local settings microphone testing.
     *
     * @return true if vocal energy dominated over background noise in this frame.
     */
    fun processPcm16(samples: ShortArray, sampleCount: Int = samples.size, numChannels: Int = 1): Boolean {
        if (sampleCount <= 0) return false
        val ch = numChannels.coerceIn(1, 2)
        if (!initialized.get()) {
            initialize(sampleRateHz, ch)
        }
        val cfg = configRef.get()
        if (!cfg.noiseSuppressionEnabled && !cfg.voicePriorityEnabled && !cfg.echoCancellationEnabled) {
            val framesCount = processedCaptureFrameCount.incrementAndGet()
            val prev = _telemetry.get()
            _telemetry.set(
                prev.copy(
                    noiseSuppressionActive = false,
                    echoCancellationActive = false,
                    voicePriorityActive = false,
                    speechDetected = false,
                    farEndPlaybackActive = false,
                    noiseReductionDb = 0f,
                    echoReductionDb = 0f,
                    processedCaptureFrames = framesCount
                )
            )
            return false
        }
        val frames = sampleCount / ch
        val work = FloatArray(sampleCount) { idx -> samples[idx] / 32768f }
        val active = processNormalizedFrameInPlace(work, frames, ch, cfg)
        for (i in 0 until sampleCount) {
            samples[i] = kotlin.math.round(work[i] * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()
        }
        return active
    }

    /**
     * Core DSP pipeline operating on normalized [-1f, 1f] samples.
     * Returns true when speech is detected above the adaptive noise floor.
     */
    internal fun processNormalizedFrameInPlace(
        samples: FloatArray,
        numFrames: Int,
        numChannels: Int,
        cfg: VoiceProcessingConfig
    ): Boolean {
        if (numFrames <= 0 || samples.isEmpty()) return false
        val chCount = numChannels.coerceIn(1, 2)

        // Stage 1: High-pass rumble/wind/hum filter + optional vocal presence peaking EQ
        var sumSq = 0.0
        for (f in 0 until numFrames) {
            for (c in 0 until chCount) {
                val idx = f * chCount + c
                if (idx >= samples.size) break
                var x = samples[idx]

                if (cfg.noiseSuppressionEnabled || cfg.voicePriorityEnabled) {
                    val yHp = hpB0 * x + hpB1 * hpX1[c] + hpB2 * hpX2[c] - hpA1 * hpY1[c] - hpA2 * hpY2[c]
                    hpX2[c] = hpX1[c]
                    hpX1[c] = x
                    hpY2[c] = hpY1[c]
                    hpY1[c] = yHp
                    x = yHp
                }

                if (cfg.voicePriorityEnabled) {
                    val yPk = pkB0 * x + pkB1 * pkX1[c] + pkB2 * pkX2[c] - pkA1 * pkY1[c] - pkA2 * pkY2[c]
                    pkX2[c] = pkX1[c]
                    pkX1[c] = x
                    pkY2[c] = pkY1[c]
                    pkY1[c] = yPk
                    x = yPk
                }

                samples[idx] = x
                sumSq += (x * x).toDouble()
            }
        }

        val count = (numFrames * chCount).coerceAtLeast(1)
        val frameRms = sqrt(sumSq / count).toFloat()

        // Stage 2: Adaptive stationary noise-floor estimation (minima tracking)
        if (frameRms < noiseFloorRms) {
            // Track quieter floors relatively quickly (~150 ms time constant)
            noiseFloorRms = noiseFloorRms * 0.90f + frameRms * 0.10f
        } else if (frameRms < noiseFloorRms * 1.9f) {
            // Drift upward very slowly only on steady stationary background hum
            noiseFloorRms = noiseFloorRms * 0.985f + frameRms * 0.015f
        }
        noiseFloorRms = noiseFloorRms.coerceIn(0.0005f, 0.18f)

        val snr = frameRms / (noiseFloorRms + 1e-5f)
        val instantSpeech = snr >= 2.2f && frameRms >= 0.0045f
        if (instantSpeech) {
            speechHoldFrames = 14 // ~140 ms vocal hangover to protect word endings & soft syllables
        } else if (speechHoldFrames > 0) {
            speechHoldFrames--
        }
        val speechActive = instantSpeech || speechHoldFrames > 0

        // Stage 3: Compute smooth noise-reduction + speech-priority gain
        val targetGain = if (!cfg.noiseSuppressionEnabled) {
            1.0f
        } else {
            val minFloorGain = 0.12f // ~-18 dB reduction instead of jarring digital silence
            when {
                speechActive -> 1.0f
                snr <= 1.25f -> minFloorGain
                snr >= 2.2f -> 1.0f
                else -> {
                    val t = ((snr - 1.25f) / (2.2f - 1.25f)).coerceIn(0f, 1f)
                    val smoothT = t * t * (3f - 2f * t)
                    minFloorGain + (1f - minFloorGain) * smoothT
                }
            }
        }

        // Far-end residual echo guard (active when speaker audio was recently played & no strong local double-talk)
        val nowMs = System.currentTimeMillis()
        val farEndWindowActive = farEndHoldFrames > 0 || (lastRenderTimestampMs > 0L && (nowMs - lastRenderTimestampMs) <= 180L)
        if (farEndHoldFrames > 0) {
            farEndHoldFrames--
            if (farEndHoldFrames == 0) {
                farEndPlaybackRms *= 0.25f
            }
        }
        val isFarEndActive = farEndWindowActive && farEndPlaybackRms > 0.015f
        val isDoubleTalk = frameRms >= farEndPlaybackRms * 1.15f || (frameRms >= 0.08f && snr >= 3.0f)
        val targetEchoGain = if (cfg.echoCancellationEnabled && isFarEndActive && !isDoubleTalk && frameRms > 0.002f) {
            // Attenuate residual speaker-to-mic bleed (~-15 dB) smoothly while letting local speech pass
            val ratio = (frameRms / (farEndPlaybackRms * 1.15f)).coerceIn(0f, 1f)
            0.18f + 0.32f * ratio
        } else {
            1.0f
        }
        val prevEchoGain = smoothedEchoGain
        val alphaEcho = when {
            targetEchoGain < prevEchoGain -> 0.55f
            isDoubleTalk -> 0.65f
            else -> 0.22f
        }
        smoothedEchoGain = prevEchoGain + alphaEcho * (targetEchoGain - prevEchoGain)

        // Fast opening (~5ms), gentle release (~120ms)
        val alphaGain = if (targetGain > smoothedGain) 0.65f else 0.14f
        val prevGain = smoothedGain
        smoothedGain = prevGain + alphaGain * (targetGain - prevGain)

        // Gentle speech boost when voice priority / AGC is active and speech is quiet
        val speechMakeup = if (cfg.voicePriorityEnabled && speechActive && frameRms in 0.005f..0.14f) {
            (0.16f / (frameRms + 0.04f)).coerceIn(1.0f, 1.55f)
        } else {
            1.0f
        }

        val startG = prevGain * prevEchoGain * speechMakeup
        val endG = smoothedGain * smoothedEchoGain * speechMakeup
        val denomFrames = (numFrames - 1).coerceAtLeast(1).toFloat()

        // Stage 4: Linear sample-by-sample gain ramp (zero click/zipper noise) + soft limiter
        for (f in 0 until numFrames) {
            val g = startG + (endG - startG) * (f / denomFrames)
            for (c in 0 until chCount) {
                val idx = f * chCount + c
                if (idx >= samples.size) break
                val scaled = samples[idx] * g
                samples[idx] = if (abs(scaled) > 0.85f) {
                    tanh(scaled.toDouble()).toFloat()
                } else {
                    scaled
                }
            }
        }

        // Update lightweight telemetry for UI meters
        val reductionDb = if (smoothedGain < 0.99f) {
            (-20.0 * log10(smoothedGain.coerceAtLeast(0.05f).toDouble())).toFloat()
        } else {
            0f
        }
        val echoDuckDb = if (smoothedEchoGain < 0.99f) {
            (-20.0 * log10(smoothedEchoGain.coerceAtLeast(0.05f).toDouble())).toFloat()
        } else {
            0f
        }
        val framesCount = processedCaptureFrameCount.incrementAndGet()
        val normalizedMeter = ((ln((frameRms * 18f + 1f).toDouble()) / ln(19.0)).toFloat()).coerceIn(0f, 1f)
        val curTel = _telemetry.get()
        _telemetry.set(
            curTel.copy(
                noiseSuppressionActive = cfg.noiseSuppressionEnabled,
                echoCancellationActive = cfg.echoCancellationEnabled,
                voicePriorityActive = cfg.voicePriorityEnabled,
                speechDetected = speechActive && !(cfg.echoCancellationEnabled && isFarEndActive && !isDoubleTalk),
                farEndPlaybackActive = isFarEndActive,
                inputLevel = normalizedMeter,
                noiseReductionDb = reductionDb,
                echoReductionDb = echoDuckDb,
                processedCaptureFrames = framesCount
            )
        )
        return speechActive && !(cfg.echoCancellationEnabled && isFarEndActive && !isDoubleTalk)
    }
}
