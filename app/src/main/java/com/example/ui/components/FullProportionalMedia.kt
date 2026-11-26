package com.example.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.media.BravVideoPlayerController
import com.example.media.LocalBravVideoController
import com.example.media.VideoTrimProcessor
import com.example.network.WorkerApiClient
import com.example.ui.theme.BravChampagneGold
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import kotlin.math.abs

/**
 * Displays a single photo completely uncropped (ContentScale.Fit) with its true proportions.
 */
@Composable
fun BravProportionalPhoto(
    model: Any?,
    contentDescription: String,
    knownAspectRatio: Float = 0f,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    testTagName: String = ""
) {
    val context = LocalContext.current
    val lux = BravTheme.colors
    var detectedRatio by remember(model, knownAspectRatio) {
        mutableFloatStateOf(
            if (knownAspectRatio > 0.05f) knownAspectRatio.coerceIn(0.45f, 2.4f) else 0f
        )
    }

    val boxRatio = if (detectedRatio > 0.05f) detectedRatio else 1f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(boxRatio)
            .background(lux.translucentSubtleSurface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (testTagName.isNotBlank()) Modifier.testTag(testTagName) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val intrinsic = state.painter.intrinsicSize
                if (intrinsic.width > 0f && intrinsic.height > 0f) {
                    val ratio = (intrinsic.width / intrinsic.height).coerceIn(0.45f, 2.4f)
                    if (abs(ratio - detectedRatio) > 0.01f) {
                        detectedRatio = ratio
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Fullscreen photo viewer supporting pinch-to-zoom, pan when zoomed, and double-tap zoom toggle,
 * without blocking HorizontalPager swiping when unzoomed.
 */
@Composable
fun BravZoomablePhotoPage(
    model: Any?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    testTagName: String = ""
) {
    val context = LocalContext.current
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(candidate: Offset, currentScale: Float): Offset {
        if (currentScale <= 1.01f || containerSize.width <= 0 || containerSize.height <= 0) {
            return Offset.Zero
        }
        val maxX = ((currentScale - 1f) * containerSize.width) / 2f
        val maxY = ((currentScale - 1f) * containerSize.height) / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(model) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.15f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.4f
                        }
                    }
                )
            }
            .pointerInput(model) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val pointerCount = event.changes.count { it.pressed }
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                            if (pointerCount > 1 || scale > 1.02f || abs(zoomChange - 1f) > 0.01f) {
                                val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
                                val nextOffset = if (nextScale > 1.02f) {
                                    clampOffset(offset + panChange, nextScale)
                                } else {
                                    Offset.Zero
                                }
                                scale = nextScale
                                offset = nextOffset
                                if (pointerCount > 1 || scale > 1.02f || centroidSize > 0f) {
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) change.consume()
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .then(if (testTagName.isNotBlank()) Modifier.testTag(testTagName) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

/**
 * Shared proportional Media3 video surface connected to [BravVideoPlayerController].
 * Uses RESIZE_MODE_FIT so original video proportions (9:16, 16:9, 1:1, 4:3...) are always shown in full.
 */
@OptIn(UnstableApi::class)
@Composable
fun BravProportionalVideoSurface(
    slotKey: String,
    videoUrl: String,
    thumbnailUrl: String = "",
    knownAspectRatio: Float = 0f,
    durationMs: Long = 0L,
    shouldAutoPlay: Boolean = false,
    allowAutoPlay: Boolean = true,
    isMuted: Boolean = false,
    onToggleMute: ((Boolean) -> Unit)? = null,
    onOpenFullscreen: (() -> Unit)? = null,
    onPlaybackStarted: (() -> Unit)? = null,
    workerApiClient: WorkerApiClient? = null,
    hairlineColor: Color = BravTheme.colors.hairline,
    showOverlayChrome: Boolean = true,
    useNativeController: Boolean = false,
    fillMaxContainer: Boolean = false,
    modifier: Modifier = Modifier,
    testTagName: String = ""
) {
    val context = LocalContext.current
    val lux = BravTheme.colors
    val inheritedController = LocalBravVideoController.current
    val fallbackController = remember(inheritedController, workerApiClient) {
        if (inheritedController == null && workerApiClient != null) {
            BravVideoPlayerController(context, workerApiClient)
        } else {
            null
        }
    }
    DisposableEffect(fallbackController) {
        onDispose { fallbackController?.release() }
    }
    val controller = inheritedController ?: fallbackController

    val playbackState by if (controller != null) {
        controller.state.collectAsStateWithLifecycle()
    } else {
        remember { mutableStateOf(com.example.media.BravVideoPlaybackState()) }
    }

    val isActiveSlot = controller != null &&
        playbackState.activeSlotKey == slotKey &&
        playbackState.activeUrl == videoUrl
    val isPlayingHere = isActiveSlot && playbackState.isPlaying

    var notifiedPlay by remember(slotKey, videoUrl) { mutableStateOf(false) }
    LaunchedEffect(isPlayingHere) {
        if (isPlayingHere && !notifiedPlay) {
            notifiedPlay = true
            onPlaybackStarted?.invoke()
        }
    }

    // Drive autoplay when visibility/preference grants this slot active playback
    LaunchedEffect(controller, slotKey, videoUrl, shouldAutoPlay, allowAutoPlay) {
        if (controller == null || videoUrl.isBlank()) return@LaunchedEffect
        if (shouldAutoPlay && allowAutoPlay) {
            controller.playSlot(
                slotKey = slotKey,
                url = videoUrl,
                loop = true,
                autoPlay = true,
                muted = isMuted
            )
        } else if (playbackState.activeSlotKey == slotKey && playbackState.isPlaying) {
            controller.pauseSlot(slotKey)
        }
    }

    val liveRatio = if (isActiveSlot && playbackState.videoAspectRatio > 0.05f) {
        playbackState.videoAspectRatio
    } else {
        0f
    }
    val displayRatio = when {
        liveRatio > 0.05f -> liveRatio
        knownAspectRatio > 0.05f -> knownAspectRatio
        else -> 16f / 9f
    }.coerceIn(0.45f, 2.4f)

    val shape = if (fillMaxContainer) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp)
    val darkOverlayGlass = Color(0xA6140C24)

    Box(
        modifier = modifier
            .then(
                if (fillMaxContainer) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(displayRatio)
                        .clip(shape)
                        .border(0.75.dp, hairlineColor, shape)
                }
            )
            .background(Color(0xFF0E0819))
            .then(if (testTagName.isNotBlank()) Modifier.testTag(testTagName) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (isActiveSlot && controller != null) {
            val exoPlayer = remember(controller) { controller.getOrCreatePlayer() }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = useNativeController
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) view.player = exoPlayer
                    view.useController = useNativeController
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Poster thumbnail shown while idle or before playback starts
        if (!isPlayingHere && thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Aperçu vidéo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Tap surface when native controller is off
        if (!useNativeController) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = controller != null && videoUrl.isNotBlank()) {
                        val c = controller ?: return@clickable
                        if (isActiveSlot) {
                            c.togglePlayPause(slotKey)
                        } else {
                            c.playSlot(
                                slotKey = slotKey,
                                url = videoUrl,
                                loop = true,
                                autoPlay = true,
                                muted = isMuted
                            )
                        }
                    }
            )
        }

        // Buffering or network error overlays
        if (isActiveSlot && playbackState.errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = darkOverlayGlass,
                border = BorderStroke(0.75.dp, BravChampagneGold.copy(alpha = 0.55f)),
                onClick = { controller?.retryCurrent() },
                modifier = Modifier
                    .padding(16.dp)
                    .testTag("${testTagName.ifBlank { "video" }}_retry")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Réessayer",
                        tint = BravChampagneGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = playbackState.errorMessage ?: "Lecture impossible",
                        style = MaterialTheme.typography.labelMedium,
                        color = BravWhite,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Appuyez pour réessayer",
                        style = MaterialTheme.typography.labelSmall,
                        color = BravChampagneGold,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else if (isActiveSlot && playbackState.isBuffering && !isPlayingHere) {
            Surface(
                shape = CircleShape,
                color = darkOverlayGlass,
                border = BorderStroke(0.75.dp, BravChampagneGold.copy(alpha = 0.45f))
            ) {
                CircularProgressIndicator(
                    color = BravChampagneGold,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(26.dp)
                )
            }
        } else if (!isPlayingHere && !useNativeController) {
            Surface(
                shape = CircleShape,
                color = darkOverlayGlass,
                border = BorderStroke(1.dp, BravChampagneGold.copy(alpha = 0.65f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Lire la vidéo",
                    tint = BravWhite,
                    modifier = Modifier
                        .padding(14.dp)
                        .size(30.dp)
                )
            }
        }

        if (showOverlayChrome) {
            // Top-start label (duration + Vidéo badge) using translucent African dark surface
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = darkOverlayGlass,
                border = BorderStroke(0.65.dp, BravChampagneGold.copy(alpha = 0.45f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isPlayingHere) Icons.Rounded.Pause else Icons.Rounded.Videocam,
                        contentDescription = null,
                        tint = BravChampagneGold,
                        modifier = Modifier.size(13.dp)
                    )
                    val totalMs = if (isActiveSlot && playbackState.durationMs > 0L) {
                        playbackState.durationMs
                    } else {
                        durationMs
                    }
                    Text(
                        text = if (totalMs > 0L) VideoTrimProcessor.formatDurationMmSs(totalMs) else "Vidéo",
                        color = BravWhite,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Top-end mute & fullscreen controls (48dp touch targets)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (controller != null) {
                    val effectiveMuted = if (isActiveSlot) playbackState.isMuted else isMuted
                    Surface(
                        shape = CircleShape,
                        color = darkOverlayGlass,
                        border = BorderStroke(0.65.dp, BravChampagneGold.copy(alpha = 0.4f))
                    ) {
                        IconButton(
                            onClick = {
                                val next = controller.toggleMute()
                                onToggleMute?.invoke(next)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("${testTagName.ifBlank { "video" }}_mute_btn")
                        ) {
                            Icon(
                                imageVector = if (effectiveMuted) {
                                    Icons.AutoMirrored.Rounded.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Rounded.VolumeUp
                                },
                                contentDescription = if (effectiveMuted) "Activer le son" else "Couper le son",
                                tint = BravWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (onOpenFullscreen != null) {
                    Surface(
                        shape = CircleShape,
                        color = darkOverlayGlass,
                        border = BorderStroke(0.65.dp, BravChampagneGold.copy(alpha = 0.4f))
                    ) {
                        IconButton(
                            onClick = onOpenFullscreen,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("${testTagName.ifBlank { "video" }}_fullscreen_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fullscreen,
                                contentDescription = "Plein écran",
                                tint = BravWhite,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
