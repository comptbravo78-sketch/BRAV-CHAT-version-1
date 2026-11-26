package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import com.example.local.AppPreferencesRepository
import com.example.local.AppPreferencesState
import com.example.local.ThemeMode
import com.example.messaging.BravFirebaseMessagingService
import com.example.network.WorkerApiClient
import com.example.network.WorkerRealtimeClient
import com.example.ui.BravMainApp
import com.example.ui.theme.BravChatTheme
import com.example.util.DefaultDialerHelper
import com.example.util.DefaultSmsHelper
import com.example.util.configureAppCheck

class MainActivity : FragmentActivity() {
    private var deepLinkConversationId by mutableStateOf<String?>(null)
    private var deepLinkCallId by mutableStateOf<String?>(null)
    private var deepLinkCallAction by mutableStateOf<String?>(null)
    private var deepLinkDialNumber by mutableStateOf<String?>(null)
    private var deepLinkSmsTarget by mutableStateOf<Pair<String, String>?>(null)
    private var deepLinkOpenNotifications by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // CRITICAL: Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)
        super.onCreate(savedInstanceState)
        BravFirebaseMessagingService.ensureChannels(this)
        enableEdgeToEdge()

        deepLinkConversationId = intent?.getStringExtra("extra_conversation_id")
        deepLinkCallId = intent?.getStringExtra("extra_call_id")
        deepLinkCallAction = intent?.getStringExtra("extra_call_action")
        deepLinkOpenNotifications = intent?.getBooleanExtra("extra_open_notifications", false) == true
        if (!deepLinkCallId.isNullOrBlank() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        deepLinkDialNumber = DefaultDialerHelper.extractDialNumberFromIntent(intent)
            ?: if (intent?.action == Intent.ACTION_CALL_BUTTON || intent?.action == Intent.ACTION_DIAL) "" else null
        deepLinkSmsTarget = DefaultSmsHelper.extractSmsTargetFromIntent(intent)

        setContent {
            val prefsRepository = remember { AppPreferencesRepository(applicationContext) }
            val workerApiClient = remember { WorkerApiClient() }
            remember(workerApiClient) {
                Coil.setImageLoader(workerApiClient.createCoilImageLoader(applicationContext))
                true
            }
            val realtimeClient = remember { WorkerRealtimeClient(workerApiClient) }
            val prefsState by prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPreferencesState())

            val darkTheme = when (prefsState.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            BravChatTheme(darkTheme = darkTheme) {
                BravMainApp(
                    prefsRepository = prefsRepository,
                    workerApiClient = workerApiClient,
                    realtimeClient = realtimeClient,
                    initialConversationId = deepLinkConversationId,
                    initialCallId = deepLinkCallId,
                    initialCallAction = deepLinkCallAction,
                    initialDialNumber = deepLinkDialNumber,
                    initialSmsTarget = deepLinkSmsTarget,
                    initialOpenNotifications = deepLinkOpenNotifications,
                    onConsumeInitialCallAction = { deepLinkCallAction = null },
                    onConsumeInitialDialNumber = { deepLinkDialNumber = null },
                    onConsumeInitialSmsTarget = { deepLinkSmsTarget = null },
                    onConsumeInitialOpenNotifications = { deepLinkOpenNotifications = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("extra_conversation_id")?.let { deepLinkConversationId = it }
        if (intent.getBooleanExtra("extra_open_notifications", false)) {
            deepLinkOpenNotifications = true
        }
        intent.getStringExtra("extra_call_id")?.let { callId ->
            deepLinkCallId = callId
            deepLinkCallAction = intent.getStringExtra("extra_call_action")
            if (callId.isNotBlank() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
        }
        if (intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_CALL_BUTTON || intent.action == Intent.ACTION_VIEW) {
            val extracted = DefaultDialerHelper.extractDialNumberFromIntent(intent)
            if (extracted != null || intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_CALL_BUTTON) {
                deepLinkDialNumber = extracted ?: ""
            }
        }
        DefaultSmsHelper.extractSmsTargetFromIntent(intent)?.let {
            deepLinkSmsTarget = it
        }
    }
}
