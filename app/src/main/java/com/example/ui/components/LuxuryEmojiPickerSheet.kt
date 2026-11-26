package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BravReactionType
import com.example.ui.theme.BravChampagneGold
import com.example.ui.theme.BravPurple

private enum class EmojiTabFilter {
    SUGGESTIONS,
    RECENTS,
    FAVORITES,
    CATEGORY
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LuxuryEmojiPickerSheet(
    onInsertEmoji: (String) -> Unit,
    onSelectReaction: ((BravReactionType) -> Unit)? = null,
    recentEmojis: List<String> = emptyList(),
    favoriteEmojis: List<String> = emptyList(),
    preferredSkinTone: String = "",
    onToggleFavoriteEmoji: ((String) -> Unit)? = null,
    onSelectSkinTone: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeFilter by remember { mutableStateOf(EmojiTabFilter.SUGGESTIONS) }
    var selectedCategory by remember { mutableStateOf(BravEmojiCategory.FACES_EMOTIONS) }
    var search by remember { mutableStateOf("") }
    var localSkinTone by remember(preferredSkinTone) { mutableStateOf(preferredSkinTone) }

    val displayedEntries: List<Pair<String, Boolean>> = remember(
        activeFilter,
        selectedCategory,
        search,
        recentEmojis,
        favoriteEmojis,
        localSkinTone
    ) {
        if (search.isNotBlank()) {
            BravEmojiCatalog.search(search).map { entry ->
                BravEmojiCatalog.applySkinTone(entry.baseEmoji, localSkinTone, entry.supportsSkinTone) to entry.supportsSkinTone
            }
        } else {
            when (activeFilter) {
                EmojiTabFilter.SUGGESTIONS -> BravEmojiCatalog.contextualSuggestions.map { entry ->
                    BravEmojiCatalog.applySkinTone(entry.baseEmoji, localSkinTone, entry.supportsSkinTone) to entry.supportsSkinTone
                }
                EmojiTabFilter.RECENTS -> recentEmojis.map { it to false }
                EmojiTabFilter.FAVORITES -> favoriteEmojis.map { it to false }
                EmojiTabFilter.CATEGORY -> selectedCategory.items.map { entry ->
                    BravEmojiCatalog.applySkinTone(entry.baseEmoji, localSkinTone, entry.supportsSkinTone) to entry.supportsSkinTone
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF130F1D),
        contentColor = Color.White,
        modifier = Modifier.testTag("luxury_emoji_picker_sheet")
    ) {
        val gold = BravChampagneGold
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Émojis & Expressions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = gold
                    )
                    Text(
                        text = "Appui long sur un émoji pour l'ajouter aux favoris",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.60f)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = Color.White)
                }
            }

            if (onSelectReaction != null) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(
                        listOf(
                            BravReactionType.CROWN_ELITE,
                            BravReactionType.DIAMOND_LUX,
                            BravReactionType.HUNDRED_PURE,
                            BravReactionType.THUMBS_UP_GOLD,
                            BravReactionType.HEART_PULSE,
                            BravReactionType.FIRE_FLAME,
                            BravReactionType.CLAP_HANDS,
                            BravReactionType.COOL_BOSS
                        ),
                        key = { it.key }
                    ) { rx ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, gold.copy(alpha = 0.35f)),
                            onClick = {
                                onSelectReaction(rx)
                                onDismiss()
                            },
                            modifier = Modifier.testTag("luxury_reaction_${rx.key}")
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedReactionIcon(
                                    reaction = rx,
                                    size = 26.dp,
                                    onClick = {
                                        onSelectReaction(rx)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Rechercher un émoji (ex: amour, lion, abidjan)…", color = Color.White.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = gold) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = gold,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.24f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = gold
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("emoji_search_input")
            )

            // Skin-tone selector strip
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text(
                        text = "Teint :",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                items(BravEmojiCatalog.skinTones, key = { it.first }) { (tone, label) ->
                    val active = localSkinTone == tone
                    val sample = if (tone.isBlank()) "👋" else "👋$tone"
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (active) BravPurple else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(0.75.dp, if (active) gold else Color.White.copy(alpha = 0.14f)),
                        onClick = {
                            localSkinTone = tone
                            onSelectSkinTone?.invoke(tone)
                        },
                        modifier = Modifier.testTag("emoji_skin_tone_${tone.ifBlank { "default" }}")
                    ) {
                        Text(
                            text = sample,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (search.isBlank()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        EmojiTabPill(
                            label = "✨ Suggestions",
                            selected = activeFilter == EmojiTabFilter.SUGGESTIONS,
                            gold = gold,
                            tag = "emoji_tab_suggestions",
                            onClick = { activeFilter = EmojiTabFilter.SUGGESTIONS }
                        )
                    }
                    item {
                        EmojiTabPill(
                            label = "🕒 Récents",
                            selected = activeFilter == EmojiTabFilter.RECENTS,
                            gold = gold,
                            tag = "emoji_tab_recents",
                            onClick = { activeFilter = EmojiTabFilter.RECENTS }
                        )
                    }
                    item {
                        EmojiTabPill(
                            label = "⭐ Favoris",
                            selected = activeFilter == EmojiTabFilter.FAVORITES,
                            gold = gold,
                            tag = "emoji_tab_favorites",
                            onClick = { activeFilter = EmojiTabFilter.FAVORITES }
                        )
                    }
                    items(BravEmojiCategory.entries, key = { it.id }) { cat ->
                        EmojiTabPill(
                            label = "${cat.iconEmoji} ${cat.title}",
                            selected = activeFilter == EmojiTabFilter.CATEGORY && selectedCategory == cat,
                            gold = gold,
                            tag = "emoji_cat_${cat.id}",
                            onClick = {
                                activeFilter = EmojiTabFilter.CATEGORY
                                selectedCategory = cat
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 260.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (displayedEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (activeFilter) {
                                EmojiTabFilter.RECENTS -> "Aucun émoji récent pour le moment."
                                EmojiTabFilter.FAVORITES -> "Appuyez longuement sur un émoji pour le mettre en favori."
                                else -> "Aucun émoji trouvé pour « $search »."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        displayedEntries.forEach { (emoji, _) ->
                            val isFav = emoji in favoriteEmojis
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = if (isFav) 0.14f else 0.07f),
                                border = if (isFav) BorderStroke(0.75.dp, gold.copy(alpha = 0.7f)) else null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .combinedClickable(
                                        onClick = { onInsertEmoji(emoji) },
                                        onLongClick = { onToggleFavoriteEmoji?.invoke(emoji) }
                                    )
                                    .testTag("luxury_emoji_item_$emoji")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 24.sp)
                                    if (isFav) {
                                        Icon(
                                            Icons.Rounded.Star,
                                            contentDescription = "Favori",
                                            tint = gold,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(3.dp)
                                                .size(10.dp)
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

@Composable
private fun EmojiTabPill(
    label: String,
    selected: Boolean,
    gold: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) BravPurple else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (selected) gold else Color.White.copy(alpha = 0.12f)),
        onClick = onClick,
        modifier = Modifier.testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.80f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}
