package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.FollowRelationState
import com.example.model.StatusPost
import com.example.model.User
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravGoldHairlineDivider
import com.example.ui.components.BravPatternType
import com.example.ui.components.FullscreenProfileImageViewer
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.components.UserPublicationsSection
import com.example.ui.components.VerifiedBlueBadge
import com.example.ui.theme.BravChampagneGold
import com.example.ui.theme.BravPurpleDeep
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import com.example.ui.theme.CallActionGreen
import com.example.util.WhatsAppLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendProfileModal(
    user: User,
    userPosts: List<StatusPost>,
    myUid: String,
    isContact: Boolean = false,
    isOnline: Boolean = false,
    isFollowing: Boolean = false,
    followRelationState: FollowRelationState? = null,
    onToggleFollow: ((User) -> Unit)? = null,
    onSendMessage: (User) -> Unit = {},
    onStartAudioCall: ((User) -> Unit)? = null,
    onOpenPostComments: ((StatusPost) -> Unit)? = null,
    onSharePost: ((StatusPost) -> Unit)? = null,
    onEditOwnPost: ((StatusPost, String) -> Unit)? = null,
    onDeleteOwnPost: ((StatusPost) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val lux = BravTheme.colors
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isOwner = user.userId == myUid
    val effectiveFollowState = followRelationState ?: when {
        isOwner -> FollowRelationState.SELF
        isFollowing -> FollowRelationState.FOLLOWING
        else -> FollowRelationState.NOT_FOLLOWING
    }

    val showPhoto = user.isFieldVisibleTo("showPhoto", isContact = isContact, isOwner = isOwner)
    val showCover = user.isFieldVisibleTo("showCover", isContact = isContact, isOwner = isOwner)
    val showInfo = user.isFieldVisibleTo("showInfo", isContact = isContact, isOwner = isOwner)
    val showCity = user.isFieldVisibleTo("showCity", isContact = isContact, isOwner = isOwner)
    val showBirthDate = user.isFieldVisibleTo("showBirthDate", isContact = isContact, isOwner = isOwner)
    val showAddress = user.isFieldVisibleTo("showAddress", isContact = isContact, isOwner = isOwner)
    val showPhone = user.isFieldVisibleTo("showPhone", isContact = isContact, isOwner = isOwner)
    val showEmail = user.isFieldVisibleTo("showEmail", isContact = isContact, isOwner = isOwner)
    val canSeePrivatePosts = !user.isPrivateProfile || isOwner || isContact || effectiveFollowState == FollowRelationState.FOLLOWING
    val showPosts = user.isFieldVisibleTo("showPosts", isContact = isContact, isOwner = isOwner) && canSeePrivatePosts
    val showStatus = user.isFieldVisibleTo("showStatus", isContact = isContact, isOwner = isOwner)
    val canCall = onStartAudioCall != null && user.isFieldVisibleTo("whoCanCall", isContact = isContact, isOwner = isOwner)

    val effectiveAvatar = if (showPhoto) user.avatarUrl else ""
    val effectiveCover = if (showCover) user.coverUrl else ""
    val visiblePhone = if (showPhone) user.publicPhone.ifBlank { user.phoneMasked.takeIf { !it.contains("•") }.orEmpty() } else ""
    val visiblePosts = remember(userPosts, showPosts, isOwner, myUid) {
        if (!showPosts && !isOwner) emptyList()
        else userPosts.filter { isOwner || it.audienceType != "selected" || myUid in it.allowedUids }
    }
    val totalLikes = remember(visiblePosts) { visiblePosts.sumOf { it.likedByUids.size } }

    var fullscreenViewer by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showWhatsAppConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = lux.modalSurface,
        modifier = Modifier.testTag("friend_profile_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cover Banner (tap to view fullscreen when visible)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.25f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(BravPurpleDeep, BravPurplePrimary)))
                    .then(
                        if (effectiveCover.isNotBlank()) {
                            Modifier.clickable {
                                fullscreenViewer = effectiveCover to "Couverture de ${user.displayName.ifBlank { "Membre" }}"
                            }
                        } else Modifier
                    )
                    .testTag("friend_profile_cover")
            ) {
                if (effectiveCover.isNotBlank()) {
                    AsyncImage(
                        model = effectiveCover,
                        contentDescription = "Photo de couverture",
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
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(48.dp)
                        .testTag("friend_profile_close_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Fermer le profil", tint = BravWhite)
                }
            }

            // Overlapping Avatar (tap to view fullscreen when visible)
            Box(
                modifier = Modifier
                    .offset(y = (-42).dp)
                    .size(94.dp)
                    .clip(CircleShape)
                    .background(lux.modalSurface)
                    .border(2.dp, lux.champagneGold, CircleShape)
                    .padding(4.dp)
                    .then(
                        if (effectiveAvatar.isNotBlank()) {
                            Modifier.clickable {
                                fullscreenViewer = effectiveAvatar to "Photo de ${user.displayName.ifBlank { "Membre" }}"
                            }
                        } else Modifier
                    )
                    .testTag("friend_profile_avatar")
            ) {
                UserAvatarWithStatus(
                    name = user.displayName,
                    avatarUrl = effectiveAvatar,
                    availabilityStatus = if (showStatus) user.availabilityStatus else "offline",
                    isOnlineRealtime = if (showStatus) isOnline else false,
                    size = 86.dp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-28).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = user.displayName.ifBlank { "Membre BRAV" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = lux.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (user.isVerifiedActive()) {
                        Spacer(Modifier.width(7.dp))
                        VerifiedBlueBadge(
                            size = 20.dp,
                            showTooltipOnTap = true,
                            verifiedOrgName = user.verifiedOrgName,
                            verificationCategory = user.verificationCategoryEnum.labelFr
                        )
                    }
                }

                if (user.username.isNotBlank()) {
                    Text(
                        text = "@${user.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = lux.titlePurple,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (user.isPrivateProfile && !isOwner) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = lux.translucentSubtleSurface,
                        border = BorderStroke(0.75.dp, lux.violetContour),
                        modifier = Modifier.testTag("friend_profile_private_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = lux.titlePurple, modifier = Modifier.size(13.dp))
                            Text(
                                text = "Compte privé",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = lux.titlePurple
                            )
                        }
                    }
                }

                if (showInfo && user.bio.isNotBlank()) {
                    Text(
                        text = user.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = lux.inkPrimary.copy(alpha = 0.88f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp).testTag("friend_profile_bio")
                    )
                }

                // Visible Public Info Chips
                val cityText = if (showCity) listOf(user.locationCity, user.locationDetails).filter { it.isNotBlank() }.joinToString(" • ") else ""
                val visibleEmail = if (showEmail) user.publicEmail else ""
                val visibleBirth = if (showBirthDate) user.publicBirthDate else ""
                val visibleAddress = if (showAddress) user.publicAddress else ""

                val infoRows = listOfNotNull(
                    cityText.takeIf { it.isNotBlank() }?.let { Triple(Icons.Rounded.LocationOn, it, "friend_profile_city") },
                    visiblePhone.takeIf { it.isNotBlank() }?.let { Triple(Icons.Rounded.Phone, it, "friend_profile_phone") },
                    visibleEmail.takeIf { it.isNotBlank() }?.let { Triple(Icons.Rounded.Email, it, "friend_profile_email") },
                    visibleBirth.takeIf { it.isNotBlank() }?.let { Triple(Icons.Rounded.Cake, it, "friend_profile_birthdate") },
                    visibleAddress.takeIf { it.isNotBlank() }?.let { Triple(Icons.Rounded.Home, it, "friend_profile_address") }
                )

                if (infoRows.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(lux.translucentSubtleSurface, RoundedCornerShape(14.dp))
                            .border(0.65.dp, lux.hairline, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        infoRows.forEach { (icon, label, tag) ->
                            FriendInfoLine(icon = icon, value = label, tag = tag)
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                BravGoldHairlineDivider(withGoldTouch = true)

                // Stats
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(lux.translucentSubtleSurface, RoundedCornerShape(14.dp))
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FriendStatItem(value = "${user.followersCount}", label = "Abonnés")
                    FriendStatItem(value = "${visiblePosts.size}", label = "Publications")
                    FriendStatItem(value = "${user.followingCount}", label = "Abonnements")
                    FriendStatItem(value = "$totalLikes", label = "J'aime")
                }

                // Actions for visitors only
                if (!isOwner) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSendMessage(user) },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("friend_profile_message_button")
                        ) {
                            Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Message", fontWeight = FontWeight.SemiBold)
                        }
                        if (canCall) {
                            OutlinedButton(
                                onClick = { onStartAudioCall?.invoke(user) },
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, CallActionGreen.copy(alpha = 0.7f)),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("friend_profile_call_button")
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = null, tint = CallActionGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Appeler", color = CallActionGreen, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onToggleFollow != null) {
                            FollowActionButton(
                                relationState = effectiveFollowState,
                                peerName = user.displayName,
                                onToggle = { onToggleFollow(user) },
                                modifier = Modifier.weight(1f),
                                testTagName = "friend_profile_follow_button"
                            )
                        }
                        val waTarget = visiblePhone.ifBlank { user.phoneMasked }
                        if (showPhone && WhatsAppLauncher.hasValidPhoneDigits(waTarget)) {
                            OutlinedButton(
                                onClick = { showWhatsAppConfirm = true },
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("friend_profile_whatsapp_button")
                            ) {
                                Text("WhatsApp", fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                UserPublicationsSection(
                    posts = userPosts,
                    isOwner = isOwner,
                    viewerUid = myUid,
                    canViewPosts = showPosts,
                    sectionTitle = if (isOwner) "MES PUBLICATIONS" else "PUBLICATIONS",
                    onOpenComments = onOpenPostComments,
                    onSharePost = onSharePost,
                    onEditPost = if (isOwner) onEditOwnPost else null,
                    onDeletePost = if (isOwner) onDeleteOwnPost else null
                )
            }
        }
    }

    if (showWhatsAppConfirm) {
        val waTarget = visiblePhone.ifBlank { user.phoneMasked }
        AlertDialog(
            onDismissRequest = { showWhatsAppConfirm = false },
            containerColor = lux.modalSurface,
            title = { Text("Contacter sur WhatsApp") },
            text = { Text("Quitter BRAV CHAT pour ouvrir la discussion WhatsApp avec ${user.displayName.ifBlank { waTarget }} ?") },
            confirmButton = {
                Button(
                    onClick = {
                        showWhatsAppConfirm = false
                        WhatsAppLauncher.openWhatsAppChat(
                            context = context,
                            rawPhone = waTarget,
                            prefilledMessage = "Bonjour ${user.displayName}, je vous écris depuis BRAV CHAT."
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White)
                ) { Text("Ouvrir WhatsApp") }
            },
            dismissButton = {
                TextButton(onClick = { showWhatsAppConfirm = false }) { Text("Annuler") }
            }
        )
    }

    fullscreenViewer?.let { (url, title) ->
        FullscreenProfileImageViewer(
            imageUrl = url,
            title = title,
            onDismiss = { fullscreenViewer = null }
        )
    }
}

@Composable
private fun FriendInfoLine(
    icon: ImageVector,
    value: String,
    tag: String
) {
    val lux = BravTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    ) {
        Icon(icon, contentDescription = null, tint = lux.titlePurple, modifier = Modifier.size(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = lux.inkPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FriendStatItem(value: String, label: String) {
    val lux = BravTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = lux.inkPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = lux.inkSecondary)
    }
}
