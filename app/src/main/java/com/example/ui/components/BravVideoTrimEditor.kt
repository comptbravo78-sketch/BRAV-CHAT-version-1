package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.media.VideoMetadata
import com.example.media.VideoTrimProcessor
import com.example.ui.theme.BravChampagneGold
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravWhite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun BravVideoTrimEditorDialog(
    sourceUri: Uri,
    metadata: VideoMetadata,
    initialStartMs: Long = 0L,
    initialEndMs: Long = metadata.durationMs,
    onConfirmTrimmed: (
        trimmedFile: File,
        startMs: Long,
        endMs: Long,
        durationMs: Long,
        aspectRatio: Float,
        width: Int,
        height: Int
    ) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val totalDurationMs = metadata.durationMs.coerceAtLeast(VideoTrimProcessor.MIN_TRIM_DURATION_MS)
    val initialRange = remember(initialStartMs, initialEndMs, totalDurationMs) {
        VideoTrimProcessor.validateTrimRange(initialStartMs, initialEndMs, totalDurationMs)
    }

    var startMs by remember(sourceUri) { mutableLongStateOf(initialRange.startMs) }
    var endMs by remember(sourceUri) { mutableLongStateOf(initialRange.endMs) }
    var currentPosMs by remember(sourceUri) { mutableLongStateOf(initialRange.startMs) }
    var isPlaying by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var frames by remember(sourceUri) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var exportJob by remember { mutableStateOf<Job?>(null) }

    val exoPlayer = remember(sourceUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(sourceUri))
            prepare()
            seekTo(initialRange.startMs)
            playWhenReady = false
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exportJob?.cancel()
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Load filmstrip thumbnails asynchronously
    LaunchedEffect(sourceUri) {
        frames = VideoTrimProcessor.extractTimelineFrames(context, sourceUri, count = 8, thumbHeightPx = 112)
    }

    // Keep preview playback constrained inside [startMs, endMs]
    LaunchedEffect(exoPlayer, startMs, endMs) {
        while (isActive) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            currentPosMs = pos
            if (exoPlayer.isPlaying && pos >= endMs) {
                exoPlayer.seekTo(startMs)
            }
            delay(80L)
        }
    }

    val selectedDurationMs = (endMs - startMs).coerceAtLeast(0L)
    val editorBg = Color(0xFF110920)
    val glassCard = Color(0xCC1E1233)
    val borderGold = BravChampagneGold.copy(alpha = 0.45f)

    Dialog(
        onDismissRequest = {
            if (!isExporting) onCancel()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = editorBg,
            modifier = Modifier
                .fillMaxSize()
                .testTag("video_trim_editor_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        enabled = !isExporting,
                        onClick = onCancel,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("trim_editor_cancel_icon")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Annuler", tint = BravWhite)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ContentCut,
                                contentDescription = null,
                                tint = BravChampagneGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Découper la vidéo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = BravWhite
                            )
                        }
                        Text(
                            text = "Durée sélectionnée : ${VideoTrimProcessor.formatDurationMmSs(selectedDurationMs)} / ${VideoTrimProcessor.formatDurationMmSs(totalDurationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = BravChampagneGold,
                            modifier = Modifier.testTag("trim_duration_label")
                        )
                    }
                    IconButton(
                        enabled = !isExporting,
                        onClick = {
                            startMs = 0L
                            endMs = totalDurationMs
                            errorMsg = null
                            exoPlayer.seekTo(0L)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("trim_editor_reset_btn")
                    ) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            contentDescription = "Réinitialiser la découpe",
                            tint = BravWhite
                        )
                    }
                }

                // Video Preview Viewport (Uncropped RESIZE_MODE_FIT)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black)
                        .border(0.85.dp, borderGold, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        shape = CircleShape,
                        color = glassCard,
                        border = BorderStroke(1.dp, borderGold),
                        onClick = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                if (exoPlayer.currentPosition < startMs || exoPlayer.currentPosition >= endMs) {
                                    exoPlayer.seekTo(startMs)
                                }
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("trim_editor_play_pause_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Lire",
                                tint = BravWhite,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                // Timeline + Draggable Trim Handles
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = glassCard,
                    border = BorderStroke(0.75.dp, borderGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Début : ${VideoTrimProcessor.formatDurationMmSs(startMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = BravWhite.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "Fin : ${VideoTrimProcessor.formatDurationMmSs(endMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = BravWhite.copy(alpha = 0.85f)
                            )
                        }

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .testTag("trim_timeline_bar")
                        ) {
                            val totalPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                            val handleSizePx = with(density) { 48.dp.toPx() }
                            val startFrac = (startMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                            val endFrac = (endMs.toFloat() / totalDurationMs.toFloat()).coerceIn(startFrac, 1f)
                            val posFrac = (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)

                            // Thumbnail filmstrip
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .align(Alignment.Center)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1F1334))
                            ) {
                                if (frames.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Aperçu chronologique...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BravWhite.copy(alpha = 0.6f)
                                        )
                                    }
                                } else {
                                    frames.forEach { bmp ->
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        )
                                    }
                                }
                            }

                            // Dimmed excluded left region
                            if (startFrac > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(startFrac)
                                        .height(52.dp)
                                        .align(Alignment.CenterStart)
                                        .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                                        .background(Color.Black.copy(alpha = 0.65f))
                                )
                            }

                            // Dimmed excluded right region
                            if (endFrac < 1f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(1f - endFrac)
                                        .height(52.dp)
                                        .align(Alignment.CenterEnd)
                                        .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                                        .background(Color.Black.copy(alpha = 0.65f))
                                )
                            }

                            // Active gold window border
                            val windowLeftPx = (startFrac * totalPx).roundToInt()
                            val windowWidthDp = with(density) { ((endFrac - startFrac) * totalPx).coerceAtLeast(8f).toDp() }
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(windowLeftPx, 0) }
                                    .width(windowWidthDp)
                                    .height(52.dp)
                                    .align(Alignment.CenterStart)
                                    .border(2.dp, BravChampagneGold, RoundedCornerShape(6.dp))
                            )

                            // Playhead line
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset((posFrac * totalPx).roundToInt(), 0) }
                                    .width(2.dp)
                                    .height(56.dp)
                                    .align(Alignment.CenterStart)
                                    .background(BravWhite)
                            )

                            // Left drag handle (48dp touch target)
                            val startOffsetPx = ((startFrac * totalPx) - handleSizePx / 2f)
                                .roundToInt()
                                .coerceIn(-12, (totalPx - handleSizePx / 2f).roundToInt())
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(startOffsetPx, 0) }
                                    .size(48.dp)
                                    .align(Alignment.CenterStart)
                                    .pointerInput(totalDurationMs, totalPx) {
                                        detectHorizontalDragGestures { _, dragAmount ->
                                            if (isExporting) return@detectHorizontalDragGestures
                                            val deltaMs = ((dragAmount / totalPx) * totalDurationMs).toLong()
                                            val next = VideoTrimProcessor.validateTrimRange(
                                                startMs = startMs + deltaMs,
                                                endMs = endMs,
                                                totalDurationMs = totalDurationMs
                                            )
                                            startMs = next.startMs
                                            endMs = next.endMs
                                            exoPlayer.seekTo(startMs)
                                        }
                                    }
                                    .testTag("trim_start_handle"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BravChampagneGold,
                                    modifier = Modifier
                                        .width(12.dp)
                                        .height(54.dp)
                                ) {}
                            }

                            // Right drag handle (48dp touch target)
                            val endOffsetPx = ((endFrac * totalPx) - handleSizePx / 2f)
                                .roundToInt()
                                .coerceIn(0, (totalPx - handleSizePx / 2f + 12f).roundToInt())
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(endOffsetPx, 0) }
                                    .size(48.dp)
                                    .align(Alignment.CenterStart)
                                    .pointerInput(totalDurationMs, totalPx) {
                                        detectHorizontalDragGestures { _, dragAmount ->
                                            if (isExporting) return@detectHorizontalDragGestures
                                            val deltaMs = ((dragAmount / totalPx) * totalDurationMs).toLong()
                                            val next = VideoTrimProcessor.validateTrimRange(
                                                startMs = startMs,
                                                endMs = endMs + deltaMs,
                                                totalDurationMs = totalDurationMs
                                            )
                                            startMs = next.startMs
                                            endMs = next.endMs
                                            exoPlayer.seekTo((endMs - 400L).coerceAtLeast(startMs))
                                        }
                                    }
                                    .testTag("trim_end_handle"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BravChampagneGold,
                                    modifier = Modifier
                                        .width(12.dp)
                                        .height(54.dp)
                                ) {}
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                enabled = !isExporting,
                                onClick = {
                                    exoPlayer.seekTo(startMs)
                                    exoPlayer.play()
                                },
                                border = BorderStroke(0.85.dp, borderGold),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("trim_preview_segment_btn")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = BravChampagneGold, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Prévisualiser l'extrait", color = BravWhite, maxLines = 1)
                            }
                        }
                    }
                }

                if (isExporting) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { exportProgress.coerceIn(0f, 1f) },
                            color = BravChampagneGold,
                            trackColor = BravWhite.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .testTag("trim_export_progress")
                        )
                        Text(
                            text = "Découpe en cours (${(exportProgress * 100).roundToInt()}%)...",
                            style = MaterialTheme.typography.labelSmall,
                            color = BravChampagneGold
                        )
                    }
                }

                errorMsg?.let { err ->
                    Text(
                        text = err,
                        color = Color(0xFFFF8A80),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("trim_error_text")
                    )
                }

                // Footer actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        enabled = !isExporting,
                        onClick = onCancel,
                        border = BorderStroke(0.85.dp, BravWhite.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("trim_cancel_button")
                    ) {
                        Text("Annuler", color = BravWhite)
                    }
                    Button(
                        enabled = !isExporting && selectedDurationMs >= VideoTrimProcessor.MIN_TRIM_DURATION_MS,
                        onClick = {
                            exoPlayer.pause()
                            isExporting = true
                            exportProgress = 0f
                            errorMsg = null
                            exportJob = scope.launch {
                                val isEssentiallyFull = startMs <= 100L && endMs >= totalDurationMs - 200L
                                val res = if (isEssentiallyFull) {
                                    VideoTrimProcessor.copyUriToTempFile(context, sourceUri, prefix = "video_orig")
                                } else {
                                    VideoTrimProcessor.trimVideoLocally(
                                        context = context,
                                        sourceUri = sourceUri,
                                        startMs = startMs,
                                        endMs = endMs,
                                        totalDurationMs = totalDurationMs,
                                        onProgress = { p -> exportProgress = p }
                                    )
                                }
                                isExporting = false
                                res.onSuccess { outFile ->
                                    val inspected = VideoTrimProcessor.inspectVideo(context, Uri.fromFile(outFile))
                                    val finalRatio = if (inspected.aspectRatio > 0.05f) inspected.aspectRatio else metadata.aspectRatio
                                    val finalW = if (inspected.effectiveWidth > 0) inspected.effectiveWidth else metadata.effectiveWidth
                                    val finalH = if (inspected.effectiveHeight > 0) inspected.effectiveHeight else metadata.effectiveHeight
                                    val finalDur = if (inspected.durationMs > 0L) inspected.durationMs else selectedDurationMs
                                    onConfirmTrimmed(outFile, startMs, endMs, finalDur, finalRatio, finalW, finalH)
                                }.onFailure { err ->
                                    errorMsg = err.localizedMessage ?: "Échec de la découpe vidéo"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BravPurplePrimary,
                            contentColor = BravWhite
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("trim_confirm_button")
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Valider la découpe", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}
