package com.example.telecom

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.CarrierCallOverlay
import com.example.ui.theme.BravChatTheme

/**
 * Full-screen system call activity shown on incoming and outgoing carrier calls,
 * including over the device lock screen when BRAV Chat is the default dialer.
 */
class CallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        configureLockscreenPresentation()
        super.onCreate(savedInstanceState)

        setContent {
            val state by CarrierCallController.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state.hasCall) {
                if (!state.hasCall) {
                    finishAndRemoveTask()
                }
            }

            BravChatTheme(darkTheme = isSystemInDarkTheme()) {
                if (state.hasCall) {
                    CarrierCallOverlay(state = state)
                }
            }
        }
    }

    private fun configureLockscreenPresentation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KeyguardManager::class.java)
            runCatching { km?.requestDismissKeyguard(this, null) }
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
