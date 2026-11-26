package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.example.model.Call
import com.example.model.User
import com.example.telecom.CallDirection
import com.example.telecom.DeviceCallLogEntry
import com.example.telecom.DeviceCallLogRepository
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravLuxuryHeader
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.components.formatShortDateTime
import com.example.ui.theme.BravInkPrimary
import com.example.ui.theme.BravInkSecondary
import com.example.ui.theme.BravIvory
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWarmBorder
import com.example.ui.theme.BravWhite
import com.example.ui.theme.CallActionGreen
import com.example.ui.theme.EndCallRed
import com.example.ui.theme.StatusOnlineGreen
import com.example.util.DefaultDialerHelper
import com.example.util.PhoneLocalityResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal val CallLightBg = BravIvory
internal val CallLightCard = Color(0xB8FAF8F3)
internal val CallLightBorder = BravWarmBorder
internal val CallSlateText = BravInkPrimary
internal val CallMutedText = BravInkSecondary

private enum class CallLogFilter(val label: String) {
    ALL("Tous"),
    PHONE("Téléphone"),
    BRAV("BRAV"),
    MISSED("Manqués"),
    VIDEO("Vidéo")
}

private sealed interface UnifiedCallItem {
    val sortTimeMs: Long
    val key: String

    data class BravItem(val call: Call, override val sortTimeMs: Long) : UnifiedCallItem {
        override val key: String = "brav_${call.id}"
    }

    data class PhoneItem(val entry: DeviceCallLogEntry, override val sortTimeMs: Long) : UnifiedCallItem {
        override val key: String = entry.id
    }
}

@Composable
fun CallHistoryScreen(
    currentUserId: String,
    calls: List<Call>,
    savedContacts: List<User> = emptyList(),
    defaultCountryIso: String = "CI",
    injectedDeviceCallLogs: List<DeviceCallLogEntry>? = null,
    onStartAudioCall: (peerUid: String, peerName: String) -> Unit,
    onStartVideoCall: (peerUid: String, peerName: String) -> Unit,
    onOpenDialer: () -> Unit = {},
    onDialPhoneNumber: (String) -> Unit = {},
    onOpenSms: () -> Unit = {},
    onDeleteCall: (callId: String) -> Unit = {},
    onClearHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    var pendingCallRequest by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var pendingCarrierDialNumber by remember { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(CallLogFilter.ALL) }
    var hasCallLogPerm by remember {
        mutableStateOf(DeviceCallLogRepository.hasCallLogPermission(context))
    }
    var refreshTick by remember { mutableIntStateOf(0) }
    var loadedDeviceLogs by remember { mutableStateOf<List<DeviceCallLogEntry>>(emptyList()) }

    val deviceLogs = injectedDeviceCallLogs ?: loadedDeviceLogs

    LaunchedEffect(hasCallLogPerm, defaultCountryIso, refreshTick, injectedDeviceCallLogs) {
        if (injectedDeviceCallLogs == null && hasCallLogPerm) {
            loadedDeviceLogs = withContext(Dispatchers.IO) {
                DeviceCallLogRepository.loadCallLogs(context, defaultCountryIso)
            }
        }
    }

    val callLogPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCallLogPerm = granted || DeviceCallLogRepository.hasCallLogPermission(context)
        if (hasCallLogPerm) refreshTick++
    }

    val carrierCallPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val num = pendingCarrierDialNumber ?: return@rememberLauncherForActivityResult
        pendingCarrierDialNumber = null
        if (granted) {
            DefaultDialerHelper.placeCarrierCall(context, num)
        } else {
            onDialPhoneNumber(num)
        }
    }

    fun placePhoneCallback(rawNumber: String) {
        if (rawNumber.isBlank()) return
        if (DefaultDialerHelper.hasCallPhonePermission(context)) {
            if (!DefaultDialerHelper.placeCarrierCall(context, rawNumber)) {
                onDialPhoneNumber(rawNumber)
            }
        } else {
            pendingCarrierDialNumber = rawNumber
            carrierCallPermLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    val unifiedItems = remember(calls, deviceLogs, filter) {
        val merged = buildList {
            if (filter != CallLogFilter.PHONE) {
                calls.forEach { c ->
                    val include = when (filter) {
                        CallLogFilter.MISSED -> c.missed
                        CallLogFilter.VIDEO -> c.callType == "video"
                        else -> true
                    }
                    if (include) {
                        add(UnifiedCallItem.BravItem(c, (c.createdAt?.seconds ?: 0L) * 1000L))
                    }
                }
            }
            if (filter != CallLogFilter.BRAV && filter != CallLogFilter.VIDEO) {
                deviceLogs.forEach { d ->
                    val include = when (filter) {
                        CallLogFilter.MISSED -> d.isMissed
                        else -> true
                    }
                    if (include) {
                        add(UnifiedCallItem.PhoneItem(d, d.timestampMs))
                    }
                }
            }
        }
        merged.sortedByDescending { it.sortTimeMs }
    }

    val callPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val req = pendingCallRequest ?: return@rememberLauncherForActivityResult
        pendingCallRequest = null
        val micOk = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micOk) {
            if (req.third) onStartVideoCall(req.first, req.second)
            else onStartAudioCall(req.first, req.second)
        }
    }

    fun launchCall(peerUid: String, peerName: String, isVideo: Boolean) {
        if (peerUid.isBlank()) return
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (needed.isEmpty()) {
            if (isVideo) onStartVideoCall(peerUid, peerName) else onStartAudioCall(peerUid, peerName)
        } else {
            pendingCallRequest = Triple(peerUid, peerName, isVideo)
            callPermLauncher.launch(needed.toTypedArray())
        }
    }

    val lux = BravTheme.colors
    Scaffold(
        containerColor = lux.ivoryCanvas,
        topBar = {
            BravLuxuryHeader(
                title = "Appels",
                showLogo = true,
                pattern = BravPatternType.KENTE_DIAMOND,
                actions = {
                    if (hasCallLogPerm && injectedDeviceCallLogs == null) {
                        IconButton(onClick = { refreshTick++ }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Actualiser l'historique", tint = lux.headerOnColor)
                        }
                    }
                    IconButton(onClick = onOpenSms, modifier = Modifier.testTag("calls_open_sms_button")) {
                        Icon(Icons.Rounded.Sms, contentDescription = "Messages SMS", tint = lux.headerOnColor)
                    }
                    if (calls.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("clear_call_history_button")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Tout effacer", tint = Color(0xFFFFB4B9), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Effacer", color = Color(0xFFFFD6D9), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenDialer,
                containerColor = BravPurplePrimary,
                contentColor = BravWhite,
                shape = CircleShape,
                modifier = Modifier.testTag("calls_open_dialer_fab")
            ) {
                Icon(Icons.Rounded.Dialpad, contentDescription = "Ouvrir le clavier")
            }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(CallLogFilter.entries, key = { it.name }) { entry ->
                        FilterChip(
                            selected = filter == entry,
                            onClick = { filter = entry },
                            label = { Text(entry.label) },
                            modifier = Modifier.testTag("calls_filter_${entry.name.lowercase()}")
                        )
                    }
                }

                if (!hasCallLogPerm && injectedDeviceCallLogs == null) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = lux.cardSurface,
                        border = BorderStroke(0.75.dp, lux.hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.PhoneInTalk, contentDescription = null, tint = lux.titlePurple, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "Historique d'appels du téléphone",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = lux.inkPrimary
                                )
                            }
                            TextButton(
                                onClick = { callLogPermLauncher.launch(Manifest.permission.READ_CALL_LOG) },
                                modifier = Modifier.testTag("grant_call_log_permission_button")
                            ) {
                                Text("Autoriser", fontWeight = FontWeight.Bold, color = lux.titlePurple)
                            }
                        }
                    }
                }

                if (!DefaultDialerHelper.isBravDefaultDialer(context)) {
                    Spacer(Modifier.height(6.dp))
                    DefaultDialerActionCard()
                }
                Spacer(Modifier.height(10.dp))

                if (savedContacts.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(savedContacts, key = { it.userId }) { contact ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = lux.cardSurface,
                                border = BorderStroke(0.75.dp, lux.hairline)
                            ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatarWithStatus(
                                    name = contact.displayName,
                                    avatarUrl = contact.avatarUrl,
                                    availabilityStatus = contact.availabilityStatus,
                                    size = 34.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = contact.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = { launchCall(contact.userId, contact.displayName, false) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Rounded.Call, contentDescription = "Appel audio", tint = BravPurple, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { launchCall(contact.userId, contact.displayName, true) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Rounded.Videocam, contentDescription = "Appel vidéo", tint = BravRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (unifiedItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Call, contentDescription = null, tint = BravPurple.copy(alpha = 0.45f), modifier = Modifier.size(42.dp))
                        Text("Aucun appel", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(unifiedItems, key = { it.key }) { item ->
                        when (item) {
                            is UnifiedCallItem.BravItem -> {
                                val call = item.call
                                val isOutgoing = call.callerId == currentUserId
                                val peerUid = if (isOutgoing) call.receiverId else call.callerId
                                val contactMatch = savedContacts.firstOrNull { it.userId == peerUid }
                                val peerName = contactMatch?.displayName?.ifBlank { null }
                                    ?: (if (isOutgoing) call.receiverName else call.callerName).ifBlank { "Membre BRAV" }
                                val peerAvatar = contactMatch?.avatarUrl?.ifBlank { null }
                                    ?: (if (isOutgoing) call.receiverAvatar else call.callerAvatar)
                                val peerLoc = contactMatch?.phoneMasked?.let { PhoneLocalityResolver.resolve(it, defaultCountryIso).compactBadge }.orEmpty()

                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = lux.cardSurface,
                                    border = BorderStroke(0.75.dp, lux.hairline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        UserAvatarWithStatus(
                                            name = peerName,
                                            avatarUrl = peerAvatar,
                                            availabilityStatus = contactMatch?.availabilityStatus ?: "offline",
                                            size = 44.dp
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = peerName,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = lux.translucentSubtleSurface,
                                                    border = BorderStroke(0.6.dp, lux.champagneGold.copy(alpha = 0.6f))
                                                ) {
                                                    Text(
                                                        text = call.effectiveCallSource().badgeLabelFr,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = lux.titlePurple,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            val ts = call.createdAt?.let { formatShortDateTime(it.seconds) } ?: ""
                                            val lifecycle = call.effectiveLifecycleState()
                                            val stateSummary = when {
                                                call.missed -> "Manqué"
                                                call.durationSeconds > 0L -> DeviceCallLogRepository.formatDuration(call.durationSeconds)
                                                lifecycle.isTerminal -> lifecycle.labelFr
                                                else -> lifecycle.labelFr
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(
                                                    imageVector = when {
                                                        call.missed -> Icons.AutoMirrored.Rounded.CallMissed
                                                        isOutgoing -> Icons.AutoMirrored.Rounded.CallMade
                                                        else -> Icons.AutoMirrored.Rounded.CallReceived
                                                    },
                                                    contentDescription = null,
                                                    tint = if (call.missed) BravRed else StatusOnlineGreen,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = listOfNotNull(
                                                        if (call.callType == "video") "Vidéo" else "Audio",
                                                        stateSummary,
                                                        peerLoc.takeIf { it.isNotBlank() },
                                                        ts.takeIf { it.isNotBlank() }
                                                    ).joinToString(" • "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        IconButton(onClick = { launchCall(peerUid, peerName, false) }) {
                                            Icon(Icons.Rounded.Call, contentDescription = "Rappeler en audio", tint = BravPurple)
                                        }
                                        IconButton(onClick = { launchCall(peerUid, peerName, true) }) {
                                            Icon(Icons.Rounded.Videocam, contentDescription = "Rappeler en vidéo", tint = BravRed)
                                        }
                                        IconButton(
                                            onClick = { onDeleteCall(call.id) },
                                            modifier = Modifier.testTag("delete_call_${call.id}")
                                        ) {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = "Supprimer cet appel de l'historique",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            is UnifiedCallItem.PhoneItem -> {
                                val entry = item.entry
                                val isOutgoing = entry.direction == CallDirection.OUTGOING
                                val ts = if (entry.timestampMs > 0L) formatShortDateTime(entry.timestampMs / 1000L) else ""
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = lux.cardSurface,
                                    border = BorderStroke(0.75.dp, lux.hairline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = entry.number.isNotBlank()) {
                                            onDialPhoneNumber(entry.number)
                                        }
                                        .testTag("device_call_row_${entry.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        UserAvatarWithStatus(
                                            name = entry.displayName,
                                            avatarUrl = entry.cachedPhotoUri,
                                            availabilityStatus = "offline",
                                            size = 44.dp
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = entry.displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = lux.translucentSubtleSurface,
                                                    border = BorderStroke(0.6.dp, lux.violetContour)
                                                ) {
                                                    Text(
                                                        text = "Tél",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(
                                                    imageVector = when {
                                                        entry.isMissed -> Icons.AutoMirrored.Rounded.CallMissed
                                                        isOutgoing -> Icons.AutoMirrored.Rounded.CallMade
                                                        else -> Icons.AutoMirrored.Rounded.CallReceived
                                                    },
                                                    contentDescription = null,
                                                    tint = if (entry.isMissed) BravRed else StatusOnlineGreen,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = listOfNotNull(
                                                        if (entry.isMissed) "Manqué" else DeviceCallLogRepository.formatDuration(entry.durationSeconds),
                                                        entry.localityBadge.takeIf { it.isNotBlank() },
                                                        ts.takeIf { it.isNotBlank() }
                                                    ).joinToString(" • "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (entry.number.isNotBlank()) {
                                            IconButton(
                                                onClick = { placePhoneCallback(entry.number) },
                                                modifier = Modifier.testTag("callback_phone_${entry.id}")
                                            ) {
                                                Icon(Icons.Rounded.Call, contentDescription = "Rappeler", tint = StatusOnlineGreen)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = lux.modalSurface,
            icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = BravRed) },
            title = { Text("Effacer l'historique ?") },
            text = { Text("Tous les appels affichés seront retirés de votre journal sur cet appareil et votre compte.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = BravRed, contentColor = Color.White),
                    onClick = {
                        showClearDialog = false
                        onClearHistory()
                    }
                ) { Text("Tout effacer") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
fun IncomingCallBannerDialog(
    call: Call,
    peerUser: User? = null,
    soundEnabled: Boolean = true,
    vibrationEnabled: Boolean = true,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val context = LocalContext.current
    val verifiedPeer = peerUser?.takeIf { it.userId == call.callerId }
    val callerName = verifiedPeer?.displayName?.ifBlank { null } ?: call.callerName.ifBlank { "Membre BRAV" }
    val callerAvatar = verifiedPeer?.avatarUrl?.ifBlank { null } ?: call.callerAvatar
    val callerUsername = verifiedPeer?.username?.ifBlank { null } ?: call.callerUsername
    val callerSubtitle = if (callerUsername.isNotBlank()) "@$callerUsername"
    else if (call.callerId.isNotBlank()) "ID : ${call.callerId.take(8)}..." else "Appel BRAV CHAT"

    LaunchedEffect(call.id, soundEnabled, vibrationEnabled) {
        val ringtone = if (soundEnabled) {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(context, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                    play()
                }
            }.getOrNull()
        } else null

        val fallbackTone = if (soundEnabled && ringtone == null) {
            runCatching { ToneGenerator(AudioManager.STREAM_RING, 85) }.getOrNull()
        } else null

        val vibrator = if (vibrationEnabled) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            }.getOrNull()
        } else null

        if (vibrator != null) {
            runCatching {
                val pattern = longArrayOf(0L, 650L, 850L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, 0)
                }
            }
        }

        try {
            while (isActive) {
                if (soundEnabled) {
                    if (ringtone != null && !ringtone.isPlaying) runCatching { ringtone.play() }
                    else if (ringtone == null) runCatching { fallbackTone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900) }
                }
                delay(1500L)
            }
        } finally {
            runCatching { ringtone?.stop() }
            runCatching { fallbackTone?.stopTone() }
            runCatching { fallbackTone?.release() }
            runCatching { vibrator?.cancel() }
        }
    }

    var micPermDeniedNotice by remember { mutableStateOf(false) }
    val acceptPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            micPermDeniedNotice = false
            onAccept()
        } else {
            micPermDeniedNotice = true
        }
    }

    val lux = BravTheme.colors
    Surface(modifier = Modifier.fillMaxSize().zIndex(100f), color = lux.ivoryCanvas) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(lux.ivoryCanvas)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.SOLAR_ADINKRA,
                variant = BravPatternVariant.GOLD_ON_IVORY,
                opacity = 0.11f,
                scale = 1.0f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = lux.translucentCardSurface,
                border = BorderStroke(0.9.dp, lux.champagneGold.copy(alpha = 0.6f)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (call.callType == "video") Icons.Rounded.Videocam else Icons.Rounded.Call,
                        contentDescription = null,
                        tint = BravPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (call.callType == "video") "Appel vidéo entrant" else "Appel audio entrant",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = CallSlateText
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.Center).padding(bottom = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CallPeerPhotoAvatar(
                    name = callerName,
                    avatarUrl = callerAvatar,
                    size = 148.dp,
                    ringAnimated = true,
                    isLightSurface = true
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = callerName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = CallSlateText,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = callerSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CallMutedText,
                    fontWeight = FontWeight.Medium
                )
                val callerLocationLabel = remember(verifiedPeer) {
                    val city = verifiedPeer?.locationCity?.trim().orEmpty()
                    val phoneLoc = verifiedPeer?.phoneMasked
                        ?.takeIf { it.isNotBlank() }
                        ?.let { PhoneLocalityResolver.resolve(it).compactBadge }
                        .orEmpty()
                    listOfNotNull(
                        city.takeIf { it.isNotBlank() },
                        phoneLoc.takeIf { it.isNotBlank() && it != city }
                    ).joinToString(" • ")
                }
                if (callerLocationLabel.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = BravPurple.copy(alpha = 0.08f)
                    ) {
                        Text(
                            text = callerLocationLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = BravPurple,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = StatusOnlineGreen, modifier = Modifier.size(14.dp))
                    Text(
                        text = "BRAV Chat • Chiffrement de bout en bout",
                        style = MaterialTheme.typography.labelMedium,
                        color = CallMutedText
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = EndCallRed,
                        shadowElevation = 4.dp,
                        onClick = onReject,
                        modifier = Modifier.size(72.dp).testTag("reject_call_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CallEnd, contentDescription = "Refuser l'appel", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Refuser", color = CallSlateText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (micPermDeniedNotice) {
                        Text(
                            text = "Autorisez le micro pour répondre",
                            color = EndCallRed,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = CallActionGreen,
                        shadowElevation = 4.dp,
                        onClick = {
                            val needed = mutableListOf<String>()
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                needed.add(Manifest.permission.RECORD_AUDIO)
                            }
                            if (call.callType == "video" && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                needed.add(Manifest.permission.CAMERA)
                            }
                            if (needed.isEmpty()) onAccept() else acceptPermLauncher.launch(needed.toTypedArray())
                        },
                        modifier = Modifier.size(72.dp).testTag("accept_call_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (call.callType == "video") Icons.Rounded.Videocam else Icons.Rounded.Call,
                                contentDescription = "Répondre à l'appel",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Répondre", color = CallSlateText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        }
    }
}

@Composable
fun CallTerminalOverlay(
    call: Call,
    reason: String,
    currentUserId: String,
    peerUser: User? = null,
    onDismiss: () -> Unit
) {
    val isCaller = call.callerId == currentUserId
    val peerUid = if (isCaller) call.receiverId else call.callerId
    val verifiedPeer = peerUser?.takeIf { it.userId == peerUid }
    val peerName = verifiedPeer?.displayName?.ifBlank { null }
        ?: (if (isCaller) call.receiverName else call.callerName).ifBlank { "Membre BRAV" }
    val peerAvatar = verifiedPeer?.avatarUrl?.ifBlank { null }
        ?: (if (isCaller) call.receiverAvatar else call.callerAvatar)

    val lux = BravTheme.colors
    Surface(
        modifier = Modifier.fillMaxSize().zIndex(102f).clickable(onClick = onDismiss),
        color = lux.ivoryCanvas
    ) {
        Box(modifier = Modifier.fillMaxSize().background(lux.ivoryCanvas)) {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.SOLAR_ADINKRA,
                variant = BravPatternVariant.GOLD_ON_IVORY,
                opacity = 0.105f,
                scale = 1.0f
            )
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CallPeerPhotoAvatar(
                        name = peerName,
                        avatarUrl = peerAvatar,
                        size = 112.dp,
                        ringAnimated = false,
                        isLightSurface = true
                    )
                    Text(peerName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = CallSlateText)
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = EndCallRed.copy(alpha = 0.1f),
                        border = BorderStroke(0.75.dp, EndCallRed.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = EndCallRed,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = lux.translucentSubtleSurface,
                        border = BorderStroke(0.75.dp, lux.champagneGold.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "${call.effectiveCallSource().badgeLabelFr} • ${call.effectiveLifecycleState().labelFr}",
                            style = MaterialTheme.typography.labelMedium,
                            color = CallMutedText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
