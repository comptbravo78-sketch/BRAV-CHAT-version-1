package com.example.ui.screens

import android.telecom.Call
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.model.CallAudioRoute
import com.example.model.CallerReportCategory
import com.example.telecom.CallDirection
import com.example.telecom.CarrierCallController
import com.example.telecom.CarrierCallUiState
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.components.VerifiedBlueBadge
import com.example.ui.components.bravOutlinedTextFieldColors
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.ui.theme.CallActionGreen
import com.example.ui.theme.EndCallRed
import com.example.util.PhoneLocalityResolver
import com.example.util.PhoneUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarrierCallOverlay(
    state: CarrierCallUiState,
    onSubmitCallerReport: ((number: String, category: CallerReportCategory, label: String, details: String) -> Unit)? = null
) {
    if (!state.hasCall) return

    var elapsedSeconds by remember(state.connectTimeMillis, state.state) { mutableLongStateOf(0L) }
    var showDtmfPad by remember { mutableStateOf(false) }
    var typedDtmf by remember { mutableStateOf("") }
    var showReportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.state, state.connectTimeMillis) {
        while (state.state == Call.STATE_ACTIVE || state.state == Call.STATE_HOLDING) {
            elapsedSeconds = if (state.connectTimeMillis > 0L) {
                ((System.currentTimeMillis() - state.connectTimeMillis) / 1000L).coerceAtLeast(0L)
            } else {
                elapsedSeconds + 1L
            }
            delay(1000L)
        }
    }

    val formattedNum = state.formattedNumber.ifBlank {
        PhoneUtils.formatDisplay(state.number)
    }
    val title = state.callerDisplayName.ifBlank { formattedNum }.ifBlank { "Appel téléphonique" }
    val subtitle = if (state.callerDisplayName.isNotBlank() && formattedNum.isNotBlank() && state.callerDisplayName != formattedNum) {
        formattedNum
    } else ""

    val resolvedFallback = remember(state.number, state.cityLabel, state.localitySummary) {
        if (state.cityLabel.isBlank() && state.localitySummary.isBlank() && state.number.isNotBlank()) {
            PhoneLocalityResolver.resolve(state.number)
        } else null
    }
    val cityBadge = state.cityLabel.ifBlank { resolvedFallback?.fixedCityLabel.orEmpty() }
    val fullLocation = state.localitySummary.ifBlank { resolvedFallback?.compactBadge.orEmpty() }

    val lux = BravTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(105f),
        color = lux.ivoryCanvas
    ) {
        Box(modifier = Modifier.fillMaxSize().background(lux.ivoryCanvas)) {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.SOLAR_ADINKRA,
                variant = BravPatternVariant.GOLD_ON_IVORY,
                opacity = 0.105f,
                scale = 1.05f
            )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            // Top call direction & identification badge
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = lux.translucentCardSurface,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .border(1.dp, lux.champagneGold.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                    .testTag("carrier_call_direction_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (state.direction) {
                            CallDirection.OUTGOING -> Icons.AutoMirrored.Rounded.CallMade
                            CallDirection.INCOMING -> Icons.AutoMirrored.Rounded.CallReceived
                            CallDirection.UNKNOWN -> Icons.Rounded.VerifiedUser
                        },
                        contentDescription = null,
                        tint = if (state.isOutgoing) BravPurple else CallActionGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when {
                            state.isOutgoing -> "Appel sortant identifié"
                            state.isRinging || state.direction == CallDirection.INCOMING -> "Appel entrant identifié"
                            else -> "Appel mobile BRAV"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = CallSlateText
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 84.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CallPeerPhotoAvatar(
                    name = title,
                    avatarUrl = state.avatarUrl,
                    size = 128.dp,
                    ringAnimated = state.isRinging || state.state == Call.STATE_DIALING,
                    isLightSurface = true
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.widthIn(max = 340.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = CallSlateText,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).testTag("carrier_call_title")
                    )
                    if (state.isVerifiedBrav) {
                        VerifiedBlueBadge(size = 20.dp)
                    }
                }
                if (state.isVerifiedBrav && state.verifiedOrgName.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.verifiedOrgName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0B69C7),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CallMutedText,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (state.isUnknownCaller && !state.isKnownContact && !state.isVerifiedBrav) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = lux.translucentSubtleSurface,
                        border = BorderStroke(0.75.dp, lux.violetContour),
                        modifier = Modifier.testTag("carrier_unknown_caller_badge")
                    ) {
                        Text(
                            text = "Numéro inconnu",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = CallMutedText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                if (state.safetyCategory.isWarning) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BravRed.copy(alpha = 0.10f),
                        modifier = Modifier
                            .widthIn(max = 350.dp)
                            .border(1.dp, BravRed.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            .testTag("carrier_call_warning_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = BravRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                val warnHeading = buildString {
                                    append(state.safetyCategory.labelFr)
                                    if (state.reportCount > 0) append(" (${state.reportCount})")
                                }
                                Text(
                                    text = warnHeading,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BravRed
                                )
                                if (state.communityLabel.isNotBlank()) {
                                    Text(
                                        text = state.communityLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CallSlateText
                                    )
                                }
                            }
                        }
                    }
                }

                if (cityBadge.isNotBlank() || fullLocation.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = BravPurple.copy(alpha = 0.08f),
                        modifier = Modifier
                            .widthIn(max = 360.dp)
                            .border(1.dp, BravPurple.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                            .testTag("carrier_call_location_pill")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = BravPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = cityBadge.ifBlank { fullLocation },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BravPurple,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (fullLocation.isNotBlank() && fullLocation != cityBadge) {
                                Text(
                                    text = fullLocation,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CallMutedText,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (state.isActive && elapsedSeconds > 0L) {
                        "${state.statusLabel} • ${formatCallChronometer(elapsedSeconds)}"
                    } else {
                        state.statusLabel
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.isActive) CallActionGreen else BravPurple
                )
                val metaParts = listOfNotNull(
                    state.confidence.labelFr.takeIf { state.number.isNotBlank() },
                    state.sourceLabel.takeIf { it.isNotBlank() }
                )
                if (metaParts.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = CallMutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("carrier_call_confidence_source")
                    )
                }
                if (onSubmitCallerReport != null && state.number.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        onClick = { showReportDialog = true },
                        shape = RoundedCornerShape(999.dp),
                        color = lux.translucentCardSurface,
                        modifier = Modifier
                            .border(1.dp, lux.violetContour, RoundedCornerShape(999.dp))
                            .testTag("carrier_report_number_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Flag,
                                contentDescription = null,
                                tint = BravRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Signaler ce numéro",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = CallSlateText
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showDtmfPad && !state.isRinging) {
                    Spacer(Modifier.height(12.dp))
                    CallInlineDtmfKeypad(
                        typedDigits = typedDtmf,
                        onDigit = { ch ->
                            typedDtmf = (typedDtmf + ch).takeLast(18)
                            ch.firstOrNull()?.let { CarrierCallController.playDtmf(it) }
                        },
                        onClear = { typedDtmf = "" }
                    )
                }
            }

            if (state.isRinging) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = EndCallRed,
                            onClick = { CarrierCallController.rejectOrHangup() },
                            modifier = Modifier.size(72.dp).testTag("carrier_reject_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.CallEnd, contentDescription = "Refuser", tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Refuser", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = CallSlateText)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = CallActionGreen,
                            onClick = { CarrierCallController.answer() },
                            modifier = Modifier.size(72.dp).testTag("carrier_accept_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Call, contentDescription = "Répondre", tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Répondre", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = CallSlateText)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .widthIn(max = 460.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (CallAudioRoute.BLUETOOTH in state.availableAudioRoutes || state.audioRoute == CallAudioRoute.BLUETOOTH) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            state.availableAudioRoutes.forEach { route ->
                                val selected = state.audioRoute == route
                                val label = when (route) {
                                    CallAudioRoute.EARPIECE -> "Écouteur"
                                    CallAudioRoute.SPEAKER -> "Haut-parleur"
                                    CallAudioRoute.BLUETOOTH -> state.bluetoothDeviceName.ifBlank { "Bluetooth" }
                                }
                                val routeIcon = when (route) {
                                    CallAudioRoute.EARPIECE -> Icons.Rounded.PhoneInTalk
                                    CallAudioRoute.SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
                                    CallAudioRoute.BLUETOOTH -> Icons.Rounded.BluetoothAudio
                                }
                                Surface(
                                    onClick = { CarrierCallController.selectAudioRoute(route) },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (selected) BravPurple else lux.translucentCardSurface,
                                    border = BorderStroke(0.75.dp, if (selected) BravPurple else lux.champagneGold.copy(alpha = 0.5f)),
                                    modifier = Modifier.testTag("carrier_audio_route_${route.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            routeIcon,
                                            contentDescription = label,
                                            tint = if (selected) Color.White else CallSlateText,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) Color.White else CallSlateText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = lux.translucentCardSurface,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, lux.champagneGold.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (state.isMuted) BravPurple else lux.translucentSubtleSurface,
                                border = if (state.isMuted) null else BorderStroke(0.75.dp, lux.violetContour),
                                onClick = { CarrierCallController.toggleMute() },
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (state.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                        contentDescription = "Micro",
                                        tint = if (state.isMuted) Color.White else CallSlateText
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = if (state.isSpeakerOn) BravPurple else lux.translucentSubtleSurface,
                                border = if (state.isSpeakerOn) null else BorderStroke(0.75.dp, lux.violetContour),
                                onClick = { CarrierCallController.toggleSpeaker() },
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = "Haut-parleur",
                                        tint = if (state.isSpeakerOn) Color.White else CallSlateText
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = if (showDtmfPad) BravPurple else lux.translucentSubtleSurface,
                                border = if (showDtmfPad) null else BorderStroke(0.75.dp, lux.violetContour),
                                onClick = { showDtmfPad = !showDtmfPad },
                                modifier = Modifier.size(50.dp).testTag("carrier_dtmf_toggle")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Dialpad,
                                        contentDescription = "Clavier DTMF",
                                        tint = if (showDtmfPad) Color.White else CallSlateText
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = if (state.isHolding) BravPurple else lux.translucentSubtleSurface,
                                border = if (state.isHolding) null else BorderStroke(0.75.dp, lux.violetContour),
                                onClick = { CarrierCallController.toggleHold() },
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (state.isHolding) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                        contentDescription = "Attente",
                                        tint = if (state.isHolding) Color.White else CallSlateText
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = EndCallRed,
                                onClick = { CarrierCallController.rejectOrHangup() },
                                modifier = Modifier.size(58.dp).testTag("carrier_hangup_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.CallEnd, contentDescription = "Raccrocher", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showReportDialog && onSubmitCallerReport != null) {
        var category by remember { mutableStateOf(CallerReportCategory.SPAM) }
        var label by remember { mutableStateOf("") }
        var details by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            containerColor = lux.modalSurface,
            title = { Text("Signaler ce numéro", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = formattedNum.ifBlank { state.number },
                        style = MaterialTheme.typography.labelLarge,
                        color = BravPurple,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CallerReportCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat.labelFr) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Nom / activité observée (optionnel)") },
                        singleLine = true,
                        colors = bravOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text("Précisions (optionnel)") },
                        maxLines = 2,
                        colors = bravOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportDialog = false
                        onSubmitCallerReport(state.number, category, label.trim(), details.trim())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BravPurple, contentColor = Color.White),
                    modifier = Modifier.testTag("carrier_submit_report_button")
                ) {
                    Text("Envoyer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Annuler") }
            }
        )
    }
}
