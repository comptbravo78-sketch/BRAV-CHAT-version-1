package com.example

import com.example.media.VoiceAudioProcessor
import com.example.media.VoiceProcessingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class VoiceAudioProcessorTest {

    private fun rms(samples: ShortArray): Double {
        var sum = 0.0
        for (s in samples) {
            val v = s.toDouble() / 32768.0
            sum += v * v
        }
        return sqrt(sum / samples.size.coerceAtLeast(1))
    }

    @Test
    fun steadyLowFrequencyAndAmbientNoise_isSignificantlyAttenuated() {
        val processor = VoiceAudioProcessor(
            VoiceProcessingConfig(
                noiseSuppressionEnabled = true,
                echoCancellationEnabled = true,
                autoGainControlEnabled = true,
                voicePriorityEnabled = true
            )
        )
        val sampleRate = 16000
        val frameSamples = 160 // 10 ms frame
        processor.initialize(sampleRate, 1)

        var lastInputRms = 0.0
        var lastOutputRms = 0.0

        // Feed 45 frames (~450 ms) of steady 65 Hz wind rumble + low stationary hiss
        for (frameIdx in 0 until 45) {
            val buf = ShortArray(frameSamples) { i ->
                val n = frameIdx * frameSamples + i
                val hum = 0.035 * sin(2.0 * PI * 65.0 * n / sampleRate)
                val hiss = 0.012 * sin(2.0 * PI * 4200.0 * n / sampleRate)
                ((hum + hiss) * 32767.0).toInt().toShort()
            }
            lastInputRms = rms(buf)
            processor.processPcm16(buf, frameSamples, 1)
            lastOutputRms = rms(buf)
        }

        val telemetry = processor.currentTelemetry()
        assertFalse("Stationary low-level background noise must not falsely trigger speech", telemetry.speechDetected)
        assertTrue("Noise reduction dB should be substantial (> 6 dB), got ${telemetry.noiseReductionDb}", telemetry.noiseReductionDb > 6f)
        assertTrue(
            "Output RMS ($lastOutputRms) should be < 45% of noisy input RMS ($lastInputRms)",
            lastOutputRms < lastInputRms * 0.45
        )
    }

    @Test
    fun vocalBandSignalAboveNoiseFloor_isDetectedAndPreserved() {
        val processor = VoiceAudioProcessor(
            VoiceProcessingConfig(
                noiseSuppressionEnabled = true,
                echoCancellationEnabled = true,
                autoGainControlEnabled = true,
                voicePriorityEnabled = true
            )
        )
        val sampleRate = 16000
        val frameSamples = 160
        processor.initialize(sampleRate, 1)

        // Calibrate quiet background floor first
        for (f in 0 until 20) {
            val noise = ShortArray(frameSamples) { i ->
                val n = f * frameSamples + i
                (0.008 * sin(2.0 * PI * 80.0 * n / sampleRate) * 32767.0).toInt().toShort()
            }
            processor.processPcm16(noise, frameSamples, 1)
        }

        // Now feed clear vocal-band tone (~1.6 kHz)
        var detectedSpeech = false
        var speechOutRms = 0.0
        for (f in 20 until 32) {
            val speech = ShortArray(frameSamples) { i ->
                val n = f * frameSamples + i
                (0.18 * sin(2.0 * PI * 1600.0 * n / sampleRate) * 32767.0).toInt().toShort()
            }
            val speechInRms = rms(speech)
            if (processor.processPcm16(speech, frameSamples, 1)) {
                detectedSpeech = true
            }
            speechOutRms = rms(speech)
            if (f >= 22) {
                assertTrue(
                    "Vocal band signal after 20ms ramp must remain strong (out=$speechOutRms vs in=$speechInRms)",
                    speechOutRms >= speechInRms * 0.90
                )
            }
        }

        assertTrue("Voice activity detector must flag active speech", detectedSpeech)
        assertTrue(processor.currentTelemetry().speechDetected)
    }

    @Test
    fun disabledProcessingConfig_passesSignalThroughWithoutAlteration() {
        val processor = VoiceAudioProcessor(
            VoiceProcessingConfig(
                noiseSuppressionEnabled = false,
                echoCancellationEnabled = false,
                autoGainControlEnabled = false,
                voicePriorityEnabled = false
            )
        )
        processor.initialize(16000, 1)

        val original = ShortArray(160) { i ->
            (0.05 * sin(2.0 * PI * 120.0 * i / 16000.0) * 32767.0).toInt().toShort()
        }
        val copy = original.copyOf()
        processor.processPcm16(copy, copy.size, 1)

        for (i in original.indices) {
            assertEquals("Bypassed sample $i should stay unchanged", original[i], copy[i])
        }
        assertTrue(abs(processor.currentTelemetry().noiseReductionDb) < 0.01f)
        assertTrue(abs(processor.currentTelemetry().echoReductionDb) < 0.01f)
        assertEquals(1L, processor.processedCaptureFrames())
    }

    @Test
    fun farEndPlayback_triggersResidualEchoSuppression_whilePreservingLoudDoubleTalk() {
        val processor = VoiceAudioProcessor(
            VoiceProcessingConfig(
                noiseSuppressionEnabled = false,
                echoCancellationEnabled = true,
                autoGainControlEnabled = false,
                voicePriorityEnabled = false
            )
        )
        val sampleRate = 16000
        val frameSamples = 160
        processor.initialize(sampleRate, 1)

        // Feed loud far-end playback (0.35 amplitude @ 900 Hz)
        val farEnd = ShortArray(frameSamples) { i ->
            (0.35 * sin(2.0 * PI * 900.0 * i / sampleRate) * 32767.0).toInt().toShort()
        }
        repeat(4) {
            processor.processRenderPcm16(farEnd, frameSamples, 1)
        }
        assertEquals(4L, processor.processedRenderFrames())

        // Feed lower-level speaker echo leaking back into microphone (0.04 amplitude)
        var echoInRms = 0.0
        var echoOutRms = 0.0
        for (f in 0 until 8) {
            val bleed = ShortArray(frameSamples) { i ->
                val n = f * frameSamples + i
                (0.04 * sin(2.0 * PI * 900.0 * n / sampleRate) * 32767.0).toInt().toShort()
            }
            echoInRms = rms(bleed)
            processor.processPcm16(bleed, frameSamples, 1)
            echoOutRms = rms(bleed)
        }

        val echoTel = processor.currentTelemetry()
        assertTrue("Far-end activity should be reported", echoTel.farEndPlaybackActive)
        assertTrue("Echo reduction dB should be > 4 dB, got ${echoTel.echoReductionDb}", echoTel.echoReductionDb > 4f)
        assertTrue("Residual echo RMS ($echoOutRms) should be < 55% of input ($echoInRms)", echoOutRms < echoInRms * 0.55)
        assertEquals(8L, processor.processedCaptureFrames())

        // Refresh far-end signal and verify loud local double-talk passes through cleanly
        processor.processRenderPcm16(farEnd, frameSamples, 1)
        var dtOutRms = 0.0
        var dtInRms = 0.0
        for (f in 0 until 8) {
            val localVoice = ShortArray(frameSamples) { i ->
                val n = f * frameSamples + i
                (0.45 * sin(2.0 * PI * 1400.0 * n / sampleRate) * 32767.0).toInt().toShort()
            }
            dtInRms = rms(localVoice)
            processor.processPcm16(localVoice, frameSamples, 1)
            dtOutRms = rms(localVoice)
        }
        assertTrue(
            "Loud local double-talk ($dtOutRms) must remain >= 85% of input ($dtInRms)",
            dtOutRms >= dtInRms * 0.85
        )
    }

    @Test
    fun webRtcByteBufferHooks_incrementCaptureAndRenderFrameCounters() {
        val processor = VoiceAudioProcessor(
            VoiceProcessingConfig(
                noiseSuppressionEnabled = true,
                echoCancellationEnabled = true,
                autoGainControlEnabled = true,
                voicePriorityEnabled = true
            )
        )
        processor.initialize(16000, 1)
        processor.renderPreProcessor.initialize(16000, 1)

        val renderBuf = java.nio.ByteBuffer.allocateDirect(160 * 2).order(java.nio.ByteOrder.nativeOrder())
        val captureBuf = java.nio.ByteBuffer.allocateDirect(160 * 2).order(java.nio.ByteOrder.nativeOrder())
        val rs = renderBuf.asShortBuffer()
        val cs = captureBuf.asShortBuffer()
        for (i in 0 until 160) {
            rs.put(i, (0.25 * sin(2.0 * PI * 700.0 * i / 16000.0) * 32767.0).toInt().toShort())
            cs.put(i, (0.03 * sin(2.0 * PI * 700.0 * i / 16000.0) * 32767.0).toInt().toShort())
        }

        processor.renderPreProcessor.process(1, 160, renderBuf)
        processor.process(1, 160, captureBuf)

        assertEquals(1L, processor.processedRenderFrames())
        assertEquals(1L, processor.processedCaptureFrames())
        assertTrue(processor.currentTelemetry().processedCaptureFrames == 1L)
    }
}
