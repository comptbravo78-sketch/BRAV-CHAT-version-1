package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.local.AppPreferencesState
import com.example.local.ThemeMode
import com.example.media.VoiceProcessingConfig
import com.example.model.User
import com.example.model.UserDevice
import com.example.model.UserPrivateInfo
import com.example.model.VerificationStatus
import com.example.network.LoginSessionEntry
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravLuxuryCard
import com.example.ui.components.BravLuxuryHeader
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.components.ProfileCoverArtPreset
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.viewmodel.BravViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: BravViewModel,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenOffers: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenAiAssistant: () -> Unit = {},
    onOpenLauncherStudio: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val privateInfo by viewModel.privateInfo.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val appPrefs by viewModel.appPrefs.collectAsStateWithLifecycle()
    val myVerificationRequests by viewModel.myVerificationRequests.collectAsStateWithLifecycle()
    val certEligibility by viewModel.certificationEligibility.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val loginHistory by viewModel.loginHistory.collectAsStateWithLifecycle()
    val followerUsers by viewModel.followerUsers.collectAsStateWithLifecycle()
    val followingUsers by viewModel.followingUsers.collectAsStateWithLifecycle()
    val incomingFollowRequests by viewModel.incomingFollowRequests.collectAsStateWithLifecycle()
    val outgoingFollowRequests by viewModel.outgoingFollowRequests.collectAsStateWithLifecycle()
    val myInvites by viewModel.myInvites.collectAsStateWithLifecycle()
    val firebaseEmail = remember { Firebase.auth.currentUser?.email.orEmpty() }

    val isDesignerAccount = remember(
        currentUser?.userId,
        currentUser?.verifiedOrgName,
        certEligibility?.exemptDesigner,
        firebaseEmail
    ) {
        viewModel.userRepository.isVerifiedDesignerAccount() ||
            certEligibility?.exemptDesigner == true ||
            (currentUser?.isVerifiedActive() == true &&
                currentUser?.verifiedOrgName?.contains("Concepteur", ignoreCase = true) == true)
    }

    val verificationStatus = when {
        isDesignerAccount -> VerificationStatus.VERIFIED
        else -> currentUser?.effectiveVerificationStatus()
            ?: myVerificationRequests.firstOrNull()?.status?.let { VerificationStatus.fromRaw(it) }
            ?: VerificationStatus.NONE
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showMicTestDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showVerificationSheet by remember { mutableStateOf(false) }
    var showFollowSheet by remember { mutableStateOf(false) }
    var showInviteSheet by remember { mutableStateOf(false) }
    var legalDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var cacheBytes by remember { mutableLongStateOf(viewModel.prefsRepository.computeLocalCacheSizeBytes()) }

    SettingsHierarchicalContent(
        currentUser = currentUser,
        privateInfo = privateInfo,
        firebaseEmail = firebaseEmail,
        appPrefs = appPrefs,
        blockedCount = blockedUsers.size,
        isAdmin = isAdmin,
        cacheBytes = cacheBytes,
        verificationStatus = verificationStatus,
        isDesignerAccount = isDesignerAccount,
        devices = devices,
        loginHistory = loginHistory,
        onBack = onBack,
        onOpenProfile = onOpenProfile,
        onOpenVerification = { showVerificationSheet = true },
        onSelectCountry = { iso, dialCode ->
            scope.launch { viewModel.prefsRepository.setCountryAndDialCode(iso, dialCode) }
        },
        onSavePersonalInfo = { payload ->
            val validation = viewModel.userRepository.validateProfileInput(
                displayName = payload.displayName,
                username = payload.username,
                bio = payload.bio,
                phoneNumber = payload.phoneNumber,
                email = payload.email
            )
            if (!validation.isValid) {
                val msg = validation.fieldErrors.values.firstOrNull() ?: "Vérifiez les champs"
                viewModel.showMessage(msg)
                Result.failure(IllegalArgumentException(msg))
            } else {
                val res = viewModel.userRepository.updateProfile(
                    uid = viewModel.currentUserId,
                    displayName = payload.displayName,
                    username = payload.username,
                    bio = payload.bio,
                    locationCity = payload.locationCity,
                    locationDetails = payload.locationDetails,
                    phoneNumber = payload.phoneNumber,
                    email = payload.email,
                    birthDate = payload.birthDate,
                    homeAddress = payload.homeAddress,
                    privacySettings = currentUser?.privacySettings
                )
                if (res.isSuccess) viewModel.showMessage("Informations personnelles enregistrées")
                res
            }
        },
        onPersonalInfoFeedback = { viewModel.showMessage(it) },
        onRevokeDevice = { devId ->
            scope.launch {
                viewModel.userRepository.revokeDeviceSession(viewModel.currentUserId, devId)
                viewModel.showMessage("Appareil révoqué")
            }
        },
        onSelectCoverPreset = { preset ->
            scope.launch {
                viewModel.prefsRepository.setProfileCoverPresetId(preset.id)
                viewModel.showMessage("Style de couverture appliqué : ${preset.labelFr}")
            }
        },
        onSignOut = onSignOut,
        onShowPinDialog = { showPinDialog = true },
        onToggleBiometric = { enabled ->
            scope.launch {
                viewModel.prefsRepository.setBiometricSessionLock(enabled)
                viewModel.showMessage(
                    if (enabled) "Verrouillage biométrique activé"
                    else "Verrouillage biométrique désactivé"
                )
            }
        },
        onToggleHideNotificationContent = { hide ->
            scope.launch { viewModel.prefsRepository.setNotificationSettings(hideContent = hide) }
        },
        onShowBlockedUsers = { showBlockedDialog = true },
        onUpdatePrivacyKey = { key, next ->
            val currentPriv = currentUser?.privacySettings ?: emptyMap()
            scope.launch {
                viewModel.userRepository.updatePrivacySettings(viewModel.currentUserId, currentPriv + (key to next))
            }
        },
        onBackup = {
            scope.launch {
                val r = viewModel.userRepository.exportEncryptedBackup(viewModel.currentUserId)
                viewModel.showMessage(if (r.isSuccess) "Sauvegarde enregistrée" else "Échec de sauvegarde")
            }
        },
        onRestore = {
            scope.launch {
                val r = viewModel.userRepository.restoreEncryptedBackup(viewModel.currentUserId)
                viewModel.showMessage(if (r.isSuccess) "Sauvegarde restaurée" else r.exceptionOrNull()?.localizedMessage ?: "Échec")
            }
        },
        onDeleteAccountRequest = { showDeleteConfirm = true },
        onSetCallAudio = { noise, echo, voicePriority, speaker, startMuted ->
            scope.launch {
                viewModel.prefsRepository.setCallAudioSettings(
                    noiseSuppression = noise,
                    echoCancellation = echo,
                    voicePriority = voicePriority,
                    autoGainControl = voicePriority,
                    defaultSpeakerAudio = speaker,
                    startMuted = startMuted
                )
            }
        },
        onShowMicTest = { showMicTestDialog = true },
        onToggleAiAutoReplySms = { scope.launch { viewModel.prefsRepository.setAiAutoReplySms(it) } },
        onToggleAiAutoReplyBrav = { scope.launch { viewModel.prefsRepository.setAiAutoReplyBrav(it) } },
        onSetNotificationSettings = { master, msg, calls, missed, groupsStatuses, sound, vibration ->
            viewModel.updateNotificationSettings(
                master = master,
                messages = msg,
                calls = calls,
                missedCalls = missed,
                groups = groupsStatuses,
                statuses = groupsStatuses,
                sound = sound,
                vibration = vibration
            )
        },
        onSetExtendedNotificationSettings = { contacts, actusPosts, actusInteractions, offersJobs, quietEnabled, quietStart, quietEnd ->
            viewModel.updateNotificationSettings(
                contacts = contacts,
                actusPosts = actusPosts,
                actusLikesComments = actusInteractions,
                offersJobs = offersJobs,
                quietHoursEnabled = quietEnabled,
                quietHoursStartHour = quietStart,
                quietHoursEndHour = quietEnd
            )
        },
        onSetThemeMode = { viewModel.setThemeMode(it) },
        onEnableLauncherPref = { scope.launch { viewModel.prefsRepository.setLauncherEnabled(true) } },
        onOpenLauncherStudio = onOpenLauncherStudio,
        onSetCompactLayout = { scope.launch { viewModel.prefsRepository.setCompactLayout(it) } },
        onSetDataSaver = { scope.launch { viewModel.prefsRepository.setDataSaver(it) } },
        onSetWifiOnly = { scope.launch { viewModel.prefsRepository.setAutoDownloadWifiOnly(it) } },
        onClearCache = {
            viewModel.prefsRepository.clearLocalMediaCache()
            cacheBytes = viewModel.prefsRepository.computeLocalCacheSizeBytes()
            viewModel.showMessage("Cache nettoyé")
        },
        onSetCallerIdEnabled = { viewModel.setCallerIdEnabled(it) },
        onSetCallerIdSpamWarnings = { viewModel.setCallerIdSpamWarningsEnabled(it) },
        onClearCallerIdCache = { viewModel.clearCallerIdPrivacyCache() },
        onOpenAiAssistant = onOpenAiAssistant,
        onOpenOffers = onOpenOffers,
        onOpenAdmin = onOpenAdmin,
        onShowPrivacyDoc = { legalDialog = "Politique de confidentialité" to PRIVACY_TEXT },
        onShowTermsDoc = { legalDialog = "Conditions d'utilisation" to TERMS_TEXT },
        isPrivateProfile = currentUser?.isPrivateProfile == true,
        allowPhoneLookup = currentUser?.allowPhoneLookup ?: true,
        pendingFollowRequestsCount = incomingFollowRequests.size,
        onTogglePrivateProfile = { viewModel.setPrivateProfileEnabled(it) },
        onToggleAllowPhoneLookup = { viewModel.setAllowPhoneLookup(it) },
        onOpenFollowSheet = { showFollowSheet = true },
        onOpenInviteSheet = { showInviteSheet = true }
    )

    if (showPinDialog) {
        ChatLockPinDialog(
            hasPinConfigured = appPrefs.chatLockPinHash.isNotBlank(),
            onSavePin = { pin ->
                viewModel.prefsRepository.setChatLockPin(pin)
                viewModel.showMessage(if (pin.isBlank()) "Code PIN désactivé" else "Code PIN enregistré")
            },
            onDismiss = { showPinDialog = false }
        )
    }

    if (showBlockedDialog) {
        BlockedUsersDialog(
            blockedUsers = blockedUsers,
            onUnblock = { targetUid ->
                scope.launch {
                    viewModel.userRepository.unblockUser(viewModel.currentUserId, targetUid)
                    viewModel.showMessage("Utilisateur débloqué")
                }
            },
            onDismiss = { showBlockedDialog = false }
        )
    }

    if (showMicTestDialog) {
        LiveMicrophoneTestDialog(
            processingConfig = VoiceProcessingConfig(
                noiseSuppressionEnabled = appPrefs.noiseSuppressionEnabled,
                echoCancellationEnabled = appPrefs.echoCancellationEnabled,
                autoGainControlEnabled = appPrefs.autoGainControlEnabled,
                voicePriorityEnabled = appPrefs.voicePriorityEnabled
            ),
            onDismiss = { showMicTestDialog = false }
        )
    }

    legalDialog?.let { (title, content) ->
        LegalDocumentDialog(
            title = title,
            content = content,
            onDismiss = { legalDialog = null }
        )
    }

    if (showDeleteConfirm) {
        val lux = BravTheme.colors
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = lux.modalSurface,
            title = { Text("Supprimer définitivement le compte ?") },
            text = { Text("Cette action efface votre profil, vos appareils enregistrés et vos préférences sur BRAV CHAT.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = BravRed, contentColor = Color.White),
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            viewModel.userRepository.deleteAccountData(viewModel.currentUserId)
                            onSignOut()
                        }
                    }
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") }
            }
        )
    }

    if (showVerificationSheet) {
        VerificationRequestSheet(
            viewModel = viewModel,
            currentUser = currentUser,
            myRequests = myVerificationRequests,
            onDismiss = { showVerificationSheet = false }
        )
    }

    if (showFollowSheet) {
        FollowManagementSheet(
            followers = followerUsers,
            following = followingUsers,
            incomingRequests = incomingFollowRequests,
            outgoingRequests = outgoingFollowRequests,
            initialTab = if (incomingFollowRequests.isNotEmpty()) {
                FollowManagementTab.INCOMING
            } else {
                FollowManagementTab.FOLLOWERS
            },
            onMessageUser = { target ->
                scope.launch {
                    viewModel.openOrCreateDirectChat(target).onSuccess {
                        showFollowSheet = false
                        onBack()
                    }
                }
            },
            onRemoveFollower = { follower -> viewModel.removeFollower(follower.userId, follower.displayName) },
            onUnfollowUser = { target -> viewModel.toggleFollowUser(target) },
            onBlockUser = { target -> viewModel.blockUser(target.userId) },
            onAcceptRequest = { req -> viewModel.acceptFollowRequest(req) },
            onDeclineRequest = { req -> viewModel.declineFollowRequest(req) },
            onCancelOutgoingRequest = { req -> viewModel.cancelOutgoingFollowRequest(req.toUid) },
            onDismiss = { showFollowSheet = false }
        )
    }

    if (showInviteSheet) {
        InviteShareSheet(
            myInvites = myInvites,
            onEnsureInviteLink = { viewModel.createOrGetPersonalInvite("settings") },
            onFeedback = { msg -> viewModel.showMessage(msg) },
            onDismiss = { showInviteSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHierarchicalContent(
    currentUser: User?,
    privateInfo: UserPrivateInfo? = null,
    firebaseEmail: String,
    appPrefs: AppPreferencesState,
    blockedCount: Int,
    isAdmin: Boolean,
    cacheBytes: Long,
    verificationStatus: VerificationStatus = VerificationStatus.NONE,
    isDesignerAccount: Boolean = false,
    devices: List<UserDevice> = emptyList(),
    loginHistory: List<LoginSessionEntry> = emptyList(),
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenVerification: () -> Unit = {},
    onSelectCountry: (iso: String, dialCode: String) -> Unit = { _, _ -> },
    onSavePersonalInfo: suspend (PersonalInfoSavePayload) -> Result<Unit> = { Result.success(Unit) },
    onPersonalInfoFeedback: (String) -> Unit = {},
    onRevokeDevice: (String) -> Unit = {},
    onSelectCoverPreset: (ProfileCoverArtPreset) -> Unit = {},
    onSignOut: () -> Unit,
    onShowPinDialog: () -> Unit = {},
    onToggleBiometric: (Boolean) -> Unit = {},
    onToggleHideNotificationContent: (Boolean) -> Unit = {},
    onShowBlockedUsers: () -> Unit = {},
    onUpdatePrivacyKey: (String, String) -> Unit = { _, _ -> },
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
    onDeleteAccountRequest: () -> Unit = {},
    onSetCallAudio: (noise: Boolean?, echo: Boolean?, voicePriority: Boolean?, speaker: Boolean?, startMuted: Boolean?) -> Unit = { _, _, _, _, _ -> },
    onShowMicTest: () -> Unit = {},
    onToggleAiAutoReplySms: (Boolean) -> Unit = {},
    onToggleAiAutoReplyBrav: (Boolean) -> Unit = {},
    onSetNotificationSettings: (master: Boolean?, msg: Boolean?, calls: Boolean?, missed: Boolean?, groupsStatuses: Boolean?, sound: Boolean?, vibration: Boolean?) -> Unit = { _, _, _, _, _, _, _ -> },
    onSetExtendedNotificationSettings: (contacts: Boolean?, actusPosts: Boolean?, actusLikesComments: Boolean?, offersJobs: Boolean?, quietEnabled: Boolean?, quietStartHour: Int?, quietEndHour: Int?) -> Unit = { _, _, _, _, _, _, _ -> },
    onSetThemeMode: (ThemeMode) -> Unit = {},
    onEnableLauncherPref: () -> Unit = {},
    onOpenLauncherStudio: () -> Unit = {},
    onSetCompactLayout: (Boolean) -> Unit = {},
    onSetDataSaver: (Boolean) -> Unit = {},
    onSetWifiOnly: (Boolean) -> Unit = {},
    onClearCache: () -> Unit = {},
    onSetCallerIdEnabled: (Boolean) -> Unit = {},
    onSetCallerIdSpamWarnings: (Boolean) -> Unit = {},
    onClearCallerIdCache: () -> Unit = {},
    onOpenAiAssistant: () -> Unit = {},
    onOpenOffers: () -> Unit = {},
    onOpenAdmin: () -> Unit = {},
    onShowPrivacyDoc: () -> Unit = {},
    onShowTermsDoc: () -> Unit = {},
    isPrivateProfile: Boolean = false,
    allowPhoneLookup: Boolean = true,
    pendingFollowRequestsCount: Int = 0,
    onTogglePrivateProfile: (Boolean) -> Unit = {},
    onToggleAllowPhoneLookup: (Boolean) -> Unit = {},
    onOpenFollowSheet: () -> Unit = {},
    onOpenInviteSheet: () -> Unit = {}
) {
    var activeCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    BackHandler(enabled = true) {
        if (activeCategory != null) activeCategory = null else onBack()
    }

    val lux = BravTheme.colors
    Scaffold(
        containerColor = lux.ivoryCanvas,
        topBar = {
            BravLuxuryHeader(
                title = activeCategory?.title ?: "Paramètres",
                showLogo = activeCategory == null,
                pattern = BravPatternType.KENTE_DIAMOND,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeCategory != null) activeCategory = null else onBack()
                        },
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour",
                            tint = lux.headerOnColor
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(lux.ivoryCanvas)
        ) {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.KENTE_DIAMOND,
                variant = BravPatternVariant.GOLD_ON_IVORY,
                opacity = 0.095f,
                scale = 1.05f
            )
            AnimatedContent(
                targetState = activeCategory,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(110)) },
                modifier = Modifier.fillMaxSize(),
                label = "settings_hierarchy_transition"
            ) { selected ->
                if (selected == null) {
                    SettingsOverviewList(
                        onSelectCategory = { activeCategory = it }
                    )
                } else {
                    SettingsCategoryDetailBody(
                        category = selected,
                        currentUser = currentUser,
                        privateInfo = privateInfo,
                        firebaseEmail = firebaseEmail,
                        appPrefs = appPrefs,
                        blockedCount = blockedCount,
                        isAdmin = isAdmin,
                        cacheBytes = cacheBytes,
                        verificationStatus = verificationStatus,
                        isDesignerAccount = isDesignerAccount,
                        devices = devices,
                        loginHistory = loginHistory,
                        onOpenProfile = onOpenProfile,
                        onOpenVerification = onOpenVerification,
                        onSelectCountry = onSelectCountry,
                        onSavePersonalInfo = onSavePersonalInfo,
                        onPersonalInfoFeedback = onPersonalInfoFeedback,
                        onRevokeDevice = onRevokeDevice,
                        onSelectCoverPreset = onSelectCoverPreset,
                        onSignOut = onSignOut,
                        onShowPinDialog = onShowPinDialog,
                        onToggleBiometric = onToggleBiometric,
                        onToggleHideNotificationContent = onToggleHideNotificationContent,
                        onShowBlockedUsers = onShowBlockedUsers,
                        onUpdatePrivacyKey = onUpdatePrivacyKey,
                        onBackup = onBackup,
                        onRestore = onRestore,
                        onDeleteAccountRequest = onDeleteAccountRequest,
                        onSetCallAudio = onSetCallAudio,
                        onShowMicTest = onShowMicTest,
                        onToggleAiAutoReplySms = onToggleAiAutoReplySms,
                        onToggleAiAutoReplyBrav = onToggleAiAutoReplyBrav,
                        onSetNotificationSettings = onSetNotificationSettings,
                        onSetExtendedNotificationSettings = onSetExtendedNotificationSettings,
                        onSetThemeMode = onSetThemeMode,
                        onEnableLauncherPref = onEnableLauncherPref,
                        onOpenLauncherStudio = onOpenLauncherStudio,
                        onSetCompactLayout = onSetCompactLayout,
                        onSetDataSaver = onSetDataSaver,
                        onSetWifiOnly = onSetWifiOnly,
                        onClearCache = onClearCache,
                        onSetCallerIdEnabled = onSetCallerIdEnabled,
                        onSetCallerIdSpamWarnings = onSetCallerIdSpamWarnings,
                        onClearCallerIdCache = onClearCallerIdCache,
                        onOpenAiAssistant = onOpenAiAssistant,
                        onOpenOffers = onOpenOffers,
                        onOpenAdmin = onOpenAdmin,
                        onShowPrivacyDoc = onShowPrivacyDoc,
                        onShowTermsDoc = onShowTermsDoc,
                        isPrivateProfile = isPrivateProfile,
                        allowPhoneLookup = allowPhoneLookup,
                        pendingFollowRequestsCount = pendingFollowRequestsCount,
                        onTogglePrivateProfile = onTogglePrivateProfile,
                        onToggleAllowPhoneLookup = onToggleAllowPhoneLookup,
                        onOpenFollowSheet = onOpenFollowSheet,
                        onOpenInviteSheet = onOpenInviteSheet
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsOverviewList(
    onSelectCategory: (SettingsCategory) -> Unit
) {
    val lux = BravTheme.colors
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_overview_list"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(SettingsCategory.entries, key = { it.name }) { category ->
            BravLuxuryCard(
                onClick = { onSelectCategory(category) },
                shapeRadius = 18.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(category.testTag)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(lux.titlePurple.copy(alpha = 0.10f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = lux.titlePurple,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = lux.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = category.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = lux.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Ouvrir ${category.title}",
                        tint = lux.inkSecondary
                    )
                }
            }
        }
    }
}

private const val TERMS_TEXT =
    "BRAV CHAT est une plateforme de messagerie chiffrée et d'appels audio/vidéo WebRTC.\n\n" +
        "1. Respect des membres : tout harcèlement, usurpation d'identité ou diffusion illégale de contenu est interdit et peut entraîner une suspension.\n" +
        "2. Appels et messagerie : vous êtes responsable des échanges effectués depuis votre compte Google authentifié.\n" +
        "3. Annonces gratuites : les offres publiées sur BRAV CHAT sont gratuites et soumises à modération en cas d'abus."

private const val PRIVACY_TEXT =
    "Protection des données BRAV CHAT :\n\n" +
        "• Chiffrement de bout en bout (E2EE) des messages et localisations en direct.\n" +
        "• Les coordonnées privées (e-mail, téléphone) sont conservées dans votre espace propriétaire isolé et ne sont jamais exposées sans votre accord.\n" +
        "• Les appels audio et vidéo WebRTC transitent en temps réel avec traitement local du bruit ambiant directement sur votre appareil.\n" +
        "• Vous pouvez modifier votre photo, exporter une sauvegarde chiffrée ou supprimer votre compte à tout moment."
