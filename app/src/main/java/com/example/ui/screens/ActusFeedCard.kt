package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BravReactionType
import com.example.model.StatusPost
import com.example.model.User
import com.example.network.WorkerApiClient
import com.example.ui.components.BravGoldHairlineDivider
import com.example.ui.components.BravLuxuryCard
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.components.VerifiedBlueBadge
import com.example.ui.theme.BravTheme

@Composable
internal fun ActusFeedCard(
    post: StatusPost,
    authorUser: User?,
    myUid: String,
    isOnline: Boolean,
    isReactionDockExpanded: Boolean,
    workerApiClient: WorkerApiClient? = null,
    isPreviewMode: Boolean = false,
    onAuthorClick: (userId: String, handle: String?) -> Unit,
    onToggleLike: () -> Unit,
    onToggleReactionDock: () -> Unit,
    onSelectReaction: (BravReactionType) -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onOpenMediaIndex: (Int) -> Unit,
    onDeletePost: () -> Unit,
    onCopyFeedback: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val palette = rememberActusPalette()
    var showOverflowMenu by remember { mutableStateOf(false) }
    var expandLongText by remember(post.id) { mutableStateOf(false) }

    val resolvedMedia = remember(post.mediaUrl, post.mediaUrls) { post.resolvedMediaUrls() }
    val bodyText = remember(post.text, post.caption) { post.effectiveText() }
    val isMine = post.userId == myUid
    val isLiked = myUid in post.likedByUids
    val myReactionKey = remember(post.reactions, myUid) {
        post.reactions.entries.firstOrNull { myUid in it.value }?.key
    }
    val authorDisplayName = authorUser?.displayName?.ifBlank { post.userName }
        ?: post.userName.ifBlank { "Membre BRAV" }
    val authorAvatar = authorUser?.avatarUrl?.ifBlank { post.userAvatar } ?: post.userAvatar
    val subtitleMeta = remember(authorUser, post) {
        val handle = authorUser?.username?.takeIf { it.isNotBlank() }
            ?: post.authorUsername.takeIf { it.isNotBlank() }
        val city = authorUser?.locationCity?.takeIf { it.isNotBlank() }
            ?: post.authorHeadline.takeIf { it.isNotBlank() }
        val time = formatRelativeTimeFr(post.createdAt?.seconds)
        listOfNotNull(
            handle?.let { "@$it" },
            city,
            time
        ).joinToString(" · ")
    }

    val totalReactions = remember(post.reactions) { post.reactions.values.sumOf { it.size } }
    val topReactionTypes = remember(post.reactions) {
        post.reactions.entries
            .filter { it.value.isNotEmpty() }
            .sortedByDescending { it.value.size }
            .take(3)
            .mapNotNull { BravReactionType.fromKey(it.key) }
    }

    val lux = BravTheme.colors
    BravLuxuryCard(
        shapeRadius = 18.dp,
        accentBorder = authorUser?.isVerifiedActive() == true,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isPreviewMode) Modifier
                else Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
            .testTag("status_card_${post.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp)
        ) {
            // Editorial Author Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !isPreviewMode) {
                            onAuthorClick(post.userId, null)
                        }
                        .testTag("actus_author_header_${post.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.then(
                            if (authorUser?.isVerifiedActive() == true) {
                                Modifier
                                    .border(1.5.dp, lux.champagneGold, CircleShape)
                                    .padding(2.dp)
                            } else {
                                Modifier
                            }
                        )
                    ) {
                        UserAvatarWithStatus(
                            name = authorDisplayName,
                            avatarUrl = authorAvatar,
                            availabilityStatus = authorUser?.availabilityStatus ?: "online",
                            isOnlineRealtime = isOnline,
                            size = 44.dp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = authorDisplayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (authorUser?.isVerifiedActive() == true) {
                            VerifiedBlueBadge(size = 15.dp)
                        }
                        if (post.isEphemeralStory()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = palette.subtleSurface
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = null,
                                        tint = palette.accent,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "24h",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.accent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = subtitleMeta,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.secondaryInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = if (post.audienceType == "selected") Icons.Rounded.Lock else Icons.Rounded.Public,
                            contentDescription = if (post.audienceType == "selected") "Audience restreinte" else "Public",
                            tint = palette.secondaryInk,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            if (!isPreviewMode) {
                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("actus_menu_button_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "Options de publication",
                            tint = palette.secondaryInk
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        containerColor = lux.modalSurface
                    ) {
                        DropdownMenuItem(
                            text = { Text("Voir le profil") },
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onAuthorClick(post.userId, null)
                            }
                        )
                        if (bodyText.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Copier le texte") },
                                onClick = {
                                    showOverflowMenu = false
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("BRAV Actus", bodyText))
                                    onCopyFeedback("Texte copié")
                                }
                            )
                        }
                        if (isMine) {
                            DropdownMenuItem(
                                text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onDeletePost()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Content Body (Editorial Text or Caption)
        if (bodyText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            if (post.type == "text" && resolvedMedia.isEmpty()) {
                // Clean Editorial Note / Quote Styling without gaudy blocks
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .heightIn(min = 28.dp)
                            .background(palette.accent.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ActusMentionRichText(
                            text = bodyText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 24.sp
                            ),
                            color = palette.primaryInk,
                            accentColor = palette.accent,
                            maxLines = if (expandLongText) Int.MAX_VALUE else 7,
                            overflow = TextOverflow.Ellipsis,
                            onMentionClick = { handle -> onAuthorClick("", handle) }
                        )
                        if (bodyText.length > 260 && !expandLongText) {
                            Text(
                                text = "Lire la suite",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.accent,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .clickable { expandLongText = true }
                            )
                        }
                    }
                }
            } else {
                ActusMentionRichText(
                    text = bodyText,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                    color = palette.primaryInk,
                    accentColor = palette.accent,
                    maxLines = if (expandLongText) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    onMentionClick = { handle -> onAuthorClick("", handle) }
                )
                if (bodyText.length > 160 && !expandLongText) {
                    Text(
                        text = "Afficher plus",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accent,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { expandLongText = true }
                    )
                }
            }
        }

        // Media Presentation (Multi-photo editorial grid or Video player)
        if (post.type == "video" && resolvedMedia.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ActusInlineVideoPlayer(
                postId = post.id,
                videoUrl = resolvedMedia.first(),
                workerApiClient = workerApiClient,
                hairlineColor = palette.hairline,
                onOpenFullscreen = { onOpenMediaIndex(0) }
            )
        } else if (resolvedMedia.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ActusMediaGallery(
                postId = post.id,
                mediaUrls = resolvedMedia,
                authorName = authorDisplayName,
                hairlineColor = palette.hairline,
                onOpenMediaIndex = onOpenMediaIndex
            )
        }

        // Discreet Engagement Summary Row with Vector Reaction Badges
        if (post.likedByUids.isNotEmpty() || totalReactions > 0 || post.commentCount > 0L) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (topReactionTypes.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = palette.subtleSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                topReactionTypes.forEach { rx ->
                                    ActusVectorReactionGlyph(reaction = rx, size = 14.dp)
                                }
                            }
                        }
                    }
                    val engagementCount = post.likedByUids.size + totalReactions
                    if (engagementCount > 0) {
                        Text(
                            text = if (engagementCount == 1) "1 appréciation" else "$engagementCount appréciations",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.secondaryInk
                        )
                    }
                }
                if (post.commentCount > 0L) {
                    Text(
                        text = if (post.commentCount == 1L) "1 commentaire" else "${post.commentCount} commentaires",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.secondaryInk,
                        modifier = Modifier.clickable(enabled = !isPreviewMode, onClick = onOpenComments)
                    )
                }
            }
        }

        if (!isPreviewMode) {
            Spacer(Modifier.height(10.dp))
            BravGoldHairlineDivider(withGoldTouch = false)
            Spacer(Modifier.height(4.dp))

            // Clean Action Footer (All >= 48dp accessible touch targets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActusFooterAction(
                    icon = if (isLiked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                    label = if (post.likedByUids.isNotEmpty()) "${post.likedByUids.size}" else "J'aime",
                    tint = if (isLiked) palette.likeActive else palette.secondaryInk,
                    fontWeight = if (isLiked) FontWeight.SemiBold else FontWeight.Medium,
                    tag = "like_status_${post.id}",
                    onClick = onToggleLike,
                    modifier = Modifier.weight(1f)
                )

                val myReactionType = myReactionKey?.let { BravReactionType.fromKey(it) }
                ActusFooterAction(
                    icon = if (myReactionType == null) Icons.Outlined.Mood else null,
                    reactionGlyph = myReactionType,
                    label = myReactionType?.let(::reactionDisplayLabel) ?: "Réagir",
                    tint = if (myReactionType != null) palette.accent else palette.secondaryInk,
                    fontWeight = if (myReactionType != null) FontWeight.SemiBold else FontWeight.Medium,
                    tag = "emoji_dock_toggle_${post.id}",
                    onClick = onToggleReactionDock,
                    modifier = Modifier.weight(1f)
                )

                ActusFooterAction(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = if (post.commentCount > 0L) "${post.commentCount}" else "Commenter",
                    tint = palette.secondaryInk,
                    fontWeight = FontWeight.Medium,
                    tag = "comment_status_${post.id}",
                    onClick = onOpenComments,
                    modifier = Modifier.weight(1f)
                )

                ActusFooterAction(
                    icon = Icons.Outlined.Share,
                    label = "Partager",
                    tint = palette.secondaryInk,
                    fontWeight = FontWeight.Medium,
                    tag = "share_status_${post.id}",
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(visible = isReactionDockExpanded) {
                ActusEditorialReactionBar(
                    postId = post.id,
                    selectedKey = myReactionKey,
                    palette = palette,
                    onSelectReaction = onSelectReaction
                )
            }
        }
        }
    }
}

@Composable
private fun ActusFooterAction(
    icon: ImageVector?,
    label: String,
    tint: Color,
    fontWeight: FontWeight,
    tag: String,
    onClick: () -> Unit,
    reactionGlyph: BravReactionType? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (reactionGlyph != null) {
            ActusVectorReactionGlyph(
                reaction = reactionGlyph,
                size = 18.dp,
                tintOverride = tint
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = fontWeight,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
