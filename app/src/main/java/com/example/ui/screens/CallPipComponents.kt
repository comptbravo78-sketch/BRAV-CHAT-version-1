package com.example.ui.screens

import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.media.WebRtcSessionState
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.ui.theme.StatusOnlineGreen
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class PipCorner {
    TOP_START, TOP_END, BOTTOM_START, BOTTOM_END
}

@Composable
internal fun CallPeerPhotoAvatar(
    name: String,
    avatarUrl: String,
    size: Dp,
    ringAnimated: Boolean = false,
    isLightSurface: Boolean = true,
    modifier: Modifier = Modifier
) {
    val lux = BravTheme.colors
    val borderCol = if (isLightSurface) lux.champagneGold.copy(alpha = 0.72f) else lux.champagneGold.copy(alpha = 0.48f)
    val pulseColor = BravPurple.copy(alpha = if (isLightSurface) 0.16f else 0.28f)

    val pulse = rememberInfiniteTransition(label = "call_avatar_pulse")
    val ringScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "call_ring_scale"
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "call_ring_alpha"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (ringAnimated) {
            Box(
                modifier = Modifier
                    .size(size * 1.18f)
                    .scale(ringScale)
                    .alpha(ringAlpha)
                    .clip(CircleShape)
                    .background(pulseColor)
            )
        }
        Surface(
            shape = CircleShape,
            color = if (isLightSurface) lux.translucentCardSurface else Color(0xFF1E293B),
            shadowElevation = if (isLightSurface) 3.dp else 2.dp,
            modifier = Modifier
                .size(size)
                .border(2.5.dp, borderCol, CircleShape)
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Photo de profil de $name",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
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
                        .background(Brush.linearGradient(listOf(BravPurple, Color(0xFF7C1C80)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontSize = (size.value * 0.34f).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
internal fun VideoStagePlaceholder(
    displayName: String,
    avatarUrl: String,
    statusText: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A2234), Color(0xFF0A0F1A)),
                    radius = 900f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val animateRing = statusText.contains("Sonnerie", ignoreCase = true) ||
            statusText.contains("Appel", ignoreCase = true) ||
            statusText.contains("Connexion", ignoreCase = true)
        CallPeerPhotoAvatar(
            name = displayName,
            avatarUrl = avatarUrl,
            size = 118.dp,
            ringAnimated = animateRing,
            isLightSurface = false
        )
    }
}

@Composable
internal fun CallVideoPipFrame(
    maxWidth: Dp,
    maxHeight: Dp,
    localRenderer: SurfaceViewRenderer,
    session: WebRtcSessionState,
    peerName: String,
    onSwapFeeds: () -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val containerWpx = with(density) { maxWidth.toPx() }
    val containerHpx = with(density) { maxHeight.toPx() }
    val marginPx = with(density) { 14.dp.toPx() }
    val topSafePx = with(density) { 96.dp.toPx() }
    val bottomSafePx = with(density) { 116.dp.toPx() }

    val aspectRatio = 1.38f
    val minPipWidthDp = 86f
    val standardPipWidthDp = 108f
    val maxWidthByW = (maxWidth.value * 0.52f).coerceIn(146f, 200f)
    val maxWidthByH = ((maxHeight.value - 230f) / aspectRatio).coerceAtLeast(104f)
    val maxPipWidthDp = minOf(maxWidthByW, maxWidthByH).coerceAtLeast(minPipWidthDp + 20f)

    val widthDpAnim = remember { Animatable(standardPipWidthDp) }
    var currentCorner by remember { mutableStateOf(PipCorner.TOP_END) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var initializedPos by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isResizing by remember { mutableStateOf(false) }

    fun cornerOffsetFor(corner: PipCorner, frameWpx: Float, frameHpx: Float): Pair<Float, Float> {
        val minX = marginPx
        val maxX = (containerWpx - frameWpx - marginPx).coerceAtLeast(minX)
        val minY = topSafePx.coerceAtMost((containerHpx - frameHpx - marginPx).coerceAtLeast(marginPx))
        val maxY = (containerHpx - frameHpx - bottomSafePx).coerceAtLeast(minY)
        return when (corner) {
            PipCorner.TOP_START -> minX to minY
            PipCorner.TOP_END -> maxX to minY
            PipCorner.BOTTOM_START -> minX to maxY
            PipCorner.BOTTOM_END -> maxX to maxY
        }
    }

    fun nearestCornerFor(centerX: Float, centerY: Float): PipCorner {
        val onRight = centerX >= containerWpx * 0.5f
        val onBottom = centerY >= containerHpx * 0.5f
        return when {
            !onRight && !onBottom -> PipCorner.TOP_START
            onRight && !onBottom -> PipCorner.TOP_END
            !onRight && onBottom -> PipCorner.BOTTOM_START
            else -> PipCorner.BOTTOM_END
        }
    }

    fun animateToSizePreset(targetDp: Float) {
        val clampedDp = targetDp.coerceIn(minPipWidthDp, maxPipWidthDp)
        coroutineScope.launch {
            isResizing = true
            try {
                val endWpx = with(density) { clampedDp.dp.toPx() }
                val endHpx = endWpx * aspectRatio
                val (tx, ty) = cornerOffsetFor(currentCorner, endWpx, endHpx)
                val animSpec = spring<Float>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
                val wJob = launch { widthDpAnim.animateTo(clampedDp, animSpec) }
                val xJob = launch { offsetX.animateTo(tx, animSpec) }
                val yJob = launch { offsetY.animateTo(ty, animSpec) }
                wJob.join()
                xJob.join()
                yJob.join()
            } finally {
                isResizing = false
            }
        }
    }

    fun snapToNearestCorner(vx: Float = 0f, vy: Float = 0f) {
        val curWpx = with(density) { widthDpAnim.value.dp.toPx() }
        val curHpx = curWpx * aspectRatio
        val projectedCenterX = offsetX.value + curWpx * 0.5f + vx * 0.11f
        val projectedCenterY = offsetY.value + curHpx * 0.5f + vy * 0.11f
        val chosen = nearestCornerFor(projectedCenterX, projectedCenterY)
        currentCorner = chosen
        val (tx, ty) = cornerOffsetFor(chosen, curWpx, curHpx)
        coroutineScope.launch {
            val animSpec = spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
            launch { offsetX.animateTo(tx, animSpec) }
            launch { offsetY.animateTo(ty, animSpec) }
        }
    }

    LaunchedEffect(containerWpx, containerHpx) {
        if (containerWpx > 0f && containerHpx > 0f && !isDragging && !isResizing) {
            val clampedW = widthDpAnim.value.coerceIn(minPipWidthDp, maxPipWidthDp)
            widthDpAnim.snapTo(clampedW)
            val wPx = with(density) { clampedW.dp.toPx() }
            val hPx = wPx * aspectRatio
            val (tx, ty) = cornerOffsetFor(currentCorner, wPx, hPx)
            offsetX.snapTo(tx)
            offsetY.snapTo(ty)
            initializedPos = true
        }
    }

    val curWidthDpValue = widthDpAnim.value.coerceIn(minPipWidthDp, maxPipWidthDp)
    val curWidthDp = curWidthDpValue.dp
    val curHeightDp = (curWidthDpValue * aspectRatio).dp
    val cornerRadiusDp = 18.dp
    val cornerRadiusPx = with(density) { cornerRadiusDp.toPx() }
    val isMiniMode = curWidthDpValue < 98f

    val pipShowsLiveVideo = if (session.isVideoSwapped) session.hasRemoteVideo else session.isCameraEnabled

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .size(width = curWidthDp, height = curHeightDp)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(cornerRadiusDp))
            .clip(RoundedCornerShape(cornerRadiusDp))
            .background(Color(0xFF1E293B))
            .border(
                width = if (isDragging || isResizing) 2.dp else 1.25.dp,
                color = if (isDragging || isResizing) BravPurple else Color.White.copy(alpha = 0.28f),
                shape = RoundedCornerShape(cornerRadiusDp)
            )
            .testTag("pip_camera_frame")
            .pointerInput(containerWpx, containerHpx, maxPipWidthDp) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    val touchSlop = viewConfiguration.touchSlop
                    var totalPan = Offset.Zero
                    var startedDrag = false
                    var usedPinch = false

                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            usedPinch = true
                            isResizing = true
                            val zoom = event.calculateZoom()
                            if (abs(zoom - 1f) > 0.004f) {
                                val nextW = (widthDpAnim.value * zoom).coerceIn(minPipWidthDp, maxPipWidthDp)
                                val nextWpx = with(density) { nextW.dp.toPx() }
                                val nextHpx = nextWpx * aspectRatio
                                val (cx, cy) = cornerOffsetFor(currentCorner, nextWpx, nextHpx)
                                coroutineScope.launch {
                                    widthDpAnim.snapTo(nextW)
                                    offsetX.snapTo(cx)
                                    offsetY.snapTo(cy)
                                }
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (pressedCount == 1 && !usedPinch) {
                            val change = event.changes.first()
                            if (!change.isConsumed) {
                                val pan = event.calculatePan()
                                totalPan += pan
                                if (!startedDrag && hypot(totalPan.x, totalPan.y) > touchSlop) {
                                    startedDrag = true
                                    isDragging = true
                                }
                                if (startedDrag) {
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val curWpx = with(density) { widthDpAnim.value.dp.toPx() }
                                    val curHpx = curWpx * aspectRatio
                                    val minX = marginPx
                                    val maxX = (containerWpx - curWpx - marginPx).coerceAtLeast(minX)
                                    val minY = marginPx
                                    val maxY = (containerHpx - curHpx - bottomSafePx + with(density) { 12.dp.toPx() }).coerceAtLeast(minY)
                                    val nx = (offsetX.value + pan.x).coerceIn(minX, maxX)
                                    val ny = (offsetY.value + pan.y).coerceIn(minY, maxY)
                                    coroutineScope.launch {
                                        offsetX.snapTo(nx)
                                        offsetY.snapTo(ny)
                                    }
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (startedDrag) {
                        val vel = velocityTracker.calculateVelocity()
                        isDragging = false
                        snapToNearestCorner(vel.x, vel.y)
                    } else if (usedPinch) {
                        isResizing = false
                        snapToNearestCorner()
                    }
                }
            }
            .pointerInput(maxPipWidthDp, onSwapFeeds) {
                detectTapGestures(
                    onTap = { onSwapFeeds() },
                    onDoubleTap = {
                        val next = when {
                            widthDpAnim.value < standardPipWidthDp - 10f -> standardPipWidthDp
                            widthDpAnim.value < maxPipWidthDp - 14f -> maxPipWidthDp
                            else -> minPipWidthDp
                        }
                        animateToSizePreset(next)
                    }
                )
            }
    ) {
        AndroidView(
            factory = {
                localRenderer.apply {
                    setZOrderMediaOverlay(true)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    clipToOutline = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.visibility = if (pipShowsLiveVideo) View.VISIBLE else View.INVISIBLE
                view.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, outline: Outline) {
                        outline.setRoundRect(0, 0, v.width, v.height, cornerRadiusPx)
                    }
                }
                view.clipToOutline = true
                view.invalidateOutline()
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!pipShowsLiveVideo) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF161E2E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.VideocamOff,
                    contentDescription = "Caméra désactivée",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(if (isMiniMode) 20.dp else 26.dp)
                )
            }
        }
    }
}
