package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.crypto.E2eeCryptoManager
import com.example.launcher.LauncherRepository
import com.example.launcher.LauncherThemeStudioScreen
import com.example.local.AppPreferencesRepository
import com.example.local.BravLocalDatabase
import com.example.media.VoiceRecorderPlayer
import com.example.media.WebRtcCallManager
import com.example.model.NotificationCategory
import com.example.network.WorkerApiClient
import com.example.network.WorkerRealtimeClient
import com.example.network.YouTubeMusicRepository
import com.example.repository.AiTranslationRepository
import com.example.repository.CallRepository
import com.example.repository.ChatRepository
import com.example.repository.ContactSyncRepository
import com.example.repository.NotificationRepository
import com.example.repository.SmsRepository
import com.example.repository.SocialRepository
import com.example.repository.UserRepository
import com.example.telecom.CarrierCallController
import com.example.ui.components.BravGoldHairlineDivider
import com.example.ui.components.BravLogoMark
import com.example.ui.components.BravPatternIntensity
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravUnreadCountBadge
import com.example.ui.components.YouTubePersistentHost
import com.example.ui.components.bravAfricanPattern
import com.example.ui.components.rememberYouTubePlayerController
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravTheme
import com.example.ui.screens.AdminModerationScreen
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.CallHistoryScreen
import com.example.ui.screens.CallScreen
import com.example.ui.screens.CallTerminalOverlay
import com.example.ui.screens.CarrierCallOverlay
import com.example.ui.screens.ChatConversationScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.DialerModalSheet
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.IncomingCallBannerDialog
import com.example.ui.screens.LiveMapScreen
import com.example.ui.screens.NotificationsCenterScreen
import com.example.ui.screens.OffersMarketplaceScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.SmsConversationsScreen
import com.example.ui.screens.StatusesScreen
import com.example.ui.screens.YouTubeMusicScreen
import com.example.util.BiometricAuthManager
import com.example.util.CountryDialCodeHelper
import com.example.util.DefaultDialerHelper
import com.example.util.findFragmentActivity
import com.example.viewmodel.AuthUiState
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.BravViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Discussions", Icons.Rounded.ChatBubble),
    STATUSES("Actus", Icons.Rounded.Newspaper),
    CONTACTS("Contacts", Icons.Rounded.Contacts),
    CALLS("Appels", Icons.Rounded.Call),
    PROFILE("Profil", Icons.Rounded.Person)
}

private sealed interface SecondaryRoute {
    data object None : SecondaryRoute
    data class Conversation(val conversationId: String) : SecondaryRoute
    data class Sms(val initialAddress: String? = null, val initialBody: String? = null) : SecondaryRoute
    data object AiAssistant : SecondaryRoute
    data object Music : SecondaryRoute
    data object Statuses : SecondaryRoute
    data object LiveMap : SecondaryRoute
    data object Offers : SecondaryRoute
    data object Notifications : SecondaryRoute
    data object Admin : SecondaryRoute
    data object Settings : SecondaryRoute
    data object LauncherStudio : SecondaryRoute
}

@Composable
fun BravMainApp(
    prefsRepository: AppPreferencesRepository,
    workerApiClient: WorkerApiClient,
    realtimeClient: WorkerRealtimeClient,
    initialConversationId: String? = null,
    initialCallId: String? = null,
    initialCallAction: String? = null,
    initialDialNumber: String? = null,
    initialSmsTarget: Pair<String, String>? = null,
    initialOpenNotifications: Boolean = false,
    onConsumeInitialCallAction: () -> Unit = {},
    onConsumeInitialDialNumber: () -> Unit = {},
    onConsumeInitialSmsTarget: () -> Unit = {},
    onConsumeInitialOpenNotifications: () -> Unit = {}
) {
    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                val crypto = E2eeCryptoManager(app)
                AuthViewModel(
                    userRepository = UserRepository(db, crypto, workerApiClient),
                    prefsRepository = prefsRepository,
                    workerApiClient = workerApiClient,
                    realtimeClient = realtimeClient
                )
            }
        }
    )

    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val isSigningIn by authViewModel.isSigningIn.collectAsStateWithLifecycle()
    val authError by authViewModel.authError.collectAsStateWithLifecycle()

    when (val state = authState) {
        AuthUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AuthUiState.Unauthenticated -> {
            SignInScreen(
                isLoading = isSigningIn,
                errorMessage = authError,
                onGoogleIdToken = authViewModel::signInWithGoogleIdToken,
                onSetLoading = authViewModel::setLoading,
                onError = authViewModel::setAuthError
            )
        }
        is AuthUiState.Authenticated -> {
            val uid = state.firebaseUser.uid
            key(uid) {
                AuthenticatedBravShell(
                    currentUserId = uid,
                    userEmail = state.firebaseUser.email,
                    prefsRepository = prefsRepository,
                    workerApiClient = workerApiClient,
                    realtimeClient = realtimeClient,
                    initialConversationId = initialConversationId,
                    initialCallId = initialCallId,
                    initialCallAction = initialCallAction,
                    initialDialNumber = initialDialNumber,
                    initialSmsTarget = initialSmsTarget,
                    initialOpenNotifications = initialOpenNotifications,
                    onConsumeInitialCallAction = onConsumeInitialCallAction,
                    onConsumeInitialDialNumber = onConsumeInitialDialNumber,
                    onConsumeInitialSmsTarget = onConsumeInitialSmsTarget,
                    onConsumeInitialOpenNotifications = onConsumeInitialOpenNotifications,
                    onSignOut = { authViewModel.signOut() }
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedBravShell(
    currentUserId: String,
    userEmail: String?,
    prefsRepository: AppPreferencesRepository,
    workerApiClient: WorkerApiClient,
    realtimeClient: WorkerRealtimeClient,
    initialConversationId: String?,
    initialCallId: String?,
    initialCallAction: String?,
    initialDialNumber: String?,
    initialSmsTarget: Pair<String, String>?,
    initialOpenNotifications: Boolean,
    onConsumeInitialCallAction: () -> Unit,
    onConsumeInitialDialNumber: () -> Unit,
    onConsumeInitialSmsTarget: () -> Unit,
    onConsumeInitialOpenNotifications: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel: BravViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                val crypto = E2eeCryptoManager(app)
                val localDb = BravLocalDatabase.getInstance(app)
                val notifRepo = NotificationRepository(db, workerApiClient)
                val callRepo = CallRepository(db, workerApiClient, notifRepo)
                BravViewModel(
                    currentUserId = currentUserId,
                    userRepository = UserRepository(db, crypto, workerApiClient, notifRepo),
                    contactRepository = ContactSyncRepository(db, workerApiClient, app, notifRepo),
                    chatRepository = ChatRepository(db, workerApiClient, crypto, localDb, notifRepo),
                    callRepository = callRepo,
                    socialRepository = SocialRepository(db, workerApiClient, crypto, notifRepo),
                    notificationRepository = notifRepo,
                    aiTranslationRepository = AiTranslationRepository(workerApiClient, localDb),
                    prefsRepository = prefsRepository,
                    workerApiClient = workerApiClient,
                    realtimeClient = realtimeClient,
                    voicePlayer = VoiceRecorderPlayer(app, workerApiClient),
                    webRtcCallManager = WebRtcCallManager(app, workerApiClient, callRepo),
                    appContext = app,
                    localDb = localDb,
                    smsRepository = SmsRepository(app)
                )
            }
        }
    )

    val youTubeMusicRepository = remember(workerApiClient) {
        YouTubeMusicRepository(workerApiClient.httpClient)
    }
    val youTubeController = rememberYouTubePlayerController()
    var showDialerSheet by remember { mutableStateOf(false) }
    var dialerInitialNumber by remember { mutableStateOf("") }

    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var secondaryRoute by remember {
        mutableStateOf<SecondaryRoute>(
            when {
                !initialConversationId.isNullOrBlank() -> SecondaryRoute.Conversation(initialConversationId)
                initialOpenNotifications -> SecondaryRoute.Notifications
                initialSmsTarget != null -> SecondaryRoute.Sms(initialSmsTarget.first, initialSmsTarget.second)
                else -> SecondaryRoute.None
            }
        )
    }

    val appPrefs by viewModel.appPrefs.collectAsStateWithLifecycle()
    val hasLoadedAppPrefs by viewModel.hasLoadedAppPrefs.collectAsStateWithLifecycle()
    var sessionUnlocked by rememberSaveable(currentUserId) { mutableStateOf(false) }
    var showWelcomeSecuritySetup by remember { mutableStateOf(false) }

    LaunchedEffect(currentUserId, userEmail) {
        viewModel.checkAdmin(userEmail)
    }
    LaunchedEffect(initialCallId, initialCallAction) {
        if (!initialCallId.isNullOrBlank()) {
            viewModel.handleDeepLinkCall(initialCallId, initialCallAction)
            onConsumeInitialCallAction()
        }
    }
    LaunchedEffect(initialDialNumber) {
        if (initialDialNumber != null) {
            dialerInitialNumber = initialDialNumber
            showDialerSheet = true
            onConsumeInitialDialNumber()
        }
    }
    LaunchedEffect(initialSmsTarget) {
        if (initialSmsTarget != null) {
            secondaryRoute = SecondaryRoute.Sms(initialSmsTarget.first, initialSmsTarget.second)
            onConsumeInitialSmsTarget()
        }
    }
    LaunchedEffect(initialOpenNotifications) {
        if (initialOpenNotifications) {
            secondaryRoute = SecondaryRoute.Notifications
            onConsumeInitialOpenNotifications()
        }
    }
    LaunchedEffect(hasLoadedAppPrefs, appPrefs.biometricPromptOffered, appPrefs.biometricSessionLockEnabled) {
        if (hasLoadedAppPrefs && !appPrefs.biometricPromptOffered && !appPrefs.biometricSessionLockEnabled) {
            showWelcomeSecuritySetup = true
        } else if (appPrefs.biometricPromptOffered || appPrefs.biometricSessionLockEnabled) {
            showWelcomeSecuritySetup = false
        }
    }

    val defaultDialerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (DefaultDialerHelper.isBravDefaultDialer(context)) {
            viewModel.showMessage("BRAV Chat est maintenant votre numérotation par défaut")
        }
    }

    fun requestDefaultDialer() {
        if (DefaultDialerHelper.isBravDefaultDialer(context)) {
            viewModel.showMessage("BRAV Chat est déjà la numérotation par défaut")
            return
        }
        val intent = DefaultDialerHelper.createRequestDefaultDialerIntent(context)
            ?: DefaultDialerHelper.createDefaultAppsSettingsIntent()
        runCatching { defaultDialerLauncher.launch(intent) }
            .onFailure {
                runCatching { defaultDialerLauncher.launch(DefaultDialerHelper.createDefaultAppsSettingsIntent()) }
                    .onFailure { viewModel.showMessage("Impossible d'ouvrir les paramètres de compositeur") }
            }
    }

    val defaultHomeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (LauncherRepository.isBravDefaultLauncher(context)) {
            viewModel.showMessage("BRAV Chat est maintenant l'application d'accueil par défaut")
        }
    }

    fun requestDefaultLauncher() {
        LauncherRepository.setLauncherComponentEnabled(context, true)
        scope.launch { prefsRepository.setLauncherEnabled(true) }
        if (LauncherRepository.isBravDefaultLauncher(context)) {
            viewModel.showMessage("BRAV Chat est déjà l'application d'accueil par défaut")
            return
        }
        val intent = LauncherRepository.createRequestDefaultLauncherIntent(context)
        runCatching { defaultHomeLauncher.launch(intent) }
            .onFailure {
                viewModel.showMessage("Ouvrez Paramètres > Applications > Appli d'accueil pour choisir BRAV Chat")
            }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val smsThreads by viewModel.smsThreads.collectAsStateWithLifecycle()
    val prefsMap by viewModel.conversationPrefs.collectAsStateWithLifecycle()
    val presenceMap by viewModel.presenceMap.collectAsStateWithLifecycle()
    val typingMap by viewModel.typingConversations.collectAsStateWithLifecycle()
    val savedContacts by viewModel.savedContactUsers.collectAsStateWithLifecycle()
    val phoneContacts by viewModel.phoneContacts.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val calls by viewModel.callHistory.collectAsStateWithLifecycle()
    val incomingCall by viewModel.incomingCall.collectAsStateWithLifecycle()
    val incomingCallPeer by viewModel.incomingCallPeer.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
    val activeCallPeer by viewModel.activeCallPeer.collectAsStateWithLifecycle()
    val terminalCallState by viewModel.terminalCallState.collectAsStateWithLifecycle()
    val carrierCallState by CarrierCallController.uiState.collectAsStateWithLifecycle()
    val knownUsersDirectory by viewModel.knownUsersDirectory.collectAsStateWithLifecycle()
    val offers by viewModel.offers.collectAsStateWithLifecycle()
    val certEligibility by viewModel.certificationEligibility.collectAsStateWithLifecycle()
    val isCurrentUserVerifiedDesigner = remember(currentUserId, certEligibility?.exemptDesigner) {
        viewModel.userRepository.isVerifiedDesignerAccount() || certEligibility?.exemptDesigner == true
    }
    val verifiedUserIds = remember(knownUsersDirectory, isCurrentUserVerifiedDesigner, currentUserId) {
        buildSet {
            knownUsersDirectory.values.filter { it.isVerifiedActive() }.forEach { add(it.userId) }
            if (isCurrentUserVerifiedDesigner) add(currentUserId)
        }
    }
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val unreadCountByConversation by viewModel.unreadCountByConversation.collectAsStateWithLifecycle()
    val totalUnreadChatsCount by viewModel.totalUnreadChatsCount.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val notificationCounters by viewModel.notificationCounters.collectAsStateWithLifecycle()
    val incomingRequests by viewModel.incomingRequests.collectAsStateWithLifecycle()

    val smsUnreadCount = remember(smsThreads) { smsThreads.sumOf { it.unreadCount } }

    fun badgeCountForTab(tab: MainTab): Int = when (tab) {
        MainTab.HOME -> totalUnreadChatsCount
        MainTab.STATUSES -> notificationCounters.actusUnread
        MainTab.CONTACTS -> maxOf(incomingRequests.size, notificationCounters.contactsUnread)
        MainTab.CALLS -> notificationCounters.callsUnread
        MainTab.PROFILE -> notificationCounters.accountSecurityUnread
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = secondaryRoute,
            transitionSpec = { fadeIn(tween(190)) togetherWith fadeOut(tween(150)) },
            label = "secondary_route_transition"
        ) { sec ->
            when (sec) {
                is SecondaryRoute.Conversation -> {
                    ChatConversationScreen(
                        conversationId = sec.conversationId,
                        viewModel = viewModel,
                        onOpenYouTube = { url, title ->
                            if (youTubeController.openUrl(url, title)) {
                                val vid = youTubeController.videoId
                                if (!vid.isNullOrBlank()) {
                                    scope.launch {
                                        val resolved = youTubeMusicRepository.resolveTrackByVideoId(vid)
                                        if (resolved != null) {
                                            youTubeController.replaceCurrentTrackMeta(resolved)
                                            val upNext = youTubeMusicRepository.fetchUpNext(
                                                seedTitle = resolved.title,
                                                seedChannel = resolved.channelTitle,
                                                currentVideoId = resolved.videoId,
                                                excludedIds = youTubeController.failedVideoIds
                                            )
                                            youTubeController.appendUpNext(upNext)
                                        }
                                    }
                                }
                            }
                        },
                        onBack = { secondaryRoute = SecondaryRoute.None }
                    )
                }
                is SecondaryRoute.Sms -> {
                    SmsConversationsScreen(
                        viewModel = viewModel,
                        initialAddress = sec.initialAddress,
                        initialBody = sec.initialBody,
                        onBack = { secondaryRoute = SecondaryRoute.None }
                    )
                }
                SecondaryRoute.AiAssistant -> {
                    AiAssistantScreen(
                        viewModel = viewModel,
                        onBack = { secondaryRoute = SecondaryRoute.None }
                    )
                }
                SecondaryRoute.Music -> {
                    YouTubeMusicScreen(
                        repository = youTubeMusicRepository,
                        controller = youTubeController,
                        onBack = { secondaryRoute = SecondaryRoute.None }
                    )
                }
                SecondaryRoute.Statuses -> {
                    StatusesScreen(
                        viewModel = viewModel,
                        onOpenConversation = { cid -> secondaryRoute = SecondaryRoute.Conversation(cid) },
                        onOpenDiscussions = {
                            currentTab = MainTab.HOME
                            secondaryRoute = SecondaryRoute.None
                        },
                        onBack = { secondaryRoute = SecondaryRoute.None }
                    )
                }
                SecondaryRoute.LiveMap -> {
                    LiveMapScreen(
                        viewModel = viewModel,
                        onBack = { secondaryRoute = SecondaryRoute.None }
                    )
                }
                SecondaryRoute.Offers -> {
                    OffersMarketplaceScreen(
                        viewModel = viewModel,
                        onOpenConversation = { cid -> secondaryRoute = SecondaryRoute.Conversation(cid) },
                        onBack = { secondaryRoute = SecondaryRoute.None }
                    )
                }
                SecondaryRoute.Notifications -> {
                    NotificationsCenterScreen(
                        notifications = notifications,
                        counters = notificationCounters,
                        onNotificationClick = { notif ->
                            viewModel.markNotificationRead(notif)
                            when (notif.categoryEnum) {
                                NotificationCategory.MESSAGES -> {
                                    if (notif.refId.isNotBlank()) {
                                        secondaryRoute = SecondaryRoute.Conversation(notif.refId)
                                    } else {
                                        currentTab = MainTab.HOME
                                        secondaryRoute = SecondaryRoute.None
                                    }
                                }
                                NotificationCategory.ACTUS -> {
                                    currentTab = MainTab.STATUSES
                                    secondaryRoute = SecondaryRoute.None
                                }
                                NotificationCategory.CONTACTS -> {
                                    currentTab = MainTab.CONTACTS
                                    secondaryRoute = SecondaryRoute.None
                                }
                                NotificationCategory.CALLS -> {
                                    currentTab = MainTab.CALLS
                                    secondaryRoute = SecondaryRoute.None
                                }
                                NotificationCategory.OFFERS_JOBS -> {
                                    secondaryRoute = SecondaryRoute.Offers
                                }
                                NotificationCategory.ACCOUNT_SECURITY, NotificationCategory.ALL -> {
                                    currentTab = MainTab.PROFILE
                                    secondaryRoute = SecondaryRoute.None
                                }
                            }
                        },
                        onMarkAllRead = { cat -> viewModel.markAllNotificationsRead(cat) },
                        onDeleteNotification = { id -> viewModel.deleteNotification(id) },
                        onBack = { secondaryRoute = SecondaryRoute.None }
                    )
                }
                SecondaryRoute.Admin -> {
                    AdminModerationScreen(
                        viewModel = viewModel,
                        onBack = { secondaryRoute = SecondaryRoute.None }
                    )
                }
                SecondaryRoute.Settings -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        isAdmin = isAdmin,
                        onBack = { secondaryRoute = SecondaryRoute.None },
                        onOpenProfile = {
                            secondaryRoute = SecondaryRoute.None
                            currentTab = MainTab.PROFILE
                        },
                        onOpenOffers = { secondaryRoute = SecondaryRoute.Offers },
                        onOpenAdmin = { secondaryRoute = SecondaryRoute.Admin },
                        onOpenAiAssistant = { secondaryRoute = SecondaryRoute.AiAssistant },
                        onOpenLauncherStudio = { secondaryRoute = SecondaryRoute.LauncherStudio },
                        onSignOut = onSignOut
                    )
                }
                SecondaryRoute.LauncherStudio -> {
                    LauncherThemeStudioScreen(
                        prefsRepository = prefsRepository,
                        onSyncCloudflare = { themeId, shape, sizeDp ->
                            scope.launch {
                                viewModel.workerApiClient.saveLauncherProfile(
                                    themeId = themeId,
                                    iconShape = shape,
                                    iconSizeDp = sizeDp,
                                    configJson = appPrefs.launcherCustomIconsJson
                                )
                            }
                        },
                        onBack = { secondaryRoute = SecondaryRoute.Settings }
                    )
                }
                SecondaryRoute.None -> {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val useRail = maxWidth >= 600.dp
                        val lux = BravTheme.colors
                        Scaffold(
                            containerColor = lux.ivoryCanvas,
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            bottomBar = {
                                if (!useRail) {
                                    Surface(
                                        color = lux.translucentCardSurface,
                                        shadowElevation = 0.dp
                                    ) {
                                        Column {
                                            BravGoldHairlineDivider(withGoldTouch = true)
                                            NavigationBar(
                                                containerColor = Color.Transparent,
                                                tonalElevation = 0.dp,
                                                windowInsets = WindowInsets(0, 0, 0, 0),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                            ) {
                                                val itemColors = NavigationBarItemDefaults.colors(
                                                    selectedIconColor = lux.titlePurple,
                                                    selectedTextColor = lux.titlePurple,
                                                    indicatorColor = BravPurplePrimary.copy(alpha = 0.12f),
                                                    unselectedIconColor = lux.inkSecondary,
                                                    unselectedTextColor = lux.inkSecondary
                                                )
                                                MainTab.entries.forEach { tab ->
                                                    val badgeCount = badgeCountForTab(tab)
                                                    NavigationBarItem(
                                                        selected = currentTab == tab,
                                                        onClick = { currentTab = tab },
                                                        colors = itemColors,
                                                        icon = {
                                                            BadgedBox(
                                                                badge = {
                                                                    if (badgeCount > 0) {
                                                                        BravUnreadCountBadge(count = badgeCount)
                                                                    }
                                                                }
                                                            ) {
                                                                Icon(
                                                                    tab.icon,
                                                                    contentDescription = tab.label,
                                                                    modifier = Modifier.size(22.dp)
                                                                )
                                                            }
                                                        },
                                                        label = {
                                                            Text(
                                                                text = tab.label,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Medium,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                if (useRail) {
                                    val railColors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = lux.titlePurple,
                                        selectedTextColor = lux.titlePurple,
                                        indicatorColor = BravPurplePrimary.copy(alpha = 0.12f),
                                        unselectedIconColor = lux.inkSecondary,
                                        unselectedTextColor = lux.inkSecondary
                                    )
                                    NavigationRail(
                                        containerColor = lux.translucentCardSurface,
                                        header = {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            ) {
                                                BravLogoMark(size = 34.dp)
                                            }
                                        }
                                    ) {
                                        MainTab.entries.forEach { tab ->
                                            val badgeCount = badgeCountForTab(tab)
                                            NavigationRailItem(
                                                selected = currentTab == tab,
                                                onClick = { currentTab = tab },
                                                colors = railColors,
                                                icon = {
                                                    BadgedBox(
                                                        badge = {
                                                            if (badgeCount > 0) {
                                                                BravUnreadCountBadge(count = badgeCount)
                                                            }
                                                        }
                                                    ) {
                                                        Icon(tab.icon, contentDescription = tab.label)
                                                    }
                                                },
                                                label = { Text(tab.label, fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Medium) }
                                            )
                                        }
                                        NavigationRailItem(
                                            selected = false,
                                            onClick = { secondaryRoute = SecondaryRoute.Music },
                                            colors = railColors,
                                            icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = "Musique") },
                                            label = { Text("Musique") }
                                        )
                                        NavigationRailItem(
                                            selected = false,
                                            onClick = {
                                                dialerInitialNumber = ""
                                                showDialerSheet = true
                                            },
                                            colors = railColors,
                                            icon = { Icon(Icons.Rounded.Dialpad, contentDescription = "Clavier") },
                                            label = { Text("Clavier") }
                                        )
                                        NavigationRailItem(
                                            selected = false,
                                            onClick = { secondaryRoute = SecondaryRoute.Settings },
                                            colors = railColors,
                                            icon = { Icon(Icons.Rounded.Settings, contentDescription = "Paramètres") },
                                            label = { Text("Paramètres") }
                                        )
                                    }
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                    AnimatedContent(
                                        targetState = currentTab,
                                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                                        label = "main_tab_transition"
                                    ) { tab ->
                                        when (tab) {
                                            MainTab.HOME -> HomeDashboardScreen(
                                                currentUserId = currentUserId,
                                                conversations = conversations,
                                                prefsMap = prefsMap,
                                                unreadCountByConversation = unreadCountByConversation,
                                                notificationCounters = notificationCounters,
                                                presenceMap = presenceMap,
                                                typingConversations = typingMap,
                                                savedContacts = savedContacts,
                                                hasPinConfigured = appPrefs.chatLockPinHash.isNotBlank(),
                                                biometricLockEnabled = appPrefs.biometricSessionLockEnabled,
                                                isAdmin = isAdmin,
                                                compactLayout = appPrefs.compactLayoutEnabled,
                                                smsUnreadCount = smsUnreadCount,
                                                offersCount = offers.size,
                                                verifiedUserIds = verifiedUserIds,
                                                aiContactRulesJson = appPrefs.aiAutoReplyContactRulesJson,
                                                globalBravAutoReplyEnabled = appPrefs.aiAutoReplyBravEnabled,
                                                onToggleConversationAi = { key, label ->
                                                    viewModel.toggleConversationDirectAi(
                                                        contactKey = key,
                                                        contactLabel = label,
                                                        globalEnabled = appPrefs.aiAutoReplyBravEnabled
                                                    )
                                                },
                                                onVerifyPin = { pin -> viewModel.prefsRepository.verifyChatLockPin(pin) },
                                                onOpenConversation = { cid -> secondaryRoute = SecondaryRoute.Conversation(cid) },
                                                onCreateGroup = { members, name, desc ->
                                                    scope.launch {
                                                        viewModel.createGroup(members, name, desc).onSuccess { conv ->
                                                            secondaryRoute = SecondaryRoute.Conversation(conv.id)
                                                        }
                                                    }
                                                },
                                                onJoinGroupByCode = { code ->
                                                    viewModel.joinGroupByCode(code) { cid ->
                                                        secondaryRoute = SecondaryRoute.Conversation(cid)
                                                    }
                                                },
                                                onUpdatePref = { cid, archived, pinned, muted, locked, unread, hidden ->
                                                    scope.launch {
                                                        viewModel.chatRepository.updateConversationPref(
                                                            myUid = currentUserId,
                                                            conversationId = cid,
                                                            archived = archived,
                                                            pinned = pinned,
                                                            muted = muted,
                                                            locked = locked,
                                                            markedUnread = unread,
                                                            hiddenLocally = hidden
                                                        )
                                                    }
                                                },
                                                onDeleteConversation = { cid -> viewModel.deleteConversation(cid) },
                                                onNavigateToContacts = { currentTab = MainTab.CONTACTS },
                                                onOpenStatuses = { currentTab = MainTab.STATUSES },
                                                onOpenDialer = {
                                                    dialerInitialNumber = ""
                                                    showDialerSheet = true
                                                },
                                                onRequestDefaultDialer = { requestDefaultDialer() },
                                                onRequestDefaultLauncher = { requestDefaultLauncher() },
                                                onOpenSms = { secondaryRoute = SecondaryRoute.Sms() },
                                                onOpenAiAssistant = { secondaryRoute = SecondaryRoute.AiAssistant },
                                                onOpenMusic = { secondaryRoute = SecondaryRoute.Music },
                                                onOpenMap = { secondaryRoute = SecondaryRoute.LiveMap },
                                                onOpenOffers = { secondaryRoute = SecondaryRoute.Offers },
                                                onOpenNotifications = { secondaryRoute = SecondaryRoute.Notifications },
                                                onOpenProfile = { currentTab = MainTab.PROFILE },
                                                onOpenSettings = { secondaryRoute = SecondaryRoute.Settings },
                                                onOpenAdmin = { secondaryRoute = SecondaryRoute.Admin }
                                            )
                                            MainTab.STATUSES -> StatusesScreen(
                                                viewModel = viewModel,
                                                onOpenConversation = { cid -> secondaryRoute = SecondaryRoute.Conversation(cid) },
                                                onOpenDiscussions = {
                                                    currentTab = MainTab.HOME
                                                    secondaryRoute = SecondaryRoute.None
                                                },
                                                onBack = null
                                            )
                                            MainTab.CONTACTS -> ContactsScreen(
                                                viewModel = viewModel,
                                                onOpenConversation = { cid -> secondaryRoute = SecondaryRoute.Conversation(cid) },
                                                onOpenSms = { addr -> secondaryRoute = SecondaryRoute.Sms(initialAddress = addr) }
                                            )
                                            MainTab.CALLS -> CallHistoryScreen(
                                                currentUserId = currentUserId,
                                                calls = calls,
                                                savedContacts = savedContacts,
                                                defaultCountryIso = appPrefs.countryIso,
                                                onStartAudioCall = { pUid, pName -> viewModel.startOutgoingCall(pUid, pName, isVideo = false) },
                                                onStartVideoCall = { pUid, pName -> viewModel.startOutgoingCall(pUid, pName, isVideo = true) },
                                                onOpenDialer = {
                                                    dialerInitialNumber = ""
                                                    showDialerSheet = true
                                                },
                                                onDialPhoneNumber = { num ->
                                                    dialerInitialNumber = num
                                                    showDialerSheet = true
                                                },
                                                onOpenSms = { secondaryRoute = SecondaryRoute.Sms() },
                                                onDeleteCall = { callId -> viewModel.deleteCallHistoryItem(callId) },
                                                onClearHistory = { viewModel.clearAllCallHistory() }
                                            )
                                            MainTab.PROFILE -> ProfileScreen(
                                                viewModel = viewModel,
                                                onOpenSettings = { secondaryRoute = SecondaryRoute.Settings },
                                                onOpenConversation = { cid -> secondaryRoute = SecondaryRoute.Conversation(cid) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Single persistent YouTube Music host across all tabs/screens and hidden-video audio mode
        if (youTubeController.isVisible) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().zIndex(92f)) {
                val isCompactBottomNav = secondaryRoute == SecondaryRoute.None && maxWidth < 600.dp
                YouTubePersistentHost(
                    controller = youTubeController,
                    activeCallPresent = activeCall != null || carrierCallState.hasCall,
                    minimizedBottomOffset = if (isCompactBottomNav) 80.dp else 8.dp,
                    onNeedMoreTracks = { seed, excluded ->
                        youTubeMusicRepository.fetchUpNext(
                            seedTitle = seed.title,
                            seedChannel = seed.channelTitle,
                            currentVideoId = seed.videoId,
                            excludedIds = excluded
                        )
                    }
                )
            }
        }

        if (showDialerSheet) {
            DialerModalSheet(
                initialNumber = dialerInitialNumber,
                defaultCountryIso = appPrefs.countryIso,
                savedContacts = savedContacts,
                phoneContacts = phoneContacts,
                searchedUsers = searchResults,
                onLookupNumber = { q -> viewModel.searchUsers(q) },
                onStartBravAudioCall = { pUid, pName -> viewModel.startOutgoingCall(pUid, pName, isVideo = false) },
                onStartBravVideoCall = { pUid, pName -> viewModel.startOutgoingCall(pUid, pName, isVideo = true) },
                onOpenSmsComposer = { number ->
                    showDialerSheet = false
                    secondaryRoute = SecondaryRoute.Sms(initialAddress = number)
                },
                onFeedback = { msg -> viewModel.showMessage(msg) },
                onDismiss = { showDialerSheet = false }
            )
        }

        // Incoming call full-screen overlay when no active call is open
        incomingCall?.let { incoming ->
            if (activeCall == null) {
                IncomingCallBannerDialog(
                    call = incoming,
                    peerUser = incomingCallPeer,
                    soundEnabled = appPrefs.notificationsEnabled && appPrefs.notifySoundEnabled,
                    vibrationEnabled = appPrefs.notificationsEnabled && appPrefs.notifyVibrationEnabled,
                    onAccept = { viewModel.acceptIncomingCall(incoming) },
                    onReject = { viewModel.rejectIncomingCall(incoming) }
                )
            }
        }

        // Full-screen active WebRTC audio/video CallScreen
        activeCall?.let { call ->
            CallScreen(
                call = call,
                currentUserId = currentUserId,
                peerUser = activeCallPeer,
                appPrefs = appPrefs,
                webRtcCallManager = viewModel.webRtcCallManager,
                onToggleHold = { viewModel.toggleCallHold() },
                onHangup = { viewModel.endActiveCall(missed = call.status == "calling") }
            )
        }

        // Native GSM/Telecom InCallService overlay when acting as default dialer
        if (carrierCallState.hasCall && activeCall == null && incomingCall == null) {
            CarrierCallOverlay(
                state = carrierCallState,
                onSubmitCallerReport = { num, cat, label, details ->
                    viewModel.submitCallerReport(
                        rawNumber = num,
                        category = cat,
                        label = label,
                        details = details
                    )
                }
            )
        }

        // Brief terminal call status overlay (Terminé / Refusé / Aucune réponse)
        terminalCallState?.let { (endedCall, reason) ->
            if (activeCall == null && incomingCall == null) {
                val peerUid = if (endedCall.callerId == currentUserId) endedCall.receiverId else endedCall.callerId
                val endedPeer = savedContacts.firstOrNull { it.userId == peerUid }
                CallTerminalOverlay(
                    call = endedCall,
                    reason = reason,
                    currentUserId = currentUserId,
                    peerUser = endedPeer,
                    onDismiss = { viewModel.dismissTerminalCallBanner() }
                )
            }
        }

        // Biometric Session Lock Gate
        if (appPrefs.biometricSessionLockEnabled && !sessionUnlocked && activeCall == null && incomingCall == null) {
            BiometricSessionLockGate(
                onUnlocked = { sessionUnlocked = true }
            )
        }

        // One-time Country & Biometric Security Welcome Setup
        if (showWelcomeSecuritySetup && !appPrefs.biometricPromptOffered) {
            WelcomeCountryAndBiometricDialog(
                initialCountryIso = appPrefs.countryIso,
                onComplete = { iso, dialCode, enableBio ->
                    showWelcomeSecuritySetup = false
                    scope.launch {
                        prefsRepository.setCountryAndDialCode(iso, dialCode)
                        prefsRepository.markBiometricPromptOffered()
                        if (enableBio) {
                            prefsRepository.setBiometricSessionLock(true)
                            sessionUnlocked = true
                        }
                    }
                },
                onDismiss = {
                    showWelcomeSecuritySetup = false
                    scope.launch { prefsRepository.markBiometricPromptOffered() }
                }
            )
        }
    }
}

@Composable
private fun BiometricSessionLockGate(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun triggerAuth() {
        val activity = context.findFragmentActivity() ?: run {
            onUnlocked()
            return
        }
        BiometricAuthManager.authenticate(
            activity = activity,
            title = "BRAV Chat verrouillé",
            subtitle = "Confirmez votre empreinte, visage ou code appareil",
            useDeviceCredentialFallback = true,
            onSuccess = onUnlocked,
            onError = { errorMsg = it }
        )
    }

    LaunchedEffect(Unit) {
        triggerAuth()
    }

    val lux = BravTheme.colors
    Surface(
        modifier = Modifier.fillMaxSize().zIndex(120f).testTag("biometric_lock_gate"),
        color = lux.ivoryCanvas
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .bravAfricanPattern(
                    pattern = BravPatternType.SOLAR_ADINKRA,
                    primaryColor = lux.watermarkGold,
                    secondaryColor = lux.watermarkViolet,
                    opacity = BravPatternIntensity.LIGHT_DECOR
                )
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.widthIn(max = 380.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(18.dp).size(38.dp)
                    )
                }
                Text(
                    text = "BRAV Chat protégé",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Votre session est protégée par authentification biométrique.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { triggerAuth() },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Déverrouiller")
                }
            }
        }
    }
}

@Composable
private fun WelcomeCountryAndBiometricDialog(
    initialCountryIso: String,
    onComplete: (iso: String, dialCode: String, enableBiometric: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val detected = remember(initialCountryIso) {
        CountryDialCodeHelper.byIso(initialCountryIso) ?: CountryDialCodeHelper.detectFromDevice(context)
    }
    var selectedCountry by remember { mutableStateOf(detected) }
    val bioAvailable = remember { BiometricAuthManager.isBiometricOrDeviceCredentialAvailable(context) }
    var enableBiometric by remember { mutableStateOf(false) }

    val locPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                val (gpsCountry, _) = CountryDialCodeHelper.detectWithGpsIfPermitted(context)
                selectedCountry = gpsCountry
            }
        }
    }

    val lux = BravTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = lux.modalSurface,
        icon = { Icon(Icons.Rounded.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Configuration rapide", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Indicatif régional détecté pour vos appels et contacts :",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = lux.translucentSubtleSurface,
                    border = BorderStroke(0.75.dp, lux.violetContour),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${selectedCountry.nameFr} (${selectedCountry.dialCode})",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Code ISO : ${selectedCountry.iso}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                scope.launch {
                                    val (gpsCountry, _) = CountryDialCodeHelper.detectWithGpsIfPermitted(context)
                                    selectedCountry = gpsCountry
                                }
                            } else {
                                locPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        }) {
                            Text("GPS")
                        }
                    }
                }
                if (bioAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Verrouillage biométrique", fontWeight = FontWeight.Medium)
                            Text(
                                "Protéger l'ouverture de l'app par empreinte / visage.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enableBiometric,
                            onCheckedChange = { enableBiometric = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onComplete(selectedCountry.iso, selectedCountry.dialCode, enableBiometric)
            }) {
                Text("Valider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Plus tard") }
        }
    )
}
