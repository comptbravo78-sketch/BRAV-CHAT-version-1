package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ai.ContentModerationGuard
import com.example.model.StatusPost
import com.example.model.User
import com.example.repository.MediaUploadPayload
import com.example.ui.components.LuxuryEmojiPickerSheet
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.theme.BravTheme
import com.example.util.ImageCompressor
import kotlinx.coroutines.launch

data class ActusDraftMediaItem(
    val id: String,
    val uri: Uri?,
    val bytes: ByteArray,
    val mimeType: String,
    val isVideo: Boolean
)

data class ActusComposeDraft(
    val text: String,
    val mediaBytes: ByteArray?,
    val mimeType: String,
    val isVideo: Boolean,
    val sourceVideoUri: Uri?,
    val mediaItems: List<MediaUploadPayload> = emptyList(),
    val backgroundColor: String,
    val audienceType: String,
    val allowedUids: List<String>,
    val isEphemeral24h: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActusComposerSheet(
    savedContacts: List<User>,
    currentUser: User? = null,
    initialIsEphemeral24h: Boolean = false,
    initialDraftMedia: List<ActusDraftMediaItem> = emptyList(),
    recentEmojis: List<String> = emptyList(),
    favoriteEmojis: List<String> = emptyList(),
    preferredSkinTone: String = "",
    onRecordEmoji: (String) -> Unit = {},
    onToggleFavoriteEmoji: (String) -> Unit = {},
    onSelectSkinTone: (String) -> Unit = {},
    onPublish: suspend (ActusComposeDraft) -> Result<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = rememberActusPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var text by remember { mutableStateOf("") }
    var isEphemeral24h by remember(initialIsEphemeral24h) { mutableStateOf(initialIsEphemeral24h) }
    var restrictAudience by remember { mutableStateOf(false) }
    var isPreviewMode by remember { mutableStateOf(false) }
    var showExpressionPanel by remember { mutableStateOf(false) }
    var showFullEmojiSheet by remember { mutableStateOf(false) }
    val allowed = remember { mutableStateListOf<String>() }
    val mediaList = remember {
        mutableStateListOf<ActusDraftMediaItem>().apply {
            addAll(initialDraftMedia)
        }
    }
    var moderationError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Mention suggestions when the trailing token starts with '@'
    val mentionQuery = remember(text) {
        val lastToken = text.substringAfterLast(' ').substringAfterLast('\n')
        if (lastToken.startsWith("@")) lastToken.drop(1).lowercase() else null
    }
    val mentionSuggestions = remember(mentionQuery, savedContacts) {
        val q = mentionQuery ?: return@remember emptyList()
        savedContacts.filter { u ->
            q.isBlank() ||
                u.username.contains(q, ignoreCase = true) ||
                u.displayName.contains(q, ignoreCase = true)
        }.take(5)
    }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            moderationError = null
            // Photos and videos are mutually exclusive in a single publication
            if (mediaList.any { it.isVideo }) mediaList.clear()
            for (uri in uris) {
                if (mediaList.size >= 6) break
                val compressed = ImageCompressor.compressForUpload(context, uri, maxDimension = 1440)
                if (compressed != null) {
                    val (compressedBytes, compressedMime) = compressed
                    val verdict = ContentModerationGuard.moderatePublication(
                        text = "",
                        imageBytes = compressedBytes,
                        isVideo = false,
                        context = context
                    )
                    if (!verdict.allowed) {
                        moderationError = verdict.userMessage
                    } else {
                        mediaList.add(
                            ActusDraftMediaItem(
                                id = "${uri}_${System.nanoTime()}",
                                uri = uri,
                                bytes = compressedBytes,
                                mimeType = compressedMime,
                                isVideo = false
                            )
                        )
                    }
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            moderationError = null
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val verdict = ContentModerationGuard.moderatePublication(
                    text = "",
                    isVideo = true,
                    videoUri = uri,
                    context = context
                )
                if (!verdict.allowed) {
                    moderationError = verdict.userMessage
                } else {
                    mediaList.clear()
                    mediaList.add(
                        ActusDraftMediaItem(
                            id = "${uri}_${System.nanoTime()}",
                            uri = uri,
                            bytes = bytes,
                            mimeType = context.contentResolver.getType(uri) ?: "video/mp4",
                            isVideo = true
                        )
                    )
                }
            }
        }
    }

    val canPublish = !isSubmitting && (text.isNotBlank() || mediaList.isNotEmpty())

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        containerColor = BravTheme.colors.modalSurface,
        modifier = Modifier.testTag("actus_composer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Crisp Editorial Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = !isSubmitting,
                    onClick = onDismiss
                ) {
                    Text("Annuler", color = palette.secondaryInk)
                }
                Text(
                    text = if (isEphemeral24h) "Nouveau statut 24h" else "Nouvelle publication",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryInk
                )
                Button(
                    enabled = canPublish,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    onClick = {
                        val cleanText = text.trim()
                        val textVerdict = ContentModerationGuard.checkText(cleanText)
                        if (!textVerdict.allowed) {
                            moderationError = textVerdict.userMessage
                            return@Button
                        }
                        if (mediaList.isEmpty() && cleanText.isBlank()) {
                            moderationError = "Ajoutez un texte, une photo ou une vidéo avant de publier."
                            return@Button
                        }
                        isSubmitting = true
                        moderationError = null
                        val firstMedia = mediaList.firstOrNull()
                        val payloads = mediaList.map {
                            MediaUploadPayload(
                                bytes = it.bytes,
                                mimeType = it.mimeType,
                                isVideo = it.isVideo,
                                sourceVideoUri = if (it.isVideo) it.uri else null
                            )
                        }
                        scope.launch {
                            val res = onPublish(
                                ActusComposeDraft(
                                    text = cleanText,
                                    mediaBytes = firstMedia?.bytes,
                                    mimeType = firstMedia?.mimeType ?: "image/jpeg",
                                    isVideo = firstMedia?.isVideo ?: false,
                                    sourceVideoUri = if (firstMedia?.isVideo == true) firstMedia.uri else null,
                                    mediaItems = payloads,
                                    backgroundColor = "#570CA2",
                                    audienceType = if (restrictAudience) "selected" else "all",
                                    allowedUids = if (restrictAudience) allowed.toList() else emptyList(),
                                    isEphemeral24h = isEphemeral24h
                                )
                            )
                            isSubmitting = false
                            if (res.isSuccess) {
                                onDismiss()
                            } else {
                                moderationError = res.exceptionOrNull()?.localizedMessage
                                    ?: ContentModerationGuard.TEXT_BLOCKED_MESSAGE
                            }
                        }
                    },
                    modifier = Modifier.testTag("actus_composer_publish_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Publier", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Scope, Audience & Live Preview Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (!isEphemeral24h) palette.accent.copy(alpha = 0.12f) else palette.subtleSurface,
                    onClick = { isEphemeral24h = false }
                ) {
                    Text(
                        text = "Fil Actus",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (!isEphemeral24h) palette.accent else palette.secondaryInk,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isEphemeral24h) palette.accent.copy(alpha = 0.12f) else palette.subtleSurface,
                    onClick = { isEphemeral24h = true }
                ) {
                    Text(
                        text = "Statut 24h",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEphemeral24h) palette.accent else palette.secondaryInk,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = palette.subtleSurface,
                    onClick = { restrictAudience = !restrictAudience }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (restrictAudience) Icons.Rounded.Lock else Icons.Rounded.Public,
                            contentDescription = null,
                            tint = palette.secondaryInk,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (restrictAudience) "Sélection (${allowed.size})" else "Public",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.primaryInk
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isPreviewMode) palette.accent.copy(alpha = 0.14f) else palette.subtleSurface,
                    onClick = { isPreviewMode = !isPreviewMode },
                    modifier = Modifier.testTag("actus_composer_preview_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isPreviewMode) Icons.Rounded.Edit else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = if (isPreviewMode) palette.accent else palette.secondaryInk,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isPreviewMode) "Éditer" else "Aperçu",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isPreviewMode) palette.accent else palette.primaryInk,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            AnimatedVisibility(visible = moderationError != null) {
                moderationError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("actus_moderation_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.ReportProblem,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (isPreviewMode) {
                val previewPost = StatusPost(
                    id = "preview_draft",
                    userId = currentUser?.userId ?: "me",
                    userName = currentUser?.displayName ?: "Vous",
                    userAvatar = currentUser?.avatarUrl.orEmpty(),
                    authorUsername = currentUser?.username.orEmpty(),
                    authorHeadline = currentUser?.locationCity.orEmpty(),
                    type = when {
                        mediaList.any { it.isVideo } -> "video"
                        mediaList.isNotEmpty() -> "image"
                        else -> "text"
                    },
                    text = if (mediaList.isEmpty()) text.trim() else "",
                    caption = if (mediaList.isNotEmpty()) text.trim() else "",
                    mediaUrl = mediaList.firstOrNull()?.uri?.toString().orEmpty(),
                    mediaUrls = mediaList.mapNotNull { it.uri?.toString() },
                    audienceType = if (restrictAudience) "selected" else "all",
                    expiresAtMs = if (isEphemeral24h) {
                        System.currentTimeMillis() + 24L * 3600L * 1000L
                    } else {
                        System.currentTimeMillis() + 3650L * 24L * 3600L * 1000L
                    }
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.75.dp, palette.hairline),
                    color = palette.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("actus_composer_preview_card")
                ) {
                    ActusFeedCard(
                        post = previewPost,
                        authorUser = currentUser,
                        myUid = currentUser?.userId ?: "me",
                        isOnline = true,
                        isReactionDockExpanded = false,
                        isPreviewMode = true,
                        onAuthorClick = { _, _ -> },
                        onToggleLike = {},
                        onToggleReactionDock = {},
                        onSelectReaction = {},
                        onOpenComments = {},
                        onShare = {},
                        onOpenMediaIndex = {},
                        onDeletePost = {}
                    )
                }
            } else {
                // Distraction-Free Editorial Writing Canvas
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        moderationError = null
                    },
                    placeholder = {
                        Text(
                            text = if (mediaList.isNotEmpty()) {
                                "Ajoutez une légende ou @mentionnez un contact..."
                            } else {
                                "Partagez une actualité, une idée ou @mentionnez un contact..."
                            },
                            color = palette.secondaryInk
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.subtleSurface.copy(alpha = 0.55f),
                        unfocusedContainerColor = palette.subtleSurface.copy(alpha = 0.35f),
                        focusedBorderColor = palette.accent.copy(alpha = 0.55f),
                        unfocusedBorderColor = palette.hairline,
                        focusedTextColor = palette.primaryInk,
                        unfocusedTextColor = palette.primaryInk
                    ),
                    shape = RoundedCornerShape(14.dp),
                    minLines = if (showExpressionPanel) 2 else 4,
                    maxLines = if (showExpressionPanel) 3 else 9,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("actus_composer_text_input")
                )

                // Live @mention Contact Autocomplete Strip
                if (mentionSuggestions.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("actus_composer_mention_suggestions")
                    ) {
                        items(mentionSuggestions, key = { it.userId }) { candidate ->
                            val handle = candidate.username.ifBlank {
                                candidate.displayName.lowercase().replace(" ", "")
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = palette.subtleSurface,
                                border = BorderStroke(0.75.dp, palette.hairline),
                                onClick = {
                                    val prefix = text.substringBeforeLast("@")
                                    text = "$prefix@$handle "
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    UserAvatarWithStatus(
                                        name = candidate.displayName,
                                        avatarUrl = candidate.avatarUrl,
                                        size = 20.dp
                                    )
                                    Text(
                                        text = "@$handle",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.accent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // Multi-Media Filmstrip with Reorder & Remove
                if (mediaList.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("actus_composer_media_strip")
                    ) {
                        itemsIndexed(mediaList, key = { _, item -> item.id }) { index, item ->
                            Box(
                                modifier = Modifier
                                    .size(width = 112.dp, height = 112.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.subtleSurface)
                                    .border(0.75.dp, palette.hairline, RoundedCornerShape(12.dp))
                            ) {
                                if (!item.isVideo && item.uri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(item.uri).build(),
                                        contentDescription = "Média sélectionné ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.matchParentSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.matchParentSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayCircleFilled,
                                            contentDescription = "Vidéo sélectionnée",
                                            tint = palette.accent,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }

                                // Remove media badge
                                Surface(
                                    shape = CircleShape,
                                    color = palette.surface.copy(alpha = 0.94f),
                                    border = BorderStroke(0.6.dp, palette.hairline),
                                    onClick = { mediaList.removeAt(index) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(26.dp)
                                        .testTag("composer_media_remove_$index")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Retirer ce média",
                                            tint = palette.primaryInk,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                // Reorder controls for multi-photo posts
                                if (mediaList.size > 1 && !item.isVideo) {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(palette.surface.copy(alpha = 0.92f))
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            enabled = index > 0,
                                            onClick = {
                                                if (index > 0) {
                                                    val moved = mediaList.removeAt(index)
                                                    mediaList.add(index - 1, moved)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(26.dp)
                                                .testTag("composer_media_move_left_$index")
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.ArrowBack,
                                                contentDescription = "Déplacer à gauche",
                                                tint = if (index > 0) palette.primaryInk else palette.secondaryInk.copy(alpha = 0.35f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = "${index + 1}/${mediaList.size}",
                                            color = palette.primaryInk,
                                            fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        IconButton(
                                            enabled = index < mediaList.lastIndex,
                                            onClick = {
                                                if (index < mediaList.lastIndex) {
                                                    val moved = mediaList.removeAt(index)
                                                    mediaList.add(index + 1, moved)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(26.dp)
                                                .testTag("composer_media_move_right_$index")
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.ArrowForward,
                                                contentDescription = "Déplacer à droite",
                                                tint = if (index < mediaList.lastIndex) palette.primaryInk else palette.secondaryInk.copy(alpha = 0.35f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Media & Expressions Action Strip
                HorizontalDivider(color = palette.hairline, thickness = 0.75.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.subtleSurface,
                        onClick = {
                            multiPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("actus_pick_photo_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (mediaList.isNotEmpty() && mediaList.none { it.isVideo }) {
                                    "Photos (${mediaList.size}/6)"
                                } else {
                                    "Photos"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.subtleSurface,
                        onClick = {
                            videoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("actus_pick_video_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Videocam, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Vidéo",
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.primaryInk,
                                maxLines = 1
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (showExpressionPanel) palette.accent.copy(alpha = 0.14f) else palette.subtleSurface,
                        border = if (showExpressionPanel) BorderStroke(0.75.dp, palette.accent.copy(alpha = 0.45f)) else null,
                        onClick = { showExpressionPanel = !showExpressionPanel },
                        modifier = Modifier
                            .weight(1.1f)
                            .heightIn(min = 48.dp)
                            .testTag("actus_composer_expression_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Mood,
                                contentDescription = "Expressions",
                                tint = palette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Nuances",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (showExpressionPanel) palette.accent else palette.primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showExpressionPanel) {
                    ActusInlineExpressionPanel(
                        palette = palette,
                        onInsertToken = { token -> text = "$text$token" },
                        onSelectReaction = null,
                        onOpenFullEmojiPicker = { showFullEmojiSheet = true },
                        onClose = { showExpressionPanel = false }
                    )
                }

                // Selective Audience Picker
                AnimatedVisibility(visible = restrictAudience) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Contacts autorisés :",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryInk
                        )
                        if (savedContacts.isEmpty()) {
                            Text(
                                text = "Aucun contact enregistré pour restreindre l'audience.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryInk
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                            ) {
                                items(savedContacts, key = { it.userId }) { contact ->
                                    val checked = contact.userId in allowed
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (checked) allowed.remove(contact.userId)
                                                else allowed.add(contact.userId)
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = {
                                                if (checked) allowed.remove(contact.userId)
                                                else allowed.add(contact.userId)
                                            }
                                        )
                                        Text(
                                            text = contact.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    if (showFullEmojiSheet) {
        LuxuryEmojiPickerSheet(
            onInsertEmoji = { emoji ->
                text = "$text$emoji"
                onRecordEmoji(emoji)
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
