package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.BravReactionType
import com.example.model.StatusPost
import com.example.model.User
import com.example.network.WorkerApiClient
import com.example.ui.components.BravEmojiCatalog
import com.example.ui.components.BravLuxuryHeader
import com.example.ui.components.BravPatternIntensity
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravUnreadCountBadge
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.components.bravAfricanPattern
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import com.example.viewmodel.BravViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

internal enum class ActusFeedTab(val label: String, val tag: String) {
    ALL("À la une", "actus_tab_all"),
    STORIES("Statuts 24h", "actus_tab_stories"),
    MINE("Mes publications", "actus_tab_mine")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusesScreen(
    viewModel: BravViewModel,
    onOpenConversation: (String) -> Unit,
    onOpenDiscussions: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    if (onBack != null) {
        BackHandler { onBack() }
    }
    val scope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val savedContacts by viewModel.savedContactUsers.collectAsStateWithLifecycle()
    val knownDirectory by viewModel.knownUsersDirectory.collectAsStateWithLifecycle()
    val searchHistory by viewModel.actusSearchHistory.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val presenceMap by viewModel.presenceMap.collectAsStateWithLifecycle()
    val followingUserIds by viewModel.followingUserIds.collectAsStateWithLifecycle()
    val outgoingFollowRequests by viewModel.outgoingFollowRequests.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshingActus.collectAsStateWithLifecycle()
    val unreadChatsCount by viewModel.totalUnreadChatsCount.collectAsStateWithLifecycle()
    val appPrefs by viewModel.appPrefs.collectAsStateWithLifecycle()
    val recentEmojis = remember(appPrefs.recentEmojisJson) {
        BravEmojiCatalog.parseListJson(appPrefs.recentEmojisJson)
    }
    val favoriteEmojis = remember(appPrefs.favoriteEmojisJson) {
        BravEmojiCatalog.parseListJson(appPrefs.favoriteEmojisJson)
    }

    var showComposerSheet by remember { mutableStateOf(false) }
    var composerInitialStoryMode by remember { mutableStateOf(false) }
    var viewingUserStatuses by remember { mutableStateOf<List<StatusPost>?>(null) }
    var viewingInitialIndex by remember { mutableIntStateOf(0) }
    var activeCommentsStatusId by remember { mutableStateOf<String?>(null) }
    var sharingPost by remember { mutableStateOf<StatusPost?>(null) }
    var profileAuthorUser by remember { mutableStateOf<User?>(null) }
    var galleryRequest by remember { mutableStateOf<Pair<StatusPost, Int>?>(null) }

    val knownUsers = remember(currentUser, savedContacts, knownDirectory) {
        buildMap {
            putAll(knownDirectory)
            savedContacts.forEach { put(it.userId, it) }
            currentUser?.let { put(it.userId, it) }
        }
    }
    val verifiedUserIds = remember(knownUsers) {
        knownUsers.values.filter { it.isVerifiedActive() }.map { it.userId }.toSet()
    }
    val onlineUids = remember(presenceMap) {
        presenceMap.filterValues { it.online }.keys
    }

    val commentsStatus = remember(statuses, activeCommentsStatusId) {
        activeCommentsStatusId?.let { id -> statuses.firstOrNull { it.id == id } }
    }

    fun openAuthorByUidOrHandle(userId: String, handle: String?) {
        val localMatch = when {
            userId.isNotBlank() -> knownUsers[userId]
            !handle.isNullOrBlank() -> knownUsers.values.firstOrNull {
                it.username.equals(handle, ignoreCase = true)
            }
            else -> null
        }
        if (localMatch != null) {
            profileAuthorUser = localMatch
            return
        }
        if (userId.isNotBlank()) {
            val fromPost = statuses.firstOrNull { it.userId == userId }
            profileAuthorUser = User(
                userId = userId,
                displayName = fromPost?.userName ?: "Membre BRAV",
                username = fromPost?.authorUsername.orEmpty(),
                avatarUrl = fromPost?.userAvatar.orEmpty(),
                locationCity = fromPost?.authorHeadline.orEmpty()
            )
            scope.launch {
                viewModel.userRepository.getUserById(userId)?.let { fetched ->
                    profileAuthorUser = fetched
                }
            }
        } else if (!handle.isNullOrBlank()) {
            scope.launch {
                val results = viewModel.contactRepository.searchUsers(handle, viewModel.currentUserId)
                val exact = results.firstOrNull { it.username.equals(handle, ignoreCase = true) }
                    ?: results.firstOrNull()
                if (exact != null) {
                    profileAuthorUser = exact
                } else {
                    viewModel.showMessage("Profil @$handle introuvable")
                }
            }
        }
    }

    ActusFeedContent(
        myUid = viewModel.currentUserId,
        currentUser = currentUser,
        posts = statuses,
        knownUsers = knownUsers,
        onlineUids = onlineUids,
        searchHistory = searchHistory,
        isInitialLoading = currentUser == null && statuses.isEmpty(),
        isRefreshing = isRefreshing,
        unreadMessagesCount = unreadChatsCount,
        workerApiClient = viewModel.workerApiClient,
        onRefresh = { viewModel.refreshActusFeed() },
        onOpenDiscussions = onOpenDiscussions,
        onSearchUsersQuery = { q -> viewModel.searchUsers(q) },
        onAddSearchHistory = { q -> viewModel.addActusSearchHistory(q) },
        onRemoveSearchHistoryItem = { q -> viewModel.removeActusSearchHistoryItem(q) },
        onClearSearchHistory = { viewModel.clearActusSearchHistory() },
        onOpenComposer = { storyMode ->
            composerInitialStoryMode = storyMode
            showComposerSheet = true
        },
        onOpenStoryGroup = { group, idx ->
            viewingUserStatuses = group
            viewingInitialIndex = idx
        },
        onOpenAuthorProfile = { uid, handle -> openAuthorByUidOrHandle(uid, handle) },
        onToggleLike = { post -> viewModel.toggleStatusLike(post) },
        onOpenComments = { post -> activeCommentsStatusId = post.id },
        onToggleReaction = { post, reaction -> viewModel.toggleStatusReaction(post, reaction.key) },
        onSharePost = { post -> sharingPost = post },
        onOpenMediaGallery = { post, index -> galleryRequest = post to index },
        onDeletePost = { post -> scope.launch { viewModel.socialRepository.deleteStatus(post.id) } },
        onCopyFeedback = { msg -> viewModel.showMessage(msg) },
        onBack = onBack
    )

    if (showComposerSheet) {
        ActusComposerSheet(
            savedContacts = savedContacts,
            currentUser = currentUser,
            initialIsEphemeral24h = composerInitialStoryMode,
            recentEmojis = recentEmojis,
            favoriteEmojis = favoriteEmojis,
            preferredSkinTone = appPrefs.preferredEmojiSkinTone,
            onRecordEmoji = { e -> scope.launch { viewModel.prefsRepository.recordRecentEmoji(e) } },
            onToggleFavoriteEmoji = { e -> scope.launch { viewModel.prefsRepository.toggleFavoriteEmoji(e) } },
            onSelectSkinTone = { tone -> scope.launch { viewModel.prefsRepository.setPreferredEmojiSkinTone(tone) } },
            onPublish = { draft ->
                viewModel.publishActusPost(
                    text = draft.text,
                    mediaBytes = draft.mediaBytes,
                    mimeType = draft.mimeType,
                    isVideo = draft.isVideo,
                    sourceVideoUri = draft.sourceVideoUri,
                    mediaItems = draft.mediaItems,
                    backgroundColor = draft.backgroundColor,
                    audienceType = draft.audienceType,
                    allowedUids = draft.allowedUids,
                    isEphemeral24h = draft.isEphemeral24h
                )
            },
            onDismiss = { showComposerSheet = false }
        )
    }

    viewingUserStatuses?.let { group ->
        val freshGroup = remember(statuses, group) {
            val uid = group.firstOrNull()?.userId
            statuses.filter { it.userId == uid && it.isEphemeralStory() }.ifEmpty { group }
        }
        MinimalStatusViewerDialog(
            groupStatuses = freshGroup,
            initialIndex = viewingInitialIndex.coerceIn(0, (freshGroup.size - 1).coerceAtLeast(0)),
            myUid = viewModel.currentUserId,
            viewModel = viewModel,
            onDismiss = { viewingUserStatuses = null },
            onOpenComments = { sid -> activeCommentsStatusId = sid },
            onOpenPrivateChat = { cid ->
                viewingUserStatuses = null
                onOpenConversation(cid)
            }
        )
    }

    commentsStatus?.let { status ->
        val comments by remember(status.id) {
            viewModel.socialRepository.observeStatusComments(status.id)
        }.collectAsStateWithLifecycle(initialValue = emptyList())

        ActusCommentsSheet(
            status = status,
            comments = comments,
            myUid = viewModel.currentUserId,
            verifiedUserIds = verifiedUserIds,
            recentEmojis = recentEmojis,
            favoriteEmojis = favoriteEmojis,
            preferredSkinTone = appPrefs.preferredEmojiSkinTone,
            onRecordEmoji = { e -> scope.launch { viewModel.prefsRepository.recordRecentEmoji(e) } },
            onToggleFavoriteEmoji = { e -> scope.launch { viewModel.prefsRepository.toggleFavoriteEmoji(e) } },
            onSelectSkinTone = { tone -> scope.launch { viewModel.prefsRepository.setPreferredEmojiSkinTone(tone) } },
            onSubmitComment = { text, rxKey, parentId, replyToName, onBlocked, onAdded ->
                viewModel.addStatusComment(
                    statusId = status.id,
                    text = text,
                    reactionKey = rxKey,
                    parentCommentId = parentId,
                    replyToAuthorName = replyToName,
                    onBlocked = onBlocked,
                    onAdded = onAdded
                )
            },
            onDeleteComment = { commentId -> viewModel.deleteStatusComment(status.id, commentId) },
            onAuthorClick = { uid ->
                activeCommentsStatusId = null
                openAuthorByUidOrHandle(uid, null)
            },
            onDismiss = { activeCommentsStatusId = null }
        )
    }

    profileAuthorUser?.let { author ->
        val authorPosts = remember(statuses, author.userId) {
            statuses.filter { it.userId == author.userId }
        }
        val liveAuthor = knownUsers[author.userId] ?: author
        val authorRelation = remember(followingUserIds, outgoingFollowRequests, liveAuthor.userId) {
            viewModel.followRelationStateFor(liveAuthor.userId)
        }
        FriendProfileModal(
            user = liveAuthor,
            userPosts = authorPosts,
            myUid = viewModel.currentUserId,
            isContact = savedContacts.any { it.userId == liveAuthor.userId },
            isOnline = liveAuthor.userId in onlineUids || liveAuthor.isOnline,
            isFollowing = liveAuthor.userId in followingUserIds,
            followRelationState = authorRelation,
            onToggleFollow = { target -> viewModel.toggleFollowUser(target) },
            onSendMessage = { target ->
                scope.launch {
                    viewModel.openOrCreateDirectChat(target).onSuccess { conv ->
                        profileAuthorUser = null
                        onOpenConversation(conv.id)
                    }
                }
            },
            onStartAudioCall = { target ->
                profileAuthorUser = null
                viewModel.startOutgoingCall(target.userId, target.displayName, isVideo = false)
            },
            onOpenPostComments = { post ->
                profileAuthorUser = null
                activeCommentsStatusId = post.id
            },
            onSharePost = { post ->
                profileAuthorUser = null
                sharingPost = post
            },
            onEditOwnPost = { post, newText ->
                viewModel.updateStatusText(post, newText) {}
            },
            onDeleteOwnPost = { post ->
                scope.launch { viewModel.socialRepository.deleteStatus(post.id) }
            },
            onDismiss = { profileAuthorUser = null }
        )
    }

    sharingPost?.let { post ->
        ActusShareSheet(
            post = post,
            conversations = conversations,
            myUid = viewModel.currentUserId,
            onForwardToConversation = { conv, note ->
                viewModel.forwardActusPostToConversation(post, conv, note)
            },
            onDismiss = { sharingPost = null }
        )
    }

    galleryRequest?.let { (post, startIndex) ->
        ActusFullScreenGalleryDialog(
            post = post,
            initialIndex = startIndex,
            workerApiClient = viewModel.workerApiClient,
            onDismiss = { galleryRequest = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActusFeedContent(
    myUid: String,
    currentUser: User?,
    posts: List<StatusPost>,
    knownUsers: Map<String, User> = emptyMap(),
    onlineUids: Set<String> = emptySet(),
    searchHistory: List<String> = emptyList(),
    isInitialLoading: Boolean = false,
    isRefreshing: Boolean = false,
    unreadMessagesCount: Int = 0,
    workerApiClient: WorkerApiClient? = null,
    onRefresh: () -> Unit = {},
    onOpenDiscussions: () -> Unit = {},
    onSearchUsersQuery: (String) -> Unit = {},
    onAddSearchHistory: (String) -> Unit = {},
    onRemoveSearchHistoryItem: (String) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onOpenComposer: (storyMode24h: Boolean) -> Unit,
    onOpenStoryGroup: (List<StatusPost>, Int) -> Unit,
    onOpenAuthorProfile: (userId: String, handle: String?) -> Unit = { _, _ -> },
    onToggleLike: (StatusPost) -> Unit,
    onOpenComments: (StatusPost) -> Unit,
    onToggleReaction: (StatusPost, BravReactionType) -> Unit,
    onSharePost: (StatusPost) -> Unit = {},
    onOpenMediaGallery: (StatusPost, Int) -> Unit = { _, _ -> },
    onDeletePost: (StatusPost) -> Unit,
    onCopyFeedback: (String) -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val palette = rememberActusPalette()
    var selectedTab by remember { mutableStateOf(ActusFeedTab.ALL) }
    var expandedReactionPostId by remember { mutableStateOf<String?>(null) }
    var searchPanelOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchCategory by remember { mutableStateOf(ActusSearchCategory.ALL) }
    var searchSort by remember { mutableStateOf(ActusSearchSort.RECENT) }

    val storyPosts = remember(posts) { posts.filter { it.isEphemeralStory() } }
    val myStories = remember(storyPosts, myUid) { storyPosts.filter { it.userId == myUid } }
    val contactStoryGroups = remember(storyPosts, myUid) {
        storyPosts.filter { it.userId != myUid }.groupBy { it.userId }.values.toList()
    }

    val trendingHashtags = remember(posts) { extractActusHashtags(posts) }
    val matchedPeople = remember(knownUsers, searchQuery, searchCategory) {
        filterActusPeople(
            knownUsers = knownUsers,
            query = searchQuery,
            verifiedOnly = searchCategory == ActusSearchCategory.VERIFIED
        )
    }

    val displayedPosts = remember(posts, selectedTab, myUid, searchPanelOpen, searchQuery, searchCategory, searchSort, knownUsers) {
        val tabScoped = when (selectedTab) {
            ActusFeedTab.ALL -> posts
            ActusFeedTab.STORIES -> posts.filter { it.isEphemeralStory() }
            ActusFeedTab.MINE -> posts.filter { it.userId == myUid }
        }
        val activeQuery = if (searchPanelOpen) searchQuery else ""
        val activeCategory = if (searchPanelOpen) searchCategory else ActusSearchCategory.ALL
        val activeSort = if (searchPanelOpen) searchSort else ActusSearchSort.RECENT
        filterActusPosts(
            posts = tabScoped,
            knownUsers = knownUsers,
            query = activeQuery,
            category = activeCategory,
            sort = activeSort
        )
    }

    val lux = BravTheme.colors
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .bravAfricanPattern(
                pattern = BravPatternType.KENTE_DIAMOND,
                primaryColor = lux.watermarkGold,
                secondaryColor = lux.watermarkViolet,
                opacity = BravPatternIntensity.SUBTLE_WATERMARK
            )
    ) {
        val availableWidth = maxWidth
        val isWideLayout = availableWidth >= 680.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BravLuxuryHeader(
                title = "BRAV Chat",
                showLogo = onBack == null,
                pattern = BravPatternType.KENTE_DIAMOND,
                navigationIcon = if (onBack != null) {
                    {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Retour",
                                tint = lux.headerOnColor
                            )
                        }
                    }
                } else null,
                actions = {
                    IconButton(
                        onClick = onOpenDiscussions,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("actus_messages_shortcut_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadMessagesCount > 0) {
                                    BravUnreadCountBadge(count = unreadMessagesCount)
                                }
                            }
                        ) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = "Ouvrir les discussions",
                                tint = lux.headerOnColor
                            )
                        }
                    }
                    IconButton(
                        onClick = { searchPanelOpen = !searchPanelOpen },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("actus_search_toggle")
                    ) {
                        Icon(
                            if (searchPanelOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (searchPanelOpen) "Fermer la recherche" else "Rechercher sur Actus",
                            tint = lux.headerOnColor
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("actus_refresh_button")
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Actualiser le fil",
                            tint = lux.headerOnColor
                        )
                    }
                }
            )

            // ACTUS Section Title + Publish Action + Pill Tabs
            Column(
                modifier = Modifier
                    .widthIn(max = if (isWideLayout) 640.dp else availableWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ACTUS",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = lux.titlePurple
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = BravPurplePrimary,
                        onClick = { onOpenComposer(selectedTab == ActusFeedTab.STORIES) },
                        modifier = Modifier.testTag("actus_compose_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = BravWhite,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Publier",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = BravWhite
                            )
                        }
                    }
                }

                // Mockup-matched Pill Segmented Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActusFeedTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) BravPurplePrimary else palette.surface,
                            border = BorderStroke(
                                0.75.dp,
                                if (selected) BravPurplePrimary else palette.hairline
                            ),
                            onClick = { selectedTab = tab },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(tab.tag)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) BravWhite else palette.secondaryInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = searchPanelOpen) {
                    ActusSmartSearchBar(
                        query = searchQuery,
                        selectedCategory = searchCategory,
                        selectedSort = searchSort,
                        searchHistory = searchHistory,
                        trendingHashtags = trendingHashtags,
                        matchedPeople = matchedPeople,
                        palette = palette,
                        onQueryChange = { q ->
                            searchQuery = q
                            onSearchUsersQuery(q)
                        },
                        onSubmitSearch = { q -> onAddSearchHistory(q) },
                        onSelectCategory = { cat -> searchCategory = cat },
                        onSelectSort = { sort -> searchSort = sort },
                        onRemoveHistoryItem = onRemoveSearchHistoryItem,
                        onClearHistory = onClearSearchHistory,
                        onSelectPerson = { user ->
                            onOpenAuthorProfile(user.userId, user.username)
                        }
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = if (isWideLayout) 640.dp else availableWidth)
                            .fillMaxSize()
                            .testTag("actus_feed_list"),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        // Compact 24h Stories Rail (hidden on empty MINE tab to avoid clutter)
                        if (selectedTab != ActusFeedTab.MINE) {
                            item(key = "actus_stories_rail") {
                                ActusStoriesRail(
                                    currentUser = currentUser,
                                    myStories = myStories,
                                    contactStoryGroups = contactStoryGroups,
                                    palette = palette,
                                    onAddStory = { onOpenComposer(true) },
                                    onOpenStoryGroup = onOpenStoryGroup
                                )
                            }
                        }

                        if (isInitialLoading) {
                            item(key = "actus_skeleton") {
                                ActusSkeletonFeed(palette = palette)
                            }
                        } else if (displayedPosts.isEmpty()) {
                            item(key = "actus_empty") {
                                val (title, subtitle, cta) = when (selectedTab) {
                                    ActusFeedTab.ALL -> Triple(
                                        "Le fil Actus est calme",
                                        "Partagez une réflexion, un reportage photo ou une vidéo avec vos contacts.",
                                        "Créer une publication"
                                    )
                                    ActusFeedTab.STORIES -> Triple(
                                        "Aucun statut 24h actif",
                                        "Publiez un moment éphémère visible pendant 24 heures.",
                                        "Partager un statut 24h"
                                    )
                                    ActusFeedTab.MINE -> Triple(
                                        "Aucune publication personnelle",
                                        "Vos publications et statuts apparaîtront ici dès leur mise en ligne.",
                                        "Écrire maintenant"
                                    )
                                }
                                ActusEmptyState(
                                    title = title,
                                    subtitle = subtitle,
                                    ctaLabel = cta,
                                    palette = palette,
                                    onCreateClick = { onOpenComposer(selectedTab == ActusFeedTab.STORIES) }
                                )
                            }
                        } else {
                            items(displayedPosts, key = { it.id }) { post ->
                                val authorUser = knownUsers[post.userId]
                                ActusFeedCard(
                                    post = post,
                                    authorUser = authorUser,
                                    myUid = myUid,
                                    isOnline = post.userId in onlineUids || (authorUser?.isOnline == true),
                                    isReactionDockExpanded = expandedReactionPostId == post.id,
                                    workerApiClient = workerApiClient,
                                    onAuthorClick = onOpenAuthorProfile,
                                    onToggleLike = { onToggleLike(post) },
                                    onToggleReactionDock = {
                                        expandedReactionPostId = if (expandedReactionPostId == post.id) null else post.id
                                    },
                                    onSelectReaction = { rx ->
                                        expandedReactionPostId = null
                                        onToggleReaction(post, rx)
                                    },
                                    onOpenComments = { onOpenComments(post) },
                                    onShare = { onSharePost(post) },
                                    onOpenMediaIndex = { idx -> onOpenMediaGallery(post, idx) },
                                    onDeletePost = { onDeletePost(post) },
                                    onCopyFeedback = onCopyFeedback
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
private fun ActusStoriesRail(
    currentUser: User?,
    myStories: List<StatusPost>,
    contactStoryGroups: List<List<StatusPost>>,
    palette: ActusEditorialPalette,
    onAddStory: () -> Unit,
    onOpenStoryGroup: (List<StatusPost>, Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("actus_stories_rail"),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "my_story_tile") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(66.dp)
                    .clickable {
                        if (myStories.isNotEmpty()) onOpenStoryGroup(myStories, 0) else onAddStory()
                    }
                    .testTag("actus_my_story_tile")
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (myStories.isNotEmpty()) 2.dp else 1.dp,
                                color = if (myStories.isNotEmpty()) palette.accent else palette.hairline,
                                shape = CircleShape
                            )
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        UserAvatarWithStatus(
                            name = currentUser?.displayName ?: "Moi",
                            avatarUrl = currentUser?.avatarUrl.orEmpty(),
                            size = 48.dp
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = palette.accent,
                        border = BorderStroke(1.5.dp, palette.surface),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(onClick = onAddStory)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "Ajouter un statut 24h",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (myStories.isNotEmpty()) "Mon statut" else "Ajouter",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.primaryInk,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        items(contactStoryGroups, key = { it.first().userId }) { group ->
            val lead = group.first()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(66.dp)
                    .clickable { onOpenStoryGroup(group, 0) }
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(2.dp, palette.accent, CircleShape)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    UserAvatarWithStatus(
                        name = lead.userName,
                        avatarUrl = lead.userAvatar,
                        size = 48.dp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = lead.userName.substringBefore(' ').ifBlank { "Membre" },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.primaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MinimalStatusViewerDialog(
    groupStatuses: List<StatusPost>,
    initialIndex: Int,
    myUid: String,
    viewModel: BravViewModel,
    onDismiss: () -> Unit,
    onOpenComments: (String) -> Unit,
    onOpenPrivateChat: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentIndex by remember(groupStatuses) {
        mutableIntStateOf(initialIndex.coerceIn(0, (groupStatuses.size - 1).coerceAtLeast(0)))
    }
    val status = groupStatuses.getOrNull(currentIndex) ?: return

    var replyText by remember { mutableStateOf("") }
    var segmentProgress by remember(status.id) { mutableFloatStateOf(0f) }
    val isLiked = myUid in status.likedByUids
    val mediaUrls = remember(status) { status.resolvedMediaUrls() }

    LaunchedEffect(status.id) {
        if (myUid !in status.viewerIds) {
            viewModel.socialRepository.markStatusViewed(status.id, myUid)
        }
        if (status.type != "video") {
            val steps = 60
            for (step in 1..steps) {
                delay(100L)
                segmentProgress = step.toFloat() / steps.toFloat()
            }
            if (currentIndex < groupStatuses.lastIndex) {
                currentIndex++
            } else {
                onDismiss()
            }
        } else {
            segmentProgress = 1f
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val palette = rememberActusPalette()
        val accentBg = runCatching { Color(android.graphics.Color.parseColor(status.backgroundColor)) }
            .getOrDefault(palette.accent)
        val luminousStoryBg = remember(accentBg) {
            Color(
                red = 0.94f + accentBg.red * 0.05f,
                green = 0.94f + accentBg.green * 0.05f,
                blue = 0.96f + accentBg.blue * 0.04f,
                alpha = 1f
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("actus_story_viewer"),
            color = if (status.type == "text") luminousStoryBg else palette.canvas
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                when (status.type) {
                    "image" -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(mediaUrls.firstOrNull().orEmpty())
                                .crossfade(true)
                                .build(),
                            contentDescription = status.effectiveText().ifBlank { "Statut de ${status.userName}" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    "video" -> {
                        var localVideoFile by remember(status.mediaUrl) { mutableStateOf<File?>(null) }
                        var videoError by remember(status.mediaUrl) { mutableStateOf<String?>(null) }
                        LaunchedEffect(status.mediaUrl) {
                            viewModel.workerApiClient.downloadMediaToCache(context, status.mediaUrl, "status_${status.id}.mp4")
                                .onSuccess { localVideoFile = it }
                                .onFailure { videoError = it.localizedMessage ?: "Lecture vidéo impossible" }
                        }
                        val playbackUri = localVideoFile?.let { Uri.fromFile(it) }
                        if (playbackUri != null) {
                            val exoPlayer = remember(playbackUri) {
                                ExoPlayer.Builder(context).build().apply {
                                    setMediaItem(MediaItem.fromUri(playbackUri))
                                    prepare()
                                    playWhenReady = true
                                }
                            }
                            DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (videoError != null) {
                            Text(
                                text = videoError ?: "Erreur vidéo",
                                color = palette.primaryInk,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp)
                            )
                        } else {
                            CircularProgressIndicator(color = palette.accent, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(luminousStoryBg)
                                .padding(horizontal = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = palette.surface,
                                border = BorderStroke(1.dp, palette.hairline),
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    text = status.text,
                                    color = palette.primaryInk,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 30.dp)
                                )
                            }
                        }
                    }
                }

                // Tap left / right zones to advance or go back
                Row(modifier = Modifier.fillMaxSize().padding(vertical = 84.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) {
                                if (currentIndex > 0) currentIndex-- else onDismiss()
                            }
                    )
                    Spacer(Modifier.weight(1.4f))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) {
                                if (currentIndex < groupStatuses.lastIndex) currentIndex++ else onDismiss()
                            }
                    )
                }

                // Luminous Story Header & Progress Segments
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(palette.surface.copy(alpha = 0.94f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            groupStatuses.forEachIndexed { idx, _ ->
                                val prog = when {
                                    idx < currentIndex -> 1f
                                    idx == currentIndex -> segmentProgress
                                    else -> 0f
                                }
                                LinearProgressIndicator(
                                    progress = { prog },
                                    color = palette.accent,
                                    trackColor = palette.hairline,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatarWithStatus(name = status.userName, avatarUrl = status.userAvatar, size = 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    status.userName.ifBlank { "Membre BRAV" },
                                    color = palette.primaryInk,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${status.viewerIds.size} vue(s) · ${formatRelativeTimeFr(status.createdAt?.seconds)}",
                                    color = palette.secondaryInk,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = palette.primaryInk)
                            }
                        }
                    }
                    HorizontalDivider(color = palette.hairline, thickness = 0.75.dp)
                }

                // Luminous Bottom Interaction Strip with Custom Vector Reactions
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(palette.surface.copy(alpha = 0.95f))
                ) {
                    HorizontalDivider(color = palette.hairline, thickness = 0.75.dp)
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (isLiked) palette.likeActive.copy(alpha = 0.12f) else palette.subtleSurface,
                                border = BorderStroke(0.75.dp, if (isLiked) palette.likeActive.copy(alpha = 0.35f) else palette.hairline),
                                onClick = { viewModel.toggleStatusLike(status) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "J'aime",
                                        tint = if (isLiked) palette.likeActive else palette.primaryInk,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        "${status.likedByUids.size}",
                                        color = if (isLiked) palette.likeActive else palette.primaryInk,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = palette.subtleSurface,
                                border = BorderStroke(0.75.dp, palette.hairline),
                                onClick = { onOpenComments(status.id) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = "Commentaires",
                                        tint = palette.primaryInk,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("${status.commentCount}", color = palette.primaryInk, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(Modifier.weight(1f))
                            ACTUS_CURATED_REACTIONS.take(3).forEach { rx ->
                                val selected = status.reactions[rx.key]?.contains(myUid) == true
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected) palette.accent.copy(alpha = 0.14f) else palette.subtleSurface,
                                    border = BorderStroke(
                                        0.75.dp,
                                        if (selected) palette.accent.copy(alpha = 0.45f) else palette.hairline
                                    ),
                                    onClick = { viewModel.toggleStatusReaction(status, rx.key) }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ActusVectorReactionGlyph(reaction = rx, size = 18.dp)
                                    }
                                }
                            }
                        }

                        if (status.userId != myUid) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = replyText,
                                    onValueChange = { replyText = it },
                                    placeholder = { Text("Répondre en privé...", color = palette.secondaryInk) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = palette.subtleSurface,
                                        unfocusedContainerColor = palette.subtleSurface,
                                        focusedTextColor = palette.primaryInk,
                                        unfocusedTextColor = palette.primaryInk,
                                        focusedBorderColor = palette.accent.copy(alpha = 0.55f),
                                        unfocusedBorderColor = palette.hairline
                                    ),
                                    shape = RoundedCornerShape(999.dp),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    enabled = replyText.isNotBlank(),
                                    onClick = {
                                        scope.launch {
                                            val target = viewModel.userRepository.getUserById(status.userId)
                                            if (target != null) {
                                                viewModel.openOrCreateDirectChat(target).onSuccess { conv ->
                                                    viewModel.chatRepository.sendTextMessage(
                                                        conv,
                                                        myUid,
                                                        "Réponse Actus : $replyText",
                                                        peerPublicKey = target.publicKey
                                                    )
                                                    onOpenPrivateChat(conv.id)
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "Envoyer",
                                        tint = if (replyText.isNotBlank()) palette.accent else palette.secondaryInk
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
