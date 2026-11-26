package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BravChampagneGold
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite

private enum class StickerTabMode {
    ALL,
    RECENTS,
    FAVORITES,
    CATEGORY,
    PACKS
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BravStickerPickerSheet(
    recentStickerIds: List<String>,
    favoriteStickerIds: List<String>,
    enabledPackIds: Set<String>,
    onSendSticker: (BravSticker) -> Unit,
    onToggleFavoriteSticker: (String) -> Unit,
    onTogglePackEnabled: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lux = BravTheme.colors
    var tabMode by remember { mutableStateOf(StickerTabMode.ALL) }
    var selectedCategory by remember { mutableStateOf(BravStickerCategory.IVORIAN_CULTURE) }
    var search by remember { mutableStateOf("") }
    var previewSticker by remember { mutableStateOf<BravSticker?>(null) }

    val activeStickers = remember(enabledPackIds, search, tabMode, selectedCategory, recentStickerIds, favoriteStickerIds) {
        val enabled = BravStickerCatalog.stickers.filter { it.packId in enabledPackIds }
        if (search.isNotBlank()) {
            val q = search.trim().lowercase()
            BravStickerCatalog.stickers.filter {
                it.label.lowercase().contains(q) ||
                    it.subtitle.lowercase().contains(q) ||
                    it.keywords.lowercase().contains(q)
            }
        } else {
            when (tabMode) {
                StickerTabMode.ALL -> enabled
                StickerTabMode.RECENTS -> recentStickerIds.mapNotNull { BravStickerCatalog.byId(it) }
                StickerTabMode.FAVORITES -> favoriteStickerIds.mapNotNull { BravStickerCatalog.byId(it) }
                StickerTabMode.CATEGORY -> enabled.filter { it.category == selectedCategory }
                    .ifEmpty { BravStickerCatalog.stickers.filter { it.category == selectedCategory } }
                StickerTabMode.PACKS -> emptyList()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF140D22),
        contentColor = BravWhite,
        modifier = Modifier.testTag("brav_sticker_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Autocollants BRAV",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BravChampagneGold
                    )
                    Text(
                        text = "Packs culturels ivoiriens, expressions africaines & réactions",
                        style = MaterialTheme.typography.labelSmall,
                        color = BravWhite.copy(alpha = 0.65f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            tabMode = if (tabMode == StickerTabMode.PACKS) StickerTabMode.ALL else StickerTabMode.PACKS
                        },
                        modifier = Modifier.testTag("sticker_pack_manager_button")
                    ) {
                        Icon(
                            Icons.Rounded.CollectionsBookmark,
                            contentDescription = "Gérer les packs",
                            tint = if (tabMode == StickerTabMode.PACKS) BravChampagneGold else BravWhite
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = BravWhite)
                    }
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Rechercher (ex: Akwaba, Yako, Ubuntu, Amour…)", color = BravWhite.copy(alpha = 0.52f)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = BravChampagneGold) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = BravChampagneGold,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.24f),
                    focusedTextColor = BravWhite,
                    unfocusedTextColor = BravWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sticker_search_input")
            )

            if (search.isBlank()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        StickerTabPill("Tous", tabMode == StickerTabMode.ALL, "sticker_tab_all") {
                            tabMode = StickerTabMode.ALL
                        }
                    }
                    item {
                        StickerTabPill("🕒 Récents", tabMode == StickerTabMode.RECENTS, "sticker_tab_recents") {
                            tabMode = StickerTabMode.RECENTS
                        }
                    }
                    item {
                        StickerTabPill("⭐ Favoris", tabMode == StickerTabMode.FAVORITES, "sticker_tab_favorites") {
                            tabMode = StickerTabMode.FAVORITES
                        }
                    }
                    items(BravStickerCategory.entries, key = { it.id }) { cat ->
                        StickerTabPill(
                            label = cat.label,
                            selected = tabMode == StickerTabMode.CATEGORY && selectedCategory == cat,
                            tag = "sticker_cat_${cat.id}"
                        ) {
                            tabMode = StickerTabMode.CATEGORY
                            selectedCategory = cat
                        }
                    }
                }
            }

            if (tabMode == StickerTabMode.PACKS && search.isBlank()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 300.dp)
                        .testTag("sticker_pack_manager_panel"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(BravStickerCatalog.packs, key = { it.id }) { pack ->
                        val enabled = pack.id in enabledPackIds
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(0.75.dp, BravChampagneGold.copy(alpha = if (enabled) 0.55f else 0.18f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pack.title, fontWeight = FontWeight.Bold, color = BravWhite, style = MaterialTheme.typography.bodyMedium)
                                    Text(pack.description, color = BravWhite.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                                }
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { onTogglePackEnabled(pack.id, it) },
                                    modifier = Modifier.testTag("sticker_pack_switch_${pack.id}")
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (activeStickers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (tabMode) {
                                    StickerTabMode.RECENTS -> "Aucun autocollant utilisé récemment."
                                    StickerTabMode.FAVORITES -> "Ajoutez vos autocollants préférés via l'étoile."
                                    else -> "Aucun autocollant disponible."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = BravWhite.copy(alpha = 0.68f)
                            )
                        }
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            activeStickers.forEach { sticker ->
                                val isFav = sticker.id in favoriteStickerIds
                                Surface(
                                    onClick = { previewSticker = sticker },
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.White.copy(alpha = 0.04f),
                                    border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.10f)),
                                    modifier = Modifier
                                        .width(104.dp)
                                        .testTag("sticker_item_${sticker.id}")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BravStickerArtwork(
                                            sticker = sticker,
                                            size = 88.dp,
                                            animated = false
                                        )
                                        if (isFav) {
                                            Icon(
                                                Icons.Rounded.Star,
                                                contentDescription = "Favori",
                                                tint = BravChampagneGold,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(14.dp)
                                            )
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
    }

    previewSticker?.let { sticker ->
        val isFav = sticker.id in favoriteStickerIds
        AlertDialog(
            onDismissRequest = { previewSticker = null },
            containerColor = lux.modalSurface,
            modifier = Modifier.testTag("sticker_preview_dialog"),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sticker.label,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onToggleFavoriteSticker(sticker.id) },
                        modifier = Modifier.testTag("sticker_favorite_toggle")
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = if (isFav) "Retirer des favoris" else "Ajouter aux favoris",
                            tint = BravChampagneGold
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BravStickerArtwork(
                        sticker = sticker,
                        size = 176.dp,
                        animated = true
                    )
                    Text(
                        text = sticker.category.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = lux.inkSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        previewSticker = null
                        onSendSticker(sticker)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("sticker_send_confirm_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Envoyer")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { previewSticker = null },
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Fermer")
                }
            }
        )
    }
}

@Composable
private fun StickerTabPill(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) BravPurplePrimary else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (selected) BravChampagneGold else Color.White.copy(alpha = 0.14f)),
        onClick = onClick,
        modifier = Modifier.testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) BravWhite else BravWhite.copy(alpha = 0.80f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}
