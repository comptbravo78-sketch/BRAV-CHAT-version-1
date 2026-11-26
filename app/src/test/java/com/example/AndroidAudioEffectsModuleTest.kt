package com.example

import android.content.Context
import android.media.MediaRecorder
import androidx.test.core.app.ApplicationProvider
import com.example.media.ActiveCaptureSessionInfo
import com.example.media.AndroidAudioEffectsModule
import com.example.media.AudioEffectHandle
import com.example.media.AudioEffectsBackend
import com.example.media.VoiceProcessingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidAudioEffectsModuleTest {

    private class FakeAudioEffectHandle(
        override val descriptorName: String
    ) : AudioEffectHandle {
        override var isEnabled: Boolean = false
        var released: Boolean = false

        override fun setEnabled(enabled: Boolean): Boolean {
            if (released) return false
            isEnabled = enabled
            return true
        }

        override fun release() {
            released = true
            isEnabled = false
        }
    }

    private class FakeAudioEffectsBackend : AudioEffectsBackend {
        var aecAvail = true
        var nsAvail = true
        var agcAvail = true
        var activeSession: ActiveCaptureSessionInfo? = null
        var callbackRegistered = false
        var callbackListener: ((ActiveCaptureSessionInfo?) -> Unit)? = null

        val createdAec = mutableListOf<Pair<Int, FakeAudioEffectHandle>>()
        val createdNs = mutableListOf<Pair<Int, FakeAudioEffectHandle>>()
        val createdAgc = mutableListOf<Pair<Int, FakeAudioEffectHandle>>()

        override fun isAecAvailable(): Boolean = aecAvail
        override fun isNsAvailable(): Boolean = nsAvail
        override fun isAgcAvailable(): Boolean = agcAvail

        override fun createAec(sessionId: Int): AudioEffectHandle? {
            if (!aecAvail) return null
            return FakeAudioEffectHandle("Android Hardware AEC").also {
                createdAec.add(sessionId to it)
            }
        }

        override fun createNs(sessionId: Int): AudioEffectHandle? {
            if (!nsAvail) return null
            return FakeAudioEffectHandle("Android Hardware NS").also {
                createdNs.add(sessionId to it)
            }
        }

        override fun createAgc(sessionId: Int): AudioEffectHandle? {
            if (!agcAvail) return null
            return FakeAudioEffectHandle("Android Hardware AGC").also {
                createdAgc.add(sessionId to it)
            }
        }

        override fun queryActiveCaptureSession(): ActiveCaptureSessionInfo? = activeSession

        override fun registerRecordingCallback(onChanged: (ActiveCaptureSessionInfo?) -> Unit) {
            callbackRegistered = true
            callbackListener = onChanged
        }

        override fun unregisterRecordingCallback() {
            callbackRegistered = false
            callbackListener = null
        }
    }

    @Test
    fun attachToWebRtcCaptureSession_enablesAecNsAgcAndReportsVerifiedState() {
        val backend = FakeAudioEffectsBackend()
        val module = AndroidAudioEffectsModule(backend)

        module.startCaptureBinding(
            VoiceProcessingConfig(
                noiseSuppressionEnabled = true,
                echoCancellationEnabled = true,
                autoGainControlEnabled = true,
                voicePriorityEnabled = true
            )
        )
        assertTrue(backend.callbackRegistered)

        backend.activeSession = ActiveCaptureSessionInfo(
            sessionId = 42,
            audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            descriptorEffects = listOf("AEC:Android Hardware AEC", "NS:Android Hardware NS")
        )
        module.onWebRtcAudioRecordStart()
        module.updatePipelineVerification(
            capturePostProcessingActive = true,
            renderPreProcessingActive = true,
            transmittedFramesProcessed = 120L
        )

        val st = module.status.value
        assertEquals(42, st.captureSessionId)
        assertTrue(st.webrtcCaptureActive)
        assertTrue(st.hwAecAttached)
        assertTrue(st.hwAecEnabled)
        assertTrue(st.hwNsAttached)
        assertTrue(st.hwNsEnabled)
        assertTrue(st.hwAgcAttached)
        assertTrue(st.hwAgcEnabled)
        assertTrue(st.isAecEffective)
        assertTrue(st.isNsEffective)
        assertEquals(120L, st.transmittedFramesProcessed)
    }

    @Test
    fun liveMidCallToggle_updatesAttachedHandlesWithoutRecreating() {
        val backend = FakeAudioEffectsBackend()
        val module = AndroidAudioEffectsModule(backend)

        module.startCaptureBinding(VoiceProcessingConfig())
        backend.callbackListener?.invoke(
            ActiveCaptureSessionInfo(
                sessionId = 88,
                audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION
            )
        )

        assertEquals(1, backend.createdAec.size)
        assertEquals(1, backend.createdNs.size)
        assertTrue(backend.createdAec.first().second.isEnabled)
        assertTrue(backend.createdNs.first().second.isEnabled)

        module.applyConfig(
            VoiceProcessingConfig(
                noiseSuppressionEnabled = false,
                echoCancellationEnabled = true,
                autoGainControlEnabled = false,
                voicePriorityEnabled = false
            )
        )

        // Same handles must remain bound without churn
        assertEquals(1, backend.createdAec.size)
        assertEquals(1, backend.createdNs.size)
        assertTrue(backend.createdAec.first().second.isEnabled)
        assertFalse(backend.createdNs.first().second.isEnabled)
        assertFalse(backend.createdAgc.first().second.isEnabled)

        val st = module.status.value
        assertTrue(st.hwAecEnabled)
        assertFalse(st.hwNsEnabled)
        assertFalse(st.hwAgcEnabled)
    }

    @Test
    fun rebindToNewSession_releasesOldHandles_andReleaseCleansEverything() {
        val backend = FakeAudioEffectsBackend()
        val module = AndroidAudioEffectsModule(backend)

        module.startCaptureBinding(VoiceProcessingConfig())
        module.bindToAudioSession(101)
        val firstAec = backend.createdAec.first().second
        val firstNs = backend.createdNs.first().second

        module.bindToAudioSession(202)
        assertTrue(firstAec.released)
        assertTrue(firstNs.released)
        assertEquals(202, module.status.value.captureSessionId)
        assertFalse(backend.createdAec.last().second.released)

        module.release()
        assertTrue(backend.createdAec.last().second.released)
        assertTrue(backend.createdNs.last().second.released)
        assertFalse(backend.callbackRegistered)
        assertEquals(-1, module.status.value.captureSessionId)
        assertFalse(module.status.value.webrtcCaptureActive)
    }

    @Test
    fun realAndroidContextConstruction_doesNotCrashUnderRobolectric() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val module = AndroidAudioEffectsModule(context)
        module.startCaptureBinding(VoiceProcessingConfig())
        module.onWebRtcAudioRecordStart()
        module.onWebRtcAudioRecordStop()
        module.release()
        assertEquals(-1, module.status.value.captureSessionId)
    }
}
