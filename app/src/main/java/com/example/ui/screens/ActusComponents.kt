package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.BravReactionType
import com.example.network.WorkerApiClient
import com.example.ui.components.BravLuxuryCard
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRedSecondary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import java.io.File

internal data class ActusEditorialPalette(
    val canvas: Color,
    val surface: Color,
    val subtleSurface: Color,
    val hairline: Color,
    val primaryInk: Color,
    val secondaryInk: Color,
    val accent: Color,
    val likeActive: Color
)

@Composable
internal fun rememberActusPalette(): ActusEditorialPalette {
    val lux = BravTheme.colors
    return remember(lux) {
        ActusEditorialPalette(
            canvas = lux.ivoryCanvas,
            surface = lux.cardSurface,
            subtleSurface = lux.softGray,
            hairline = lux.hairline,
            primaryInk = lux.inkPrimary,
            secondaryInk = lux.inkSecondary,
            accent = lux.titlePurple,
            likeActive = BravRedSecondary
        )
    }
}

/**
 * Curated, dignified reaction set tailored for a mature social editorial experience.
 */
internal val ACTUS_CURATED_REACTIONS: List<BravReactionType> = listOf(
    BravReactionType.THUMBS_UP_GOLD,
    BravReactionType.HEART_PULSE,
    BravReactionType.STAR_HOLO,
    BravReactionType.PRAY_GRATEFUL,
    BravReactionType.FIRE_FLAME
)

internal fun reactionDisplayLabel(type: BravReactionType): String = when (type) {
    BravReactionType.THUMBS_UP_GOLD -> "Approuver"
    BravReactionType.HEART_PULSE -> "Apprécier"
    BravReactionType.STAR_HOLO -> "Pertinent"
    BravReactionType.PRAY_GRATEFUL -> "Soutien"
    BravReactionType.FIRE_FLAME -> "Remarquable"
    else -> type.label
}

internal fun formatRelativeTimeFr(epochSeconds: Long?, nowMs: Long = System.currentTimeMillis()): String {
    if (epochSeconds == null || epochSeconds <= 0L) return "À l'instant"
    val diffSec = ((nowMs / 1000L) - epochSeconds).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "À l'instant"
        diffSec < 3600L -> "Il y a ${diffSec / 60L} min"
        diffSec < 86_400L -> "Il y a ${diffSec / 3600L} h"
        diffSec < 7 * 86_400L -> "Il y a ${diffSec / 86_400L} j"
        else -> {
            val days = diffSec / 86_400L
            if (days < 30) "Il y a $days j" else "Il y a ${days / 30} mois"
        }
    }
}

private val richTokenRegex = Regex("""(@[A-Za-z0-9._-]{2,32}|#[A-Za-z0-9À-ÖØ-öø-ÿ_-]{2,32})""")

@Composable
internal fun ActusMentionRichText(
    text: String,
    style: TextStyle,
    color: Color,
    accentColor: Color,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onMentionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val annotated = remember(text, accentColor) {
        buildAnnotatedString {
            var cursor = 0
            richTokenRegex.findAll(text).forEach { match ->
                if (match.range.first > cursor) {
                    append(text.substring(cursor, match.range.first))
                }
                val token = match.value
                if (token.startsWith("@")) {
                    val handle = token.drop(1)
                    pushLink(
                        LinkAnnotation.Clickable(
                            tag = "mention_$handle",
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = accentColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            ),
                            linkInteractionListener = { onMentionClick(handle) }
                        )
                    )
                    append(token)
                    pop()
                } else {
                    pushStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Medium))
                    append(token)
                    pop()
                }
                cursor = match.range.last + 1
            }
            if (cursor < text.length) {
                append(text.substring(cursor))
            }
        }
    }

    Text(
        text = annotated,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
    )
}

@Composable
internal fun ActusMediaGallery(
    postId: String,
    mediaUrls: List<String>,
    authorName: String,
    hairlineColor: Color,
    onOpenMediaIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val urls = mediaUrls.filter { it.isNotBlank() }
    if (urls.isEmpty()) return
    val outerShape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(outerShape)
            .border(0.75.dp, hairlineColor, outerShape)
            .testTag("actus_gallery_${postId}")
    ) {
        when (urls.size) {
            1 -> {
                GalleryTile(
                    url = urls[0],
                    desc = "Photo de $authorName",
                    tag = "actus_gallery_item_${postId}_0",
                    onClick = { onOpenMediaIndex(0) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                )
            }
            2 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    GalleryTile(
                        url = urls[0],
                        desc = "Photo 1 de $authorName",
                        tag = "actus_gallery_item_${postId}_0",
                        onClick = { onOpenMediaIndex(0) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    GalleryTile(
                        url = urls[1],
                        desc = "Photo 2 de $authorName",
                        tag = "actus_gallery_item_${postId}_1",
                        onClick = { onOpenMediaIndex(1) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
            3 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(206.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    GalleryTile(
                        url = urls[0],
                        desc = "Photo 1 de $authorName",
                        tag = "actus_gallery_item_${postId}_0",
                        onClick = { onOpenMediaIndex(0) },
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight()
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        GalleryTile(
                            url = urls[1],
                            desc = "Photo 2 de $authorName",
                            tag = "actus_gallery_item_${postId}_1",
                            onClick = { onOpenMediaIndex(1) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                        GalleryTile(
                            url = urls[2],
                            desc = "Photo 3 de $authorName",
                            tag = "actus_gallery_item_${postId}_2",
                            onClick = { onOpenMediaIndex(2) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
            else -> {
                val extraCount = urls.size - 4
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        GalleryTile(
                            url = urls[0],
                            desc = "Photo 1 de $authorName",
                            tag = "actus_gallery_item_${postId}_0",
                            onClick = { onOpenMediaIndex(0) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        GalleryTile(
                            url = urls[1],
                            desc = "Photo 2 de $authorName",
                            tag = "actus_gallery_item_${postId}_1",
                            onClick = { onOpenMediaIndex(1) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        GalleryTile(
                            url = urls[2],
                            desc = "Photo 3 de $authorName",
                            tag = "actus_gallery_item_${postId}_2",
                            onClick = { onOpenMediaIndex(2) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            GalleryTile(
                                url = urls[3],
                                desc = "Photo 4 de $authorName",
                                tag = "actus_gallery_item_${postId}_3",
                                onClick = { onOpenMediaIndex(3) },
                                modifier = Modifier.fillMaxSize()
                            )
                            if (extraCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0x880F172A))
                                        .clickable { onOpenMediaIndex(3) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+$extraCount",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryTile(
    url: String,
    desc: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = desc,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .background(BravTheme.colors.translucentSubtleSurface)
            .clickable(onClick = onClick)
            .testTag(tag)
    )
}

@Composable
internal fun ActusInlineVideoPlayer(
    postId: String,
    videoUrl: String,
    workerApiClient: WorkerApiClient?,
    hairlineColor: Color,
    onOpenFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isStarted by remember(postId) { mutableStateOf(false) }
    var isMuted by remember(postId) { mutableStateOf(false) }
    var cachedFile by remember(videoUrl) { mutableStateOf<File?>(null) }
    var loadError by remember(videoUrl) { mutableStateOf<String?>(null) }

    LaunchedEffect(isStarted, videoUrl) {
        if (isStarted && cachedFile == null && videoUrl.isNotBlank() && workerApiClient != null) {
            workerApiClient.downloadMediaToCache(context, videoUrl, "actus_${postId}.mp4")
                .onSuccess { cachedFile = it }
                .onFailure { loadError = "Lecture vidéo indisponible" }
        }
    }

    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(BravTheme.colors.translucentSubtleSurface)
            .border(0.75.dp, hairlineColor, shape)
            .testTag("actus_video_${postId}")
    ) {
        if (!isStarted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isStarted = true },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = BravTheme.colors.translucentCardSurface,
                    border = BorderStroke(0.75.dp, hairlineColor),
                    shadowElevation = 2.dp
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Lire la vidéo",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier
                            .padding(14.dp)
                            .size(30.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.92f),
                    border = BorderStroke(0.6.dp, hairlineColor),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Vidéo",
                        color = Color(0xFF0F172A),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        } else if (cachedFile != null) {
            val uri = Uri.fromFile(cachedFile)
            val exoPlayer = remember(uri) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                    playWhenReady = true
                }
            }
            LaunchedEffect(isMuted) {
                exoPlayer.volume = if (isMuted) 0f else 1f
            }
            DisposableEffect(exoPlayer) {
                onDispose { exoPlayer.release() }
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.90f),
                    border = BorderStroke(0.6.dp, Color(0xFFE5E9F0))
                ) {
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = if (isMuted) "Activer le son" else "Couper le son",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.90f),
                    border = BorderStroke(0.6.dp, Color(0xFFE5E9F0))
                ) {
                    IconButton(
                        onClick = onOpenFullscreen,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = "Plein écran",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else if (loadError != null) {
            Text(
                text = loadError ?: "",
                color = Color(0xFF0F172A),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
            )
        }
    }
}

@Composable
internal fun ActusEditorialReactionBar(
    postId: String,
    selectedKey: String?,
    palette: ActusEditorialPalette,
    onSelectReaction: (BravReactionType) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = palette.surface,
        border = BorderStroke(0.75.dp, palette.hairline),
        shadowElevation = 2.dp,
        modifier = Modifier
            .padding(top = 6.dp)
            .testTag("actus_reaction_dock_${postId}")
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ACTUS_CURATED_REACTIONS.forEach { reaction ->
                val isSelected = selectedKey == reaction.key
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) palette.accent.copy(alpha = 0.12f) else Color.Transparent,
                    label = "actus_rx_bg"
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = bgColor,
                    border = if (isSelected) BorderStroke(0.75.dp, palette.accent.copy(alpha = 0.40f)) else null,
                    onClick = { onSelectReaction(reaction) },
                    modifier = Modifier.testTag("actus_reaction_${reaction.key}_${postId}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ActusVectorReactionGlyph(
                            reaction = reaction,
                            size = 18.dp,
                            tintOverride = if (isSelected) palette.accent else null
                        )
                        Text(
                            text = reactionDisplayLabel(reaction),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) palette.accent else palette.secondaryInk
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActusSkeletonFeed(palette: ActusEditorialPalette) {
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(3) { idx ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(palette.subtleSurface)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.subtleSurface)
                        )
                        Box(
                            modifier = Modifier
                                .width(92.dp)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.subtleSurface)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.subtleSurface)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.subtleSurface)
                )
                if (idx == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.subtleSurface)
                    )
                }
            }
            HorizontalDivider(thickness = 0.75.dp, color = palette.hairline)
        }
    }
}

@Composable
internal fun ActusEmptyState(
    title: String,
    subtitle: String,
    ctaLabel: String,
    palette: ActusEditorialPalette,
    onCreateClick: () -> Unit
) {
    val lux = BravTheme.colors
    BravLuxuryCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("actus_empty_state")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = BravPurplePrimary.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, lux.champagneGold.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = if (title.contains("statut", ignoreCase = true)) {
                        Icons.Rounded.AutoAwesome
                    } else {
                        Icons.Rounded.Public
                    },
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(28.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.primaryInk,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.secondaryInk,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = BravPurplePrimary,
                onClick = onCreateClick
            ) {
                Text(
                    text = ctaLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = BravWhite,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp)
                )
            }
        }
    }
}
