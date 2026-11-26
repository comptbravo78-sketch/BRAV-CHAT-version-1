package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.R
import com.example.media.VoicePlaybackState
import com.example.model.VerificationStatus
import com.example.network.WorkerApiClient
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.ui.theme.StatusBusyAmber
import com.example.ui.theme.StatusDndRed
import com.example.ui.theme.StatusOfflineGray
import com.example.ui.theme.StatusOnlineGreen
import com.example.util.BiometricAuthManager
import com.example.util.MediaDownloadHelper
import com.example.util.findFragmentActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BravLogoMark(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.ic_brav_logo),
        contentDescription = "Logo officiel BRAV Chat",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit
    )
}

@Composable
fun UserAvatarWithStatus(
    name: String,
    avatarUrl: String,
    availabilityStatus: String = "offline",
    isOnlineRealtime: Boolean? = null,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val effectiveStatus = when {
        availabilityStatus == "invisible" -> "invisible"
        availabilityStatus == "dnd" -> "dnd"
        availabilityStatus == "busy" -> "busy"
        isOnlineRealtime == true || availabilityStatus == "online" -> "online"
        else -> "offline"
    }

    val statusColor = when (effectiveStatus) {
        "online" -> StatusOnlineGreen
        "busy" -> StatusBusyAmber
        "dnd" -> StatusDndRed
        else -> StatusOfflineGray
    }

    Box(modifier = modifier.size(size)) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Photo de $name",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            val initials = name.trim()
                .split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercase() }
                .ifBlank { "B" }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(BravPurple, BravRed))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        if (effectiveStatus != "invisible") {
            Box(
                modifier = Modifier
                    .size((size.value * 0.28f).dp.coerceAtLeast(10.dp))
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(statusColor)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun VoiceMessageBubble(
    mediaUrl: String,
    durationMs: Long,
    waveform: List<Int>,
    playbackState: VoicePlaybackState,
    isMine: Boolean,
    onTogglePlay: () -> Unit,
    onCycleSpeed: () -> Unit
) {
    val isThisActive = playbackState.activeUrl == mediaUrl
    val isThisPlaying = isThisActive && playbackState.isPlaying
    val totalMs = if (isThisActive && playbackState.durationMs > 0L) playbackState.durationMs else durationMs.coerceAtLeast(1000L)
    val progress = if (isThisActive) (playbackState.positionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f

    val bars = remember(waveform) {
        val raw = if (waveform.isNotEmpty()) waveform else List(30) { idx ->
            22 + ((idx * 19 + (idx % 3) * 11) % 62)
        }
        // Normalize smoothly to ~30 fine vertical strokes
        if (raw.size <= 30) raw
        else List(30) { i -> raw[(i * raw.size) / 30] }
    }
    val fg = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
    val activeAccent = if (isMine) Color.White else MaterialTheme.colorScheme.primary
    val inactiveStroke = if (isMine) Color.White.copy(alpha = 0.34f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
    val trackHairline = if (isMine) Color.White.copy(alpha = 0.20f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val playBg = if (isMine) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val playTint = if (isMine) Color.White else MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .widthIn(min = 176.dp, max = 212.dp)
            .padding(vertical = 1.dp)
            .testTag("voice_message_bubble")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(playBg)
                .clickable(onClick = onTogglePlay)
                .testTag("voice_bubble_play_button")
        ) {
            if (isThisActive && playbackState.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    color = playTint,
                    strokeWidth = 1.8.dp
                )
            } else {
                Icon(
                    imageVector = if (isThisPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isThisPlaying) "Mettre en pause" else "Lire le message vocal",
                    tint = playTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .testTag("voice_waveform_track")
            ) {
                val count = bars.size.coerceAtLeast(1)
                val strokePx = 1.5.dp.toPx().coerceAtLeast(1.8f)
                val dotRadius = 2.4.dp.toPx()
                val padX = dotRadius
                val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
                val centerY = size.height / 2f

                // Ultra-thin baseline guide
                drawLine(
                    color = trackHairline,
                    start = Offset(padX, centerY),
                    end = Offset(padX + usableW, centerY),
                    strokeWidth = 0.9.dp.toPx(),
                    cap = StrokeCap.Round
                )

                val step = if (count > 1) usableW / (count - 1).toFloat() else usableW
                bars.forEachIndexed { idx, amp ->
                    val frac = if (count > 1) idx.toFloat() / (count - 1).toFloat() else 0f
                    val normAmp = (amp / 100f).coerceIn(0.18f, 0.96f)
                    val halfH = (size.height * 0.44f * normAmp).coerceAtLeast(1.8f)
                    val x = padX + idx * step
                    val isFilled = isThisActive && frac <= progress
                    drawLine(
                        color = if (isFilled) activeAccent else inactiveStroke,
                        start = Offset(x, centerY - halfH),
                        end = Offset(x, centerY + halfH),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }

                if (isThisActive) {
                    val activeX = padX + usableW * progress
                    drawLine(
                        color = activeAccent.copy(alpha = 0.72f),
                        start = Offset(padX, centerY),
                        end = Offset(activeX, centerY),
                        strokeWidth = 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = activeAccent,
                        radius = dotRadius,
                        center = Offset(activeX, centerY)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val shownMs = if (isThisActive && playbackState.positionMs > 0L) playbackState.positionMs else totalMs
                Text(
                    text = formatDurationMs(shownMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = fg.copy(alpha = 0.76f)
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isMine) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                    onClick = onCycleSpeed,
                    modifier = Modifier.testTag("voice_speed_toggle")
                ) {
                    val speedLabel = if (playbackState.speed % 1f == 0f) {
                        "${playbackState.speed.toInt()}x"
                    } else {
                        "${playbackState.speed}x"
                    }
                    Text(
                        text = speedLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = fg.copy(alpha = 0.88f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MediaLightboxDialog(
    url: String,
    isVideo: Boolean,
    title: String,
    workerApiClient: WorkerApiClient? = null,
    onSavedFeedback: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var holdProgress by remember(url) { mutableFloatStateOf(0f) }
    var showDownloadButton by remember(url) { mutableStateOf(false) }
    var isSavingMedia by remember(url) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isVideo) {
                    var localVideoFile by remember(url) { mutableStateOf<File?>(null) }
                    var videoError by remember(url) { mutableStateOf<String?>(null) }
                    LaunchedEffect(url) {
                        if (workerApiClient != null) {
                            val res = workerApiClient.downloadMediaToCache(context, url, title.ifBlank { "video.mp4" })
                            res.onSuccess { localVideoFile = it }
                                .onFailure { videoError = it.localizedMessage ?: "Lecture vidéo impossible" }
                        }
                    }

                    val playbackUri = localVideoFile?.let { Uri.fromFile(it) } ?: if (workerApiClient == null) Uri.parse(url) else null
                    if (playbackUri != null) {
                        val exoPlayer = remember(playbackUri) {
                            ExoPlayer.Builder(context).build().apply {
                                setMediaItem(MediaItem.fromUri(playbackUri))
                                prepare()
                                playWhenReady = true
                            }
                        }
                        DisposableEffect(exoPlayer) {
                            onDispose { exoPlayer.release() }
                        }
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (videoError != null) {
                        Text(
                            text = videoError ?: "Erreur vidéo",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = Color.White)
                            Text("Chargement vidéo...", color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(url) {
                                coroutineScope {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        val holdJob = launch {
                                            delay(350L)
                                            val remainingTarget = 5000L - 350L
                                            var elapsed = 0L
                                            while (true) {
                                                val tickStart = System.currentTimeMillis()
                                                delay(45L)
                                                val wallDelta = (System.currentTimeMillis() - tickStart).coerceAtLeast(0L)
                                                elapsed += maxOf(45L, wallDelta)
                                                holdProgress = (elapsed.toFloat() / remainingTarget.toFloat()).coerceIn(0f, 1f)
                                                if (elapsed >= remainingTarget) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    showDownloadButton = true
                                                    holdProgress = 0f
                                                    break
                                                }
                                            }
                                        }
                                        waitForUpOrCancellation()
                                        holdJob.cancel()
                                        holdProgress = 0f
                                    }
                                }
                            }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (holdProgress > 0f && !showDownloadButton) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier.align(Alignment.Center).size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress = { holdProgress },
                                        color = Color.White,
                                        trackColor = Color.White.copy(alpha = 0.25f),
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                        if (showDownloadButton) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFF14111F).copy(alpha = 0.92f),
                                tonalElevation = 6.dp,
                                onClick = {
                                    if (!isSavingMedia) {
                                        isSavingMedia = true
                                        scope.launch {
                                            val res = MediaDownloadHelper.saveMediaToGallery(
                                                context = context,
                                                workerApiClient = workerApiClient,
                                                mediaUrl = url,
                                                fileNameHint = title,
                                                isVideo = false
                                            )
                                            isSavingMedia = false
                                            showDownloadButton = false
                                            val feedback = if (res.isSuccess) {
                                                "Image enregistrée dans la galerie"
                                            } else {
                                                res.exceptionOrNull()?.localizedMessage ?: "Téléchargement impossible"
                                            }
                                            onSavedFeedback?.invoke(feedback)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 36.dp)
                                    .testTag("lightbox_download_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isSavingMedia) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.Download,
                                            contentDescription = "Télécharger",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isSavingMedia) "Enregistrement..." else "Télécharger",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = Color.White)
                    }
                    Text(
                        text = title.ifBlank { "Aperçu média" },
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun ChatLockPromptDialog(
    title: String = "Messages verrouillés",
    hasPinConfigured: Boolean,
    preferBiometric: Boolean = true,
    onVerifyPin: suspend (String) -> Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun launchBiometric() {
        val activity = context.findFragmentActivity() ?: run {
            errorMsg = "Biométrie indisponible sur cet appareil."
            return
        }
        BiometricAuthManager.authenticate(
            activity = activity,
            title = title,
            subtitle = "Empreinte digitale, reconnaissance faciale ou schéma",
            useDeviceCredentialFallback = !hasPinConfigured,
            negativeButtonLabel = "Utiliser le code PIN",
            onSuccess = onSuccess,
            onNegativeFallback = { },
            onError = { errorMsg = it }
        )
    }

    LaunchedEffect(preferBiometric) {
        if (preferBiometric &&
            context.findFragmentActivity() != null &&
            BiometricAuthManager.isBiometricHardwareAvailable(context)
        ) {
            launchBiometric()
        }
    }

    val lux = BravTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = lux.modalSurface,
        modifier = Modifier.testTag("chat_lock_dialog"),
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = BravPurple) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (hasPinConfigured) {
                        "Empreinte, reconnaissance faciale ou code PIN"
                    } else {
                        "Empreinte digitale, reconnaissance faciale ou code système"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasPinConfigured) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            errorMsg = null
                            pinInput = it.filter { ch -> ch.isDigit() }.take(8)
                        },
                        label = { Text("Code PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = bravOutlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_lock_pin_input")
                    )
                }
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = { launchBiometric() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_lock_biometric_button")
                ) {
                    Icon(
                        Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Empreinte / Visage")
                }
            }
        },
        confirmButton = {
            if (hasPinConfigured) {
                var checking by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                Button(
                    enabled = pinInput.length >= 4 && !checking,
                    modifier = Modifier.testTag("chat_lock_validate_button"),
                    onClick = {
                        checking = true
                        scope.launch {
                            val ok = onVerifyPin(pinInput)
                            checking = false
                            if (ok) onSuccess() else errorMsg = "Code PIN incorrect"
                        }
                    }
                ) {
                    Text("Valider")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

fun formatDurationMs(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(0L)
    val m = sec / 60L
    val s = sec % 60L
    return "%d:%02d".format(m, s)
}

private val shortTimeFormatter = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
}

private val dateTimeFormatter = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
}

fun formatShortTime(epochSeconds: Long): String =
    shortTimeFormatter.get()?.format(Date(epochSeconds * 1000L)).orEmpty()

fun formatShortDateTime(epochSeconds: Long): String =
    dateTimeFormatter.get()?.format(Date(epochSeconds * 1000L)).orEmpty()

@Composable
fun TypingDotsWave(
    color: Color = BravPurple,
    dotSize: Dp = 7.dp,
    spacing: Dp = 4.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "typing_dots_wave")
    val offsets = listOf(0, 160, 320)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        offsets.forEachIndexed { idx, delayMs ->
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(delayMs)
                ),
                label = "typing_dot_$idx"
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        translationY = -progress * dotSize.toPx() * 0.55f
                        val scale = 0.84f + (progress * 0.28f)
                        scaleX = scale
                        scaleY = scale
                    }
                    .alpha(0.45f + progress * 0.55f)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun PeerTypingIndicatorBubble(
    senderName: String = "",
    avatarUrl: String = "",
    isGroup: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = 4.dp,
        bottomEnd = 18.dp
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("peer_typing_indicator_bubble"),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (avatarUrl.isNotBlank() || senderName.isNotBlank()) {
            UserAvatarWithStatus(
                name = senderName.ifBlank { "B" },
                avatarUrl = avatarUrl,
                isOnlineRealtime = true,
                size = 28.dp
            )
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            shape = bubbleShape,
            color = BravTheme.colors.bubbleIncoming,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (isGroup && senderName.isNotBlank()) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = BravPurple,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TypingDotsWave(
                    color = BravPurple,
                    dotSize = 7.dp,
                    spacing = 5.dp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun VerifiedBlueBadge(
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
    contentDescriptionText: String = "Compte certifié CERTIFICATION",
    showTooltipOnTap: Boolean = false,
    verifiedOrgName: String = "",
    verificationCategory: String = ""
) {
    var showInfoDialog by remember { mutableStateOf(false) }
    val baseModifier = modifier
        .size(size)
        .semantics { contentDescription = contentDescriptionText }
        .testTag("verified_blue_badge")
        .then(if (showTooltipOnTap) Modifier.clickable { showInfoDialog = true } else Modifier)

    androidx.compose.foundation.Canvas(modifier = baseModifier) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val outerR = this.size.minDimension * 0.49f
        val innerR = this.size.minDimension * 0.40f
        val lobes = 10
        val steps = lobes * 4
        val sealPath = Path()
        for (i in 0..steps) {
            val angle = (2.0 * PI * i) / steps
            val wave = cos(lobes * angle)
            val r = innerR + ((outerR - innerR) * ((wave + 1.0) * 0.5)).toFloat()
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) sealPath.moveTo(x, y) else sealPath.lineTo(x, y)
        }
        sealPath.close()
        drawPath(path = sealPath, color = Color(0xFF1D9BF0))

        val checkPath = Path().apply {
            moveTo(cx - this@Canvas.size.minDimension * 0.21f, cy + this@Canvas.size.minDimension * 0.01f)
            lineTo(cx - this@Canvas.size.minDimension * 0.05f, cy + this@Canvas.size.minDimension * 0.17f)
            lineTo(cx + this@Canvas.size.minDimension * 0.23f, cy - this@Canvas.size.minDimension * 0.15f)
        }
        drawPath(
            path = checkPath,
            color = Color.White,
            style = Stroke(
                width = (this.size.minDimension * 0.135f).coerceAtLeast(1.4f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }

    if (showInfoDialog) {
        val lux = BravTheme.colors
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = lux.modalSurface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VerifiedBlueBadge(size = 20.dp)
                    Text("Compte CERTIFICATION", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Ce compte a fait l'objet d'une vérification administrative officielle de son identité ou de son organisation sur BRAV CHAT."
                    )
                    if (verificationCategory.isNotBlank()) {
                        Text(
                            "Catégorie : $verificationCategory",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0B69C7)
                        )
                    }
                    if (verifiedOrgName.isNotBlank()) {
                        Text(
                            "Entité confirmée : $verifiedOrgName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Compris") }
            }
        )
    }
}

@Composable
fun VerificationStatusPill(
    status: VerificationStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, fgColor) = when (status) {
        VerificationStatus.VERIFIED, VerificationStatus.PAYMENT_CONFIRMED -> Color(0xFF1D9BF0).copy(alpha = 0.14f) to Color(0xFF0B69C7)
        VerificationStatus.ELIGIBLE -> Color(0xFF0D9488).copy(alpha = 0.15f) to Color(0xFF0F766E)
        VerificationStatus.AWAITING_SELFIE, VerificationStatus.AWAITING_PAYMENT, VerificationStatus.PENDING -> Color(0xFFF59E0B).copy(alpha = 0.16f) to Color(0xFFB45309)
        VerificationStatus.IN_REVIEW -> Color(0xFF6366F1).copy(alpha = 0.15f) to Color(0xFF4338CA)
        VerificationStatus.INFO_REQUIRED, VerificationStatus.RETAKE_REQUIRED -> Color(0xFFEA580C).copy(alpha = 0.15f) to Color(0xFFC2410C)
        VerificationStatus.REJECTED, VerificationStatus.REVOKED, VerificationStatus.NOT_ELIGIBLE -> Color(0xFFDC2626).copy(alpha = 0.13f) to Color(0xFFB91C1C)
        VerificationStatus.SUSPENDED -> Color(0xFFD97706).copy(alpha = 0.15f) to Color(0xFF92400E)
        VerificationStatus.EXPIRED, VerificationStatus.NONE -> Color(0xFF64748B).copy(alpha = 0.14f) to Color(0xFF475569)
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (status == VerificationStatus.VERIFIED) {
                VerifiedBlueBadge(size = 13.dp)
            }
            Text(
                text = status.labelFr,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = fgColor
            )
        }
    }
}

