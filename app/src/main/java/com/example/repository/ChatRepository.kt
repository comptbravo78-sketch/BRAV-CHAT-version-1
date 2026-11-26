package com.example.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.crypto.E2eeCryptoManager
import com.example.local.BravLocalDatabase
import com.example.local.OfflineMessageEntity
import com.example.model.Conversation
import com.example.model.ConversationPref
import com.example.model.Message
import com.example.model.NotificationCategory
import com.example.model.User
import com.example.network.MediaUploadResponse
import com.example.network.WorkerApiClient
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID

class ChatRepository(
    private val db: FirebaseFirestore,
    private val workerApiClient: WorkerApiClient? = null,
    private val cryptoManager: E2eeCryptoManager? = null,
    private val localDb: BravLocalDatabase? = null,
    private val notificationRepository: NotificationRepository? = null
) {
    constructor(
        context: Context,
        workerApiClient: WorkerApiClient? = null
    ) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        workerApiClient = workerApiClient,
        cryptoManager = E2eeCryptoManager(context.applicationContext),
        localDb = BravLocalDatabase.getInstance(context.applicationContext)
    )

    fun observeConversations(userId: String): Flow<List<Conversation>> {
        return db.collection("conversations")
            .whereArrayContains("participants", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastMessageTimestamp ?: it.updatedAt ?: Timestamp(0, 0) }
            }
    }

    fun observeConversation(conversationId: String): Flow<Conversation?> {
        return db.collection("conversations").document(conversationId)
            .snapshots()
            .map { snap ->
                snap.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    fun observeConversationPrefs(userId: String): Flow<Map<String, ConversationPref>> {
        return db.collection("users").document(userId)
            .collection("conversation_prefs")
            .snapshots()
            .map { snap ->
                snap.toObjects(ConversationPref::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .associateBy { it.conversationId.ifBlank { it.id } }
            }
    }

    fun observeMessages(conversationId: String): Flow<List<Message>> {
        return db.collection("conversations").document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap ->
                snap.toObjects(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    fun observeUnreadIncomingCountForConversation(
        conversationId: String,
        myUid: String,
        lastReadAtMs: Long
    ): Flow<Int> {
        if (conversationId.isBlank() || myUid.isBlank()) return kotlinx.coroutines.flow.flowOf(0)
        return db.collection("conversations").document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(25)
            .snapshots()
            .map { snap ->
                snap.toObjects(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .count { msg ->
                        val msgTsMs = msg.timestamp?.let {
                            it.seconds * 1000L + it.nanoseconds / 1_000_000L
                        } ?: 0L
                        msg.senderId.isNotBlank() &&
                            msg.senderId != myUid &&
                            myUid !in msg.deletedForUsers &&
                            msg.status != "read" &&
                            (lastReadAtMs <= 0L || msgTsMs > lastReadAtMs)
                    }
            }
    }

    fun observeOfflinePending(conversationId: String): Flow<List<OfflineMessageEntity>> {
        val dao = localDb?.offlineQueueDao() ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return dao.observePendingForConversation(conversationId)
    }

    fun directConversationId(uidA: String, uidB: String): String {
        val sorted = listOf(uidA, uidB).sorted()
        return "conv_${sorted[0]}_${sorted[1]}"
    }

    suspend fun getOrCreateDirectConversation(currentUser: User, peerUser: User): Result<Conversation> {
        val convId = directConversationId(currentUser.userId, peerUser.userId)
        val convRef = db.collection("conversations").document(convId)
        return try {
            val snap = convRef.get().await()
            val existing = snap.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (snap.exists() && existing != null) {
                val updates = mutableMapOf<String, Any>()
                if (peerUser.publicKey.isNotBlank() && existing.participantPublicKeys[peerUser.userId] != peerUser.publicKey) {
                    updates["participantPublicKeys.${peerUser.userId}"] = peerUser.publicKey
                }
                if (currentUser.publicKey.isNotBlank() && existing.participantPublicKeys[currentUser.userId] != currentUser.publicKey) {
                    updates["participantPublicKeys.${currentUser.userId}"] = currentUser.publicKey
                }
                if (updates.isNotEmpty()) {
                    updates["updatedAt"] = FieldValue.serverTimestamp()
                    convRef.update(updates).await()
                }
                return Result.success(existing)
            }

            val participants = listOf(currentUser.userId, peerUser.userId).sorted()
            val payload = hashMapOf<String, Any>(
                "participants" to participants,
                "isGroup" to false,
                "groupName" to "",
                "groupDescription" to "",
                "groupAvatarUrl" to "",
                "ownerId" to "",
                "adminIds" to emptyList<String>(),
                "inviteCode" to "",
                "onlyAdminsCanMessage" to false,
                "onlyAdminsCanEditInfo" to true,
                "participantNames" to mapOf(
                    currentUser.userId to currentUser.displayName,
                    peerUser.userId to peerUser.displayName
                ),
                "participantAvatars" to mapOf(
                    currentUser.userId to currentUser.avatarUrl,
                    peerUser.userId to peerUser.avatarUrl
                ),
                "participantPublicKeys" to buildMap {
                    if (currentUser.publicKey.isNotBlank()) put(currentUser.userId, currentUser.publicKey)
                    if (peerUser.publicKey.isNotBlank()) put(peerUser.userId, peerUser.publicKey)
                },
                "wrappedGroupKeys" to emptyMap<String, String>(),
                "lastMessage" to "",
                "lastMessageSenderId" to "",
                "lastMessageType" to "text",
                "pinnedMessageId" to "",
                "pinnedMessagePreview" to "",
                "typingUsers" to emptyList<String>(),
                "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            convRef.set(payload).await()
            val created = convRef.get().await()
                .toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: Conversation(id = convId, participants = participants)
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, convRef.path)
            Result.failure(e)
        }
    }

    suspend fun createGroupConversation(
        owner: User,
        members: List<User>,
        groupName: String,
        groupDescription: String
    ): Result<Conversation> {
        val convId = "grp_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val convRef = db.collection("conversations").document(convId)
        val inviteCode = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        val allUsers = (listOf(owner) + members).distinctBy { it.userId }.take(50)
        if (allUsers.size < 2) {
            return Result.failure(IllegalArgumentException("Sélectionnez au moins un membre pour créer un groupe."))
        }
        return try {
            val payload = hashMapOf<String, Any>(
                "participants" to allUsers.map { it.userId },
                "isGroup" to true,
                "groupName" to groupName.trim().take(120),
                "groupDescription" to groupDescription.trim().take(500),
                "groupAvatarUrl" to "",
                "ownerId" to owner.userId,
                "adminIds" to listOf(owner.userId),
                "inviteCode" to inviteCode,
                "onlyAdminsCanMessage" to false,
                "onlyAdminsCanEditInfo" to true,
                "participantNames" to allUsers.associate { it.userId to it.displayName },
                "participantAvatars" to allUsers.associate { it.userId to it.avatarUrl },
                "participantPublicKeys" to allUsers.filter { it.publicKey.isNotBlank() }.associate { it.userId to it.publicKey },
                "wrappedGroupKeys" to emptyMap<String, String>(),
                "lastMessage" to "Groupe créé par ${owner.displayName}",
                "lastMessageSenderId" to owner.userId,
                "lastMessageType" to "text",
                "pinnedMessageId" to "",
                "pinnedMessagePreview" to "",
                "typingUsers" to emptyList<String>(),
                "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            convRef.set(payload).await()
            val created = convRef.get().await()
                .toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: Conversation(id = convId, isGroup = true, groupName = groupName)
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, convRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinGroupByInviteCode(code: String, user: User): Result<Conversation> {
        val clean = code.trim().uppercase()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Code d'invitation vide"))
        return try {
            val snap = db.collection("conversations")
                .whereEqualTo("inviteCode", clean)
                .limit(1)
                .get()
                .await()
            val doc = snap.documents.firstOrNull()
                ?: return Result.failure(IllegalStateException("Aucun groupe trouvé avec ce code."))
            val conv = doc.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Groupe invalide."))

            if (user.userId !in conv.participants) {
                val updatedParticipants = conv.participants + user.userId
                val updatedNames = conv.participantNames + (user.userId to user.displayName)
                val updatedAvatars = conv.participantAvatars + (user.userId to user.avatarUrl)
                doc.reference.update(
                    mapOf(
                        "participants" to updatedParticipants,
                        "participantNames" to updatedNames,
                        "participantAvatars" to updatedAvatars,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(conv)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations")
            Result.failure(e)
        }
    }

    suspend fun updateGroupSettings(
        conversationId: String,
        groupName: String? = null,
        groupDescription: String? = null,
        onlyAdminsCanMessage: Boolean? = null,
        onlyAdminsCanEditInfo: Boolean? = null,
        addMember: User? = null,
        removeMemberUid: String? = null,
        toggleAdminUid: String? = null
    ): Result<Unit> {
        val ref = db.collection("conversations").document(conversationId)
        return try {
            val conv = ref.get().await().toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Groupe introuvable"))
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            groupName?.let { updates["groupName"] = it.trim().take(120) }
            groupDescription?.let { updates["groupDescription"] = it.trim().take(500) }
            onlyAdminsCanMessage?.let { updates["onlyAdminsCanMessage"] = it }
            onlyAdminsCanEditInfo?.let { updates["onlyAdminsCanEditInfo"] = it }
            if (addMember != null && addMember.userId !in conv.participants) {
                updates["participants"] = conv.participants + addMember.userId
                updates["participantNames"] = conv.participantNames + (addMember.userId to addMember.displayName)
                updates["participantAvatars"] = conv.participantAvatars + (addMember.userId to addMember.avatarUrl)
            }
            if (removeMemberUid != null && removeMemberUid != conv.ownerId) {
                updates["participants"] = conv.participants.filter { it != removeMemberUid }
                updates["adminIds"] = conv.adminIds.filter { it != removeMemberUid }
            }
            if (toggleAdminUid != null && toggleAdminUid != conv.ownerId) {
                updates["adminIds"] = if (toggleAdminUid in conv.adminIds) {
                    conv.adminIds.filter { it != toggleAdminUid }
                } else {
                    conv.adminIds + toggleAdminUid
                }
            }
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun sendTextMessage(
        conversation: Conversation,
        senderId: String,
        plaintext: String,
        peerPublicKey: String = "",
        replyTo: Message? = null,
        forwardedFrom: String = "",
        queueIfOffline: Boolean = true
    ): Result<String> {
        val clean = plaintext.trim()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Message vide"))

        val receiverId = if (conversation.isGroup) {
            conversation.id
        } else {
            conversation.participants.firstOrNull { it != senderId } ?: senderId
        }

        val effectivePeerPub = if (!conversation.isGroup) {
            peerPublicKey.ifBlank { conversation.participantPublicKeys[receiverId].orEmpty() }
        } else ""

        return try {
            sendMessageInternal(
                conversationId = conversation.id,
                senderId = senderId,
                receiverId = receiverId,
                plaintext = clean,
                messageType = "text",
                peerPublicKey = effectivePeerPub,
                replyToMessageId = replyTo?.id.orEmpty(),
                replyToPreview = replyTo?.let { previewForReply(it) }.orEmpty(),
                forwardedFrom = forwardedFrom
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (queueIfOffline && localDb != null) {
                val localId = "offline_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                localDb.offlineQueueDao().enqueue(
                    OfflineMessageEntity(
                        localId = localId,
                        conversationId = conversation.id,
                        senderId = senderId,
                        receiverId = receiverId,
                        plaintext = clean,
                        messageType = "text",
                        replyToMessageId = replyTo?.id.orEmpty(),
                        replyToPreview = replyTo?.let { previewForReply(it) }.orEmpty(),
                        forwardedFrom = forwardedFrom,
                        peerPublicKey = effectivePeerPub,
                        isGroup = conversation.isGroup
                    )
                )
                return Result.success(localId)
            }
            Result.failure(e)
        }
    }

    suspend fun flushOfflineOutbox(): Int {
        val dao = localDb?.offlineQueueDao() ?: return 0
        val pending = dao.getAllPending()
        var flushed = 0
        for (item in pending) {
            val res = runCatching {
                sendMessageInternal(
                    conversationId = item.conversationId,
                    senderId = item.senderId,
                    receiverId = item.receiverId,
                    plaintext = item.plaintext,
                    messageType = item.messageType,
                    peerPublicKey = item.peerPublicKey,
                    replyToMessageId = item.replyToMessageId,
                    replyToPreview = item.replyToPreview,
                    forwardedFrom = item.forwardedFrom
                )
            }
            if (res.isSuccess) {
                dao.deleteById(item.localId)
                flushed++
            }
        }
        return flushed
    }

    private suspend fun sendMessageInternal(
        conversationId: String,
        senderId: String,
        receiverId: String,
        plaintext: String,
        messageType: String,
        peerPublicKey: String = "",
        mediaUrl: String = "",
        mediaKey: String = "",
        mediaMimeType: String = "",
        mediaName: String = "",
        mediaSize: Long = 0L,
        durationMs: Long = 0L,
        waveform: List<Int> = emptyList(),
        replyToMessageId: String = "",
        replyToPreview: String = "",
        forwardedFrom: String = "",
        stickerId: String = "",
        stickerPackId: String = "",
        stickerLabel: String = ""
    ): Result<String> {
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val convRef = db.collection("conversations").document(conversationId)
        val msgRef = convRef.collection("messages").document(msgId)

        var storedContent = plaintext
        var isEncrypted = false
        var encryptionIv = ""
        var senderPub = ""

        if (peerPublicKey.isNotBlank() && cryptoManager != null && messageType == "text") {
            val encrypted = cryptoManager.encryptForPeer(senderId, peerPublicKey, conversationId, plaintext)
            storedContent = encrypted.ciphertextBase64
            encryptionIv = encrypted.ivBase64
            senderPub = encrypted.senderPublicKeyBase64
            isEncrypted = true
        }

        val payload = hashMapOf<String, Any>(
            "conversationId" to conversationId,
            "senderId" to senderId,
            "receiverId" to receiverId,
            "content" to storedContent.take(15000),
            "messageType" to messageType,
            "isEncrypted" to isEncrypted,
            "encryptionIv" to encryptionIv,
            "senderPublicKey" to senderPub,
            "mediaUrl" to mediaUrl,
            "mediaKey" to mediaKey,
            "mediaMimeType" to mediaMimeType,
            "mediaName" to mediaName.take(256),
            "mediaSize" to mediaSize,
            "durationMs" to durationMs,
            "waveform" to waveform.take(128),
            "stickerId" to stickerId.take(128),
            "stickerPackId" to stickerPackId.take(128),
            "stickerLabel" to stickerLabel.take(120),
            "replyToMessageId" to replyToMessageId,
            "replyToPreview" to replyToPreview.take(300),
            "forwardedFrom" to forwardedFrom.take(120),
            "deletedForAll" to false,
            "deletedForUsers" to emptyList<String>(),
            "pinned" to false,
            "status" to "sent",
            "reactions" to emptyMap<String, List<String>>(),
            "timestamp" to FieldValue.serverTimestamp()
        )

        return try {
            msgRef.set(payload).await()
            val lastPreview = when {
                isEncrypted -> "Message chiffré E2EE"
                messageType == "image" -> "Photo"
                messageType == "video" -> "Vidéo"
                messageType == "voice" || messageType == "audio" -> "Message vocal"
                messageType == "file" -> mediaName.ifBlank { "Document" }
                messageType == "location" -> "Position partagée"
                messageType == "contact" -> "Contact partagé"
                messageType == "sticker" -> if (stickerLabel.isNotBlank()) "Autocollant : $stickerLabel" else "Autocollant"
                else -> plaintext.take(160)
            }
            convRef.update(
                mapOf(
                    "lastMessage" to lastPreview,
                    "lastMessageSenderId" to senderId,
                    "lastMessageType" to messageType,
                    "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            var recipients: List<String> = emptyList()
            var senderLabel = "BRAV Chat"
            var isGroupConv = false
            var groupLabel = ""
            runCatching {
                val snap = convRef.get().await()
                @Suppress("UNCHECKED_CAST")
                val partList = (snap.get("participants") as? List<String>).orEmpty()
                recipients = if (receiverId != conversationId && receiverId != senderId) {
                    listOf(receiverId)
                } else {
                    partList.filter { it != senderId }
                }
                isGroupConv = snap.getBoolean("isGroup") ?: false
                groupLabel = snap.getString("groupName").orEmpty()
                @Suppress("UNCHECKED_CAST")
                val namesMap = (snap.get("participantNames") as? Map<String, String>).orEmpty()
                senderLabel = namesMap[senderId].orEmpty().ifBlank { "Nouveau message" }
            }
            workerApiClient?.let { client ->
                runCatching {
                    client.notifyChatMessage(
                        conversationId = conversationId,
                        messageId = msgId,
                        recipientIds = recipients,
                        senderName = senderLabel,
                        messageType = messageType,
                        preview = lastPreview
                    )
                }
            }
            notificationRepository?.let { notifRepo ->
                val titleText = if (isGroupConv && groupLabel.isNotBlank()) {
                    "$senderLabel · $groupLabel"
                } else {
                    senderLabel
                }
                val bodyText = lastPreview.ifBlank { "Nouveau message" }
                recipients.filter { it.isNotBlank() && it != senderId }.forEach { targetUid ->
                    runCatching {
                        notifRepo.emitNotification(
                            targetUid = targetUid,
                            category = NotificationCategory.MESSAGES,
                            type = if (isGroupConv) "group_message" else "message",
                            title = titleText,
                            body = bodyText,
                            refId = conversationId,
                            refType = "conversation",
                            subRefId = msgId,
                            dedupKey = "msg_${conversationId}_${msgId}_${targetUid}",
                            senderUid = senderId,
                            senderName = senderLabel,
                            priority = "high"
                        )
                    }
                }
            }
            Result.success(msgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            throw e
        }
    }

    suspend fun uploadAndSendMediaMessage(
        conversation: Conversation,
        senderId: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        messageType: String,
        caption: String = "",
        durationMs: Long = 0L,
        waveform: List<Int> = emptyList(),
        replyTo: Message? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        val client = workerApiClient
            ?: return Result.failure(IllegalStateException("Client Worker indisponible"))
        val uploadRes = client.uploadMedia(
            conversationId = conversation.id,
            bytes = bytes,
            mimeType = mimeType,
            fileName = fileName,
            onProgress = onProgress
        )
        val uploaded: MediaUploadResponse = uploadRes.getOrElse { return Result.failure(it) }
        val receiverId = if (conversation.isGroup) conversation.id else (conversation.participants.firstOrNull { it != senderId } ?: senderId)
        return try {
            sendMessageInternal(
                conversationId = conversation.id,
                senderId = senderId,
                receiverId = receiverId,
                plaintext = caption,
                messageType = messageType,
                mediaUrl = uploaded.url,
                mediaKey = uploaded.key,
                mediaMimeType = uploaded.contentType,
                mediaName = fileName,
                mediaSize = uploaded.size,
                durationMs = durationMs,
                waveform = waveform,
                replyToMessageId = replyTo?.id.orEmpty(),
                replyToPreview = replyTo?.let { previewForReply(it) }.orEmpty()
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendLocationMessage(
        conversation: Conversation,
        senderId: String,
        latitude: Double,
        longitude: Double,
        label: String = "Position actuelle"
    ): Result<String> {
        val json = JSONObject().apply {
            put("lat", latitude)
            put("lng", longitude)
            put("label", label)
        }.toString()
        val receiverId = if (conversation.isGroup) conversation.id else (conversation.participants.firstOrNull { it != senderId } ?: senderId)
        return try {
            sendMessageInternal(
                conversationId = conversation.id,
                senderId = senderId,
                receiverId = receiverId,
                plaintext = json,
                messageType = "location"
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendContactMessage(
        conversation: Conversation,
        senderId: String,
        contact: User
    ): Result<String> {
        val json = JSONObject().apply {
            put("userId", contact.userId)
            put("displayName", contact.displayName)
            put("username", contact.username)
            put("city", contact.locationCity)
        }.toString()
        val receiverId = if (conversation.isGroup) conversation.id else (conversation.participants.firstOrNull { it != senderId } ?: senderId)
        return try {
            sendMessageInternal(
                conversationId = conversation.id,
                senderId = senderId,
                receiverId = receiverId,
                plaintext = json,
                messageType = "contact"
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendStickerMessage(
        conversation: Conversation,
        senderId: String,
        stickerId: String,
        stickerPackId: String,
        stickerLabel: String,
        replyTo: Message? = null
    ): Result<String> {
        val receiverId = if (conversation.isGroup) conversation.id
        else (conversation.participants.firstOrNull { it != senderId } ?: senderId)
        return try {
            sendMessageInternal(
                conversationId = conversation.id,
                senderId = senderId,
                receiverId = receiverId,
                plaintext = stickerLabel.ifBlank { "Autocollant" },
                messageType = "sticker",
                stickerId = stickerId,
                stickerPackId = stickerPackId,
                stickerLabel = stickerLabel,
                replyToMessageId = replyTo?.id.orEmpty(),
                replyToPreview = replyTo?.let { previewForReply(it) }.orEmpty()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun decryptDisplayText(
        message: Message,
        myUid: String,
        conversation: Conversation?
    ): String {
        if (message.deletedForAll) return "Ce message a été supprimé"
        if (!message.isEncrypted) return message.content
        val crypto = cryptoManager ?: return message.content
        val peerKey = if (message.senderId == myUid) {
            val peerUid = conversation?.participants?.firstOrNull { it != myUid }.orEmpty()
            conversation?.participantPublicKeys?.get(peerUid).orEmpty()
        } else {
            message.senderPublicKey.ifBlank {
                conversation?.participantPublicKeys?.get(message.senderId).orEmpty()
            }
        }
        if (peerKey.isBlank() || message.encryptionIv.isBlank()) return "Message chiffré (clé indisponible)"
        return crypto.decryptFromPeer(
            myUid = myUid,
            peerPublicKeyBase64 = peerKey,
            contextId = message.conversationId,
            ciphertextBase64 = message.content,
            ivBase64 = message.encryptionIv
        ) ?: "Message chiffré (appareil différent)"
    }

    suspend fun toggleReaction(
        conversationId: String,
        message: Message,
        uid: String,
        reactionKey: String
    ): Result<Unit> {
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(message.id)
        return try {
            val updated = message.reactions.mapValues { it.value.toMutableList() }.toMutableMap()
            val alreadyHasThis = updated[reactionKey]?.contains(uid) == true
            // Remove user from any prior reaction on this message so user holds one primary reaction cleanly
            updated.values.forEach { it.remove(uid) }
            if (!alreadyHasThis) {
                val list = updated.getOrPut(reactionKey) { mutableListOf() }
                list.add(uid)
            }
            val cleaned = updated.filterValues { it.isNotEmpty() }
            msgRef.update("reactions", cleaned).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    fun canEditMessage(message: Message, myUid: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        return message.senderId == myUid &&
            message.messageType == "text" &&
            !message.deletedForAll &&
            remainingEditSeconds(message, nowMs) > 0L
    }

    fun remainingEditSeconds(message: Message, nowMs: Long = System.currentTimeMillis()): Long {
        if (message.deletedForAll || message.messageType != "text") return 0L
        val sentMs = message.timestamp?.toDate()?.time ?: return 0L
        val elapsedMs = (nowMs - sentMs).coerceAtLeast(0L)
        if (elapsedMs > EDIT_WINDOW_MS) return 0L
        return ((EDIT_WINDOW_MS - elapsedMs + 999L) / 1000L).coerceIn(0L, 30L)
    }

    suspend fun editMessage(
        conversation: Conversation,
        message: Message,
        myUid: String,
        newPlaintext: String
    ): Result<Unit> {
        if (message.senderId != myUid || message.deletedForAll || message.messageType != "text") {
            return Result.failure(IllegalStateException("Modification non autorisée"))
        }
        val sentMs = message.timestamp?.toDate()?.time ?: 0L
        if (sentMs <= 0L || (System.currentTimeMillis() - sentMs) > EDIT_WINDOW_MS) {
            return Result.failure(IllegalStateException("Modification impossible : le délai de 30 secondes est expiré."))
        }
        val clean = newPlaintext.trim()
        if (clean.isBlank()) {
            return Result.failure(IllegalArgumentException("Le message ne peut pas être vide"))
        }
        val msgRef = db.collection("conversations").document(conversation.id)
            .collection("messages").document(message.id)
        return try {
            val peerUid = conversation.participants.firstOrNull { it != myUid }.orEmpty()
            val peerPub = conversation.participantPublicKeys[peerUid].orEmpty()
            val updates = if (!conversation.isGroup && peerPub.isNotBlank() && cryptoManager != null) {
                val enc = cryptoManager.encryptForPeer(myUid, peerPub, conversation.id, clean)
                mapOf(
                    "content" to enc.ciphertextBase64,
                    "isEncrypted" to true,
                    "encryptionIv" to enc.ivBase64,
                    "senderPublicKey" to enc.senderPublicKeyBase64,
                    "editedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "content" to clean,
                    "isEncrypted" to false,
                    "encryptionIv" to "",
                    "editedAt" to FieldValue.serverTimestamp()
                )
            }
            msgRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String, myUid: String): Result<Unit> {
        val convRef = db.collection("conversations").document(conversationId)
        val prefRef = db.collection("users").document(myUid)
            .collection("conversation_prefs").document(conversationId)
        return try {
            runCatching { localDb?.offlineQueueDao()?.deleteForConversation(conversationId) }
            val snap = convRef.get().await()
            if (snap.exists()) {
                val conv = snap.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (conv != null && conv.isGroup && conv.ownerId != myUid && conv.participants.size > 2) {
                    convRef.update(
                        mapOf(
                            "participants" to conv.participants.filter { it != myUid },
                            "adminIds" to conv.adminIds.filter { it != myUid },
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    while (true) {
                        val batchSnap = convRef.collection("messages").limit(350).get().await()
                        if (batchSnap.isEmpty) break
                        val batch = db.batch()
                        batchSnap.documents.forEach { batch.delete(it.reference) }
                        batch.commit().await()
                    }
                    convRef.delete().await()
                }
            }
            runCatching { prefRef.delete().await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, convRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForMe(conversationId: String, messageId: String, myUid: String): Result<Unit> {
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        return try {
            msgRef.update("deletedForUsers", FieldValue.arrayUnion(myUid)).await()
            runCatching { workerApiClient?.syncMessageDeletion(conversationId, messageId, "me") }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForAll(conversationId: String, messageId: String): Result<Unit> {
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        return try {
            msgRef.update(
                mapOf(
                    "deletedForAll" to true,
                    "content" to "Ce message a été supprimé",
                    "isEncrypted" to false,
                    "encryptionIv" to "",
                    "editedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching { workerApiClient?.syncMessageDeletion(conversationId, messageId, "all") }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun pinMessage(conversationId: String, message: Message, pinned: Boolean): Result<Unit> {
        val convRef = db.collection("conversations").document(conversationId)
        val msgRef = convRef.collection("messages").document(message.id)
        return try {
            msgRef.update("pinned", pinned).await()
            convRef.update(
                mapOf(
                    "pinnedMessageId" to if (pinned) message.id else "",
                    "pinnedMessagePreview" to if (pinned) previewForReply(message) else "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun setTypingStatus(conversationId: String, userId: String, isTyping: Boolean) {
        if (conversationId.isBlank() || userId.isBlank()) return
        runCatching {
            val op = if (isTyping) FieldValue.arrayUnion(userId) else FieldValue.arrayRemove(userId)
            db.collection("conversations").document(conversationId)
                .update(
                    mapOf(
                        "typingUsers" to op,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        }
    }

    suspend fun markMessagesAsRead(conversationId: String, messages: List<Message>, myUid: String) {
        val unread = messages.filter { it.senderId != myUid && it.status != "read" }
        for (msg in unread.take(25)) {
            runCatching {
                db.collection("conversations").document(conversationId)
                    .collection("messages").document(msg.id)
                    .update(
                        mapOf(
                            "status" to "read",
                            "readAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }
        }
    }

    suspend fun markConversationAsRead(
        myUid: String,
        conversationId: String,
        latestTimestampMs: Long = System.currentTimeMillis()
    ): Result<Unit> {
        if (myUid.isBlank() || conversationId.isBlank()) return Result.success(Unit)
        val prefRef = db.collection("users").document(myUid)
            .collection("conversation_prefs").document(conversationId)
        val nowMs = System.currentTimeMillis()
        val existingReadMs = runCatching { prefRef.get().await().getLong("lastReadAtMs") ?: 0L }.getOrDefault(0L)
        val resolvedReadMs = maxOf(existingReadMs, latestTimestampMs, nowMs)
        return updateConversationPref(
            myUid = myUid,
            conversationId = conversationId,
            markedUnread = false,
            lastReadAtMs = resolvedReadMs
        )
    }

    suspend fun updateConversationPref(
        myUid: String,
        conversationId: String,
        archived: Boolean? = null,
        pinned: Boolean? = null,
        muted: Boolean? = null,
        locked: Boolean? = null,
        markedUnread: Boolean? = null,
        hiddenLocally: Boolean? = null,
        wallpaperId: String? = null,
        clearedAtMs: Long? = null,
        lastReadAtMs: Long? = null
    ): Result<Unit> {
        val prefRef = db.collection("users").document(myUid)
            .collection("conversation_prefs").document(conversationId)
        return try {
            val map = mutableMapOf<String, Any>(
                "conversationId" to conversationId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            archived?.let { map["archived"] = it }
            pinned?.let { map["pinned"] = it }
            muted?.let { map["muted"] = it }
            locked?.let { map["locked"] = it }
            markedUnread?.let { map["markedUnread"] = it }
            hiddenLocally?.let { map["hiddenLocally"] = it }
            wallpaperId?.let { map["wallpaperId"] = it }
            clearedAtMs?.let { map["clearedAtMs"] = it }
            lastReadAtMs?.let { map["lastReadAtMs"] = it.coerceAtLeast(0L) }
            prefRef.set(map, SetOptions.merge()).await()
            runCatching {
                val snap = prefRef.get().await()
                workerApiClient?.syncConversationState(
                    conversationId = conversationId,
                    archived = snap.getBoolean("archived") ?: false,
                    pinned = snap.getBoolean("pinned") ?: false,
                    muted = snap.getBoolean("muted") ?: false,
                    locked = snap.getBoolean("locked") ?: false,
                    markedUnread = snap.getBoolean("markedUnread") ?: false,
                    hiddenLocally = snap.getBoolean("hiddenLocally") ?: false,
                    lastReadAtMs = snap.getLong("lastReadAtMs") ?: lastReadAtMs
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, prefRef.path)
            Result.failure(e)
        }
    }

    fun computeSafetyCode(myUid: String, peerPublicKey: String): String {
        val crypto = cryptoManager ?: return "Chiffrement indisponible"
        return crypto.computeSafetyFingerprint(crypto.getPublicKeyBase64(myUid), peerPublicKey)
    }

    internal fun previewForReply(msg: Message): String {
        return when {
            msg.deletedForAll -> "Message supprimé"
            msg.messageType == "image" -> "Photo"
            msg.messageType == "video" -> "Vidéo"
            msg.messageType == "voice" || msg.messageType == "audio" -> "Message vocal"
            msg.messageType == "file" -> msg.mediaName.ifBlank { "Document" }
            msg.messageType == "location" -> "Position GPS"
            msg.messageType == "sticker" -> msg.stickerLabel.ifBlank { "Autocollant" }
            msg.isEncrypted -> "Message chiffré"
            else -> msg.content.take(120)
        }
    }

    companion object {
        const val EDIT_WINDOW_MS = 30_000L
    }
}
