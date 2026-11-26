package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.media.VoicePlaybackState
import com.example.model.BravReactionType
import com.example.model.Message
import com.example.ui.components.BravStickerArtwork
import com.example.ui.components.BravStickerCatalog
import com.example.ui.components.MessageReactionsRow
import com.example.ui.components.VoiceMessageBubble
import com.example.ui.components.YouTubePlayerController
import com.example.ui.components.formatShortTime
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravPurpleDeep
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageBubble(
    message: Message,
    displayText: String,
    translatedText: String?,
    isMine: Boolean,
    myUid: String,
    senderDisplayName: String,
    isGroup: Boolean,
    isSelected: Boolean = false,
    playbackState: VoicePlaybackState,
    onTapOverride: (() -> Unit)? = null,
    onLongPress: () -> Unit,
    onToggleReaction: (BravReactionType) -> Unit,
    onOpenMedia: (url: String, isVideo: Boolean, title: String) -> Unit,
    onDownloadMedia: ((Message) -> Unit)? = null,
    longPressDownloadDurationMs: Long = 5000L,
    onOpenDocument: (Message) -> Unit = {},
    onOpenYouTube: (url: String, title: String) -> Unit = { _, _ -> },
    onToggleVoicePlay: () -> Unit,
    onCycleVoiceSpeed: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val bubbleShape = RoundedCornerShape(
        topStart = 17.dp,
        topEnd = 17.dp,
        bottomStart = if (isMine) 17.dp else 5.dp,
        bottomEnd = if (isMine) 5.dp else 17.dp
    )

    val lux = BravTheme.colors
    val mineBrush = Brush.linearGradient(listOf(BravPurplePrimary, BravPurpleDeep))
    val fgColor = if (isMine) BravWhite else lux.inkPrimary

    val isSticker = message.messageType == "sticker" && !message.deletedForAll

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.background(BravPurplePrimary.copy(alpha = 0.12f))
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 2.5.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 288.dp)
                .then(
                    if (isSticker) {
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Transparent)
                            .combinedClickable(
                                onClick = { onTapOverride?.invoke() },
                                onLongClick = onLongPress
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .testTag("chat_sticker_bubble_${message.id}")
                    } else {
                        Modifier
                            .clip(bubbleShape)
                            .then(
                                if (isMine) Modifier.background(mineBrush)
                                else Modifier
                                    .background(lux.cardSurface)
                                    .border(0.75.dp, lux.hairline, bubbleShape)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (onTapOverride != null) {
                                        onTapOverride()
                                    } else if (message.messageType == "image" || message.messageType == "video") {
                                        if (message.mediaUrl.isNotBlank()) {
                                            onOpenMedia(
                                                message.mediaUrl,
                                                message.messageType == "video",
                                                message.mediaName
                                            )
                                        }
                                    }
                                },
                                onLongClick = onLongPress
                            )
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    }
                )
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (isGroup && !isMine) {
                    Text(
                        text = senderDisplayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = lux.titlePurple
                    )
                }

                if (message.forwardedFrom.isNotBlank()) {
                    Text(
                        text = "Transféré de ${message.forwardedFrom}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontStyle = FontStyle.Italic,
                        color = fgColor.copy(alpha = 0.72f)
                    )
                }

                if (message.replyToPreview.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (isMine) BravWhite.copy(alpha = 0.14f) else lux.softGray
                    ) {
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(if (isMine) lux.champagneGold else BravPurplePrimary)
                            )
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Reply,
                                    contentDescription = null,
                                    tint = fgColor.copy(alpha = 0.78f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = message.replyToPreview,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fgColor.copy(alpha = 0.92f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                when {
                    message.deletedForAll -> {
                        Text(
                            text = "Ce message a été supprimé",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = fgColor.copy(alpha = 0.72f)
                        )
                    }
                    message.messageType == "sticker" -> {
                        val sticker = remember(message.stickerId) {
                            BravStickerCatalog.byId(message.stickerId)
                        }
                        if (sticker != null) {
                            BravStickerArtwork(
                                sticker = sticker,
                                size = 136.dp,
                                animated = true
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = lux.translucentCardSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, lux.champagneGold)
                            ) {
                                Text(
                                    text = message.stickerLabel.ifBlank { displayText.ifBlank { "Autocollant BRAV" } },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = lux.titlePurple,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                    message.messageType == "voice" || message.messageType == "audio" -> {
                        VoiceMessageBubble(
                            mediaUrl = message.mediaUrl,
                            durationMs = message.durationMs,
                            waveform = message.waveform,
                            playbackState = playbackState,
                            isMine = isMine,
                            onTogglePlay = onToggleVoicePlay,
                            onCycleSpeed = onCycleVoiceSpeed
                        )
                    }
                    message.messageType == "image" -> {
                        var holdProgress by remember(message.id) { mutableFloatStateOf(0f) }
                        var showDownloadPill by remember(message.id) { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .pointerInput(message.id, message.mediaUrl, onTapOverride != null) {
                                    coroutineScope {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val pressTime = System.currentTimeMillis()
                                            var triggered5sDownload = false
                                            var pastTapThreshold = false
                                            val holdJob = launch {
                                                val initialThreshold = 350L.coerceAtMost(longPressDownloadDurationMs / 3)
                                                delay(initialThreshold)
                                                pastTapThreshold = true
                                                val remainingTarget = (longPressDownloadDurationMs - initialThreshold).coerceAtLeast(100L)
                                                var elapsed = 0L
                                                while (true) {
                                                    val tickStart = System.currentTimeMillis()
                                                    delay(45L)
                                                    val wallDelta = (System.currentTimeMillis() - tickStart).coerceAtLeast(0L)
                                                    elapsed += maxOf(45L, wallDelta)
                                                    holdProgress = (elapsed.toFloat() / remainingTarget.toFloat()).coerceIn(0f, 1f)
                                                    if (elapsed >= remainingTarget) {
                                                        triggered5sDownload = true
                                                        holdProgress = 0f
                                                        showDownloadPill = true
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        break
                                                    }
                                                }
                                            }
                                            val up = waitForUpOrCancellation()
                                            holdJob.cancel()
                                            holdProgress = 0f
                                            val heldMs = System.currentTimeMillis() - pressTime
                                            if (up != null && !triggered5sDownload) {
                                                if (!pastTapThreshold && heldMs < 350L) {
                                                    if (onTapOverride != null) onTapOverride()
                                                    else if (message.mediaUrl.isNotBlank()) {
                                                        onOpenMedia(message.mediaUrl, false, message.mediaName)
                                                    }
                                                } else {
                                                    onLongPress()
                                                }
                                            }
                                            down.consume()
                                        }
                                    }
                                }
                                .testTag("chat_image_${message.id}")
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(message.mediaUrl).crossfade(true).build(),
                                contentDescription = "Photo partagée",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(190.dp)
                            )
                            if (holdProgress > 0f && !showDownloadPill) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.60f),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(44.dp)
                                        .testTag("image_hold_progress_${message.id}")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            progress = { holdProgress },
                                            color = Color.White,
                                            trackColor = Color.White.copy(alpha = 0.25f),
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                            if (showDownloadPill && message.mediaUrl.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(0xFF130F1D).copy(alpha = 0.90f),
                                    tonalElevation = 4.dp,
                                    onClick = {
                                        showDownloadPill = false
                                        onDownloadMedia?.invoke(message)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .testTag("image_download_button_${message.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Download,
                                            contentDescription = "Télécharger",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "Télécharger",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                        if (displayText.isNotBlank()) {
                            Text(displayText, color = fgColor, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    message.messageType == "video" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .clickable { onOpenMedia(message.mediaUrl, true, message.mediaName) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.PlayCircle,
                                contentDescription = "Lire la vidéo",
                                tint = Color.White,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        if (displayText.isNotBlank()) {
                            Text(displayText, color = fgColor, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    message.messageType == "file" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (message.mediaUrl.isNotBlank()) {
                                        onOpenDocument(message)
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, tint = fgColor)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = message.mediaName.ifBlank { "Document" },
                                    color = fgColor,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${(message.mediaSize / 1024L).coerceAtLeast(1L)} Ko",
                                    color = fgColor.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    message.messageType == "location" -> {
                        val loc = remember(message.content) {
                            runCatching { JSONObject(message.content) }.getOrNull()
                        }
                        val lat = loc?.optDouble("lat", 0.0) ?: 0.0
                        val lng = loc?.optDouble("lng", 0.0) ?: 0.0
                        val label = loc?.optString("label", "Position GPS") ?: "Position GPS"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            }
                        ) {
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = BravRed)
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(label, color = fgColor, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "%.5f, %.5f".format(lat, lng),
                                    color = fgColor.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    message.messageType == "contact" -> {
                        val c = remember(message.content) {
                            runCatching { JSONObject(message.content) }.getOrNull()
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Person, contentDescription = null, tint = fgColor)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(c?.optString("displayName").orEmpty().ifBlank { "Contact BRAV" }, color = fgColor, fontWeight = FontWeight.Bold)
                                Text("@${c?.optString("username").orEmpty()}", color = fgColor.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    else -> {
                        val ytUrl = remember(displayText) {
                            YouTubePlayerController.findFirstYouTubeUrl(displayText)
                        }
                        Text(
                            text = displayText,
                            color = fgColor,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.5.sp,
                                lineHeight = 20.sp
                            )
                        )
                        if (ytUrl != null && !message.deletedForAll) {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isMine) Color.White.copy(alpha = 0.16f) else lux.translucentSubtleSurface,
                                border = if (isMine) null else androidx.compose.foundation.BorderStroke(0.6.dp, lux.violetContour),
                                onClick = { onOpenYouTube(ytUrl, "Vidéo partagée") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayCircle,
                                        contentDescription = "Lire sur YouTube",
                                        tint = BravRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Lire la vidéo YouTube",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = fgColor
                                        )
                                        Text(
                                            text = "Ouvrir dans le mini-lecteur",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = fgColor.copy(alpha = 0.78f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!translatedText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isMine) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Rounded.Translate, contentDescription = null, tint = fgColor, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(translatedText, style = MaterialTheme.typography.bodySmall, color = fgColor)
                        }
                    }
                }

                val metaColor = if (isSticker) lux.inkSecondary else fgColor.copy(alpha = 0.68f)
                val metaRowContent: @Composable () -> Unit = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (message.pinned) {
                            Icon(Icons.Rounded.PushPin, contentDescription = "Épinglé", tint = metaColor, modifier = Modifier.size(10.dp))
                        }
                        if (message.isEncrypted) {
                            Icon(Icons.Rounded.Lock, contentDescription = "E2EE", tint = metaColor, modifier = Modifier.size(10.dp))
                        }
                        if (message.editedAt != null && !message.deletedForAll) {
                            Text(
                                text = "modifié",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = metaColor
                            )
                        }
                        val ts = message.timestamp
                        if (ts != null) {
                            Text(
                                text = formatShortTime(ts.seconds),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = metaColor
                            )
                        }
                        if (isMine) {
                            val readIcon = if (message.status == "read" || message.status == "delivered") {
                                Icons.Rounded.DoneAll
                            } else {
                                Icons.Rounded.Done
                            }
                            val tint = if (message.status == "read") Color(0xFF0288D1) else metaColor
                            Icon(readIcon, contentDescription = message.status, tint = tint, modifier = Modifier.size(13.dp))
                        }
                    }
                }
                if (isSticker) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = lux.translucentCardSurface,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                            metaRowContent()
                        }
                    }
                } else {
                    Box(modifier = Modifier.align(Alignment.End)) {
                        metaRowContent()
                    }
                }
            }
        }

        MessageReactionsRow(
            reactions = message.reactions,
            myUid = myUid,
            onToggleReaction = onToggleReaction
        )
    }
}
