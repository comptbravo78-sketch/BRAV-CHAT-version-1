package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.ContentModerationGuard
import com.example.model.VerificationCategory
import com.example.ui.components.BravLuxuryCard
import com.example.ui.components.BravLuxuryHeader
import com.example.ui.components.BravPatternIntensity
import com.example.ui.components.BravPatternType
import com.example.ui.components.FullscreenProfileImageViewer
import com.example.ui.components.ProfileCoverArtPreset
import com.example.ui.components.UserPublicationsSection
import com.example.ui.components.bravAfricanPattern
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRedSecondary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import com.example.util.ImageCompressor
import com.example.viewmodel.BravViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: BravViewModel,
    onOpenSettings: () -> Unit,
    onOpenConversation: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val appPrefs by viewModel.appPrefs.collectAsStateWithLifecycle()
    val allStatuses by viewModel.statuses.collectAsStateWithLifecycle()
    val savedContacts by viewModel.savedContactUsers.collectAsStateWithLifecycle()
    val certEligibility by viewModel.certificationEligibility.collectAsStateWithLifecycle()
    val followerUsers by viewModel.followerUsers.collectAsStateWithLifecycle()
    val followingUsers by viewModel.followingUsers.collectAsStateWithLifecycle()
    val incomingFollowRequests by viewModel.incomingFollowRequests.collectAsStateWithLifecycle()
    val outgoingFollowRequests by viewModel.outgoingFollowRequests.collectAsStateWithLifecycle()
    val myInvites by viewModel.myInvites.collectAsStateWithLifecycle()

    val firebaseUserMeta = remember(viewModel.currentUserId) {
        val u = runCatching { Firebase.auth.currentUser }.getOrNull()
        Triple(u?.email.orEmpty(), u?.displayName.orEmpty(), u?.photoUrl?.toString().orEmpty())
    }
    val firebaseDisplayName = firebaseUserMeta.second

    val isDesignerAccount = remember(
        currentUser?.userId,
        currentUser?.verifiedOrgName,
        certEligibility?.exemptDesigner,
        firebaseUserMeta.first
    ) {
        viewModel.userRepository.isVerifiedDesignerAccount() ||
            certEligibility?.exemptDesigner == true ||
            (currentUser?.isVerifiedActive() == true &&
                currentUser?.verifiedOrgName?.contains("Concepteur", ignoreCase = true) == true)
    }
    val isVerifiedActive = isDesignerAccount || currentUser?.isVerifiedActive() == true
    val lux = BravTheme.colors

    val myPosts = remember(allStatuses, viewModel.currentUserId) {
        allStatuses.filter { it.userId == viewModel.currentUserId }
    }
    val myTotalLikes = remember(myPosts) { myPosts.sumOf { it.likedByUids.size } }
    val selectedCoverPreset = remember(appPrefs.profileCoverPresetId) {
        ProfileCoverArtPreset.fromId(appPrefs.profileCoverPresetId)
    }

    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isUploadingCover by remember { mutableStateOf(false) }
    var pendingCrop by remember { mutableStateOf<PendingCropMedia?>(null) }
    var showRemovePhotoConfirm by remember { mutableStateOf(false) }
    var showRemoveCoverConfirm by remember { mutableStateOf(false) }
    var fullscreenImage by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showFollowSheet by remember { mutableStateOf(false) }
    var followSheetTab by remember { mutableStateOf(FollowManagementTab.FOLLOWERS) }
    var showInviteSheet by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = ImageCompressor.decodePreviewBitmap(context, uri, maxDimension = 1400)
            if (bmp == null) {
                viewModel.showMessage("Impossible de lire l'image sélectionnée.")
                return@launch
            }
            pendingCrop = PendingCropMedia(target = ProfilePhotoTarget.AVATAR, bitmap = bmp)
        }
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = ImageCompressor.decodePreviewBitmap(context, uri, maxDimension = 1800)
            if (bmp == null) {
                viewModel.showMessage("Impossible de lire l'image de couverture.")
                return@launch
            }
            pendingCrop = PendingCropMedia(target = ProfilePhotoTarget.COVER, bitmap = bmp)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lux.ivoryCanvas)
            .bravAfricanPattern(
                pattern = BravPatternType.KENTE_DIAMOND,
                primaryColor = lux.watermarkGold,
                secondaryColor = lux.watermarkViolet,
                opacity = BravPatternIntensity.SUBTLE_WATERMARK
            )
    ) {
        BravLuxuryHeader(
            title = "Mon profil",
            showLogo = true,
            pattern = BravPatternType.HERITAGE_COVER,
            actions = {
                Surface(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(999.dp),
                    color = BravWhite.copy(alpha = 0.16f),
                    border = BorderStroke(0.75.dp, lux.champagneGold.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("profile_open_settings_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Ouvrir les paramètres",
                            tint = lux.headerOnColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Paramètres",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = lux.headerOnColor
                        )
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .testTag("profile_main_list"),
            contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero éditorial épuré (aucun bloc Certification ni bouton Supprimer permanent)
            item(key = "hero") {
                val city = currentUser?.locationCity.orEmpty()
                val details = currentUser?.locationDetails.orEmpty()
                val locationSummary = listOf(city, details).filter { it.isNotBlank() }.joinToString(" • ")
                val resolvedDisplayName = currentUser?.displayName.orEmpty().ifBlank { firebaseDisplayName }
                val resolvedVerifiedOrg = currentUser?.verifiedOrgName.orEmpty()
                    .ifBlank { if (isDesignerAccount) "Concepteur BRAV CHAT" else "" }
                val resolvedVerificationCategory = currentUser?.verificationCategory
                    ?.takeIf { it.isNotBlank() }
                    ?.let { currentUser?.verificationCategoryEnum?.labelFr.orEmpty() }
                    .orEmpty()
                    .ifBlank { if (isDesignerAccount) VerificationCategory.CREATOR.labelFr else "" }
                val avatarUrl = currentUser?.avatarUrl.orEmpty()
                val coverUrl = currentUser?.coverUrl.orEmpty()

                ProfileEditorialHeroCard(
                    displayName = resolvedDisplayName,
                    username = currentUser?.username.orEmpty(),
                    avatarUrl = avatarUrl,
                    coverUrl = coverUrl,
                    availabilityStatus = currentUser?.availabilityStatus ?: "online",
                    locationSummary = locationSummary,
                    bio = currentUser?.bio.orEmpty(),
                    isVerified = isVerifiedActive,
                    verifiedOrgName = resolvedVerifiedOrg,
                    verificationCategory = resolvedVerificationCategory,
                    isUploadingAvatar = isUploadingAvatar,
                    isUploadingCover = isUploadingCover,
                    onPickAvatar = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemoveAvatar = { showRemovePhotoConfirm = true },
                    onPickCover = {
                        coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemoveCover = { showRemoveCoverConfirm = true },
                    coverPreset = selectedCoverPreset,
                    postsCount = myPosts.size,
                    likesCount = myTotalLikes,
                    contactsCount = savedContacts.size,
                    followersCount = currentUser?.followersCount ?: 0,
                    followingCount = currentUser?.followingCount ?: 0,
                    onSelectCoverPreset = { preset ->
                        scope.launch { viewModel.prefsRepository.setProfileCoverPresetId(preset.id) }
                    },
                    isOwner = true,
                    onOpenAvatarViewer = {
                        if (avatarUrl.isNotBlank()) {
                            fullscreenImage = avatarUrl to "Photo de profil"
                        }
                    },
                    onOpenCoverViewer = {
                        if (coverUrl.isNotBlank()) {
                            fullscreenImage = coverUrl to "Photo de couverture"
                        }
                    },
                    isPrivateProfile = currentUser?.isPrivateProfile == true,
                    pendingFollowRequestsCount = incomingFollowRequests.size,
                    onFollowersClick = {
                        followSheetTab = if (incomingFollowRequests.isNotEmpty()) {
                            FollowManagementTab.INCOMING
                        } else {
                            FollowManagementTab.FOLLOWERS
                        }
                        showFollowSheet = true
                    },
                    onFollowingClick = {
                        followSheetTab = FollowManagementTab.FOLLOWING
                        showFollowSheet = true
                    }
                )
            }

            // Disponibilité visible
            item(key = "availability") {
                BravLuxuryCard(
                    shapeRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Disponibilité visible",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = lux.inkPrimary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "online" to "En ligne",
                                "busy" to "Occupé",
                                "dnd" to "Ne pas déranger",
                                "invisible" to "Invisible",
                                "offline" to "Hors ligne"
                            ).forEach { (key, label) ->
                                val selected = currentUser?.availabilityStatus == key
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        scope.launch {
                                            viewModel.userRepository.updateAvailabilityStatus(viewModel.currentUserId, key)
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BravPurplePrimary.copy(alpha = 0.14f),
                                        selectedLabelColor = lux.titlePurple
                                    ),
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }

            // Réseau & Invitations (abonnés, demandes, lien personnel)
            item(key = "network_invites") {
                BravLuxuryCard(
                    shapeRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Réseau & Invitations",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = lux.inkPrimary
                            )
                            if (incomingFollowRequests.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = BravRedSecondary
                                ) {
                                    Text(
                                        text = "${incomingFollowRequests.size} demande(s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BravWhite,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = {
                                    followSheetTab = if (incomingFollowRequests.isNotEmpty()) {
                                        FollowManagementTab.INCOMING
                                    } else {
                                        FollowManagementTab.FOLLOWERS
                                    }
                                    showFollowSheet = true
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = lux.translucentSubtleSurface,
                                border = BorderStroke(0.85.dp, lux.violetContour),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_manage_follows_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                                ) {
                                    Icon(Icons.Rounded.Group, contentDescription = null, tint = lux.titlePurple, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Abonnés & demandes",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = lux.titlePurple,
                                        maxLines = 1
                                    )
                                }
                            }
                            Surface(
                                onClick = { showInviteSheet = true },
                                shape = RoundedCornerShape(999.dp),
                                color = lux.champagneSoftTint,
                                border = BorderStroke(0.85.dp, lux.champagneGold),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_invite_friends_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                                ) {
                                    Icon(Icons.Rounded.Link, contentDescription = null, tint = lux.inkPrimary, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Inviter des amis",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = lux.inkPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // MES PUBLICATIONS (Grille / Liste, aperçu, modification et suppression)
            item(key = "my_publications") {
                UserPublicationsSection(
                    posts = myPosts,
                    isOwner = true,
                    viewerUid = viewModel.currentUserId,
                    canViewPosts = true,
                    sectionTitle = "MES PUBLICATIONS",
                    onEditPost = { post, newText ->
                        viewModel.updateStatusText(post, newText) {}
                    },
                    onDeletePost = { post ->
                        scope.launch {
                            viewModel.socialRepository.deleteStatus(post.id)
                            viewModel.showMessage("Publication supprimée")
                        }
                    }
                )
            }
        }
    }

    fullscreenImage?.let { (url, title) ->
        FullscreenProfileImageViewer(
            imageUrl = url,
            title = title,
            onDismiss = { fullscreenImage = null }
        )
    }

    pendingCrop?.let { cropTask ->
        val isBusy = isUploadingAvatar || isUploadingCover
        ProfileImageCropDialog(
            pending = cropTask,
            isProcessing = isBusy,
            onDismiss = {
                cropTask.bitmap.recycle()
                pendingCrop = null
            },
            onConfirmCrop = { zoom, panX, panY ->
                val uid = viewModel.currentUserId
                val isCover = cropTask.target == ProfilePhotoTarget.COVER
                if (isCover) isUploadingCover = true else isUploadingAvatar = true
                scope.launch {
                    val compressed = ImageCompressor.cropAndCompressBitmap(
                        source = cropTask.bitmap,
                        targetAspectRatio = cropTask.target.aspectRatio,
                        zoom = zoom,
                        panX = panX,
                        panY = panY,
                        maxDimension = cropTask.target.maxDimension,
                        targetMaxBytes = if (isCover) 850_000 else 450_000
                    )
                    if (compressed == null || compressed.first.isEmpty()) {
                        isUploadingAvatar = false
                        isUploadingCover = false
                        viewModel.showMessage("Impossible de recadrer cette image.")
                        return@launch
                    }
                    val moderation = ContentModerationGuard.checkImageBytes(compressed.first)
                    if (!moderation.allowed) {
                        isUploadingAvatar = false
                        isUploadingCover = false
                        cropTask.bitmap.recycle()
                        pendingCrop = null
                        viewModel.showMessage(moderation.userMessage)
                        return@launch
                    }

                    val fileName = if (isCover) {
                        "cover_${uid}_${System.currentTimeMillis()}.jpg"
                    } else {
                        "avatar_${uid}_${System.currentTimeMillis()}.jpg"
                    }
                    val up = viewModel.workerApiClient.uploadMedia(
                        conversationId = "profile_$uid",
                        bytes = compressed.first,
                        mimeType = compressed.second,
                        fileName = fileName
                    )
                    up.onSuccess { res ->
                        val updateRes = viewModel.userRepository.updateProfile(
                            uid = uid,
                            displayName = currentUser?.displayName.orEmpty().ifBlank { firebaseDisplayName.ifBlank { "Membre BRAV" } },
                            username = currentUser?.username.orEmpty(),
                            bio = currentUser?.bio.orEmpty(),
                            locationCity = currentUser?.locationCity.orEmpty(),
                            locationDetails = currentUser?.locationDetails.orEmpty(),
                            avatarUrl = if (isCover) null else res.url,
                            coverUrl = if (isCover) res.url else null
                        )
                        isUploadingAvatar = false
                        isUploadingCover = false
                        cropTask.bitmap.recycle()
                        pendingCrop = null
                        if (updateRes.isSuccess) {
                            viewModel.showMessage(
                                if (isCover) "Photo de couverture mise à jour" else "Photo de profil mise à jour"
                            )
                        } else {
                            viewModel.showMessage(updateRes.exceptionOrNull()?.localizedMessage ?: "Échec sauvegarde image")
                        }
                    }.onFailure { err ->
                        isUploadingAvatar = false
                        isUploadingCover = false
                        cropTask.bitmap.recycle()
                        pendingCrop = null
                        viewModel.showMessage(err.localizedMessage ?: "Échec upload image")
                    }
                }
            }
        )
    }

    if (showRemovePhotoConfirm) {
        AlertDialog(
            onDismissRequest = { showRemovePhotoConfirm = false },
            containerColor = lux.modalSurface,
            modifier = Modifier.testTag("confirm_remove_avatar_dialog"),
            title = { Text("Supprimer la photo de profil ?") },
            text = { Text("Votre photo de profil sera remplacée par vos initiales.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = BravRedSecondary, contentColor = BravWhite),
                    onClick = {
                        showRemovePhotoConfirm = false
                        viewModel.removeProfilePhoto()
                    },
                    modifier = Modifier.testTag("confirm_remove_avatar_button")
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showRemovePhotoConfirm = false }) { Text("Annuler") }
            }
        )
    }

    if (showRemoveCoverConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveCoverConfirm = false },
            containerColor = lux.modalSurface,
            modifier = Modifier.testTag("confirm_remove_cover_dialog"),
            title = { Text("Supprimer la photo de couverture ?") },
            text = { Text("La couverture reviendra au motif africain sélectionné.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = BravRedSecondary, contentColor = BravWhite),
                    onClick = {
                        showRemoveCoverConfirm = false
                        viewModel.removeCoverPhoto()
                    },
                    modifier = Modifier.testTag("confirm_remove_cover_button")
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveCoverConfirm = false }) { Text("Annuler") }
            }
        )
    }

    if (showFollowSheet) {
        FollowManagementSheet(
            followers = followerUsers,
            following = followingUsers,
            incomingRequests = incomingFollowRequests,
            outgoingRequests = outgoingFollowRequests,
            initialTab = followSheetTab,
            onMessageUser = { target ->
                scope.launch {
                    viewModel.openOrCreateDirectChat(target).onSuccess { conv ->
                        showFollowSheet = false
                        onOpenConversation(conv.id)
                    }
                }
            },
            onRemoveFollower = { follower ->
                viewModel.removeFollower(follower.userId, follower.displayName)
            },
            onUnfollowUser = { target ->
                viewModel.toggleFollowUser(target)
            },
            onBlockUser = { target ->
                viewModel.blockUser(target.userId)
            },
            onAcceptRequest = { req -> viewModel.acceptFollowRequest(req) },
            onDeclineRequest = { req -> viewModel.declineFollowRequest(req) },
            onCancelOutgoingRequest = { req -> viewModel.cancelOutgoingFollowRequest(req.toUid) },
            onDismiss = { showFollowSheet = false }
        )
    }

    if (showInviteSheet) {
        InviteShareSheet(
            myInvites = myInvites,
            onEnsureInviteLink = { viewModel.createOrGetPersonalInvite("profile") },
            onFeedback = { msg -> viewModel.showMessage(msg) },
            onDismiss = { showInviteSheet = false }
        )
    }
}
