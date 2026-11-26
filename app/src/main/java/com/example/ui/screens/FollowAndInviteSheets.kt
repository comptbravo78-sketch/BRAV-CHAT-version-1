package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FollowRelationState
import com.example.model.FollowRequest
import com.example.model.InviteLink
import com.example.model.User
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravGoldHairlineDivider
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.components.BravSearchBarField
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.components.VerifiedBlueBadge
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRedSecondary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import com.example.ui.theme.CallActionGreen
import kotlinx.coroutines.launch

enum class FollowManagementTab(val labelFr: String, val tag: String) {
    FOLLOWERS("Mes abonnés", "follow_tab_followers"),
    FOLLOWING("Mes abonnements", "follow_tab_following"),
    INCOMING("Demandes reçues", "follow_tab_incoming"),
    OUTGOING("Demandes envoyées", "follow_tab_outgoing")
}

@Composable
fun FollowActionButton(
    relationState: FollowRelationState,
    peerName: String = "",
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    testTagName: String = "follow_action_button"
) {
    if (relationState == FollowRelationState.SELF) return

    val lux = BravTheme.colors
    var showConfirmDialog by remember { mutableStateOf(false) }

    val label = when (relationState) {
        FollowRelationState.NOT_FOLLOWING -> "S'abonner"
        FollowRelationState.FOLLOWING -> "Abonné"
        FollowRelationState.REQUESTED -> "Abonnement demandé"
        FollowRelationState.SELF -> "Vous"
    }

    val containerColor = when (relationState) {
        FollowRelationState.NOT_FOLLOWING -> BravPurplePrimary.copy(alpha = 0.14f)
        FollowRelationState.FOLLOWING -> lux.champagneSoftTint
        FollowRelationState.REQUESTED -> lux.translucentSubtleSurface
        FollowRelationState.SELF -> lux.translucentSubtleSurface
    }
    val borderColor = when (relationState) {
        FollowRelationState.NOT_FOLLOWING -> BravPurplePrimary
        FollowRelationState.FOLLOWING -> lux.champagneGold
        FollowRelationState.REQUESTED -> lux.violetContour
        FollowRelationState.SELF -> lux.hairline
    }
    val contentColor = when (relationState) {
        FollowRelationState.NOT_FOLLOWING -> lux.titlePurple
        FollowRelationState.FOLLOWING -> lux.inkPrimary
        FollowRelationState.REQUESTED -> lux.inkSecondary
        FollowRelationState.SELF -> lux.inkSecondary
    }

    Surface(
        onClick = {
            when (relationState) {
                FollowRelationState.NOT_FOLLOWING -> onToggle()
                FollowRelationState.FOLLOWING,
                FollowRelationState.REQUESTED -> showConfirmDialog = true
                FollowRelationState.SELF -> Unit
            }
        },
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .height(48.dp)
            .testTag(testTagName)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when (relationState) {
                    FollowRelationState.NOT_FOLLOWING -> Icons.Rounded.PersonAdd
                    FollowRelationState.FOLLOWING -> Icons.Rounded.Check
                    FollowRelationState.REQUESTED -> Icons.Rounded.HourglassTop
                    FollowRelationState.SELF -> Icons.Rounded.Group
                },
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (showConfirmDialog) {
        val cleanName = peerName.ifBlank { "ce membre" }
        val isCancelReq = relationState == FollowRelationState.REQUESTED
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = lux.modalSurface,
            modifier = Modifier.testTag("follow_confirm_dialog"),
            title = {
                Text(
                    text = if (isCancelReq) "Annuler la demande d'abonnement ?"
                    else "Se désabonner de $cleanName ?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isCancelReq) {
                        "Votre demande d'abonnement adressée à $cleanName sera retirée."
                    } else {
                        "Vous ne verrez plus les publications réservées aux abonnés de $cleanName."
                    }
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BravRedSecondary,
                        contentColor = BravWhite
                    ),
                    onClick = {
                        showConfirmDialog = false
                        onToggle()
                    },
                    modifier = Modifier.testTag("follow_confirm_action_button")
                ) {
                    Text(if (isCancelReq) "Annuler la demande" else "Se désabonner")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(if (isCancelReq) "Fermer" else "Annuler")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowManagementSheet(
    followers: List<User>,
    following: List<User>,
    incomingRequests: List<FollowRequest>,
    outgoingRequests: List<FollowRequest>,
    initialTab: FollowManagementTab = FollowManagementTab.FOLLOWERS,
    onMessageUser: (User) -> Unit,
    onOpenUserProfile: ((User) -> Unit)? = null,
    onRemoveFollower: (User) -> Unit,
    onUnfollowUser: (User) -> Unit,
    onBlockUser: (User) -> Unit,
    onAcceptRequest: (FollowRequest) -> Unit,
    onDeclineRequest: (FollowRequest) -> Unit,
    onCancelOutgoingRequest: (FollowRequest) -> Unit,
    onDismiss: () -> Unit
) {
    val lux = BravTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var pendingUnfollowUser by remember { mutableStateOf<User?>(null) }
    var pendingRemoveFollower by remember { mutableStateOf<User?>(null) }
    var pendingBlockUser by remember { mutableStateOf<User?>(null) }

    val q = remember(searchQuery) { searchQuery.trim().lowercase() }
    val filteredFollowers = remember(followers, q) {
        if (q.isBlank()) followers else followers.filter {
            it.displayName.lowercase().contains(q) || it.username.lowercase().contains(q)
        }
    }
    val filteredFollowing = remember(following, q) {
        if (q.isBlank()) following else following.filter {
            it.displayName.lowercase().contains(q) || it.username.lowercase().contains(q)
        }
    }
    val filteredIncoming = remember(incomingRequests, q) {
        if (q.isBlank()) incomingRequests else incomingRequests.filter {
            it.fromName.lowercase().contains(q) || it.fromUsername.lowercase().contains(q)
        }
    }
    val filteredOutgoing = remember(outgoingRequests, q) {
        if (q.isBlank()) outgoingRequests else outgoingRequests.filter {
            it.toName.lowercase().contains(q)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = lux.modalSurface,
        modifier = Modifier.testTag("follow_management_sheet")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.KENTE_DIAMOND,
                variant = BravPatternVariant.GOLD_ON_IVORY,
                opacity = 0.085f,
                scale = 1.0f,
                modifier = Modifier.matchParentSize()
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Abonnements & Réseau",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = lux.titlePurple
                        )
                        Text(
                            text = "${followers.size} abonnés • ${following.size} abonnements • ${incomingRequests.size} demande(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = lux.inkSecondary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = lux.inkSecondary)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FollowManagementTab.entries.forEach { tab ->
                        val active = tab == selectedTab
                        val count = when (tab) {
                            FollowManagementTab.FOLLOWERS -> followers.size
                            FollowManagementTab.FOLLOWING -> following.size
                            FollowManagementTab.INCOMING -> incomingRequests.size
                            FollowManagementTab.OUTGOING -> outgoingRequests.size
                        }
                        Surface(
                            onClick = { selectedTab = tab },
                            shape = RoundedCornerShape(999.dp),
                            color = if (active) BravPurplePrimary.copy(alpha = 0.16f) else lux.translucentSubtleSurface,
                            border = BorderStroke(
                                1.dp,
                                if (active) lux.champagneGold else lux.violetContour
                            ),
                            modifier = Modifier.testTag(tab.tag)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = tab.labelFr,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) lux.titlePurple else lux.inkPrimary
                                )
                                if (count > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (tab == FollowManagementTab.INCOMING) BravRedSecondary
                                        else BravPurplePrimary.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "$count",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tab == FollowManagementTab.INCOMING) BravWhite else lux.titlePurple,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                BravSearchBarField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Filtrer par nom ou @identifiant...",
                    inputTestTag = "follow_sheet_search_input",
                    modifier = Modifier.fillMaxWidth()
                )

                BravGoldHairlineDivider(withGoldTouch = true)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 420.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (selectedTab) {
                        FollowManagementTab.FOLLOWERS -> {
                            if (filteredFollowers.isEmpty()) {
                                item {
                                    FollowSheetEmptyNotice("Aucun abonné correspondant pour le moment.")
                                }
                            } else {
                                items(filteredFollowers, key = { "follower_${it.userId}" }) { user ->
                                    FollowUserRow(
                                        user = user,
                                        primaryActionLabel = "Message",
                                        secondaryActionLabel = "Retirer",
                                        onRowClick = { onOpenUserProfile?.invoke(user) },
                                        onPrimaryAction = { onMessageUser(user) },
                                        onSecondaryAction = { pendingRemoveFollower = user },
                                        onBlockAction = { pendingBlockUser = user }
                                    )
                                }
                            }
                        }

                        FollowManagementTab.FOLLOWING -> {
                            if (filteredFollowing.isEmpty()) {
                                item {
                                    FollowSheetEmptyNotice("Aucun abonnement trouvé.")
                                }
                            } else {
                                items(filteredFollowing, key = { "following_${it.userId}" }) { user ->
                                    FollowUserRow(
                                        user = user,
                                        primaryActionLabel = "Message",
                                        secondaryActionLabel = "Se désabonner",
                                        onRowClick = { onOpenUserProfile?.invoke(user) },
                                        onPrimaryAction = { onMessageUser(user) },
                                        onSecondaryAction = { pendingUnfollowUser = user },
                                        onBlockAction = { pendingBlockUser = user }
                                    )
                                }
                            }
                        }

                        FollowManagementTab.INCOMING -> {
                            if (filteredIncoming.isEmpty()) {
                                item {
                                    FollowSheetEmptyNotice("Aucune demande d'abonnement reçue.")
                                }
                            } else {
                                items(filteredIncoming, key = { "in_${it.id}_${it.fromUid}" }) { req ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = lux.translucentCardSurface,
                                        border = BorderStroke(0.75.dp, lux.violetContour),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            UserAvatarWithStatus(
                                                name = req.fromName,
                                                avatarUrl = req.fromAvatar,
                                                size = 44.dp
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = req.fromName.ifBlank { "Membre BRAV" },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = lux.inkPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (req.fromUsername.isNotBlank()) {
                                                    Text(
                                                        text = "@${req.fromUsername}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = lux.inkSecondary
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { onDeclineRequest(req) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Close,
                                                    contentDescription = "Refuser",
                                                    tint = BravRedSecondary
                                                )
                                            }
                                            IconButton(
                                                onClick = { onAcceptRequest(req) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Check,
                                                    contentDescription = "Accepter",
                                                    tint = CallActionGreen
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        FollowManagementTab.OUTGOING -> {
                            if (filteredOutgoing.isEmpty()) {
                                item {
                                    FollowSheetEmptyNotice("Aucune demande d'abonnement envoyée en attente.")
                                }
                            } else {
                                items(filteredOutgoing, key = { "out_${it.id}_${it.toUid}" }) { req ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = lux.translucentCardSurface,
                                        border = BorderStroke(0.75.dp, lux.hairline),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            UserAvatarWithStatus(
                                                name = req.toName,
                                                avatarUrl = req.toAvatar,
                                                size = 44.dp
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = req.toName.ifBlank { "Membre BRAV" },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = lux.inkPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Demande en attente de validation",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = lux.inkSecondary
                                                )
                                            }
                                            TextButton(
                                                onClick = { onCancelOutgoingRequest(req) },
                                                modifier = Modifier.height(48.dp)
                                            ) {
                                                Text(
                                                    text = "Annuler",
                                                    color = BravRedSecondary,
                                                    fontWeight = FontWeight.SemiBold
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
        }
    }

    pendingUnfollowUser?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingUnfollowUser = null },
            containerColor = lux.modalSurface,
            title = { Text("Se désabonner de ${target.displayName} ?") },
            text = { Text("Vous pourrez vous réabonner à tout moment depuis son profil.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = BravRedSecondary, contentColor = BravWhite),
                    onClick = {
                        pendingUnfollowUser = null
                        onUnfollowUser(target)
                    }
                ) { Text("Se désabonner") }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnfollowUser = null }) { Text("Annuler") }
            }
        )
    }

    pendingRemoveFollower?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRemoveFollower = null },
            containerColor = lux.modalSurface,
            title = { Text("Retirer ${target.displayName} ?") },
            text = { Text("Cet utilisateur sera retiré de votre liste d'abonnés.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = BravRedSecondary, contentColor = BravWhite),
                    onClick = {
                        pendingRemoveFollower = null
                        onRemoveFollower(target)
                    }
                ) { Text("Retirer") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveFollower = null }) { Text("Annuler") }
            }
        )
    }

    pendingBlockUser?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingBlockUser = null },
            containerColor = lux.modalSurface,
            title = { Text("Bloquer ${target.displayName} ?") },
            text = { Text("Ce membre ne pourra plus vous envoyer de messages, vous appeler ni voir votre profil.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = BravRedSecondary, contentColor = BravWhite),
                    onClick = {
                        pendingBlockUser = null
                        onBlockUser(target)
                    }
                ) { Text("Bloquer") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBlockUser = null }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun FollowUserRow(
    user: User,
    primaryActionLabel: String,
    secondaryActionLabel: String,
    onRowClick: (() -> Unit)? = null,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    onBlockAction: () -> Unit
) {
    val lux = BravTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = lux.translucentCardSurface,
        border = BorderStroke(0.75.dp, lux.violetContour),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onRowClick != null) Modifier.clickable(onClick = onRowClick) else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                UserAvatarWithStatus(
                    name = user.displayName,
                    avatarUrl = user.avatarUrl,
                    availabilityStatus = user.availabilityStatus,
                    size = 44.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = user.displayName.ifBlank { "Membre BRAV" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = lux.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (user.isVerifiedActive()) {
                            VerifiedBlueBadge(size = 15.dp)
                        }
                    }
                    Text(
                        text = listOfNotNull(
                            user.username.takeIf { it.isNotBlank() }?.let { "@$it" },
                            user.locationCity.takeIf { it.isNotBlank() }
                        ).joinToString(" • ").ifBlank { "Membre BRAV CHAT" },
                        style = MaterialTheme.typography.labelSmall,
                        color = lux.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPrimaryAction,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(0.85.dp, lux.violetContour),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(primaryActionLabel, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onSecondaryAction,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(0.85.dp, lux.champagneGold),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Rounded.PersonRemove, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(secondaryActionLabel, style = MaterialTheme.typography.labelMedium, color = lux.inkPrimary)
                }
                IconButton(
                    onClick = onBlockAction,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Rounded.Block,
                        contentDescription = "Bloquer ${user.displayName}",
                        tint = BravRedSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowSheetEmptyNotice(message: String) {
    val lux = BravTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = lux.translucentSubtleSurface,
        border = BorderStroke(0.75.dp, lux.hairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = lux.inkSecondary,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteShareSheet(
    myInvites: List<InviteLink>,
    onEnsureInviteLink: suspend () -> Result<InviteLink>,
    onFeedback: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lux = BravTheme.colors
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var generatedInvite by remember { mutableStateOf<InviteLink?>(myInvites.firstOrNull()) }
    var isLoading by remember { mutableStateOf(myInvites.isEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (generatedInvite == null) {
            isLoading = true
            val res = onEnsureInviteLink()
            isLoading = false
            res.onSuccess { generatedInvite = it }
                .onFailure { errorMessage = it.localizedMessage ?: "Impossible de créer le lien" }
        }
    }

    val activeInvite = generatedInvite ?: myInvites.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = lux.modalSurface,
        modifier = Modifier.testTag("invite_share_sheet")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.SOLAR_ADINKRA,
                variant = BravPatternVariant.GOLD_ON_IVORY,
                opacity = 0.095f,
                scale = 1.0f,
                modifier = Modifier.matchParentSize()
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(BravPurplePrimary.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, tint = lux.titlePurple)
                        }
                        Column {
                            Text(
                                text = "Inviter sur BRAV CHAT",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = lux.titlePurple
                            )
                            Text(
                                text = "Lien personnel sécurisé sans exposer vos données privées",
                                style = MaterialTheme.typography.bodySmall,
                                color = lux.inkSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = lux.inkSecondary)
                    }
                }

                if (isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = BravPurplePrimary)
                        Spacer(Modifier.width(10.dp))
                        Text("Génération de votre lien personnel...", style = MaterialTheme.typography.bodySmall, color = lux.inkSecondary)
                    }
                } else if (activeInvite != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = lux.translucentCardSurface,
                        border = BorderStroke(1.dp, lux.champagneGold.copy(alpha = 0.75f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Lien d'invitation personnel",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = lux.titlePurple
                            )
                            Text(
                                text = activeInvite.shareUrl,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = lux.inkPrimary,
                                modifier = Modifier.testTag("invite_share_url_text")
                            )
                            Text(
                                text = "Code : ${activeInvite.code}",
                                style = MaterialTheme.typography.labelSmall,
                                color = lux.inkSecondary
                            )
                            BravGoldHairlineDivider(withGoldTouch = true)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${activeInvite.clicks}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = lux.inkPrimary
                                    )
                                    Text("Ouvertures", style = MaterialTheme.typography.labelSmall, color = lux.inkSecondary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${activeInvite.signups}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = lux.titlePurple
                                    )
                                    Text("Inscriptions", style = MaterialTheme.typography.labelSmall, color = lux.inkSecondary)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Lien BRAV CHAT", activeInvite.shareUrl))
                                onFeedback("Lien d'invitation copié")
                            },
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, lux.champagneGold),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("invite_copy_link_button")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copier le lien", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                val shareText = "Rejoins-moi sur BRAV CHAT pour discuter et appeler en HD : ${activeInvite.shareUrl}"
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Inviter sur BRAV CHAT"))
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("invite_share_intent_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Partager", fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = lux.translucentSubtleSurface,
                        border = BorderStroke(0.75.dp, BravRedSecondary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "Impossible de charger votre lien d'invitation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BravRedSecondary
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        val res = onEnsureInviteLink()
                                        isLoading = false
                                        res.onSuccess { generatedInvite = it }
                                            .onFailure { errorMessage = it.localizedMessage ?: "Échec réseau" }
                                    }
                                }
                            ) { Text("Réessayer", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
