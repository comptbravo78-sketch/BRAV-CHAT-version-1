package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.AiAutoReplyManager
import com.example.model.Conversation
import com.example.model.ConversationPref
import com.example.model.NotificationCounters
import com.example.model.User
import com.example.network.PresenceStatus
import com.example.ui.components.AiReplyDotButton
import com.example.ui.components.BravCircleIconButton
import com.example.ui.components.BravGoldHairlineDivider
import com.example.ui.components.BravLuxuryHeader
import com.example.ui.components.BravOffersLuxuryIcon
import com.example.ui.components.BravPatternIntensity
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPrimaryButton
import com.example.ui.components.BravSearchBarField
import com.example.ui.components.BravUnreadCountBadge
import com.example.ui.components.ChatLockPromptDialog
import com.example.ui.components.TypingDotsWave
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.components.VerifiedBlueBadge
import com.example.ui.components.bravAfricanPattern
import com.example.ui.components.formatShortTime
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRedSecondary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite

private enum class HomeChatFilter(val label: String) {
    ALL("Toutes"),
    UNREAD("Non lues"),
    GROUPS("Groupes"),
    ARCHIVED("Archivées"),
    LOCKED("Verrouillées")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeDashboardScreen(
    currentUserId: String,
    conversations: List<Conversation>,
    prefsMap: Map<String, ConversationPref>,
    unreadCountByConversation: Map<String, Int> = emptyMap(),
    notificationCounters: NotificationCounters = NotificationCounters(),
    presenceMap: Map<String, PresenceStatus>,
    typingConversations: Map<String, Set<String>>,
    savedContacts: List<User>,
    hasPinConfigured: Boolean,
    biometricLockEnabled: Boolean = false,
    isAdmin: Boolean,
    compactLayout: Boolean = false,
    smsUnreadCount: Int = 0,
    offersCount: Int = 0,
    verifiedUserIds: Set<String> = emptySet(),
    aiContactRulesJson: String = "[]",
    globalBravAutoReplyEnabled: Boolean = false,
    onToggleConversationAi: (contactKey: String, contactLabel: String) -> Unit = { _, _ -> },
    onVerifyPin: suspend (String) -> Boolean,
    onOpenConversation: (String) -> Unit,
    onCreateGroup: (List<User>, String, String) -> Unit,
    onJoinGroupByCode: (String) -> Unit,
    onUpdatePref: (String, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onNavigateToContacts: () -> Unit,
    onOpenStatuses: () -> Unit,
    onOpenDialer: () -> Unit,
    onRequestDefaultDialer: () -> Unit,
    onRequestDefaultLauncher: () -> Unit = {},
    onOpenSms: () -> Unit = {},
    onOpenAiAssistant: () -> Unit = {},
    onOpenMusic: () -> Unit = {},
    onOpenMap: () -> Unit,
    onOpenOffers: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    val lux = BravTheme.colors
    var filter by remember { mutableStateOf(HomeChatFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var overflowExpanded by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var actionTargetConv by remember { mutableStateOf<Conversation?>(null) }
    var deleteConfirmConv by remember { mutableStateOf<Conversation?>(null) }
    var lockedConvToOpen by remember { mutableStateOf<String?>(null) }
    var lockedVaultUnlocked by remember { mutableStateOf(false) }
    var promptUnlockVault by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    val filtered = remember(
        conversations,
        prefsMap,
        unreadCountByConversation,
        filter,
        searchQuery,
        currentUserId,
        lockedVaultUnlocked
    ) {
        conversations
            .filter { conv ->
                val pref = prefsMap[conv.id]
                if (pref?.hiddenLocally == true) return@filter false
                val isLocked = pref?.locked == true
                val isConvUnread = (unreadCountByConversation[conv.id] ?: 0) > 0 || pref?.markedUnread == true
                when (filter) {
                    HomeChatFilter.ALL -> !isLocked && pref?.archived != true
                    HomeChatFilter.UNREAD -> !isLocked && isConvUnread
                    HomeChatFilter.GROUPS -> !isLocked && conv.isGroup && pref?.archived != true
                    HomeChatFilter.ARCHIVED -> !isLocked && pref?.archived == true
                    HomeChatFilter.LOCKED -> lockedVaultUnlocked && isLocked
                }
            }
            .filter { conv ->
                if (searchQuery.isBlank()) return@filter true
                val title = resolveConversationTitle(conv, currentUserId)
                title.contains(searchQuery, ignoreCase = true) ||
                    conv.lastMessage.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<Conversation> { prefsMap[it.id]?.pinned == true }
                    .thenByDescending { it.lastMessageTimestamp?.seconds ?: it.updatedAt?.seconds ?: 0L }
            )
    }

    Scaffold(
        containerColor = lux.ivoryCanvas,
        topBar = {
            BravLuxuryHeader(
                title = "BRAV Chat",
                showLogo = true,
                pattern = BravPatternType.KENTE_DIAMOND,
                actions = {
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("home_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (notificationCounters.totalUnread > 0) {
                                    BravUnreadCountBadge(count = notificationCounters.totalUnread)
                                }
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Notifications,
                                contentDescription = "Notifications",
                                tint = lux.headerOnColor
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenDialer,
                        modifier = Modifier.testTag("home_dialer_button")
                    ) {
                        Icon(
                            Icons.Rounded.Dialpad,
                            contentDescription = "Clavier d'appel",
                            tint = lux.headerOnColor
                        )
                    }
                    IconButton(
                        onClick = { runCatching { searchFocusRequester.requestFocus() } },
                        modifier = Modifier.testTag("home_search_button")
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Rechercher",
                            tint = lux.headerOnColor
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { overflowExpanded = true },
                            modifier = Modifier.testTag("home_overflow_menu_button")
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "Menu",
                                tint = lux.headerOnColor
                            )
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                            containerColor = lux.modalSurface
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (smsUnreadCount > 0) "Messages SMS ($smsUnreadCount)" else "Messages SMS") },
                                leadingIcon = { Icon(Icons.Rounded.Sms, contentDescription = null) },
                                onClick = {
                                    overflowExpanded = false
                                    onOpenSms()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Musique YouTube") },
                                leadingIcon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = lux.titlePurple) },
                                onClick = {
                                    overflowExpanded = false
                                    onOpenMusic()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Assistant IA") },
                                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = lux.titlePurple) },
                                onClick = {
                                    overflowExpanded = false
                                    onOpenAiAssistant()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Nouveau groupe") },
                                leadingIcon = { Icon(Icons.Rounded.GroupAdd, contentDescription = null) },
                                onClick = {
                                    overflowExpanded = false
                                    showCreateGroupDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rejoindre par code") },
                                leadingIcon = { Icon(Icons.Rounded.Group, contentDescription = null) },
                                onClick = {
                                    overflowExpanded = false
                                    showJoinGroupDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Numérotation par défaut") },
                                leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
                                onClick = {
                                    overflowExpanded = false
                                    onRequestDefaultDialer()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Accueil système par défaut") },
                                leadingIcon = { Icon(Icons.Rounded.Home, contentDescription = null, tint = lux.titlePurple) },
                                onClick = {
                                    overflowExpanded = false
                                    onRequestDefaultLauncher()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Statuts 24h") },
                                leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
                                onClick = {
                                    overflowExpanded = false
                                    onOpenStatuses()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Carte en direct") },
                                leadingIcon = { Icon(Icons.Rounded.Map, contentDescription = null) },
                                onClick = {
                                    overflowExpanded = false
                                    onOpenMap()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Annonces & Offres") },
                                leadingIcon = { Icon(Icons.Rounded.Storefront, contentDescription = null) },
                                onClick = {
                                    overflowExpanded = false
                                    onOpenOffers()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Mon profil") },
                                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                onClick = {
                                    overflowExpanded = false
                                    onOpenProfile()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Paramètres") },
                                leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                onClick = {
                                    overflowExpanded = false
                                    onOpenSettings()
                                }
                            )
                            if (isAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Administration", color = BravRedSecondary) },
                                    leadingIcon = { Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = BravRedSecondary) },
                                    onClick = {
                                        overflowExpanded = false
                                        onOpenAdmin()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToContacts,
                containerColor = BravPurplePrimary,
                contentColor = BravWhite,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .size(56.dp)
                    .testTag("home_new_chat_fab")
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = "Nouvelle discussion", modifier = Modifier.size(24.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(lux.ivoryCanvas)
                .bravAfricanPattern(
                    pattern = BravPatternType.KENTE_DIAMOND,
                    primaryColor = lux.watermarkGold,
                    secondaryColor = lux.watermarkViolet,
                    opacity = BravPatternIntensity.SUBTLE_WATERMARK
                )
        ) {
            // Accès direct OFFRES ET ANNONCES dès l'écran d'accueil
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val offersInteraction = remember { MutableInteractionSource() }
                val offersPressed by offersInteraction.collectIsPressedAsState()
                val offersScale by animateFloatAsState(
                    targetValue = if (offersPressed) 0.98f else 1f,
                    label = "home_offers_scale"
                )
                Surface(
                    onClick = onOpenOffers,
                    interactionSource = offersInteraction,
                    shape = RoundedCornerShape(14.dp),
                    color = lux.translucentCardSurface,
                    border = BorderStroke(0.9.dp, lux.champagneGold.copy(alpha = 0.72f)),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(scaleX = offersScale, scaleY = offersScale)
                        .testTag("home_offers_tile")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bravAfricanPattern(
                                pattern = BravPatternType.HERITAGE_COVER,
                                primaryColor = lux.watermarkGold,
                                secondaryColor = lux.watermarkViolet,
                                opacity = BravPatternIntensity.LIGHT_DECOR,
                                scale = 0.9f
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BravOffersLuxuryIcon(modifier = Modifier.size(32.dp))
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "OFFRES ET ANNONCES",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = lux.titlePurple,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (offersCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = BravRedSecondary,
                                    modifier = Modifier.testTag("home_offers_count_badge")
                                ) {
                                    Text(
                                        text = "$offersCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BravWhite,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            if (notificationCounters.offersJobsUnread > 0) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = lux.champagneGold,
                                    modifier = Modifier.testTag("home_offers_unread_badge")
                                ) {
                                    Text(
                                        text = "+${notificationCounters.offersJobsUnread}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = lux.inkPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = Color.Transparent,
                            border = BorderStroke(0.75.dp, lux.violetContour)
                        ) {
                            Text(
                                text = "Ouvrir",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = lux.titlePurple,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discussions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = lux.titlePurple
                    )
                    if (lockedVaultUnlocked && filter == HomeChatFilter.LOCKED) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = BravRedSecondary.copy(alpha = 0.12f),
                            border = BorderStroke(0.75.dp, BravRedSecondary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, tint = BravRedSecondary, modifier = Modifier.size(13.dp))
                                Text(
                                    text = "Coffre ouvert",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BravRedSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BravSearchBarField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Rechercher...",
                        inputTestTag = "home_search_input",
                        inputModifier = Modifier.focusRequester(searchFocusRequester),
                        modifier = Modifier.weight(1f)
                    )
                    BravCircleIconButton(
                        icon = Icons.Rounded.Tune,
                        contentDescriptionText = "Filtrer les discussions",
                        onClick = {
                            filter = when (filter) {
                                HomeChatFilter.ALL -> HomeChatFilter.UNREAD
                                HomeChatFilter.UNREAD -> HomeChatFilter.GROUPS
                                else -> HomeChatFilter.ALL
                            }
                        },
                        containerColor = if (filter != HomeChatFilter.ALL) BravPurplePrimary.copy(alpha = 0.14f) else lux.translucentSubtleSurface,
                        iconTint = if (filter != HomeChatFilter.ALL) lux.titlePurple else lux.inkPrimary
                    )
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("home_filter_row")
            ) {
                item {
                    Surface(
                        onClick = onOpenSms,
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Transparent,
                        border = BorderStroke(0.8.dp, lux.champagneGold.copy(alpha = 0.65f)),
                        modifier = Modifier.testTag("home_open_sms_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Rounded.Sms, contentDescription = null, tint = lux.titlePurple, modifier = Modifier.size(15.dp))
                            Text(
                                text = if (smsUnreadCount > 0) "SMS ($smsUnreadCount)" else "SMS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = lux.titlePurple
                            )
                        }
                    }
                }
                items(HomeChatFilter.entries) { item ->
                    val selected = filter == item
                    Surface(
                        onClick = {
                            if (item == HomeChatFilter.LOCKED) {
                                if (lockedVaultUnlocked) {
                                    filter = HomeChatFilter.LOCKED
                                } else {
                                    promptUnlockVault = true
                                }
                            } else {
                                lockedVaultUnlocked = false
                                filter = item
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) BravPurplePrimary else Color.Transparent,
                        border = BorderStroke(
                            0.8.dp,
                            if (selected) BravPurplePrimary else lux.violetContour
                        ),
                        modifier = Modifier.testTag("home_filter_${item.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (item == HomeChatFilter.LOCKED) {
                                Icon(
                                    Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = if (selected) BravWhite else lux.inkSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) BravWhite else lux.inkSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BravPurplePrimary.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, lux.champagneGold.copy(alpha = 0.45f)),
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = lux.titlePurple,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Text(
                            text = if (searchQuery.isNotBlank()) "Aucun résultat" else "Aucune discussion",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = lux.inkPrimary
                        )
                        BravPrimaryButton(
                            text = "Nouvelle discussion",
                            onClick = onNavigateToContacts
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 92.dp)
                ) {
                    items(filtered, key = { it.id }) { conv ->
                        val pref = prefsMap[conv.id]
                        val peerUid = if (!conv.isGroup) conv.participants.firstOrNull { it != currentUserId }.orEmpty() else ""
                        val title = resolveConversationTitle(conv, currentUserId)
                        val avatar = if (conv.isGroup) conv.groupAvatarUrl else conv.participantAvatars[peerUid].orEmpty()
                        val isPeerOnline = peerUid.isNotBlank() && presenceMap[peerUid]?.online == true
                        val typingSet = (typingConversations[conv.id].orEmpty() + conv.typingUsers)
                            .distinct()
                            .filter { it != currentUserId }
                        val unreadCount = unreadCountByConversation[conv.id]
                            ?: if (pref?.markedUnread == true) 1 else 0
                        val isUnread = unreadCount > 0
                        val isPeerVerified = !conv.isGroup && peerUid.isNotBlank() && peerUid in verifiedUserIds

                        Surface(
                            color = if (isUnread) lux.translucentCardSurface else lux.translucentSubtleSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (pref?.locked == true) {
                                            lockedConvToOpen = conv.id
                                        } else {
                                            onOpenConversation(conv.id)
                                        }
                                    },
                                    onLongClick = { actionTargetConv = conv }
                                )
                                .testTag("home_conversation_${conv.id}"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                0.75.dp,
                                if (isUnread) lux.champagneGold.copy(alpha = 0.72f) else lux.violetContour
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = if (compactLayout) 9.dp else 11.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .then(
                                            if (isPeerVerified) {
                                                Modifier
                                                    .border(1.5.dp, lux.champagneGold, CircleShape)
                                                    .padding(2.dp)
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    UserAvatarWithStatus(
                                        name = title,
                                        avatarUrl = avatar,
                                        isOnlineRealtime = if (conv.isGroup) null else isPeerOnline,
                                        size = if (compactLayout) 44.dp else 52.dp
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = lux.inkPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isPeerVerified) {
                                                VerifiedBlueBadge(size = 15.dp)
                                            }
                                        }
                                        val ts = conv.lastMessageTimestamp ?: conv.updatedAt
                                        if (ts != null) {
                                            Text(
                                                text = formatShortTime(ts.seconds),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isUnread) lux.titlePurple else lux.inkSecondary
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        if (typingSet.isNotEmpty()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 8.dp)
                                                    .testTag("home_typing_indicator_${conv.id}")
                                            ) {
                                                TypingDotsWave(color = lux.titlePurple, dotSize = 5.dp, spacing = 3.dp)
                                                Text(
                                                    text = "écrit...",
                                                    color = lux.titlePurple,
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = conv.lastMessage.ifBlank { "Appuyez pour discuter" },
                                                color = if (isUnread) lux.inkPrimary else lux.inkSecondary,
                                                fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val convDotActive = AiAutoReplyManager.isDirectDotActive(
                                                contactCandidates = listOfNotNull(peerUid.takeIf { it.isNotBlank() }, conv.id),
                                                rulesJson = aiContactRulesJson,
                                                globalAutoReplyEnabled = globalBravAutoReplyEnabled
                                            )
                                            if (pref?.muted == true) {
                                                Icon(
                                                    Icons.Rounded.NotificationsOff,
                                                    contentDescription = "Muet",
                                                    tint = lux.inkSecondary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            if (pref?.pinned == true) {
                                                Icon(
                                                    Icons.Rounded.PushPin,
                                                    contentDescription = "Épinglée",
                                                    tint = lux.champagneGold,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            if (pref?.locked == true) {
                                                Icon(
                                                    Icons.Rounded.Lock,
                                                    contentDescription = "Verrouillée",
                                                    tint = BravRedSecondary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            if (isUnread) {
                                                BravUnreadCountBadge(count = unreadCount)
                                            }
                                            AiReplyDotButton(
                                                isActive = convDotActive,
                                                dotSize = 11.dp,
                                                touchSize = 48.dp,
                                                contentDescriptionText = if (convDotActive) {
                                                    "IA directe active pour $title"
                                                } else {
                                                    "Activer IA directe pour $title"
                                                },
                                                testTagName = "home_conv_ai_dot_${conv.id}",
                                                onClick = {
                                                    onToggleConversationAi(peerUid.ifBlank { conv.id }, title)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "home_footer_divider") {
                        BravGoldHairlineDivider(
                            withGoldTouch = true,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }
    }

    actionTargetConv?.let { conv ->
        ConversationActionsDialog(
            title = resolveConversationTitle(conv, currentUserId),
            pref = prefsMap[conv.id],
            onTogglePin = {
                onUpdatePref(conv.id, null, !(prefsMap[conv.id]?.pinned ?: false), null, null, null, null)
                actionTargetConv = null
            },
            onToggleArchive = {
                onUpdatePref(conv.id, !(prefsMap[conv.id]?.archived ?: false), null, null, null, null, null)
                actionTargetConv = null
            },
            onToggleMute = {
                onUpdatePref(conv.id, null, null, !(prefsMap[conv.id]?.muted ?: false), null, null, null)
                actionTargetConv = null
            },
            onToggleLock = {
                onUpdatePref(conv.id, null, null, null, !(prefsMap[conv.id]?.locked ?: false), null, null)
                actionTargetConv = null
            },
            onToggleUnread = {
                onUpdatePref(conv.id, null, null, null, null, !(prefsMap[conv.id]?.markedUnread ?: false), null)
                actionTargetConv = null
            },
            onRequestDelete = {
                deleteConfirmConv = conv
                actionTargetConv = null
            },
            onDismiss = { actionTargetConv = null }
        )
    }

    deleteConfirmConv?.let { conv ->
        DeleteConversationDialog(
            conv = conv,
            title = resolveConversationTitle(conv, currentUserId),
            onConfirm = {
                onDeleteConversation(conv.id)
                deleteConfirmConv = null
            },
            onDismiss = { deleteConfirmConv = null }
        )
    }

    lockedConvToOpen?.let { cid ->
        ChatLockPromptDialog(
            title = "Messages verrouillés",
            hasPinConfigured = hasPinConfigured,
            preferBiometric = biometricLockEnabled,
            onVerifyPin = onVerifyPin,
            onSuccess = {
                lockedConvToOpen = null
                onOpenConversation(cid)
            },
            onDismiss = { lockedConvToOpen = null }
        )
    }

    if (promptUnlockVault) {
        ChatLockPromptDialog(
            title = "Messages verrouillés",
            hasPinConfigured = hasPinConfigured,
            preferBiometric = biometricLockEnabled,
            onVerifyPin = onVerifyPin,
            onSuccess = {
                promptUnlockVault = false
                lockedVaultUnlocked = true
                filter = HomeChatFilter.LOCKED
            },
            onDismiss = { promptUnlockVault = false }
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            savedContacts = savedContacts,
            onCreate = { members, name, desc ->
                onCreateGroup(members, name, desc)
                showCreateGroupDialog = false
            },
            onDismiss = { showCreateGroupDialog = false }
        )
    }

    if (showJoinGroupDialog) {
        JoinGroupByCodeDialog(
            onJoin = { code ->
                onJoinGroupByCode(code)
                showJoinGroupDialog = false
            },
            onDismiss = { showJoinGroupDialog = false }
        )
    }
}

internal fun resolveConversationTitle(conv: Conversation, currentUserId: String): String {
    if (conv.isGroup) return conv.groupName.ifBlank { "Groupe BRAV" }
    val peerId = conv.participants.firstOrNull { it != currentUserId }.orEmpty()
    return conv.participantNames[peerId]?.ifBlank { "Membre BRAV" } ?: "Membre BRAV"
}
