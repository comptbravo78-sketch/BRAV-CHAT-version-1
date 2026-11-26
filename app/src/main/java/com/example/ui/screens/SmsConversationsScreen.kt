package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AiAutoReplyManager
import com.example.network.WorkerAiReply
import com.example.repository.SmsThreadSummary
import com.example.ui.components.AiReplyDotButton
import com.example.ui.components.AiReplySuggestionBar
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravGoldHairlineDivider
import com.example.ui.components.BravLuxuryCard
import com.example.ui.components.BravLuxuryHeader
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.components.BravSearchBarField
import com.example.ui.components.ChatLockPromptDialog
import com.example.ui.components.LuxuryEmojiPickerSheet
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.components.formatShortDateTime
import com.example.ui.components.formatShortTime
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravPurpleDeep
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import com.example.util.DefaultSmsHelper
import com.example.util.PhoneUtils
import com.example.viewmodel.BravViewModel
import kotlinx.coroutines.launch

internal fun filterSmsThreadsForVault(
    threads: List<SmsThreadSummary>,
    archivedKeys: Set<String>,
    lockedKeys: Set<String>,
    pinnedKeys: Set<String>,
    showArchived: Boolean,
    showLocked: Boolean,
    lockedVaultUnlocked: Boolean,
    searchQuery: String
): List<SmsThreadSummary> {
    return threads.filter { thread ->
        val archived = thread.threadKey in archivedKeys
        val locked = thread.threadKey in lockedKeys
        val visibilityMatch = when {
            showLocked -> lockedVaultUnlocked && locked
            showArchived -> !locked && archived
            else -> !locked && !archived
        }
        val queryMatch = searchQuery.isBlank() ||
            thread.displayName.contains(searchQuery, ignoreCase = true) ||
            thread.address.contains(searchQuery, ignoreCase = true) ||
            thread.lastBody.contains(searchQuery, ignoreCase = true)
        visibilityMatch && queryMatch
    }.sortedWith(
        compareByDescending<SmsThreadSummary> { it.threadKey in pinnedKeys }
            .thenByDescending { it.timestampMs }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SmsConversationsScreen(
    viewModel: BravViewModel,
    initialAddress: String? = null,
    initialBody: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val threads by viewModel.smsThreads.collectAsStateWithLifecycle()
    val appPrefs by viewModel.appPrefs.collectAsStateWithLifecycle()
    val phoneContacts by viewModel.phoneContacts.collectAsStateWithLifecycle()
    val savedUsers by viewModel.savedContactUsers.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(DefaultSmsHelper.hasSmsReadPermission(context)) }
    var isDefaultSms by remember { mutableStateOf(DefaultSmsHelper.isBravDefaultSms(context)) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedThreadKey by remember { mutableStateOf<String?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var showLocked by remember { mutableStateOf(false) }
    var lockedVaultUnlocked by remember { mutableStateOf(false) }
    var promptUnlockSmsVault by remember { mutableStateOf(false) }
    var showNewSmsComposer by remember { mutableStateOf(!initialAddress.isNullOrBlank() || !initialBody.isNullOrBlank()) }
    var newSmsTo by remember { mutableStateOf(initialAddress.orEmpty()) }
    var newSmsBody by remember { mutableStateOf(initialBody.orEmpty()) }
    var pendingLockedThread by remember { mutableStateOf<SmsThreadSummary?>(null) }
    var pendingDeleteThread by remember { mutableStateOf<SmsThreadSummary?>(null) }

    val smsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasPermission = grants.values.any { it } || DefaultSmsHelper.hasSmsReadPermission(context)
        if (hasPermission) viewModel.refreshSmsThreads()
    }

    val defaultSmsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultSms = DefaultSmsHelper.isBravDefaultSms(context)
        if (isDefaultSms && !hasPermission) {
            smsPermLauncher.launch(DefaultSmsHelper.requiredSmsPermissions())
        } else if (hasPermission) {
            viewModel.refreshSmsThreads()
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermission) {
            viewModel.refreshSmsThreads()
        }
        viewModel.syncDeviceContacts()
    }

    val selectedThread = threads.firstOrNull { it.threadKey == selectedThreadKey }
    if (selectedThread != null) {
        val threadCandidates = remember(selectedThread) {
            listOfNotNull(
                selectedThread.normalizedAddress.takeIf { it.isNotBlank() },
                selectedThread.address.takeIf { it.isNotBlank() },
                selectedThread.threadKey
            )
        }
        val threadAiActive = remember(threadCandidates, appPrefs.aiAutoReplyContactRulesJson, appPrefs.aiAutoReplySmsEnabled) {
            AiAutoReplyManager.isDirectDotActive(
                contactCandidates = threadCandidates,
                rulesJson = appPrefs.aiAutoReplyContactRulesJson,
                globalAutoReplyEnabled = appPrefs.aiAutoReplySmsEnabled
            )
        }
        val isSelectedLocked = selectedThread.threadKey in appPrefs.smsLockedThreads
        val recentEmojis = remember(appPrefs.recentEmojisJson) {
            com.example.ui.components.BravEmojiCatalog.parseListJson(appPrefs.recentEmojisJson)
        }
        val favoriteEmojis = remember(appPrefs.favoriteEmojisJson) {
            com.example.ui.components.BravEmojiCatalog.parseListJson(appPrefs.favoriteEmojisJson)
        }
        SmsThreadDetailScreen(
            thread = selectedThread,
            isDefaultSms = isDefaultSms,
            isAiAutoActive = threadAiActive,
            isLocked = isSelectedLocked,
            recentEmojis = recentEmojis,
            favoriteEmojis = favoriteEmojis,
            preferredSkinTone = appPrefs.preferredEmojiSkinTone,
            onRecordEmoji = { e -> scope.launch { viewModel.prefsRepository.recordRecentEmoji(e) } },
            onToggleFavoriteEmoji = { e -> scope.launch { viewModel.prefsRepository.toggleFavoriteEmoji(e) } },
            onSelectSkinTone = { tone -> scope.launch { viewModel.prefsRepository.setPreferredEmojiSkinTone(tone) } },
            onRequestDefaultSms = {
                DefaultSmsHelper.createRequestDefaultSmsIntent(context)?.let {
                    runCatching { defaultSmsLauncher.launch(it) }
                }
            },
            onToggleLock = {
                val nextLocked = !isSelectedLocked
                scope.launch {
                    viewModel.prefsRepository.toggleSmsThreadLocked(selectedThread.threadKey, nextLocked)
                    if (nextLocked) {
                        viewModel.showMessage("SMS masqué dans Verrouillés")
                        selectedThreadKey = null
                    } else {
                        viewModel.showMessage("SMS déverrouillé")
                    }
                }
            },
            onToggleAiAuto = {
                viewModel.toggleConversationDirectAi(
                    contactKey = selectedThread.normalizedAddress.ifBlank { selectedThread.address },
                    contactLabel = selectedThread.displayName,
                    globalEnabled = appPrefs.aiAutoReplySmsEnabled,
                    candidates = threadCandidates
                )
            },
            onGenerateAiReply = { targetPreview, historyPairs ->
                viewModel.generateDirectAiReply(
                    channel = "sms",
                    contactKey = selectedThread.normalizedAddress.ifBlank { selectedThread.address },
                    contactLabel = selectedThread.displayName,
                    incomingMessage = targetPreview,
                    historyPairs = historyPairs
                )
            },
            onDeleteMessage = { msg ->
                viewModel.deleteSmsMessage(msg.id, msg.isMms)
            },
            onDeleteThread = {
                viewModel.deleteSmsThread(selectedThread.threadId)
                selectedThreadKey = null
            },
            onShowMessage = { viewModel.showMessage(it) },
            onSend = { body -> viewModel.sendSmsMessage(selectedThread.address, body) },
            onMarkRead = { viewModel.markSmsRead(selectedThread.threadId) },
            onBack = { selectedThreadKey = null }
        )
        return
    }

    BackHandler {
        if (showLocked || showArchived) {
            showLocked = false
            showArchived = false
            lockedVaultUnlocked = false
        } else {
            onBack()
        }
    }

    val visibleThreads = remember(
        threads,
        searchQuery,
        showArchived,
        showLocked,
        lockedVaultUnlocked,
        appPrefs.smsArchivedThreads,
        appPrefs.smsLockedThreads,
        appPrefs.smsPinnedThreads
    ) {
        filterSmsThreadsForVault(
            threads = threads,
            archivedKeys = appPrefs.smsArchivedThreads,
            lockedKeys = appPrefs.smsLockedThreads,
            pinnedKeys = appPrefs.smsPinnedThreads,
            showArchived = showArchived,
            showLocked = showLocked,
            lockedVaultUnlocked = lockedVaultUnlocked,
            searchQuery = searchQuery
        )
    }

    val lux = BravTheme.colors
    Scaffold(
        containerColor = lux.ivoryCanvas,
        topBar = {
            BravLuxuryHeader(
                title = when {
                    showLocked -> "SMS verrouillés"
                    showArchived -> "SMS archivés"
                    else -> "Messages SMS"
                },
                showLogo = true,
                pattern = BravPatternType.KENTE_DIAMOND,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (showLocked || showArchived) {
                                showLocked = false
                                showArchived = false
                                lockedVaultUnlocked = false
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("sms_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour", tint = lux.headerOnColor)
                    }
                },
                actions = {
                    if (appPrefs.smsArchivedThreads.isNotEmpty()) {
                        TextButton(onClick = {
                            showLocked = false
                            lockedVaultUnlocked = false
                            showArchived = !showArchived
                        }) {
                            Text(
                                text = if (showArchived) "Boîte" else "Archives (${appPrefs.smsArchivedThreads.size})",
                                color = lux.headerOnColor
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            if (showLocked) {
                                showLocked = false
                                lockedVaultUnlocked = false
                            } else {
                                promptUnlockSmsVault = true
                            }
                        },
                        modifier = Modifier.testTag("sms_locked_vault_button")
                    ) {
                        Icon(
                            if (showLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = lux.headerOnColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (showLocked) "Boîte" else "Verrouillés", color = lux.headerOnColor)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newSmsTo = ""
                    newSmsBody = ""
                    showNewSmsComposer = true
                },
                containerColor = BravPurplePrimary,
                contentColor = BravWhite,
                shape = CircleShape,
                modifier = Modifier.testTag("new_sms_fab")
            ) {
                Icon(Icons.Rounded.Sms, contentDescription = "Nouveau SMS")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(lux.ivoryCanvas)
        ) {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.KENTE_DIAMOND,
                variant = BravPatternVariant.GOLD_ON_IVORY,
                opacity = 0.095f,
                scale = 1.05f
            )
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (!isDefaultSms && DefaultSmsHelper.isDefaultSmsSupported(context)) {
                    BravLuxuryCard(
                        shapeRadius = 18.dp,
                        accentBorder = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Définir BRAV comme appli SMS", fontWeight = FontWeight.SemiBold, color = lux.inkPrimary)
                                Text(
                                    "Recevez et gérez vos SMS/MMS directement ici.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = lux.inkSecondary
                                )
                            }
                            Button(onClick = {
                                DefaultSmsHelper.createRequestDefaultSmsIntent(context)?.let {
                                    runCatching { defaultSmsLauncher.launch(it) }
                                }
                            }) {
                                Text("Activer")
                            }
                        }
                    }
                }

                if (!hasPermission) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Sms,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = lux.titlePurple
                            )
                            Text("Autorisation SMS requise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = lux.inkPrimary)
                            Text(
                                "Autorisez BRAV Chat à afficher vos conversations SMS et y répondre.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = lux.inkSecondary
                            )
                            Button(onClick = { smsPermLauncher.launch(DefaultSmsHelper.requiredSmsPermissions()) }) {
                                Text("Autoriser l'accès SMS")
                            }
                        }
                    }
                    return@Column
                }

                BravSearchBarField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Rechercher un numéro ou un SMS…",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )

                if (visibleThreads.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isBlank()) "Aucune conversation SMS trouvée." else "Aucun résultat pour « $searchQuery ».",
                            color = lux.inkSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleThreads, key = { it.threadKey }) { thread ->
                            var menuOpen by remember { mutableStateOf(false) }
                            val isPinned = thread.threadKey in appPrefs.smsPinnedThreads
                            val isLocked = thread.threadKey in appPrefs.smsLockedThreads
                            val isArchived = thread.threadKey in appPrefs.smsArchivedThreads

                            Box {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = lux.cardSurface,
                                    border = BorderStroke(0.75.dp, lux.hairline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (isLocked) {
                                                    pendingLockedThread = thread
                                                } else {
                                                    selectedThreadKey = thread.threadKey
                                                }
                                            },
                                            onLongClick = { menuOpen = true }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                UserAvatarWithStatus(
                                    name = thread.displayName,
                                    avatarUrl = thread.photoUri,
                                    availabilityStatus = "offline",
                                    size = 48.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = thread.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (thread.unreadCount > 0) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = formatShortDateTime(thread.timestampMs / 1000L),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (thread.localityHint.isNotBlank()) {
                                            Text(
                                                text = thread.localityHint,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (isPinned) {
                                            Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(13.dp))
                                        }
                                        if (isLocked) {
                                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isLocked) "Conversation SMS verrouillée" else thread.lastBody,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            val rowCandidates = listOfNotNull(
                                                thread.normalizedAddress.takeIf { it.isNotBlank() },
                                                thread.address.takeIf { it.isNotBlank() },
                                                thread.threadKey
                                            )
                                            val rowDotActive = AiAutoReplyManager.isDirectDotActive(
                                                contactCandidates = rowCandidates,
                                                rulesJson = appPrefs.aiAutoReplyContactRulesJson,
                                                globalAutoReplyEnabled = appPrefs.aiAutoReplySmsEnabled
                                            )
                                            if (thread.unreadCount > 0) {
                                                Badge { Text("${thread.unreadCount}") }
                                            }
                                            AiReplyDotButton(
                                                isActive = rowDotActive,
                                                dotSize = 11.dp,
                                                touchSize = 48.dp,
                                                contentDescriptionText = if (rowDotActive) {
                                                    "IA directe active pour ${thread.displayName}"
                                                } else {
                                                    "Activer IA directe pour ${thread.displayName}"
                                                },
                                                testTagName = "sms_thread_ai_dot_${thread.threadKey}",
                                                onClick = {
                                                    viewModel.toggleConversationDirectAi(
                                                        contactKey = thread.normalizedAddress.ifBlank { thread.address },
                                                        contactLabel = thread.displayName,
                                                        globalEnabled = appPrefs.aiAutoReplySmsEnabled,
                                                        candidates = rowCandidates
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                                }
                            }

                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                containerColor = lux.modalSurface
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                                    text = { Text(if (isPinned) "Désépingler" else "Épingler") },
                                    onClick = {
                                        menuOpen = false
                                        scope.launch {
                                            viewModel.prefsRepository.toggleSmsThreadPinned(
                                                thread.threadKey, !isPinned
                                            )
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Rounded.Archive, null) },
                                    text = { Text(if (isArchived) "Désarchiver" else "Archiver") },
                                    onClick = {
                                        menuOpen = false
                                        scope.launch {
                                            viewModel.prefsRepository.toggleSmsThreadArchived(
                                                thread.threadKey, !isArchived
                                            )
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                                    text = { Text(if (isLocked) "Déverrouiller" else "Verrouiller") },
                                    onClick = {
                                        menuOpen = false
                                        scope.launch {
                                            viewModel.prefsRepository.toggleSmsThreadLocked(
                                                thread.threadKey, !isLocked
                                            )
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                                    text = { Text("Supprimer le fil", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuOpen = false
                                        pendingDeleteThread = thread
                                    }
                                )
                            }
                            }
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showNewSmsComposer,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
    ) {
        NewSmsComposerSheet(
            initialRecipient = newSmsTo,
            initialBody = newSmsBody,
            phoneContacts = phoneContacts,
            savedUsers = savedUsers,
            existingThreads = threads,
            onGenerateAiDraft = { recipient, hint ->
                viewModel.generateDirectAiReply(
                    channel = "sms",
                    contactKey = PhoneUtils.normalize(recipient).ifBlank { recipient.ifBlank { "new_sms" } },
                    contactLabel = recipient.ifBlank { "Destinataire" },
                    incomingMessage = hint.ifBlank { "Rédige un SMS poli, clair et concis." }
                )
            },
            onOpenExistingThread = { existing ->
                showNewSmsComposer = false
                selectedThreadKey = existing.threadKey
            },
            onSendSms = { recipient, body ->
                viewModel.sendSmsMessage(recipient, body) { ok ->
                    if (ok) {
                        showNewSmsComposer = false
                        val norm = PhoneUtils.normalize(recipient)
                        val match = threads.firstOrNull {
                            (norm.isNotBlank() && it.normalizedAddress == norm) || PhoneUtils.matches(it.address, recipient)
                        }
                        if (match != null) {
                            selectedThreadKey = match.threadKey
                        }
                    }
                }
            },
            onShowMessage = { viewModel.showMessage(it) },
            onDismiss = { showNewSmsComposer = false }
        )
    }

    pendingLockedThread?.let { locked ->
        ChatLockPromptDialog(
            title = "SMS verrouillé",
            hasPinConfigured = appPrefs.chatLockPinHash.isNotBlank(),
            preferBiometric = appPrefs.biometricSessionLockEnabled,
            onVerifyPin = { pin -> viewModel.prefsRepository.verifyChatLockPin(pin) },
            onSuccess = {
                selectedThreadKey = locked.threadKey
                pendingLockedThread = null
            },
            onDismiss = { pendingLockedThread = null }
        )
    }

    if (promptUnlockSmsVault) {
        ChatLockPromptDialog(
            title = "SMS verrouillés",
            hasPinConfigured = appPrefs.chatLockPinHash.isNotBlank(),
            preferBiometric = appPrefs.biometricSessionLockEnabled,
            onVerifyPin = { pin -> viewModel.prefsRepository.verifyChatLockPin(pin) },
            onSuccess = {
                promptUnlockSmsVault = false
                lockedVaultUnlocked = true
                showArchived = false
                showLocked = true
            },
            onDismiss = { promptUnlockSmsVault = false }
        )
    }

    pendingDeleteThread?.let { targetThread ->
        SmsLocalDeleteConfirmationDialog(
            title = "Supprimer cette conversation SMS ?",
            message = "Suppression locale uniquement : les messages seront retirés de cet appareil, mais ${targetThread.displayName} conserve sa copie.",
            isAuthorizedDefaultSms = isDefaultSms || !DefaultSmsHelper.isDefaultSmsSupported(context),
            onRequestDefaultSms = {
                pendingDeleteThread = null
                DefaultSmsHelper.createRequestDefaultSmsIntent(context)?.let {
                    runCatching { defaultSmsLauncher.launch(it) }
                }
            },
            onConfirmDelete = {
                pendingDeleteThread = null
                viewModel.deleteSmsThread(targetThread.threadId)
            },
            onDismiss = { pendingDeleteThread = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SmsThreadDetailScreen(
    thread: SmsThreadSummary,
    isDefaultSms: Boolean = true,
    isAiAutoActive: Boolean = false,
    isLocked: Boolean = false,
    recentEmojis: List<String> = emptyList(),
    favoriteEmojis: List<String> = emptyList(),
    preferredSkinTone: String = "",
    onRecordEmoji: (String) -> Unit = {},
    onToggleFavoriteEmoji: (String) -> Unit = {},
    onSelectSkinTone: (String) -> Unit = {},
    onRequestDefaultSms: () -> Unit = {},
    onToggleLock: () -> Unit = {},
    onToggleAiAuto: () -> Unit = {},
    onGenerateAiReply: suspend (targetPreview: String, historyPairs: List<Pair<String, String>>) -> Result<WorkerAiReply>,
    onDeleteMessage: (com.example.repository.SmsMessageItem) -> Unit = {},
    onDeleteThread: () -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    onSend: (String) -> Unit,
    onMarkRead: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var isGeneratingAi by remember { mutableStateOf(false) }
    var aiSuggestion by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showLuxuryEmojiSheet by remember { mutableStateOf(false) }
    var menuMessage by remember { mutableStateOf<com.example.repository.SmsMessageItem?>(null) }
    var pendingDeleteMessage by remember { mutableStateOf<com.example.repository.SmsMessageItem?>(null) }
    var showDeleteThreadConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun requestSmsAiDraft() {
        if (isGeneratingAi) return
        val historyPairs = thread.messages.takeLast(8).map { msg ->
            val role = if (msg.isIncoming) "user" else "assistant"
            role to msg.body
        }.filter { it.second.isNotBlank() }
        val latestIncoming = thread.messages.lastOrNull { it.isIncoming }?.body
            ?: thread.messages.lastOrNull()?.body
            ?: draft
        isGeneratingAi = true
        scope.launch {
            val res = onGenerateAiReply(latestIncoming, historyPairs)
            isGeneratingAi = false
            res.onSuccess { reply ->
                aiSuggestion = reply.reply to reply.model
            }.onFailure { err ->
                onShowMessage(err.localizedMessage ?: "Assistant IA indisponible")
            }
        }
    }

    LaunchedEffect(thread.threadId, thread.unreadCount) {
        if (thread.unreadCount > 0) onMarkRead()
        if (thread.messages.isNotEmpty()) {
            listState.scrollToItem(thread.messages.lastIndex)
        }
    }

    val lux = BravTheme.colors
    Scaffold(
        containerColor = lux.ivoryCanvas,
        topBar = {
            BravLuxuryHeader(
                title = thread.displayName,
                showLogo = false,
                pattern = BravPatternType.KENTE_DIAMOND,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour", tint = lux.headerOnColor)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleLock,
                        modifier = Modifier.testTag("sms_detail_lock_button")
                    ) {
                        Icon(
                            if (isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                            contentDescription = if (isLocked) "Déverrouiller ce SMS" else "Verrouiller ce SMS",
                            tint = if (isLocked) lux.champagneGold else lux.headerOnColor
                        )
                    }
                    IconButton(
                        onClick = { showDeleteThreadConfirm = true },
                        modifier = Modifier.testTag("sms_detail_delete_thread")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Supprimer cette conversation SMS",
                            tint = lux.headerOnColor
                        )
                    }
                    AiReplyDotButton(
                        isActive = isAiAutoActive,
                        dotSize = 11.dp,
                        touchSize = 48.dp,
                        contentDescriptionText = if (isAiAutoActive) "IA automatique active" else "Activer IA automatique",
                        testTagName = "sms_detail_ai_dot",
                        onClick = onToggleAiAuto
                    )
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
                pattern = BravPatternType.KENTE_DIAMOND,
                variant = BravPatternVariant.GOLD_ON_IVORY,
                opacity = 0.095f,
                scale = 1.05f
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(thread.messages, key = { "${it.id}_${it.timestampMs}" }) { msg ->
                        val isMine = !msg.isIncoming
                        val bubbleShape = RoundedCornerShape(
                            topStart = 17.dp,
                            topEnd = 17.dp,
                            bottomStart = if (isMine) 17.dp else 5.dp,
                            bottomEnd = if (isMine) 5.dp else 17.dp
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 310.dp)
                                        .then(
                                            if (isMine) Modifier.background(
                                                Brush.linearGradient(listOf(BravPurplePrimary, BravPurpleDeep)),
                                                bubbleShape
                                            ) else Modifier
                                                .background(lux.cardSurface, bubbleShape)
                                                .border(0.75.dp, lux.hairline, bubbleShape)
                                        )
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = { menuMessage = msg }
                                        )
                                        .testTag("sms_bubble_${msg.id}")
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(
                                            text = msg.body,
                                            color = if (isMine) BravWhite else lux.inkPrimary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = formatShortTime(msg.timestampMs / 1000L),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isMine) BravWhite.copy(alpha = 0.78f) else lux.inkSecondary
                                        )
                                    }
                                }
                            }

                        DropdownMenu(
                            expanded = menuMessage?.id == msg.id,
                            onDismissRequest = { menuMessage = null },
                            containerColor = lux.modalSurface
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                text = { Text("Supprimer sur cet appareil", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuMessage = null
                                    pendingDeleteMessage = msg
                                },
                                modifier = Modifier.testTag("sms_delete_message_${msg.id}")
                            )
                        }
                    }
                }
            }

            AiReplySuggestionBar(
                visible = aiSuggestion != null,
                suggestedText = aiSuggestion?.first.orEmpty(),
                modelLabel = aiSuggestion?.second.orEmpty(),
                onSendDirect = {
                    val replyTxt = aiSuggestion?.first?.trim().orEmpty()
                    if (replyTxt.isNotBlank()) {
                        aiSuggestion = null
                        onSend(replyTxt)
                    }
                },
                onEditInComposer = {
                    val replyTxt = aiSuggestion?.first.orEmpty()
                    if (replyTxt.isNotBlank()) draft = replyTxt
                    aiSuggestion = null
                },
                onRegenerate = { requestSmsAiDraft() },
                onDismiss = { aiSuggestion = null }
            )

            Surface(
                color = lux.cardSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BravGoldHairlineDivider(withGoldTouch = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = lux.softGray,
                            border = BorderStroke(
                                0.9.dp,
                                if (draft.isNotBlank()) lux.champagneGold.copy(alpha = 0.7f) else lux.hairline
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 2.dp, end = 10.dp)
                            ) {
                                AiReplyDotButton(
                                    isActive = true,
                                    isLoading = isGeneratingAi,
                                    dotSize = 13.dp,
                                    touchSize = 48.dp,
                                    contentDescriptionText = "Générer une réponse SMS avec l'IA",
                                    testTagName = "sms_composer_ai_dot",
                                    onClick = { requestSmsAiDraft() }
                                )
                                IconButton(
                                    onClick = { showLuxuryEmojiSheet = true },
                                    modifier = Modifier.testTag("sms_emoji_picker_button")
                                ) {
                                    Icon(Icons.Rounded.Mood, contentDescription = "Émojis", tint = lux.titlePurple)
                                }
                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = { draft = it },
                                    placeholder = { Text("Message SMS…", color = lux.inkSecondary) },
                                    maxLines = 4,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f).testTag("sms_thread_reply_input")
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (draft.isNotBlank()) BravPurplePrimary else lux.softGray,
                            enabled = draft.isNotBlank(),
                            onClick = {
                                val txt = draft.trim()
                                if (txt.isNotBlank()) {
                                    draft = ""
                                    onSend(txt)
                                }
                            },
                            modifier = Modifier.size(48.dp).testTag("sms_thread_send_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Envoyer SMS",
                                    tint = if (draft.isNotBlank()) BravWhite else lux.inkSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showLuxuryEmojiSheet) {
        LuxuryEmojiPickerSheet(
            onInsertEmoji = { emoji ->
                draft += emoji
                onRecordEmoji(emoji)
            },
            recentEmojis = recentEmojis,
            favoriteEmojis = favoriteEmojis,
            preferredSkinTone = preferredSkinTone,
            onToggleFavoriteEmoji = onToggleFavoriteEmoji,
            onSelectSkinTone = onSelectSkinTone,
            onDismiss = { showLuxuryEmojiSheet = false }
        )
    }

    val isSmsDeleteAuthorized = isDefaultSms || !DefaultSmsHelper.isDefaultSmsSupported(context)

    pendingDeleteMessage?.let { targetMsg ->
        SmsLocalDeleteConfirmationDialog(
            title = "Supprimer ce SMS ?",
            message = "Ce message sera effacé uniquement sur votre appareil. Le destinataire conserve sa propre copie.",
            isAuthorizedDefaultSms = isSmsDeleteAuthorized,
            onRequestDefaultSms = {
                pendingDeleteMessage = null
                onRequestDefaultSms()
            },
            onConfirmDelete = {
                pendingDeleteMessage = null
                onDeleteMessage(targetMsg)
            },
            onDismiss = { pendingDeleteMessage = null }
        )
    }

    if (showDeleteThreadConfirm) {
        SmsLocalDeleteConfirmationDialog(
            title = "Supprimer toute la conversation ?",
            message = "Suppression locale uniquement : tous les SMS de ce fil seront supprimés sur cet appareil. Le destinataire conserve sa copie.",
            isAuthorizedDefaultSms = isSmsDeleteAuthorized,
            onRequestDefaultSms = {
                showDeleteThreadConfirm = false
                onRequestDefaultSms()
            },
            onConfirmDelete = {
                showDeleteThreadConfirm = false
                onDeleteThread()
            },
            onDismiss = { showDeleteThreadConfirm = false }
        )
    }
}

@Composable
internal fun SmsLocalDeleteConfirmationDialog(
    title: String,
    message: String,
    isAuthorizedDefaultSms: Boolean,
    onRequestDefaultSms: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BravTheme.colors.modalSurface,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (!isAuthorizedDefaultSms) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = "Autorisation requise : Android autorise uniquement l'application SMS par défaut à supprimer des messages de la base téléphonique.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isAuthorizedDefaultSms) {
                Button(
                    onClick = onConfirmDelete,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_local_sms_delete_button")
                ) {
                    Text("Supprimer localement")
                }
            } else {
                Button(
                    onClick = onRequestDefaultSms,
                    modifier = Modifier.testTag("sms_delete_set_default_button")
                ) {
                    Text("Définir par défaut")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
