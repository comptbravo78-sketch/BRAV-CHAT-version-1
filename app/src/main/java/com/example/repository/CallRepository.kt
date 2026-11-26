package com.example.repository

import android.content.Context
import com.example.R
import com.example.model.Call
import com.example.model.CallLifecycleState
import com.example.model.CallSource
import com.example.model.NotificationCategory
import com.example.network.WorkerApiClient
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CallRepository(
    private val db: FirebaseFirestore,
    private val workerApiClient: WorkerApiClient? = null,
    private val notificationRepository: NotificationRepository? = null
) {
    constructor(context: Context, workerApiClient: WorkerApiClient? = null) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        workerApiClient = workerApiClient
    )

    fun observeCallHistory(userId: String): Flow<List<Call>> {
        return db.collection("calls")
            .whereArrayContains("participantIds", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(Call::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { userId !in it.hiddenForUsers }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
    }

    fun observeIncomingCalls(userId: String): Flow<List<Call>> {
        return db.collection("calls")
            .whereEqualTo("receiverId", userId)
            .snapshots()
            .map { snap ->
                val cutoffMs = System.currentTimeMillis() - 180_000L
                snap.toObjects(Call::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { call ->
                        if (call.status != "calling") return@filter false
                        val ts = call.createdAt ?: return@filter true
                        (ts.seconds * 1000L + ts.nanoseconds / 1_000_000L) >= cutoffMs
                    }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
    }

    fun observeCall(callId: String): Flow<Call?> {
        return db.collection("calls").document(callId)
            .snapshots()
            .map { snap ->
                snap.toObject(Call::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    suspend fun getCall(callId: String): Call? {
        return runCatching {
            db.collection("calls").document(callId).get().await()
                .toObject(Call::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }.getOrNull()
    }

    suspend fun startCall(
        callerId: String,
        callerName: String,
        receiverId: String,
        receiverName: String,
        callType: String,
        callerAvatar: String = "",
        callerUsername: String = "",
        receiverAvatar: String = "",
        receiverUsername: String = ""
    ): Result<Call> {
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val ref = db.collection("calls").document(callId)
        val normalizedType = if (callType == "video") "video" else "audio"
        return try {
            var resolvedCallerAvatar = callerAvatar
            var resolvedCallerUsername = callerUsername
            var resolvedReceiverAvatar = receiverAvatar
            var resolvedReceiverUsername = receiverUsername
            if (resolvedCallerAvatar.isBlank() || resolvedCallerUsername.isBlank()) {
                val cSnap = runCatching { db.collection("users").document(callerId).get().await() }.getOrNull()
                if (resolvedCallerAvatar.isBlank()) resolvedCallerAvatar = cSnap?.getString("avatarUrl").orEmpty()
                if (resolvedCallerUsername.isBlank()) resolvedCallerUsername = cSnap?.getString("username").orEmpty()
            }
            if (resolvedReceiverAvatar.isBlank() || resolvedReceiverUsername.isBlank()) {
                val rSnap = runCatching { db.collection("users").document(receiverId).get().await() }.getOrNull()
                if (resolvedReceiverAvatar.isBlank()) resolvedReceiverAvatar = rSnap?.getString("avatarUrl").orEmpty()
                if (resolvedReceiverUsername.isBlank()) resolvedReceiverUsername = rSnap?.getString("username").orEmpty()
            }

            val payload = hashMapOf<String, Any>(
                "callerId" to callerId,
                "callerName" to callerName.take(120),
                "callerAvatar" to resolvedCallerAvatar.take(1000),
                "callerUsername" to resolvedCallerUsername.take(64),
                "receiverId" to receiverId,
                "receiverName" to receiverName.take(120),
                "receiverAvatar" to resolvedReceiverAvatar.take(1000),
                "receiverUsername" to resolvedReceiverUsername.take(64),
                "participantIds" to listOf(callerId, receiverId),
                "callType" to normalizedType,
                "callSource" to CallSource.BRAV_INTERNET.raw,
                "status" to "calling",
                "lifecycleState" to CallLifecycleState.RINGING.raw,
                "heldByUid" to "",
                "missed" to false,
                "durationSeconds" to 0L,
                "createdAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            workerApiClient?.let { client ->
                runCatching {
                    client.ringCall(
                        callId = callId,
                        receiverId = receiverId,
                        callerName = callerName.take(120),
                        callType = normalizedType
                    )
                }
            }
            val created = ref.get().await()
                .toObject(Call::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: Call(
                    id = callId,
                    callerId = callerId,
                    callerName = callerName,
                    callerAvatar = resolvedCallerAvatar,
                    callerUsername = resolvedCallerUsername,
                    receiverId = receiverId,
                    receiverName = receiverName,
                    receiverAvatar = resolvedReceiverAvatar,
                    receiverUsername = resolvedReceiverUsername,
                    participantIds = listOf(callerId, receiverId),
                    callType = normalizedType,
                    callSource = CallSource.BRAV_INTERNET.raw,
                    status = "calling",
                    lifecycleState = CallLifecycleState.RINGING.raw
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(
        callId: String,
        status: String,
        missed: Boolean = false,
        durationSeconds: Long = 0L,
        lifecycleState: CallLifecycleState? = null
    ): Result<Unit> {
        val ref = db.collection("calls").document(callId)
        val resolvedLifecycle = lifecycleState ?: CallLifecycleState.fromRaw(null, status, missed)
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to status,
                "lifecycleState" to resolvedLifecycle.raw,
                "missed" to missed,
                "durationSeconds" to durationSeconds
            )
            if (status != "on_hold") {
                updates["heldByUid"] = ""
            }
            if (status == "ended" || status == "rejected") {
                updates["endedAt"] = FieldValue.serverTimestamp()
            }
            ref.update(updates).await()
            if (missed && status == "ended") {
                runCatching {
                    val snap = ref.get().await()
                    val callerId = snap.getString("callerId").orEmpty()
                    val callerName = snap.getString("callerName").orEmpty().ifBlank { "Membre BRAV" }
                    val callerAvatar = snap.getString("callerAvatar").orEmpty()
                    val receiverId = snap.getString("receiverId").orEmpty()
                    val callType = snap.getString("callType").orEmpty()
                    if (receiverId.isNotBlank() && callerId.isNotBlank()) {
                        notificationRepository?.emitNotification(
                            targetUid = receiverId,
                            category = NotificationCategory.CALLS,
                            type = "missed_call",
                            title = if (callType == "video") "Appel vidéo manqué" else "Appel audio manqué",
                            body = "$callerName a essayé de vous appeler.",
                            refId = callId,
                            refType = "call",
                            dedupKey = "missed_call_${callId}",
                            senderUid = callerId,
                            senderName = callerName,
                            senderAvatar = callerAvatar,
                            priority = "high"
                        )
                    }
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

    suspend fun setCallHold(callId: String, isHeld: Boolean, heldByUid: String): Result<Unit> {
        val ref = db.collection("calls").document(callId)
        return try {
            ref.update(
                mapOf(
                    "status" to if (isHeld) "on_hold" else "accepted",
                    "lifecycleState" to if (isHeld) CallLifecycleState.ON_HOLD.raw else CallLifecycleState.ACTIVE.raw,
                    "heldByUid" to if (isHeld) heldByUid else ""
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

    suspend fun deleteCallFromHistory(callId: String, userId: String): Result<Unit> {
        val ref = db.collection("calls").document(callId)
        return try {
            ref.update("hiddenForUsers", FieldValue.arrayUnion(userId)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun clearCallHistory(userId: String, callIds: List<String>): Result<Unit> {
        if (callIds.isEmpty()) return Result.success(Unit)
        return try {
            callIds.distinct().chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { callId ->
                    val ref = db.collection("calls").document(callId)
                    batch.update(ref, "hiddenForUsers", FieldValue.arrayUnion(userId))
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "calls/batch_hide")
            Result.failure(e)
        }
    }

    data class CallSignalPacket(
        val id: String = "",
        val senderId: String = "",
        val type: String = "",
        val payload: String = ""
    )

    suspend fun sendCallSignal(
        callId: String,
        senderId: String,
        type: String,
        payloadJson: String
    ): Result<Unit> {
        val sigId = "sig_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val ref = db.collection("calls").document(callId).collection("signals").document(sigId)
        return try {
            ref.set(
                mapOf(
                    "senderId" to senderId,
                    "type" to type,
                    "payload" to payloadJson.take(32000),
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

    fun observeCallSignals(callId: String, myUid: String): Flow<List<CallSignalPacket>> {
        return db.collection("calls").document(callId)
            .collection("signals")
            .orderBy("createdAt")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    val sender = doc.getString("senderId").orEmpty()
                    if (sender.isBlank() || sender == myUid) return@mapNotNull null
                    CallSignalPacket(
                        id = doc.id,
                        senderId = sender,
                        type = doc.getString("type").orEmpty(),
                        payload = doc.getString("payload").orEmpty()
                    )
                }
            }
    }
}
