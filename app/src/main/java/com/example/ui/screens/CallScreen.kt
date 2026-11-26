package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.local.AppPreferencesState
import com.example.media.VoiceAudioTelemetry
import com.example.media.WebRtcCallManager
import com.example.media.WebRtcConnectionState
import com.example.media.WebRtcSessionState
import com.example.model.Call
import com.example.model.CallAudioRoute
import com.example.model.CallLifecycleState
import com.example.model.User
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravTheme
import com.example.ui.theme.CallActionGreen
import com.example.ui.theme.EndCallRed
import com.example.ui.theme.StatusBusyAmber
import com.example.ui.theme.StatusOnlineGreen
import com.example.util.PhoneLocalityResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun ActiveCallOverlay(
    call: Call,
    currentUserId: String,
    peerUser: User? = null,
    appPrefs: AppPreferencesState = AppPreferencesState(),
    webRtcCallManager: WebRtcCallManager,
    onToggleHold: () -> Unit = {},
    onHangup: () -> Unit
) {
    CallScreen(
        call = call,
        currentUserId = currentUserId,
        peerUser = peerUser,
        appPrefs = appPrefs,
        webRtcCallManager = webRtcCallManager,
        onToggleHold = onToggleHold,
        onHangup = onHangup
    )
}

/**
 * Full-screen CallScreen wired directly to [WebRtcCallManager] real-time audio/video streams,
 * permissions, ringback tone, and hardware renderers.
 */
@Composable
fun CallScreen(
    call: Call,
    currentUserId: String,
    peerUser: User? = null,
    appPrefs: AppPreferencesState = AppPreferencesState(),
    webRtcCallManager: WebRtcCallManager,
    onToggleHold: () -> Unit = {},
    onHangup: () -> Unit
) {
    val context = LocalContext.current
    val session by webRtcCallManager.session.collectAsStateWithLifecycle()
    val audioTelemetry by webRtcCallManager.audioTelemetry.collectAsStateWithLifecycle()
    val isCaller = call.callerId == currentUserId

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        webRtcCallManager.refreshLocalMediaAfterPermissions()
    }
    LaunchedEffect(call.id) {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (call.callType == "video" && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())
    }

    val shouldPlayRingback = isCaller &&
        call.status == "calling" &&
        session.connectionState == WebRtcConnectionState.SIGNALING &&
        appPrefs.notifySoundEnabled
    LaunchedEffect(call.id, shouldPlayRingback) {
        if (!shouldPlayRingback) return@LaunchedEffect
        val toneGen = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 58) }.getOrNull()
        try {
            while (isActive) {
                runCatching { toneGen?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1100) }
                delay(3400L)
            }
        } finally {
            runCatching { toneGen?.stopTone() }
            runCatching { toneGen?.release() }
        }
    }

    val isVideo = call.callType == "video"
    val videoSlot: (@Composable BoxWithConstraintsScope.(String, String, String) -> Unit)? = if (isVideo) {
        val remoteRenderer = remember { SurfaceViewRenderer(context) }
        val localRenderer = remember { SurfaceViewRenderer(context) }
        DisposableEffect(call.id) {
            webRtcCallManager.attachRenderers(localRenderer, remoteRenderer)
            onDispose { webRtcCallManager.detachRenderers(localRenderer, remoteRenderer) }
        }
        val slot: @Composable BoxWithConstraintsScope.(String, String, String) -> Unit = { peerName, peerAvatarUrl, statusText ->
            val showBackgroundFeed = if (session.isVideoSwapped) session.isCameraEnabled else session.hasRemoteVideo
            AndroidView(
                factory = {
                    remoteRenderer.apply {
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (!showBackgroundFeed) {
                VideoStagePlaceholder(
                    displayName = if (session.isVideoSwapped) "Vous" else peerName,
                    avatarUrl = if (session.isVideoSwapped) "" else peerAvatarUrl,
                    statusText = when {
                        session.isOnHold || call.status == "on_hold" -> "En attente"
                        session.isVideoSwapped -> "Caméra en pause"
                        call.status == "calling" -> "Sonnerie..."
                        else -> statusText
                    }
                )
            }
            CallVideoPipFrame(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                localRenderer = localRenderer,
                session = session,
                peerName = peerName,
                onSwapFeeds = { webRtcCallManager.setVideoSwapped(!session.isVideoSwapped) }
            )
        }
        slot
    } else null

    CallScreenContent(
        call = call,
        currentUserId = currentUserId,
        peerUser = peerUser,
        session = session,
        audioTelemetry = audioTelemetry,
        onToggleMute = { webRtcCallManager.toggleMute() },
        onToggleSpeaker = { webRtcCallManager.toggleSpeaker() },
        onSelectAudioRoute = { route -> webRtcCallManager.selectAudioRoute(route) },
        onToggleHold = onToggleHold,
        onToggleCamera = { webRtcCallManager.toggleCamera() },
        onSwitchCamera = { webRtcCallManager.switchCamera() },
        onHangup = onHangup,
        videoStageContent = videoSlot
    )
}

/**
 * Stateless presentation component for [CallScreen], rendering peer identity details, live WebRTC
 * chronometer, real-time audio DSP status, and responsive audio/video control docks.
 */
@Composable
fun CallScreenContent(
    call: Call,
    currentUserId: String,
    peerUser: User? = null,
    session: WebRtcSessionState,
    audioTelemetry: VoiceAudioTelemetry = VoiceAudioTelemetry(),
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSelectAudioRoute: (CallAudioRoute) -> Unit = {},
    onToggleHold: () -> Unit,
    onToggleCamera: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onHangup: () -> Unit,
    videoStageContent: (@Composable BoxWithConstraintsScope.(peerName: String, peerAvatarUrl: String, statusText: String) -> Unit)? = null
) {
    val isCaller = call.callerId == currentUserId
    val peerUid = if (isCaller) call.receiverId else call.callerId
    val verifiedPeer = peerUser?.takeIf { it.userId == peerUid }
    val peerName = verifiedPeer?.displayName?.ifBlank { null }
        ?: (if (isCaller) call.receiverName else call.callerName).ifBlank { "Correspondant BRAV" }
    val peerAvatarUrl = verifiedPeer?.avatarUrl?.ifBlank { null }
        ?: (if (isCaller) call.receiverAvatar else call.callerAvatar)
    val peerUsername = verifiedPeer?.username?.ifBlank { null }
        ?: if (isCaller) call.receiverUsername else call.callerUsername
    val peerPhone = verifiedPeer?.phoneMasked?.ifBlank { null }
    val peerCity = verifiedPeer?.locationCity?.ifBlank { null }
    val localityBadge = remember(peerPhone) {
        peerPhone?.let { PhoneLocalityResolver.resolve(it).compactBadge }?.ifBlank { null }
    }
    val peerSubtitle = buildList {
        if (peerUsername.isNotBlank()) add("@$peerUsername")
        if (peerPhone != null) add(peerPhone)
        if (isEmpty() && peerUid.isNotBlank()) add("ID : ${peerUid.take(8)}...")
    }.joinToString(" • ").ifBlank { "Membre BRAV CHAT" }

    val isEstablished = session.connectionState == WebRtcConnectionState.CONNECTED || session.elapsedSeconds > 0L
    val chronometerText = formatCallChronometer(session.elapsedSeconds)
    val effectiveLifecycle = when {
        session.isOnHold || session.isRemoteOnHold || call.status == "on_hold" -> CallLifecycleState.ON_HOLD
        session.connectionState == WebRtcConnectionState.FAILED -> CallLifecycleState.FAILED
        session.connectionState == WebRtcConnectionState.RECONNECTING -> CallLifecycleState.CONNECTION_LOST
        session.connectionState == WebRtcConnectionState.CONNECTED -> CallLifecycleState.ACTIVE
        call.status == "accepted" -> CallLifecycleState.ACCEPTED
        call.status == "calling" -> CallLifecycleState.RINGING
        else -> call.effectiveLifecycleState().takeIf { call.lifecycleState.isNotBlank() } ?: session.lifecycleState
    }
    val stateLabel = when {
        session.isOnHold -> "Appel en attente"
        session.isRemoteOnHold || call.status == "on_hold" -> "Mis en attente par $peerName"
        session.connectionState == WebRtcConnectionState.FAILED -> session.errorMessage ?: effectiveLifecycle.labelFr
        session.connectionState == WebRtcConnectionState.RECONNECTING -> "Connexion perdue • Reconnexion..."
        session.connectionState == WebRtcConnectionState.CONNECTED ->
            if (session.usingRelayTurn) "En communication • Relais sécurisé" else "En communication • Audio HD"
        call.status == "accepted" -> CallLifecycleState.ACCEPTED.labelFr
        call.status == "calling" -> CallLifecycleState.RINGING.labelFr
        session.connectionState == WebRtcConnectionState.CONNECTING -> "Établissement sécurisé..."
        else -> effectiveLifecycle.labelFr
    }

    var showDetailsPanel by remember { mutableStateOf(false) }
    var showDtmfKeypad by remember { mutableStateOf(false) }
    var dtmfSequence by remember { mutableStateOf("") }

    val lux = BravTheme.colors
    if (call.callType == "video") {
        var hudVisible by remember { mutableStateOf(true) }
        val effectiveHudVisible = hudVisible ||
            !isEstablished ||
            session.isOnHold ||
            session.isRemoteOnHold ||
            call.status == "on_hold" ||
            !session.hasRemoteVideo

        val videoSubLabel = when {
            session.isOnHold || call.status == "on_hold" -> "En attente"
            session.isRemoteOnHold -> "En attente"
            session.connectionState == WebRtcConnectionState.FAILED -> effectiveLifecycle.labelFr
            session.connectionState == WebRtcConnectionState.RECONNECTING -> "Reconnexion..."
            isEstablished -> chronometerText
            call.status == "calling" -> CallLifecycleState.RINGING.labelFr
            else -> effectiveLifecycle.labelFr
        }

        Surface(modifier = Modifier.fillMaxSize().zIndex(101f), color = Color(0xFF100B1B)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF100B1B))
                    .pointerInput(isEstablished) {
                        detectTapGestures(onTap = {
                            if (isEstablished) hudVisible = !hudVisible
                        })
                    }
            ) {
                BravAfricanPatternCanvas(
                    pattern = BravPatternType.HERITAGE_COVER,
                    variant = BravPatternVariant.WHITE_ON_VIOLET,
                    opacity = 0.095f,
                    scale = 1.0f
                )
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    if (videoStageContent != null) {
                        videoStageContent(peerName, peerAvatarUrl, videoSubLabel)
                    } else {
                        VideoStagePlaceholder(
                            displayName = peerName,
                            avatarUrl = peerAvatarUrl,
                            statusText = videoSubLabel
                        )
                    }
                }

                // Clean floating top header over soft vertical scrim
                AnimatedVisibility(
                    visible = effectiveHudVisible,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180)),
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xC2170D29), Color.Transparent)
                                )
                            )
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.size(44.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = peerName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("call_peer_name")
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = lux.champagneGold,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = videoSubLabel,
                                        style = TextStyle(
                                            fontFeatureSettings = "tnum",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = if (session.isOnHold) StatusBusyAmber else Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.testTag("call_chronometer_text")
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = if (session.isOnHold) StatusBusyAmber else Color.White.copy(alpha = 0.14f),
                                border = BorderStroke(0.75.dp, lux.champagneGold.copy(alpha = 0.5f)),
                                onClick = onToggleHold,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(44.dp)
                                    .testTag("toggle_hold_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (session.isOnHold) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                        contentDescription = if (session.isOnHold) "Reprendre" else "Mettre en attente",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Floating African-luxury glass dock
                AnimatedVisibility(
                    visible = effectiveHudVisible,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180)),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xCC140B24))
                                )
                            )
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (CallAudioRoute.BLUETOOTH in session.availableAudioRoutes || session.audioRoute == CallAudioRoute.BLUETOOTH) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                session.availableAudioRoutes.forEach { route ->
                                    val selected = session.audioRoute == route
                                    val label = when (route) {
                                        CallAudioRoute.EARPIECE -> "Écouteur"
                                        CallAudioRoute.SPEAKER -> "Haut-parleur"
                                        CallAudioRoute.BLUETOOTH -> session.bluetoothDeviceName.ifBlank { "Bluetooth" }
                                    }
                                    Surface(
                                        onClick = { onSelectAudioRoute(route) },
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (selected) BravPurple else Color(0xAA1E152E),
                                        border = BorderStroke(0.75.dp, lux.champagneGold.copy(alpha = if (selected) 0.85f else 0.4f)),
                                        modifier = Modifier.testTag("video_audio_route_${route.name.lowercase()}")
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(36.dp),
                            color = Color(0xC21D1430),
                            modifier = Modifier
                                .widthIn(max = 400.dp)
                                .fillMaxWidth()
                                .border(0.9.dp, lux.champagneGold.copy(alpha = 0.45f), RoundedCornerShape(36.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                VideoDockIconButton(
                                    icon = if (session.isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.PhoneInTalk,
                                    contentDescription = "Haut-parleur",
                                    active = session.isSpeakerOn,
                                    activeBg = lux.champagneGold,
                                    activeTint = Color(0xFF1A0F2E),
                                    testTag = "toggle_speaker_button",
                                    onClick = onToggleSpeaker
                                )
                                VideoDockIconButton(
                                    icon = if (session.isCameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                                    contentDescription = "Caméra",
                                    active = !session.isCameraEnabled,
                                    activeBg = Color.White,
                                    activeTint = Color(0xFF0F172A),
                                    testTag = "toggle_camera_button",
                                    onClick = onToggleCamera
                                )
                                VideoDockIconButton(
                                    icon = Icons.Rounded.Cameraswitch,
                                    contentDescription = "Changer de caméra",
                                    active = false,
                                    enabled = session.isCameraEnabled,
                                    testTag = "switch_camera_button",
                                    onClick = onSwitchCamera
                                )
                                VideoDockIconButton(
                                    icon = if (session.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                    contentDescription = "Micro",
                                    active = session.isMuted,
                                    activeBg = Color.White,
                                    activeTint = Color(0xFF0F172A),
                                    testTag = "toggle_mic_button",
                                    onClick = onToggleMute
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = EndCallRed,
                                    onClick = onHangup,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .size(52.dp)
                                        .testTag("hangup_call_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.CallEnd,
                                            contentDescription = "Terminer",
                                            tint = Color.White,
                                            modifier = Modifier.size(25.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Surface(modifier = Modifier.fillMaxSize().zIndex(101f), color = lux.ivoryCanvas) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(lux.ivoryCanvas)
            ) {
                BravAfricanPatternCanvas(
                    pattern = BravPatternType.KENTE_DIAMOND,
                    variant = BravPatternVariant.GOLD_ON_IVORY,
                    opacity = 0.095f,
                    scale = 1.05f
                )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // Top security & real-time WebRTC audio status bar
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .widthIn(max = 540.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = lux.translucentCardSurface,
                        shadowElevation = 0.dp,
                        modifier = Modifier.border(0.9.dp, lux.champagneGold.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = StatusOnlineGreen, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Audio E2EE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = CallMutedText
                            )
                        }
                    }
                    AudioDspBadge(
                        telemetry = audioTelemetry,
                        isLightSurface = true,
                        onClick = { showDetailsPanel = !showDetailsPanel }
                    )
                }

                // Center detailed profile portrait, contact metadata, live chronometer & optional inspector
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp, bottom = 104.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val ultraCompact = maxHeight < 340.dp
                    val compactHeight = maxHeight < 420.dp
                    val avatarSize = when {
                        ultraCompact -> 68.dp
                        compactHeight -> 88.dp
                        else -> 148.dp
                    }
                    val topSpacer = if (ultraCompact) 6.dp else if (compactHeight) 10.dp else 20.dp
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CallPeerPhotoAvatar(
                            name = peerName,
                            avatarUrl = peerAvatarUrl,
                            size = avatarSize,
                            ringAnimated = call.status == "calling",
                            isLightSurface = true,
                            modifier = Modifier.clickable { showDetailsPanel = !showDetailsPanel }
                        )
                        Spacer(Modifier.height(topSpacer))
                        Text(
                            text = peerName,
                            style = if (compactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = CallSlateText,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("call_peer_name")
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { showDetailsPanel = !showDetailsPanel }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = peerSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CallMutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = if (showDetailsPanel) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = "Détails du contact",
                                tint = CallMutedText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        val effectiveLocality = listOfNotNull(
                            peerCity?.takeIf { it.isNotBlank() },
                            localityBadge?.takeIf { it.isNotBlank() && it != peerCity }
                        ).joinToString(" • ")
                        if (effectiveLocality.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = BravPurple.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .testTag("call_peer_locality_badge")
                            ) {
                                Text(
                                    text = effectiveLocality,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BravPurple,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(if (compactHeight) 6.dp else 10.dp))

                        // Status chip
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = when {
                                session.isOnHold || session.isRemoteOnHold || call.status == "on_hold" -> StatusBusyAmber.copy(alpha = 0.14f)
                                session.connectionState == WebRtcConnectionState.FAILED -> EndCallRed.copy(alpha = 0.12f)
                                session.connectionState == WebRtcConnectionState.CONNECTED -> CallActionGreen.copy(alpha = 0.12f)
                                else -> BravPurple.copy(alpha = 0.1f)
                            },
                            modifier = Modifier.testTag("call_status_pill")
                        ) {
                            Text(
                                text = stateLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    session.isOnHold || session.isRemoteOnHold || call.status == "on_hold" -> Color(0xFFB45309)
                                    session.connectionState == WebRtcConnectionState.FAILED -> EndCallRed
                                    session.connectionState == WebRtcConnectionState.CONNECTED -> CallActionGreen
                                    else -> BravPurple
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                            )
                        }

                        // Dedicated tabular chronometer when connected or non-zero
                        if (isEstablished) {
                            Spacer(Modifier.height(if (compactHeight) 6.dp else 10.dp))
                            Text(
                                text = chronometerText,
                                style = TextStyle(
                                    fontFeatureSettings = "tnum",
                                    fontSize = if (compactHeight) 26.sp else 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                ),
                                color = if (session.isOnHold || session.isRemoteOnHold) Color(0xFFB45309) else CallSlateText,
                                modifier = Modifier.testTag("call_chronometer_text")
                            )
                        }

                        AnimatedVisibility(
                            visible = showDetailsPanel,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            CallDetailsExpandedCard(
                                peerSubtitle = peerSubtitle,
                                peerCity = peerCity,
                                stateLabel = stateLabel,
                                telemetry = audioTelemetry,
                                isLightSurface = true
                            )
                        }

                        AnimatedVisibility(
                            visible = showDtmfKeypad,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            CallInlineDtmfKeypad(
                                typedDigits = dtmfSequence,
                                onDigit = { ch -> dtmfSequence = (dtmfSequence + ch).takeLast(18) },
                                onClear = { dtmfSequence = "" }
                            )
                        }
                    }
                }

                // Elevated translucent bottom dock + Bluetooth audio routing selector
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 520.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (CallAudioRoute.BLUETOOTH in session.availableAudioRoutes || session.audioRoute == CallAudioRoute.BLUETOOTH) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            session.availableAudioRoutes.forEach { route ->
                                val selected = session.audioRoute == route
                                val label = when (route) {
                                    CallAudioRoute.EARPIECE -> "Écouteur"
                                    CallAudioRoute.SPEAKER -> "Haut-parleur"
                                    CallAudioRoute.BLUETOOTH -> session.bluetoothDeviceName.ifBlank { "Bluetooth" }
                                }
                                val routeIcon = when (route) {
                                    CallAudioRoute.EARPIECE -> Icons.Rounded.PhoneInTalk
                                    CallAudioRoute.SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
                                    CallAudioRoute.BLUETOOTH -> Icons.Rounded.BluetoothAudio
                                }
                                Surface(
                                    onClick = { onSelectAudioRoute(route) },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (selected) BravPurple else lux.translucentCardSurface,
                                    border = BorderStroke(0.75.dp, if (selected) BravPurple else lux.champagneGold.copy(alpha = 0.55f)),
                                    modifier = Modifier.testTag("audio_route_${route.name.lowercase()}")
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Top
                        ) {
                            CallCommandButton(
                                icon = if (session.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                label = if (session.isMuted) "Coupé" else "Micro",
                                active = session.isMuted,
                                activeBg = EndCallRed,
                                isLightSurface = true,
                                size = 48.dp,
                                testTag = "toggle_mic_button",
                                onClick = onToggleMute
                            )
                            CallCommandButton(
                                icon = if (session.isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.PhoneInTalk,
                                label = if (session.isSpeakerOn) "HP actif" else "HP",
                                active = session.isSpeakerOn,
                                activeBg = BravPurple,
                                isLightSurface = true,
                                size = 48.dp,
                                testTag = "toggle_speaker_button",
                                onClick = onToggleSpeaker
                            )
                            CallCommandButton(
                                icon = Icons.Rounded.Dialpad,
                                label = "Clavier",
                                active = showDtmfKeypad,
                                activeBg = BravPurple,
                                isLightSurface = true,
                                size = 48.dp,
                                testTag = "toggle_dtmf_button",
                                onClick = { showDtmfKeypad = !showDtmfKeypad }
                            )
                            CallCommandButton(
                                icon = if (session.isOnHold) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                label = if (session.isOnHold) "Reprendre" else "Attente",
                                active = session.isOnHold,
                                activeBg = StatusBusyAmber,
                                isLightSurface = true,
                                size = 48.dp,
                                testTag = "toggle_hold_button",
                                onClick = onToggleHold
                            )
                            HangupCommandButton(isLightSurface = true, size = 48.dp, onHangup = onHangup)
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun VideoDockIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    enabled: Boolean = true,
    activeBg: Color = Color.White,
    activeTint: Color = Color(0xFF0F172A),
    size: Dp = 50.dp,
    testTag: String,
    onClick: () -> Unit
) {
    val bgColor = when {
        !enabled -> Color.White.copy(alpha = 0.08f)
        active -> activeBg
        else -> Color.White.copy(alpha = 0.14f)
    }
    val tintColor = when {
        !enabled -> Color.White.copy(alpha = 0.35f)
        active -> activeTint
        else -> Color.White
    }
    Surface(
        shape = CircleShape,
        color = bgColor,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tintColor,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

@Composable
private fun CallDetailsExpandedCard(
    peerSubtitle: String,
    peerCity: String?,
    stateLabel: String,
    telemetry: VoiceAudioTelemetry,
    isLightSurface: Boolean
) {
    val lux = BravTheme.colors
    val bg = if (isLightSurface) lux.translucentCardSurface else Color(0xFF1E293B)
    val fg = if (isLightSurface) CallSlateText else Color.White
    val sub = if (isLightSurface) CallMutedText else Color(0xFFCBD5E1)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bg,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .border(1.dp, if (isLightSurface) lux.champagneGold.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .testTag("call_details_panel")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Phone, contentDescription = null, tint = BravPurple, modifier = Modifier.size(15.dp))
                Text(
                    text = listOfNotNull(peerSubtitle, peerCity).joinToString(" • "),
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "État flux : $stateLabel",
                style = MaterialTheme.typography.labelSmall,
                color = sub
            )
            LinearProgressIndicator(
                progress = { telemetry.inputLevel.coerceIn(0f, 1f) },
                color = if (telemetry.speechDetected) StatusOnlineGreen else BravPurple,
                trackColor = if (isLightSurface) lux.translucentSubtleSurface else Color.White.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp))
            )
            val aecMode = if (telemetry.hwAecEnabled) "AEC Matériel" else if (telemetry.echoCancellationActive) "AEC WebRTC" else "Désactivé"
            val nsMode = if (telemetry.hwNsEnabled) "NS Matériel + DSP" else if (telemetry.noiseSuppressionActive) "DSP BRAV" else "Désactivé"
            Text(
                text = "AEC : $aecMode • NS : $nsMode • Trames transmises : ${telemetry.processedCaptureFrames}",
                style = MaterialTheme.typography.labelSmall,
                color = sub
            )
        }
    }
}

@Composable
private fun AudioDspBadge(
    telemetry: VoiceAudioTelemetry,
    isLightSurface: Boolean,
    onClick: () -> Unit = {}
) {
    val aecActive = telemetry.hwAecEnabled || telemetry.echoCancellationActive
    val nsActive = telemetry.hwNsEnabled || telemetry.noiseSuppressionActive
    val aecTag = if (telemetry.hwAecEnabled) "AEC Matériel" else if (aecActive) "AEC WebRTC" else null
    val label = when {
        telemetry.voicePriorityActive && nsActive && aecActive ->
            if (telemetry.noiseReductionDb >= 2f && !telemetry.speechDetected) {
                "Voix prioritaire • NS (-${telemetry.noiseReductionDb.toInt()} dB) • $aecTag"
            } else {
                "Voix prioritaire • NS + $aecTag"
            }
        nsActive && aecActive -> "Anti-bruit + $aecTag actifs"
        nsActive -> "Réduction du bruit active"
        aecActive -> "$aecTag actif"
        telemetry.voicePriorityActive -> "Priorité vocale active"
        else -> "Audio standard"
    }
    val lux = BravTheme.colors
    val tint = if (telemetry.speechDetected) CallActionGreen else if (isLightSurface) BravPurple else Color(0xFFA7F3D0)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (isLightSurface) lux.translucentCardSurface else Color.White.copy(alpha = 0.1f),
        onClick = onClick,
        modifier = Modifier
            .testTag("audio_dsp_badge")
            .then(if (isLightSurface) Modifier.border(0.9.dp, lux.champagneGold.copy(alpha = 0.55f), RoundedCornerShape(999.dp)) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = "Détails du traitement audio", tint = tint, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isLightSurface) CallMutedText else Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CallCommandButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeBg: Color = BravPurple,
    enabled: Boolean = true,
    isLightSurface: Boolean,
    size: Dp = 54.dp,
    testTag: String,
    onClick: () -> Unit
) {
    val lux = BravTheme.colors
    val bgColor = when {
        !enabled -> if (isLightSurface) lux.translucentSubtleSurface.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.06f)
        active -> activeBg
        isLightSurface -> lux.translucentSubtleSurface
        else -> Color.White.copy(alpha = 0.14f)
    }
    val iconTint = when {
        !enabled -> if (isLightSurface) Color(0xFF94A3B8) else Color.White.copy(alpha = 0.35f)
        active -> Color.White
        isLightSurface -> CallSlateText
        else -> Color.White
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = bgColor,
            border = if (isLightSurface && !active) BorderStroke(0.75.dp, lux.violetContour) else null,
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(size)
                .testTag(testTag)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (isLightSurface) CallMutedText else Color.White.copy(alpha = 0.85f),
            maxLines = 1
        )
    }
}

@Composable
private fun HangupCommandButton(
    isLightSurface: Boolean,
    size: Dp = 54.dp,
    onHangup: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = EndCallRed,
            onClick = onHangup,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(size)
                .testTag("hangup_call_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CallEnd, contentDescription = "Raccrocher", tint = Color.White, modifier = Modifier.size(25.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Terminer",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isLightSurface) EndCallRed else Color(0xFFFCA5A5),
            maxLines = 1
        )
    }
}

fun formatCallChronometer(elapsedSeconds: Long): String {
    val safe = elapsedSeconds.coerceAtLeast(0L)
    val h = safe / 3600L
    val m = (safe % 3600L) / 60L
    val s = safe % 60L
    return if (h > 0L) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
internal fun CallInlineDtmfKeypad(
    typedDigits: String,
    onDigit: (String) -> Unit,
    onClear: () -> Unit
) {
    val dtmfMap = remember {
        mapOf(
            "1" to ToneGenerator.TONE_DTMF_1,
            "2" to ToneGenerator.TONE_DTMF_2,
            "3" to ToneGenerator.TONE_DTMF_3,
            "4" to ToneGenerator.TONE_DTMF_4,
            "5" to ToneGenerator.TONE_DTMF_5,
            "6" to ToneGenerator.TONE_DTMF_6,
            "7" to ToneGenerator.TONE_DTMF_7,
            "8" to ToneGenerator.TONE_DTMF_8,
            "9" to ToneGenerator.TONE_DTMF_9,
            "0" to ToneGenerator.TONE_DTMF_0,
            "*" to ToneGenerator.TONE_DTMF_S,
            "#" to ToneGenerator.TONE_DTMF_P
        )
    }
    val toneGen = remember { runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 60) }.getOrNull() }
    DisposableEffect(Unit) {
        onDispose { runCatching { toneGen?.release() } }
    }

    val lux = BravTheme.colors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = lux.translucentCardSurface,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .border(1.dp, lux.champagneGold.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .testTag("call_dtmf_keypad")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = typedDigits.ifBlank { "Touches DTMF" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CallSlateText
                )
                if (typedDigits.isNotEmpty()) {
                    Text(
                        text = "Effacer",
                        style = MaterialTheme.typography.labelMedium,
                        color = BravPurple,
                        modifier = Modifier.clickable(onClick = onClear)
                    )
                }
            }
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("*", "0", "#")
            ).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { d ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = lux.translucentSubtleSurface,
                            border = BorderStroke(0.65.dp, lux.violetContour),
                            onClick = {
                                dtmfMap[d]?.let { code -> runCatching { toneGen?.startTone(code, 140) } }
                                onDigit(d)
                            },
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(d, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = CallSlateText)
                            }
                        }
                    }
                }
            }
        }
    }
}
