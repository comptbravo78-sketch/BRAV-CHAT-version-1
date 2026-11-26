package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.StatusPost
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravGoldHairlineDivider
import com.example.ui.components.BravLuxuryCard
import com.example.ui.components.BravPatternIntensity
import com.example.ui.components.ProfileCoverArtPreset
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.components.VerifiedBlueBadge
import com.example.ui.theme.BravChampagneGold
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRedSecondary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravVerifiedBlue
import com.example.ui.theme.BravWhite
import kotlin.math.max

enum class ProfilePhotoTarget(val aspectRatio: Float, val maxDimension: Int) {
    AVATAR(1f, 1024),
    COVER(2.25f, 1600)
}

data class PendingCropMedia(
    val target: ProfilePhotoTarget,
    val bitmap: Bitmap
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileEditorialHeroCard(
    displayName: String,
    username: String,
    avatarUrl: String,
    coverUrl: String,
    availabilityStatus: String,
    locationSummary: String,
    bio: String,
    isVerified: Boolean = false,
    verifiedOrgName: String = "",
    verificationCategory: String = "",
    isUploadingAvatar: Boolean,
    isUploadingCover: Boolean,
    onPickAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    coverPreset: ProfileCoverArtPreset = ProfileCoverArtPreset.ROYAL_HERITAGE,
    postsCount: Int = 0,
    likesCount: Int = 0,
    contactsCount: Int = 0,
    followersCount: Int = 0,
    followingCount: Int = 0,
    onSelectCoverPreset: ((ProfileCoverArtPreset) -> Unit)? = null,
    isOwner: Boolean = true,
    onOpenAvatarViewer: (() -> Unit)? = null,
    onOpenCoverViewer: (() -> Unit)? = null,
    isPrivateProfile: Boolean = false,
    pendingFollowRequestsCount: Int = 0,
    onFollowersClick: (() -> Unit)? = null,
    onFollowingClick: (() -> Unit)? = null
) {
    val lux = BravTheme.colors
    val cleanName = displayName.ifBlank { "Membre BRAV" }
    var showAvatarMenu by remember { mutableStateOf(false) }
    var showCoverMenu by remember { mutableStateOf(false) }

    BravLuxuryCard(
        shapeRadius = 22.dp,
        accentBorder = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_editorial_hero")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Panoramic cover artwork (custom photo OR African Heritage geometric cover)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.25f)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(coverPreset.topColor, coverPreset.bottomColor)
                        )
                    )
                    .combinedClickable(
                        onClick = {
                            if (coverUrl.isNotBlank()) onOpenCoverViewer?.invoke()
                        },
                        onLongClick = {
                            if (isOwner) showCoverMenu = true
                        }
                    )
                    .testTag("profile_cover_container")
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "Photo de couverture du profil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("profile_cover_image")
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.16f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.28f)
                                    )
                                )
                            )
                    )
                } else {
                    BravAfricanPatternCanvas(
                        pattern = coverPreset.pattern,
                        primaryColor = coverPreset.accentPrimary,
                        secondaryColor = coverPreset.accentSecondary,
                        opacity = 0.58f,
                        scale = 1.05f,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.08f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.25f)
                                    )
                                )
                            )
                    )
                    if (isOwner) {
                        Surface(
                            onClick = onPickCover,
                            shape = RoundedCornerShape(999.dp),
                            color = lux.translucentCardSurface,
                            border = BorderStroke(0.75.dp, BravChampagneGold.copy(alpha = 0.72f)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .testTag("profile_empty_add_cover_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.AddPhotoAlternate,
                                    contentDescription = "Ajouter une couverture",
                                    tint = lux.titlePurple,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Ajouter photo",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = lux.titlePurple
                                )
                            }
                        }
                    }
                }

                // Cover management discreet pill (top-right, owner only; deletion is inside long-press menu)
                if (isOwner) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    ) {
                        Surface(
                            onClick = { if (!isUploadingCover) onPickCover() },
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.testTag("change_cover_photo_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isUploadingCover) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        color = BravWhite,
                                        strokeWidth = 1.8.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.PhotoCamera,
                                        contentDescription = "Modifier la couverture",
                                        tint = BravWhite,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = if (coverUrl.isBlank()) "Couverture" else "Modifier",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BravWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showCoverMenu,
                            onDismissRequest = { showCoverMenu = false },
                            containerColor = lux.modalSurface,
                            modifier = Modifier.testTag("profile_cover_context_menu")
                        ) {
                            DropdownMenuItem(
                                text = { Text("Modifier la couverture") },
                                leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
                                onClick = {
                                    showCoverMenu = false
                                    if (!isUploadingCover) onPickCover()
                                },
                                modifier = Modifier.testTag("cover_menu_change_item")
                            )
                            if (coverUrl.isNotBlank() && onOpenCoverViewer != null) {
                                DropdownMenuItem(
                                    text = { Text("Voir en grand") },
                                    leadingIcon = { Icon(Icons.Rounded.Visibility, contentDescription = null) },
                                    onClick = {
                                        showCoverMenu = false
                                        onOpenCoverViewer()
                                    },
                                    modifier = Modifier.testTag("cover_menu_view_item")
                                )
                            }
                            if (coverUrl.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Supprimer la couverture", color = BravRedSecondary) },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = BravRedSecondary) },
                                    onClick = {
                                        showCoverMenu = false
                                        if (!isUploadingCover) onRemoveCover()
                                    },
                                    modifier = Modifier.testTag("cover_menu_remove_item")
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Annuler") },
                                leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                                onClick = { showCoverMenu = false },
                                modifier = Modifier.testTag("cover_menu_cancel_item")
                            )
                        }
                    }
                }
            }

            // Centered overlapping avatar (tap to view fullscreen, long-press for owner context menu)
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.offset(y = (-44).dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(lux.cardSurface)
                        .border(2.dp, lux.champagneGold.copy(alpha = 0.85f), CircleShape)
                        .combinedClickable(
                            onClick = {
                                if (avatarUrl.isNotBlank()) onOpenAvatarViewer?.invoke()
                            },
                            onLongClick = {
                                if (isOwner) showAvatarMenu = true
                            }
                        )
                        .padding(4.dp)
                        .testTag("profile_avatar_container")
                ) {
                    UserAvatarWithStatus(
                        name = cleanName,
                        avatarUrl = avatarUrl,
                        availabilityStatus = availabilityStatus,
                        size = 90.dp
                    )
                }

                if (isOwner) {
                    Surface(
                        shape = CircleShape,
                        color = BravPurplePrimary,
                        onClick = { if (!isUploadingAvatar) onPickAvatar() },
                        modifier = Modifier
                            .size(32.dp)
                            .border(2.dp, lux.cardSurface, CircleShape)
                            .testTag("change_profile_photo_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isUploadingAvatar) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = BravWhite,
                                    strokeWidth = 1.8.dp
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.PhotoCamera,
                                    contentDescription = "Choisir une photo de profil",
                                    tint = BravWhite,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = showAvatarMenu,
                        onDismissRequest = { showAvatarMenu = false },
                        containerColor = lux.modalSurface,
                        modifier = Modifier.testTag("profile_avatar_context_menu")
                    ) {
                        DropdownMenuItem(
                            text = { Text("Changer la photo de profil") },
                            leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
                            onClick = {
                                showAvatarMenu = false
                                if (!isUploadingAvatar) onPickAvatar()
                            },
                            modifier = Modifier.testTag("avatar_menu_change_item")
                        )
                        if (avatarUrl.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Supprimer la photo de profil", color = BravRedSecondary) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = BravRedSecondary) },
                                onClick = {
                                    showAvatarMenu = false
                                    if (!isUploadingAvatar) onRemoveAvatar()
                                },
                                modifier = Modifier.testTag("avatar_menu_remove_item")
                            )
                        }
                        if (avatarUrl.isNotBlank() && onOpenAvatarViewer != null) {
                            DropdownMenuItem(
                                text = { Text("Voir en grand") },
                                leadingIcon = { Icon(Icons.Rounded.Visibility, contentDescription = null) },
                                onClick = {
                                    showAvatarMenu = false
                                    onOpenAvatarViewer()
                                },
                                modifier = Modifier.testTag("avatar_menu_view_item")
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Annuler") },
                            leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                            onClick = { showAvatarMenu = false },
                            modifier = Modifier.testTag("avatar_menu_cancel_item")
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-28).dp)
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = cleanName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = lux.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isVerified) {
                        Spacer(Modifier.width(7.dp))
                        VerifiedBlueBadge(
                            size = 20.dp,
                            showTooltipOnTap = true,
                            verifiedOrgName = verifiedOrgName,
                            verificationCategory = verificationCategory
                        )
                    }
                }

                if (isVerified && (verificationCategory.isNotBlank() || verifiedOrgName.isNotBlank())) {
                    Text(
                        text = listOf(verificationCategory, verifiedOrgName).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BravVerifiedBlue,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (username.isNotBlank() || locationSummary.isNotBlank()) {
                    Text(
                        text = listOfNotNull(
                            username.takeIf { it.isNotBlank() }?.let { "@$it" },
                            locationSummary.takeIf { it.isNotBlank() }
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = lux.inkSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isPrivateProfile) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = lux.translucentSubtleSurface,
                        border = BorderStroke(0.75.dp, lux.violetContour),
                        modifier = Modifier.testTag("profile_private_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = lux.titlePurple,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Compte privé",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = lux.titlePurple
                            )
                        }
                    }
                }

                if (bio.isNotBlank()) {
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = lux.inkPrimary.copy(alpha = 0.88f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))
                BravGoldHairlineDivider(withGoldTouch = true)
                Spacer(Modifier.height(4.dp))

                // Stats Row (Publications, Abonnés, Abonnements, J'aime)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileStatCell(
                        value = followersCount.toString(),
                        label = "Abonnés",
                        badgeCount = pendingFollowRequestsCount,
                        onClick = onFollowersClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_followers_stat")
                    )
                    Box(modifier = Modifier.width(1.dp).height(28.dp).background(lux.hairline))
                    ProfileStatCell(
                        value = postsCount.toString(),
                        label = "Publications",
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.width(1.dp).height(28.dp).background(lux.hairline))
                    ProfileStatCell(
                        value = followingCount.toString(),
                        label = "Abonnements",
                        onClick = onFollowingClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_following_stat")
                    )
                    Box(modifier = Modifier.width(1.dp).height(28.dp).background(lux.hairline))
                    ProfileStatCell(
                        value = likesCount.toString(),
                        label = "J'aime",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Cover Preset Selector when using Heritage Cover Art (owner only)
                if (isOwner && coverUrl.isBlank() && onSelectCoverPreset != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = lux.champagneGold,
                            modifier = Modifier.size(15.dp)
                        )
                        ProfileCoverArtPreset.entries.forEach { preset ->
                            val selected = preset == coverPreset
                            Surface(
                                onClick = { onSelectCoverPreset(preset) },
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) BravPurplePrimary.copy(alpha = 0.14f) else lux.softGray,
                                border = BorderStroke(
                                    0.75.dp,
                                    if (selected) lux.champagneGold else lux.hairline
                                ),
                                modifier = Modifier.testTag("profile_cover_preset_${preset.id}")
                            ) {
                                Text(
                                    text = preset.labelFr,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) lux.titlePurple else lux.inkSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatCell(
    value: String,
    label: String,
    badgeCount: Int = 0,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lux = BravTheme.colors
    Column(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClick)
                        .padding(vertical = 4.dp)
                } else {
                    Modifier.padding(vertical = 4.dp)
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = lux.inkPrimary
            )
            if (badgeCount > 0) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = BravRedSecondary
                ) {
                    Text(
                        text = "+$badgeCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = BravWhite,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (onClick != null) lux.titlePurple else lux.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ProfileImageCropDialog(
    pending: PendingCropMedia,
    isProcessing: Boolean,
    onConfirmCrop: (zoom: Float, panX: Float, panY: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var zoom by remember(pending.bitmap) { mutableFloatStateOf(1f) }
    var panX by remember(pending.bitmap) { mutableFloatStateOf(0f) }
    var panY by remember(pending.bitmap) { mutableFloatStateOf(0f) }
    val imageBitmap = remember(pending.bitmap) { pending.bitmap.asImageBitmap() }

    val isCover = pending.target == ProfilePhotoTarget.COVER
    val titleText = if (isCover) "Recadrer la couverture" else "Recadrer la photo de profil"

    val lux = BravTheme.colors
    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        containerColor = lux.modalSurface,
        title = {
            Text(titleText, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Faites glisser l'image et ajustez le zoom pour obtenir le cadrage souhaité.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val frameShape = if (isCover) RoundedCornerShape(16.dp) else CircleShape
                BoxWithConstraints(
                    modifier = Modifier
                        .then(
                            if (isCover) Modifier.fillMaxWidth().aspectRatio(pending.target.aspectRatio)
                            else Modifier.size(210.dp).aspectRatio(1f)
                        )
                        .clip(frameShape)
                        .background(lux.translucentSubtleSurface)
                        .border(2.dp, BravPurplePrimary, frameShape)
                        .testTag("profile_crop_viewport")
                ) {
                    val density = LocalDensity.current
                    val boxW = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                    val boxH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                    val bmpW = pending.bitmap.width.toFloat().coerceAtLeast(1f)
                    val bmpH = pending.bitmap.height.toFloat().coerceAtLeast(1f)
                    val baseScale = max(boxW / bmpW, boxH / bmpH)
                    val dispW = bmpW * baseScale * zoom
                    val dispH = bmpH * baseScale * zoom
                    val maxShiftX = ((dispW - boxW) / 2f).coerceAtLeast(0f)
                    val maxShiftY = ((dispH - boxH) / 2f).coerceAtLeast(0f)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(zoom, maxShiftX, maxShiftY) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    if (maxShiftX > 1f) {
                                        panX = (panX + dragAmount.x / maxShiftX).coerceIn(-1f, 1f)
                                    }
                                    if (maxShiftY > 1f) {
                                        panY = (panY + dragAmount.y / maxShiftY).coerceIn(-1f, 1f)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = titleText,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = panX * maxShiftX
                                    translationY = panY * maxShiftY
                                }
                        )
                        // Subtle editorial framing grid
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val lineColor = Color.White.copy(alpha = 0.24f)
                            drawLine(lineColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeWidth = 1f)
                            drawLine(lineColor, Offset(2f * size.width / 3f, 0f), Offset(2f * size.width / 3f, size.height), strokeWidth = 1f)
                            drawLine(lineColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeWidth = 1f)
                            drawLine(lineColor, Offset(0f, 2f * size.height / 3f), Offset(size.width, 2f * size.height / 3f), strokeWidth = 1f)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom", tint = BravPurplePrimary, modifier = Modifier.size(18.dp))
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        valueRange = 1f..3f,
                        colors = SliderDefaults.colors(thumbColor = BravPurplePrimary, activeTrackColor = BravPurplePrimary),
                        modifier = Modifier.weight(1f).testTag("profile_crop_zoom_slider")
                    )
                    TextButton(
                        onClick = {
                            zoom = 1f
                            panX = 0f
                            panY = 0f
                        }
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Réinitialiser le cadrage", modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                onClick = { onConfirmCrop(zoom, panX, panY) },
                modifier = Modifier.testTag(
                    if (isCover) "confirm_cover_upload_button" else "confirm_photo_upload_button"
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BravWhite, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (isCover) "Appliquer la couverture" else "Valider la photo")
            }
        },
        dismissButton = {
            TextButton(enabled = !isProcessing, onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun ProfileActusSummaryCard(
    myPosts: List<StatusPost>
) {
    val lux = BravTheme.colors
    BravLuxuryCard(
        shapeRadius = 20.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_actus_summary_card")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Publications Actus",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = lux.inkPrimary
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = BravPurplePrimary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${myPosts.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = lux.titlePurple,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            if (myPosts.isEmpty()) {
                Text(
                    text = "Aucune publication récente sur Actus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = lux.inkSecondary
                )
            } else {
                myPosts.take(2).forEach { post ->
                    val excerpt = post.effectiveText().ifBlank {
                        when (post.type) {
                            "video" -> "Publication vidéo"
                            "image" -> "Publication photo"
                            else -> "Statut partagé"
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = lux.softGray,
                        border = BorderStroke(0.5.dp, lux.hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = excerpt,
                                style = MaterialTheme.typography.bodySmall,
                                color = lux.inkPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${post.likedByUids.size} j'aime • ${post.commentCount} commentaires",
                                style = MaterialTheme.typography.labelSmall,
                                color = lux.inkSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
