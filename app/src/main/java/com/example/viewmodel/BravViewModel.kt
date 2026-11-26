package com.example.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiAutoReplyManager
import com.example.ai.AutoReplyContactRule
import com.example.ai.AutoReplyLogEntry
import com.example.ai.ContactRuleMode
import com.example.ai.LatexMathTopic
import com.example.ai.MathComputationEngine
import com.example.ai.MathComputationResult
import com.example.ai.PendingAutoReply
import com.example.export.DocTemplateType
import com.example.export.DocumentExportManager
import com.example.export.FormulaRecord
import com.example.local.AppPreferencesRepository
import com.example.local.AppPreferencesState
import com.example.local.BravLocalDatabase
import com.example.local.LoginHistoryEntity
import com.example.local.ThemeMode
import com.example.local.VideoAutoPlayMode
import com.example.media.VoiceProcessingConfig
import com.example.media.VoiceRecorderPlayer
import com.example.media.WebRtcCallManager
import com.example.messaging.BravFirebaseMessagingService
import com.example.model.AbuseReport
import com.example.model.AppNotification
import com.example.model.BlockedEntry
import com.example.model.Call
import com.example.model.CallAudioRoute
import com.example.model.CallLifecycleState
import com.example.model.CallerDirectoryEntry
import com.example.model.CallerReport
import com.example.model.CallerReportCategory
import com.example.model.CertificationEligibility
import com.example.model.CertificationPaymentReceipt
import com.example.model.Contact
import com.example.model.ContactRequest
import com.example.model.Conversation
import com.example.model.ConversationPref
import com.example.model.DesignerSyncPolicyState
import com.example.model.FollowRelationState
import com.example.model.FollowRequest
import com.example.model.InviteLink
import com.example.model.LiveLocationShare
import com.example.model.Message
import com.example.model.NotificationCategory
import com.example.model.NotificationCounters
import com.example.model.OfferListing
import com.example.model.StatusPost
import com.example.model.User
import com.example.model.UserDevice
import com.example.model.UserPrivateInfo
import com.example.model.VerificationCategory
import com.example.model.VerificationRequest
import com.example.model.VerificationStatus
import com.example.network.LoginSessionEntry
import com.example.network.PresenceStatus
import com.example.network.RealtimeEvent
import com.example.network.AutoReplyRuleDto
import com.example.network.WorkerAiModel
import com.example.network.WorkerAiReply
import com.example.network.WorkerApiClient
import com.example.network.WorkerDocumentResponse
import com.example.network.WorkerPaymentInitiateResult
import com.example.network.WorkerRealtimeClient
import com.example.security.BravHumanVerificationService
import com.example.telecom.CallerIdResolver
import org.json.JSONArray
import com.example.repository.AiTranslationRepository
import com.example.repository.CallRepository
import com.example.repository.ChatRepository
import com.example.repository.ContactSyncRepository
import com.example.repository.DecryptedCoordinates
import com.example.repository.MediaUploadPayload
import com.example.repository.NotificationRepository
import com.example.repository.SmsRepository
import com.example.repository.SmsThreadSummary
import com.example.repository.SocialRepository
import com.example.repository.UserRepository
import com.example.util.DefaultSmsHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class AiAssistantMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val model: String = "",
    val attachedMath: MathComputationResult? = null,
    val attachedDocument: WorkerDocumentResponse? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

class BravViewModel(
    val currentUserId: String,
    val userRepository: UserRepository,
    val contactRepository: ContactSyncRepository,
    val chatRepository: ChatRepository,
    val callRepository: CallRepository,
    val socialRepository: SocialRepository,
    val notificationRepository: NotificationRepository,
    val aiTranslationRepository: AiTranslationRepository,
    val prefsRepository: AppPreferencesRepository,
    val workerApiClient: WorkerApiClient,
    val realtimeClient: WorkerRealtimeClient,
    val voicePlayer: VoiceRecorderPlayer,
    val webRtcCallManager: WebRtcCallManager,
    private val appContext: Context? = null,
    private val localDb: BravLocalDatabase? = null,
    private val smsRepository: SmsRepository? = appContext?.let { SmsRepository(it) }
) : ViewModel() {

    val appPrefs: StateFlow<AppPreferencesState> = prefsRepository.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferencesState())

    val hasLoadedAppPrefs: StateFlow<Boolean> = prefsRepository.state
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _certificationEligibility = MutableStateFlow<CertificationEligibility?>(null)
    val certificationEligibility: StateFlow<CertificationEligibility?> = _certificationEligibility.asStateFlow()

    private val rawCurrentUser: StateFlow<User?> = userRepository.observeUser(currentUserId)
        .catch { handleFlowError(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentUser: StateFlow<User?> = combine(
        rawCurrentUser,
        _certificationEligibility
    ) { user, elig ->
        val isDesigner = userRepository.isVerifiedDesignerAccount() || elig?.exemptDesigner == true
        user?.withDesignerCertificationIfEligible(isDesigner)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val privateInfo: StateFlow<UserPrivateInfo?> = userRepository.observePrivateInfo(currentUserId)
        .catch { handleFlowError(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val devices: StateFlow<List<UserDevice>> = userRepository.observeDevices(currentUserId)
        .catch { handleFlowError(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedUsers: StateFlow<List<BlockedEntry>> = userRepository.observeBlockedUsers(currentUserId)
        .catch { handleFlowError(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<Conversation>> = chatRepository.observeConversations(currentUserId)
        .catch { handleFlowError(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversationPrefs: StateFlow<Map<String, ConversationPref>> =
        chatRepository.observeConversationPrefs(currentUserId)
            .catch { handleFlowError(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadCountByConversation: StateFlow<Map<String, Int>> = combine(
        conversations,
        conversationPrefs,
        _activeConversationId
    ) { convs, prefs, activeConvId ->
        Triple(convs.filter { prefs[it.id]?.hiddenLocally != true }.take(30), prefs, activeConvId)
    }.flatMapLatest { (activeConvs, prefs, activeConvId) ->
        if (activeConvs.isEmpty()) {
            flowOf(emptyMap())
        } else {
            val perConvFlows = activeConvs.map { conv ->
                val pref = prefs[conv.id]
                val lastReadAtMs = pref?.lastReadAtMs ?: 0L
                val isMarkedUnread = pref?.markedUnread == true
                if (conv.id == activeConvId) {
                    flowOf(conv.id to 0)
                } else {
                    chatRepository.observeUnreadIncomingCountForConversation(
                        conversationId = conv.id,
                        myUid = currentUserId,
                        lastReadAtMs = lastReadAtMs
                    )
                        .catch { emit(0) }
                        .map { incomingCount ->
                            val effective = if (incomingCount > 0) incomingCount else if (isMarkedUnread) 1 else 0
                            conv.id to effective
                        }
                }
            }
            combine(perConvFlows) { pairs ->
                pairs.filter { it.second > 0 }.toMap()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val totalUnreadChatsCount: StateFlow<Int> = unreadCountByConversation
        .map { map -> map.values.sum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _workerNotifications = MutableStateFlow<List<AppNotification>>(emptyList())
    private val _locallyReadNotifIds = MutableStateFlow<Set<String>>(emptySet())
    private val _locallyDeletedNotifIds = MutableStateFlow<Set<String>>(emptySet())

    val notifications: StateFlow<List<AppNotification>> = combine(
        notificationRepository.observeNotifications(currentUserId)
            .catch {
                handleFlowError(it)
                emit(emptyList())
            },
        _workerNotifications,
        _locallyReadNotifIds,
        _locallyDeletedNotifIds
    ) { fsList, workerList, readIds, deletedIds ->
        NotificationRepository.mergeNotifications(
            firestoreList = fsList,
            workerList = workerList,
            locallyReadIds = readIds,
            locallyDeletedIds = deletedIds
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notificationCounters: StateFlow<NotificationCounters> = notifications
        .map { NotificationRepository.computeCounters(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationCounters())

    val callHistory: StateFlow<List<Call>> = callRepository.observeCallHistory(currentUserId)
        .catch { handleFlowError(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _realtimeIncomingCall = MutableStateFlow<Call?>(null)
    private var lastNotifiedCallId: String? = null

    val incomingCall: StateFlow<Call?> = combine(
        callRepository.observeIncomingCalls(currentUserId)
            .catch {
                handleFlowError(it)
                emit(emptyList())
            },
        _realtimeIncomingCall
    ) { fsList, wsCall ->
        fsList.firstOrNull() ?: wsCall?.takeIf { it.status == "calling" }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val followingUserIds: StateFlow<Set<String>> =
        userRepository.observeFollowingIds(currentUserId)
            .catch { handleFlowError(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val followerUserIds: StateFlow<Set<String>> =
        userRepository.observeFollowerIds(currentUserId)
            .catch { handleFlowError(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val incomingFollowRequests: StateFlow<List<FollowRequest>> =
        userRepository.observeIncomingFollowRequests(currentUserId)
            .catch { handleFlowError(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outgoingFollowRequests: StateFlow<List<FollowRequest>> =
        userRepository.observeOutgoingFollowRequests(currentUserId)
            .catch { handleFlowError(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myInvites: StateFlow<List<InviteLink>> =
        userRepository.observeMyInvites(currentUserId)
            .catch { handleFlowError(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>> = _searchResults.asStateFlow()

    val myLiveLocations: StateFlow<List<LiveLocationShare>> =
        socialRepository.observeMySharedLocations(currentUserId)
            .catch { handleFlowError(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sharedLiveLocations: StateFlow<List<LiveLocationShare>> =
        socialRepository.observeLocationsSharedWithMe(currentUserId)
            .catch { handleFlowError(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offers: StateFlow<List<OfferListing>> = socialRepository.observeOffers()
        .catch { handleFlowError(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomingRequests: StateFlow<List<ContactRequest>> =
        contactRepository.observeIncomingRequests(currentUserId)
            .catch { handleFlowError(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outgoingRequests: StateFlow<List<ContactRequest>> =
        contactRepository.observeOutgoingRequests(currentUserId)
            .catch { handleFlowError(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedContactUsers: StateFlow<List<User>> =
        contactRepository.observeSavedContactUids(currentUserId)
            .map { pairs ->
                val users = contactRepository.fetchUsersByIds(pairs.map { it.first })
                val map = users.associateBy { it.userId }
                pairs.mapNotNull { map[it.first] }
            }
            .catch { handleFlowError(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val presenceMap: StateFlow<Map<String, PresenceStatus>> = realtimeClient.presenceMap
    val typingConversations: StateFlow<Map<String, Set<String>>> = realtimeClient.typingConversations
    val isRealtimeConnected: StateFlow<Boolean> = realtimeClient.isConnected

    private val _phoneContacts = MutableStateFlow<List<Contact>>(emptyList())
    val phoneContacts: StateFlow<List<Contact>> = _phoneContacts.asStateFlow()

    private val _isSyncingContacts = MutableStateFlow(false)
    val isSyncingContacts: StateFlow<Boolean> = _isSyncingContacts.asStateFlow()

    private val _translations = MutableStateFlow<Map<String, String>>(emptyMap())
    val translations: StateFlow<Map<String, String>> = _translations.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private val _activeCall = MutableStateFlow<Call?>(null)
    val activeCall: StateFlow<Call?> = _activeCall.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val incomingCallPeer: StateFlow<User?> = incomingCall
        .flatMapLatest { call ->
            if (call == null || call.callerId.isBlank()) flowOf(null)
            else userRepository.observeUser(call.callerId).catch { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeCallPeer: StateFlow<User?> = _activeCall
        .flatMapLatest { call ->
            if (call == null) flowOf(null)
            else {
                val peerUid = if (call.callerId == currentUserId) call.receiverId else call.callerId
                if (peerUid.isBlank()) flowOf(null)
                else userRepository.observeUser(peerUid).catch { emit(null) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _terminalCallState = MutableStateFlow<Pair<Call, String>?>(null)
    val terminalCallState: StateFlow<Pair<Call, String>?> = _terminalCallState.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _adminReports = MutableStateFlow<List<AbuseReport>>(emptyList())
    val adminReports: StateFlow<List<AbuseReport>> = _adminReports.asStateFlow()

    val callerDirectoryEntries: StateFlow<List<CallerDirectoryEntry>> =
        socialRepository.observeCallerDirectory()
            .catch { handleFlowError(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val approvedCallerReports: StateFlow<List<CallerReport>> =
        socialRepository.observeApprovedCallerReports()
            .catch { handleFlowError(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _adminCallerReports = MutableStateFlow<List<CallerReport>>(emptyList())
    val adminCallerReports: StateFlow<List<CallerReport>> = _adminCallerReports.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _smsThreads = MutableStateFlow<List<SmsThreadSummary>>(emptyList())
    val smsThreads: StateFlow<List<SmsThreadSummary>> = _smsThreads.asStateFlow()

    private val _isLoadingSms = MutableStateFlow(false)
    val isLoadingSms: StateFlow<Boolean> = _isLoadingSms.asStateFlow()

    private val _isRefreshingActus = MutableStateFlow(false)
    val isRefreshingActus: StateFlow<Boolean> = _isRefreshingActus.asStateFlow()

    private val _loginHistory = MutableStateFlow<List<LoginSessionEntry>>(emptyList())
    val loginHistory: StateFlow<List<LoginSessionEntry>> = _loginHistory.asStateFlow()

    private val _aiModels = MutableStateFlow(AiTranslationRepository.defaultFreeModels)
    val aiModels: StateFlow<List<WorkerAiModel>> = _aiModels.asStateFlow()

    private val _aiMessages = MutableStateFlow<List<AiAssistantMessage>>(emptyList())
    val aiMessages: StateFlow<List<AiAssistantMessage>> = _aiMessages.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _mathResult = MutableStateFlow<MathComputationResult?>(null)
    val mathResult: StateFlow<MathComputationResult?> = _mathResult.asStateFlow()

    private val _mathAiExplanation = MutableStateFlow("")
    val mathAiExplanation: StateFlow<String> = _mathAiExplanation.asStateFlow()

    private val _isMathAiLoading = MutableStateFlow(false)
    val isMathAiLoading: StateFlow<Boolean> = _isMathAiLoading.asStateFlow()

    val savedFormulas: StateFlow<List<FormulaRecord>> = appPrefs
        .map { DocumentExportManager.parseSavedFormulas(it.savedFormulasJson) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val autoReplyRules: StateFlow<List<AutoReplyContactRule>> = appPrefs
        .map { AiAutoReplyManager.parseContactRules(it.aiAutoReplyContactRulesJson) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val autoReplyLogs: StateFlow<List<AutoReplyLogEntry>> = AiAutoReplyManager.logs
    val pendingAutoReplies: StateFlow<List<PendingAutoReply>> = AiAutoReplyManager.pendingReplies

    val myVerificationRequests: StateFlow<List<VerificationRequest>> =
        userRepository.observeMyVerificationRequests(currentUserId)
            .catch { handleFlowError(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _lastCertificationReceipt = MutableStateFlow<CertificationPaymentReceipt?>(null)
    val lastCertificationReceipt: StateFlow<CertificationPaymentReceipt?> = _lastCertificationReceipt.asStateFlow()

    private val _isCertificationBusy = MutableStateFlow(false)
    val isCertificationBusy: StateFlow<Boolean> = _isCertificationBusy.asStateFlow()

    private val _adminVerificationRequests = MutableStateFlow<List<VerificationRequest>>(emptyList())
    val adminVerificationRequests: StateFlow<List<VerificationRequest>> =
        _adminVerificationRequests.asStateFlow()

    val knownUsersDirectory: StateFlow<Map<String, User>> = combine(
        currentUser,
        savedContactUsers,
        _searchResults
    ) { me, contacts, searched ->
        buildMap {
            contacts.forEach { put(it.userId, it) }
            searched.forEach { put(it.userId, it) }
            me?.let { put(it.userId, it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val followingUsers: StateFlow<List<User>> = followingUserIds
        .map { ids -> userRepository.fetchUsersByIds(ids.toList()) }
        .catch { handleFlowError(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val followerUsers: StateFlow<List<User>> = followerUserIds
        .map { ids -> userRepository.fetchUsersByIds(ids.toList()) }
        .catch { handleFlowError(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statuses: StateFlow<List<StatusPost>> = combine(
        socialRepository.observeStatuses(currentUserId)
            .catch {
                handleFlowError(it)
                emit(emptyList())
            },
        followingUserIds,
        knownUsersDirectory
    ) { posts, followingIds, usersDir ->
        posts.filter { post ->
            if (post.userId == currentUserId || currentUserId in post.allowedUids) return@filter true
            val author = usersDir[post.userId]
            if (author?.isPrivateProfile == true) post.userId in followingIds else true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val actusSearchHistory: StateFlow<List<String>> = appPrefs
        .map { parseStringListJson(it.actusSearchHistoryJson) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var lastObservedVerificationStatus: String? = null
    private var designerReloadAttempted = false
    private val designerSyncMutex = Mutex()
    private var designerSyncPolicy = DesignerSyncPolicyState()
    private var lastWorkerEligibilityCheckMs = 0L
    private var cachedServerDesignerExempt: Boolean? = null
    private var activeCallObserverJob: Job? = null
    private var ringTimeoutJob: Job? = null
    private var terminalBannerJob: Job? = null
    private var typingAutoClearJob: Job? = null
    private var lastTypingConversationId: String = ""
    private var lastFirestoreTypingWriteMs: Long = 0L
    private var lastFirestoreTypingValue: Boolean = false

    init {
        viewModelScope.launch {
            chatRepository.flushOfflineOutbox()
        }
        startAutoObservers()
        refreshLoginHistory()
        refreshAiModels()
        refreshNotifications()
        viewModelScope.launch {
            appPrefs.collect { prefs ->
                webRtcCallManager.updateProcessingConfig(
                    VoiceProcessingConfig(
                        noiseSuppressionEnabled = prefs.noiseSuppressionEnabled,
                        echoCancellationEnabled = prefs.echoCancellationEnabled,
                        autoGainControlEnabled = prefs.autoGainControlEnabled,
                        voicePriorityEnabled = prefs.voicePriorityEnabled
                    )
                )
            }
        }
        viewModelScope.launch {
            conversations.collect { list ->
                val uids = list.flatMap { it.participants }.filter { it != currentUserId }.distinct()
                if (uids.isNotEmpty()) realtimeClient.watchUsers(uids)
            }
        }
        viewModelScope.launch {
            incomingCall.collect { call ->
                val ctx = appContext
                val prefs = appPrefs.value
                if (call != null && _activeCall.value == null) {
                    if (ctx != null && lastNotifiedCallId != call.id &&
                        prefs.notificationsEnabled && prefs.notifyCalls
                    ) {
                        lastNotifiedCallId = call.id
                        val resolvedAvatar = call.callerAvatar.ifBlank {
                            userRepository.getUserById(call.callerId)?.avatarUrl.orEmpty()
                        }
                        BravFirebaseMessagingService.showIncomingCallNotification(
                            context = ctx,
                            callId = call.id,
                            callerName = call.callerName,
                            callerAvatarUrl = resolvedAvatar,
                            isVideo = call.callType == "video",
                            soundEnabled = prefs.notifySoundEnabled,
                            vibrationEnabled = prefs.notifyVibrationEnabled
                        )
                    }
                } else {
                    if (ctx != null && lastNotifiedCallId != null) {
                        BravFirebaseMessagingService.cancelIncomingCallNotification(ctx, lastNotifiedCallId!!)
                        lastNotifiedCallId = null
                    }
                }
            }
        }
        viewModelScope.launch {
            realtimeClient.events.collect { ev ->
                when (ev) {
                    is RealtimeEvent.IncomingCall -> {
                        if (_activeCall.value == null && ev.callId.isNotBlank() && ev.callerId != currentUserId) {
                            val fetched = callRepository.getCall(ev.callId) ?: Call(
                                id = ev.callId,
                                callerId = ev.callerId,
                                callerName = ev.callerName,
                                receiverId = currentUserId,
                                participantIds = listOf(ev.callerId, currentUserId),
                                callType = ev.callType,
                                status = "calling"
                            )
                            if (fetched.status == "calling") {
                                _realtimeIncomingCall.value = fetched
                            }
                        }
                    }
                    is RealtimeEvent.CallEnded -> {
                        if (_realtimeIncomingCall.value?.id == ev.callId) {
                            _realtimeIncomingCall.value = null
                        }
                        val active = _activeCall.value
                        if (active?.id == ev.callId) {
                            activeCallObserverJob?.cancel()
                            ringTimeoutJob?.cancel()
                            webRtcCallManager.endSession(sendHangup = false)
                            _activeCall.value = null
                            showTerminalCallBanner(active, "Appel terminé")
                        }
                    }
                    is RealtimeEvent.MissedCall -> {
                        val incoming = _realtimeIncomingCall.value
                        if (incoming?.id == ev.callId) {
                            _realtimeIncomingCall.value = null
                            val prefs = appPrefs.value
                            if (appContext != null && prefs.notificationsEnabled && prefs.notifyMissedCalls) {
                                BravFirebaseMessagingService.showMissedCallNotification(
                                    context = appContext,
                                    callId = incoming.id,
                                    callerName = incoming.callerName,
                                    callerAvatarUrl = incoming.callerAvatar,
                                    isVideo = incoming.callType == "video",
                                    soundEnabled = prefs.notifySoundEnabled,
                                    vibrationEnabled = prefs.notifyVibrationEnabled
                                )
                            }
                        }
                        val active = _activeCall.value
                        if (active?.id == ev.callId) {
                            activeCallObserverJob?.cancel()
                            ringTimeoutJob?.cancel()
                            webRtcCallManager.endSession(sendHangup = false)
                            _activeCall.value = null
                            showTerminalCallBanner(active, "Appel manqué")
                        }
                    }
                    is RealtimeEvent.IncomingMessage -> {
                        handleBravChatAutoReply(ev)
                        if (ev.conversationId == _activeConversationId.value && ev.senderId != currentUserId) {
                            viewModelScope.launch {
                                chatRepository.markConversationAsRead(currentUserId, ev.conversationId)
                                notificationRepository.markByRefIdRead(currentUserId, ev.conversationId)
                            }
                        } else {
                            refreshNotifications()
                        }
                    }
                    is RealtimeEvent.NotificationReceived -> {
                        val incoming = ev.notification
                        val isOpenChatMsg = incoming.categoryEnum == NotificationCategory.MESSAGES &&
                            incoming.refId.isNotBlank() &&
                            incoming.refId == _activeConversationId.value
                        if (isOpenChatMsg) {
                            viewModelScope.launch {
                                notificationRepository.markAsRead(currentUserId, incoming.id, incoming.refId)
                            }
                        } else {
                            _workerNotifications.value = (listOf(incoming) + _workerNotifications.value)
                                .distinctBy { it.dedupKey.ifBlank { it.id } }
                            appContext?.let { ctx ->
                                BravFirebaseMessagingService.showCategorizedNotification(
                                    context = ctx,
                                    notification = incoming,
                                    prefs = appPrefs.value
                                )
                            }
                        }
                    }
                    is RealtimeEvent.NotificationsRead -> {
                        val markSet = buildSet {
                            addAll(ev.ids)
                            if (ev.refId.isNotBlank()) {
                                notifications.value
                                    .filter { it.refId == ev.refId }
                                    .forEach { n ->
                                        add(n.id)
                                        if (n.dedupKey.isNotBlank()) add(n.dedupKey)
                                    }
                            }
                        }
                        if (markSet.isNotEmpty()) {
                            _locallyReadNotifIds.value = _locallyReadNotifIds.value + markSet
                        }
                    }
                    is RealtimeEvent.NotificationsReadAll -> {
                        val cat = ev.category
                        val markSet = notifications.value
                            .filter { cat.isBlank() || it.category.equals(cat, ignoreCase = true) }
                            .flatMap { listOfNotNull(it.id.takeIf(String::isNotBlank), it.dedupKey.takeIf(String::isNotBlank)) }
                            .toSet()
                        if (markSet.isNotEmpty()) {
                            _locallyReadNotifIds.value = _locallyReadNotifIds.value + markSet
                        }
                    }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            combine(
                knownUsersDirectory,
                approvedCallerReports,
                callerDirectoryEntries,
                appPrefs
            ) { usersMap, reports, dirEntries, prefs ->
                listOf(usersMap.values.toList(), reports, dirEntries, prefs)
            }.collect { tuple ->
                @Suppress("UNCHECKED_CAST")
                val usersList = tuple[0] as List<User>
                @Suppress("UNCHECKED_CAST")
                val reports = tuple[1] as List<CallerReport>
                @Suppress("UNCHECKED_CAST")
                val dirEntries = tuple[2] as List<CallerDirectoryEntry>
                val prefs = tuple[3] as AppPreferencesState
                CallerIdResolver.updateRuntimeDirectory(
                    users = usersList,
                    approvedReports = reports,
                    callerIdEnabled = prefs.callerIdEnabled,
                    spamWarningsEnabled = prefs.callerIdSpamWarningsEnabled,
                    callerDirectory = dirEntries
                )
            }
        }
        viewModelScope.launch {
            rawCurrentUser.collect { me ->
                if (me == null) return@collect
                syncDesignerCertificationIfNeeded(me)
            }
        }
        viewModelScope.launch {
            myVerificationRequests.collect { list ->
                val latest = list.firstOrNull() ?: return@collect
                val prev = lastObservedVerificationStatus
                lastObservedVerificationStatus = latest.status
                val newSt = VerificationStatus.fromRaw(latest.status)
                val isAutoDesignerVerified = latest.designerExempt && newSt == VerificationStatus.VERIFIED
                if (!isAutoDesignerVerified &&
                    prev != null && prev != latest.status && appContext != null &&
                    newSt != VerificationStatus.NONE && newSt != VerificationStatus.PENDING
                ) {
                    _snackbarMessage.value = when (newSt) {
                        VerificationStatus.VERIFIED -> "CERTIFICATION : votre badge bleu est actif !"
                        VerificationStatus.AWAITING_PAYMENT -> "Selfie validé ! Réglez 3 600 F CFA (5,99 USD) pour activer votre badge."
                        VerificationStatus.RETAKE_REQUIRED -> "Selfie à reprendre pour finaliser CERTIFICATION"
                        VerificationStatus.IN_REVIEW -> "Dossier ${latest.caseNumber} en cours d'examen"
                        VerificationStatus.REJECTED -> "Certification refusée : ${latest.reviewerNote.ifBlank { "consultez le motif." }}"
                        VerificationStatus.SUSPENDED -> "Certification suspendue temporairement"
                        VerificationStatus.REVOKED -> "Certification révoquée"
                        VerificationStatus.EXPIRED -> "Certification expirée — renouvellement nécessaire"
                        else -> "Statut certification mis à jour"
                    }
                    BravFirebaseMessagingService.showCertificationDecisionNotification(
                        context = appContext,
                        status = newSt,
                        caseNumber = latest.caseNumber,
                        note = latest.reviewerNote
                    )
                }
            }
        }
    }

    private suspend fun syncDesignerCertificationIfNeeded(rawMe: User) {
        val matchesDesignerEmail = userRepository.currentUserMatchesDesignerEmail()
        if (!matchesDesignerEmail) return

        val isAlreadyNormalized = rawMe.isDesignerProfileNormalized()
        if (userRepository.isVerifiedDesignerAccount() || cachedServerDesignerExempt == true) {
            val nextElig = CertificationEligibility(
                followersCount = rawMe.followersCount,
                requiredFollowers = CertificationEligibility.REQUIRED_FOLLOWERS,
                eligible = true,
                exemptDesigner = true,
                status = VerificationStatus.VERIFIED,
                caseNumber = _certificationEligibility.value?.caseNumber?.ifBlank { null }
                    ?: "BRV-DESIGNER-${rawMe.userId.take(6).uppercase()}"
            )
            if (_certificationEligibility.value != nextElig) {
                _certificationEligibility.value = nextElig
            }
        }

        val nowEarly = System.currentTimeMillis()
        if (!designerSyncPolicy.shouldAttemptSync(isAlreadyNormalized = isAlreadyNormalized, nowMs = nowEarly)) {
            return
        }

        designerSyncMutex.withLock {
            val latestMe = rawCurrentUser.value ?: rawMe
            val normalizedNow = latestMe.isDesignerProfileNormalized()
            val nowMs = System.currentTimeMillis()
            if (!designerSyncPolicy.shouldAttemptSync(isAlreadyNormalized = normalizedNow, nowMs = nowMs)) {
                return@withLock
            }

            val fbUser = runCatching { Firebase.auth.currentUser }.getOrNull()
            if (fbUser != null && !fbUser.isEmailVerified && !designerReloadAttempted) {
                designerReloadAttempted = true
                runCatching { fbUser.reload().await() }
            }

            val isClientDesigner = userRepository.isVerifiedDesignerAccount()
            if (!isClientDesigner && cachedServerDesignerExempt == null && nowMs - lastWorkerEligibilityCheckMs > 15_000L) {
                lastWorkerEligibilityCheckMs = nowMs
                val serverElig = runCatching { userRepository.checkCertificationEligibility(latestMe) }.getOrNull()
                if (serverElig != null) {
                    cachedServerDesignerExempt = serverElig.exemptDesigner
                    if (_certificationEligibility.value != serverElig) {
                        _certificationEligibility.value = serverElig
                    }
                }
            }

            val serverConfirmedExempt = cachedServerDesignerExempt == true
            if (!isClientDesigner && !serverConfirmedExempt) return@withLock

            val nextElig = CertificationEligibility(
                followersCount = latestMe.followersCount,
                requiredFollowers = CertificationEligibility.REQUIRED_FOLLOWERS,
                eligible = true,
                exemptDesigner = true,
                status = VerificationStatus.VERIFIED,
                caseNumber = _certificationEligibility.value?.caseNumber?.ifBlank { null }
                    ?: "BRV-DESIGNER-${latestMe.userId.take(6).uppercase()}"
            )
            if (_certificationEligibility.value != nextElig) {
                _certificationEligibility.value = nextElig
            }

            designerSyncPolicy = designerSyncPolicy.copy(
                attemptCount = designerSyncPolicy.attemptCount + 1,
                lastAttemptMs = nowMs
            )

            val res = userRepository.activateDesignerCertificationIfEligible(
                user = latestMe,
                allowServerConfirmedFallback = serverConfirmedExempt
            )
            if (res.getOrDefault(false)) {
                designerSyncPolicy = designerSyncPolicy.copy(completedForSession = true)
            }
        }
    }

    fun checkAdmin(email: String?) {
        viewModelScope.launch {
            val admin = userRepository.checkIsAdmin(currentUserId, email)
            _isAdmin.value = admin
            if (admin) {
                launch {
                    socialRepository.observeAllReportsForAdmin()
                        .catch { handleFlowError(it) }
                        .collect { _adminReports.value = it }
                }
                launch {
                    userRepository.observeAllVerificationRequestsForAdmin()
                        .catch { handleFlowError(it) }
                        .collect { _adminVerificationRequests.value = it }
                }
                launch {
                    socialRepository.observeAllCallerReportsForAdmin()
                        .catch { handleFlowError(it) }
                        .collect { _adminCallerReports.value = it }
                }
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun searchUsers(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _searchResults.value = emptyList()
                return@launch
            }
            _searchResults.value = contactRepository.searchUsers(query, currentUserId)
        }
    }

    fun syncDeviceContacts() {
        viewModelScope.launch {
            _isSyncingContacts.value = true
            _phoneContacts.value = contactRepository.syncPhoneContacts(currentUserId)
            _isSyncingContacts.value = false
        }
    }

    fun sendContactRequest(target: User) {
        val me = currentUser.value ?: return
        viewModelScope.launch {
            val res = contactRepository.sendContactRequest(me, target)
            _snackbarMessage.value = if (res.isSuccess) {
                "Invitation envoyée à ${target.displayName}"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Échec d'envoi"
            }
        }
    }

    fun acceptRequest(request: ContactRequest) {
        viewModelScope.launch {
            val res = contactRepository.acceptContactRequest(request, currentUserId)
            if (res.isSuccess) _snackbarMessage.value = "${request.fromName} ajouté à vos contacts"
        }
    }

    fun declineRequest(request: ContactRequest) {
        viewModelScope.launch { contactRepository.declineContactRequest(request) }
    }

    fun removeContact(uid: String) {
        viewModelScope.launch {
            contactRepository.removeContact(currentUserId, uid)
            _snackbarMessage.value = "Contact supprimé"
        }
    }

    suspend fun openOrCreateDirectChat(peer: User): Result<Conversation> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Profil en cours de chargement"))
        return chatRepository.getOrCreateDirectConversation(me, peer)
    }

    suspend fun createGroup(members: List<User>, name: String, desc: String): Result<Conversation> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Profil manquant"))
        return chatRepository.createGroupConversation(me, members, name, desc)
    }

    fun joinGroupByCode(code: String, onOpened: (String) -> Unit) {
        val me = currentUser.value ?: return
        viewModelScope.launch {
            val res = chatRepository.joinGroupByInviteCode(code, me)
            if (res.isSuccess) {
                onOpened(res.getOrThrow().id)
            } else {
                _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Code invalide"
            }
        }
    }

    fun translateMessage(messageId: String, originalText: String, targetLanguage: String) {
        viewModelScope.launch {
            val res = aiTranslationRepository.translateMessage(
                text = originalText,
                targetLanguage = targetLanguage,
                preferredModel = appPrefs.value.preferredAiModel
            )
            if (res.isSuccess) {
                _translations.value = _translations.value + (messageId to "[$targetLanguage] ${res.getOrThrow()}")
            } else {
                _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Erreur de traduction"
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun startAutoObservers() {
        val ctx = appContext ?: return
        viewModelScope.launch {
            contactRepository.observeDeviceContactsChanges()
                .debounce(1200L)
                .catch { handleFlowError(it) }
                .collect {
                    val granted = ContextCompat.checkSelfPermission(
                        ctx,
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        syncDeviceContacts()
                    }
                }
        }
        viewModelScope.launch {
            smsRepository?.observeSmsChanges()
                ?.debounce(600L)
                ?.catch { handleFlowError(it) }
                ?.collect {
                    if (DefaultSmsHelper.hasSmsReadPermission(ctx)) {
                        refreshSmsThreads()
                    }
                }
        }
        localDb?.let { db ->
            viewModelScope.launch {
                db.loginHistoryDao().observeForUser(currentUserId)
                    .catch { handleFlowError(it) }
                    .collect { rows ->
                        if (_loginHistory.value.isEmpty() && rows.isNotEmpty()) {
                            _loginHistory.value = rows.map {
                                LoginSessionEntry(
                                    id = it.id,
                                    deviceId = it.deviceId,
                                    deviceName = it.deviceName,
                                    countryCode = it.countryCode,
                                    city = it.city,
                                    createdAtMs = it.createdAtMs
                                )
                            }
                        }
                    }
            }
        }
    }

    fun refreshSmsThreads() {
        val repo = smsRepository ?: return
        viewModelScope.launch {
            _isLoadingSms.value = true
            _smsThreads.value = repo.loadSmsThreads(_phoneContacts.value)
            _isLoadingSms.value = false
        }
    }

    fun sendSmsMessage(destination: String, body: String, onSent: (Boolean) -> Unit = {}) {
        val repo = smsRepository ?: run {
            onSent(false)
            return
        }
        viewModelScope.launch {
            val res = repo.sendSms(destination, body)
            if (res.isSuccess) {
                refreshSmsThreads()
                onSent(true)
            } else {
                _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Échec d'envoi SMS"
                onSent(false)
            }
        }
    }

    fun markSmsRead(threadId: Long) {
        val repo = smsRepository ?: return
        viewModelScope.launch {
            repo.markThreadAsRead(threadId)
            refreshSmsThreads()
        }
    }

    fun deleteSmsThread(threadId: Long) {
        val repo = smsRepository ?: return
        viewModelScope.launch {
            val res = repo.deleteThread(threadId)
            if (res.isSuccess) {
                refreshSmsThreads()
                _snackbarMessage.value = "Fil SMS supprimé localement"
            } else {
                _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Suppression SMS impossible"
            }
        }
    }

    fun deleteSmsMessage(messageId: String, isMms: Boolean = false) {
        val repo = smsRepository ?: return
        viewModelScope.launch {
            val res = repo.deleteSmsMessage(messageId, isMms)
            if (res.isSuccess) {
                refreshSmsThreads()
                _snackbarMessage.value = "Message supprimé sur cet appareil"
            } else {
                _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Suppression du message impossible"
            }
        }
    }

    fun refreshLoginHistory() {
        viewModelScope.launch {
            val res = workerApiClient.fetchLoginHistory()
            val list = res.getOrNull().orEmpty()
            if (list.isNotEmpty()) {
                _loginHistory.value = list
                localDb?.let { db ->
                    runCatching {
                        db.loginHistoryDao().insertAll(
                            list.map { item ->
                                LoginHistoryEntity(
                                    id = item.id,
                                    userId = currentUserId,
                                    deviceId = item.deviceId,
                                    deviceName = item.deviceName,
                                    countryCode = item.countryCode,
                                    city = item.city,
                                    createdAtMs = item.createdAtMs
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    fun refreshAiModels() {
        viewModelScope.launch {
            val models = aiTranslationRepository.getAvailableModels()
            if (models.isNotEmpty()) {
                _aiModels.value = models
            }
        }
    }

    fun sendAiPrompt(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || _isAiThinking.value) return
        val historyPairs = _aiMessages.value.takeLast(8).map { msg ->
            (if (msg.isUser) "user" else "assistant") to msg.text
        }
        val userMsg = AiAssistantMessage(isUser = true, text = clean)
        _aiMessages.value = _aiMessages.value + userMsg
        _isAiThinking.value = true
        viewModelScope.launch {
            val lower = clean.lowercase()
            val isDocIntent = Regex("""\b(pdf|word|docx|rapport|exposé|expose|mémoire|memoire|fiche pédagogique|génère un document|rédige un document)\b""")
                .containsMatchIn(lower)
            val isMathIntent = clean.contains("\\frac") || clean.contains("\\int") || clean.contains("\\lim") ||
                clean.contains("\\sum") || clean.contains("\\det") || clean.contains("\\vec") ||
                clean.contains("y'") ||
                Regex("""\b(calcule|calculer|dérivée|intégrale|équation|résoudre|racine|matrice|déterminant|vecteur|vecteurs|taylor|latex|probabilité|statistique|moyenne|variance|écart-type|trigonométrie|géométrie|périmètre|aire|volume|somme|addition|soustraction|multiplication|division|pourcentage|démontre|démonstration)\b""")
                    .containsMatchIn(lower)

            if (isDocIntent) {
                val template = when {
                    lower.contains("exposé") || lower.contains("expose") -> DocTemplateType.EXPOSE.name
                    lower.contains("mémoire") || lower.contains("memoire") -> DocTemplateType.MEMOIRE.name
                    lower.contains("fiche") -> DocTemplateType.FICHE_PEDAGOGIQUE.name
                    else -> DocTemplateType.RAPPORT.name
                }
                val docRes = aiTranslationRepository.generateDocumentWithCloudflareAi(
                    prompt = clean,
                    templateType = template,
                    preferredModel = appPrefs.value.preferredAiModel
                )
                if (docRes.isSuccess) {
                    val doc = docRes.getOrThrow()
                    _isAiThinking.value = false
                    val previewText = buildString {
                        appendLine("Document prêt : ${doc.title}")
                        if (doc.bodyMarkdown.isNotBlank()) {
                            appendLine()
                            appendLine(doc.bodyMarkdown)
                        }
                        if (doc.keyFormulasLatex.isNotEmpty()) {
                            appendLine()
                            appendLine("Formules clés :")
                            doc.keyFormulasLatex.forEach { appendLine("$$ $it $$") }
                        }
                    }.trim()
                    _aiMessages.value = _aiMessages.value + AiAssistantMessage(
                        isUser = false,
                        text = previewText,
                        model = doc.model,
                        attachedDocument = doc
                    )
                    return@launch
                }
            }

            val localMath = if (isMathIntent) {
                MathComputationEngine.compute(clean, LatexMathTopic.fromId(appPrefs.value.preferredMathTopic))
                    .takeIf { it.isSuccess }
            } else null

            val systemPrompt = if (isMathIntent) {
                "${AiTranslationRepository.STRICT_FRENCH_SYSTEM_RULE} ${AiTranslationRepository.MATH_PEDAGOGY_SYSTEM_RULE} " +
                    "Tu es le professeur de mathématiques de BRAV Chat. Adapte la structure au niveau de difficulté (Énoncé, Données, Méthode, Résolution détaillée, Résultat final, Interprétation). Ne montre jamais de code LaTeX brut non délimité par $...$ ou $$...$$."
            } else {
                "${AiTranslationRepository.STRICT_FRENCH_SYSTEM_RULE} Tu es l'assistant personnel intelligent de BRAV Chat. Si la question comporte des mathématiques, applique la méthode pédagogique étape par étape en français avec formules encadrées par $...$ ou $$...$$ sans jamais montrer de code LaTeX brut."
            }

            val res = aiTranslationRepository.askAssistant(
                prompt = if (localMath != null) {
                    "$clean\n\n[Donnée vérifiée en interne : ${localMath.primaryResult} ; équation visuelle : $$${localMath.latexFormula}$$ — Explique maintenant la démarche complète étape par étape en français lisible pour l'utilisateur.]"
                } else clean,
                history = historyPairs,
                preferredModel = appPrefs.value.preferredAiModel,
                systemPrompt = systemPrompt,
                intent = if (isMathIntent) "math" else "chat"
            )
            _isAiThinking.value = false
            res.onSuccess { reply ->
                _aiMessages.value = _aiMessages.value + AiAssistantMessage(
                    isUser = false,
                    text = reply.reply,
                    model = reply.model,
                    attachedMath = localMath
                )
            }.onFailure { err ->
                if (localMath != null) {
                    val stepsReadable = localMath.steps.mapIndexed { idx, s ->
                        "${idx + 1}. $s"
                    }.joinToString("\n")
                    _aiMessages.value = _aiMessages.value + AiAssistantMessage(
                        isUser = false,
                        text = buildString {
                            appendLine("**1. Méthode et calcul**")
                            if (stepsReadable.isNotBlank()) appendLine(stepsReadable)
                            appendLine()
                            appendLine("**2. Résultat final**")
                            append("$$${localMath.latexFormula}$$")
                        }.trim(),
                        model = "Moteur mathématique BRAV",
                        attachedMath = localMath
                    )
                } else {
                    _aiMessages.value = _aiMessages.value + AiAssistantMessage(
                        isUser = false,
                        text = err.localizedMessage ?: "Impossible de joindre l'assistant IA."
                    )
                }
            }
        }
    }

    fun clearAiChat() {
        _aiMessages.value = emptyList()
    }

    fun runMathComputation(rawInput: String, topic: LatexMathTopic) {
        _mathAiExplanation.value = ""
        _mathResult.value = MathComputationEngine.compute(rawInput, topic)
    }

    fun explainMathWithCloudflareAi(rawInput: String, topic: LatexMathTopic) {
        val clean = rawInput.trim()
        if (clean.isBlank() || _isMathAiLoading.value) return
        val localRes = MathComputationEngine.compute(clean, topic)
        _mathResult.value = localRes
        val localSummary = if (localRes.isSuccess) {
            "${localRes.operationLabel} => ${localRes.primaryResult} (formule : $$${localRes.latexFormula}$$)"
        } else ""
        _isMathAiLoading.value = true
        viewModelScope.launch {
            val mathPreferredModel = appPrefs.value.preferredAiModel.takeIf { it.isNotBlank() }
                ?: "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b"
            val res = aiTranslationRepository.solveMathWithCloudflareAi(
                topicName = topic.displayName,
                expression = clean,
                localSummary = localSummary,
                preferredModel = mathPreferredModel
            )
            _isMathAiLoading.value = false
            res.onSuccess { reply ->
                _mathAiExplanation.value = reply.reply
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Erreur Assistant IA Maths"
            }
        }
    }

    fun saveFormulaRecord(
        title: String,
        result: MathComputationResult,
        aiExplanation: String = _mathAiExplanation.value
    ) {
        val existing = savedFormulas.value
        val record = FormulaRecord(
            title = title.trim().ifBlank { result.operationLabel },
            topicId = result.topic.name,
            verificationSource = result.verificationSource.name,
            inputExpression = result.rawInput,
            primaryResult = result.primaryResult,
            unicodeFormula = result.unicodeFormula,
            latexFormula = result.latexFormula,
            steps = result.steps,
            aiExplanation = aiExplanation
        )
        val updated = listOf(record) + existing
        viewModelScope.launch {
            prefsRepository.setSavedFormulasJson(DocumentExportManager.serializeSavedFormulas(updated))
            _snackbarMessage.value = "Formule enregistrée"
        }
    }

    fun deleteFormulaRecord(formulaId: String) {
        val updated = savedFormulas.value.filterNot { it.id == formulaId }
        viewModelScope.launch {
            prefsRepository.setSavedFormulasJson(DocumentExportManager.serializeSavedFormulas(updated))
            _snackbarMessage.value = "Formule supprimée"
        }
    }

    fun upsertAutoReplyContactRule(rule: AutoReplyContactRule) {
        val current = autoReplyRules.value.filterNot { it.contactKey.equals(rule.contactKey, ignoreCase = true) }
        val updated = listOf(rule) + current
        viewModelScope.launch {
            prefsRepository.setAiAutoReplyContactRulesJson(AiAutoReplyManager.serializeContactRules(updated))
            workerApiClient.upsertAutoReplyRule(
                AutoReplyRuleDto(
                    contactKey = rule.contactKey,
                    contactLabel = rule.contactLabel,
                    mode = rule.mode.name,
                    toneOverride = rule.toneOverride
                )
            )
            _snackbarMessage.value = "Règle enregistrée pour ${rule.contactLabel}"
        }
    }

    fun deleteAutoReplyContactRule(contactKey: String) {
        val updated = autoReplyRules.value.filterNot { it.contactKey.equals(contactKey, ignoreCase = true) }
        viewModelScope.launch {
            prefsRepository.setAiAutoReplyContactRulesJson(AiAutoReplyManager.serializeContactRules(updated))
            workerApiClient.deleteAutoReplyRule(contactKey)
        }
    }

    fun approvePendingAutoReply(pendingId: String, editedText: String? = null) {
        val pending = AiAutoReplyManager.removePendingReply(pendingId) ?: return
        val finalReply = (editedText ?: pending.suggestedReply).trim()
        if (finalReply.isBlank()) return

        viewModelScope.launch {
            if (pending.channel == "SMS") {
                val ctx = appContext ?: return@launch
                if (AiAutoReplyManager.sendSmsDirect(ctx, pending.target, finalReply)) {
                    AiAutoReplyManager.recordLog(
                        AutoReplyLogEntry(
                            channel = "SMS",
                            target = pending.target,
                            incomingPreview = pending.incomingPreview,
                            replyText = finalReply,
                            model = pending.model
                        )
                    )
                    _snackbarMessage.value = "SMS envoyé à ${pending.target}"
                }
            } else {
                val conv = conversations.value.firstOrNull { it.id == pending.conversationId } ?: return@launch
                val outText = if (finalReply.startsWith("[Auto-IA]")) finalReply else "[Auto-IA] $finalReply"
                val sendRes = chatRepository.sendTextMessage(
                    conversation = conv,
                    senderId = currentUserId,
                    plaintext = outText,
                    queueIfOffline = false
                )
                if (sendRes.isSuccess) {
                    AiAutoReplyManager.recordLog(
                        AutoReplyLogEntry(
                            channel = "BRAV Chat",
                            target = pending.senderName.ifBlank { pending.target },
                            incomingPreview = pending.incomingPreview,
                            replyText = outText,
                            model = pending.model
                        )
                    )
                    _snackbarMessage.value = "Réponse BRAV Chat envoyée"
                }
            }
        }
    }

    fun dismissPendingAutoReply(pendingId: String) {
        AiAutoReplyManager.removePendingReply(pendingId)
    }

    fun toggleConversationDirectAi(
        contactKey: String,
        contactLabel: String,
        globalEnabled: Boolean,
        candidates: List<String> = listOf(contactKey)
    ) {
        val prefs = appPrefs.value
        val currentlyActive = AiAutoReplyManager.isDirectDotActive(
            contactCandidates = candidates,
            rulesJson = prefs.aiAutoReplyContactRulesJson,
            globalAutoReplyEnabled = globalEnabled
        )
        val nextMode = if (currentlyActive) ContactRuleMode.NEVER else ContactRuleMode.AUTO
        val (_, tone) = AiAutoReplyManager.resolveRuleForContact(
            contactCandidates = candidates,
            rulesJson = prefs.aiAutoReplyContactRulesJson,
            globalValidationRequired = prefs.aiAutoReplyValidationRequired,
            defaultTone = prefs.aiAutoReplyTone
        )
        val rule = AutoReplyContactRule(
            contactKey = contactKey,
            contactLabel = contactLabel.ifBlank { contactKey },
            mode = nextMode,
            toneOverride = tone
        )
        val current = autoReplyRules.value.filterNot { r ->
            r.contactKey.equals(contactKey, ignoreCase = true) ||
                candidates.any { c -> c.isNotBlank() && r.contactKey.equals(c, ignoreCase = true) }
        }
        val updated = listOf(rule) + current
        viewModelScope.launch {
            prefsRepository.setAiAutoReplyContactRulesJson(AiAutoReplyManager.serializeContactRules(updated))
            workerApiClient.upsertAutoReplyRule(
                AutoReplyRuleDto(
                    contactKey = rule.contactKey,
                    contactLabel = rule.contactLabel,
                    mode = rule.mode.name,
                    toneOverride = rule.toneOverride
                )
            )
            _snackbarMessage.value = if (nextMode == ContactRuleMode.AUTO) {
                "IA directe activée • ${rule.contactLabel}"
            } else {
                "IA directe désactivée • ${rule.contactLabel}"
            }
        }
    }

    suspend fun generateDirectAiReply(
        channel: String,
        contactKey: String,
        contactLabel: String,
        incomingMessage: String,
        historyPairs: List<Pair<String, String>> = emptyList()
    ): Result<WorkerAiReply> {
        val prefs = appPrefs.value
        val (_, tone) = AiAutoReplyManager.resolveRuleForContact(
            contactCandidates = listOf(contactKey, contactLabel),
            rulesJson = prefs.aiAutoReplyContactRulesJson,
            globalValidationRequired = prefs.aiAutoReplyValidationRequired,
            defaultTone = prefs.aiAutoReplyTone
        )
        val promptInput = incomingMessage.trim()
            .ifBlank { "Rédige une réponse courte, naturelle et pertinente pour continuer cette conversation." }
        return aiTranslationRepository.generateAutoReply(
            channel = channel,
            senderName = contactLabel.ifBlank { "Contact" },
            incomingMessage = promptInput,
            instruction = prefs.aiAutoReplyInstruction,
            preferredModel = prefs.preferredAiModel,
            tone = tone,
            recentReplies = emptyList(),
            history = historyPairs
        )
    }

    private fun handleBravChatAutoReply(ev: RealtimeEvent.IncomingMessage) {
        val prefs = appPrefs.value
        val candidates = listOf(ev.senderId, ev.senderName, ev.conversationId)
        val explicitAuto = AiAutoReplyManager.hasExplicitAutoRule(candidates, prefs.aiAutoReplyContactRulesJson)
        if (!prefs.aiAutoReplyBravEnabled && !explicitAuto) return
        if (ev.messageType != "text") return
        if (!AiAutoReplyManager.shouldAutoReplyToBravMessage(
                messageId = ev.messageId,
                conversationId = ev.conversationId,
                senderId = ev.senderId,
                currentUserId = currentUserId,
                preview = ev.preview
            )
        ) return

        val (effectiveMode, effectiveTone) = AiAutoReplyManager.resolveRuleForContact(
            contactCandidates = listOf(ev.senderId, ev.senderName, ev.conversationId),
            rulesJson = prefs.aiAutoReplyContactRulesJson,
            globalValidationRequired = prefs.aiAutoReplyValidationRequired,
            defaultTone = prefs.aiAutoReplyTone
        )
        if (effectiveMode == ContactRuleMode.NEVER) return

        val conv = conversations.value.firstOrNull { it.id == ev.conversationId } ?: return
        val recentForTarget = AiAutoReplyManager.logs.value
            .filter { it.target == ev.senderName || it.target == ev.conversationId }
            .take(5)
            .map { it.replyText }

        viewModelScope.launch {
            val res = aiTranslationRepository.generateAutoReply(
                channel = "brav_chat",
                senderName = ev.senderName.ifBlank { "Contact" },
                incomingMessage = ev.preview,
                instruction = prefs.aiAutoReplyInstruction,
                preferredModel = prefs.preferredAiModel,
                tone = effectiveTone,
                recentReplies = recentForTarget
            )
            val reply = res.getOrNull() ?: return@launch
            if (AiAutoReplyManager.isRepetitiveReply(reply.reply, recentForTarget)) return@launch

            if (effectiveMode == ContactRuleMode.VALIDATE) {
                AiAutoReplyManager.enqueuePendingReply(
                    PendingAutoReply(
                        channel = "BRAV Chat",
                        target = ev.senderId,
                        conversationId = ev.conversationId,
                        senderName = ev.senderName.ifBlank { "Contact" },
                        incomingPreview = ev.preview.take(140),
                        suggestedReply = reply.reply,
                        model = reply.model
                    )
                )
                return@launch
            }

            val outText = "[Auto-IA] ${reply.reply}"
            val sendRes = chatRepository.sendTextMessage(
                conversation = conv,
                senderId = currentUserId,
                plaintext = outText,
                queueIfOffline = false
            )
            if (sendRes.isSuccess) {
                AiAutoReplyManager.recordLog(
                    AutoReplyLogEntry(
                        channel = "BRAV Chat",
                        target = ev.senderName.ifBlank { ev.conversationId },
                        incomingPreview = ev.preview.take(120),
                        replyText = outText,
                        model = reply.model
                    )
                )
            }
        }
    }

    fun blockUser(targetUid: String, reason: String = "") {
        viewModelScope.launch {
            val res = userRepository.blockUser(currentUserId, targetUid, reason)
            _snackbarMessage.value = if (res.isSuccess) {
                "Utilisateur bloqué"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Blocage impossible"
            }
        }
    }

    fun unblockUser(targetUid: String) {
        viewModelScope.launch {
            val res = userRepository.unblockUser(currentUserId, targetUid)
            _snackbarMessage.value = if (res.isSuccess) {
                "Utilisateur débloqué"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Déblocage impossible"
            }
        }
    }

    fun handleDeepLinkCall(callId: String?, action: String? = null) {
        if (callId.isNullOrBlank()) return
        viewModelScope.launch {
            val call = callRepository.getCall(callId) ?: return@launch
            when (action?.lowercase()) {
                "accept" -> if (call.status == "calling" && call.receiverId == currentUserId && _activeCall.value == null) {
                    acceptIncomingCall(call)
                }
                "reject" -> if (call.status == "calling" && call.receiverId == currentUserId) {
                    rejectIncomingCall(call)
                }
                else -> if (call.status == "calling" && call.receiverId == currentUserId && _activeCall.value == null) {
                    _realtimeIncomingCall.value = call
                }
            }
        }
    }

    fun selectWebRtcAudioRoute(route: CallAudioRoute) {
        webRtcCallManager.selectAudioRoute(route)
    }

    fun startOutgoingCall(peerUid: String, peerName: String, isVideo: Boolean) {
        val me = currentUser.value
        val myName = me?.displayName ?: "Membre BRAV"
        val prefs = appPrefs.value
        val procConfig = VoiceProcessingConfig(
            noiseSuppressionEnabled = prefs.noiseSuppressionEnabled,
            echoCancellationEnabled = prefs.echoCancellationEnabled,
            autoGainControlEnabled = prefs.autoGainControlEnabled,
            voicePriorityEnabled = prefs.voicePriorityEnabled
        )
        viewModelScope.launch {
            val knownPeer = savedContactUsers.value.firstOrNull { it.userId == peerUid }
                ?: userRepository.getUserById(peerUid)
            val res = callRepository.startCall(
                callerId = currentUserId,
                callerName = myName,
                receiverId = peerUid,
                receiverName = peerName,
                callType = if (isVideo) "video" else "audio",
                callerAvatar = me?.avatarUrl.orEmpty(),
                callerUsername = me?.username.orEmpty(),
                receiverAvatar = knownPeer?.avatarUrl.orEmpty(),
                receiverUsername = knownPeer?.username.orEmpty()
            )
            if (res.isSuccess) {
                val call = res.getOrThrow()
                _activeCall.value = call
                observeActiveCallRemotely(call.id)
                startRingTimeout(call.id)
                webRtcCallManager.startSession(
                    callId = call.id,
                    isCaller = true,
                    isVideo = isVideo,
                    myUid = currentUserId,
                    processingConfig = procConfig,
                    defaultSpeakerOn = if (isVideo) true else prefs.defaultSpeakerAudio,
                    startMuted = prefs.startCallMuted
                ) {
                    endActiveCall(missed = false)
                }
            } else {
                _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Impossible de lancer l'appel"
            }
        }
    }

    fun acceptIncomingCall(call: Call) {
        _realtimeIncomingCall.value = null
        appContext?.let { BravFirebaseMessagingService.cancelIncomingCallNotification(it, call.id) }
        val prefs = appPrefs.value
        val procConfig = VoiceProcessingConfig(
            noiseSuppressionEnabled = prefs.noiseSuppressionEnabled,
            echoCancellationEnabled = prefs.echoCancellationEnabled,
            autoGainControlEnabled = prefs.autoGainControlEnabled,
            voicePriorityEnabled = prefs.voicePriorityEnabled
        )
        val isVideo = call.callType == "video"
        viewModelScope.launch {
            callRepository.updateCallStatus(call.id, "accepted")
            _activeCall.value = call.copy(status = "accepted")
            observeActiveCallRemotely(call.id)
            webRtcCallManager.startSession(
                callId = call.id,
                isCaller = false,
                isVideo = isVideo,
                myUid = currentUserId,
                processingConfig = procConfig,
                defaultSpeakerOn = if (isVideo) true else prefs.defaultSpeakerAudio,
                startMuted = prefs.startCallMuted
            ) {
                endActiveCall(missed = false)
            }
        }
    }

    fun rejectIncomingCall(call: Call) {
        _realtimeIncomingCall.value = null
        appContext?.let { BravFirebaseMessagingService.cancelIncomingCallNotification(it, call.id) }
        viewModelScope.launch {
            callRepository.updateCallStatus(call.id, "rejected", missed = true)
        }
    }

    fun toggleCallHold() {
        val call = _activeCall.value ?: return
        val session = webRtcCallManager.session.value
        val nextHeld = !session.isOnHold
        webRtcCallManager.setHold(nextHeld)
        _activeCall.value = call.copy(
            status = if (nextHeld) "on_hold" else "accepted",
            heldByUid = if (nextHeld) currentUserId else ""
        )
        viewModelScope.launch {
            callRepository.setCallHold(call.id, nextHeld, currentUserId)
        }
    }

    fun endActiveCall(missed: Boolean = false) {
        activeCallObserverJob?.cancel()
        ringTimeoutJob?.cancel()
        val call = _activeCall.value
        val session = webRtcCallManager.session.value
        val duration = session.elapsedSeconds
        webRtcCallManager.endSession(sendHangup = true)
        _activeCall.value = null
        if (call != null) {
            val wasStillCalling = call.status == "calling"
            val isMissed = missed && wasStillCalling && duration == 0L
            val terminalLifecycle = when {
                isMissed -> CallLifecycleState.MISSED
                session.lifecycleState == CallLifecycleState.FAILED -> CallLifecycleState.FAILED
                session.lifecycleState == CallLifecycleState.CONNECTION_LOST -> CallLifecycleState.CONNECTION_LOST
                else -> CallLifecycleState.ENDED
            }
            showTerminalCallBanner(call, if (isMissed) "Aucune réponse" else terminalLifecycle.labelFr)
            viewModelScope.launch {
                callRepository.updateCallStatus(
                    callId = call.id,
                    status = "ended",
                    missed = isMissed,
                    durationSeconds = duration,
                    lifecycleState = terminalLifecycle
                )
            }
        }
    }

    private fun observeActiveCallRemotely(callId: String) {
        activeCallObserverJob?.cancel()
        activeCallObserverJob = viewModelScope.launch {
            callRepository.observeCall(callId)
                .catch { handleFlowError(it) }
                .collect { remote ->
                    if (remote == null) return@collect
                    when (remote.status) {
                        "accepted" -> {
                            ringTimeoutJob?.cancel()
                            if (_activeCall.value?.id == callId) {
                                _activeCall.value = remote
                            }
                            webRtcCallManager.setRemoteHold(false)
                            webRtcCallManager.notifyPeerAccepted()
                        }
                        "on_hold" -> {
                            if (_activeCall.value?.id == callId) {
                                _activeCall.value = remote
                            }
                            val remoteHeld = remote.heldByUid.isNotBlank() && remote.heldByUid != currentUserId
                            webRtcCallManager.setRemoteHold(remoteHeld)
                        }
                        "rejected", "ended" -> {
                            if (_activeCall.value?.id == callId) {
                                ringTimeoutJob?.cancel()
                                webRtcCallManager.endSession(sendHangup = false)
                                _activeCall.value = null
                                showTerminalCallBanner(
                                    remote,
                                    if (remote.status == "rejected") "Appel refusé" else "Appel terminé"
                                )
                            }
                            activeCallObserverJob?.cancel()
                        }
                    }
                }
        }
    }

    private fun startRingTimeout(callId: String) {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = viewModelScope.launch {
            delay(45_000L)
            val current = _activeCall.value
            if (current?.id == callId && current.status == "calling") {
                endActiveCall(missed = true)
            }
        }
    }

    private fun showTerminalCallBanner(call: Call, reason: String) {
        terminalBannerJob?.cancel()
        _terminalCallState.value = call to reason
        terminalBannerJob = viewModelScope.launch {
            delay(1800L)
            _terminalCallState.value = null
        }
    }

    fun dismissTerminalCallBanner() {
        terminalBannerJob?.cancel()
        _terminalCallState.value = null
    }

    fun publishLiveLocation(recipients: List<User>, lat: Double, lng: Double, acc: Float, durationMin: Int) {
        val me = currentUser.value ?: return
        viewModelScope.launch {
            val res = socialRepository.publishEncryptedLocation(me, recipients, lat, lng, acc, durationMin)
            _snackbarMessage.value = if (res.isSuccess) {
                "Position chiffrée partagée ($durationMin min)"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Erreur de partage GPS"
            }
        }
    }

    fun stopLiveLocation() {
        viewModelScope.launch {
            socialRepository.stopLocationSharing(currentUserId)
            _snackbarMessage.value = "Partage de position arrêté"
        }
    }

    fun decryptPeerLocation(share: LiveLocationShare): DecryptedCoordinates? {
        return socialRepository.decryptLocation(share, currentUserId)
    }

    fun notifyTyping(conversationId: String, peerUid: String, isTyping: Boolean) {
        if (conversationId.isBlank() || currentUserId.isBlank()) return
        if (peerUid.isNotBlank()) {
            realtimeClient.sendTyping(peerUid, conversationId, isTyping)
        }
        val now = System.currentTimeMillis()
        typingAutoClearJob?.cancel()
        if (isTyping) {
            lastTypingConversationId = conversationId
            val shouldWriteTrue = !lastFirestoreTypingValue || (now - lastFirestoreTypingWriteMs > 2500L)
            if (shouldWriteTrue) {
                lastFirestoreTypingValue = true
                lastFirestoreTypingWriteMs = now
                viewModelScope.launch {
                    chatRepository.setTypingStatus(conversationId, currentUserId, true)
                }
            }
            typingAutoClearJob = viewModelScope.launch {
                delay(4500L)
                lastFirestoreTypingValue = false
                if (peerUid.isNotBlank()) {
                    realtimeClient.sendTyping(peerUid, conversationId, false)
                }
                chatRepository.setTypingStatus(conversationId, currentUserId, false)
            }
        } else {
            val shouldWriteFalse = lastFirestoreTypingValue || lastTypingConversationId == conversationId
            lastFirestoreTypingValue = false
            lastFirestoreTypingWriteMs = 0L
            if (shouldWriteFalse) {
                viewModelScope.launch {
                    chatRepository.setTypingStatus(conversationId, currentUserId, false)
                }
            }
        }
    }

    fun deleteConversation(conversationId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val res = chatRepository.deleteConversation(conversationId, currentUserId)
            if (res.isSuccess) {
                _snackbarMessage.value = "Conversation supprimée"
                onDeleted()
            } else {
                _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Impossible de supprimer la conversation"
            }
        }
    }

    fun createFreeListing(
        title: String,
        category: String,
        description: String,
        city: String,
        price: String
    ) {
        val me = currentUser.value ?: return
        viewModelScope.launch {
            val res = socialRepository.createFreeOffer(me, title, category, description, city, price)
            _snackbarMessage.value = if (res.isSuccess) {
                "Annonce publiée gratuitement !"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Erreur lors de la publication"
            }
        }
    }

    suspend fun createOfferWithImages(
        offerType: String,
        title: String,
        category: String,
        description: String,
        condition: String,
        city: String,
        price: String,
        contactInfo: String,
        imagePayloads: List<Pair<ByteArray, String>>
    ): Result<String> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Profil en cours de chargement"))
        val res = socialRepository.createFreeOffer(
            user = me,
            title = title,
            category = category,
            description = description,
            locationCity = city,
            priceOrSalary = price,
            offerType = offerType,
            condition = condition,
            contactInfo = contactInfo,
            imagePayloads = imagePayloads
        )
        _snackbarMessage.value = if (res.isSuccess) {
            "Annonce publiée gratuitement !"
        } else {
            res.exceptionOrNull()?.localizedMessage ?: "Erreur lors de la publication"
        }
        return res
    }

    suspend fun updateOfferWithImages(
        offerId: String,
        offerType: String,
        title: String,
        category: String,
        description: String,
        condition: String,
        city: String,
        price: String,
        contactInfo: String,
        keptExistingUrls: List<String>,
        newImagePayloads: List<Pair<ByteArray, String>>,
        removedUrls: List<String>
    ): Result<Unit> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Profil en cours de chargement"))
        val res = socialRepository.updateOffer(
            offerId = offerId,
            user = me,
            offerType = offerType,
            title = title,
            category = category,
            description = description,
            condition = condition,
            locationCity = city,
            priceOrSalary = price,
            contactInfo = contactInfo,
            keptExistingUrls = keptExistingUrls,
            newImagePayloads = newImagePayloads,
            removedUrls = removedUrls
        )
        _snackbarMessage.value = if (res.isSuccess) {
            "Annonce mise à jour"
        } else {
            res.exceptionOrNull()?.localizedMessage ?: "Mise à jour impossible"
        }
        return res
    }

    fun deleteListing(offerId: String) {
        viewModelScope.launch {
            val res = socialRepository.deleteOffer(offerId)
            _snackbarMessage.value = if (res.isSuccess) "Annonce supprimée" else "Suppression impossible"
        }
    }

    fun deleteCallHistoryItem(callId: String) {
        viewModelScope.launch {
            val res = callRepository.deleteCallFromHistory(callId, currentUserId)
            _snackbarMessage.value = if (res.isSuccess) {
                "Appel retiré de l'historique"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Suppression impossible"
            }
        }
    }

    fun clearAllCallHistory() {
        val ids = callHistory.value.map { it.id }.filter { it.isNotBlank() }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val res = callRepository.clearCallHistory(currentUserId, ids)
            _snackbarMessage.value = if (res.isSuccess) {
                "Historique d'appels effacé"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Impossible d'effacer l'historique"
            }
        }
    }

    fun toggleStatusLike(status: StatusPost) {
        val actorName = currentUser.value?.displayName.orEmpty()
        viewModelScope.launch {
            val res = socialRepository.toggleStatusLike(status, currentUserId, actorName)
            if (res.isFailure) {
                _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Action impossible"
            }
        }
    }

    fun toggleStatusReaction(status: StatusPost, reactionKey: String) {
        val actorName = currentUser.value?.displayName.orEmpty()
        viewModelScope.launch {
            val res = socialRepository.toggleStatusReaction(status, currentUserId, reactionKey, actorName)
            if (res.isFailure) {
                _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Réaction impossible"
            }
        }
    }

    fun toggleStatusBookmark(status: StatusPost) {
        viewModelScope.launch {
            val res = socialRepository.toggleStatusBookmark(status, currentUserId)
            _snackbarMessage.value = res.fold(
                onSuccess = { added -> if (added) "Ajouté aux favoris" else "Retiré des favoris" },
                onFailure = { it.localizedMessage ?: "Action impossible" }
            )
        }
    }

    fun recordVideoView(status: StatusPost) {
        viewModelScope.launch {
            socialRepository.recordVideoView(status, currentUserId)
        }
    }

    fun recordStatusShare(status: StatusPost) {
        val actorName = currentUser.value?.displayName.orEmpty()
        viewModelScope.launch {
            socialRepository.recordStatusShare(status, currentUserId, actorName)
        }
    }

    fun setVideoAutoPlayMode(mode: VideoAutoPlayMode) {
        viewModelScope.launch {
            prefsRepository.setVideoAutoPlayMode(mode)
        }
    }

    fun setVideoSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setVideoSoundEnabled(enabled)
        }
    }

    fun reportStatusPost(status: StatusPost, reason: String, details: String = "") {
        viewModelScope.launch {
            val res = socialRepository.submitReport(
                reporterId = currentUserId,
                targetUid = status.userId,
                messageId = status.id,
                reason = reason,
                details = details
            )
            _snackbarMessage.value = if (res.isSuccess) {
                "Signalement transmis à l'équipe de modération"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Signalement impossible"
            }
        }
    }

    fun refreshActusFeed() {
        if (_isRefreshingActus.value) return
        viewModelScope.launch {
            _isRefreshingActus.value = true
            runCatching {
                delay(450L)
                userRepository.getUserById(currentUserId)
            }
            _isRefreshingActus.value = false
        }
    }

    suspend fun publishActusPost(
        text: String,
        mediaBytes: ByteArray? = null,
        mimeType: String = "image/jpeg",
        isVideo: Boolean = false,
        sourceVideoUri: Uri? = null,
        mediaItems: List<MediaUploadPayload> = emptyList(),
        backgroundColor: String = "#570CA2",
        audienceType: String = "all",
        allowedUids: List<String> = emptyList(),
        isEphemeral24h: Boolean = false,
        allowComments: Boolean = true,
        locationLabel: String = "",
        onUploadProgress: (Float) -> Unit = {}
    ): Result<String> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Profil en cours de chargement"))
        val effectiveMedia = mediaItems.ifEmpty {
            if (mediaBytes != null && mediaBytes.isNotEmpty()) {
                listOf(
                    MediaUploadPayload(
                        bytes = mediaBytes,
                        mimeType = mimeType,
                        isVideo = isVideo,
                        sourceVideoUri = sourceVideoUri
                    )
                )
            } else {
                emptyList()
            }
        }
        val res = if (effectiveMedia.isNotEmpty()) {
            socialRepository.createMultiMediaStatus(
                user = me,
                mediaItems = effectiveMedia,
                caption = text,
                audienceType = audienceType,
                allowedUids = allowedUids,
                isEphemeral24h = isEphemeral24h,
                context = appContext,
                allowComments = allowComments,
                locationLabel = locationLabel,
                onUploadProgress = onUploadProgress
            )
        } else {
            socialRepository.createTextStatus(
                user = me,
                text = text,
                backgroundColor = backgroundColor,
                audienceType = audienceType,
                allowedUids = allowedUids,
                isEphemeral24h = isEphemeral24h,
                allowComments = allowComments,
                locationLabel = locationLabel
            )
        }
        if (res.isSuccess) {
            _snackbarMessage.value = if (isEphemeral24h) "Statut publié pour 24h" else "Publication ajoutée au fil d'actualité"
        } else {
            _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Publication refusée"
        }
        return res
    }

    suspend fun forwardActusPostToConversation(
        post: StatusPost,
        conversation: Conversation,
        personalNote: String = ""
    ): Result<String> {
        val bodyText = post.effectiveText()
        val mediaLinks = post.resolvedMediaUrls()
        val compiled = buildString {
            appendLine("Publication Actus • ${post.userName.ifBlank { "Membre BRAV" }}")
            if (bodyText.isNotBlank()) appendLine(bodyText)
            if (mediaLinks.isNotEmpty()) {
                appendLine()
                append(mediaLinks.first())
            }
            if (personalNote.isNotBlank()) {
                appendLine()
                appendLine()
                append(personalNote.trim())
            }
        }.trim()

        val peerUid = if (!conversation.isGroup) {
            conversation.participants.firstOrNull { it != currentUserId }.orEmpty()
        } else ""
        val peerPub = if (peerUid.isNotBlank()) {
            conversation.participantPublicKeys[peerUid].orEmpty()
                .ifBlank { userRepository.getUserById(peerUid)?.publicKey.orEmpty() }
        } else ""

        val res = chatRepository.sendTextMessage(
            conversation = conversation,
            senderId = currentUserId,
            plaintext = compiled,
            peerPublicKey = peerPub,
            forwardedFrom = post.userName
        )
        if (res.isSuccess) {
            socialRepository.recordStatusShare(post, currentUserId, currentUser.value?.displayName.orEmpty())
            _snackbarMessage.value = "Publication partagée dans BRAV Chat"
        } else {
            _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Partage impossible"
        }
        return res
    }

    fun addStatusComment(
        statusId: String,
        text: String,
        reactionKey: String = "",
        parentCommentId: String = "",
        replyToAuthorName: String = "",
        onBlocked: (String) -> Unit = {},
        onAdded: () -> Unit = {}
    ) {
        val me = currentUser.value ?: return
        viewModelScope.launch {
            val res = socialRepository.addStatusComment(
                statusId = statusId,
                author = me,
                text = text,
                reactionKey = reactionKey,
                parentCommentId = parentCommentId,
                replyToAuthorName = replyToAuthorName
            )
            if (res.isSuccess) {
                onAdded()
            } else {
                val errMsg = res.exceptionOrNull()?.localizedMessage ?: "Commentaire impossible"
                onBlocked(errMsg)
                _snackbarMessage.value = errMsg
            }
        }
    }

    fun deleteStatusComment(statusId: String, commentId: String) {
        viewModelScope.launch {
            val res = socialRepository.deleteStatusComment(statusId, commentId)
            if (res.isFailure) {
                _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Suppression impossible"
            }
        }
    }

    fun updateStatusText(
        status: StatusPost,
        newText: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val isMedia = status.type != "text" || status.resolvedMediaUrls().isNotEmpty()
            val res = socialRepository.updateStatusText(status.id, newText, isMedia)
            _snackbarMessage.value = if (res.isSuccess) {
                onComplete(true)
                "Publication mise à jour"
            } else {
                onComplete(false)
                res.exceptionOrNull()?.localizedMessage ?: "Modification impossible"
            }
        }
    }

    fun removeProfilePhoto(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = userRepository.removeProfilePhoto(currentUserId)
            _snackbarMessage.value = if (res.isSuccess) {
                onComplete(true)
                "Photo de profil supprimée"
            } else {
                onComplete(false)
                res.exceptionOrNull()?.localizedMessage ?: "Suppression de photo impossible"
            }
        }
    }

    fun removeCoverPhoto(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = userRepository.removeCoverPhoto(currentUserId)
            _snackbarMessage.value = if (res.isSuccess) {
                onComplete(true)
                "Photo de couverture supprimée"
            } else {
                onComplete(false)
                res.exceptionOrNull()?.localizedMessage ?: "Suppression de la couverture impossible"
            }
        }
    }

    fun followRelationStateFor(targetUid: String): FollowRelationState {
        if (targetUid.isBlank()) return FollowRelationState.NOT_FOLLOWING
        if (targetUid == currentUserId) return FollowRelationState.SELF
        if (targetUid in followingUserIds.value) return FollowRelationState.FOLLOWING
        if (outgoingFollowRequests.value.any { it.toUid == targetUid && it.status == "pending" }) {
            return FollowRelationState.REQUESTED
        }
        return FollowRelationState.NOT_FOLLOWING
    }

    fun toggleFollowUser(targetUser: User) {
        if (targetUser.userId == currentUserId || targetUser.userId.isBlank()) return
        val me = currentUser.value
        val relation = followRelationStateFor(targetUser.userId)
        viewModelScope.launch {
            when (relation) {
                FollowRelationState.FOLLOWING -> {
                    val res = userRepository.unfollowUser(currentUserId, targetUser.userId)
                    if (res.isSuccess) {
                        _snackbarMessage.value = "Vous ne suivez plus ${targetUser.displayName}"
                        refreshCertificationEligibility()
                    } else {
                        _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Action impossible"
                    }
                }
                FollowRelationState.REQUESTED -> {
                    val res = userRepository.cancelOutgoingFollowRequest(currentUserId, targetUser.userId)
                    _snackbarMessage.value = if (res.isSuccess) {
                        "Demande d'abonnement annulée"
                    } else {
                        res.exceptionOrNull()?.localizedMessage ?: "Annulation impossible"
                    }
                }
                FollowRelationState.NOT_FOLLOWING -> {
                    val res = if (me != null) {
                        userRepository.followOrRequestUser(me, targetUser)
                    } else {
                        userRepository.followUser(currentUserId, targetUser.userId).map { FollowRelationState.FOLLOWING }
                    }
                    if (res.isSuccess) {
                        _snackbarMessage.value = when (res.getOrThrow()) {
                            FollowRelationState.REQUESTED -> "Demande d'abonnement envoyée à ${targetUser.displayName}"
                            else -> "Vous suivez désormais ${targetUser.displayName}"
                        }
                        refreshCertificationEligibility()
                    } else {
                        _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Action impossible"
                    }
                }
                FollowRelationState.SELF -> Unit
            }
        }
    }

    fun acceptFollowRequest(request: FollowRequest) {
        val me = currentUser.value ?: User(userId = currentUserId, displayName = "Membre BRAV")
        viewModelScope.launch {
            val res = userRepository.acceptFollowRequest(request, me)
            _snackbarMessage.value = if (res.isSuccess) {
                "${request.fromName.ifBlank { "Ce membre" }} est désormais abonné"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Validation impossible"
            }
        }
    }

    fun declineFollowRequest(request: FollowRequest) {
        viewModelScope.launch {
            val res = userRepository.declineFollowRequest(request, currentUserId)
            _snackbarMessage.value = if (res.isSuccess) {
                "Demande refusée"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Refus impossible"
            }
        }
    }

    fun cancelOutgoingFollowRequest(targetUid: String) {
        viewModelScope.launch {
            val res = userRepository.cancelOutgoingFollowRequest(currentUserId, targetUid)
            _snackbarMessage.value = if (res.isSuccess) {
                "Demande d'abonnement annulée"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Annulation impossible"
            }
        }
    }

    fun removeFollower(followerUid: String, displayName: String = "") {
        viewModelScope.launch {
            val res = userRepository.removeFollower(currentUserId, followerUid)
            _snackbarMessage.value = if (res.isSuccess) {
                if (displayName.isNotBlank()) "$displayName retiré de vos abonnés" else "Abonné retiré"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Suppression impossible"
            }
        }
    }

    fun setPrivateProfileEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val res = userRepository.updatePrivacyAndLookupFlags(currentUserId, isPrivateProfile = enabled)
            _snackbarMessage.value = if (res.isSuccess) {
                if (enabled) "Profil privé activé — validation requise pour s'abonner"
                else "Profil public activé"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Mise à jour impossible"
            }
        }
    }

    fun setAllowPhoneLookup(enabled: Boolean) {
        viewModelScope.launch {
            val res = userRepository.updatePrivacyAndLookupFlags(currentUserId, allowPhoneLookup = enabled)
            _snackbarMessage.value = if (res.isSuccess) {
                if (enabled) "Identification par numéro autorisée"
                else "Identification par numéro désactivée"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Mise à jour impossible"
            }
        }
    }

    suspend fun createOrGetPersonalInvite(channel: String = "share"): Result<InviteLink> {
        val me = currentUser.value ?: User(userId = currentUserId, displayName = "Membre BRAV")
        return userRepository.createOrGetPersonalInvite(me, channel)
    }

    fun trackInviteUse(code: String, isSignup: Boolean = false) {
        viewModelScope.launch {
            userRepository.trackInviteUse(code, isSignup)
        }
    }

    fun setActiveConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
        if (!conversationId.isNullOrBlank()) {
            viewModelScope.launch {
                chatRepository.markConversationAsRead(currentUserId, conversationId)
                notificationRepository.markByRefIdRead(currentUserId, conversationId)
                val matchingKeys = notifications.value
                    .filter { it.refId == conversationId }
                    .flatMap { listOfNotNull(it.id.takeIf(String::isNotBlank), it.dedupKey.takeIf(String::isNotBlank)) }
                if (matchingKeys.isNotEmpty()) {
                    _locallyReadNotifIds.value = _locallyReadNotifIds.value + matchingKeys
                }
            }
        }
    }

    fun markMessagesAsRead(conversationId: String, messages: List<Message>) {
        if (conversationId.isBlank()) return
        val latestMsgMs = messages.maxOfOrNull { msg ->
            msg.timestamp?.let { it.seconds * 1000L + it.nanoseconds / 1_000_000L } ?: 0L
        } ?: System.currentTimeMillis()
        viewModelScope.launch {
            chatRepository.markMessagesAsRead(conversationId, messages, currentUserId)
            chatRepository.markConversationAsRead(
                myUid = currentUserId,
                conversationId = conversationId,
                latestTimestampMs = maxOf(latestMsgMs, System.currentTimeMillis())
            )
            notificationRepository.markByRefIdRead(currentUserId, conversationId)
            val matchingKeys = notifications.value
                .filter { it.refId == conversationId }
                .flatMap { listOfNotNull(it.id.takeIf(String::isNotBlank), it.dedupKey.takeIf(String::isNotBlank)) }
            if (matchingKeys.isNotEmpty()) {
                _locallyReadNotifIds.value = _locallyReadNotifIds.value + matchingKeys
            }
        }
    }

    fun refreshNotifications(category: String = "") {
        viewModelScope.launch {
            val fetched = notificationRepository.fetchFromWorker(category = category, limit = 80)
            if (fetched.isNotEmpty()) {
                _workerNotifications.value = fetched
            }
        }
    }

    fun markNotificationRead(notification: AppNotification) {
        val keys = listOfNotNull(
            notification.id.takeIf(String::isNotBlank),
            notification.dedupKey.takeIf(String::isNotBlank)
        )
        if (keys.isNotEmpty()) {
            _locallyReadNotifIds.value = _locallyReadNotifIds.value + keys
        }
        viewModelScope.launch {
            notificationRepository.markAsRead(
                uid = currentUserId,
                notifId = notification.id,
                refId = notification.refId
            )
        }
    }

    fun markAllNotificationsRead(category: NotificationCategory = NotificationCategory.ALL) {
        val keys = notifications.value
            .filter { category == NotificationCategory.ALL || it.categoryEnum == category }
            .flatMap { listOfNotNull(it.id.takeIf(String::isNotBlank), it.dedupKey.takeIf(String::isNotBlank)) }
            .toSet()
        if (keys.isNotEmpty()) {
            _locallyReadNotifIds.value = _locallyReadNotifIds.value + keys
        }
        viewModelScope.launch {
            notificationRepository.markAllAsRead(currentUserId, category)
        }
    }

    fun deleteNotification(notificationId: String) {
        if (notificationId.isBlank()) return
        _locallyDeletedNotifIds.value = _locallyDeletedNotifIds.value + notificationId
        _workerNotifications.value = _workerNotifications.value.filterNot { it.id == notificationId }
        viewModelScope.launch {
            notificationRepository.deleteNotification(currentUserId, notificationId)
        }
    }

    fun updateNotificationSettings(
        master: Boolean? = null,
        messages: Boolean? = null,
        calls: Boolean? = null,
        missedCalls: Boolean? = null,
        groups: Boolean? = null,
        statuses: Boolean? = null,
        contacts: Boolean? = null,
        actusPosts: Boolean? = null,
        actusLikesComments: Boolean? = null,
        offersJobs: Boolean? = null,
        sound: Boolean? = null,
        vibration: Boolean? = null,
        hideContent: Boolean? = null,
        quietHoursEnabled: Boolean? = null,
        quietHoursStartHour: Int? = null,
        quietHoursEndHour: Int? = null
    ) {
        viewModelScope.launch {
            prefsRepository.setNotificationSettings(
                master = master,
                messages = messages,
                calls = calls,
                missedCalls = missedCalls,
                groups = groups,
                statuses = statuses,
                contacts = contacts,
                actusPosts = actusPosts,
                actusLikesComments = actusLikesComments,
                offersJobs = offersJobs,
                sound = sound,
                vibration = vibration,
                hideContent = hideContent,
                quietHoursEnabled = quietHoursEnabled,
                quietHoursStartHour = quietHoursStartHour,
                quietHoursEndHour = quietHoursEndHour
            )
        }
    }

    fun refreshCertificationEligibility() {
        viewModelScope.launch {
            val fbUser = runCatching { Firebase.auth.currentUser }.getOrNull()
            if (fbUser != null &&
                userRepository.currentUserMatchesDesignerEmail() &&
                !fbUser.isEmailVerified
            ) {
                runCatching { fbUser.reload().await() }
            }
            val rawMe = rawCurrentUser.value ?: User(userId = currentUserId)
            val elig = userRepository.checkCertificationEligibility(rawMe)
            if (_certificationEligibility.value != elig) {
                _certificationEligibility.value = elig
            }
            if (elig.exemptDesigner) {
                cachedServerDesignerExempt = true
                syncDesignerCertificationIfNeeded(rawMe)
            }
        }
    }

    fun submitLiveSelfieForCertification(
        category: VerificationCategory,
        organizationName: String,
        roleOrActivity: String,
        country: String,
        payload: BravHumanVerificationService.AnalyzedSelfiePayload,
        termsAccepted: Boolean,
        selfieConsent: Boolean,
        onComplete: (VerificationRequest?, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isCertificationBusy.value = true
            val activeUser = currentUser.value ?: User(userId = currentUserId)
            val res = userRepository.submitLiveSelfieVerification(
                user = activeUser,
                category = category,
                organizationName = organizationName,
                roleOrActivity = roleOrActivity,
                country = country,
                payload = payload,
                termsAccepted = termsAccepted,
                selfieConsent = selfieConsent
            )
            _isCertificationBusy.value = false
            if (res.isSuccess) {
                val created = res.getOrNull()
                _snackbarMessage.value = "Selfie vérifié — procédez au paiement de 3 600 F CFA (5,99 USD)"
                refreshCertificationEligibility()
                onComplete(created, null)
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "Validation du selfie impossible"
                _snackbarMessage.value = err
                onComplete(null, err)
            }
        }
    }

    suspend fun initiateCertificationPayment(
        idempotencyKey: String,
        currency: String = "XOF",
        provider: String = "brav_mobile_money"
    ): Result<WorkerPaymentInitiateResult> {
        return userRepository.initiateCertificationPayment(
            idempotencyKey = idempotencyKey,
            currency = currency,
            provider = provider
        )
    }

    fun confirmCertificationPayment(
        existingRequest: VerificationRequest?,
        txRef: String,
        outcome: String = "confirmed",
        currency: String = "XOF",
        onComplete: (CertificationPaymentReceipt?, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isCertificationBusy.value = true
            val activeUser = currentUser.value ?: User(userId = currentUserId)
            val res = userRepository.confirmCertificationPaymentAndGrantBadge(
                user = activeUser,
                existingRequest = existingRequest,
                txRef = txRef,
                outcome = outcome,
                currency = currency
            )
            _isCertificationBusy.value = false
            if (res.isSuccess) {
                val receipt = res.getOrThrow()
                _lastCertificationReceipt.value = receipt
                _snackbarMessage.value = "CERTIFICATION activée ! Reçu ${receipt.receiptNumber}"
                appContext?.let { ctx ->
                    BravFirebaseMessagingService.showCertificationDecisionNotification(
                        context = ctx,
                        status = VerificationStatus.VERIFIED,
                        caseNumber = receipt.caseNumber,
                        note = "Reçu ${receipt.receiptNumber} · 3 600 F CFA (5,99 USD)"
                    )
                }
                refreshCertificationEligibility()
                onComplete(receipt, null)
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "Paiement refusé ou annulé"
                _snackbarMessage.value = err
                onComplete(null, err)
            }
        }
    }

    fun cancelPendingVerificationRequest(requestId: String) {
        viewModelScope.launch {
            val res = userRepository.cancelPendingVerificationRequest(requestId)
            _snackbarMessage.value = if (res.isSuccess) {
                refreshCertificationEligibility()
                "Demande annulée"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Annulation impossible"
            }
        }
    }

    fun adminReviewVerificationRequest(
        request: VerificationRequest,
        newStatus: VerificationStatus,
        reviewerNote: String = ""
    ) {
        viewModelScope.launch {
            val res = userRepository.adminReviewVerificationRequest(
                request = request,
                newStatus = newStatus,
                reviewerUid = currentUserId,
                reviewerNote = reviewerNote
            )
            _snackbarMessage.value = if (res.isSuccess) {
                when (newStatus) {
                    VerificationStatus.VERIFIED -> "Badge bleu attribué à ${request.fullName}"
                    VerificationStatus.IN_REVIEW -> "Dossier placé en examen"
                    VerificationStatus.RETAKE_REQUIRED -> "Nouvelle capture selfie demandée"
                    VerificationStatus.REJECTED -> "Demande refusée"
                    VerificationStatus.SUSPENDED -> "Certification suspendue"
                    VerificationStatus.REVOKED -> "Certification révoquée"
                    VerificationStatus.EXPIRED -> "Certification expirée"
                    else -> "Statut mis à jour"
                }
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Action impossible"
            }
        }
    }

    fun submitCallerReport(
        rawNumber: String,
        category: CallerReportCategory,
        label: String,
        details: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = socialRepository.submitCallerReport(
                reporterId = currentUserId,
                rawNumber = rawNumber,
                category = category,
                label = label,
                details = details,
                defaultCountryIso = appPrefs.value.countryIso.ifBlank { "CI" }
            )
            if (res.isSuccess) {
                _snackbarMessage.value = "Signalement enregistré pour modération"
                onComplete(true)
            } else {
                _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Signalement impossible"
                onComplete(false)
            }
        }
    }

    fun adminModerateCallerReport(
        reportId: String,
        status: String,
        category: CallerReportCategory? = null,
        label: String? = null
    ) {
        viewModelScope.launch {
            val res = socialRepository.adminModerateCallerReport(reportId, status, category, label)
            _snackbarMessage.value = if (res.isSuccess) {
                "Signalement BRAV Call ID mis à jour"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Modération impossible"
            }
        }
    }

    fun addActusSearchHistory(query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        val updated = (listOf(clean) + actusSearchHistory.value.filterNot { it.equals(clean, ignoreCase = true) })
            .take(10)
        viewModelScope.launch {
            prefsRepository.setActusSearchHistoryJson(serializeStringListJson(updated))
        }
    }

    fun removeActusSearchHistoryItem(query: String) {
        val updated = actusSearchHistory.value.filterNot { it == query }
        viewModelScope.launch {
            prefsRepository.setActusSearchHistoryJson(serializeStringListJson(updated))
        }
    }

    fun clearActusSearchHistory() {
        viewModelScope.launch {
            prefsRepository.setActusSearchHistoryJson("[]")
        }
    }

    fun setCallerIdEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setCallerIdEnabled(enabled) }
    }

    fun setCallerIdSpamWarningsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setCallerIdSpamWarningsEnabled(enabled) }
    }

    fun clearCallerIdPrivacyCache() {
        viewModelScope.launch {
            prefsRepository.clearCallerIdPrivacyData()
            _snackbarMessage.value = "Cache BRAV Call ID local effacé"
        }
    }

    private fun parseStringListJson(raw: String): List<String> {
        if (raw.isBlank() || raw == "[]") return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotBlank()) add(item)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeStringListJson(items: List<String>): String {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        return arr.toString()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepository.setThemeMode(mode) }
    }

    fun uploadProgressValue(value: Float?) {
        _uploadProgress.value = value
    }

    private fun handleFlowError(err: Throwable) {
        val ffe = generateSequence(err) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (ffe == null && err is CancellationException) throw err
    }

    override fun onCleared() {
        super.onCleared()
        voicePlayer.release()
        webRtcCallManager.endSession(sendHangup = false)
    }
}
