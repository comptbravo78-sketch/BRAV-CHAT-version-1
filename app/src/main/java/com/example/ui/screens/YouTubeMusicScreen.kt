package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import coil.compose.AsyncImage
import com.example.network.YouTubeMusicRepository
import com.example.network.YouTubeTrack
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.components.YouTubePlayerController
import com.example.ui.components.bravOutlinedTextFieldColors
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravTheme
import com.example.ui.theme.StatusOnlineGreen
import kotlinx.coroutines.launch

private data class MusicCategory(val label: String, val query: String)

private val musicCategories = listOf(
    MusicCategory("Tendances", ""),
    MusicCategory("Afrobeats", "afrobeats hits mix"),
    MusicCategory("Rap & Urbain", "rap francais afropop hits"),
    MusicCategory("RnB & Soul", "rnb soul smooth hits"),
    MusicCategory("Gospel", "gospel worship music"),
    MusicCategory("Lo-Fi Chill", "lofi chill beats")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeMusicScreen(
    repository: YouTubeMusicRepository,
    controller: YouTubePlayerController,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(musicCategories.first()) }
    var tracks by remember { mutableStateOf<List<YouTubeTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var warningMessage by remember { mutableStateOf<String?>(null) }

    fun loadCategory(category: MusicCategory) {
        selectedCategory = category
        isLoading = true
        warningMessage = null
        scope.launch {
            val result = if (category.query.isBlank()) {
                repository.fetchTrendingMusic()
            } else {
                repository.searchMusic(category.query)
            }
            tracks = result.tracks
            warningMessage = result.warningMessage
            isLoading = false
        }
    }

    fun submitSearch() {
        keyboardController?.hide()
        val trimmed = searchQuery.trim()
        val directId = YouTubePlayerController.extractYouTubeVideoId(trimmed)
        if (directId != null) {
            isLoading = true
            scope.launch {
                val track = repository.resolveTrackByVideoId(directId)
                isLoading = false
                if (track != null) {
                    val queue = (listOf(track) + tracks).distinctBy { it.videoId }
                    controller.playTrack(track, queue, startMinimized = false)
                }
            }
            return
        }

        isLoading = true
        warningMessage = null
        scope.launch {
            val result = repository.searchMusic(trimmed)
            tracks = result.tracks
            warningMessage = result.warningMessage
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadCategory(musicCategories.first())
    }

    val lux = BravTheme.colors
    Scaffold(
        containerColor = lux.ivoryCanvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = lux.translucentCardSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("music_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = {
                    Text(
                        text = "BRAV Musique",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BravPurple
                    )
                },
                actions = {
                    if (controller.isVisible) {
                        IconButton(
                            onClick = { controller.toggleHideVideo() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("music_topbar_hide_video_button")
                        ) {
                            Icon(
                                imageVector = if (controller.isVideoHidden) Icons.Rounded.OndemandVideo else Icons.Rounded.VisibilityOff,
                                contentDescription = if (controller.isVideoHidden) "Afficher la vidéo" else "Masquer la vidéo",
                                tint = BravPurple
                            )
                        }
                        IconButton(
                            onClick = { controller.expand() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("music_open_player_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "Ouvrir le lecteur en cours",
                                tint = StatusOnlineGreen
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(lux.ivoryCanvas)
        ) {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.SOLAR_ADINKRA,
                variant = BravPatternVariant.GOLD_ON_IVORY,
                opacity = 0.095f,
                scale = 1.05f
            )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Titre, artiste ou lien YouTube...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    loadCategory(selectedCategory)
                                }
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Effacer")
                            }
                            IconButton(
                                onClick = { submitSearch() },
                                modifier = Modifier.testTag("music_search_submit_button")
                            ) {
                                Icon(Icons.Rounded.PlayCircle, contentDescription = "Rechercher", tint = BravPurple)
                            }
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                shape = RoundedCornerShape(18.dp),
                colors = bravOutlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("music_search_input")
            )

            Spacer(Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(musicCategories, key = { it.label }) { category ->
                    val selected = category.label == selectedCategory.label && searchQuery.isBlank()
                    FilterChip(
                        selected = selected,
                        onClick = {
                            searchQuery = ""
                            loadCategory(category)
                        },
                        label = { Text(category.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BravPurple.copy(alpha = 0.14f),
                            selectedLabelColor = BravPurple
                        )
                    )
                }
            }

            if (tracks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${tracks.size} titres disponibles",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                controller.playTrack(tracks.first(), tracks, startMinimized = false)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("music_play_all_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tout lire", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (!warningMessage.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = warningMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = BravPurple)
                            Text(
                                text = "Chargement de la musique...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                tracks.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LibraryMusic,
                                contentDescription = null,
                                tint = BravPurple.copy(alpha = 0.6f),
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = "Aucune piste affichée",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Recherchez un artiste, choisissez une catégorie ou collez un lien YouTube pour démarrer la lecture continue.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { loadCategory(musicCategories.first()) },
                                colors = ButtonDefaults.buttonColors(containerColor = BravPurple)
                            ) {
                                Text("Actualiser")
                            }
                        }
                    }
                }

                else -> {
                    val bottomSpace = if (controller.isVisible && controller.isMinimized) 96.dp else 20.dp
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = bottomSpace),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(tracks, key = { it.videoId }) { track ->
                            val isPlayingThis = controller.videoId == track.videoId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        controller.playTrack(track, tracks, startMinimized = false)
                                    }
                                    .testTag("music_track_card_${track.videoId}"),
                                shape = RoundedCornerShape(16.dp),
                                color = if (isPlayingThis) BravPurple.copy(alpha = 0.10f) else lux.translucentCardSurface,
                                border = BorderStroke(
                                    0.8.dp,
                                    if (isPlayingThis) lux.champagneGold else lux.violetContour
                                ),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(72.dp)
                                            .height(50.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = track.thumbnailUrl,
                                            contentDescription = track.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (track.durationLabel.isNotBlank()) {
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.72f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(4.dp)
                                            ) {
                                                Text(
                                                    text = track.durationLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isPlayingThis) BravPurple else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = track.channelTitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Icon(
                                        imageVector = if (isPlayingThis) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,
                                        contentDescription = if (isPlayingThis) "En lecture" else "Lire",
                                        tint = if (isPlayingThis) BravPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
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
