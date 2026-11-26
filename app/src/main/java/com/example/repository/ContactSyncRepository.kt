package com.example.repository

import android.content.Context
import android.provider.ContactsContract
import com.example.R
import com.example.model.Contact
import com.example.model.ContactRequest
import com.example.model.NotificationCategory
import com.example.model.User
import com.example.network.WorkerApiClient
import com.example.util.OperationType
import com.example.util.PhoneUtils
import com.example.util.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ContactSyncRepository(
    private val db: FirebaseFirestore,
    private val workerApiClient: WorkerApiClient? = null,
    private val appContext: Context? = null,
    private val notificationRepository: NotificationRepository? = null
) {
    constructor(context: Context, workerApiClient: WorkerApiClient? = null) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        workerApiClient = workerApiClient,
        appContext = context.applicationContext
    )

    fun observeSavedContactUids(myUid: String): Flow<List<Pair<String, Timestamp?>>> {
        return db.collection("users").document(myUid).collection("contacts")
            .snapshots()
            .map { snap ->
                snap.documents.map { doc ->
                    doc.id to doc.getTimestamp("addedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.second ?: Timestamp(0, 0) }
            }
    }

    fun observeIncomingRequests(myUid: String): Flow<List<ContactRequest>> {
        return db.collection("contact_requests")
            .whereEqualTo("toUid", myUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(ContactRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.status == "pending" }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
    }

    fun observeOutgoingRequests(myUid: String): Flow<List<ContactRequest>> {
        return db.collection("contact_requests")
            .whereEqualTo("fromUid", myUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(ContactRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.status == "pending" }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
    }

    suspend fun fetchUsersByIds(uids: List<String>): List<User> {
        if (uids.isEmpty()) return emptyList()
        val results = mutableListOf<User>()
        for (chunk in uids.distinct().chunked(10)) {
            runCatching {
                val snap = db.collection("users").whereIn("userId", chunk).get().await()
                results.addAll(snap.toObjects(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
            }
        }
        return results
    }

    /**
     * Real smart search by:
     * 1. Exact phone number (SHA-256 lookup hash)
     * 2. Exact email (SHA-256 lookup hash)
     * 3. Exact @username or prefix
     * 4. Display name / city search via Firestore & Cloudflare Worker /v1/search
     */
    suspend fun searchUsers(query: String, myUid: String): List<User> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val found = linkedMapOf<String, User>()

        // 1. Phone hash lookup
        val normalizedPhone = PhoneUtils.normalize(q)
        if (normalizedPhone.count { it.isDigit() } >= 6) {
            val phoneHash = PhoneUtils.sha256LookupHash(normalizedPhone)
            runCatching {
                val snap = db.collection("users")
                    .whereEqualTo("phoneLookupHash", phoneHash)
                    .limit(10)
                    .get()
                    .await()
                snap.toObjects(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .forEach { found[it.userId] = it }
            }
        }

        // 2. Email hash lookup
        if (q.contains("@") && q.contains(".")) {
            val emailHash = PhoneUtils.sha256LookupHash(q)
            runCatching {
                val snap = db.collection("users")
                    .whereEqualTo("emailLookupHash", emailHash)
                    .limit(10)
                    .get()
                    .await()
                snap.toObjects(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .forEach { found[it.userId] = it }
            }
        }

        // 3. @username lookup
        val cleanUsername = q.removePrefix("@").lowercase()
        if (cleanUsername.length >= 2) {
            runCatching {
                val snap = db.collection("users")
                    .whereGreaterThanOrEqualTo("username", cleanUsername)
                    .whereLessThanOrEqualTo("username", "$cleanUsername\uf8ff")
                    .limit(15)
                    .get()
                    .await()
                snap.toObjects(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .forEach { found[it.userId] = it }
            }
        }

        // 4. Worker full-text user search (/v1/search?type=users)
        if (workerApiClient != null) {
            workerApiClient.search(query = q, type = "users", limit = 20).getOrNull()?.users?.forEach { map ->
                val uid = map["userId"]?.toString() ?: map["id"]?.toString() ?: ""
                if (uid.isNotBlank() && uid !in found) {
                    val user = getUserSafe(uid)
                    if (user != null) found[uid] = user
                }
            }
        }

        // 5. Fallback Firestore directory prefix/substring match (authenticated users only)
        if (found.size < 10) {
            runCatching {
                val snap = db.collection("users").limit(60).get().await()
                snap.toObjects(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter {
                        it.displayName.contains(q, ignoreCase = true) ||
                            it.username.contains(cleanUsername, ignoreCase = true) ||
                            it.locationCity.contains(q, ignoreCase = true)
                    }
                    .forEach { found[it.userId] = it }
            }
        }

        found.remove(myUid)
        return found.values.toList()
    }

    private suspend fun getUserSafe(uid: String): User? {
        return runCatching {
            db.collection("users").document(uid).get().await()
                .toObject(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }.getOrNull()
    }

    fun observeDeviceContactsChanges(): Flow<Long> = kotlinx.coroutines.flow.callbackFlow {
        val ctx = appContext
        if (ctx == null) {
            trySend(0L)
            close()
            return@callbackFlow
        }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val observer = object : android.database.ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(System.currentTimeMillis())
            }
        }
        runCatching {
            ctx.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                observer
            )
        }
        trySend(System.currentTimeMillis())
        awaitClose {
            runCatching { ctx.contentResolver.unregisterContentObserver(observer) }
        }
    }

    /**
     * Reads real device contacts (only when READ_CONTACTS permission is granted) including photos/emails,
     * resolves phone locality, and matches them securely against BRAV Chat users using phoneLookupHash.
     */
    suspend fun syncPhoneContacts(myUid: String): List<Contact> = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext emptyList()
        data class RawDeviceContact(
            val contactId: Long,
            val name: String,
            val raw: String,
            val norm: String,
            val photoUri: String
        )
        val rawContacts = mutableListOf<RawDeviceContact>()
        val seenNormalized = mutableSetOf<String>()

        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val cidIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                while (cursor.moveToNext()) {
                    val cid = if (cidIdx >= 0) cursor.getLong(cidIdx) else 0L
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                    val raw = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""
                    val photo = if (photoIdx >= 0) cursor.getString(photoIdx).orEmpty() else ""
                    val norm = PhoneUtils.normalize(raw)
                    if (norm.isNotBlank() && seenNormalized.add(norm)) {
                        rawContacts.add(RawDeviceContact(cid, name, raw, norm, photo))
                    }
                }
            }
        } catch (e: SecurityException) {
            return@withContext emptyList()
        }

        if (rawContacts.isEmpty()) return@withContext emptyList()

        // Read contact emails map best-effort
        val emailsByContactId = mutableMapOf<Long, String>()
        runCatching {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.ADDRESS
                ),
                null,
                null,
                null
            )?.use { c ->
                val cidIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                val addrIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                while (c.moveToNext()) {
                    val cid = if (cidIdx >= 0) c.getLong(cidIdx) else 0L
                    val addr = if (addrIdx >= 0) c.getString(addrIdx).orEmpty() else ""
                    if (cid > 0L && addr.isNotBlank() && cid !in emailsByContactId) {
                        emailsByContactId[cid] = addr
                    }
                }
            }
        }

        val hashToUser = mutableMapOf<String, User>()
        val hashes = rawContacts.map { PhoneUtils.sha256LookupHash(it.norm) }.distinct()
        for (chunk in hashes.chunked(10)) {
            runCatching {
                val snap = db.collection("users").whereIn("phoneLookupHash", chunk).get().await()
                snap.toObjects(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.userId != myUid }
                    .forEach { u ->
                        if (u.phoneLookupHash.isNotBlank()) hashToUser[u.phoneLookupHash] = u
                    }
            }
        }

        rawContacts.map { item ->
            val hash = PhoneUtils.sha256LookupHash(item.norm)
            val matchedUser = hashToUser[hash]
            val locality = com.example.util.PhoneLocalityResolver.resolve(item.norm).compactBadge
            Contact(
                id = matchedUser?.userId ?: item.norm,
                phoneDisplayName = item.name,
                rawPhoneNumber = item.raw,
                normalizedPhoneNumber = item.norm,
                bravUser = matchedUser,
                phonePhotoUri = item.photoUri,
                phoneEmail = emailsByContactId[item.contactId].orEmpty(),
                localityHint = locality
            )
        }.sortedWith(compareByDescending<Contact> { it.isBravUser }.thenBy { it.preferredDisplayName })
    }

    suspend fun sendContactRequest(fromUser: User, toUser: User): Result<Unit> {
        val requestId = "${fromUser.userId}_${toUser.userId}".replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val ref = db.collection("contact_requests").document(requestId)
        return try {
            ref.set(
                mapOf(
                    "fromUid" to fromUser.userId,
                    "fromName" to fromUser.displayName.take(120),
                    "fromUsername" to fromUser.username.take(64),
                    "toUid" to toUser.userId,
                    "toName" to toUser.displayName.take(120),
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching {
                notificationRepository?.emitNotification(
                    targetUid = toUser.userId,
                    category = NotificationCategory.CONTACTS,
                    type = "contact_request",
                    title = "Nouvelle demande de contact",
                    body = "${fromUser.displayName.ifBlank { "Un membre BRAV" }} souhaite se connecter avec vous.",
                    refId = fromUser.userId,
                    refType = "contact_request",
                    subRefId = requestId,
                    dedupKey = "creq_${requestId}",
                    senderUid = fromUser.userId,
                    senderName = fromUser.displayName,
                    senderAvatar = fromUser.avatarUrl,
                    priority = "high"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun acceptContactRequest(
        request: ContactRequest,
        myUid: String,
        myDisplayName: String = ""
    ): Result<Unit> {
        val reqRef = db.collection("contact_requests").document(request.id)
        val myContactRef = db.collection("users").document(myUid).collection("contacts").document(request.fromUid)
        return try {
            reqRef.update(
                mapOf(
                    "status" to "accepted",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            myContactRef.set(
                mapOf(
                    "contactUid" to request.fromUid,
                    "alias" to request.fromName.take(100),
                    "addedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val accepterLabel = myDisplayName.ifBlank { request.toName }.ifBlank { "Votre contact" }
            runCatching {
                notificationRepository?.emitNotification(
                    targetUid = request.fromUid,
                    category = NotificationCategory.CONTACTS,
                    type = "contact_accepted",
                    title = "Invitation acceptée",
                    body = "$accepterLabel a accepté votre demande de contact.",
                    refId = myUid,
                    refType = "contact_accepted",
                    subRefId = request.id,
                    dedupKey = "cacc_${request.id}",
                    senderUid = myUid,
                    senderName = accepterLabel,
                    priority = "normal"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reqRef.path)
            Result.failure(e)
        }
    }

    suspend fun declineContactRequest(request: ContactRequest): Result<Unit> {
        val reqRef = db.collection("contact_requests").document(request.id)
        return try {
            reqRef.update(
                mapOf(
                    "status" to "declined",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reqRef.path)
            Result.failure(e)
        }
    }

    suspend fun addContactDirectly(myUid: String, targetUser: User): Result<Unit> {
        val ref = db.collection("users").document(myUid).collection("contacts").document(targetUser.userId)
        return try {
            ref.set(
                mapOf(
                    "contactUid" to targetUser.userId,
                    "alias" to targetUser.displayName.take(100),
                    "addedAt" to FieldValue.serverTimestamp()
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

    suspend fun removeContact(myUid: String, contactUid: String): Result<Unit> {
        val ref = db.collection("users").document(myUid).collection("contacts").document(contactUid)
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
}
