package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ai.ContentModerationGuard
import com.example.model.BravReactionType
import com.example.model.Conversation
import com.example.model.StatusComment
import com.example.model.FollowRelationState
import com.example.model.StatusPost
import com.example.model.User
import com.example.network.WorkerApiClient
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravPatternType
import com.example.ui.components.LuxuryEmojiPickerSheet
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.components.VerifiedBlueBadge
import com.example.ui.theme.BravChampagneGold
import com.example.ui.theme.BravPurpleDeep
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import com.example.util.WhatsAppLauncher
import kotlinx.coroutines.launch
import java.io.File

internal data class CommentThreadGroup(
    val parent: StatusComment,
    val replies: List<StatusComment>
)

internal fun organizeCommentThreads(comments: List<StatusComment>): List<CommentThreadGroup> {
    val byId = comments.associateBy { it.id }
    val topLevel = comments.filter { it.parentCommentId.isBlank() || byId[it.parentCommentId] == null }
    val repliesByParent = comments
        .filter { it.parentCommentId.isNotBlank() && byId[it.parentCommentId] != null }
        .groupBy { it.parentCommentId }
    return topLevel.map { parent ->
        CommentThreadGroup(
            parent = parent,
            replies = repliesByParent[parent.id].orEmpty()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActusCommentsSheet(
    status: StatusPost,
    comments: List<StatusComment>,
    myUid: String,
    verifiedUserIds: Set<String> = emptySet(),
    recentEmojis: List<String> = emptyList(),
    favoriteEmojis: List<String> = emptyList(),
    preferredSkinTone: String = "",
    onRecordEmoji: (String) -> Unit = {},
    onToggleFavoriteEmoji: (String) -> Unit = {},
    onSelectSkinTone: (String) -> Unit = {},
    onSubmitComment: (
        text: String,
        reactionKey: String,
        parentCommentId: String,
        replyToAuthorName: String,
        onBlocked: (String) -> Unit,
        onAdded: () -> Unit
    ) -> Unit,
    onDeleteComment: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = rememberActusPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var commentField by remember { mutableStateOf(TextFieldValue("")) }
    var moderationError by remember { mutableStateOf<String?>(null) }
    var showExpressionPanel by remember { mutableStateOf(false) }
    var showFullEmojiSheet by remember { mutableStateOf(false) }
    var replyingTo by remember { mutableStateOf<StatusComment?>(null) }

    val threads = remember(comments) { organizeCommentThreads(comments) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BravTheme.colors.modalSurface,
        modifier = Modifier.testTag("actus_comments_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Commentaires (${comments.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryInk
                    )
                    Text(
                        text = "Publication de ${status.userName.ifBlank { "Membre BRAV" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryInk
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = palette.secondaryInk)
                }
            }

            HorizontalDivider(color = palette.hairline, thickness = 0.75.dp, modifier = Modifier.padding(vertical = 8.dp))

            if (threads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (showExpressionPanel) 8.dp else 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun commentaire pour le moment. Lancez l'échange.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.secondaryInk
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (showExpressionPanel) 130.dp else 360.dp)
                        .testTag("actus_comments_list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(threads, key = { it.parent.id }) { thread ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CommentItemRow(
                                comment = thread.parent,
                                isReply = false,
                                isMine = thread.parent.authorId == myUid,
                                isVerified = thread.parent.authorId in verifiedUserIds,
                                palette = palette,
                                onReply = { replyingTo = thread.parent },
                                onAuthorClick = { onAuthorClick(thread.parent.authorId) },
                                onDelete = { onDeleteComment(thread.parent.id) }
                            )
                            thread.replies.forEach { reply ->
                                CommentItemRow(
                                    comment = reply,
                                    isReply = true,
                                    isMine = reply.authorId == myUid,
                                    isVerified = reply.authorId in verifiedUserIds,
                                    palette = palette,
                                    onReply = { replyingTo = thread.parent },
                                    onAuthorClick = { onAuthorClick(reply.authorId) },
                                    onDelete = { onDeleteComment(reply.id) }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = replyingTo != null) {
                replyingTo?.let { target ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.subtleSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("actus_comment_reply_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Réponse à ${target.authorName.ifBlank { "Membre" }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.accent,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = { replyingTo = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Annuler la réponse", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            moderationError?.let { err ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .testTag("actus_comment_moderation_error")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.ReportProblem,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showExpressionPanel) {
                ActusInlineExpressionPanel(
                    palette = palette,
                    onInsertToken = { token ->
                        val current = commentField.text
                        val start = commentField.selection.min.coerceIn(0, current.length)
                        val end = commentField.selection.max.coerceIn(0, current.length)
                        val updated = current.substring(0, start) + token + current.substring(end)
                        commentField = TextFieldValue(
                            text = updated,
                            selection = TextRange(start + token.length)
                        )
                    },
                    onSelectReaction = { rx ->
                        showExpressionPanel = false
                        onSubmitComment(
                            "",
                            rx.key,
                            replyingTo?.id.orEmpty(),
                            replyingTo?.authorName.orEmpty(),
                            { err -> moderationError = err },
                            { replyingTo = null }
                        )
                    },
                    onOpenFullEmojiPicker = { showFullEmojiSheet = true },
                    onClose = { showExpressionPanel = false },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { showExpressionPanel = !showExpressionPanel },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("actus_comment_emoji_toggle")
                ) {
                    Icon(
                        Icons.Rounded.Mood,
                        contentDescription = "Expressions",
                        tint = if (showExpressionPanel) palette.accent else palette.secondaryInk
                    )
                }
                OutlinedTextField(
                    value = commentField,
                    onValueChange = {
                        commentField = it
                        moderationError = null
                    },
                    placeholder = {
                        Text(
                            if (replyingTo != null) "Votre réponse..." else "Ajouter un commentaire...",
                            color = palette.secondaryInk
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.subtleSurface,
                        unfocusedContainerColor = palette.subtleSurface,
                        focusedBorderColor = palette.accent.copy(alpha = 0.55f),
                        unfocusedBorderColor = palette.hairline,
                        focusedTextColor = palette.primaryInk,
                        unfocusedTextColor = palette.primaryInk
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("actus_comment_input")
                )
                IconButton(
                    enabled = commentField.text.isNotBlank(),
                    onClick = {
                        val clean = commentField.text.trim()
                        val verdict = ContentModerationGuard.checkText(clean)
                        if (!verdict.allowed) {
                            moderationError = verdict.userMessage
                            return@IconButton
                        }
                        val parent = replyingTo
                        onSubmitComment(
                            clean,
                            "",
                            parent?.id.orEmpty(),
                            parent?.authorName.orEmpty(),
                            { err -> moderationError = err },
                            {
                                commentField = TextFieldValue("")
                                replyingTo = null
                                moderationError = null
                                showExpressionPanel = false
                            }
                        )
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("actus_comment_send_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Publier le commentaire",
                        tint = if (commentField.text.isNotBlank()) palette.accent else palette.secondaryInk
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (showFullEmojiSheet) {
        LuxuryEmojiPickerSheet(
            onInsertEmoji = { emoji ->
                val current = commentField.text
                val start = commentField.selection.min.coerceIn(0, current.length)
                val end = commentField.selection.max.coerceIn(0, current.length)
                val updated = current.substring(0, start) + emoji + current.substring(end)
                commentField = TextFieldValue(
                    text = updated,
                    selection = TextRange(start + emoji.length)
                )
                onRecordEmoji(emoji)
            },
            onSelectReaction = { rx ->
                showFullEmojiSheet = false
                showExpressionPanel = false
                onSubmitComment(
                    "",
                    rx.key,
                    replyingTo?.id.orEmpty(),
                    replyingTo?.authorName.orEmpty(),
                    { err -> moderationError = err },
                    { replyingTo = null }
                )
            },
            recentEmojis = recentEmojis,
            favoriteEmojis = favoriteEmojis,
            preferredSkinTone = preferredSkinTone,
            onToggleFavoriteEmoji = onToggleFavoriteEmoji,
            onSelectSkinTone = onSelectSkinTone,
            onDismiss = { showFullEmojiSheet = false }
        )
    }
}

@Composable
private fun CommentItemRow(
    comment: StatusComment,
    isReply: Boolean,
    isMine: Boolean,
    isVerified: Boolean = false,
    palette: ActusEditorialPalette,
    onReply: () -> Unit,
    onAuthorClick: () -> Unit,
    onDelete: () -> Unit
) {
    val reactionType = remember(comment.reactionKey) {
        comment.reactionKey.takeIf { it.isNotBlank() }?.let { BravReactionType.fromKey(it) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 38.dp else 0.dp)
            .testTag("actus_comment_item_${comment.id}"),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.clickable(onClick = onAuthorClick)) {
            UserAvatarWithStatus(
                name = comment.authorName,
                avatarUrl = comment.authorAvatar,
                size = if (isReply) 28.dp else 34.dp
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = palette.subtleSurface
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = comment.authorName.ifBlank { "Membre" },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryInk
                        )
                        if (isVerified) {
                            VerifiedBlueBadge(size = 13.dp)
                        }
                        if (comment.replyToAuthorName.isNotBlank()) {
                            Text(
                                text = "→ ${comment.replyToAuthorName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accent
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    if (comment.text.isNotBlank()) {
                        Text(
                            text = comment.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.primaryInk
                        )
                    } else if (reactionType != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            ActusVectorReactionGlyph(reaction = reactionType, size = 20.dp)
                            Text(
                                text = reactionDisplayLabel(reactionType),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = palette.primaryInk
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatRelativeTimeFr(comment.createdAt?.seconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryInk
                )
                Text(
                    text = "Répondre",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent,
                    modifier = Modifier
                        .clickable(onClick = onReply)
                        .testTag("reply_comment_${comment.id}")
                )
            }
        }
        if (isMine) {
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Supprimer le commentaire",
                    tint = palette.secondaryInk,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActusAuthorProfileSheet(
    author: User,
    authorPosts: List<StatusPost>,
    myUid: String,
    isOnline: Boolean,
    isFollowing: Boolean = false,
    followRelationState: FollowRelationState? = null,
    onToggleFollow: ((User) -> Unit)? = null,
    onSendMessage: (User) -> Unit,
    onOpenPostComments: (StatusPost) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = rememberActusPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val totalLikes = remember(authorPosts) { authorPosts.sumOf { it.likedByUids.size } }
    val effectiveFollowState = followRelationState ?: when {
        author.userId == myUid -> FollowRelationState.SELF
        isFollowing -> FollowRelationState.FOLLOWING
        else -> FollowRelationState.NOT_FOLLOWING
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BravTheme.colors.modalSurface,
        modifier = Modifier.testTag("actus_author_profile_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(BravPurpleDeep, BravPurplePrimary)))
            ) {
                if (author.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = author.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    BravAfricanPatternCanvas(
                        pattern = BravPatternType.HERITAGE_COVER,
                        primaryColor = Color(0xFF4A0A8C),
                        secondaryColor = BravChampagneGold,
                        opacity = 0.56f,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = BravWhite)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .border(2.dp, BravTheme.colors.champagneGold, CircleShape)
                        .padding(2.dp)
                ) {
                    UserAvatarWithStatus(
                        name = author.displayName,
                        avatarUrl = author.avatarUrl,
                        availabilityStatus = author.availabilityStatus,
                        isOnlineRealtime = isOnline,
                        size = 64.dp
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = author.displayName.ifBlank { "Membre BRAV" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (author.isVerifiedActive()) {
                            VerifiedBlueBadge(
                                size = 18.dp,
                                showTooltipOnTap = true,
                                verifiedOrgName = author.verifiedOrgName,
                                verificationCategory = author.verificationCategoryEnum.labelFr
                            )
                        }
                    }
                    if (author.verifiedOrgName.isNotBlank() && author.isVerifiedActive()) {
                        Text(
                            text = "${author.verificationCategoryEnum.labelFr} · ${author.verifiedOrgName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF0B69C7),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (author.username.isNotBlank()) {
                        Text(
                            text = "@${author.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.accent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (author.locationCity.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = palette.secondaryInk,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = author.locationCity,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryInk
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = palette.secondaryInk)
                }
            }

            if (author.bio.isNotBlank()) {
                Text(
                    text = author.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.primaryInk
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.subtleSurface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${author.followersCount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryInk
                    )
                    Text("Abonnés", style = MaterialTheme.typography.labelSmall, color = palette.secondaryInk)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${authorPosts.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryInk
                    )
                    Text("Publications", style = MaterialTheme.typography.labelSmall, color = palette.secondaryInk)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$totalLikes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryInk
                    )
                    Text("J'aime reçus", style = MaterialTheme.typography.labelSmall, color = palette.secondaryInk)
                }
            }

            val authorContext = LocalContext.current
            var showWhatsAppConfirm by remember { mutableStateOf(false) }
            if (author.userId != myUid) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onToggleFollow != null) {
                        FollowActionButton(
                            relationState = effectiveFollowState,
                            peerName = author.displayName,
                            onToggle = { onToggleFollow(author) },
                            modifier = Modifier.weight(1f),
                            testTagName = "actus_author_follow_button"
                        )
                    }
                    Button(
                        onClick = { onSendMessage(author) },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("actus_author_message_button")
                    ) {
                        Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Message", fontWeight = FontWeight.SemiBold)
                    }
                }
                if (WhatsAppLauncher.hasValidPhoneDigits(author.phoneMasked)) {
                    OutlinedButton(
                        onClick = { showWhatsAppConfirm = true },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("actus_author_whatsapp_button")
                    ) {
                        Text("Contacter sur WhatsApp", fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A))
                    }
                }
            }

            if (showWhatsAppConfirm) {
                AlertDialog(
                    onDismissRequest = { showWhatsAppConfirm = false },
                    containerColor = BravTheme.colors.modalSurface,
                    title = { Text("Contacter sur WhatsApp") },
                    text = {
                        Text("Quitter BRAV CHAT pour ouvrir la discussion WhatsApp avec ${author.displayName.ifBlank { author.phoneMasked }} ?")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showWhatsAppConfirm = false
                                WhatsAppLauncher.openWhatsAppChat(
                                    context = authorContext,
                                    rawPhone = author.phoneMasked,
                                    prefilledMessage = "Bonjour ${author.displayName}, je vous écris suite à votre publication sur BRAV Chat."
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White)
                        ) {
                            Text("Ouvrir WhatsApp")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWhatsAppConfirm = false }) { Text("Annuler") }
                    }
                )
            }

            if (authorPosts.isNotEmpty()) {
                HorizontalDivider(color = palette.hairline, thickness = 0.75.dp)
                Text(
                    text = "Publications récentes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primaryInk
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(authorPosts.take(8), key = { it.id }) { post ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.subtleSurface,
                            onClick = { onOpenPostComments(post) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val summary = post.effectiveText().ifBlank {
                                    if (post.type == "video") "Publication vidéo" else "Publication photo"
                                }
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.primaryInk,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatRelativeTimeFr(post.createdAt?.seconds)} · ${post.likedByUids.size} j'aime · ${post.commentCount} commentaires",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.secondaryInk
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActusShareSheet(
    post: StatusPost,
    conversations: List<Conversation>,
    myUid: String,
    onForwardToConversation: suspend (Conversation, String) -> Result<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = rememberActusPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var personalNote by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var sendingConvId by remember { mutableStateOf<String?>(null) }

    val filteredConversations = remember(conversations, search) {
        val q = search.trim()
        conversations.filter { conv ->
            val title = if (conv.isGroup) conv.groupName else {
                conv.participantNames.entries.firstOrNull { it.key != myUid }?.value.orEmpty()
            }
            q.isBlank() || title.contains(q, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BravTheme.colors.modalSurface,
        modifier = Modifier.testTag("actus_share_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Partager la publication",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryInk
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = palette.secondaryInk)
                }
            }

            OutlinedButton(
                onClick = {
                    val sharePayload = buildString {
                        appendLine("Actus BRAV Chat • ${post.userName}")
                        if (post.effectiveText().isNotBlank()) appendLine(post.effectiveText())
                        post.resolvedMediaUrls().firstOrNull()?.let { append(it) }
                    }.trim()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, sharePayload)
                    }
                    context.startActivity(Intent.createChooser(intent, "Partager cette publication"))
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.75.dp, palette.hairline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("actus_share_external_button")
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Partager vers une autre application", fontWeight = FontWeight.Medium)
            }

            HorizontalDivider(color = palette.hairline, thickness = 0.75.dp)

            Text(
                text = "Transférer dans BRAV Chat",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.primaryInk
            )

            OutlinedTextField(
                value = personalNote,
                onValueChange = { personalNote = it },
                placeholder = { Text("Ajouter un message d'accompagnement (optionnel)", color = palette.secondaryInk) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.subtleSurface,
                    unfocusedContainerColor = palette.subtleSurface,
                    focusedBorderColor = palette.accent.copy(alpha = 0.55f),
                    unfocusedBorderColor = palette.hairline,
                    focusedTextColor = palette.primaryInk,
                    unfocusedTextColor = palette.primaryInk
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (conversations.size > 4) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Filtrer une discussion...", color = palette.secondaryInk) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.secondaryInk) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.subtleSurface,
                        unfocusedContainerColor = palette.subtleSurface,
                        focusedBorderColor = palette.accent.copy(alpha = 0.55f),
                        unfocusedBorderColor = palette.hairline,
                        focusedTextColor = palette.primaryInk,
                        unfocusedTextColor = palette.primaryInk
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (filteredConversations.isEmpty()) {
                Text(
                    text = "Aucune discussion disponible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryInk,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredConversations, key = { it.id }) { conv ->
                        val peerUid = if (conv.isGroup) "" else conv.participants.firstOrNull { it != myUid }.orEmpty()
                        val title = if (conv.isGroup) conv.groupName.ifBlank { "Groupe BRAV" } else {
                            conv.participantNames[peerUid].orEmpty().ifBlank { "Contact BRAV" }
                        }
                        val avatar = if (conv.isGroup) conv.groupAvatarUrl else conv.participantAvatars[peerUid].orEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .background(palette.subtleSurface, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatarWithStatus(name = title, avatarUrl = avatar, size = 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                enabled = sendingConvId == null,
                                onClick = {
                                    sendingConvId = conv.id
                                    scope.launch {
                                        onForwardToConversation(conv, personalNote)
                                        sendingConvId = null
                                        onDismiss()
                                    }
                                },
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("actus_share_conv_${conv.id}")
                            ) {
                                if (sendingConvId == conv.id) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Envoyer", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun ActusFullScreenGalleryDialog(
    post: StatusPost,
    initialIndex: Int,
    workerApiClient: WorkerApiClient?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val palette = rememberActusPalette()
    val mediaUrls = remember(post) { post.resolvedMediaUrls() }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (mediaUrls.size - 1).coerceAtLeast(0)),
        pageCount = { mediaUrls.size.coerceAtLeast(1) }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("actus_fullscreen_gallery"),
            color = palette.canvas
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                if (post.type == "video" && mediaUrls.isNotEmpty()) {
                    var cachedFile by remember(mediaUrls.first()) { mutableStateOf<File?>(null) }
                    LaunchedEffect(mediaUrls.first()) {
                        workerApiClient?.downloadMediaToCache(context, mediaUrls.first(), "full_${post.id}.mp4")
                            ?.onSuccess { cachedFile = it }
                    }
                    if (cachedFile != null) {
                        val uri = Uri.fromFile(cachedFile)
                        val exoPlayer = remember(uri) {
                            ExoPlayer.Builder(context).build().apply {
                                setMediaItem(MediaItem.fromUri(uri))
                                prepare()
                                playWhenReady = true
                            }
                        }
                        DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
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
                    } else {
                        CircularProgressIndicator(color = palette.accent, modifier = Modifier.align(Alignment.Center))
                    }
                } else if (mediaUrls.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(mediaUrls[page])
                                .crossfade(true)
                                .build(),
                            contentDescription = "Photo ${page + 1} de ${post.userName}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(palette.surface.copy(alpha = 0.94f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = post.userName.ifBlank { "Membre BRAV" },
                                color = palette.primaryInk,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (mediaUrls.size > 1 && post.type != "video") {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${mediaUrls.size}",
                                    color = palette.secondaryInk,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = palette.primaryInk)
                        }
                    }
                    HorizontalDivider(color = palette.hairline, thickness = 0.75.dp)
                }

                if (post.effectiveText().isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(palette.surface.copy(alpha = 0.95f))
                    ) {
                        HorizontalDivider(color = palette.hairline, thickness = 0.75.dp)
                        Text(
                            text = post.effectiveText(),
                            color = palette.primaryInk,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }
    }
}
