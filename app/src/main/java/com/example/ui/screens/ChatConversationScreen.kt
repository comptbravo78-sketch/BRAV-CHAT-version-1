package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AiAutoReplyManager
import com.example.model.Conversation
import com.example.model.Message
import com.example.model.User
import com.example.repository.AiTranslationRepository
import com.example.ui.components.AiReplyDotButton
import com.example.ui.components.AiReplySuggestionBar
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravEmojiCatalog
import com.example.ui.components.BravGoldHairlineDivider
import com.example.ui.components.BravPatternIntensity
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.components.BravStickerCatalog
import com.example.ui.components.BravStickerPickerSheet
import com.example.ui.components.LuxuryEmojiPickerSheet
import com.example.ui.components.MediaLightboxDialog
import com.example.ui.components.PeerTypingIndicatorBubble
import com.example.ui.components.ReactionPickerBar
import com.example.ui.components.TypingDotsWave
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.components.bravAfricanPattern
import com.example.ui.components.bravOutlinedTextFieldColors
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRedSecondary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import com.example.ui.components.VerifiedBlueBadge
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatShortDateTime
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravRed
import com.example.util.ImageCompressor
import com.example.util.MediaDownloadHelper
import com.example.viewmodel.BravViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationScreen(
    conversationId: String,
    viewModel: BravViewModel,
    onOpenYouTube: (url: String, title: String) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myUid = viewModel.currentUserId

    val conversation by viewModel.chatRepository.observeConversation(conversationId)
        .collectAsStateWithLifecycle(initialValue = null)
    val messages by viewModel.chatRepository.observeMessages(conversationId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingOffline by viewModel.chatRepository.observeOfflinePending(conversationId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val prefsMap by viewModel.conversationPrefs.collectAsStateWithLifecycle()
    val presenceMap by viewModel.presenceMap.collectAsStateWithLifecycle()
    val typingMap by viewModel.typingConversations.collectAsStateWithLifecycle()
    val translations by viewModel.translations.collectAsStateWithLifecycle()
    val savedContacts by viewModel.savedContactUsers.collectAsStateWithLifecycle()
    val isRecording by viewModel.voicePlayer.isRecording.collectAsStateWithLifecycle()
    val recDurationMs by viewModel.voicePlayer.recordingDurationMs.collectAsStateWithLifecycle()
    val playbackState by viewModel.voicePlayer.playback.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val appPrefs by viewModel.appPrefs.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val followingUserIds by viewModel.followingUserIds.collectAsStateWithLifecycle()
    val outgoingFollowRequests by viewModel.outgoingFollowRequests.collectAsStateWithLifecycle()

    val convPref = prefsMap[conversationId]
    val clearedAtMs = convPref?.clearedAtMs ?: 0L
    val visibleMessages = remember(messages, myUid, clearedAtMs) {
        messages.filter { msg ->
            myUid !in msg.deletedForUsers &&
                ((msg.timestamp?.seconds ?: Long.MAX_VALUE) * 1000L) >= clearedAtMs
        }
    }

    var entryLastReadAtMs by remember(conversationId) {
        mutableLongStateOf(convPref?.lastReadAtMs ?: 0L)
    }
    var capturedUnreadBoundaryReady by remember(conversationId) { mutableStateOf(false) }
    var firstUnreadMessageId by remember(conversationId) { mutableStateOf<String?>(null) }

    DisposableEffect(conversationId) {
        viewModel.setActiveConversation(conversationId)
        onDispose {
            if (viewModel.activeConversationId.value == conversationId) {
                viewModel.setActiveConversation(null)
            }
        }
    }

    var peerUser by remember { mutableStateOf<User?>(null) }
    val peerUid = remember(conversation, myUid) {
        if (conversation?.isGroup == false) conversation?.participants?.firstOrNull { it != myUid }.orEmpty() else ""
    }
    val isPeerBlocked = remember(blockedUsers, peerUid) {
        peerUid.isNotBlank() && blockedUsers.any { it.blockedUid == peerUid }
    }
    LaunchedEffect(peerUid) {
        if (peerUid.isNotBlank()) {
            peerUser = viewModel.userRepository.getUserById(peerUid)
        }
    }
    LaunchedEffect(conversationId, visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) {
            if (!capturedUnreadBoundaryReady) {
                val firstUnread = visibleMessages.firstOrNull { msg ->
                    val tsMs = msg.timestamp?.let { it.seconds * 1000L + it.nanoseconds / 1_000_000L } ?: 0L
                    msg.senderId != myUid &&
                        msg.status != "read" &&
                        (entryLastReadAtMs <= 0L || tsMs > entryLastReadAtMs)
                }
                firstUnreadMessageId = firstUnread?.id
                capturedUnreadBoundaryReady = true
            }
            viewModel.markMessagesAsRead(conversationId, visibleMessages)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    var inputText by remember { mutableStateOf("") }
    var replyToMsg by remember { mutableStateOf<Message?>(null) }
    var editingMsg by remember { mutableStateOf<Message?>(null) }
    var selectedMsgForMenu by remember { mutableStateOf<Message?>(null) }
    var multiSelectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var infoTargetMsg by remember { mutableStateOf<Message?>(null) }
    var forwardMsg by remember { mutableStateOf<Message?>(null) }
    var translateTargetMsg by remember { mutableStateOf<Message?>(null) }
    var reportTargetMsg by remember { mutableStateOf<Message?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showContactShareDialog by remember { mutableStateOf(false) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showGroupInfoDialog by remember { mutableStateOf(false) }
    var showDeleteConversationConfirm by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var lightboxMedia by remember { mutableStateOf<Triple<String, Boolean, String>?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCallIsVideo by remember { mutableStateOf<Boolean?>(null) }
    var aiDraftReply by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isGeneratingAiReply by remember { mutableStateOf(false) }
    var showLuxuryEmojiSheet by remember { mutableStateOf(false) }
    var showStickerSheet by remember { mutableStateOf(false) }
    var showPeerProfileModal by remember { mutableStateOf(false) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(selectedMsgForMenu, editingMsg) {
        if (selectedMsgForMenu != null || editingMsg != null) {
            nowMs = System.currentTimeMillis()
            while (isActive) {
                delay(1000L)
                nowMs = System.currentTimeMillis()
            }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val conv = conversation ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val isVid = mime.startsWith("video/")
            val compressed = if (!isVid) {
                ImageCompressor.compressForUpload(context, uri)
            } else {
                val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                raw?.let { it to mime }
            } ?: return@launch
            viewModel.uploadProgressValue(0.05f)
            val res = viewModel.chatRepository.uploadAndSendMediaMessage(
                conversation = conv,
                senderId = myUid,
                bytes = compressed.first,
                mimeType = compressed.second,
                fileName = queryFileName(context, uri),
                messageType = if (isVid) "video" else "image",
                onProgress = { viewModel.uploadProgressValue(it) }
            )
            viewModel.uploadProgressValue(null)
            if (res.isFailure) viewModel.showMessage(res.exceptionOrNull()?.localizedMessage ?: "Échec envoi média")
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val conv = conversation ?: return@rememberLauncherForActivityResult
        val uri = cameraTempUri
        if (ok && uri != null) {
            scope.launch {
                val compressed = ImageCompressor.compressForUpload(context, uri)
                    ?: return@launch
                viewModel.uploadProgressValue(0.05f)
                val res = viewModel.chatRepository.uploadAndSendMediaMessage(
                    conversation = conv,
                    senderId = myUid,
                    bytes = compressed.first,
                    mimeType = compressed.second,
                    fileName = "brav_photo_${System.currentTimeMillis()}.jpg",
                    messageType = "image",
                    onProgress = { viewModel.uploadProgressValue(it) }
                )
                viewModel.uploadProgressValue(null)
                if (res.isFailure) viewModel.showMessage(res.exceptionOrNull()?.localizedMessage ?: "Échec envoi photo")
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraTempUri = uri
            takePictureLauncher.launch(uri)
        } else {
            viewModel.showMessage("Permission caméra refusée.")
        }
    }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val conv = conversation ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            viewModel.uploadProgressValue(0.05f)
            val res = viewModel.chatRepository.uploadAndSendMediaMessage(
                conversation = conv,
                senderId = myUid,
                bytes = bytes,
                mimeType = mime,
                fileName = queryFileName(context, uri),
                messageType = "file",
                onProgress = { viewModel.uploadProgressValue(it) }
            )
            viewModel.uploadProgressValue(null)
            if (res.isFailure) viewModel.showMessage(res.exceptionOrNull()?.localizedMessage ?: "Échec envoi fichier")
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.voicePlayer.startRecording()
        } else {
            viewModel.showMessage("Permission microphone requise pour les messages vocaux.")
        }
    }

    @SuppressLint("MissingPermission")
    fun sendCurrentGpsLocation() {
        val conv = conversation ?: return
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    scope.launch {
                        viewModel.chatRepository.sendLocationMessage(conv, myUid, loc.latitude, loc.longitude)
                    }
                } else {
                    viewModel.showMessage("Position GPS indisponible pour le moment.")
                }
            }
            .addOnFailureListener {
                viewModel.showMessage("Impossible d'obtenir la position GPS.")
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) sendCurrentGpsLocation() else viewModel.showMessage("Permission localisation refusée.")
    }

    val conv = conversation
    val chatTitle = when {
        conv == null -> "Chargement..."
        conv.isGroup -> conv.groupName.ifBlank { "Groupe BRAV" }
        else -> peerUser?.displayName ?: conv.participantNames[peerUid] ?: "Membre BRAV"
    }
    val typingOthers = remember(typingMap, conv?.typingUsers, conversationId, myUid) {
        (typingMap[conversationId].orEmpty() + conv?.typingUsers.orEmpty())
            .distinct()
            .filter { it != myUid }
    }
    DisposableEffect(conversationId, peerUid) {
        onDispose {
            viewModel.notifyTyping(conversationId, peerUid, false)
        }
    }
    LaunchedEffect(typingOthers.isNotEmpty(), visibleMessages.size) {
        if (typingOthers.isNotEmpty() && visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.size)
        }
    }
    val isPeerOnline = peerUid.isNotBlank() && presenceMap[peerUid]?.online == true
    val aiRuleCandidates = remember(peerUid, conversationId) {
        listOfNotNull(peerUid.takeIf { it.isNotBlank() }, conversationId)
    }
    val isAiConvDotActive = remember(aiRuleCandidates, appPrefs.aiAutoReplyContactRulesJson, appPrefs.aiAutoReplyBravEnabled) {
        AiAutoReplyManager.isDirectDotActive(
            contactCandidates = aiRuleCandidates,
            rulesJson = appPrefs.aiAutoReplyContactRulesJson,
            globalAutoReplyEnabled = appPrefs.aiAutoReplyBravEnabled
        )
    }

    fun requestDirectAiDraft(replyOverridePreview: String = "") {
        if (isGeneratingAiReply) return
        val historyPairs = visibleMessages
            .filter { !it.deletedForAll && it.messageType == "text" }
            .takeLast(8)
            .map { msg ->
                val role = if (msg.senderId == myUid) "assistant" else "user"
                role to viewModel.chatRepository.decryptDisplayText(msg, myUid, conv)
            }
            .filter { it.second.isNotBlank() }
        val latestIncoming = replyOverridePreview.trim().ifBlank {
            visibleMessages
                .lastOrNull { it.senderId != myUid && !it.deletedForAll && it.messageType == "text" }
                ?.let { viewModel.chatRepository.decryptDisplayText(it, myUid, conv) }
                ?: visibleMessages.lastOrNull { !it.deletedForAll && it.messageType == "text" }
                    ?.let { viewModel.chatRepository.decryptDisplayText(it, myUid, conv) }
                ?: inputText.trim()
        }
        isGeneratingAiReply = true
        scope.launch {
            val res = viewModel.generateDirectAiReply(
                channel = "brav_chat",
                contactKey = peerUid.ifBlank { conversationId },
                contactLabel = chatTitle,
                incomingMessage = latestIncoming,
                historyPairs = historyPairs
            )
            isGeneratingAiReply = false
            res.onSuccess { reply ->
                aiDraftReply = reply.reply to reply.model
            }.onFailure { err ->
                viewModel.showMessage(err.localizedMessage ?: "Assistant IA indisponible")
            }
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val isVideo = pendingCallIsVideo ?: return@rememberLauncherForActivityResult
        pendingCallIsVideo = null
        val micOk = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micOk && peerUid.isNotBlank()) {
            viewModel.startOutgoingCall(peerUid, chatTitle, isVideo = isVideo)
        } else if (!micOk) {
            viewModel.showMessage("Permission microphone requise pour passer un appel.")
        }
    }

    fun launchCallWithPermissions(isVideo: Boolean) {
        if (peerUid.isBlank()) return
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (needed.isEmpty()) {
            viewModel.startOutgoingCall(peerUid, chatTitle, isVideo = isVideo)
        } else {
            pendingCallIsVideo = isVideo
            callPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    val wallpaperBrush = remember(convPref?.wallpaperId) {
        when (convPref?.wallpaperId) {
            "violet" -> Brush.verticalGradient(listOf(BravPurple.copy(alpha = 0.14f), Color.Transparent))
            "sunset" -> Brush.verticalGradient(listOf(BravRed.copy(alpha = 0.12f), BravPurple.copy(alpha = 0.08f)))
            "emerald" -> Brush.verticalGradient(listOf(Color(0xFF00897B).copy(alpha = 0.12f), Color.Transparent))
            else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
        }
    }

    val lux = BravTheme.colors
    Scaffold(
        containerColor = lux.ivoryCanvas,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(lux.headerTop, lux.headerBottom)))
                    .bravAfricanPattern(
                        pattern = BravPatternType.KENTE_DIAMOND,
                        primaryColor = BravWhite,
                        secondaryColor = lux.champagneGold,
                        opacity = BravPatternIntensity.HEADER_TONE,
                        scale = 0.9f
                    )
            ) {
                val barColors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    navigationIconContentColor = lux.headerOnColor,
                    titleContentColor = lux.headerOnColor,
                    actionIconContentColor = lux.headerOnColor
                )
                if (multiSelectIds.isNotEmpty()) {
                    TopAppBar(
                        colors = barColors,
                        navigationIcon = {
                            IconButton(onClick = { multiSelectIds = emptySet() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Annuler la sélection")
                            }
                        },
                        title = { Text("${multiSelectIds.size} sélectionné(s)", fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = {
                                val selectedTexts = visibleMessages
                                    .filter { it.id in multiSelectIds && !it.deletedForAll }
                                    .joinToString("\n") { viewModel.chatRepository.decryptDisplayText(it, myUid, conv) }
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("BRAV Chat", selectedTexts))
                                viewModel.showMessage("Messages copiés")
                                multiSelectIds = emptySet()
                            }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copier la sélection")
                            }
                            IconButton(onClick = {
                                val ids = multiSelectIds.toList()
                                multiSelectIds = emptySet()
                                scope.launch {
                                    ids.forEach { msgId ->
                                        viewModel.chatRepository.deleteMessageForMe(conversationId, msgId, myUid)
                                    }
                                }
                            }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Supprimer pour moi", tint = Color(0xFFFF8A92))
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        colors = barColors,
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                            }
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        if (conv?.isGroup == true) showGroupInfoDialog = true
                                        else if (peerUser != null) showPeerProfileModal = true
                                        else showSafetyDialog = true
                                    }
                                    .testTag("chat_header_peer_profile")
                            ) {
                                UserAvatarWithStatus(
                                    name = chatTitle,
                                    avatarUrl = if (conv?.isGroup == true) conv.groupAvatarUrl else peerUser?.avatarUrl.orEmpty(),
                                    availabilityStatus = peerUser?.availabilityStatus ?: "offline",
                                    isOnlineRealtime = if (conv?.isGroup == true) null else isPeerOnline,
                                    size = 38.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = chatTitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = lux.headerOnColor,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (conv?.isGroup != true && peerUser?.isVerifiedActive() == true) {
                                            VerifiedBlueBadge(size = 16.dp)
                                        }
                                    }
                                    if (typingOthers.isNotEmpty()) {
                                        val typingLabel = if (conv?.isGroup == true) {
                                            val firstTyper = conv.participantNames[typingOthers.first()]?.split(" ")?.firstOrNull().orEmpty()
                                            if (firstTyper.isNotBlank()) "$firstTyper écrit..." else "écrit..."
                                        } else {
                                            "en train d'écrire..."
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            modifier = Modifier.testTag("chat_header_typing_indicator")
                                        ) {
                                            TypingDotsWave(color = lux.champagneGold, dotSize = 4.dp, spacing = 3.dp)
                                            Text(
                                                text = typingLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = lux.champagneGold,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = when {
                                                conv?.isGroup == true -> "${conv.participants.size} membres"
                                                isPeerOnline -> "En ligne"
                                                else -> "Chiffré de bout en bout"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = lux.headerOnColor.copy(alpha = 0.80f)
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            AiReplyDotButton(
                                isActive = isAiConvDotActive,
                                dotSize = 11.dp,
                                touchSize = 48.dp,
                                contentDescriptionText = if (isAiConvDotActive) "IA automatique active" else "Activer l'IA automatique",
                                testTagName = "chat_conv_ai_dot",
                                onClick = {
                                    viewModel.toggleConversationDirectAi(
                                        contactKey = peerUid.ifBlank { conversationId },
                                        contactLabel = chatTitle,
                                        globalEnabled = appPrefs.aiAutoReplyBravEnabled,
                                        candidates = aiRuleCandidates
                                    )
                                }
                            )
                            if (conv?.isGroup == false && peerUid.isNotBlank()) {
                                IconButton(onClick = { launchCallWithPermissions(isVideo = false) }) {
                                    Icon(Icons.Rounded.Call, contentDescription = "Appel audio")
                                }
                                IconButton(onClick = { launchCallWithPermissions(isVideo = true) }) {
                                    Icon(Icons.Rounded.Videocam, contentDescription = "Appel vidéo")
                                }
                            }
                            Box {
                                IconButton(onClick = { showTopMenu = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options")
                                }
                                DropdownMenu(
                                    expanded = showTopMenu,
                                    onDismissRequest = { showTopMenu = false },
                                    containerColor = lux.modalSurface
                                ) {
                                    if (conv?.isGroup == true) {
                                        DropdownMenuItem(
                                            text = { Text("Infos & Admin du groupe") },
                                            leadingIcon = { Icon(Icons.Rounded.Group, contentDescription = null) },
                                            onClick = { showTopMenu = false; showGroupInfoDialog = true }
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text("Code sécurité E2EE") },
                                            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                                            onClick = { showTopMenu = false; showSafetyDialog = true }
                                        )
                                        if (peerUid.isNotBlank()) {
                                            DropdownMenuItem(
                                                text = { Text(if (isPeerBlocked) "Débloquer" else "Bloquer cet utilisateur") },
                                                leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null, tint = BravRed) },
                                                onClick = {
                                                    showTopMenu = false
                                                    if (isPeerBlocked) viewModel.unblockUser(peerUid)
                                                    else viewModel.blockUser(peerUid, "Bloqué depuis la discussion")
                                                }
                                            )
                                        }
                                    }
                                    DropdownMenuItem(
                                        text = { Text(if (convPref?.locked == true) "Déverrouiller la discussion" else "Verrouiller la discussion") },
                                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                                        onClick = {
                                            showTopMenu = false
                                            val nextLocked = !(convPref?.locked ?: false)
                                            scope.launch {
                                                viewModel.chatRepository.updateConversationPref(
                                                    myUid, conversationId, locked = nextLocked
                                                )
                                                if (nextLocked) {
                                                    viewModel.showMessage("Discussion masquée dans Verrouillées")
                                                    onBack()
                                                } else {
                                                    viewModel.showMessage("Discussion déverrouillée")
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Fond d'écran") },
                                        onClick = { showTopMenu = false; showWallpaperDialog = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Vider l'historique") },
                                        onClick = {
                                            showTopMenu = false
                                            scope.launch {
                                                viewModel.chatRepository.updateConversationPref(
                                                    myUid, conversationId, clearedAtMs = System.currentTimeMillis()
                                                )
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Supprimer la conversation", color = BravRed) },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = BravRed) },
                                        onClick = {
                                            showTopMenu = false
                                            showDeleteConversationConfirm = true
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
                BravGoldHairlineDivider(withGoldTouch = true)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(lux.ivoryCanvas)
                .background(wallpaperBrush)
        ) {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.KENTE_DIAMOND,
                variant = BravPatternVariant.GOLD_ON_IVORY,
                opacity = 0.09f,
                scale = 1.05f
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                if (!conv?.pinnedMessagePreview.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(16.dp), tint = BravPurple)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = conv?.pinnedMessagePreview.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                uploadProgress?.let { progress ->
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(visibleMessages, key = { it.id }) { msg ->
                        if (msg.id == firstUnreadMessageId) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag("chat_new_messages_separator"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                BravGoldHairlineDivider(
                                    withGoldTouch = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = lux.translucentCardSurface,
                                    border = BorderStroke(0.85.dp, lux.champagneGold.copy(alpha = 0.78f))
                                ) {
                                    Text(
                                        text = "NOUVEAUX MESSAGES",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = lux.titlePurple,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                                BravGoldHairlineDivider(
                                    withGoldTouch = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        val displayText = viewModel.chatRepository.decryptDisplayText(msg, myUid, conv)
                        val senderName = conv?.participantNames?.get(msg.senderId) ?: "Membre"
                        val isMsgSelected = msg.id in multiSelectIds
                        ChatMessageBubble(
                            message = msg,
                            displayText = displayText,
                            translatedText = translations[msg.id],
                            isMine = msg.senderId == myUid,
                            myUid = myUid,
                            senderDisplayName = senderName,
                            isGroup = conv?.isGroup == true,
                            isSelected = isMsgSelected,
                            playbackState = playbackState,
                            onTapOverride = if (multiSelectIds.isNotEmpty()) {
                                {
                                    multiSelectIds = if (isMsgSelected) multiSelectIds - msg.id else multiSelectIds + msg.id
                                }
                            } else null,
                            onLongPress = {
                                if (multiSelectIds.isNotEmpty()) {
                                    multiSelectIds = if (isMsgSelected) multiSelectIds - msg.id else multiSelectIds + msg.id
                                } else {
                                    selectedMsgForMenu = msg
                                }
                            },
                            onToggleReaction = { type ->
                                scope.launch { viewModel.chatRepository.toggleReaction(conversationId, msg, myUid, type.key) }
                            },
                            onOpenMedia = { url, isVid, title -> lightboxMedia = Triple(url, isVid, title) },
                            onDownloadMedia = { mediaMsg ->
                                scope.launch {
                                    viewModel.showMessage("Téléchargement de l'image...")
                                    val res = MediaDownloadHelper.saveMediaToGallery(
                                        context = context,
                                        workerApiClient = viewModel.workerApiClient,
                                        mediaUrl = mediaMsg.mediaUrl,
                                        fileNameHint = mediaMsg.mediaName,
                                        mimeTypeHint = mediaMsg.mediaMimeType,
                                        isVideo = false
                                    )
                                    viewModel.showMessage(
                                        if (res.isSuccess) "Image enregistrée dans la galerie"
                                        else res.exceptionOrNull()?.localizedMessage ?: "Téléchargement impossible"
                                    )
                                }
                            },
                            onOpenYouTube = onOpenYouTube,
                        onOpenDocument = { docMsg ->
                            scope.launch {
                                viewModel.showMessage("Téléchargement sécurisé...")
                                val cached = viewModel.workerApiClient.downloadMediaToCache(
                                    context,
                                    docMsg.mediaUrl,
                                    docMsg.mediaName
                                )
                                cached.onSuccess { file ->
                                    runCatching {
                                        val contentUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(contentUri, docMsg.mediaMimeType.ifBlank { "*/*" })
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(openIntent)
                                    }.onFailure {
                                        viewModel.showMessage("Aucune application trouvée pour ouvrir ce fichier.")
                                    }
                                }.onFailure {
                                    viewModel.showMessage(it.localizedMessage ?: "Téléchargement impossible")
                                }
                            }
                        },
                        onToggleVoicePlay = { viewModel.voicePlayer.togglePlayPause(msg.mediaUrl) },
                        onCycleVoiceSpeed = { viewModel.voicePlayer.cyclePlaybackSpeed() }
                    )
                }

                if (pendingOffline.isNotEmpty()) {
                    items(pendingOffline, key = { it.localId }) { pending ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = BravTheme.colors.translucentSubtleSurface,
                                border = BorderStroke(0.75.dp, BravTheme.colors.violetContour)
                            ) {
                                Text(
                                    text = "${pending.plaintext} (En attente réseau)",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }

                item(key = "peer_typing_indicator") {
                    AnimatedVisibility(
                        visible = typingOthers.isNotEmpty() || isGeneratingAiReply,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val activeTyperId = typingOthers.firstOrNull().orEmpty()
                        val typerName = when {
                            isGeneratingAiReply -> "Assistant IA"
                            conv?.isGroup == true -> conv.participantNames[activeTyperId].orEmpty()
                            else -> chatTitle
                        }
                        val typerAvatar = when {
                            isGeneratingAiReply -> ""
                            conv?.isGroup == true -> conv.participantAvatars[activeTyperId].orEmpty()
                            else -> peerUser?.avatarUrl.orEmpty()
                        }
                        PeerTypingIndicatorBubble(
                            senderName = typerName,
                            avatarUrl = typerAvatar,
                            isGroup = conv?.isGroup == true || isGeneratingAiReply
                        )
                    }
                }
            }

            replyToMsg?.let { reply ->
                val replyPreview = viewModel.chatRepository.decryptDisplayText(reply, myUid, conv)
                Surface(
                    color = BravTheme.colors.translucentCardSurface,
                    border = BorderStroke(0.75.dp, BravTheme.colors.violetContour),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Réponse à un message", style = MaterialTheme.typography.labelSmall, color = BravPurple, fontWeight = FontWeight.Bold)
                            Text(
                                replyPreview,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        AiReplyDotButton(
                            isActive = true,
                            isLoading = isGeneratingAiReply,
                            dotSize = 11.dp,
                            touchSize = 48.dp,
                            contentDescriptionText = "Répondre avec l'IA à ce message",
                            testTagName = "chat_reply_ai_dot",
                            onClick = { requestDirectAiDraft(replyPreview) }
                        )
                        IconButton(onClick = { replyToMsg = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Annuler réponse")
                        }
                    }
                }
            }

            AiReplySuggestionBar(
                visible = aiDraftReply != null,
                suggestedText = aiDraftReply?.first.orEmpty(),
                modelLabel = aiDraftReply?.second.orEmpty(),
                onSendDirect = {
                    val activeConv = conversation ?: return@AiReplySuggestionBar
                    val replyTxt = aiDraftReply?.first?.trim().orEmpty()
                    if (replyTxt.isNotBlank()) {
                        val target = replyToMsg
                        aiDraftReply = null
                        replyToMsg = null
                        scope.launch {
                            viewModel.chatRepository.sendTextMessage(
                                conversation = activeConv,
                                senderId = myUid,
                                plaintext = replyTxt,
                                peerPublicKey = peerUser?.publicKey.orEmpty(),
                                replyTo = target
                            )
                        }
                    }
                },
                onEditInComposer = {
                    val replyTxt = aiDraftReply?.first.orEmpty()
                    if (replyTxt.isNotBlank()) {
                        inputText = replyTxt
                    }
                    aiDraftReply = null
                },
                onRegenerate = {
                    val targetPreview = replyToMsg?.let { viewModel.chatRepository.decryptDisplayText(it, myUid, conv) }.orEmpty()
                    requestDirectAiDraft(targetPreview)
                },
                onDismiss = { aiDraftReply = null }
            )

            editingMsg?.let { target ->
                val remaining = viewModel.chatRepository.remainingEditSeconds(target, nowMs)
                LaunchedEffect(remaining, target.id) {
                    if (remaining <= 0L) {
                        editingMsg = null
                        inputText = ""
                        viewModel.showMessage("Délai de 30 secondes expiré : modification impossible.")
                    }
                }
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Modification du message (${remaining}s restantes)",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = { editingMsg = null; inputText = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Annuler modification")
                        }
                    }
                }
            }

            val blockedByOnlyAdmins = conv?.isGroup == true && conv.onlyAdminsCanMessage && myUid !in conv.adminIds
            if (isPeerBlocked) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Vous avez bloqué ce contact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.unblockUser(peerUid) }) {
                            Text("Débloquer")
                        }
                    }
                }
            } else if (blockedByOnlyAdmins) {
                Surface(
                    color = BravTheme.colors.translucentSubtleSurface,
                    border = BorderStroke(0.75.dp, BravTheme.colors.violetContour),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Seuls les administrateurs peuvent envoyer des messages dans ce groupe.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else if (isRecording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Enregistrement vocal : ${formatDurationMs(recDurationMs)}",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        TextButton(onClick = { viewModel.voicePlayer.cancelRecording() }) {
                            Text("Annuler")
                        }
                        Button(onClick = {
                            val activeConv = conversation ?: return@Button
                            scope.launch {
                                val recorded = viewModel.voicePlayer.stopRecording() ?: return@launch
                                viewModel.uploadProgressValue(0.1f)
                                viewModel.chatRepository.uploadAndSendMediaMessage(
                                    conversation = activeConv,
                                    senderId = myUid,
                                    bytes = recorded.bytes,
                                    mimeType = "audio/mp4",
                                    fileName = recorded.file.name,
                                    messageType = "voice",
                                    durationMs = recorded.durationMs,
                                    waveform = recorded.waveform,
                                    onProgress = { viewModel.uploadProgressValue(it) }
                                )
                                viewModel.uploadProgressValue(null)
                            }
                        }) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Envoyer")
                        }
                    }
                }
            } else {
                Surface(
                    color = lux.translucentCardSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BravGoldHairlineDivider(withGoldTouch = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showAttachmentSheet = true }) {
                                Icon(Icons.Rounded.AttachFile, contentDescription = "Joindre un fichier", tint = lux.inkSecondary)
                            }
                            AiReplyDotButton(
                                isActive = true,
                                isLoading = isGeneratingAiReply,
                                dotSize = 13.dp,
                                touchSize = 48.dp,
                                contentDescriptionText = "Générer une réponse IA directe",
                                testTagName = "chat_composer_ai_dot",
                                onClick = {
                                    val targetPreview = replyToMsg?.let { viewModel.chatRepository.decryptDisplayText(it, myUid, conv) }.orEmpty()
                                    requestDirectAiDraft(targetPreview)
                                }
                            )
                            IconButton(
                                onClick = { showLuxuryEmojiSheet = true },
                                modifier = Modifier.testTag("chat_emoji_picker_button")
                            ) {
                                Icon(Icons.Rounded.Mood, contentDescription = "Émojis", tint = lux.titlePurple)
                            }
                            IconButton(
                                onClick = { showStickerSheet = true },
                                modifier = Modifier.testTag("chat_sticker_picker_button")
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = "Autocollants", tint = lux.champagneGold)
                            }
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = {
                                    inputText = it
                                    viewModel.notifyTyping(conversationId, peerUid, it.isNotBlank())
                                },
                                placeholder = { Text("Écrire un message...", color = lux.inkSecondary) },
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 4,
                                colors = bravOutlinedTextFieldColors(),
                                modifier = Modifier.weight(1f).testTag("chat_message_input")
                            )
                            Spacer(Modifier.width(6.dp))
                            if (inputText.isNotBlank()) {
                                Surface(
                                    shape = CircleShape,
                                    color = BravPurplePrimary,
                                    onClick = {
                                        val activeConv = conversation ?: return@Surface
                                        val txt = inputText.trim()
                                        val editTarget = editingMsg
                                        val replyTarget = replyToMsg
                                        inputText = ""
                                        editingMsg = null
                                        replyToMsg = null
                                        viewModel.notifyTyping(conversationId, peerUid, false)
                                        scope.launch {
                                            if (editTarget != null) {
                                                val editRes = viewModel.chatRepository.editMessage(activeConv, editTarget, myUid, txt)
                                                editRes.onFailure {
                                                    viewModel.showMessage(it.localizedMessage ?: "Délai de 30s expiré : modification impossible.")
                                                }
                                            } else {
                                                viewModel.chatRepository.sendTextMessage(
                                                    conversation = activeConv,
                                                    senderId = myUid,
                                                    plaintext = txt,
                                                    peerPublicKey = peerUser?.publicKey.orEmpty(),
                                                    replyTo = replyTarget
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(48.dp).testTag("send_message_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Envoyer", tint = BravWhite)
                                    }
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = BravRedSecondary,
                                    onClick = {
                                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                        if (granted) viewModel.voicePlayer.startRecording()
                                        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    modifier = Modifier.size(48.dp).testTag("voice_record_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Mic, contentDescription = "Message vocal", tint = BravWhite)
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

    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = lux.modalSurface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Partager dans la discussion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                AttachmentOptionRow(Icons.Rounded.Image, "Photo ou Vidéo (Galerie)") {
                    showAttachmentSheet = false
                    mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }
                AttachmentOptionRow(Icons.Rounded.CameraAlt, "Prendre une photo (Caméra)") {
                    showAttachmentSheet = false
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        val file = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        cameraTempUri = uri
                        takePictureLauncher.launch(uri)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
                AttachmentOptionRow(Icons.Rounded.Description, "Document (PDF, Word, Archive)") {
                    showAttachmentSheet = false
                    documentPicker.launch(arrayOf("*/*"))
                }
                AttachmentOptionRow(Icons.Rounded.LocationOn, "Ma position GPS actuelle") {
                    showAttachmentSheet = false
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (granted) sendCurrentGpsLocation()
                    else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                AttachmentOptionRow(Icons.Rounded.Person, "Partager un contact BRAV") {
                    showAttachmentSheet = false
                    showContactShareDialog = true
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    selectedMsgForMenu?.let { msg ->
        val plain = viewModel.chatRepository.decryptDisplayText(msg, myUid, conv)
        val myCurrentReaction = msg.reactions.entries.firstOrNull { myUid in it.value }?.key
        AlertDialog(
            onDismissRequest = { selectedMsgForMenu = null },
            containerColor = lux.modalSurface,
            title = { Text("Actions du message") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ReactionPickerBar(
                        selectedKey = myCurrentReaction,
                        onSelect = { reaction ->
                            scope.launch { viewModel.chatRepository.toggleReaction(conversationId, msg, myUid, reaction.key) }
                            selectedMsgForMenu = null
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { replyToMsg = msg; selectedMsgForMenu = null }) { Text("Répondre") }
                    TextButton(onClick = { forwardMsg = msg; selectedMsgForMenu = null }) { Text("Transférer") }
                    if (msg.messageType == "text" && !msg.deletedForAll) {
                        TextButton(onClick = { translateTargetMsg = msg; selectedMsgForMenu = null }) { Text("Traduire (IA Gemini)") }
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("BRAV Chat", plain))
                            viewModel.showMessage("Texte copié")
                            selectedMsgForMenu = null
                        }) { Text("Copier") }
                    }
                    TextButton(onClick = {
                        scope.launch { viewModel.chatRepository.pinMessage(conversationId, msg, !msg.pinned) }
                        selectedMsgForMenu = null
                    }) { Text(if (msg.pinned) "Désépingler" else "Épingler") }
                    if (msg.senderId == myUid && msg.messageType == "text" && !msg.deletedForAll) {
                        val remainingEdit = viewModel.chatRepository.remainingEditSeconds(msg, nowMs)
                        if (remainingEdit > 0L) {
                            TextButton(onClick = {
                                editingMsg = msg
                                inputText = plain
                                selectedMsgForMenu = null
                            }) { Text("Modifier (${remainingEdit}s)", color = BravPurple) }
                        } else {
                            Text(
                                text = "Modification impossible (délai de 30s dépassé)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        TextButton(onClick = {
                            scope.launch { viewModel.chatRepository.deleteMessageForAll(conversationId, msg.id) }
                            selectedMsgForMenu = null
                        }) { Text("Supprimer pour tous", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = {
                        scope.launch { viewModel.chatRepository.deleteMessageForMe(conversationId, msg.id, myUid) }
                        selectedMsgForMenu = null
                    }) { Text("Supprimer pour moi") }
                    TextButton(onClick = {
                        multiSelectIds = setOf(msg.id)
                        selectedMsgForMenu = null
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.CheckBox, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Sélectionner plusieurs")
                        }
                    }
                    TextButton(onClick = {
                        infoTargetMsg = msg
                        selectedMsgForMenu = null
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Infos du message")
                        }
                    }
                    if (msg.senderId != myUid) {
                        TextButton(onClick = { reportTargetMsg = msg; selectedMsgForMenu = null }) {
                            Text("Signaler ce message", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedMsgForMenu = null }) { Text("Fermer") } }
        )
    }

    infoTargetMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { infoTargetMsg = null },
            containerColor = lux.modalSurface,
            icon = { Icon(Icons.Rounded.Info, contentDescription = null, tint = BravPurple) },
            title = { Text("Infos du message") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Expéditeur : ${conv?.participantNames?.get(msg.senderId) ?: msg.senderId.take(10)}")
                    Text("Statut : ${when (msg.status) { "read" -> "Lu"; "delivered" -> "Reçu"; else -> "Envoyé" }}")
                    Text("Chiffrement : ${if (msg.isEncrypted) "E2EE ECDH + AES-256-GCM" else "Standard"}")
                    msg.timestamp?.let { ts ->
                        Text("Envoyé le : ${formatShortDateTime(ts.seconds)}")
                    }
                    msg.editedAt?.let { ed ->
                        Text("Modifié le : ${formatShortDateTime(ed.seconds)}")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { infoTargetMsg = null }) { Text("Fermer") } }
        )
    }

    translateTargetMsg?.let { msg ->
        val plain = viewModel.chatRepository.decryptDisplayText(msg, myUid, conv)
        AlertDialog(
            onDismissRequest = { translateTargetMsg = null },
            containerColor = lux.modalSurface,
            title = { Text("Traduire le message vers :") },
            text = {
                Column {
                    AiTranslationRepository.supportedLanguages.forEach { lang ->
                        TextButton(onClick = {
                            viewModel.translateMessage(msg.id, plain, lang)
                            translateTargetMsg = null
                        }) { Text(lang) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { translateTargetMsg = null }) { Text("Annuler") } }
        )
    }

    forwardMsg?.let { msg ->
        val plain = viewModel.chatRepository.decryptDisplayText(msg, myUid, conv)
        val allConvs by viewModel.conversations.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { forwardMsg = null },
            containerColor = lux.modalSurface,
            title = { Text("Transférer vers...") },
            text = {
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(allConvs.filter { it.id != conversationId }, key = { it.id }) { targetConv ->
                        val title = if (targetConv.isGroup) targetConv.groupName else {
                            val p = targetConv.participants.firstOrNull { it != myUid }.orEmpty()
                            targetConv.participantNames[p] ?: "Contact BRAV"
                        }
                        TextButton(onClick = {
                            scope.launch {
                                viewModel.chatRepository.sendTextMessage(
                                    targetConv, myUid, plain,
                                    forwardedFrom = chatTitle
                                )
                                viewModel.showMessage("Message transféré vers $title")
                            }
                            forwardMsg = null
                        }) { Text(title) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { forwardMsg = null }) { Text("Fermer") } }
        )
    }

    if (showContactShareDialog) {
        val activeConv = conv
        AlertDialog(
            onDismissRequest = { showContactShareDialog = false },
            containerColor = lux.modalSurface,
            title = { Text("Envoyer un contact") },
            text = {
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(savedContacts, key = { it.userId }) { contact ->
                        TextButton(onClick = {
                            if (activeConv != null) {
                                scope.launch { viewModel.chatRepository.sendContactMessage(activeConv, myUid, contact) }
                            }
                            showContactShareDialog = false
                        }) { Text(contact.displayName) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContactShareDialog = false }) { Text("Fermer") } }
        )
    }

    reportTargetMsg?.let { msg ->
        var reason by remember { mutableStateOf("Spam ou contenu inapproprié") }
        AlertDialog(
            onDismissRequest = { reportTargetMsg = null },
            containerColor = lux.modalSurface,
            title = { Text("Signaler ce message") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motif") },
                    colors = bravOutlinedTextFieldColors()
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        viewModel.socialRepository.submitReport(
                            reporterId = myUid,
                            targetUid = msg.senderId,
                            conversationId = conversationId,
                            messageId = msg.id,
                            reason = reason,
                            details = "Signalé depuis la discussion"
                        )
                        viewModel.showMessage("Signalement envoyé à la modération.")
                    }
                    reportTargetMsg = null
                }) { Text("Signaler") }
            },
            dismissButton = { TextButton(onClick = { reportTargetMsg = null }) { Text("Annuler") } }
        )
    }

    if (showSafetyDialog) {
        val code = viewModel.chatRepository.computeSafetyCode(myUid, peerUser?.publicKey.orEmpty())
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            containerColor = lux.modalSurface,
            icon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = BravPurple) },
            title = { Text("Empreinte E2EE") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Comparez ce code de sécurité avec ${peerUser?.displayName ?: "votre contact"} pour vérifier le chiffrement ECDH + AES-256-GCM :")
                    Text(code, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            confirmButton = { TextButton(onClick = { showSafetyDialog = false }) { Text("OK") } }
        )
    }

    if (showWallpaperDialog) {
        AlertDialog(
            onDismissRequest = { showWallpaperDialog = false },
            containerColor = lux.modalSurface,
            title = { Text("Fond de discussion") },
            text = {
                Column {
                    listOf("default" to "Neutre", "violet" to "BRAV Violet", "sunset" to "Violet & Rouge", "emerald" to "Émeraude").forEach { (id, label) ->
                        TextButton(onClick = {
                            scope.launch { viewModel.chatRepository.updateConversationPref(myUid, conversationId, wallpaperId = id) }
                            showWallpaperDialog = false
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showWallpaperDialog = false }) { Text("Fermer") } }
        )
    }

    if (showGroupInfoDialog && conv != null) {
        val isAdminInGroup = myUid in conv.adminIds
        AlertDialog(
            onDismissRequest = { showGroupInfoDialog = false },
            containerColor = lux.modalSurface,
            title = { Text(conv.groupName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (conv.groupDescription.isNotBlank()) Text(conv.groupDescription, style = MaterialTheme.typography.bodySmall)
                    Text("Code d'invitation : ${conv.inviteCode}", fontWeight = FontWeight.Bold)
                    if (isAdminInGroup) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Admins seuls écrivent", modifier = Modifier.weight(1f))
                            Switch(
                                checked = conv.onlyAdminsCanMessage,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        viewModel.chatRepository.updateGroupSettings(conversationId, onlyAdminsCanMessage = checked)
                                    }
                                }
                            )
                        }
                    }
                    Text("Membres (${conv.participants.size}) :", fontWeight = FontWeight.SemiBold)
                    conv.participants.forEach { memberUid ->
                        val name = conv.participantNames[memberUid] ?: memberUid.take(8)
                        Text("• $name ${if (memberUid in conv.adminIds) "(Admin)" else ""}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showGroupInfoDialog = false }) { Text("Fermer") } }
        )
    }

    if (showDeleteConversationConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConversationConfirm = false },
            containerColor = lux.modalSurface,
            title = { Text("Supprimer la conversation ?") },
            text = {
                Text(
                    if (conv?.isGroup == true) "Voulez-vous supprimer ou quitter le groupe « $chatTitle » ?"
                    else "Cette conversation et tous ses messages seront définitivement supprimés."
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = BravRed, contentColor = Color.White),
                    onClick = {
                        showDeleteConversationConfirm = false
                        viewModel.deleteConversation(conversationId) { onBack() }
                    }
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConversationConfirm = false }) { Text("Annuler") }
            }
        )
    }

    lightboxMedia?.let { (url, isVideo, title) ->
        MediaLightboxDialog(
            url = url,
            isVideo = isVideo,
            title = title,
            workerApiClient = viewModel.workerApiClient,
            onSavedFeedback = { viewModel.showMessage(it) },
            onDismiss = { lightboxMedia = null }
        )
    }

    if (showLuxuryEmojiSheet) {
        val recentEmojis = remember(appPrefs.recentEmojisJson) {
            BravEmojiCatalog.parseListJson(appPrefs.recentEmojisJson)
        }
        val favoriteEmojis = remember(appPrefs.favoriteEmojisJson) {
            BravEmojiCatalog.parseListJson(appPrefs.favoriteEmojisJson)
        }
        LuxuryEmojiPickerSheet(
            onInsertEmoji = { emoji ->
                inputText += emoji
                viewModel.notifyTyping(conversationId, peerUid, inputText.isNotBlank())
                scope.launch { viewModel.prefsRepository.recordRecentEmoji(emoji) }
            },
            recentEmojis = recentEmojis,
            favoriteEmojis = favoriteEmojis,
            preferredSkinTone = appPrefs.preferredEmojiSkinTone,
            onToggleFavoriteEmoji = { emoji ->
                scope.launch { viewModel.prefsRepository.toggleFavoriteEmoji(emoji) }
            },
            onSelectSkinTone = { tone ->
                scope.launch { viewModel.prefsRepository.setPreferredEmojiSkinTone(tone) }
            },
            onDismiss = { showLuxuryEmojiSheet = false }
        )
    }

    if (showStickerSheet) {
        val recentStickers = remember(appPrefs.recentStickersJson) {
            BravEmojiCatalog.parseListJson(appPrefs.recentStickersJson)
        }
        val favoriteStickers = remember(appPrefs.favoriteStickersJson) {
            BravEmojiCatalog.parseListJson(appPrefs.favoriteStickersJson)
        }
        val allPackIds = remember { BravStickerCatalog.packs.map { it.id }.toSet() }
        val enabledPacks = remember(appPrefs.enabledStickerPacksJson) {
            val parsed = BravEmojiCatalog.parseListJson(appPrefs.enabledStickerPacksJson).toSet()
            parsed.ifEmpty { allPackIds }
        }
        BravStickerPickerSheet(
            recentStickerIds = recentStickers,
            favoriteStickerIds = favoriteStickers,
            enabledPackIds = enabledPacks,
            onSendSticker = { sticker ->
                val activeConv = conversation ?: return@BravStickerPickerSheet
                val replyTarget = replyToMsg
                replyToMsg = null
                showStickerSheet = false
                scope.launch {
                    viewModel.chatRepository.sendStickerMessage(
                        conversation = activeConv,
                        senderId = myUid,
                        stickerId = sticker.id,
                        stickerPackId = sticker.packId,
                        stickerLabel = sticker.label,
                        replyTo = replyTarget
                    )
                    viewModel.prefsRepository.recordRecentSticker(sticker.id)
                }
            },
            onToggleFavoriteSticker = { stickerId ->
                scope.launch { viewModel.prefsRepository.toggleFavoriteSticker(stickerId) }
            },
            onTogglePackEnabled = { packId, enabled ->
                scope.launch { viewModel.prefsRepository.setStickerPackEnabled(packId, enabled) }
            },
            onDismiss = { showStickerSheet = false }
        )
    }

    if (showPeerProfileModal) {
        peerUser?.let { peer ->
            val peerPosts = remember(statuses, peer.userId) {
                statuses.filter { it.userId == peer.userId }
            }
            val peerFollowRelation = remember(followingUserIds, outgoingFollowRequests, peer.userId) {
                viewModel.followRelationStateFor(peer.userId)
            }
            FriendProfileModal(
                user = peer,
                userPosts = peerPosts,
                myUid = myUid,
                isContact = savedContacts.any { it.userId == peer.userId },
                isOnline = isPeerOnline || peer.isOnline,
                isFollowing = peer.userId in followingUserIds,
                followRelationState = peerFollowRelation,
                onToggleFollow = { target -> viewModel.toggleFollowUser(target) },
                onSendMessage = { showPeerProfileModal = false },
                onStartAudioCall = {
                    showPeerProfileModal = false
                    launchCallWithPermissions(isVideo = false)
                },
                onDismiss = { showPeerProfileModal = false }
            )
        }
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val lux = BravTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = lux.translucentSubtleSurface,
        border = BorderStroke(0.75.dp, lux.violetContour),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = lux.titlePurple)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = lux.inkPrimary)
        }
    }
}

private fun queryFileName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && idx >= 0) return cursor.getString(idx)
    }
    return "fichier_${System.currentTimeMillis()}"
}
