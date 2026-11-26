package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.StatusPost
import com.example.ui.theme.BravChampagneGold
import com.example.ui.theme.BravPurpleDeep
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRedSecondary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite

enum class PublicationsLayoutMode {
    GRID,
    LIST
}

@Composable
fun UserPublicationsSection(
    posts: List<StatusPost>,
    isOwner: Boolean,
    viewerUid: String,
    canViewPosts: Boolean = true,
    sectionTitle: String = if (isOwner) "MES PUBLICATIONS" else "PUBLICATIONS",
    onOpenComments: ((StatusPost) -> Unit)? = null,
    onSharePost: ((StatusPost) -> Unit)? = null,
    onEditPost: ((StatusPost, String) -> Unit)? = null,
    onDeletePost: ((StatusPost) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lux = BravTheme.colors
    var layoutMode by remember { mutableStateOf(PublicationsLayoutMode.GRID) }
    var selectedPost by remember { mutableStateOf<StatusPost?>(null) }
    var editingPost by remember { mutableStateOf<StatusPost?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var deletingPost by remember { mutableStateOf<StatusPost?>(null) }

    val visiblePosts = remember(posts, isOwner, viewerUid, canViewPosts) {
        if (!canViewPosts && !isOwner) {
            emptyList()
        } else {
            posts.filter { post ->
                isOwner ||
                    post.audienceType != "selected" ||
                    post.userId == viewerUid ||
                    viewerUid in post.allowedUids
            }
        }
    }

    BravLuxuryCard(
        shapeRadius = 20.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("user_publications_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = sectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = lux.inkPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = BravPurplePrimary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${visiblePosts.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = lux.titlePurple,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                if (canViewPosts || isOwner) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { layoutMode = PublicationsLayoutMode.GRID },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("publications_view_grid_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GridView,
                                contentDescription = "Vue grille",
                                tint = if (layoutMode == PublicationsLayoutMode.GRID) lux.titlePurple else lux.inkSecondary
                            )
                        }
                        IconButton(
                            onClick = { layoutMode = PublicationsLayoutMode.LIST },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("publications_view_list_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ViewAgenda,
                                contentDescription = "Vue liste",
                                tint = if (layoutMode == PublicationsLayoutMode.LIST) lux.titlePurple else lux.inkSecondary
                            )
                        }
                    }
                }
            }

            if (!canViewPosts && !isOwner) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = lux.translucentSubtleSurface,
                    border = BorderStroke(0.75.dp, lux.hairline),
                    modifier = Modifier.fillMaxWidth().testTag("publications_private_notice")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = lux.inkSecondary)
                        Text(
                            text = "Les publications de ce profil sont limitées par ses paramètres de confidentialité.",
                            style = MaterialTheme.typography.bodySmall,
                            color = lux.inkSecondary
                        )
                    }
                }
            } else if (visiblePosts.isEmpty()) {
                Text(
                    text = if (isOwner) "Aucune publication pour le moment. Publiez depuis l'onglet Actus."
                    else "Aucune publication visible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = lux.inkSecondary,
                    modifier = Modifier.testTag("publications_empty_notice")
                )
            } else if (layoutMode == PublicationsLayoutMode.GRID) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().testTag("publications_grid_container")
                ) {
                    visiblePosts.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowItems.forEach { post ->
                                PublicationGridTile(
                                    post = post,
                                    onClick = { selectedPost = post },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("publications_list_container")
                ) {
                    visiblePosts.forEach { post ->
                        PublicationListCard(
                            post = post,
                            onClick = { selectedPost = post }
                        )
                    }
                }
            }
        }
    }

    selectedPost?.let { post ->
        val mediaUrls = post.resolvedMediaUrls()
        val firstUrl = mediaUrls.firstOrNull().orEmpty()
        val totalReactions = post.likedByUids.size + post.reactions.values.sumOf { it.size }

        AlertDialog(
            onDismissRequest = { selectedPost = null },
            containerColor = lux.modalSurface,
            modifier = Modifier.testTag("publication_detail_dialog"),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = post.userName.ifBlank { "Publication" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = post.createdAt?.seconds?.let { formatShortDateTime(it) }.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = lux.inkSecondary
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (firstUrl.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (post.type == "video") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .background(Brush.linearGradient(listOf(BravPurpleDeep, BravPurplePrimary))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayCircle,
                                        contentDescription = "Vidéo",
                                        tint = BravWhite,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = firstUrl,
                                    contentDescription = "Média de la publication",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                                )
                            }
                        }
                    }

                    val body = post.effectiveText()
                    if (body.isNotBlank()) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = lux.inkPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = BravRedSecondary, modifier = Modifier.size(16.dp))
                            Text("$totalReactions réactions", style = MaterialTheme.typography.labelSmall, color = lux.inkSecondary)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, tint = lux.titlePurple, modifier = Modifier.size(16.dp))
                            Text("${post.commentCount} commentaires", style = MaterialTheme.typography.labelSmall, color = lux.inkSecondary)
                        }
                    }

                    if (onOpenComments != null || onSharePost != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (onOpenComments != null) {
                                OutlinedButton(
                                    onClick = {
                                        selectedPost = null
                                        onOpenComments(post)
                                    },
                                    modifier = Modifier.weight(1f).testTag("pub_detail_comments_button")
                                ) {
                                    Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Commentaires", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (onSharePost != null) {
                                OutlinedButton(
                                    onClick = {
                                        selectedPost = null
                                        onSharePost(post)
                                    },
                                    modifier = Modifier.weight(1f).testTag("pub_detail_share_button")
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Partager", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    if (isOwner && (onEditPost != null || onDeletePost != null)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (onEditPost != null) {
                                OutlinedButton(
                                    onClick = {
                                        editDraft = post.effectiveText()
                                        editingPost = post
                                        selectedPost = null
                                    },
                                    modifier = Modifier.weight(1f).testTag("pub_edit_button")
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Modifier")
                                }
                            }
                            if (onDeletePost != null) {
                                OutlinedButton(
                                    onClick = {
                                        deletingPost = post
                                        selectedPost = null
                                    },
                                    border = BorderStroke(1.dp, BravRedSecondary.copy(alpha = 0.6f)),
                                    modifier = Modifier.weight(1f).testTag("pub_delete_button")
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = BravRedSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Supprimer", color = BravRedSecondary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPost = null }) { Text("Fermer") }
            }
        )
    }

    editingPost?.let { post ->
        AlertDialog(
            onDismissRequest = { editingPost = null },
            containerColor = lux.modalSurface,
            title = { Text("Modifier la publication", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editDraft,
                    onValueChange = { editDraft = it },
                    label = { Text("Texte de la publication") },
                    minLines = 3,
                    maxLines = 6,
                    colors = bravOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("pub_edit_text_input")
                )
            },
            confirmButton = {
                Button(
                    enabled = editDraft.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                    onClick = {
                        val updated = editDraft.trim()
                        editingPost = null
                        onEditPost?.invoke(post, updated)
                    },
                    modifier = Modifier.testTag("pub_edit_save_button")
                ) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { editingPost = null }) { Text("Annuler") }
            }
        )
    }

    deletingPost?.let { post ->
        AlertDialog(
            onDismissRequest = { deletingPost = null },
            containerColor = lux.modalSurface,
            title = { Text("Supprimer cette publication ?") },
            text = { Text("Cette action supprimera définitivement votre publication d'Actus.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = BravRedSecondary, contentColor = BravWhite),
                    onClick = {
                        deletingPost = null
                        onDeletePost?.invoke(post)
                    },
                    modifier = Modifier.testTag("pub_delete_confirm_button")
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { deletingPost = null }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun PublicationGridTile(
    post: StatusPost,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lux = BravTheme.colors
    val mediaUrls = post.resolvedMediaUrls()
    val firstUrl = mediaUrls.firstOrNull().orEmpty()
    val likesTotal = post.likedByUids.size + post.reactions.values.sumOf { it.size }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(BravPurpleDeep, BravPurplePrimary)
                )
            )
            .clickable(onClick = onClick)
            .testTag("publication_grid_item_${post.id}")
    ) {
        if (firstUrl.isNotBlank() && post.type != "video") {
            AsyncImage(
                model = firstUrl,
                contentDescription = post.effectiveText().ifBlank { "Publication photo" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (post.type == "video") {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = "Publication vidéo",
                    tint = BravWhite,
                    modifier = Modifier.size(34.dp)
                )
            }
        } else {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.KENTE_DIAMOND,
                primaryColor = BravChampagneGold,
                secondaryColor = BravWhite,
                opacity = 0.24f,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = post.effectiveText().ifBlank { "Publication" },
                style = MaterialTheme.typography.labelSmall,
                color = BravWhite,
                fontWeight = FontWeight.Medium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            )
        }

        if (mediaUrls.size > 1) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Collections,
                    contentDescription = null,
                    tint = BravWhite,
                    modifier = Modifier.padding(3.dp).size(12.dp)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color.Black.copy(alpha = 0.52f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = lux.champagneGold,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = "$likesTotal",
                    style = MaterialTheme.typography.labelSmall,
                    color = BravWhite
                )
            }
        }
    }
}

@Composable
private fun PublicationListCard(
    post: StatusPost,
    onClick: () -> Unit
) {
    val lux = BravTheme.colors
    val firstUrl = post.resolvedMediaUrls().firstOrNull().orEmpty()
    val likesTotal = post.likedByUids.size + post.reactions.values.sumOf { it.size }
    val excerpt = post.effectiveText().ifBlank {
        when (post.type) {
            "video" -> "Publication vidéo"
            "image" -> "Publication photo"
            else -> "Statut partagé"
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = lux.translucentSubtleSurface,
        border = BorderStroke(0.65.dp, lux.hairline),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("publication_list_item_${post.id}")
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(BravPurpleDeep, BravPurplePrimary))),
                contentAlignment = Alignment.Center
            ) {
                if (firstUrl.isNotBlank() && post.type != "video") {
                    AsyncImage(
                        model = firstUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (post.type == "video") Icons.Rounded.PlayCircle else Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = BravChampagneGold,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = excerpt,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = lux.inkPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val datePart = post.createdAt?.seconds?.let { formatShortDateTime(it) }.orEmpty()
                Text(
                    text = listOfNotNull(
                        datePart.takeIf { it.isNotBlank() },
                        "$likesTotal j'aime",
                        "${post.commentCount} com."
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = lux.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
