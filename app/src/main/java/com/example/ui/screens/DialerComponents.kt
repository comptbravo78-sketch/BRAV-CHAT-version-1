package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.Contact
import com.example.model.User
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.ui.theme.CallActionGreen
import com.example.ui.theme.StatusOnlineGreen
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import com.example.telecom.CallDirection
import com.example.telecom.CallerIdResolver
import com.example.telecom.CallerSafetyCategory
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.components.VerifiedBlueBadge
import com.example.util.DefaultDialerHelper
import com.example.util.PhoneUtils
import com.example.util.WhatsAppLauncher
import kotlinx.coroutines.delay

@Composable
fun DefaultDialerActionCard(
    onStatusChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    val supported = remember(refreshTick) { DefaultDialerHelper.isDefaultDialerSupported(context) }
    val isDefault = remember(refreshTick) { DefaultDialerHelper.isBravDefaultDialer(context) }
    val screeningSupported = remember(refreshTick) { DefaultDialerHelper.isCallScreeningSupported(context) }
    val screeningHeld = remember(refreshTick) { DefaultDialerHelper.isBravCallScreeningHeld(context) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshTick++
        onStatusChanged(DefaultDialerHelper.isBravDefaultDialer(context))
    }

    val lux = BravTheme.colors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isDefault) StatusOnlineGreen.copy(alpha = 0.1f) else lux.translucentCardSurface,
        border = BorderStroke(0.75.dp, if (isDefault) StatusOnlineGreen.copy(alpha = 0.4f) else lux.violetContour),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("default_dialer_card")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isDefault) Icons.Rounded.CheckCircle else Icons.Rounded.PhoneAndroid,
                    contentDescription = null,
                    tint = if (isDefault) StatusOnlineGreen else BravPurple,
                    modifier = Modifier.size(22.dp)
                )
                    Text(
                        text = if (isDefault) "Numérotation par défaut active" else "Numérotation par défaut",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                if (!isDefault) {
                    Button(
                        onClick = {
                            val intent = DefaultDialerHelper.createRequestDefaultDialerIntent(context)
                                ?: DefaultDialerHelper.createDefaultAppsSettingsIntent()
                            runCatching { roleLauncher.launch(intent) }.onFailure {
                                runCatching { roleLauncher.launch(DefaultDialerHelper.createDefaultAppsSettingsIntent()) }
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.testTag("set_default_dialer_button")
                    ) {
                        Text(if (supported) "Définir" else "Paramètres", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (screeningSupported) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (screeningHeld) Icons.Rounded.CheckCircle else Icons.Rounded.Call,
                        contentDescription = null,
                        tint = if (screeningHeld) StatusOnlineGreen else BravPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (screeningHeld) "Identification ville & appel active" else "Identification ville des numéros",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (!screeningHeld) {
                        OutlinedButton(
                            onClick = {
                                val intent = DefaultDialerHelper.createRequestCallScreeningIntent(context)
                                    ?: DefaultDialerHelper.createDefaultAppsSettingsIntent()
                                runCatching { roleLauncher.launch(intent) }
                            },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.testTag("set_call_screening_button")
                        ) {
                            Text("Activer")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DialerModalSheet(
    initialNumber: String = "",
    defaultCountryIso: String = "CI",
    savedContacts: List<User>,
    phoneContacts: List<Contact>,
    searchedUsers: List<User>,
    onLookupNumber: (String) -> Unit,
    onStartBravAudioCall: (peerUid: String, peerName: String) -> Unit,
    onStartBravVideoCall: (peerUid: String, peerName: String) -> Unit,
    onOpenSmsComposer: (String) -> Unit = {},
    onFeedback: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var dialNumber by remember(initialNumber) {
        mutableStateOf(DefaultDialerHelper.sanitizeDialInput(initialNumber))
    }
    var pendingCarrierNumber by remember { mutableStateOf<String?>(null) }
    var pendingWhatsAppNumber by remember { mutableStateOf<String?>(null) }
    var dialerRefreshTick by remember { mutableIntStateOf(0) }

    val isDefaultDialer = remember(dialerRefreshTick) {
        DefaultDialerHelper.isBravDefaultDialer(context)
    }
    val canBeDefaultDialer = remember(dialerRefreshTick) {
        DefaultDialerHelper.isDefaultDialerSupported(context)
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        dialerRefreshTick++
        val nowDefault = DefaultDialerHelper.isBravDefaultDialer(context)
        if (nowDefault) {
            onFeedback("BRAV Chat est maintenant votre numérotation par défaut")
        }
    }

    val callPhoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val num = pendingCarrierNumber
        pendingCarrierNumber = null
        if (granted && !num.isNullOrBlank()) {
            val placed = DefaultDialerHelper.placeCarrierCall(context, num)
            if (placed) onDismiss() else onFeedback("Impossible de lancer l'appel mobile")
        } else if (!granted) {
            onFeedback("Autorisation Téléphone requise pour les appels mobiles")
        }
    }

    val bravCallPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(dialNumber) {
        val digitCount = dialNumber.count { it.isDigit() }
        if (digitCount >= 6) {
            delay(280L)
            onLookupNumber(dialNumber)
        }
    }

    val matchedPhoneContacts = remember(dialNumber, phoneContacts) {
        val qDigits = dialNumber.filter { it.isDigit() }
        if (qDigits.length < 2) emptyList()
        else phoneContacts.filter { c ->
            c.normalizedPhoneNumber.contains(qDigits) ||
                c.rawPhoneNumber.filter { it.isDigit() }.contains(qDigits)
        }.take(4)
    }

    val matchedBravUsers = remember(dialNumber, savedContacts, searchedUsers, matchedPhoneContacts) {
        val pool = (savedContacts + searchedUsers + matchedPhoneContacts.mapNotNull { it.bravUser })
            .distinctBy { it.userId }
        val qDigits = dialNumber.filter { it.isDigit() }
        if (qDigits.length < 2) emptyList()
        else {
            pool.filter { user ->
                val maskedDigits = user.phoneMasked.filter { it.isDigit() }
                (maskedDigits.isNotEmpty() && (qDigits.endsWith(maskedDigits) || maskedDigits.contains(qDigits))) ||
                    searchedUsers.any { it.userId == user.userId } ||
                    matchedPhoneContacts.any { it.bravUser?.userId == user.userId }
            }.take(3)
        }
    }

    fun appendDigit(d: String) {
        if (dialNumber.length < 22) {
            dialNumber = DefaultDialerHelper.sanitizeDialInput(dialNumber + d)
        }
    }

    fun triggerCarrierCall(number: String) {
        val cleaned = DefaultDialerHelper.sanitizeDialInput(number)
        if (cleaned.isBlank()) return
        if (DefaultDialerHelper.hasCallPhonePermission(context)) {
            val ok = DefaultDialerHelper.placeCarrierCall(context, cleaned)
            if (ok) onDismiss() else onFeedback("Impossible de passer cet appel mobile")
        } else {
            pendingCarrierNumber = cleaned
            callPhoneLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    fun triggerBravCall(user: User, isVideo: Boolean) {
        val name = user.displayName.ifBlank { "@${user.username}" }
        val needMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        val needCam = isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        if (needMic || needCam) {
            val perms = buildList {
                if (needMic) add(Manifest.permission.RECORD_AUDIO)
                if (needCam) add(Manifest.permission.CAMERA)
            }.toTypedArray()
            bravCallPermLauncher.launch(perms)
            return
        }
        onDismiss()
        if (isVideo) onStartBravVideoCall(user.userId, name) else onStartBravAudioCall(user.userId, name)
    }

    val lux = BravTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = lux.modalSurface,
        dragHandle = null
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.WAX_TEXTILE,
                variant = BravPatternVariant.GOLD_ON_IVORY,
                opacity = 0.085f,
                scale = 1.0f,
                modifier = Modifier.matchParentSize()
            )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .align(Alignment.TopCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Dialpad, contentDescription = null, tint = BravPurple)
                    Text("Clavier téléphonique", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isDefaultDialer) {
                        TextButton(
                            onClick = {
                                val intent = DefaultDialerHelper.createRequestDefaultDialerIntent(context)
                                    ?: DefaultDialerHelper.createDefaultAppsSettingsIntent()
                                runCatching { roleLauncher.launch(intent) }.onFailure {
                                    runCatching { roleLauncher.launch(DefaultDialerHelper.createDefaultAppsSettingsIntent()) }
                                }
                            },
                            modifier = Modifier.testTag("dialer_set_default_button")
                        ) {
                            Text("Définir par défaut", color = BravPurple, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("dialer_close_button")) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer le clavier")
                    }
                }
            }

            // Number display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (dialNumber.isBlank()) "Saisir un numéro" else PhoneUtils.formatDisplay(dialNumber, defaultCountryIso),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (dialNumber.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dialer_number_display")
                )
                if (dialNumber.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { dialNumber = dialNumber.dropLast(1) },
                                onLongClick = { dialNumber = "" }
                            )
                            .testTag("dialer_backspace_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Backspace,
                            contentDescription = "Effacer un chiffre",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val callerIdentity = remember(dialNumber, defaultCountryIso, matchedBravUsers, matchedPhoneContacts) {
                CallerIdResolver.resolveFromKnownData(
                    rawNumber = dialNumber,
                    direction = CallDirection.OUTGOING,
                    savedUsers = matchedBravUsers,
                    phoneContacts = matchedPhoneContacts,
                    defaultCountryIso = defaultCountryIso
                )
            }
            val localityInfo = callerIdentity.locality
            val showUnknownNote = dialNumber.count { it.isDigit() } >= 6 &&
                callerIdentity.isUnknownCaller &&
                !callerIdentity.isKnownContact &&
                !callerIdentity.isVerifiedBrav &&
                matchedBravUsers.isEmpty() &&
                matchedPhoneContacts.isEmpty()
            if (localityInfo.compactBadge.isNotBlank() || callerIdentity.isVerifiedBrav || callerIdentity.safetyCategory.isWarning || showUnknownNote) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (callerIdentity.safetyCategory.isWarning) BravRed.copy(alpha = 0.11f) else lux.translucentSubtleSurface,
                    border = BorderStroke(0.75.dp, if (callerIdentity.safetyCategory.isWarning) BravRed.copy(alpha = 0.4f) else lux.champagneGold.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag("dialer_locality_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (callerIdentity.safetyCategory.isWarning) {
                            Icon(
                                Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = BravRed,
                                modifier = Modifier.size(15.dp)
                            )
                        } else if (callerIdentity.isVerifiedBrav) {
                            VerifiedBlueBadge(size = 14.dp)
                        } else {
                            Icon(
                                Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = BravPurple,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        val badgeLabel = buildString {
                            if (callerIdentity.safetyCategory.isWarning) {
                                append(callerIdentity.safetyCategory.labelFr)
                                if (localityInfo.compactBadge.isNotBlank()) append(" · ")
                            } else if (showUnknownNote) {
                                append("Numéro inconnu")
                                if (localityInfo.compactBadge.isNotBlank()) append(" · ")
                            }
                            if (localityInfo.compactBadge.isNotBlank()) {
                                append(localityInfo.compactBadge)
                            }
                        }.ifBlank { callerIdentity.confidence.labelFr }
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (callerIdentity.safetyCategory.isWarning) BravRed else BravPurple,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Live Contact / BRAV User Matches
            if (matchedBravUsers.isNotEmpty() || matchedPhoneContacts.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(matchedBravUsers, key = { "brav_${it.userId}" }) { user ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BravPurple.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatarWithStatus(
                                    name = user.displayName,
                                    avatarUrl = user.avatarUrl,
                                    availabilityStatus = user.availabilityStatus,
                                    size = 36.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            user.displayName.ifBlank { user.username },
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (user.isVerifiedActive()) {
                                            VerifiedBlueBadge(size = 14.dp)
                                        }
                                    }
                                    Text(
                                        text = "BRAV Chat • @${user.username}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BravPurple
                                    )
                                }
                                IconButton(
                                    onClick = { triggerBravCall(user, isVideo = false) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Rounded.Call, contentDescription = "Appel audio BRAV", tint = CallActionGreen)
                                }
                                IconButton(
                                    onClick = { triggerBravCall(user, isVideo = true) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Rounded.Videocam, contentDescription = "Appel vidéo BRAV", tint = BravRed)
                                }
                            }
                        }
                    }

                    items(
                        matchedPhoneContacts.filter { c -> matchedBravUsers.none { it.userId == c.bravUser?.userId } },
                        key = { "sim_${it.id}" }
                    ) { contact ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = lux.translucentSubtleSurface,
                            border = BorderStroke(0.75.dp, lux.violetContour),
                            onClick = { dialNumber = DefaultDialerHelper.sanitizeDialInput(contact.rawPhoneNumber) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.preferredDisplayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(contact.rawPhoneNumber, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.Call, contentDescription = "Sélectionner", tint = CallActionGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Keypad Grid
            val rows = remember {
                listOf(
                    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
                    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
                    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
                    listOf("*" to "", "0" to "+", "#" to "")
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { (digit, letters) ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = lux.translucentSubtleSurface,
                                border = BorderStroke(0.75.dp, lux.violetContour),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .combinedClickable(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            appendDigit(digit)
                                        },
                                        onLongClick = {
                                            if (digit == "0") {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                appendDigit("+")
                                            }
                                        }
                                    )
                                    .testTag("dialer_key_$digit")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(digit, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                    if (letters.isNotEmpty()) {
                                        Text(
                                            letters,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }

            Spacer(Modifier.height(10.dp))

            // Primary Call Actions (pinned at bottom so always reachable on compact screens)
            val primaryBravUser = matchedBravUsers.firstOrNull()
            if (primaryBravUser != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { triggerBravCall(primaryBravUser, isVideo = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = CallActionGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(52.dp).testTag("dialer_brav_audio_button")
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("BRAV Audio", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { triggerCarrierCall(dialNumber) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(52.dp).testTag("dialer_carrier_call_button")
                    ) {
                        Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("SIM")
                    }
                    OutlinedButton(
                        onClick = { onOpenSmsComposer(dialNumber) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(52.dp).testTag("dialer_sms_button")
                    ) {
                        Icon(Icons.Rounded.Sms, contentDescription = "Envoyer un SMS", modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { triggerCarrierCall(dialNumber) },
                        enabled = dialNumber.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CallActionGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("dialer_place_call_button")
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Appeler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (dialNumber.isNotBlank()) {
                        OutlinedButton(
                            onClick = { onOpenSmsComposer(dialNumber) },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.height(54.dp).testTag("dialer_sms_button")
                        ) {
                            Icon(Icons.Rounded.Sms, contentDescription = "Envoyer SMS", modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("SMS")
                        }
                        if (WhatsAppLauncher.hasValidPhoneDigits(dialNumber)) {
                            OutlinedButton(
                                onClick = { pendingWhatsAppNumber = dialNumber },
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.height(54.dp).testTag("dialer_whatsapp_button")
                            ) {
                                Icon(
                                    Icons.Rounded.Forum,
                                    contentDescription = "Ouvrir sur WhatsApp",
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
        }
    }

    pendingWhatsAppNumber?.let { targetNum ->
        AlertDialog(
            onDismissRequest = { pendingWhatsAppNumber = null },
            containerColor = lux.modalSurface,
            title = { Text("Contacter sur WhatsApp") },
            text = {
                Text("Quitter BRAV CHAT pour écrire au ${PhoneUtils.formatDisplay(targetNum, defaultCountryIso)} sur WhatsApp ?")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White),
                    onClick = {
                        pendingWhatsAppNumber = null
                        WhatsAppLauncher.openWhatsAppChat(context, targetNum, defaultCountryIso = defaultCountryIso)
                            .onFailure { err -> onFeedback(err.localizedMessage ?: "WhatsApp indisponible") }
                    },
                    modifier = Modifier.testTag("confirm_dialer_whatsapp_button")
                ) {
                    Text("Ouvrir WhatsApp")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingWhatsAppNumber = null }) { Text("Annuler") }
            }
        )
    }
}
