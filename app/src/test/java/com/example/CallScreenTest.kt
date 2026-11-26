package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.media.VoiceAudioTelemetry
import com.example.media.WebRtcConnectionState
import com.example.media.WebRtcSessionState
import com.example.model.Call
import com.example.model.User
import com.example.ui.screens.CallScreenContent
import com.example.ui.screens.formatCallChronometer
import com.example.ui.theme.BravChatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CallScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun formatCallChronometer_formatsMinutesSecondsAndHours() {
        assertEquals("00:00", formatCallChronometer(0L))
        assertEquals("02:05", formatCallChronometer(125L))
        assertEquals("01:02:03", formatCallChronometer(3723L))
    }

    @Test
    fun audioCallScreen_displaysPeerDetailsChronometerAndAudioControls() {
        var micClicked = 0
        var speakerClicked = 0
        var holdClicked = 0
        var hangupClicked = 0

        val call = Call(
            id = "call_audio_1",
            callerId = "me_uid",
            callerName = "Moi",
            receiverId = "peer_uid",
            receiverName = "Amina Diallo",
            receiverUsername = "amina.d",
            callType = "audio",
            status = "accepted"
        )
        val peer = User(
            userId = "peer_uid",
            displayName = "Amina Diallo",
            username = "amina.d",
            phoneMasked = "+33 6 •• •• 56 78",
            locationCity = "Lyon"
        )
        val session = WebRtcSessionState(
            callId = "call_audio_1",
            isVideoCall = false,
            isCaller = true,
            connectionState = WebRtcConnectionState.CONNECTED,
            elapsedSeconds = 125L
        )
        val telemetry = VoiceAudioTelemetry(
            hwAecEnabled = true,
            hwNsEnabled = true,
            noiseSuppressionActive = true,
            echoCancellationActive = true,
            voicePriorityActive = true,
            processedCaptureFrames = 320L
        )

        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                CallScreenContent(
                    call = call,
                    currentUserId = "me_uid",
                    peerUser = peer,
                    session = session,
                    audioTelemetry = telemetry,
                    onToggleMute = { micClicked++ },
                    onToggleSpeaker = { speakerClicked++ },
                    onToggleHold = { holdClicked++ },
                    onHangup = { hangupClicked++ }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("call_peer_name").assertIsDisplayed().assertTextContains("Amina Diallo")
        composeTestRule.onNodeWithTag("call_chronometer_text").assertIsDisplayed().assertTextContains("02:05")
        composeTestRule.onNodeWithText("@amina.d • +33 6 •• •• 56 78").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_dsp_badge").assertIsDisplayed()

        // Tap DSP badge to open detailed peer & WebRTC telemetry card
        composeTestRule.onNodeWithTag("audio_dsp_badge").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("call_details_panel").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Trames transmises : 320", substring = true).performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("toggle_mic_button").performClick()
        composeTestRule.onNodeWithTag("toggle_speaker_button").performClick()
        composeTestRule.onNodeWithTag("toggle_hold_button").performClick()
        composeTestRule.onNodeWithTag("hangup_call_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, micClicked)
        assertEquals(1, speakerClicked)
        assertEquals(1, holdClicked)
        assertEquals(1, hangupClicked)
    }

    @Test
    fun videoCallScreen_displaysVideoControlsAndTriggersCameraActions() {
        var camClicked = 0
        var switchClicked = 0
        var hangupClicked = 0

        val call = Call(
            id = "call_video_1",
            callerId = "peer_uid",
            callerName = "Marc Kouassi",
            callerUsername = "marck",
            receiverId = "me_uid",
            receiverName = "Moi",
            callType = "video",
            status = "accepted"
        )
        val session = WebRtcSessionState(
            callId = "call_video_1",
            isVideoCall = true,
            isCaller = false,
            connectionState = WebRtcConnectionState.CONNECTED,
            isCameraEnabled = true,
            elapsedSeconds = 64L
        )

        composeTestRule.setContent {
            BravChatTheme(darkTheme = true) {
                CallScreenContent(
                    call = call,
                    currentUserId = "me_uid",
                    session = session,
                    audioTelemetry = VoiceAudioTelemetry(),
                    onToggleMute = {},
                    onToggleSpeaker = {},
                    onToggleHold = {},
                    onToggleCamera = { camClicked++ },
                    onSwitchCamera = { switchClicked++ },
                    onHangup = { hangupClicked++ }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("call_peer_name").assertIsDisplayed().assertTextContains("Marc Kouassi")
        composeTestRule.onNodeWithTag("call_chronometer_text").assertIsDisplayed().assertTextContains("01:04")
        composeTestRule.onNodeWithTag("toggle_speaker_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_camera_button").performClick()
        composeTestRule.onNodeWithTag("switch_camera_button").performClick()
        composeTestRule.onNodeWithTag("toggle_mic_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_hold_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_dsp_badge").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hangup_call_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(camClicked == 1)
        assertTrue(switchClicked == 1)
        assertTrue(hangupClicked == 1)
    }

    @Test
    fun callHistoryScreen_showsUnifiedPhoneAndBravLogsAndFiltersCleanly() {
        var dialedNumber = ""
        val nowSec = System.currentTimeMillis() / 1000L
        val bravCalls = listOf(
            Call(
                id = "brav_1",
                callerId = "me_uid",
                callerName = "Moi",
                receiverId = "u2",
                receiverName = "Amina Diallo",
                callType = "video",
                status = "ended",
                durationSeconds = 95,
                createdAt = com.google.firebase.Timestamp(nowSec - 120, 0)
            )
        )
        val phoneLogs = listOf(
            com.example.telecom.DeviceCallLogEntry(
                id = "sys_42",
                number = "+2250701020304",
                formattedNumber = "+225 07 01 02 03 04",
                displayName = "Moussa Koné",
                direction = com.example.telecom.CallDirection.INCOMING,
                isMissed = true,
                timestampMs = (nowSec - 30) * 1000L,
                durationSeconds = 0L,
                localityBadge = "Abidjan",
                isKnownContact = true
            )
        )

        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                com.example.ui.screens.CallHistoryScreen(
                    currentUserId = "me_uid",
                    calls = bravCalls,
                    injectedDeviceCallLogs = phoneLogs,
                    onStartAudioCall = { _, _ -> },
                    onStartVideoCall = { _, _ -> },
                    onDialPhoneNumber = { dialedNumber = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Moussa Koné").assertIsDisplayed()
        composeTestRule.onNodeWithText("Amina Diallo").assertIsDisplayed()

        // Filter to phone calls only -> Amina (BRAV) hidden, Moussa remains
        composeTestRule.onNodeWithTag("calls_filter_phone").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Moussa Koné").assertIsDisplayed()
        composeTestRule.onNodeWithText("Amina Diallo").assertDoesNotExist()

        // Clicking the phone call row triggers onDialPhoneNumber
        composeTestRule.onNodeWithTag("device_call_row_sys_42").performClick()
        assertEquals("+2250701020304", dialedNumber)

        // Filter to BRAV calls only
        composeTestRule.onNodeWithTag("calls_filter_brav").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Amina Diallo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Moussa Koné").assertDoesNotExist()
    }
}
