package com.example.repository

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.ai.ContentBlockedException
import com.example.ai.ContentModerationGuard
import com.example.crypto.E2eeCryptoManager
import com.example.model.AbuseReport
import com.example.model.CallerDirectoryEntry
import com.example.model.CallerReport
import com.example.model.CallerReportCategory
import com.example.model.LiveLocationShare
import com.example.model.NotificationCategory
import com.example.model.OfferListing
import com.example.model.StatusComment
import com.example.model.StatusPost
import com.example.model.User
import com.example.network.WorkerApiClient
import com.example.util.ImageCompressor
import com.example.util.OperationType
import com.example.util.PhoneUtils
import com.example.util.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import java.util.UUID

data class DecryptedCoordinates(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0f
)

data class MediaUploadPayload(
    val bytes: ByteArray = ByteArray(0),
    val mimeType: String = "image/jpeg",
    val isVideo: Boolean = false,
    val sourceVideoUri: Uri? = null,
    val localFile: File? = null,
    val width: Int = 0,
    val height: Int = 0,
    val aspectRatio: Float = 0f,
    val durationMs: Long = 0L,
    val thumbnailBytes: ByteArray? = null
)

class SocialRepository(
    private val db: FirebaseFirestore,
    private val workerApiClient: WorkerApiClient? = null,
    private val cryptoManager: E2eeCryptoManager? = null,
    private val notificationRepository: NotificationRepository? = null
) {
    constructor(context: Context, workerApiClient: WorkerApiClient? = null) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        workerApiClient = workerApiClient,
        cryptoManager = E2eeCryptoManager(context.applicationContext)
    )

    // =========================================================================
    // ACTUS SOCIAL FEED & 24H EPHEMERAL STATUSES
    // =========================================================================
    fun observeStatuses(myUid: String = ""): Flow<List<StatusPost>> {
        val publicFlow = db.collection("statuses")
            .whereEqualTo("audienceType", "all")
            .snapshots()
            .map { it.toObjects(StatusPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        if (myUid.isBlank()) {
            return publicFlow.map { list ->
                val nowMs = System.currentTimeMillis()
                list.filter { it.expiresAtMs > nowMs }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
        }

        val mineFlow = db.collection("statuses")
            .whereEqualTo("userId", myUid)
            .snapshots()
            .map { it.toObjects(StatusPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        val targetedFlow = db.collection("statuses")
            .whereArrayContains("allowedUids", myUid)
            .snapshots()
            .map { it.toObjects(StatusPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        return combine(publicFlow, mineFlow, targetedFlow) { pub, mine, targeted ->
            val nowMs = System.currentTimeMillis()
            (pub + mine + targeted)
                .distinctBy { it.id }
                .filter { it.expiresAtMs > nowMs }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
    }

    suspend fun createTextStatus(
        user: User,
        text: String,
        backgroundColor: String,
        audienceType: String = "all",
        allowedUids: List<String> = emptyList(),
        isEphemeral24h: Boolean = true,
        allowComments: Boolean = true,
        locationLabel: String = ""
    ): Result<String> {
        val verdict = ContentModerationGuard.moderatePublication(
            text = text,
            workerApiClient = workerApiClient
        )
        if (!verdict.allowed) {
            return Result.failure(ContentBlockedException(verdict.reasonCode, verdict.userMessage))
        }

        val cleanText = text.trim().take(1000)
        val cleanLocation = locationLabel.trim().take(80)
        val id = "st_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("statuses").document(id)
        val nowMs = System.currentTimeMillis()
        val expires = if (isEphemeral24h) {
            nowMs + 24L * 3600L * 1000L
        } else {
            nowMs + 3650L * 24L * 3600L * 1000L
        }
        val headline = user.locationCity.ifBlank { user.bio.take(64) }
        val mentions = StatusPost.extractMentions(cleanText)
        val hashtags = StatusPost.extractHashtags(cleanText)
        return try {
            ref.set(
                mapOf(
                    "userId" to user.userId,
                    "userName" to user.displayName.take(120),
                    "userAvatar" to user.avatarUrl,
                    "authorUsername" to user.username.take(64),
                    "authorHeadline" to headline.take(100),
                    "type" to "text",
                    "text" to cleanText,
                    "caption" to "",
                    "mediaUrl" to "",
                    "mediaUrls" to emptyList<String>(),
                    "mediaAspectRatio" to 0.0,
                    "mediaWidth" to 0L,
                    "mediaHeight" to 0L,
                    "mediaAspectRatios" to emptyList<Double>(),
                    "thumbnailUrl" to "",
                    "durationMs" to 0L,
                    "allowComments" to allowComments,
                    "locationLabel" to cleanLocation,
                    "hashtags" to hashtags,
                    "mentions" to mentions,
                    "backgroundColor" to backgroundColor,
                    "audienceType" to if (audienceType == "selected") "selected" else "all",
                    "allowedUids" to allowedUids.take(100),
                    "viewerIds" to emptyList<String>(),
                    "likedByUids" to emptyList<String>(),
                    "bookmarkedByUids" to emptyList<String>(),
                    "reactions" to emptyMap<String, List<String>>(),
                    "commentCount" to 0L,
                    "viewCount" to 0L,
                    "shareCount" to 0L,
                    "expiresAtMs" to expires,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            emitActusPostNotificationsToFollowers(
                author = user,
                statusId = id,
                previewText = cleanText,
                audienceType = audienceType,
                allowedUids = allowedUids,
                isVideoPost = false
            )
            emitMentionNotifications(
                author = user,
                statusId = id,
                mentions = mentions,
                snippet = cleanText
            )
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun createMediaStatus(
        user: User,
        bytes: ByteArray,
        mimeType: String,
        isVideo: Boolean,
        caption: String,
        audienceType: String = "all",
        allowedUids: List<String> = emptyList(),
        isEphemeral24h: Boolean = true,
        context: Context? = null,
        sourceVideoUri: Uri? = null,
        allowComments: Boolean = true,
        locationLabel: String = ""
    ): Result<String> {
        return createMultiMediaStatus(
            user = user,
            mediaItems = listOf(
                MediaUploadPayload(
                    bytes = bytes,
                    mimeType = mimeType,
                    isVideo = isVideo,
                    sourceVideoUri = sourceVideoUri
                )
            ),
            caption = caption,
            audienceType = audienceType,
            allowedUids = allowedUids,
            isEphemeral24h = isEphemeral24h,
            context = context,
            allowComments = allowComments,
            locationLabel = locationLabel
        )
    }

    suspend fun createMultiMediaStatus(
        user: User,
        mediaItems: List<MediaUploadPayload>,
        caption: String,
        audienceType: String = "all",
        allowedUids: List<String> = emptyList(),
        isEphemeral24h: Boolean = true,
        context: Context? = null,
        allowComments: Boolean = true,
        locationLabel: String = "",
        onUploadProgress: (Float) -> Unit = {}
    ): Result<String> {
        val limitedItems = mediaItems.filter {
            it.bytes.isNotEmpty() || (it.localFile != null && it.localFile.exists() && it.localFile.length() > 0L)
        }.take(6)
        if (limitedItems.isEmpty()) {
            return Result.failure(IllegalArgumentException("Aucun média valide sélectionné."))
        }
        val cleanCaption = caption.trim().take(500)
        val cleanLocation = locationLabel.trim().take(80)

        // Moderate text & EVERY selected visual media BEFORE uploading anything
        val textVerdict = ContentModerationGuard.moderatePublication(
            text = cleanCaption,
            workerApiClient = workerApiClient
        )
        if (!textVerdict.allowed) {
            return Result.failure(ContentBlockedException(textVerdict.reasonCode, textVerdict.userMessage))
        }

        for (item in limitedItems) {
            val effectiveVideoUri = item.sourceVideoUri ?: item.localFile?.let { Uri.fromFile(it) }
            val mediaVerdict = ContentModerationGuard.moderatePublication(
                text = "",
                imageBytes = if (!item.isVideo && item.bytes.isNotEmpty()) item.bytes else null,
                isVideo = item.isVideo,
                videoUri = effectiveVideoUri,
                context = context,
                workerApiClient = workerApiClient
            )
            if (!mediaVerdict.allowed) {
                return Result.failure(ContentBlockedException(mediaVerdict.reasonCode, mediaVerdict.userMessage))
            }
        }

        val client = workerApiClient ?: return Result.failure(IllegalStateException("Client Worker indisponible"))
        val uploadedUrls = mutableListOf<String>()
        val itemRatios = mutableListOf<Double>()
        var primaryWidth = 0
        var primaryHeight = 0
        var primaryDurationMs = 0L
        var uploadedThumbnailUrl = ""

        val totalSteps = limitedItems.size.coerceAtLeast(1)
        for ((idx, item) in limitedItems.withIndex()) {
            val baseProgress = idx.toFloat() / totalSteps.toFloat()
            val span = 1f / totalSteps.toFloat()
            val uploadResult = if (item.localFile != null && item.localFile.exists()) {
                client.uploadMediaFile(
                    conversationId = "status_${user.userId}",
                    file = item.localFile,
                    mimeType = item.mimeType,
                    onProgress = { p -> onUploadProgress((baseProgress + p * span).coerceIn(0f, 0.96f)) }
                )
            } else {
                client.uploadMedia("status_${user.userId}", item.bytes, item.mimeType).also {
                    onUploadProgress((baseProgress + span * 0.9f).coerceIn(0f, 0.96f))
                }
            }
            val uploaded = uploadResult.getOrElse { err ->
                uploadedUrls.forEach { runCatching { client.deleteMediaByUrl(it) } }
                return Result.failure(err)
            }
            uploadedUrls += uploaded.url

            var itemW = item.width
            var itemH = item.height
            if (!item.isVideo && (itemW <= 0 || itemH <= 0) && item.bytes.isNotEmpty()) {
                val (mw, mh) = ImageCompressor.measureBytesDimensions(item.bytes)
                itemW = mw
                itemH = mh
            }
            val ratio = when {
                item.aspectRatio > 0.05f -> item.aspectRatio
                itemW > 0 && itemH > 0 -> itemW.toFloat() / itemH.toFloat()
                else -> 0f
            }
            itemRatios += ratio.toDouble()
            if (idx == 0) {
                primaryWidth = itemW
                primaryHeight = itemH
                primaryDurationMs = item.durationMs.coerceAtLeast(0L)
                val thumb = item.thumbnailBytes
                if (item.isVideo && thumb != null && thumb.isNotEmpty()) {
                    client.uploadMedia(
                        conversationId = "status_${user.userId}",
                        bytes = thumb,
                        mimeType = "image/jpeg",
                        fileName = "thumb_${System.currentTimeMillis()}.jpg"
                    ).onSuccess { uploadedThumbnailUrl = it.url }
                }
            }
        }

        val isVideoPost = limitedItems.first().isVideo
        val statusId = "st_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("statuses").document(statusId)
        val nowMs = System.currentTimeMillis()
        val expires = if (isEphemeral24h) {
            nowMs + 24L * 3600L * 1000L
        } else {
            nowMs + 3650L * 24L * 3600L * 1000L
        }
        val headline = user.locationCity.ifBlank { user.bio.take(64) }
        val mentions = StatusPost.extractMentions(cleanCaption)
        val hashtags = StatusPost.extractHashtags(cleanCaption)
        val primaryAspectRatio = itemRatios.firstOrNull() ?: 0.0
        onUploadProgress(0.98f)
        return try {
            ref.set(
                mapOf(
                    "userId" to user.userId,
                    "userName" to user.displayName.take(120),
                    "userAvatar" to user.avatarUrl,
                    "authorUsername" to user.username.take(64),
                    "authorHeadline" to headline.take(100),
                    "type" to if (isVideoPost) "video" else "image",
                    "text" to "",
                    "caption" to cleanCaption,
                    "mediaUrl" to uploadedUrls.first(),
                    "mediaUrls" to uploadedUrls,
                    "mediaAspectRatio" to primaryAspectRatio,
                    "mediaWidth" to primaryWidth.toLong(),
                    "mediaHeight" to primaryHeight.toLong(),
                    "mediaAspectRatios" to itemRatios,
                    "thumbnailUrl" to uploadedThumbnailUrl,
                    "durationMs" to primaryDurationMs,
                    "allowComments" to allowComments,
                    "locationLabel" to cleanLocation,
                    "hashtags" to hashtags,
                    "mentions" to mentions,
                    "backgroundColor" to "#570CA2",
                    "audienceType" to if (audienceType == "selected") "selected" else "all",
                    "allowedUids" to allowedUids.take(100),
                    "viewerIds" to emptyList<String>(),
                    "likedByUids" to emptyList<String>(),
                    "bookmarkedByUids" to emptyList<String>(),
                    "reactions" to emptyMap<String, List<String>>(),
                    "commentCount" to 0L,
                    "viewCount" to 0L,
                    "shareCount" to 0L,
                    "expiresAtMs" to expires,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            onUploadProgress(1f)
            emitActusPostNotificationsToFollowers(
                author = user,
                statusId = statusId,
                previewText = cleanCaption.ifBlank {
                    if (isVideoPost) "A publié une nouvelle vidéo sur BRAV Vidéos." else "A publié une nouvelle photo sur ACTUS."
                },
                audienceType = audienceType,
                allowedUids = allowedUids,
                isVideoPost = isVideoPost
            )
            emitMentionNotifications(
                author = user,
                statusId = statusId,
                mentions = mentions,
                snippet = cleanCaption
            )
            Result.success(statusId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    private suspend fun emitActusPostNotificationsToFollowers(
        author: User,
        statusId: String,
        previewText: String,
        audienceType: String,
        allowedUids: List<String>,
        isVideoPost: Boolean = false
    ) {
        val repo = notificationRepository ?: return
        runCatching {
            val targets = if (audienceType == "selected") {
                allowedUids.filter { it.isNotBlank() && it != author.userId }.distinct().take(40)
            } else {
                val snap = db.collection("users")
                    .document(author.userId)
                    .collection("followers")
                    .limit(40)
                    .get()
                    .await()
                snap.documents.mapNotNull { doc ->
                    (doc.getString("followerUid") ?: doc.id).takeIf { it.isNotBlank() && it != author.userId }
                }.distinct()
            }
            if (targets.isEmpty()) return
            val snippet = previewText.trim().take(100).ifBlank {
                if (isVideoPost) "Nouvelle vidéo publiée sur BRAV." else "Nouvelle publication sur ACTUS."
            }
            targets.forEach { targetUid ->
                repo.emitNotification(
                    targetUid = targetUid,
                    category = NotificationCategory.ACTUS,
                    type = if (isVideoPost) "actus_video" else "actus_post",
                    title = if (isVideoPost) "Nouvelle vidéo BRAV" else "Nouvelle publication ACTUS",
                    body = "${author.displayName.ifBlank { "Un membre BRAV" }} : $snippet",
                    refId = statusId,
                    refType = if (isVideoPost) "actus_video" else "actus_post",
                    dedupKey = "post_${statusId}_${targetUid}",
                    senderUid = author.userId,
                    senderName = author.displayName,
                    senderAvatar = author.avatarUrl
                )
            }
        }
    }

    private suspend fun emitMentionNotifications(
        author: User,
        statusId: String,
        mentions: List<String>,
        snippet: String
    ) {
        val repo = notificationRepository ?: return
        if (mentions.isEmpty()) return
        runCatching {
            val preview = snippet.trim().take(80)
            mentions.take(8).forEach { handle ->
                val snap = db.collection("users")
                    .whereEqualTo("usernameLower", handle.lowercase())
                    .limit(1)
                    .get()
                    .await()
                val targetUid = snap.documents.firstOrNull()?.id.orEmpty()
                if (targetUid.isNotBlank() && targetUid != author.userId) {
                    repo.emitNotification(
                        targetUid = targetUid,
                        category = NotificationCategory.ACTUS,
                        type = "actus_mention",
                        title = "Vous avez été mentionné(e)",
                        body = "${author.displayName.ifBlank { "Un membre BRAV" }} vous a mentionné(e) : $preview",
                        refId = statusId,
                        refType = "actus_post",
                        dedupKey = "mention_${statusId}_${targetUid}",
                        senderUid = author.userId,
                        senderName = author.displayName,
                        senderAvatar = author.avatarUrl
                    )
                }
            }
        }
    }

    suspend fun markStatusViewed(statusId: String, viewerUid: String) {
        val ref = db.collection("statuses").document(statusId)
        runCatching {
            ref.update("viewerIds", FieldValue.arrayUnion(viewerUid)).await()
        }
    }

    suspend fun recordVideoView(status: StatusPost, viewerUid: String) {
        if (status.id.isBlank() || viewerUid.isBlank()) return
        if (viewerUid in status.viewerIds) return
        val ref = db.collection("statuses").document(status.id)
        runCatching {
            ref.update(
                mapOf(
                    "viewerIds" to FieldValue.arrayUnion(viewerUid),
                    "viewCount" to FieldValue.increment(1L)
                )
            ).await()
        }
    }

    suspend fun toggleStatusBookmark(status: StatusPost, myUid: String): Result<Boolean> {
        if (status.id.isBlank() || myUid.isBlank()) {
            return Result.failure(IllegalArgumentException("Publication invalide"))
        }
        val ref = db.collection("statuses").document(status.id)
        val alreadyBookmarked = myUid in status.bookmarkedByUids
        return try {
            val op = if (alreadyBookmarked) FieldValue.arrayRemove(myUid) else FieldValue.arrayUnion(myUid)
            ref.update("bookmarkedByUids", op).await()
            Result.success(!alreadyBookmarked)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun recordStatusShare(status: StatusPost, actorUid: String, actorName: String = "") {
        if (status.id.isBlank() || actorUid.isBlank()) return
        val ref = db.collection("statuses").document(status.id)
        runCatching {
            ref.update("shareCount", FieldValue.increment(1L)).await()
        }
        if (status.userId.isNotBlank() && status.userId != actorUid) {
            runCatching {
                val actor = actorName.ifBlank { "Un membre BRAV" }
                notificationRepository?.emitNotification(
                    targetUid = status.userId,
                    category = NotificationCategory.ACTUS,
                    type = "actus_share",
                    title = "Publication partagée",
                    body = "$actor a partagé votre publication.",
                    refId = status.id,
                    refType = "actus_post",
                    dedupKey = "share_${status.id}_${actorUid}",
                    senderUid = actorUid,
                    senderName = actor
                )
            }
        }
    }

    suspend fun deleteStatus(statusId: String): Result<Unit> {
        val ref = db.collection("statuses").document(statusId)
        return try {
            val snap = runCatching { ref.get().await() }.getOrNull()
            val primaryUrl = snap?.getString("mediaUrl").orEmpty()
            val thumbUrl = snap?.getString("thumbnailUrl").orEmpty()
            @Suppress("UNCHECKED_CAST")
            val multiUrls = (snap?.get("mediaUrls") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            ref.delete().await()
            (listOf(primaryUrl, thumbUrl) + multiUrls)
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { url -> runCatching { workerApiClient?.deleteMediaByUrl(url) } }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun updateStatusText(statusId: String, newText: String, isMediaPost: Boolean): Result<Unit> {
        val trimmed = newText.trim().take(1000)
        if (trimmed.isBlank() && !isMediaPost) {
            return Result.failure(IllegalArgumentException("Le texte de la publication ne peut pas être vide."))
        }
        val verdict = ContentModerationGuard.moderatePublication(
            text = trimmed,
            workerApiClient = workerApiClient
        )
        if (!verdict.allowed) {
            return Result.failure(ContentBlockedException(verdict.reasonCode, verdict.userMessage))
        }
        val ref = db.collection("statuses").document(statusId)
        val fieldKey = if (isMediaPost) "caption" else "text"
        val maxLen = if (isMediaPost) 500 else 1000
        return try {
            ref.update(
                mapOf(
                    fieldKey to trimmed.take(maxLen)
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun toggleStatusLike(
        status: StatusPost,
        myUid: String,
        actorName: String = ""
    ): Result<Unit> {
        val ref = db.collection("statuses").document(status.id)
        val alreadyLiked = myUid in status.likedByUids
        return try {
            val op = if (alreadyLiked) FieldValue.arrayRemove(myUid) else FieldValue.arrayUnion(myUid)
            ref.update("likedByUids", op).await()
            if (!alreadyLiked && status.userId.isNotBlank() && status.userId != myUid) {
                runCatching {
                    val actor = actorName.ifBlank { "Un membre BRAV" }
                    notificationRepository?.emitNotification(
                        targetUid = status.userId,
                        category = NotificationCategory.ACTUS,
                        type = "actus_like",
                        title = "Nouvelle mention J'aime",
                        body = "$actor a aimé votre publication ACTUS.",
                        refId = status.id,
                        refType = "actus_post",
                        dedupKey = "like_${status.id}_${myUid}",
                        senderUid = myUid,
                        senderName = actor
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun toggleStatusReaction(
        status: StatusPost,
        myUid: String,
        reactionKey: String,
        actorName: String = ""
    ): Result<Unit> {
        val ref = db.collection("statuses").document(status.id)
        val updated = status.reactions.toMutableMap()
        val currentList = updated[reactionKey].orEmpty().toMutableList()
        val adding = myUid !in currentList
        if (!adding) {
            currentList.remove(myUid)
        } else {
            currentList.add(myUid)
        }
        if (currentList.isEmpty()) updated.remove(reactionKey) else updated[reactionKey] = currentList
        return try {
            ref.update("reactions", updated).await()
            if (adding && status.userId.isNotBlank() && status.userId != myUid) {
                runCatching {
                    val actor = actorName.ifBlank { "Un membre BRAV" }
                    notificationRepository?.emitNotification(
                        targetUid = status.userId,
                        category = NotificationCategory.ACTUS,
                        type = "actus_reaction",
                        title = "Nouvelle réaction",
                        body = "$actor a réagi à votre publication.",
                        refId = status.id,
                        refType = "actus_post",
                        dedupKey = "react_${status.id}_${myUid}_${reactionKey}",
                        senderUid = myUid,
                        senderName = actor
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    fun observeStatusComments(statusId: String): Flow<List<StatusComment>> {
        return db.collection("statuses").document(statusId)
            .collection("comments")
            .snapshots()
            .map { snap ->
                snap.toObjects(StatusComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
    }

    suspend fun addStatusComment(
        statusId: String,
        author: User,
        text: String,
        reactionKey: String = "",
        parentCommentId: String = "",
        replyToAuthorName: String = ""
    ): Result<String> {
        val trimmed = text.trim().take(500)
        if (trimmed.isBlank() && reactionKey.isBlank()) {
            return Result.failure(IllegalArgumentException("Commentaire vide"))
        }
        val verdict = ContentModerationGuard.moderatePublication(
            text = trimmed,
            workerApiClient = workerApiClient
        )
        if (!verdict.allowed) {
            return Result.failure(ContentBlockedException(verdict.reasonCode, verdict.userMessage))
        }
        val statusRef = db.collection("statuses").document(statusId)
        val statusSnap = runCatching { statusRef.get().await() }.getOrNull()
        if (statusSnap != null && statusSnap.exists() && statusSnap.getBoolean("allowComments") == false) {
            return Result.failure(IllegalStateException("Les commentaires sont désactivés pour cette publication."))
        }
        val commentId = "cmt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val commentRef = statusRef.collection("comments").document(commentId)
        return try {
            commentRef.set(
                mapOf(
                    "statusId" to statusId,
                    "authorId" to author.userId,
                    "authorName" to author.displayName.take(120),
                    "authorAvatar" to author.avatarUrl,
                    "text" to trimmed,
                    "reactionKey" to reactionKey.take(32),
                    "parentCommentId" to parentCommentId.take(64),
                    "replyToAuthorName" to replyToAuthorName.take(120),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching {
                statusRef.update("commentCount", FieldValue.increment(1L)).await()
            }
            runCatching {
                val statusOwnerUid = statusSnap?.getString("userId")
                    ?: statusRef.get().await().getString("userId").orEmpty()
                if (statusOwnerUid.isNotBlank() && statusOwnerUid != author.userId) {
                    val snippet = if (trimmed.isNotBlank()) trimmed.take(90) else "A réagi à votre publication"
                    notificationRepository?.emitNotification(
                        targetUid = statusOwnerUid,
                        category = NotificationCategory.ACTUS,
                        type = "actus_comment",
                        title = "Nouveau commentaire ACTUS",
                        body = "${author.displayName} : $snippet",
                        refId = statusId,
                        refType = "actus_post",
                        subRefId = commentId,
                        dedupKey = "cmt_${statusId}_${commentId}",
                        senderUid = author.userId,
                        senderName = author.displayName,
                        senderAvatar = author.avatarUrl
                    )
                }
                val commentMentions = StatusPost.extractMentions(trimmed)
                if (commentMentions.isNotEmpty()) {
                    emitMentionNotifications(
                        author = author,
                        statusId = statusId,
                        mentions = commentMentions,
                        snippet = trimmed
                    )
                }
            }
            Result.success(commentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteStatusComment(statusId: String, commentId: String): Result<Unit> {
        val statusRef = db.collection("statuses").document(statusId)
        val commentRef = statusRef.collection("comments").document(commentId)
        return try {
            commentRef.delete().await()
            runCatching {
                val snap = statusRef.get().await()
                val current = (snap.getLong("commentCount") ?: 1L).coerceAtLeast(1L)
                statusRef.update("commentCount", current - 1L).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, commentRef.path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // ENCRYPTED REAL-TIME LIVE LOCATION SHARING
    // =========================================================================
    fun observeMySharedLocations(uid: String): Flow<List<LiveLocationShare>> {
        return db.collection("live_locations")
            .whereEqualTo("ownerId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(LiveLocationShare::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.active && it.expiresAtMs > System.currentTimeMillis() }
            }
    }

    fun observeLocationsSharedWithMe(uid: String): Flow<List<LiveLocationShare>> {
        return db.collection("live_locations")
            .whereArrayContains("recipientUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(LiveLocationShare::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.active && it.expiresAtMs > System.currentTimeMillis() }
            }
    }

    suspend fun publishEncryptedLocation(
        owner: User,
        recipients: List<User>,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        durationMinutes: Int
    ): Result<String> {
        val shareId = "loc_${owner.userId}"
        val ref = db.collection("live_locations").document(shareId)
        val crypto = cryptoManager ?: return Result.failure(IllegalStateException("Chiffrement indisponible"))
        val expires = System.currentTimeMillis() + durationMinutes * 60_000L

        val coordsPlain = JSONObject().apply {
            put("lat", latitude)
            put("lng", longitude)
            put("acc", accuracyMeters.toDouble())
        }.toString()

        val symKey = crypto.generateGroupKeyBase64()
        val (cipher, iv) = crypto.encryptWithSymmetricKey(symKey, coordsPlain)
        val ownerPub = crypto.getPublicKeyBase64(owner.userId)
        val wrappedKeys = JSONObject()

        val allTargets = (listOf(owner) + recipients).distinctBy { it.userId }.take(20)
        for (target in allTargets) {
            val pub = if (target.userId == owner.userId) ownerPub else target.publicKey
            if (pub.isNotBlank()) {
                val encKey = crypto.encryptForPeer(owner.userId, pub, shareId, symKey)
                wrappedKeys.put(
                    target.userId,
                    JSONObject().apply {
                        put("k", encKey.ciphertextBase64)
                        put("iv", encKey.ivBase64)
                    }
                )
            }
        }

        val envelope = JSONObject().apply {
            put("c", cipher)
            put("iv", iv)
            put("pk", ownerPub)
            put("keys", wrappedKeys)
        }.toString()

        return try {
            ref.set(
                mapOf(
                    "ownerId" to owner.userId,
                    "ownerName" to owner.displayName.take(120),
                    "recipientUids" to recipients.map { it.userId }.take(50),
                    "encryptedCoords" to envelope.take(4000),
                    "encryptionIv" to iv,
                    "senderPublicKey" to ownerPub,
                    "isEncrypted" to true,
                    "active" to true,
                    "expiresAtMs" to expires,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(shareId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun stopLocationSharing(ownerId: String): Result<Unit> {
        val shareId = "loc_$ownerId"
        val ref = db.collection("live_locations").document(shareId)
        return try {
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    fun decryptLocation(share: LiveLocationShare, myUid: String): DecryptedCoordinates? {
        val crypto = cryptoManager ?: return null
        return try {
            val env = JSONObject(share.encryptedCoords)
            val cipher = env.getString("c")
            val iv = env.getString("iv")
            val senderPub = env.optString("pk").ifBlank { share.senderPublicKey }
            val keysObj = env.optJSONObject("keys") ?: return null
            val myWrapped = keysObj.optJSONObject(myUid) ?: return null
            val symKey = crypto.decryptFromPeer(
                myUid = myUid,
                peerPublicKeyBase64 = senderPub,
                contextId = share.id.ifBlank { "loc_${share.ownerId}" },
                ciphertextBase64 = myWrapped.getString("k"),
                ivBase64 = myWrapped.getString("iv")
            ) ?: return null
            val plain = crypto.decryptWithSymmetricKey(symKey, cipher, iv) ?: return null
            val j = JSONObject(plain)
            DecryptedCoordinates(
                latitude = j.getDouble("lat"),
                longitude = j.getDouble("lng"),
                accuracyMeters = j.optDouble("acc", 0.0).toFloat()
            )
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // FREE OFFERS / LISTINGS (100% GRATUIT)
    // =========================================================================
    fun observeOffers(): Flow<List<OfferListing>> {
        return db.collection("offers")
            .snapshots()
            .map { snap ->
                snap.toObjects(OfferListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
    }

    suspend fun createFreeOffer(
        user: User,
        title: String,
        category: String,
        description: String,
        locationCity: String,
        priceOrSalary: String,
        offerType: String = if (category.equals("Vente", ignoreCase = true)) "product" else "service",
        condition: String = "",
        contactInfo: String = "",
        imagePayloads: List<Pair<ByteArray, String>> = emptyList()
    ): Result<String> {
        val textVerdict = ContentModerationGuard.checkText("$title $description $condition $contactInfo")
        if (!textVerdict.allowed) {
            return Result.failure(ContentBlockedException(textVerdict.reasonCode, textVerdict.userMessage))
        }
        val cleanPayloads = imagePayloads.filter { it.first.isNotEmpty() }.take(6)
        cleanPayloads.forEach { (bytes, mime) ->
            if (mime.startsWith("image/")) {
                val imgVerdict = ContentModerationGuard.checkImageBytes(bytes)
                if (!imgVerdict.allowed) {
                    return Result.failure(ContentBlockedException(imgVerdict.reasonCode, imgVerdict.userMessage))
                }
            }
        }

        val offerId = "off_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val uploadedUrls = mutableListOf<String>()
        if (cleanPayloads.isNotEmpty()) {
            val client = workerApiClient
                ?: return Result.failure(IllegalStateException("Service média indisponible pour importer les photos."))
            for ((index, pair) in cleanPayloads.withIndex()) {
                val ext = if (pair.second.contains("png")) "png" else "jpg"
                val fileName = "off_${offerId}_${index}_${System.currentTimeMillis()}.$ext"
                val upRes = client.uploadMedia(
                    conversationId = "offers_${user.userId}",
                    bytes = pair.first,
                    mimeType = pair.second,
                    fileName = fileName
                )
                val uploadedUrl = upRes.getOrElse { err ->
                    // Cleanup already uploaded partial files if one fails
                    uploadedUrls.forEach { deleteR2MediaIfManaged(it) }
                    return Result.failure(err)
                }.url
                if (uploadedUrl.isNotBlank()) uploadedUrls.add(uploadedUrl)
            }
        }

        val ref = db.collection("offers").document(offerId)
        val normalizedType = if (offerType == "product") "product" else "service"
        return try {
            ref.set(
                mapOf(
                    "userId" to user.userId,
                    "authorName" to user.displayName.take(120),
                    "authorAvatar" to user.avatarUrl.take(1000),
                    "title" to title.trim().take(160),
                    "offerType" to normalizedType,
                    "category" to category.trim().take(80),
                    "description" to description.trim().take(2000),
                    "condition" to condition.trim().take(80),
                    "locationCity" to locationCity.trim().take(120),
                    "priceOrSalary" to priceOrSalary.trim().take(80),
                    "contactInfo" to contactInfo.trim().take(180),
                    "imageUrl" to uploadedUrls.firstOrNull().orEmpty().take(1000),
                    "imageUrls" to uploadedUrls,
                    "status" to "published",
                    "paymentStatus" to "free",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            workerApiClient?.syncOfferToD1(
                offerId = offerId,
                userId = user.userId,
                title = title.trim().take(160),
                category = category.trim().take(80),
                description = description.trim().take(2000),
                city = locationCity.trim().take(120),
                priceOrSalary = priceOrSalary.trim().take(80),
                authorName = user.displayName.take(120)
            )
            runCatching {
                notificationRepository?.emitNotification(
                    targetUid = user.userId,
                    category = NotificationCategory.OFFERS_JOBS,
                    type = "offer_published",
                    title = "Annonce publiée dans Offres et emploi",
                    body = "« ${title.trim().take(100)} » est maintenant visible gratuitement sur BRAV Chat.",
                    refId = offerId,
                    refType = "offer",
                    dedupKey = "offer_pub_${offerId}",
                    senderUid = user.userId,
                    senderName = user.displayName
                )
            }
            Result.success(offerId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun updateOffer(
        offerId: String,
        user: User,
        offerType: String,
        title: String,
        category: String,
        description: String,
        condition: String,
        locationCity: String,
        priceOrSalary: String,
        contactInfo: String,
        keptExistingUrls: List<String>,
        newImagePayloads: List<Pair<ByteArray, String>>,
        removedUrls: List<String>
    ): Result<Unit> {
        val textVerdict = ContentModerationGuard.checkText("$title $description $condition $contactInfo")
        if (!textVerdict.allowed) {
            return Result.failure(ContentBlockedException(textVerdict.reasonCode, textVerdict.userMessage))
        }
        val cleanPayloads = newImagePayloads.filter { it.first.isNotEmpty() }.take((6 - keptExistingUrls.size).coerceAtLeast(0))
        cleanPayloads.forEach { (bytes, mime) ->
            if (mime.startsWith("image/")) {
                val imgVerdict = ContentModerationGuard.checkImageBytes(bytes)
                if (!imgVerdict.allowed) {
                    return Result.failure(ContentBlockedException(imgVerdict.reasonCode, imgVerdict.userMessage))
                }
            }
        }

        val uploadedNewUrls = mutableListOf<String>()
        if (cleanPayloads.isNotEmpty()) {
            val client = workerApiClient
                ?: return Result.failure(IllegalStateException("Service média indisponible pour importer les photos."))
            for ((index, pair) in cleanPayloads.withIndex()) {
                val ext = if (pair.second.contains("png")) "png" else "jpg"
                val fileName = "off_${offerId}_u${index}_${System.currentTimeMillis()}.$ext"
                val upRes = client.uploadMedia(
                    conversationId = "offers_${user.userId}",
                    bytes = pair.first,
                    mimeType = pair.second,
                    fileName = fileName
                )
                val uploadedUrl = upRes.getOrElse { err ->
                    uploadedNewUrls.forEach { deleteR2MediaIfManaged(it) }
                    return Result.failure(err)
                }.url
                if (uploadedUrl.isNotBlank()) uploadedNewUrls.add(uploadedUrl)
            }
        }

        val finalUrls = (keptExistingUrls + uploadedNewUrls).filter { it.isNotBlank() }.distinct().take(6)
        val ref = db.collection("offers").document(offerId)
        val normalizedType = if (offerType == "product") "product" else "service"
        return try {
            ref.update(
                mapOf(
                    "title" to title.trim().take(160),
                    "offerType" to normalizedType,
                    "category" to category.trim().take(80),
                    "description" to description.trim().take(2000),
                    "condition" to condition.trim().take(80),
                    "locationCity" to locationCity.trim().take(120),
                    "priceOrSalary" to priceOrSalary.trim().take(80),
                    "contactInfo" to contactInfo.trim().take(180),
                    "authorName" to user.displayName.take(120),
                    "authorAvatar" to user.avatarUrl.take(1000),
                    "imageUrl" to finalUrls.firstOrNull().orEmpty().take(1000),
                    "imageUrls" to finalUrls,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            removedUrls.forEach { old ->
                if (old !in finalUrls) deleteR2MediaIfManaged(old)
            }
            workerApiClient?.syncOfferToD1(
                offerId = offerId,
                userId = user.userId,
                title = title.trim().take(160),
                category = category.trim().take(80),
                description = description.trim().take(2000),
                city = locationCity.trim().take(120),
                priceOrSalary = priceOrSalary.trim().take(80),
                authorName = user.displayName.take(120)
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteOffer(offerId: String): Result<Unit> {
        val ref = db.collection("offers").document(offerId)
        return try {
            val snap = runCatching { ref.get().await() }.getOrNull()
            val primaryImg = snap?.getString("imageUrl").orEmpty()
            @Suppress("UNCHECKED_CAST")
            val multiImgs = (snap?.get("imageUrls") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            ref.delete().await()
            (listOf(primaryImg) + multiImgs).filter { it.isNotBlank() }.distinct().forEach { url ->
                deleteR2MediaIfManaged(url)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    private suspend fun deleteR2MediaIfManaged(mediaUrl: String) {
        val idx = mediaUrl.indexOf("/v1/media/c/")
        if (idx < 0) return
        val tail = mediaUrl.substring(idx + "/v1/media/c/".length).substringBefore('?')
        val convId = tail.substringBefore('/', "")
        val fileName = tail.substringAfter('/', "")
        if (convId.isNotBlank() && fileName.isNotBlank()) {
            runCatching { workerApiClient?.deleteMedia(convId, fileName) }
        }
    }

    // =========================================================================
    // MODERATION / REPORTS / ADMIN MONITORING
    // =========================================================================
    suspend fun submitReport(
        reporterId: String,
        targetUid: String = "",
        conversationId: String = "",
        messageId: String = "",
        reason: String,
        details: String
    ): Result<Unit> {
        val id = "rep_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("reports").document(id)
        return try {
            ref.set(
                mapOf(
                    "reporterId" to reporterId,
                    "targetUid" to targetUid.take(128),
                    "conversationId" to conversationId.take(128),
                    "messageId" to messageId.take(128),
                    "reason" to reason.trim().take(120),
                    "details" to details.trim().take(1000),
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    fun observeAllReportsForAdmin(): Flow<List<AbuseReport>> {
        return db.collection("reports")
            .snapshots()
            .map { snap ->
                snap.toObjects(AbuseReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
    }

    suspend fun adminUpdateReportStatus(reportId: String, status: String): Result<Unit> {
        val ref = db.collection("reports").document(reportId)
        return try {
            ref.update("status", status).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun adminToggleUserSuspension(targetUid: String, suspended: Boolean): Result<Unit> {
        val ref = db.collection("users").document(targetUid)
        return try {
            ref.update(
                mapOf(
                    "isSuspended" to suspended,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // BRAV CALL ID — SIGNALEMENTS COMMUNAUTAIRES & MODERATION
    // =========================================================================
    suspend fun submitCallerReport(
        reporterId: String,
        rawNumber: String,
        category: CallerReportCategory,
        label: String,
        details: String = "",
        defaultCountryIso: String = "CI"
    ): Result<String> {
        val normalized = PhoneUtils.normalize(rawNumber, defaultCountryIso)
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("Numéro de téléphone invalide"))
        }
        val hash = sha256Hex(normalized)
        val masked = PhoneUtils.maskPhone(normalized)
        val reportId = "cid_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val ref = db.collection("caller_reports").document(reportId)
        return try {
            ref.set(
                mapOf(
                    "reporterId" to reporterId,
                    "phoneLookupHash" to hash,
                    "maskedNumber" to masked,
                    "category" to category.raw,
                    "label" to label.trim().take(80),
                    "details" to details.trim().take(600),
                    "status" to "pending",
                    "reportCount" to 1,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(reportId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    fun observeApprovedCallerReports(): Flow<List<CallerReport>> = callbackFlow {
        val reg = db.collection("caller_reports")
            .whereEqualTo("status", "approved")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(CallerReport::class.java)?.copy(id = doc.id)
                }.orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeCallerDirectory(): Flow<List<CallerDirectoryEntry>> = callbackFlow {
        val reg = db.collection("caller_directory")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(CallerDirectoryEntry::class.java)?.copy(id = doc.id)
                }.orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeAllCallerReportsForAdmin(): Flow<List<CallerReport>> = callbackFlow {
        val reg = db.collection("caller_reports")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(CallerReport::class.java)?.copy(id = doc.id)
                }.orEmpty().sortedWith(
                    compareBy<CallerReport> {
                        when (it.status) {
                            "pending" -> 0
                            "approved" -> 1
                            else -> 2
                        }
                    }.thenByDescending { it.createdAt?.seconds ?: 0L }
                )
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun lookupApprovedCallerReports(
        rawNumber: String,
        defaultCountryIso: String = "CI"
    ): List<CallerReport> {
        val norm = PhoneUtils.normalize(rawNumber, defaultCountryIso)
        if (norm.isBlank()) return emptyList()
        val hash = sha256Hex(norm)
        return runCatching {
            val snap = db.collection("caller_reports")
                .whereEqualTo("status", "approved")
                .whereEqualTo("phoneLookupHash", hash)
                .get()
                .await()
            snap.documents.mapNotNull { doc ->
                doc.toObject(CallerReport::class.java)?.copy(id = doc.id)
            }
        }.getOrDefault(emptyList())
    }

    suspend fun adminModerateCallerReport(
        reportId: String,
        status: String,
        category: CallerReportCategory? = null,
        label: String? = null
    ): Result<Unit> {
        val ref = db.collection("caller_reports").document(reportId)
        val updates = hashMapOf<String, Any>(
            "status" to status,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (category != null) updates["category"] = category.raw
        if (!label.isNullOrBlank()) updates["label"] = label.trim().take(80)
        return try {
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
