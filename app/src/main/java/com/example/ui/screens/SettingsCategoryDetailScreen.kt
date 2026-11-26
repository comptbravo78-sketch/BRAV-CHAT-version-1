package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.launcher.LauncherRepository
import com.example.local.AppPreferencesState
import com.example.local.ThemeMode
import com.example.model.CertificationEligibility
import com.example.model.User
import com.example.model.UserDevice
import com.example.model.UserPrivateInfo
import com.example.model.VerificationStatus
import com.example.network.LoginSessionEntry
import com.example.ui.components.BravLuxuryCard
import com.example.ui.components.BravPrimaryButton
import com.example.ui.components.ProfileCoverArtPreset
import com.example.ui.components.VerificationStatusPill
import com.example.ui.components.VerifiedBlueBadge
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravVerifiedBlue
import com.example.ui.theme.StatusOnlineGreen
import com.example.util.BiometricAuthManager
import com.example.util.DefaultDialerHelper
import com.example.util.DefaultSmsHelper

enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val testTag: String
) {
    ACCOUNT(
        title = "Mon compte",
        subtitle = "Informations personnelles, visibilité, sécurité, appareils",
        icon = Icons.Rounded.Person,
        testTag = "settings_cat_account"
    ),
    PERSONALIZATION(
        title = "Personnalisation",
        subtitle = "Thème, couverture africaine, icônes et notifications",
        icon = Icons.Rounded.Palette,
        testTag = "settings_cat_personalization"
    ),
    CERTIFICATION(
        title = "CERTIFICATION",
        subtitle = "Badge bleu, éligibilité, statut et demande",
        icon = Icons.Rounded.VerifiedUser,
        testTag = "settings_cat_certification"
    ),
    COMMUNICATION(
        title = "Communication",
        subtitle = "Appels, SMS, audio DSP, IA, confidentialité et réseau",
        icon = Icons.Rounded.Call,
        testTag = "settings_cat_communication"
    ),
    OTHER(
        title = "Autres",
        subtitle = "Assistant IA, Offres, administration, aide et version",
        icon = Icons.Rounded.Info,
        testTag = "settings_cat_other"
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsCategoryDetailBody(
    category: SettingsCategory,
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
    onOpenProfile: () -> Unit,
    onOpenVerification: () -> Unit = {},
    onSelectCountry: (iso: String, dialCode: String) -> Unit = { _, _ -> },
    onSavePersonalInfo: suspend (PersonalInfoSavePayload) -> Result<Unit> = { Result.success(Unit) },
    onPersonalInfoFeedback: (String) -> Unit = {},
    onRevokeDevice: (String) -> Unit = {},
    onSelectCoverPreset: (ProfileCoverArtPreset) -> Unit = {},
    onSignOut: () -> Unit,
    onShowPinDialog: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleHideNotificationContent: (Boolean) -> Unit,
    onShowBlockedUsers: () -> Unit,
    onUpdatePrivacyKey: (String, String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onDeleteAccountRequest: () -> Unit,
    onSetCallAudio: (noise: Boolean?, echo: Boolean?, voicePriority: Boolean?, speaker: Boolean?, startMuted: Boolean?) -> Unit,
    onShowMicTest: () -> Unit,
    onToggleAiAutoReplySms: (Boolean) -> Unit,
    onToggleAiAutoReplyBrav: (Boolean) -> Unit,
    onSetNotificationSettings: (master: Boolean?, msg: Boolean?, calls: Boolean?, missed: Boolean?, groupsStatuses: Boolean?, sound: Boolean?, vibration: Boolean?) -> Unit,
    onSetExtendedNotificationSettings: (contacts: Boolean?, actusPosts: Boolean?, actusLikesComments: Boolean?, offersJobs: Boolean?, quietEnabled: Boolean?, quietStartHour: Int?, quietEndHour: Int?) -> Unit = { _, _, _, _, _, _, _ -> },
    onSetThemeMode: (ThemeMode) -> Unit,
    onEnableLauncherPref: () -> Unit,
    onOpenLauncherStudio: () -> Unit,
    onSetCompactLayout: (Boolean) -> Unit,
    onSetDataSaver: (Boolean) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onSetCallerIdEnabled: (Boolean) -> Unit = {},
    onSetCallerIdSpamWarnings: (Boolean) -> Unit = {},
    onClearCallerIdCache: () -> Unit = {},
    onOpenAiAssistant: () -> Unit,
    onOpenOffers: () -> Unit,
    onOpenAdmin: () -> Unit,
    onShowPrivacyDoc: () -> Unit,
    onShowTermsDoc: () -> Unit,
    isPrivateProfile: Boolean = false,
    allowPhoneLookup: Boolean = true,
    pendingFollowRequestsCount: Int = 0,
    onTogglePrivateProfile: (Boolean) -> Unit = {},
    onToggleAllowPhoneLookup: (Boolean) -> Unit = {},
    onOpenFollowSheet: () -> Unit = {},
    onOpenInviteSheet: () -> Unit = {}
) {
    val context = LocalContext.current
    val lux = BravTheme.colors
    var permissionRefreshTick by remember { mutableIntStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRefreshTick++
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionRefreshTick++
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_detail_list"),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when (category) {
            SettingsCategory.ACCOUNT -> {
                item {
                    SettingsSectionCard(title = "Profil BRAV") {
                        SettingsActionRow(
                            icon = Icons.Rounded.Person,
                            title = currentUser?.displayName?.ifBlank { null } ?: "Mon profil",
                            subtitle = firebaseEmail.ifBlank { "Voir mon profil, ma couverture et mes publications" },
                            onClick = onOpenProfile,
                            testTag = "settings_to_profile_row"
                        )
                    }
                }

                item {
                    SettingsPersonalInfoCard(
                        currentUser = currentUser,
                        privateInfo = privateInfo,
                        firebaseEmail = firebaseEmail,
                        appPrefs = appPrefs,
                        onSelectCountry = onSelectCountry,
                        onSavePersonalInfo = onSavePersonalInfo,
                        onValidationFeedback = onPersonalInfoFeedback
                    )
                }

                item {
                    SettingsFieldPrivacyCard(
                        privacyMap = currentUser?.privacySettings ?: emptyMap(),
                        onUpdatePrivacyKey = onUpdatePrivacyKey
                    )
                }

                item {
                    SettingsSectionCard(title = "Abonnements, confidentialité & invitations") {
                        SettingsSwitchRow(
                            title = "Profil privé",
                            subtitle = "Validation requise pour s'abonner et consulter vos publications",
                            checked = isPrivateProfile,
                            testTag = "switch_private_profile",
                            onCheckedChange = onTogglePrivateProfile
                        )
                        SettingsSwitchRow(
                            title = "Identification par numéro",
                            subtitle = "Permet aux membres de vous identifier lors des appels",
                            checked = allowPhoneLookup,
                            testTag = "switch_allow_phone_lookup",
                            onCheckedChange = onToggleAllowPhoneLookup
                        )
                        SettingsActionRow(
                            icon = Icons.Rounded.Group,
                            title = "Abonnés, abonnements & demandes",
                            subtitle = if (pendingFollowRequestsCount > 0) {
                                "$pendingFollowRequestsCount demande(s) d'abonnement en attente"
                            } else {
                                "Gérer vos abonnés, abonnements et demandes"
                            },
                            onClick = onOpenFollowSheet,
                            testTag = "settings_open_follow_sheet"
                        )
                        SettingsActionRow(
                            icon = Icons.Rounded.Link,
                            title = "Partager mon lien d'invitation BRAV",
                            subtitle = "Lien personnel avec suivi des ouvertures et inscriptions",
                            onClick = onOpenInviteSheet,
                            testTag = "settings_open_invite_sheet"
                        )
                    }
                }

                item {
                    val bioSupported = remember { BiometricAuthManager.isBiometricOrDeviceCredentialAvailable(context) }
                    SettingsSectionCard(title = "Coordonnées & Sécurité") {
                        OutlinedButton(
                            onClick = onShowPinDialog,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("settings_pin_button")
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (appPrefs.chatLockPinHash.isBlank()) "Code PIN" else "Modifier le code PIN")
                        }
                        SettingsSwitchRow(
                            title = "Verrouillage biométrique",
                            checked = appPrefs.biometricSessionLockEnabled,
                            enabled = bioSupported,
                            testTag = "switch_biometric_lock",
                            onCheckedChange = onToggleBiometric
                        )
                        SettingsSwitchRow(
                            title = "Masquer le contenu des notifications",
                            checked = appPrefs.hideNotificationContent,
                            enabled = appPrefs.notificationsEnabled,
                            onCheckedChange = onToggleHideNotificationContent
                        )
                        SettingsActionRow(
                            icon = Icons.Rounded.Block,
                            title = "Utilisateurs bloqués",
                            subtitle = if (blockedCount == 0) "Aucun utilisateur bloqué" else "$blockedCount bloqué(s)",
                            onClick = onShowBlockedUsers,
                            testTag = "settings_blocked_users_button"
                        )
                        SettingsSwitchRow(
                            title = "Identification BRAV Call ID",
                            subtitle = "Identifie les contacts, comptes certifiés et localités",
                            checked = appPrefs.callerIdEnabled,
                            testTag = "switch_caller_id_enabled",
                            onCheckedChange = onSetCallerIdEnabled
                        )
                        SettingsSwitchRow(
                            title = "Alertes démarchage et spam",
                            subtitle = "Affiche les avertissements communautaires sans blocage forcé",
                            checked = appPrefs.callerIdSpamWarningsEnabled,
                            enabled = appPrefs.callerIdEnabled,
                            testTag = "switch_caller_id_spam",
                            onCheckedChange = onSetCallerIdSpamWarnings
                        )
                        OutlinedButton(
                            onClick = onClearCallerIdCache,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("clear_caller_id_cache_button")
                        ) {
                            Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Effacer le cache Call ID local")
                        }
                    }
                }

                item {
                    SettingsConnectedDevicesCard(
                        devices = devices,
                        loginHistory = loginHistory,
                        onRevokeDevice = onRevokeDevice
                    )
                }

                item {
                    SettingsSectionCard(title = "Centre des autorisations & Rôles Android") {
                        val permissionsList = buildList {
                            add(Triple(Manifest.permission.RECORD_AUDIO, "Microphone (Appels & vocaux)", true))
                            add(Triple(Manifest.permission.CAMERA, "Caméra (Appels vidéo & selfie)", true))
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Triple(Manifest.permission.POST_NOTIFICATIONS, "Notifications & appels entrants", true))
                            }
                            add(Triple(Manifest.permission.READ_CONTACTS, "Contacts & répertoire", true))
                            add(Triple(Manifest.permission.READ_CALL_LOG, "Historique d'appels", true))
                            add(Triple(Manifest.permission.CALL_PHONE, "Passer des appels téléphoniques", true))
                            add(Triple(Manifest.permission.READ_PHONE_STATE, "État du réseau téléphonique", true))
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                add(Triple(Manifest.permission.BLUETOOTH_CONNECT, "Audio Bluetooth (casque / voiture)", true))
                            }
                            add(Triple(Manifest.permission.ACCESS_FINE_LOCATION, "Localisation en direct", true))
                        }
                        remember(permissionRefreshTick) { permissionRefreshTick }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("settings_permissions_center")
                        ) {
                            permissionsList.forEach { (perm, label, _) ->
                                val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = lux.translucentSubtleSurface,
                                    border = BorderStroke(0.65.dp, if (granted) StatusOnlineGreen.copy(alpha = 0.4f) else lux.hairline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = lux.inkPrimary,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        if (granted) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = StatusOnlineGreen.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = "Accordée",
                                                    color = StatusOnlineGreen,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else {
                                            TextButton(
                                                onClick = { permLauncher.launch(perm) },
                                                modifier = Modifier.testTag("perm_grant_${perm.substringAfterLast('.')}")
                                            ) {
                                                Text("Autoriser", color = lux.titlePurple, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = lux.hairline)

                            val isDefaultDialer = DefaultDialerHelper.isBravDefaultDialer(context)
                            val isScreeningHeld = DefaultDialerHelper.isBravCallScreeningHeld(context)
                            val isDefaultSms = DefaultSmsHelper.isBravDefaultSms(context)

                            listOf(
                                Triple("Téléphone par défaut", isDefaultDialer) {
                                    val intent = DefaultDialerHelper.createRequestDefaultDialerIntent(context)
                                        ?: DefaultDialerHelper.createDefaultAppsSettingsIntent()
                                    runCatching { roleLauncher.launch(intent) }
                                    Unit
                                },
                                Triple("Filtrage & ID d'appel", isScreeningHeld) {
                                    val intent = DefaultDialerHelper.createRequestCallScreeningIntent(context)
                                        ?: DefaultDialerHelper.createDefaultAppsSettingsIntent()
                                    runCatching { roleLauncher.launch(intent) }
                                    Unit
                                },
                                Triple("Messagerie SMS par défaut", isDefaultSms) {
                                    val intent = DefaultSmsHelper.createRequestDefaultSmsIntent(context)
                                        ?: DefaultDialerHelper.createDefaultAppsSettingsIntent()
                                    runCatching { roleLauncher.launch(intent) }
                                    Unit
                                }
                            ).forEach { (roleLabel, isActive, onTrigger) ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = lux.translucentSubtleSurface,
                                    border = BorderStroke(0.65.dp, if (isActive) StatusOnlineGreen.copy(alpha = 0.45f) else lux.violetContour),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = roleLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = lux.inkPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isActive) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = StatusOnlineGreen.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = "Actif",
                                                    color = StatusOnlineGreen,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else {
                                            TextButton(onClick = onTrigger) {
                                                Text("Activer", color = lux.titlePurple, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                    runCatching { roleLauncher.launch(intent) }
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(0.85.dp, lux.violetContour),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_android_app_settings_button")
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Paramètres système de l'application")
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Sauvegarde et session") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = onBackup, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sauvegarder")
                            }
                            OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                                Text("Restaurer")
                            }
                        }
                        Button(
                            onClick = onSignOut,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Se déconnecter")
                        }
                        TextButton(
                            onClick = onDeleteAccountRequest,
                            modifier = Modifier.fillMaxWidth().testTag("delete_account_button")
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = BravRed, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Supprimer mon compte", color = BravRed)
                        }
                    }
                }
            }

            SettingsCategory.PERSONALIZATION -> {
                item {
                    SettingsSectionCard(title = "Thème et apparence") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = appPrefs.themeMode == mode,
                                    onClick = { onSetThemeMode(mode) },
                                    label = { Text(mode.label) }
                                )
                            }
                        }
                        SettingsSwitchRow(
                            title = "Affichage compact",
                            checked = appPrefs.compactLayoutEnabled,
                            onCheckedChange = onSetCompactLayout
                        )
                        Text(
                            text = "Style de couverture africaine (si aucune photo personnalisée)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = lux.inkPrimary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProfileCoverArtPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = appPrefs.profileCoverPresetId == preset.id,
                                    onClick = { onSelectCoverPreset(preset) },
                                    label = { Text(preset.labelFr) }
                                )
                            }
                        }
                    }
                }

                item {
                    var launcherRefreshTick by remember { mutableIntStateOf(0) }
                    val isBravHome = remember(launcherRefreshTick) { LauncherRepository.isBravDefaultLauncher(context) }
                    val defaultHomeLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) { launcherRefreshTick++ }

                    SettingsSectionCard(title = "Lanceur Android et icônes") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = lux.translucentSubtleSurface,
                            border = BorderStroke(0.75.dp, lux.violetContour),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Home,
                                    contentDescription = null,
                                    tint = if (isBravHome) StatusOnlineGreen else BravPurple
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Accueil par défaut", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (isBravHome) "Actif" else "Inactif",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isBravHome) StatusOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!isBravHome) {
                                    TextButton(
                                        onClick = {
                                            LauncherRepository.setLauncherComponentEnabled(context, true)
                                            onEnableLauncherPref()
                                            val intent = LauncherRepository.createRequestDefaultLauncherIntent(context)
                                            runCatching { defaultHomeLauncher.launch(intent) }
                                        },
                                        modifier = Modifier.testTag("settings_set_default_home_button")
                                    ) { Text("Activer", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }

                        SettingsActionRow(
                            icon = Icons.Rounded.Home,
                            title = "Accueil & Icônes",
                            subtitle = "Personnaliser la grille, les formes et thèmes",
                            onClick = onOpenLauncherStudio,
                            testTag = "settings_open_launcher_studio_row"
                        )
                    }
                }

                item {
                    SettingsSectionCard(title = "Notifications et alertes") {
                        SettingsSwitchRow(
                            title = "Notifications générales",
                            checked = appPrefs.notificationsEnabled,
                            testTag = "switch_notify_master"
                        ) { onSetNotificationSettings(it, null, null, null, null, null, null) }
                        SettingsSwitchRow(
                            title = "Messages privés & groupes",
                            checked = appPrefs.notifyMessages,
                            enabled = appPrefs.notificationsEnabled,
                            testTag = "switch_notify_messages"
                        ) { onSetNotificationSettings(null, it, null, null, null, null, null) }
                        SettingsSwitchRow(
                            title = "Contacts & invitations",
                            checked = appPrefs.notifyContacts,
                            enabled = appPrefs.notificationsEnabled,
                            testTag = "switch_notify_contacts"
                        ) { onSetExtendedNotificationSettings(it, null, null, null, null, null, null) }
                        SettingsSwitchRow(
                            title = "Publications ACTUS",
                            checked = appPrefs.notifyActusPosts,
                            enabled = appPrefs.notificationsEnabled,
                            testTag = "switch_notify_actus_posts"
                        ) { onSetExtendedNotificationSettings(null, it, null, null, null, null, null) }
                        SettingsSwitchRow(
                            title = "J'aimes & commentaires ACTUS",
                            checked = appPrefs.notifyActusLikesComments,
                            enabled = appPrefs.notificationsEnabled,
                            testTag = "switch_notify_actus_interactions"
                        ) { onSetExtendedNotificationSettings(null, null, it, null, null, null, null) }
                        SettingsSwitchRow(
                            title = "Offres et annonces d'emploi",
                            checked = appPrefs.notifyOffersJobs,
                            enabled = appPrefs.notificationsEnabled,
                            testTag = "switch_notify_offers_jobs"
                        ) { onSetExtendedNotificationSettings(null, null, null, it, null, null, null) }
                        SettingsSwitchRow(
                            title = "Appels entrants",
                            checked = appPrefs.notifyCalls,
                            enabled = appPrefs.notificationsEnabled
                        ) { onSetNotificationSettings(null, null, it, null, null, null, null) }
                        SettingsSwitchRow(
                            title = "Appels manqués",
                            checked = appPrefs.notifyMissedCalls,
                            enabled = appPrefs.notificationsEnabled
                        ) { onSetNotificationSettings(null, null, null, it, null, null, null) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        SettingsSwitchRow(
                            title = "Son",
                            checked = appPrefs.notifySoundEnabled,
                            enabled = appPrefs.notificationsEnabled
                        ) { onSetNotificationSettings(null, null, null, null, null, it, null) }
                        SettingsSwitchRow(
                            title = "Vibrations",
                            checked = appPrefs.notifyVibrationEnabled,
                            enabled = appPrefs.notificationsEnabled
                        ) { onSetNotificationSettings(null, null, null, null, null, null, it) }
                    }
                }

                item {
                    SettingsSectionCard(title = "Plages de silence") {
                        SettingsSwitchRow(
                            title = "Activer le mode silencieux programmé",
                            subtitle = "Met en sourdine les alertes non urgentes la nuit",
                            checked = appPrefs.quietHoursEnabled,
                            testTag = "switch_quiet_hours"
                        ) { onSetExtendedNotificationSettings(null, null, null, null, it, null, null) }
                        if (appPrefs.quietHoursEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = lux.translucentSubtleSurface,
                                    border = BorderStroke(0.75.dp, lux.violetContour),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Début : ${"%02d".format(appPrefs.quietHoursStartHour)}h",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = lux.inkPrimary
                                        )
                                        TextButton(
                                            onClick = {
                                                val next = (appPrefs.quietHoursStartHour + 1) % 24
                                                onSetExtendedNotificationSettings(null, null, null, null, null, next, null)
                                            },
                                            modifier = Modifier.testTag("quiet_hours_start_button")
                                        ) { Text("+1h", fontWeight = FontWeight.Bold) }
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = lux.translucentSubtleSurface,
                                    border = BorderStroke(0.75.dp, lux.violetContour),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Fin : ${"%02d".format(appPrefs.quietHoursEndHour)}h",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = lux.inkPrimary
                                        )
                                        TextButton(
                                            onClick = {
                                                val next = (appPrefs.quietHoursEndHour + 1) % 24
                                                onSetExtendedNotificationSettings(null, null, null, null, null, null, next)
                                            },
                                            modifier = Modifier.testTag("quiet_hours_end_button")
                                        ) { Text("+1h", fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingsCategory.CERTIFICATION -> {
                item {
                    val followers = currentUser?.followersCount ?: 0
                    val progress = if (isDesignerAccount) 1f
                    else (followers.toFloat() / CertificationEligibility.REQUIRED_FOLLOWERS.toFloat()).coerceIn(0f, 1f)

                    BravLuxuryCard(
                        shapeRadius = 22.dp,
                        accentBorder = isDesignerAccount || verificationStatus == VerificationStatus.VERIFIED,
                        modifier = Modifier.fillMaxWidth().testTag("settings_certification_card")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                VerifiedBlueBadge(size = 28.dp)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "CERTIFICATION BRAV CHAT",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = lux.inkPrimary
                                    )
                                    VerificationStatusPill(status = if (isDesignerAccount) VerificationStatus.VERIFIED else verificationStatus)
                                }
                            }

                            Text(
                                text = when {
                                    isDesignerAccount ->
                                        "Statut : Certifié · Compte Concepteur BRAV CHAT autorisé automatiquement sans condition."
                                    verificationStatus == VerificationStatus.VERIFIED ->
                                        "Votre badge bleu officiel est actif sur votre profil, vos messages et vos publications Actus."
                                    else ->
                                        "Obtenez le badge bleu officiel BRAV CHAT ($followers / ${CertificationEligibility.REQUIRED_FOLLOWERS} abonnés · Frais uniques : 3 600 F CFA)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = lux.inkSecondary
                            )

                            LinearProgressIndicator(
                                progress = { progress },
                                color = BravVerifiedBlue,
                                trackColor = lux.hairline,
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )

                            BravPrimaryButton(
                                text = when {
                                    isDesignerAccount || verificationStatus == VerificationStatus.VERIFIED ->
                                        "Voir les détails de certification"
                                    verificationStatus == VerificationStatus.AWAITING_PAYMENT ->
                                        "Finaliser le paiement (3 600 F CFA)"
                                    verificationStatus == VerificationStatus.RETAKE_REQUIRED ||
                                        verificationStatus == VerificationStatus.REJECTED ||
                                        verificationStatus == VerificationStatus.EXPIRED ->
                                        "Reprendre la certification"
                                    else -> "Gérer ou demander la certification"
                                },
                                onClick = onOpenVerification,
                                testTagName = "settings_open_verification_button",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            SettingsCategory.COMMUNICATION -> {
                item {
                    SettingsSectionCard(title = "Applications par défaut") {
                        DefaultDialerActionCard()
                        DefaultSmsActionCard()
                    }
                }

                item {
                    SettingsSectionCard(title = "Audio et traitement vocal") {
                        SettingsSwitchRow(
                            title = "Réduction du bruit",
                            checked = appPrefs.noiseSuppressionEnabled,
                            testTag = "switch_noise_suppression"
                        ) { onSetCallAudio(it, null, null, null, null) }
                        SettingsSwitchRow(
                            title = "Suppression de l'écho",
                            checked = appPrefs.echoCancellationEnabled,
                            testTag = "switch_echo_cancellation"
                        ) { onSetCallAudio(null, it, null, null, null) }
                        SettingsSwitchRow(
                            title = "Priorité vocale",
                            checked = appPrefs.voicePriorityEnabled,
                            testTag = "switch_voice_priority"
                        ) { onSetCallAudio(null, null, it, null, null) }
                        SettingsSwitchRow(
                            title = "Haut-parleur par défaut",
                            checked = appPrefs.defaultSpeakerAudio
                        ) { onSetCallAudio(null, null, null, it, null) }
                        SettingsSwitchRow(
                            title = "Démarrer micro coupé",
                            checked = appPrefs.startCallMuted
                        ) { onSetCallAudio(null, null, null, null, it) }

                        OutlinedButton(
                            onClick = onShowMicTest,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("test_mic_dsp_button")
                        ) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = BravPurple, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Tester le microphone")
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Réponses automatiques IA") {
                        SettingsSwitchRow(
                            title = "Réponse automatique SMS",
                            checked = appPrefs.aiAutoReplySmsEnabled,
                            onCheckedChange = onToggleAiAutoReplySms
                        )
                        SettingsSwitchRow(
                            title = "Réponse automatique BRAV Chat",
                            checked = appPrefs.aiAutoReplyBravEnabled,
                            onCheckedChange = onToggleAiAutoReplyBrav
                        )
                    }
                }

                item {
                    val priv = currentUser?.privacySettings ?: emptyMap()
                    SettingsSectionCard(title = "Autorisations d'interaction") {
                        listOf(
                            "whoCanCall" to "Appels autorisés",
                            "whoCanAddToGroup" to "Ajout aux groupes",
                            "whoCanRequest" to "Demandes de contact"
                        ).forEach { (key, label) ->
                            val currentVal = priv[key] ?: "everyone"
                            val next = when (currentVal) {
                                "everyone" -> "contacts"
                                "contacts" -> "nobody"
                                else -> "everyone"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { onUpdatePrivacyKey(key, next) }) {
                                    Text(
                                        text = when (currentVal) {
                                            "contacts" -> "Contacts"
                                            "nobody" -> "Personne"
                                            else -> "Tout le monde"
                                        },
                                        color = BravPurple,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Réseau et stockage local") {
                        SettingsSwitchRow(
                            title = "Économiseur de données",
                            subtitle = "Réduit la consommation sur réseau mobile",
                            checked = appPrefs.dataSaverEnabled,
                            onCheckedChange = onSetDataSaver
                        )
                        SettingsSwitchRow(
                            title = "Médias en Wi-Fi uniquement",
                            checked = appPrefs.autoDownloadWifiOnly,
                            onCheckedChange = onSetWifiOnly
                        )
                        OutlinedButton(
                            onClick = onClearCache,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("clear_cache_button")
                        ) {
                            Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Vider le cache (${(cacheBytes / 1024L)} Ko)")
                        }
                    }
                }
            }

            SettingsCategory.OTHER -> {
                item {
                    SettingsSectionCard(title = "Outils et services BRAV") {
                        SettingsActionRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "Assistant IA",
                            subtitle = "Chat, Mathématiques et Documents",
                            onClick = onOpenAiAssistant,
                            testTag = "settings_open_ai_row"
                        )
                        SettingsActionRow(
                            icon = Icons.Rounded.Storefront,
                            title = "Offres et annonces",
                            subtitle = "Consulter ou publier des annonces",
                            onClick = onOpenOffers,
                            testTag = "settings_open_offers_row"
                        )
                        if (isAdmin) {
                            SettingsActionRow(
                                icon = Icons.Rounded.AdminPanelSettings,
                                title = "Administration",
                                onClick = onOpenAdmin,
                                accentTint = BravRed
                            )
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "Assistance et informations légales") {
                        SettingsActionRow(
                            icon = Icons.Rounded.VerifiedUser,
                            title = "Politique de confidentialité",
                            onClick = onShowPrivacyDoc
                        )
                        SettingsActionRow(
                            icon = Icons.Rounded.Description,
                            title = "Conditions d'utilisation",
                            onClick = onShowTermsDoc
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Version de l'application", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val lux = BravTheme.colors
    BravLuxuryCard(
        shapeRadius = 20.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = lux.titlePurple
            )
            content()
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    testTag: String = "",
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier
        )
    }
}

@Composable
internal fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    accentTint: Color = BravPurple,
    testTag: String = ""
) {
    val lux = BravTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = lux.translucentSubtleSurface,
        border = BorderStroke(0.75.dp, lux.violetContour),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(accentTint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentTint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = lux.inkPrimary)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = lux.inkSecondary)
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = lux.inkSecondary
            )
        }
    }
}

@Composable
private fun DefaultSmsActionCard() {
    val context = LocalContext.current
    val lux = BravTheme.colors
    var isDefault by remember { mutableStateOf(DefaultSmsHelper.isBravDefaultSms(context)) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = DefaultSmsHelper.isBravDefaultSms(context)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = lux.translucentSubtleSurface,
        border = BorderStroke(0.75.dp, lux.violetContour),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.Sms,
                contentDescription = null,
                tint = if (isDefault) StatusOnlineGreen else lux.titlePurple
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Messagerie SMS / MMS par défaut", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = lux.inkPrimary)
                Text(
                    text = if (isDefault) "BRAV Chat gère vos SMS/MMS natifs" else "Gérer vos SMS et MMS directement dans BRAV Chat",
                    style = MaterialTheme.typography.labelSmall,
                    color = lux.inkSecondary
                )
            }
            if (!isDefault && DefaultSmsHelper.isDefaultSmsSupported(context)) {
                TextButton(onClick = {
                    val intent = DefaultSmsHelper.createRequestDefaultSmsIntent(context)
                    if (intent != null) runCatching { smsLauncher.launch(intent) }
                }) { Text("Activer", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
