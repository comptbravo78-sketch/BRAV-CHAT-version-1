package com.example.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.local.AppPreferencesRepository
import com.example.local.AppPreferencesState
import com.example.local.ThemeMode
import com.example.ui.theme.BravChatTheme

class BravLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val prefsRepository = remember { AppPreferencesRepository(applicationContext) }
            val prefsState by prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPreferencesState())
            val darkTheme = when (prefsState.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            BravChatTheme(darkTheme = darkTheme) {
                BravLauncherScreen(prefsRepository = prefsRepository)
            }
        }
    }
}
