package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.StatusPost
import com.example.model.User
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.components.VerifiedBlueBadge

enum class ActusSearchCategory(val labelFr: String, val tag: String) {
    ALL("Tout", "actus_filter_all"),
    POSTS("Publications", "actus_filter_posts"),
    PEOPLE("Personnes", "actus_filter_people"),
    VERIFIED("Vérifiés", "actus_filter_verified"),
    PHOTOS("Photos", "actus_filter_photos"),
    VIDEOS("Vidéos", "actus_filter_videos"),
    HASHTAGS("#Hashtags", "actus_filter_hashtags")
}

enum class ActusSearchSort(val labelFr: String) {
    RECENT("Plus récents"),
    POPULAR("Populaires")
}

private val HASHTAG_REGEX = Regex("""#[\p{L}\p{N}_]{2,}""")

fun extractActusHashtags(posts: List<StatusPost>, limit: Int = 12): List<String> {
    val counts = linkedMapOf<String, Int>()
    posts.forEach { post ->
        val body = "${post.text} ${post.caption}"
        HASHTAG_REGEX.findAll(body).forEach { match ->
            val tag = match.value
            val existingKey = counts.keys.firstOrNull { it.equals(tag, ignoreCase = true) } ?: tag
            counts[existingKey] = (counts[existingKey] ?: 0) + 1
        }
    }
    return counts.entries
        .sortedByDescending { it.value }
        .take(limit)
        .map { it.key }
}

fun filterActusPosts(
    posts: List<StatusPost>,
    knownUsers: Map<String, User>,
    query: String,
    category: ActusSearchCategory,
    sort: ActusSearchSort
): List<StatusPost> {
    val q = query.trim().lowercase()
    val filtered = posts.filter { post ->
        val author = knownUsers[post.userId]
        val isVerifiedAuthor = author?.isVerifiedActive() == true
        val resolvedMedia = post.resolvedMediaUrls()
        val hasVideo = post.type == "video"
        val hasPhoto = !hasVideo && (post.type == "image" || resolvedMedia.isNotEmpty())
        val textBody = "${post.text} ${post.caption}".lowercase()
        val hasHashtag = HASHTAG_REGEX.containsMatchIn(textBody)

        val matchesCategory = when (category) {
            ActusSearchCategory.ALL, ActusSearchCategory.POSTS, ActusSearchCategory.PEOPLE -> true
            ActusSearchCategory.VERIFIED -> isVerifiedAuthor
            ActusSearchCategory.PHOTOS -> hasPhoto
            ActusSearchCategory.VIDEOS -> hasVideo
            ActusSearchCategory.HASHTAGS -> hasHashtag
        }
        if (!matchesCategory) return@filter false

        if (q.isBlank()) return@filter true
        val nameMatch = post.userName.lowercase().contains(q) ||
            (author?.displayName?.lowercase()?.contains(q) == true)
        val handleMatch = post.authorUsername.lowercase().contains(q.removePrefix("@")) ||
            (author?.username?.lowercase()?.contains(q.removePrefix("@")) == true)
        val orgMatch = author?.verifiedOrgName?.lowercase()?.contains(q) == true
        val textMatch = textBody.contains(q)
        nameMatch || handleMatch || orgMatch || textMatch
    }

    return when (sort) {
        ActusSearchSort.RECENT -> filtered.sortedByDescending { it.createdAt?.seconds ?: 0L }
        ActusSearchSort.POPULAR -> filtered.sortedByDescending { post ->
            post.likedByUids.size * 2 + post.commentCount * 3 + post.reactions.values.sumOf { it.size }
        }
    }
}

fun filterActusPeople(
    knownUsers: Map<String, User>,
    query: String,
    verifiedOnly: Boolean
): List<User> {
    val q = query.trim().lowercase().removePrefix("@")
    return knownUsers.values
        .filter { user ->
            if (verifiedOnly && !user.isVerifiedActive()) return@filter false
            if (q.isBlank()) return@filter verifiedOnly
            user.displayName.lowercase().contains(q) ||
                user.username.lowercase().contains(q) ||
                user.locationCity.lowercase().contains(q) ||
                user.verifiedOrgName.lowercase().contains(q)
        }
        .sortedWith(
            compareByDescending<User> { it.isVerifiedActive() }
                .thenBy { it.displayName.lowercase() }
        )
        .take(15)
}

@Composable
internal fun ActusSmartSearchBar(
    query: String,
    selectedCategory: ActusSearchCategory,
    selectedSort: ActusSearchSort,
    searchHistory: List<String>,
    trendingHashtags: List<String>,
    matchedPeople: List<User>,
    palette: ActusEditorialPalette,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onSelectCategory: (ActusSearchCategory) -> Unit,
    onSelectSort: (ActusSearchSort) -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSelectPerson: (User) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("actus_smart_search_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    "Rechercher publications, @comptes, #tags...",
                    color = palette.secondaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.accent)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.testTag("actus_search_clear_input")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Effacer", tint = palette.secondaryInk)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmitSearch(query) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = palette.subtleSurface,
                unfocusedContainerColor = palette.subtleSurface,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.hairline,
                focusedTextColor = palette.primaryInk,
                unfocusedTextColor = palette.primaryInk
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("actus_search_input")
        )

        // Category chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(ActusSearchCategory.entries, key = { it.name }) { cat ->
                val active = selectedCategory == cat
                FilterChip(
                    selected = active,
                    onClick = { onSelectCategory(cat) },
                    label = {
                        Text(
                            text = cat.labelFr,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.accent.copy(alpha = 0.14f),
                        selectedLabelColor = palette.accent
                    ),
                    modifier = Modifier.testTag(cat.tag)
                )
            }
        }

        // Sort + History header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActusSearchSort.entries.forEach { sort ->
                    val active = selectedSort == sort
                    Surface(
                        onClick = { onSelectSort(sort) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (active) palette.accent else palette.subtleSurface,
                        border = if (active) null else BorderStroke(0.75.dp, palette.hairline),
                        modifier = Modifier.testTag("actus_sort_${sort.name.lowercase()}")
                    ) {
                        Text(
                            text = sort.labelFr,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) Color.White else palette.secondaryInk,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            if (searchHistory.isNotEmpty()) {
                TextButton(
                    onClick = onClearHistory,
                    modifier = Modifier.testTag("actus_search_clear_history")
                ) {
                    Text(
                        "Effacer l'historique",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondaryInk
                    )
                }
            }
        }

        // History chips when query empty
        if (query.isBlank() && searchHistory.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchHistory, key = { "hist_$it" }) { item ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = palette.subtleSurface,
                        border = BorderStroke(0.75.dp, palette.hairline),
                        modifier = Modifier.testTag("actus_history_chip_$item")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { onQueryChange(item) }
                                .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)
                        ) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = null,
                                tint = palette.secondaryInk,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = item,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.primaryInk,
                                maxLines = 1
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Retirer $item",
                                tint = palette.secondaryInk,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { onRemoveHistoryItem(item) }
                            )
                        }
                    }
                }
            }
        }

        // Trending #hashtags strip
        val showTags = remember(selectedCategory, trendingHashtags) {
            trendingHashtags.isNotEmpty() && (selectedCategory == ActusSearchCategory.HASHTAGS || query.startsWith("#"))
        }
        if (showTags) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(trendingHashtags, key = { "tag_$it" }) { tag ->
                    Surface(
                        onClick = {
                            onQueryChange(tag)
                            onSubmitSearch(tag)
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = palette.accent.copy(alpha = 0.10f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Tag, contentDescription = null, tint = palette.accent, modifier = Modifier.size(14.dp))
                            Text(tag, style = MaterialTheme.typography.labelMedium, color = palette.accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Matching People & Verified accounts carousel
        if (matchedPeople.isNotEmpty() && (query.isNotBlank() || selectedCategory == ActusSearchCategory.PEOPLE || selectedCategory == ActusSearchCategory.VERIFIED)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Comptes correspondants (${matchedPeople.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.secondaryInk
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(matchedPeople, key = { "person_${it.userId}" }) { user ->
                        Surface(
                            onClick = {
                                onSubmitSearch(query)
                                onSelectPerson(user)
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = palette.subtleSurface,
                            border = BorderStroke(0.75.dp, palette.hairline),
                            modifier = Modifier.testTag("actus_search_person_${user.userId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                UserAvatarWithStatus(
                                    name = user.displayName,
                                    avatarUrl = user.avatarUrl,
                                    availabilityStatus = user.availabilityStatus,
                                    size = 34.dp
                                )
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = user.displayName.ifBlank { user.username },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.primaryInk,
                                            maxLines = 1
                                        )
                                        if (user.isVerifiedActive()) {
                                            VerifiedBlueBadge(size = 13.dp)
                                        }
                                    }
                                    if (user.username.isNotBlank()) {
                                        Text(
                                            text = "@${user.username}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.secondaryInk,
                                            maxLines = 1
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
