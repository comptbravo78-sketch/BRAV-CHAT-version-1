package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.VerifiedBlueBadge
import com.example.util.WhatsAppLauncher
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ai.ContentModerationGuard
import com.example.model.OfferListing
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravGoldHairlineDivider
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.components.bravOutlinedTextFieldColors
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.ui.theme.StatusOnlineGreen
import com.example.util.ImageCompressor
import kotlinx.coroutines.launch

data class OfferDraftImage(
    val id: String,
    val remoteUrl: String? = null,
    val bytes: ByteArray? = null,
    val mimeType: String = "image/jpeg"
)

data class OfferComposeSubmission(
    val offerType: String,
    val title: String,
    val category: String,
    val description: String,
    val condition: String,
    val locationCity: String,
    val priceOrSalary: String,
    val contactInfo: String,
    val keptExistingUrls: List<String>,
    val newImagePayloads: List<Pair<ByteArray, String>>,
    val removedUrls: List<String>
)

@Composable
fun OfferMarketplaceCard(
    offer: OfferListing,
    isMine: Boolean,
    isAuthorVerified: Boolean = false,
    onOpenDetail: () -> Unit
) {
    val lux = BravTheme.colors
    val images = remember(offer.id, offer.imageUrl, offer.imageUrls) { offer.resolvedImageUrls() }
    val offerType = remember(offer.id, offer.offerType, offer.category) { offer.resolvedOfferType() }
    val isProduct = offerType == "product"

    Card(
        onClick = onOpenDetail,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = lux.cardSurface),
        border = BorderStroke(0.9.dp, lux.champagneGold.copy(alpha = 0.52f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (lux.isDark) 0.dp else 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("offer_card_${offer.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Hero image section (never distorted/stretched)
            if (images.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.65f)
                        .background(lux.translucentSubtleSurface)
                ) {
                    AsyncImage(
                        model = images.first(),
                        contentDescription = offer.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.62f)
                        ) {
                            Text(
                                text = if (isProduct) "Produit" else "Service",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        if (isProduct && offer.condition.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = lux.modalSurface,
                                border = BorderStroke(0.75.dp, lux.violetContour)
                            ) {
                                Text(
                                    text = offer.condition,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = lux.inkPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    if (images.size > 1) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.62f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.Collections, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Text(
                                    text = "${images.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BravPurple.copy(alpha = 0.11f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isProduct) Icons.Rounded.ShoppingBag else Icons.Rounded.Build,
                                    contentDescription = null,
                                    tint = BravPurple,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = offer.category.ifBlank { if (isProduct) "Vente" else "Services" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BravPurple,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (images.isEmpty() && isProduct && offer.condition.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = lux.translucentSubtleSurface,
                                border = BorderStroke(0.75.dp, lux.violetContour)
                            ) {
                                Text(
                                    text = offer.condition,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = lux.inkPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StatusOnlineGreen.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (isMine) "Mon annonce" else "Disponible",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusOnlineGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = offer.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (offer.priceOrSalary.isNotBlank()) {
                    Text(
                        text = offer.priceOrSalary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = BravPurple
                    )
                }

                if (offer.description.isNotBlank()) {
                    Text(
                        text = offer.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        UserAvatarWithStatus(
                            name = offer.authorName.ifBlank { "Annonceur" },
                            avatarUrl = offer.authorAvatar,
                            availabilityStatus = "invisible",
                            size = 28.dp
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = offer.authorName.ifBlank { "Annonceur BRAV" },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isAuthorVerified) {
                                    VerifiedBlueBadge(size = 14.dp)
                                }
                            }
                            Text(
                                text = offer.locationCity.ifBlank { "Zone flexible" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = "Voir la fiche",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = BravPurple
                    )
                }
            }
        }
    }
}

@Composable
fun OfferDetailDialog(
    offer: OfferListing,
    isMine: Boolean,
    isAuthorVerified: Boolean = false,
    onShowMessage: (String) -> Unit = {},
    onContactSeller: () -> Unit,
    onEditOffer: () -> Unit,
    onDeleteOffer: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showWhatsAppPrompt by remember { mutableStateOf(false) }
    val hasWhatsAppPhone = remember(offer.contactInfo) {
        WhatsAppLauncher.hasValidPhoneDigits(offer.contactInfo)
    }
    val images = remember(offer.id, offer.imageUrl, offer.imageUrls) { offer.resolvedImageUrls() }
    val isProduct = remember(offer.id, offer.offerType, offer.category) {
        offer.resolvedOfferType() == "product"
    }

    val lux = BravTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .testTag("offer_detail_dialog"),
            shape = RoundedCornerShape(26.dp),
            color = lux.modalSurface,
            border = BorderStroke(1.dp, lux.champagneGold.copy(alpha = 0.55f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                BravAfricanPatternCanvas(
                    pattern = BravPatternType.WAX_TEXTILE,
                    variant = BravPatternVariant.GOLD_ON_IVORY,
                    opacity = 0.095f,
                    scale = 1.05f
                )
            Column(modifier = Modifier.fillMaxSize()) {
                // Header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isProduct) "Fiche produit" else "Fiche service",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("offer_detail_close_button")) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (images.isNotEmpty()) {
                        val pagerState = rememberPagerState(pageCount = { images.size })
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.45f)
                                .background(lux.translucentSubtleSurface)
                                .testTag("offer_detail_gallery")
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                AsyncImage(
                                    model = images[page],
                                    contentDescription = "${offer.title} - photo ${page + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (images.size > 1) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.Black.copy(alpha = 0.62f),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "${pagerState.currentPage + 1} / ${images.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BravPurple.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = if (isProduct) "Vente • ${offer.category}" else "Service • ${offer.category}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BravPurple,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            if (isProduct && offer.condition.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = lux.translucentSubtleSurface,
                                    border = BorderStroke(0.75.dp, lux.violetContour)
                                ) {
                                    Text(
                                        text = offer.condition,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = lux.inkPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = offer.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        if (offer.priceOrSalary.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BravPurple.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = offer.priceOrSalary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BravPurple,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        if (offer.description.isNotBlank()) {
                            Text(
                                text = "Description",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = offer.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = lux.translucentCardSurface,
                            border = BorderStroke(0.8.dp, lux.violetContour),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = BravPurple, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = if (isProduct) {
                                            "Localisation : ${offer.locationCity.ifBlank { "Non précisée" }}"
                                        } else {
                                            "Zone d'intervention : ${offer.locationCity.ifBlank { "Flexible" }}"
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (offer.contactInfo.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Rounded.Phone, contentDescription = null, tint = BravPurple, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "Contact : ${offer.contactInfo}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        // Seller / Provider Identity
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = lux.translucentCardSurface,
                            border = BorderStroke(0.8.dp, lux.violetContour),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                UserAvatarWithStatus(
                                    name = offer.authorName.ifBlank { "Annonceur" },
                                    avatarUrl = offer.authorAvatar,
                                    availabilityStatus = "invisible",
                                    size = 44.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isProduct) "Vendeur" else "Prestataire",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = offer.authorName.ifBlank { "Membre BRAV" },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isAuthorVerified) {
                                            VerifiedBlueBadge(size = 16.dp)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // Bottom action bar
                Surface(
                    color = lux.translucentCardSurface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BravGoldHairlineDivider(withGoldTouch = true)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isMine) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDeleteOffer,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BravRed),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).testTag("offer_detail_delete_button")
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Supprimer")
                                }
                                Button(
                                    onClick = onEditOffer,
                                    colors = ButtonDefaults.buttonColors(containerColor = BravPurple, contentColor = Color.White),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).testTag("offer_detail_edit_button")
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Modifier")
                                }
                            }
                        } else {
                            Button(
                                onClick = onContactSeller,
                                colors = ButtonDefaults.buttonColors(containerColor = BravPurple, contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("offer_detail_contact_button")
                            ) {
                                Icon(Icons.Rounded.ChatBubble, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Contacter dans BRAV Chat", fontWeight = FontWeight.Bold)
                            }
                            if (hasWhatsAppPhone) {
                                OutlinedButton(
                                    onClick = { showWhatsAppPrompt = true },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF15803D)),
                                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("offer_detail_whatsapp_button")
                                ) {
                                    Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Contacter sur WhatsApp", fontWeight = FontWeight.SemiBold)
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

    if (showWhatsAppPrompt) {
        var prefill by remember(offer.id) {
            mutableStateOf("Bonjour, je vous contacte au sujet de votre annonce « ${offer.title} » sur BRAV CHAT.")
        }
        AlertDialog(
            onDismissRequest = { showWhatsAppPrompt = false },
            containerColor = lux.modalSurface,
            title = { Text("Contacter sur WhatsApp") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ouvrir la discussion WhatsApp avec ${offer.authorName.ifBlank { offer.contactInfo }} (${offer.contactInfo}).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = prefill,
                        onValueChange = { prefill = it },
                        label = { Text("Message pré-rempli (optionnel)") },
                        minLines = 2,
                        maxLines = 4,
                        colors = bravOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White),
                    onClick = {
                        showWhatsAppPrompt = false
                        val res = WhatsAppLauncher.openWhatsAppChat(
                            context = context,
                            rawPhone = offer.contactInfo,
                            prefilledMessage = prefill
                        )
                        if (res.isFailure) {
                            onShowMessage(res.exceptionOrNull()?.localizedMessage ?: "Impossible d'ouvrir WhatsApp")
                        }
                    },
                    modifier = Modifier.testTag("confirm_open_whatsapp_button")
                ) {
                    Text("Ouvrir WhatsApp")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWhatsAppPrompt = false }) { Text("Annuler") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OfferComposerDialog(
    editingOffer: OfferListing? = null,
    initialDraftImages: List<OfferDraftImage> = emptyList(),
    isSubmitting: Boolean = false,
    onSubmit: suspend (OfferComposeSubmission) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var offerType by remember(editingOffer?.id) {
        mutableStateOf(editingOffer?.resolvedOfferType() ?: "product")
    }
    var title by remember(editingOffer?.id) { mutableStateOf(editingOffer?.title.orEmpty()) }
    var category by remember(editingOffer?.id) {
        mutableStateOf(
            editingOffer?.category?.takeIf { it.isNotBlank() }
                ?: if (offerType == "product") "Vente" else "Services"
        )
    }
    var description by remember(editingOffer?.id) { mutableStateOf(editingOffer?.description.orEmpty()) }
    var condition by remember(editingOffer?.id) {
        mutableStateOf(editingOffer?.condition?.takeIf { it.isNotBlank() } ?: "Neuf")
    }
    var locationCity by remember(editingOffer?.id) { mutableStateOf(editingOffer?.locationCity.orEmpty()) }
    var priceOrSalary by remember(editingOffer?.id) { mutableStateOf(editingOffer?.priceOrSalary.orEmpty()) }
    var contactInfo by remember(editingOffer?.id) { mutableStateOf(editingOffer?.contactInfo.orEmpty()) }

    val initialExistingUrls = remember(editingOffer?.id) { editingOffer?.resolvedImageUrls().orEmpty() }
    var draftImages by remember(editingOffer?.id) {
        mutableStateOf(
            if (initialDraftImages.isNotEmpty()) {
                initialDraftImages
            } else {
                initialExistingUrls.mapIndexed { i, url ->
                    OfferDraftImage(id = "existing_$i", remoteUrl = url)
                }
            }
        )
    }
    var moderationError by remember { mutableStateOf<String?>(null) }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            moderationError = null
            val remainingSlots = (6 - draftImages.size).coerceAtLeast(0)
            val newItems = mutableListOf<OfferDraftImage>()
            for (uri in uris.take(remainingSlots)) {
                val compressed = ImageCompressor.compressForUpload(
                    context = context,
                    uri = uri,
                    maxDimension = 1400,
                    targetMaxBytes = 750_000
                ) ?: continue
                val verdict = ContentModerationGuard.checkImageBytes(compressed.first)
                if (!verdict.allowed) {
                    moderationError = verdict.userMessage
                    break
                }
                newItems.add(
                    OfferDraftImage(
                        id = "local_${System.nanoTime()}",
                        bytes = compressed.first,
                        mimeType = compressed.second
                    )
                )
            }
            if (newItems.isNotEmpty()) {
                draftImages = (draftImages + newItems).take(6)
            }
        }
    }

    val isProduct = offerType == "product"
    val productConditions = remember { listOf("Neuf", "Comme neuf", "Bon état", "Occasion") }
    val serviceCategories = remember { listOf("Services", "Emploi", "Immobilier") }

    val lux = BravTheme.colors
    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .testTag("offer_composer_dialog"),
            shape = RoundedCornerShape(26.dp),
            color = lux.modalSurface,
            border = BorderStroke(1.dp, lux.champagneGold.copy(alpha = 0.55f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                BravAfricanPatternCanvas(
                    pattern = BravPatternType.WAX_TEXTILE,
                    variant = BravPatternVariant.GOLD_ON_IVORY,
                    opacity = 0.095f,
                    scale = 1.05f
                )
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (editingOffer == null) "Nouvelle annonce" else "Modifier l'annonce",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { if (!isSubmitting) onDismiss() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Type d'annonce",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = isProduct,
                            onClick = {
                                offerType = "product"
                                if (category in serviceCategories) category = "Vente"
                            },
                            leadingIcon = { Icon(Icons.Rounded.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Vente de produit") },
                            modifier = Modifier.weight(1f).testTag("offer_type_product_chip")
                        )
                        FilterChip(
                            selected = !isProduct,
                            onClick = {
                                offerType = "service"
                                if (category == "Vente") category = "Services"
                            },
                            leadingIcon = { Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Service") },
                            modifier = Modifier.weight(1f).testTag("offer_type_service_chip")
                        )
                    }

                    // Photo gallery manager
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isProduct) "Photos du produit (${draftImages.size}/6)" else "Images de présentation (${draftImages.size}/6)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (draftImages.size < 6) {
                            TextButton(
                                onClick = {
                                    multiPhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("offer_add_photos_button")
                            ) {
                                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ajouter photos")
                            }
                        }
                    }

                    if (draftImages.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(draftImages, key = { _, img -> img.id }) { idx, img ->
                                Box(
                                    modifier = Modifier
                                        .size(width = 112.dp, height = 88.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            width = if (idx == 0) 2.dp else 1.dp,
                                            color = if (idx == 0) BravPurple else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    if (img.remoteUrl != null) {
                                        AsyncImage(
                                            model = img.remoteUrl,
                                            contentDescription = "Photo ${idx + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (img.bytes != null) {
                                        val bmp = remember(img.id) {
                                            runCatching {
                                                BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size)?.asImageBitmap()
                                            }.getOrNull()
                                        }
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp,
                                                contentDescription = "Photo ${idx + 1}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }

                                    // Set primary cover button
                                    if (idx > 0) {
                                        Surface(
                                            onClick = {
                                                val mutable = draftImages.toMutableList()
                                                val chosen = mutable.removeAt(idx)
                                                mutable.add(0, chosen)
                                                draftImages = mutable
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.Black.copy(alpha = 0.65f),
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(4.dp)
                                        ) {
                                            Text(
                                                text = "Principale",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = BravPurple,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Star,
                                                contentDescription = "Photo principale",
                                                tint = Color.White,
                                                modifier = Modifier.padding(3.dp).size(12.dp)
                                            )
                                        }
                                    }

                                    // Delete image button
                                    Surface(
                                        onClick = {
                                            draftImages = draftImages.filterNot { it.id == img.id }
                                        },
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.68f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(22.dp)
                                            .testTag("offer_remove_draft_image_$idx")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Retirer l'image", tint = Color.White, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            moderationError = null
                        },
                        label = { Text(if (isProduct) "Nom du produit" else "Nom du service") },
                        singleLine = true,
                        colors = bravOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("offer_title_input")
                    )

                    if (!isProduct) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            serviceCategories.forEach { preset ->
                                FilterChip(
                                    selected = category.equals(preset, ignoreCase = true),
                                    onClick = { category = preset },
                                    label = { Text(preset) }
                                )
                            }
                        }
                    } else {
                        Text("État du produit", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            productConditions.forEach { cond ->
                                FilterChip(
                                    selected = condition == cond,
                                    onClick = { condition = cond },
                                    label = { Text(cond) },
                                    modifier = Modifier.testTag("offer_condition_$cond")
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = priceOrSalary,
                        onValueChange = { priceOrSalary = it },
                        label = { Text(if (isProduct) "Prix (ex: 45 000 FCFA)" else "Tarif indicatif (ex: 15 000 FCFA / h)") },
                        singleLine = true,
                        colors = bravOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("offer_price_input")
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            description = it
                            moderationError = null
                        },
                        label = { Text("Description détaillée") },
                        minLines = 3,
                        maxLines = 5,
                        colors = bravOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("offer_description_input")
                    )

                    OutlinedTextField(
                        value = locationCity,
                        onValueChange = { locationCity = it },
                        label = { Text(if (isProduct) "Localisation (ville / quartier)" else "Zone d'intervention") },
                        singleLine = true,
                        colors = bravOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("offer_location_input")
                    )

                    OutlinedTextField(
                        value = contactInfo,
                        onValueChange = {
                            contactInfo = it
                            moderationError = null
                        },
                        label = { Text("Coordonnées de contact (optionnel)") },
                        singleLine = true,
                        colors = bravOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("offer_contact_input")
                    )

                    if (!moderationError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BravRed.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth().testTag("offer_moderation_error_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = BravRed)
                                Text(
                                    text = moderationError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BravRed,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Surface(
                    color = lux.translucentCardSurface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BravGoldHairlineDivider(withGoldTouch = true)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            enabled = !isSubmitting,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Annuler")
                        }
                        Button(
                            enabled = !isSubmitting && title.trim().length >= 2 && category.trim().length >= 2,
                            colors = ButtonDefaults.buttonColors(containerColor = BravPurple, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            onClick = {
                                val textVerdict = ContentModerationGuard.checkText(
                                    "$title $description ${if (isProduct) condition else ""} $contactInfo"
                                )
                                if (!textVerdict.allowed) {
                                    moderationError = textVerdict.userMessage
                                    return@Button
                                }
                                val kept = draftImages.mapNotNull { it.remoteUrl }
                                val newPayloads = draftImages.mapNotNull { img ->
                                    val b = img.bytes
                                    if (b != null && b.isNotEmpty()) b to img.mimeType else null
                                }
                                val removed = initialExistingUrls.filter { it !in kept }
                                scope.launch {
                                    val res = onSubmit(
                                        OfferComposeSubmission(
                                            offerType = offerType,
                                            title = title.trim(),
                                            category = category.trim(),
                                            description = description.trim(),
                                            condition = if (isProduct) condition else "",
                                            locationCity = locationCity.trim(),
                                            priceOrSalary = priceOrSalary.trim(),
                                            contactInfo = contactInfo.trim(),
                                            keptExistingUrls = kept,
                                            newImagePayloads = newPayloads,
                                            removedUrls = removed
                                        )
                                    )
                                    if (res.isSuccess) {
                                        onDismiss()
                                    } else {
                                        moderationError = res.exceptionOrNull()?.localizedMessage ?: "Publication impossible"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.4f).testTag("offer_submit_button")
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (editingOffer == null) "Publier l'annonce" else "Enregistrer")
                        }
                    }
                    }
                }
            }
            }
        }
    }
}
