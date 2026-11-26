package com.example.repository

import android.content.Context
import android.os.Build
import com.example.R
import com.example.crypto.E2eeCryptoManager
import com.example.model.BlockedEntry
import com.example.model.CertificationEligibility
import com.example.model.CertificationPaymentReceipt
import com.example.model.FollowRelationState
import com.example.model.FollowRequest
import com.example.model.InviteLink
import com.example.model.NotificationCategory
import com.example.model.User
import com.example.model.UserDevice
import com.example.model.UserPrivateInfo
import com.example.model.VerificationCategory
import com.example.model.VerificationRequest
import com.example.model.VerificationStatus
import com.example.network.WorkerApiClient
import com.example.network.WorkerPaymentInitiateResult
import com.example.security.BravHumanVerificationService
import com.example.util.OperationType
import com.example.util.PhoneUtils
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class UserRepository(
    private val db: FirebaseFirestore,
    private val cryptoManager: E2eeCryptoManager? = null,
    private val workerApiClient: WorkerApiClient? = null,
    private val notificationRepository: NotificationRepository? = null
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        cryptoManager = E2eeCryptoManager(context.applicationContext)
    )

    suspend fun ensureUserProfileExists(
        firebaseUser: FirebaseUser,
        deviceId: String? = null
    ): Result<User> {
        val uid = firebaseUser.uid
        val userRef = db.collection("users").document(uid)
        val privateRef = userRef.collection("private").document("info")
        val pubKey = cryptoManager?.getPublicKeyBase64(uid).orEmpty()

        return try {
            val snap = userRef.get().await()
            val email = firebaseUser.email.orEmpty()
            val phone = firebaseUser.phoneNumber.orEmpty()
            val displayName = firebaseUser.displayName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").takeIf { it.isNotBlank() }
                ?: "Membre BRAV"

            val isDesignerExempt = firebaseUser.isEmailVerified &&
                email.trim().equals(CertificationEligibility.DESIGNER_EXEMPT_EMAIL, ignoreCase = true)

            val existing = snap.toObject(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (!snap.exists() || existing == null) {
                val baseUsername = sanitizeUsername(displayName) + "_" + uid.take(4).lowercase()
                val newUserMap = hashMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to displayName.take(100),
                    "username" to baseUsername.take(40),
                    "phoneLookupHash" to if (phone.isNotBlank()) PhoneUtils.sha256LookupHash(PhoneUtils.normalize(phone)) else "",
                    "emailLookupHash" to if (email.isNotBlank()) PhoneUtils.sha256LookupHash(email) else "",
                    "phoneMasked" to if (phone.isNotBlank()) PhoneUtils.maskPhone(phone) else "",
                    "bio" to "Disponible sur BRAV Chat",
                    "locationCity" to "",
                    "locationDetails" to "",
                    "avatarUrl" to (firebaseUser.photoUrl?.toString().orEmpty()),
                    "coverUrl" to "",
                    "availabilityStatus" to "online",
                    "isOnline" to true,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "publicKey" to pubKey,
                    "privacySettings" to mapOf(
                        "showPhoto" to "everyone",
                        "showCover" to "everyone",
                        "showStatus" to "everyone",
                        "showLastSeen" to "everyone",
                        "whoCanAddToGroup" to "everyone",
                        "whoCanCall" to "everyone",
                        "whoCanRequest" to "everyone",
                        "showInfo" to "everyone",
                        "showCity" to "everyone",
                        "showBirthDate" to "contacts",
                        "showAddress" to "nobody",
                        "showPhone" to "contacts",
                        "showEmail" to "nobody",
                        "showPosts" to "everyone"
                    ),
                    "role" to "user",
                    "isSuspended" to false,
                    "followersCount" to 0,
                    "followingCount" to 0,
                    "verificationStatus" to if (isDesignerExempt) VerificationStatus.VERIFIED.raw else VerificationStatus.NONE.raw,
                    "verificationCategory" to if (isDesignerExempt) VerificationCategory.CREATOR.raw else "",
                    "verifiedOrgName" to if (isDesignerExempt) "Concepteur BRAV CHAT" else "",
                    "verificationExpiresAtMs" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.set(newUserMap).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "isOnline" to (existing.availabilityStatus != "invisible" && existing.availabilityStatus != "offline"),
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (pubKey.isNotBlank() && existing.publicKey != pubKey) {
                    updates["publicKey"] = pubKey
                }
                if (existing.emailLookupHash.isBlank() && email.isNotBlank()) {
                    updates["emailLookupHash"] = PhoneUtils.sha256LookupHash(email)
                }
                if (existing.avatarUrl.isBlank() && firebaseUser.photoUrl != null) {
                    updates["avatarUrl"] = firebaseUser.photoUrl.toString()
                }
                if (isDesignerExempt && (
                        !existing.isVerifiedActive() ||
                            existing.verificationExpiresAtMs != 0L ||
                            existing.verificationCategory != VerificationCategory.CREATOR.raw ||
                            existing.verifiedOrgName != "Concepteur BRAV CHAT"
                        )
                ) {
                    updates["verificationStatus"] = VerificationStatus.VERIFIED.raw
                    updates["verificationCategory"] = VerificationCategory.CREATOR.raw
                    updates["verifiedOrgName"] = "Concepteur BRAV CHAT"
                    updates["verificationExpiresAtMs"] = 0L
                }
                userRef.update(updates).await()
            }

            // Ensure private PII doc exists for owner only
            val privateSnap = privateRef.get().await()
            if (!privateSnap.exists()) {
                val privMap = hashMapOf<String, Any>(
                    "userId" to uid,
                    "email" to email,
                    "phoneNumber" to phone,
                    "birthDate" to "",
                    "homeAddress" to "",
                    "phoneVerified" to phone.isNotBlank(),
                    "securityPinHash" to "",
                    "encryptedBackupJson" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                privateRef.set(privMap).await()
            }

            if (!deviceId.isNullOrBlank()) {
                registerDeviceSession(uid, deviceId)
            }

            val updatedSnap = userRef.get().await()
            val finalUser = updatedSnap.toObject(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: User(id = uid, userId = uid, displayName = displayName)
            Result.success(finalUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            Result.failure(e)
        }
    }

    fun observeUser(uid: String): Flow<User?> {
        return db.collection("users").document(uid).snapshots().map { snap ->
            snap.toObject(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
    }

    fun observePrivateInfo(uid: String): Flow<UserPrivateInfo?> {
        return db.collection("users").document(uid)
            .collection("private").document("info")
            .snapshots()
            .map { snap ->
                snap.toObject(UserPrivateInfo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    fun observeDevices(uid: String): Flow<List<UserDevice>> {
        return db.collection("users").document(uid)
            .collection("devices")
            .snapshots()
            .map { snap ->
                snap.toObjects(UserDevice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastActiveAt ?: Timestamp(0, 0) }
            }
    }

    fun observeBlockedUsers(uid: String): Flow<List<BlockedEntry>> {
        return db.collection("users").document(uid)
            .collection("blocked")
            .snapshots()
            .map { snap ->
                snap.toObjects(BlockedEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    suspend fun getUserById(uid: String): User? {
        return try {
            db.collection("users").document(uid).get().await()
                .toObject(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$uid")
            null
        }
    }

    suspend fun isUsernameAvailable(username: String, excludeUid: String): Boolean {
        val clean = sanitizeUsername(username)
        if (clean.length < 3) return false
        return try {
            val snap = db.collection("users")
                .whereEqualTo("username", clean)
                .limit(2)
                .get()
                .await()
            snap.documents.none { it.id != excludeUid }
        } catch (_: Exception) {
            true
        }
    }

    data class ProfileValidationResult(
        val isValid: Boolean,
        val fieldErrors: Map<String, String> = emptyMap()
    )

    fun validateProfileInput(
        displayName: String,
        username: String,
        bio: String,
        phoneNumber: String,
        email: String
    ): ProfileValidationResult {
        val errors = mutableMapOf<String, String>()
        val cleanName = displayName.trim()
        if (cleanName.length < 2) {
            errors["displayName"] = "Entrez au moins 2 caractères pour votre nom complet."
        } else if (cleanName.length > 100) {
            errors["displayName"] = "Le nom complet ne doit pas dépasser 100 caractères."
        }

        val cleanUser = sanitizeUsername(username)
        if (username.isNotBlank() && cleanUser.length < 3) {
            errors["username"] = "Le nom d'utilisateur doit contenir au moins 3 caractères."
        }

        if (bio.trim().length > 300) {
            errors["bio"] = "La biographie est limitée à 300 caractères."
        }

        val trimmedEmail = email.trim()
        if (trimmedEmail.isNotBlank() && !Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(trimmedEmail)) {
            errors["email"] = "Adresse e-mail invalide."
        }

        val trimmedPhone = phoneNumber.trim()
        if (trimmedPhone.isNotBlank()) {
            val digits = trimmedPhone.filter { it.isDigit() }
            if (digits.length < 7 || digits.length > 15) {
                errors["phone"] = "Numéro de téléphone invalide (7 à 15 chiffres attendus)."
            }
        }
        return ProfileValidationResult(isValid = errors.isEmpty(), fieldErrors = errors)
    }

    suspend fun updateProfile(
        uid: String,
        displayName: String,
        username: String,
        bio: String,
        locationCity: String,
        locationDetails: String,
        avatarUrl: String? = null,
        coverUrl: String? = null,
        phoneNumber: String? = null,
        email: String? = null,
        birthDate: String? = null,
        homeAddress: String? = null,
        privacySettings: Map<String, String>? = null
    ): Result<Unit> {
        val userRef = db.collection("users").document(uid)
        val privateRef = userRef.collection("private").document("info")
        return try {
            val validation = validateProfileInput(
                displayName = displayName,
                username = username,
                bio = bio,
                phoneNumber = phoneNumber.orEmpty(),
                email = email.orEmpty()
            )
            if (!validation.isValid) {
                val firstMsg = validation.fieldErrors.values.firstOrNull() ?: "Champs invalides."
                return Result.failure(IllegalArgumentException(firstMsg))
            }

            val cleanUsername = sanitizeUsername(username)
            if (cleanUsername.isNotBlank() && !isUsernameAvailable(cleanUsername, uid)) {
                return Result.failure(IllegalArgumentException("Ce nom d'utilisateur @$cleanUsername est déjà pris."))
            }

            val existingSnap = runCatching { userRef.get().await() }.getOrNull()
            // Preserve existing avatarUrl in D1 sync when caller did not explicitly supply a new avatarUrl
            val previousAvatarUrl = existingSnap?.getString("avatarUrl").orEmpty()
            val resolvedAvatarForSync = avatarUrl ?: previousAvatarUrl
            val previousCoverUrl = existingSnap?.getString("coverUrl").orEmpty()
            @Suppress("UNCHECKED_CAST")
            val activePrivacy = privacySettings
                ?: (existingSnap?.get("privacySettings") as? Map<String, String>)
                ?: emptyMap()

            val updates = mutableMapOf<String, Any>(
                "displayName" to displayName.trim().take(100),
                "username" to cleanUsername.take(40),
                "bio" to bio.trim().take(300),
                "locationCity" to locationCity.trim().take(120),
                "locationDetails" to locationDetails.trim().take(200),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            avatarUrl?.let { updates["avatarUrl"] = it }
            coverUrl?.let { updates["coverUrl"] = it }
            privacySettings?.let { updates["privacySettings"] = it }

            val privUpdates = mutableMapOf<String, Any>(
                "userId" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (phoneNumber != null) {
                val norm = PhoneUtils.normalize(phoneNumber)
                updates["phoneLookupHash"] = if (norm.isNotBlank()) PhoneUtils.sha256LookupHash(norm) else ""
                updates["phoneMasked"] = if (norm.isNotBlank()) PhoneUtils.maskPhone(norm) else ""
                updates["publicPhone"] = if ((activePrivacy["showPhone"] ?: "contacts") != "nobody") norm else ""
                privUpdates["phoneNumber"] = norm
            }
            if (email != null) {
                val trimmedEmail = email.trim().take(200)
                updates["emailLookupHash"] = if (trimmedEmail.isNotBlank()) PhoneUtils.sha256LookupHash(trimmedEmail) else ""
                updates["publicEmail"] = if ((activePrivacy["showEmail"] ?: "nobody") != "nobody") trimmedEmail else ""
                privUpdates["email"] = trimmedEmail
            }
            if (birthDate != null) {
                val cleanBirth = birthDate.trim().take(32)
                updates["publicBirthDate"] = if ((activePrivacy["showBirthDate"] ?: "contacts") != "nobody") cleanBirth else ""
                privUpdates["birthDate"] = cleanBirth
            }
            if (homeAddress != null) {
                val cleanAddr = homeAddress.trim().take(240)
                updates["publicAddress"] = if ((activePrivacy["showAddress"] ?: "nobody") != "nobody") cleanAddr else ""
                privUpdates["homeAddress"] = cleanAddr
            }

            userRef.update(updates).await()
            if (phoneNumber != null || email != null || birthDate != null || homeAddress != null) {
                privateRef.set(privUpdates, SetOptions.merge()).await()
            }
            if (avatarUrl != null && previousAvatarUrl.isNotBlank() && previousAvatarUrl != avatarUrl) {
                deleteR2MediaIfManaged(previousAvatarUrl)
            }
            if (coverUrl != null && previousCoverUrl.isNotBlank() && previousCoverUrl != coverUrl) {
                deleteR2MediaIfManaged(previousCoverUrl)
            }
            workerApiClient?.syncUserDirectoryToD1(
                userId = uid,
                displayName = displayName.trim().take(100),
                username = cleanUsername.take(40),
                city = locationCity.trim().take(120),
                avatarUrl = resolvedAvatarForSync
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun removeProfilePhoto(uid: String): Result<Unit> {
        val userRef = db.collection("users").document(uid)
        return try {
            val snap = userRef.get().await()
            val oldAvatar = snap.getString("avatarUrl").orEmpty()
            userRef.update(
                mapOf(
                    "avatarUrl" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (oldAvatar.isNotBlank()) {
                deleteR2MediaIfManaged(oldAvatar)
            }
            workerApiClient?.syncUserDirectoryToD1(
                userId = uid,
                displayName = snap.getString("displayName").orEmpty(),
                username = snap.getString("username").orEmpty(),
                city = snap.getString("locationCity").orEmpty(),
                avatarUrl = ""
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun removeCoverPhoto(uid: String): Result<Unit> {
        val userRef = db.collection("users").document(uid)
        return try {
            val snap = userRef.get().await()
            val oldCover = snap.getString("coverUrl").orEmpty()
            userRef.update(
                mapOf(
                    "coverUrl" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (oldCover.isNotBlank()) {
                deleteR2MediaIfManaged(oldCover)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
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

    suspend fun updateAvailabilityStatus(uid: String, status: String): Result<Unit> {
        val valid = status in setOf("online", "offline", "busy", "dnd", "invisible")
        if (!valid) return Result.failure(IllegalArgumentException("Statut invalide"))
        val userRef = db.collection("users").document(uid)
        return try {
            userRef.update(
                mapOf(
                    "availabilityStatus" to status,
                    "isOnline" to (status == "online" || status == "busy"),
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(uid: String, settings: Map<String, String>): Result<Unit> {
        val userRef = db.collection("users").document(uid)
        val privateRef = userRef.collection("private").document("info")
        return try {
            val privSnap = runCatching { privateRef.get().await() }.getOrNull()
            val updates = mutableMapOf<String, Any>(
                "privacySettings" to settings,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            updates["publicPhone"] = if ((settings["showPhone"] ?: "contacts") != "nobody") {
                privSnap?.getString("phoneNumber").orEmpty()
            } else ""
            updates["publicEmail"] = if ((settings["showEmail"] ?: "nobody") != "nobody") {
                privSnap?.getString("email").orEmpty()
            } else ""
            updates["publicBirthDate"] = if ((settings["showBirthDate"] ?: "contacts") != "nobody") {
                privSnap?.getString("birthDate").orEmpty()
            } else ""
            updates["publicAddress"] = if ((settings["showAddress"] ?: "nobody") != "nobody") {
                privSnap?.getString("homeAddress").orEmpty()
            } else ""
            userRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun registerDeviceSession(uid: String, deviceId: String): Result<Unit> {
        val cleanDeviceId = deviceId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(120)
        val devRef = db.collection("users").document(uid).collection("devices").document(cleanDeviceId)
        val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".take(100)
        return try {
            val snap = devRef.get().await()
            val payload = hashMapOf<String, Any>(
                "deviceId" to cleanDeviceId,
                "deviceName" to deviceName,
                "platform" to "android",
                "revoked" to false,
                "lastActiveAt" to FieldValue.serverTimestamp()
            )
            if (!snap.exists()) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            devRef.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, devRef.path)
            Result.failure(e)
        }
    }

    suspend fun revokeDeviceSession(uid: String, deviceId: String): Result<Unit> {
        val devRef = db.collection("users").document(uid).collection("devices").document(deviceId)
        return try {
            devRef.update(
                mapOf(
                    "revoked" to true,
                    "lastActiveAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, devRef.path)
            Result.failure(e)
        }
    }

    suspend fun blockUser(uid: String, targetUid: String, reason: String = ""): Result<Unit> {
        val blockRef = db.collection("users").document(uid).collection("blocked").document(targetUid)
        return try {
            blockRef.set(
                mapOf(
                    "blockedUid" to targetUid,
                    "reason" to reason.take(300),
                    "blockedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching { workerApiClient?.syncBlockUser(targetUid, reason) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, blockRef.path)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(uid: String, targetUid: String): Result<Unit> {
        val blockRef = db.collection("users").document(uid).collection("blocked").document(targetUid)
        return try {
            blockRef.delete().await()
            runCatching { workerApiClient?.syncUnblockUser(targetUid) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, blockRef.path)
            Result.failure(e)
        }
    }

    suspend fun exportEncryptedBackup(uid: String): Result<String> {
        return try {
            val userSnap = db.collection("users").document(uid).get().await()
            val contactsSnap = db.collection("users").document(uid).collection("contacts").get().await()
            val privSnap = db.collection("users").document(uid).collection("private").document("info").get().await()
            val backupJson = JSONObject().apply {
                put("version", 1)
                put("exportedAtMs", System.currentTimeMillis())
                put("userId", uid)
                put("displayName", userSnap.getString("displayName").orEmpty())
                put("username", userSnap.getString("username").orEmpty())
                put("bio", userSnap.getString("bio").orEmpty())
                put("contactsCount", contactsSnap.size())
                put("phoneNumber", privSnap.getString("phoneNumber").orEmpty())
            }.toString()

            val crypto = cryptoManager ?: return Result.success(backupJson)
            val myPub = crypto.getPublicKeyBase64(uid)
            val encrypted = crypto.encryptForPeer(uid, myPub, "backup_$uid", backupJson)
            val envelope = JSONObject().apply {
                put("ciphertext", encrypted.ciphertextBase64)
                put("iv", encrypted.ivBase64)
                put("senderPublicKey", encrypted.senderPublicKeyBase64)
            }.toString()

            db.collection("users").document(uid)
                .collection("private").document("info")
                .set(
                    mapOf(
                        "userId" to uid,
                        "encryptedBackupJson" to envelope.take(450_000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            Result.success(envelope)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreEncryptedBackup(uid: String): Result<String> {
        return try {
            val privSnap = db.collection("users").document(uid).collection("private").document("info").get().await()
            val envelopeStr = privSnap.getString("encryptedBackupJson").orEmpty()
            if (envelopeStr.isBlank()) return Result.failure(IllegalStateException("Aucune sauvegarde chiffrée trouvée."))
            val obj = JSONObject(envelopeStr)
            val ciphertext = obj.getString("ciphertext")
            val iv = obj.getString("iv")
            val senderPub = obj.getString("senderPublicKey")
            val crypto = cryptoManager ?: return Result.failure(IllegalStateException("Crypto indisponible"))
            val plain = crypto.decryptFromPeer(uid, senderPub, "backup_$uid", ciphertext, iv)
                ?: return Result.failure(IllegalStateException("Impossible de déchiffrer la sauvegarde sur cet appareil."))
            val restored = JSONObject(plain)
            val bio = restored.optString("bio")
            if (bio.isNotBlank()) {
                db.collection("users").document(uid).update(
                    mapOf("bio" to bio, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            }
            Result.success(plain)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkIsAdmin(uid: String, email: String?): Boolean {
        if (!email.isNullOrBlank() && email.lowercase() in setOf("comptbravo78@gmail.com", "admin@bravchat.ci")) {
            return true
        }
        return runCatching {
            db.collection("admins").document(uid).get().await().exists()
        }.getOrDefault(false)
    }

    suspend fun deleteAccountData(uid: String): Result<Unit> {
        val userRef = db.collection("users").document(uid)
        return try {
            userRef.collection("private").document("info").delete().await()
            userRef.collection("devices").get().await().documents.forEach { it.reference.delete().await() }
            userRef.collection("contacts").get().await().documents.forEach { it.reference.delete().await() }
            userRef.collection("blocked").get().await().documents.forEach { it.reference.delete().await() }
            userRef.collection("conversation_prefs").get().await().documents.forEach { it.reference.delete().await() }
            userRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun logTelemetry(uid: String, category: String, message: String) {
        val id = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        runCatching {
            db.collection("system_telemetry").document(id).set(
                mapOf(
                    "userId" to uid,
                    "category" to category.take(64),
                    "message" to message.take(1000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun observeFollowingIds(myUid: String): Flow<Set<String>> {
        if (myUid.isBlank()) return kotlinx.coroutines.flow.flowOf(emptySet())
        return db.collection("users").document(myUid)
            .collection("following")
            .snapshots()
            .map { snap -> snap.documents.map { it.id }.toSet() }
    }

    fun observeFollowerIds(myUid: String): Flow<Set<String>> {
        if (myUid.isBlank()) return kotlinx.coroutines.flow.flowOf(emptySet())
        return db.collection("users").document(myUid)
            .collection("followers")
            .snapshots()
            .map { snap -> snap.documents.map { it.id }.toSet() }
    }

    fun observeIncomingFollowRequests(myUid: String): Flow<List<FollowRequest>> {
        if (myUid.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return db.collection("follow_requests")
            .whereEqualTo("toUid", myUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(FollowRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.status == "pending" }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
    }

    fun observeOutgoingFollowRequests(myUid: String): Flow<List<FollowRequest>> {
        if (myUid.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return db.collection("follow_requests")
            .whereEqualTo("fromUid", myUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(FollowRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.status == "pending" }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
    }

    fun observeMyInvites(myUid: String): Flow<List<InviteLink>> {
        if (myUid.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return db.collection("invites")
            .whereEqualTo("inviterUid", myUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(InviteLink::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
    }

    suspend fun fetchUsersByIds(uids: List<String>): List<User> {
        val clean = uids.filter { it.isNotBlank() }.distinct().take(120)
        if (clean.isEmpty()) return emptyList()
        return clean.chunked(10).flatMap { chunk ->
            runCatching {
                db.collection("users")
                    .whereIn("userId", chunk)
                    .get()
                    .await()
                    .toObjects(User::class.java)
            }.getOrDefault(emptyList())
        }
    }

    suspend fun followOrRequestUser(me: User, target: User): Result<FollowRelationState> {
        val myUid = me.userId
        val targetUid = target.userId
        if (myUid.isBlank() || targetUid.isBlank() || myUid == targetUid) {
            return Result.failure(IllegalArgumentException("Abonnement invalide."))
        }
        val targetRef = db.collection("users").document(targetUid)
        val followerDocRef = targetRef.collection("followers").document(myUid)
        val reqId = "${myUid}_${targetUid}"
        val reqRef = db.collection("follow_requests").document(reqId)

        return try {
            if (followerDocRef.get().await().exists()) {
                return Result.success(FollowRelationState.FOLLOWING)
            }
            val freshTargetSnap = runCatching { targetRef.get().await() }.getOrNull()
            val isPrivate = freshTargetSnap?.getBoolean("isPrivateProfile") ?: target.isPrivateProfile

            if (isPrivate) {
                val existingReq = runCatching { reqRef.get().await() }.getOrNull()
                if (existingReq?.exists() == true && existingReq.getString("status") == "pending") {
                    return Result.success(FollowRelationState.REQUESTED)
                }
                val payload = mapOf(
                    "fromUid" to myUid,
                    "toUid" to targetUid,
                    "fromName" to me.displayName.ifBlank { "Membre BRAV" }.take(120),
                    "fromUsername" to me.username.take(64),
                    "fromAvatar" to me.avatarUrl.take(1000),
                    "toName" to target.displayName.ifBlank { "Membre BRAV" }.take(120),
                    "toAvatar" to target.avatarUrl.take(1000),
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (existingReq?.exists() == true) {
                    reqRef.delete().await()
                }
                reqRef.set(payload).await()
                runCatching {
                    workerApiClient?.toggleFollowWorker(targetUid, follow = true, isPrivate = true)
                }
                runCatching {
                    notificationRepository?.emitNotification(
                        targetUid = targetUid,
                        category = NotificationCategory.CONTACTS,
                        type = "follow_request",
                        title = "Demande d'abonnement",
                        body = "${me.displayName.ifBlank { "Un membre BRAV" }} souhaite s'abonner à votre profil.",
                        refId = reqId,
                        refType = "follow_request",
                        dedupKey = "follow_req_${reqId}",
                        senderUid = myUid,
                        senderName = me.displayName,
                        senderAvatar = me.avatarUrl
                    )
                }
                Result.success(FollowRelationState.REQUESTED)
            } else {
                followUser(myUid, targetUid).map { FollowRelationState.FOLLOWING }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, reqRef.path)
            Result.failure(e)
        }
    }

    suspend fun acceptFollowRequest(request: FollowRequest, myUser: User): Result<Unit> {
        val myUid = myUser.userId
        val fromUid = request.fromUid
        if (myUid.isBlank() || fromUid.isBlank() || fromUid == myUid || request.toUid != myUid) {
            return Result.failure(IllegalArgumentException("Demande d'abonnement invalide."))
        }
        val reqId = request.id.ifBlank { "${fromUid}_${myUid}" }
        val reqRef = db.collection("follow_requests").document(reqId)
        val myRef = db.collection("users").document(myUid)
        val fromRef = db.collection("users").document(fromUid)
        val myFollowerDocRef = myRef.collection("followers").document(fromUid)
        val fromFollowingDocRef = fromRef.collection("following").document(myUid)
        return try {
            val alreadyFollower = myFollowerDocRef.get().await().exists()
            val batch = db.batch()
            batch.update(reqRef, mapOf("status" to "accepted"))
            if (!alreadyFollower) {
                batch.set(
                    myFollowerDocRef,
                    mapOf("followerUid" to fromUid, "createdAt" to FieldValue.serverTimestamp())
                )
                batch.set(
                    fromFollowingDocRef,
                    mapOf("targetUid" to myUid, "createdAt" to FieldValue.serverTimestamp())
                )
                batch.update(
                    myRef,
                    mapOf(
                        "followersCount" to FieldValue.increment(1),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(fromRef, mapOf("followingCount" to FieldValue.increment(1)))
            }
            batch.commit().await()
            runCatching { workerApiClient?.respondFollowRequestWorker(fromUid, accept = true) }
            runCatching {
                notificationRepository?.emitNotification(
                    targetUid = fromUid,
                    category = NotificationCategory.CONTACTS,
                    type = "follow_accepted",
                    title = "Abonnement accepté",
                    body = "${myUser.displayName.ifBlank { "Un membre BRAV" }} a accepté votre demande d'abonnement.",
                    refId = myUid,
                    refType = "profile",
                    dedupKey = "follow_acc_${reqId}",
                    senderUid = myUid,
                    senderName = myUser.displayName,
                    senderAvatar = myUser.avatarUrl
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, reqRef.path)
            Result.failure(e)
        }
    }

    suspend fun declineFollowRequest(request: FollowRequest, myUid: String): Result<Unit> {
        val fromUid = request.fromUid
        val reqId = request.id.ifBlank { "${fromUid}_${myUid}" }
        if (reqId.isBlank() || request.toUid != myUid) {
            return Result.failure(IllegalArgumentException("Demande introuvable."))
        }
        val reqRef = db.collection("follow_requests").document(reqId)
        return try {
            reqRef.update(mapOf("status" to "declined")).await()
            runCatching { workerApiClient?.respondFollowRequestWorker(fromUid, accept = false) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reqRef.path)
            Result.failure(e)
        }
    }

    suspend fun cancelOutgoingFollowRequest(myUid: String, targetUid: String): Result<Unit> {
        if (myUid.isBlank() || targetUid.isBlank()) {
            return Result.failure(IllegalArgumentException("Demande invalide."))
        }
        val reqId = "${myUid}_${targetUid}"
        val reqRef = db.collection("follow_requests").document(reqId)
        return try {
            reqRef.delete().await()
            runCatching { workerApiClient?.toggleFollowWorker(targetUid, follow = false, isPrivate = false) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, reqRef.path)
            Result.failure(e)
        }
    }

    suspend fun removeFollower(myUid: String, followerUid: String): Result<Unit> {
        if (myUid.isBlank() || followerUid.isBlank() || myUid == followerUid) {
            return Result.failure(IllegalArgumentException("Suppression impossible."))
        }
        val myRef = db.collection("users").document(myUid)
        val followerRef = db.collection("users").document(followerUid)
        val myFollowerDocRef = myRef.collection("followers").document(followerUid)
        val followerFollowingDocRef = followerRef.collection("following").document(myUid)
        return try {
            val exists = myFollowerDocRef.get().await().exists()
            if (exists) {
                val batch = db.batch()
                batch.delete(myFollowerDocRef)
                batch.delete(followerFollowingDocRef)
                batch.update(
                    myRef,
                    mapOf(
                        "followersCount" to FieldValue.increment(-1),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(followerRef, mapOf("followingCount" to FieldValue.increment(-1)))
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, myFollowerDocRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivacyAndLookupFlags(
        uid: String,
        isPrivateProfile: Boolean? = null,
        allowPhoneLookup: Boolean? = null,
        defaultCountryIso: String? = null
    ): Result<Unit> {
        if (uid.isBlank()) return Result.failure(IllegalArgumentException("Utilisateur manquant."))
        val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        if (isPrivateProfile != null) updates["isPrivateProfile"] = isPrivateProfile
        if (allowPhoneLookup != null) updates["allowPhoneLookup"] = allowPhoneLookup
        if (!defaultCountryIso.isNullOrBlank()) updates["defaultCountryIso"] = defaultCountryIso.uppercase().take(8)
        val ref = db.collection("users").document(uid)
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

    suspend fun createOrGetPersonalInvite(me: User, channel: String = "share"): Result<InviteLink> {
        val uid = me.userId
        if (uid.isBlank()) return Result.failure(IllegalArgumentException("Session invalide."))
        return try {
            val existing = db.collection("invites")
                .whereEqualTo("inviterUid", uid)
                .get()
                .await()
                .toObjects(InviteLink::class.java)
                .firstOrNull { it.active }
            if (existing != null) {
                return Result.success(existing)
            }
            val code = "brav-${uid.take(4).lowercase()}${UUID.randomUUID().toString().replace("-", "").take(6)}"
            val ref = db.collection("invites").document(code)
            val displayName = me.displayName.ifBlank { "Membre BRAV" }.take(120)
            ref.set(
                mapOf(
                    "code" to code,
                    "inviterUid" to uid,
                    "inviterDisplayName" to displayName,
                    "inviterAvatarUrl" to me.avatarUrl.take(1000),
                    "channel" to channel.take(40),
                    "clicks" to 0,
                    "signups" to 0,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching {
                workerApiClient?.createInviteWorker(code = code, inviterDisplayName = displayName, channel = channel)
            }
            Result.success(
                InviteLink(
                    id = code,
                    code = code,
                    inviterUid = uid,
                    inviterDisplayName = displayName,
                    inviterAvatarUrl = me.avatarUrl,
                    channel = channel,
                    clicks = 0L,
                    signups = 0L,
                    active = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "invites")
            Result.failure(e)
        }
    }

    suspend fun trackInviteUse(code: String, isSignup: Boolean = false) {
        val clean = code.trim()
        if (clean.length < 4) return
        runCatching {
            val ref = db.collection("invites").document(clean)
            val key = if (isSignup) "signups" else "clicks"
            ref.update(
                mapOf(
                    key to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        runCatching {
            workerApiClient?.trackInviteWorker(clean, isSignup = isSignup)
        }
    }

    suspend fun followUser(myUid: String, targetUid: String): Result<Int> {
        if (myUid.isBlank() || targetUid.isBlank() || myUid == targetUid) {
            return Result.failure(IllegalArgumentException("Abonnement invalide."))
        }
        val targetRef = db.collection("users").document(targetUid)
        val myRef = db.collection("users").document(myUid)
        val followerDocRef = targetRef.collection("followers").document(myUid)
        val followingDocRef = myRef.collection("following").document(targetUid)
        return try {
            val alreadyFollowing = followerDocRef.get().await().exists()
            if (!alreadyFollowing) {
                val batch = db.batch()
                batch.set(
                    followerDocRef,
                    mapOf("followerUid" to myUid, "createdAt" to FieldValue.serverTimestamp())
                )
                batch.set(
                    followingDocRef,
                    mapOf("targetUid" to targetUid, "createdAt" to FieldValue.serverTimestamp())
                )
                batch.update(targetRef, mapOf("followersCount" to FieldValue.increment(1)))
                batch.update(
                    myRef,
                    mapOf(
                        "followingCount" to FieldValue.increment(1),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
            val workerRes = runCatching { workerApiClient?.toggleFollowWorker(targetUid, true)?.getOrNull() }.getOrNull()
            val refreshed = workerRes?.followersCount ?: (targetRef.get().await().getLong("followersCount")?.toInt() ?: 0)
            Result.success(refreshed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, followerDocRef.path)
            Result.failure(e)
        }
    }

    suspend fun unfollowUser(myUid: String, targetUid: String): Result<Int> {
        if (myUid.isBlank() || targetUid.isBlank() || myUid == targetUid) {
            return Result.failure(IllegalArgumentException("Désabonnement invalide."))
        }
        val targetRef = db.collection("users").document(targetUid)
        val myRef = db.collection("users").document(myUid)
        val followerDocRef = targetRef.collection("followers").document(myUid)
        val followingDocRef = myRef.collection("following").document(targetUid)
        val reqRef = db.collection("follow_requests").document("${myUid}_${targetUid}")
        return try {
            val isCurrentlyFollowing = followerDocRef.get().await().exists()
            if (isCurrentlyFollowing) {
                val batch = db.batch()
                batch.delete(followerDocRef)
                batch.delete(followingDocRef)
                batch.update(targetRef, mapOf("followersCount" to FieldValue.increment(-1)))
                batch.update(
                    myRef,
                    mapOf(
                        "followingCount" to FieldValue.increment(-1),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            } else {
                runCatching { reqRef.delete().await() }
            }
            val workerRes = runCatching { workerApiClient?.toggleFollowWorker(targetUid, false)?.getOrNull() }.getOrNull()
            val refreshed = workerRes?.followersCount ?: (targetRef.get().await().getLong("followersCount")?.toInt() ?: 0)
            Result.success(refreshed.coerceAtLeast(0))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, followerDocRef.path)
            Result.failure(e)
        }
    }

    fun currentUserMatchesDesignerEmail(): Boolean {
        val fbUser = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return false
        val primaryMatches = fbUser.email?.trim()
            ?.equals(CertificationEligibility.DESIGNER_EXEMPT_EMAIL, ignoreCase = true) == true
        val providerMatches = fbUser.providerData.any { info ->
            info.email?.trim()?.equals(CertificationEligibility.DESIGNER_EXEMPT_EMAIL, ignoreCase = true) == true
        }
        return primaryMatches || providerMatches
    }

    fun isVerifiedDesignerAccount(): Boolean {
        val fbUser = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return false
        if (!fbUser.isEmailVerified) return false
        return currentUserMatchesDesignerEmail()
    }

    suspend fun activateDesignerCertificationIfEligible(
        user: User,
        allowServerConfirmedFallback: Boolean = false
    ): Result<Boolean> {
        val canProceed = isVerifiedDesignerAccount() ||
            (allowServerConfirmedFallback && currentUserMatchesDesignerEmail())
        if (!canProceed) return Result.success(false)
        val uid = user.userId
        if (uid.isBlank()) return Result.success(false)
        val userRef = db.collection("users").document(uid)
        val reqId = "verif_$uid"
        val reqRef = db.collection("verification_requests").document(reqId)
        val wasVerifiedBefore = user.isVerifiedActive()
        val needsUserUpdate = !wasVerifiedBefore ||
            user.verificationExpiresAtMs != 0L ||
            user.verificationStatus != VerificationStatus.VERIFIED.raw ||
            user.verificationCategory != VerificationCategory.CREATOR.raw ||
            user.verifiedOrgName != "Concepteur BRAV CHAT"

        return try {
            if (needsUserUpdate) {
                userRef.update(
                    mapOf(
                        "verificationStatus" to VerificationStatus.VERIFIED.raw,
                        "verificationCategory" to VerificationCategory.CREATOR.raw,
                        "verifiedOrgName" to "Concepteur BRAV CHAT",
                        "verificationExpiresAtMs" to 0L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Heal the audit dossier independently so any legacy schema mismatch here
            // never fails or loops the primary designer user badge state.
            runCatching {
                val reqSnap = reqRef.get().await()
                val dateStamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
                val safeFullName = user.displayName.trim().let {
                    if (it.length >= 2) it else "Concepteur BRAV CHAT"
                }.take(140)
                if (!reqSnap.exists()) {
                    reqRef.set(
                        mapOf(
                            "caseNumber" to "BRV-DESIGNER-${uid.take(6).uppercase()}",
                            "userId" to uid,
                            "displayName" to user.displayName.ifBlank { "Concepteur BRAV CHAT" }.take(120),
                            "username" to user.username.take(64),
                            "avatarUrl" to user.avatarUrl.take(1000),
                            "fullName" to safeFullName,
                            "category" to VerificationCategory.CREATOR.raw,
                            "organizationName" to "BRAV CHAT",
                            "roleOrActivity" to "Concepteur officiel",
                            "country" to user.locationCity.take(80),
                            "officialWebsite" to "",
                            "justification" to "Exemption officielle du concepteur BRAV CHAT",
                            "followersCountAtRequest" to user.followersCount,
                            "selfieVerified" to true,
                            "selfieScore" to 1.0,
                            "spoofRisk" to "low",
                            "selfieStorageKey" to "",
                            "selfieConsent" to true,
                            "termsAccepted" to true,
                            "paymentTxRef" to "",
                            "paymentAmountXof" to 0,
                            "paymentCurrency" to "XOF",
                            "paymentStatus" to "exempt",
                            "receiptNumber" to "EXEMPT-DESIGNER",
                            "designerExempt" to true,
                            "status" to VerificationStatus.VERIFIED.raw,
                            "reviewerNote" to "Certification automatique officielle du concepteur BRAV CHAT.",
                            "reviewedBy" to "system_designer_exempt",
                            "auditTrail" to listOf("$dateStamp · Badge bleu concepteur activé automatiquement"),
                            "expiresAtMs" to 0L,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    val curStatus = reqSnap.getString("status").orEmpty()
                    val curExempt = reqSnap.getBoolean("designerExempt") ?: false
                    val curExp = reqSnap.getLong("expiresAtMs") ?: 0L
                    if (curStatus != VerificationStatus.VERIFIED.raw || !curExempt || curExp != 0L) {
                        @Suppress("UNCHECKED_CAST")
                        val prevTrail = (reqSnap.get("auditTrail") as? List<String>).orEmpty()
                        val nextTrail = (prevTrail + "$dateStamp · Régularisation automatique compte concepteur").takeLast(40)
                        reqRef.update(
                            mapOf(
                                "status" to VerificationStatus.VERIFIED.raw,
                                "designerExempt" to true,
                                "paymentStatus" to "exempt",
                                "paymentAmountXof" to 0,
                                "receiptNumber" to "EXEMPT-DESIGNER",
                                "selfieVerified" to true,
                                "selfieScore" to 1.0,
                                "spoofRisk" to "low",
                                "selfieConsent" to true,
                                "termsAccepted" to true,
                                "expiresAtMs" to 0L,
                                "reviewerNote" to "Certification automatique officielle du concepteur BRAV CHAT.",
                                "reviewedBy" to "system_designer_exempt",
                                "auditTrail" to nextTrail,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }

            if (!wasVerifiedBefore) {
                runCatching {
                    notificationRepository?.emitNotification(
                        targetUid = uid,
                        category = NotificationCategory.ACCOUNT_SECURITY,
                        type = "certification_verified",
                        title = "CERTIFICATION — Badge bleu activé",
                        body = "Compte concepteur officiel vérifié automatiquement. Votre badge bleu CERTIFICATION est actif.",
                        refId = reqId,
                        refType = "certification",
                        dedupKey = "cert_designer_${uid}",
                        senderUid = uid,
                        senderName = "BRAV CHAT",
                        priority = "critical"
                    )
                }
            }
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun checkCertificationEligibility(user: User): CertificationEligibility {
        if (isVerifiedDesignerAccount()) {
            return CertificationEligibility(
                followersCount = user.followersCount,
                requiredFollowers = CertificationEligibility.REQUIRED_FOLLOWERS,
                eligible = true,
                exemptDesigner = true,
                status = VerificationStatus.VERIFIED,
                caseNumber = "BRV-DESIGNER-${user.userId.take(6).uppercase()}"
            )
        }
        val workerElig = runCatching { workerApiClient?.fetchCertificationEligibility()?.getOrNull() }.getOrNull()
        if (workerElig?.exemptDesigner == true) {
            return CertificationEligibility(
                followersCount = workerElig.followersCount,
                requiredFollowers = workerElig.requiredFollowers,
                eligible = true,
                exemptDesigner = true,
                status = VerificationStatus.VERIFIED,
                caseNumber = workerElig.caseNumber.ifBlank { "BRV-DESIGNER-${user.userId.take(6).uppercase()}" }
            )
        }
        val fsCount = runCatching {
            db.collection("users").document(user.userId).get().await().getLong("followersCount")?.toInt()
        }.getOrNull() ?: user.followersCount
        val authoritativeFollowers = workerElig?.followersCount ?: fsCount
        val eligible = authoritativeFollowers >= CertificationEligibility.REQUIRED_FOLLOWERS
        val resolvedStatus = workerElig?.status?.let { VerificationStatus.fromRaw(it) }
            ?: if (eligible) VerificationStatus.ELIGIBLE else VerificationStatus.NOT_ELIGIBLE
        return CertificationEligibility(
            followersCount = authoritativeFollowers,
            requiredFollowers = workerElig?.requiredFollowers ?: CertificationEligibility.REQUIRED_FOLLOWERS,
            eligible = workerElig?.eligible ?: eligible,
            exemptDesigner = workerElig?.exemptDesigner ?: false,
            status = resolvedStatus,
            caseNumber = workerElig?.caseNumber.orEmpty(),
            feeXof = workerElig?.feeXof ?: CertificationEligibility.FEE_XOF,
            feeUsdLabel = workerElig?.feeUsd ?: CertificationEligibility.FEE_USD_LABEL,
            receiptNumber = workerElig?.receiptNumber.orEmpty(),
            paymentTxRef = workerElig?.paymentTxRef.orEmpty()
        )
    }

    suspend fun submitLiveSelfieVerification(
        user: User,
        category: VerificationCategory,
        organizationName: String,
        roleOrActivity: String,
        country: String,
        payload: BravHumanVerificationService.AnalyzedSelfiePayload,
        termsAccepted: Boolean,
        selfieConsent: Boolean
    ): Result<VerificationRequest> {
        if (!termsAccepted || !selfieConsent) {
            return Result.failure(IllegalArgumentException("Veuillez accepter les conditions et le consentement selfie."))
        }
        if (!payload.assessment.passed) {
            return Result.failure(IllegalArgumentException(payload.assessment.feedbackFr))
        }
        if (isVerifiedDesignerAccount()) {
            activateDesignerCertificationIfEligible(user)
            return Result.success(
                VerificationRequest(
                    id = "verif_${user.userId}",
                    caseNumber = "BRV-DESIGNER-${user.userId.take(6).uppercase()}",
                    userId = user.userId,
                    displayName = user.displayName,
                    fullName = user.displayName,
                    category = category.raw,
                    designerExempt = true,
                    status = VerificationStatus.VERIFIED.raw
                )
            )
        }
        val worker = workerApiClient ?: return Result.failure(IllegalStateException("Service de vérification indisponible"))
        val workerRes = worker.submitCertificationSelfie(
            category = category.raw,
            faceCount = payload.assessment.faceCount,
            livenessScore = payload.assessment.livenessScore,
            sharpnessScore = payload.assessment.sharpnessScore,
            luminance = payload.assessment.luminance,
            spoofRisk = payload.assessment.spoofRisk,
            termsAccepted = termsAccepted,
            selfieConsent = selfieConsent,
            imageBytes = payload.jpegBytes
        )
        if (workerRes.isFailure) {
            return Result.failure(workerRes.exceptionOrNull() ?: IllegalStateException("Validation du selfie refusée par le serveur"))
        }
        val server = workerRes.getOrThrow()
        val reqId = "verif_${user.userId}"
        val reqRef = db.collection("verification_requests").document(reqId)
        val nowMs = System.currentTimeMillis()
        val dateStamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(nowMs))
        val fsFollowers = runCatching {
            db.collection("users").document(user.userId).get().await().getLong("followersCount")?.toInt()
        }.getOrNull() ?: user.followersCount
        val effectiveFollowers = maxOf(fsFollowers, CertificationEligibility.REQUIRED_FOLLOWERS)

        return try {
            val existingSnap = reqRef.get().await()
            val cleanName = user.displayName.ifBlank { "Membre BRAV" }.take(140)
            if (existingSnap.exists()) {
                val prevTrail = (existingSnap.get("auditTrail") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                val nextTrail = (prevTrail + "$dateStamp · Selfie caméra live validé (score ${(payload.assessment.livenessScore * 100).toInt()}%)").takeLast(40)
                reqRef.update(
                    mapOf(
                        "selfieVerified" to true,
                        "selfieScore" to payload.assessment.livenessScore,
                        "spoofRisk" to payload.assessment.spoofRisk,
                        "selfieStorageKey" to server.selfieKey.take(500),
                        "selfieConsent" to true,
                        "followersCountAtRequest" to effectiveFollowers,
                        "status" to VerificationStatus.AWAITING_PAYMENT.raw,
                        "auditTrail" to nextTrail,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val initialTrail = listOf("$dateStamp · Dossier ${server.caseNumber} — Selfie caméra live validé")
                reqRef.set(
                    mapOf(
                        "caseNumber" to server.caseNumber,
                        "userId" to user.userId,
                        "displayName" to user.displayName.take(120),
                        "username" to user.username.take(64),
                        "avatarUrl" to user.avatarUrl.take(1000),
                        "fullName" to cleanName,
                        "category" to category.raw,
                        "organizationName" to organizationName.trim().take(140),
                        "roleOrActivity" to roleOrActivity.trim().take(140),
                        "country" to country.trim().take(80),
                        "officialWebsite" to "",
                        "justification" to "Certification automatisée CERTIFICATION (5 000+ abonnés et selfie en direct).",
                        "followersCountAtRequest" to effectiveFollowers,
                        "selfieVerified" to true,
                        "selfieScore" to payload.assessment.livenessScore,
                        "spoofRisk" to payload.assessment.spoofRisk,
                        "selfieStorageKey" to server.selfieKey.take(500),
                        "selfieConsent" to true,
                        "termsAccepted" to true,
                        "paymentTxRef" to "",
                        "paymentAmountXof" to CertificationEligibility.FEE_XOF,
                        "paymentCurrency" to "XOF",
                        "paymentStatus" to "unpaid",
                        "receiptNumber" to "",
                        "designerExempt" to false,
                        "status" to VerificationStatus.AWAITING_PAYMENT.raw,
                        "reviewerNote" to "",
                        "reviewedBy" to "",
                        "auditTrail" to initialTrail,
                        "expiresAtMs" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            val saved = reqRef.get().await().toObject(VerificationRequest::class.java)?.copy(id = reqId)
                ?: VerificationRequest(
                    id = reqId,
                    caseNumber = server.caseNumber,
                    userId = user.userId,
                    fullName = cleanName,
                    category = category.raw,
                    organizationName = organizationName.trim(),
                    roleOrActivity = roleOrActivity.trim(),
                    followersCountAtRequest = effectiveFollowers,
                    selfieVerified = true,
                    selfieScore = payload.assessment.livenessScore,
                    spoofRisk = payload.assessment.spoofRisk,
                    selfieStorageKey = server.selfieKey,
                    selfieConsent = true,
                    termsAccepted = true,
                    status = VerificationStatus.AWAITING_PAYMENT.raw
                )
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, reqRef.path)
            Result.failure(e)
        }
    }

    suspend fun initiateCertificationPayment(
        idempotencyKey: String,
        currency: String = "XOF",
        provider: String = "brav_mobile_money"
    ): Result<WorkerPaymentInitiateResult> {
        val worker = workerApiClient ?: return Result.failure(IllegalStateException("Service paiement indisponible"))
        return worker.initiateCertificationPayment(
            idempotencyKey = idempotencyKey,
            currency = currency,
            provider = provider
        )
    }

    suspend fun confirmCertificationPaymentAndGrantBadge(
        user: User,
        existingRequest: VerificationRequest?,
        txRef: String,
        outcome: String = "confirmed",
        currency: String = "XOF"
    ): Result<CertificationPaymentReceipt> {
        val worker = workerApiClient ?: return Result.failure(IllegalStateException("Service paiement indisponible"))
        val confirmRes = worker.confirmCertificationPayment(txRef = txRef, outcome = outcome, currency = currency)
        if (confirmRes.isFailure) {
            return Result.failure(confirmRes.exceptionOrNull() ?: IllegalStateException("Paiement non confirmé"))
        }
        val confirmed = confirmRes.getOrThrow()
        if (!confirmed.badgeGranted || confirmed.receiptNumber.isBlank()) {
            return Result.failure(IllegalStateException("Le badge bleu n'a pas pu être activé après vérification serveur."))
        }

        val receipt = CertificationPaymentReceipt(
            receiptNumber = confirmed.receiptNumber,
            txRef = confirmed.txRef,
            caseNumber = confirmed.caseNumber.ifBlank { existingRequest?.caseNumber.orEmpty() },
            amountXof = confirmed.amountXof,
            amountUsdLabel = confirmed.amountUsd,
            currency = confirmed.currency,
            verifiedAtMs = confirmed.verifiedAtMs
        )

        val uid = user.userId
        val reqId = "verif_$uid"
        val reqRef = db.collection("verification_requests").document(reqId)
        val userRef = db.collection("users").document(uid)
        val expiresAtMs = System.currentTimeMillis() + 730L * 86_400_000L
        val dateStamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
        val prevTrail = existingRequest?.auditTrail.orEmpty()
        val nextTrail = (prevTrail + "$dateStamp · Paiement confirmé (${receipt.receiptNumber}) — Badge bleu activé").takeLast(40)
        val catRaw = existingRequest?.category?.ifBlank { VerificationCategory.CREATOR.raw } ?: VerificationCategory.CREATOR.raw
        val orgBadge = existingRequest?.organizationName?.ifBlank { existingRequest.roleOrActivity }.orEmpty().take(140)

        return try {
            val batch = db.batch()
            batch.update(
                reqRef,
                mapOf(
                    "status" to VerificationStatus.VERIFIED.raw,
                    "paymentTxRef" to receipt.txRef,
                    "paymentAmountXof" to CertificationEligibility.FEE_XOF,
                    "paymentCurrency" to receipt.currency,
                    "paymentStatus" to "confirmed",
                    "receiptNumber" to receipt.receiptNumber,
                    "followersCountAtRequest" to confirmed.followersCount,
                    "expiresAtMs" to expiresAtMs,
                    "auditTrail" to nextTrail,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                userRef,
                mapOf(
                    "verificationStatus" to VerificationStatus.VERIFIED.raw,
                    "verificationCategory" to catRaw,
                    "verifiedOrgName" to orgBadge,
                    "verificationExpiresAtMs" to expiresAtMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(receipt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    fun observeMyVerificationRequests(uid: String): Flow<List<VerificationRequest>> = callbackFlow {
        val reg = db.collection("verification_requests")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(VerificationRequest::class.java)?.copy(id = doc.id)
                }.orEmpty().sortedByDescending { it.createdAt?.seconds ?: 0L }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeAllVerificationRequestsForAdmin(): Flow<List<VerificationRequest>> = callbackFlow {
        val reg = db.collection("verification_requests")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(VerificationRequest::class.java)?.copy(id = doc.id)
                }.orEmpty().sortedWith(
                    compareBy<VerificationRequest> {
                        when (it.status) {
                            VerificationStatus.AWAITING_PAYMENT.raw -> 0
                            VerificationStatus.PAYMENT_CONFIRMED.raw -> 1
                            VerificationStatus.PENDING.raw -> 2
                            VerificationStatus.IN_REVIEW.raw -> 3
                            VerificationStatus.RETAKE_REQUIRED.raw -> 4
                            VerificationStatus.VERIFIED.raw -> 5
                            VerificationStatus.SUSPENDED.raw -> 6
                            VerificationStatus.REJECTED.raw -> 7
                            else -> 8
                        }
                    }.thenByDescending { it.createdAt?.seconds ?: 0L }
                )
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun adminReviewVerificationRequest(
        request: VerificationRequest,
        newStatus: VerificationStatus,
        reviewerUid: String,
        reviewerNote: String = "",
        validityDays: Long = 730L
    ): Result<Unit> {
        val expiresAtMs = if (newStatus == VerificationStatus.VERIFIED && validityDays > 0L) {
            System.currentTimeMillis() + validityDays * 86_400_000L
        } else {
            0L
        }
        val dateStamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
        val noteSummary = reviewerNote.trim().take(120).ifBlank { newStatus.labelFr }
        val nextTrail = (request.auditTrail + "$dateStamp · ${newStatus.labelFr} (${reviewerUid.take(8)}) : $noteSummary").takeLast(40)
        return try {
            runCatching {
                workerApiClient?.submitAdminVerificationDecision(
                    targetUid = request.userId,
                    status = newStatus.raw,
                    reviewerNote = reviewerNote
                )
            }
            val batch = db.batch()
            val reqRef = db.collection("verification_requests").document(request.id)
            batch.update(
                reqRef,
                mapOf(
                    "status" to newStatus.raw,
                    "reviewerNote" to reviewerNote.trim().take(600),
                    "reviewedBy" to reviewerUid,
                    "auditTrail" to nextTrail,
                    "expiresAtMs" to expiresAtMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val userRef = db.collection("users").document(request.userId)
            val badgeOrg = request.organizationName.ifBlank { request.roleOrActivity }.take(140)
            val shouldUpdatePublicUser = newStatus != VerificationStatus.IN_REVIEW && newStatus != VerificationStatus.INFO_REQUIRED
            if (shouldUpdatePublicUser) {
                batch.update(
                    userRef,
                    mapOf(
                        "verificationStatus" to newStatus.raw,
                        "verificationCategory" to request.category,
                        "verifiedOrgName" to if (newStatus == VerificationStatus.VERIFIED) badgeOrg else "",
                        "verificationExpiresAtMs" to expiresAtMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            runCatching {
                notificationRepository?.emitNotification(
                    targetUid = request.userId,
                    category = NotificationCategory.ACCOUNT_SECURITY,
                    type = "certification_decision",
                    title = if (newStatus == VerificationStatus.VERIFIED) "CERTIFICATION validée" else "CERTIFICATION — Mise à jour",
                    body = reviewerNote.trim().ifBlank {
                        if (newStatus == VerificationStatus.VERIFIED) {
                            "Votre badge bleu CERTIFICATION a été activé."
                        } else {
                            "Statut de votre dossier ${request.caseNumber} : ${newStatus.labelFr}"
                        }
                    },
                    refId = request.id,
                    refType = "certification",
                    dedupKey = "cert_admin_${request.id}_${newStatus.raw}_${System.currentTimeMillis() / 1000L}",
                    senderUid = reviewerUid,
                    senderName = "BRAV CHAT",
                    priority = "critical"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "verification_requests/${request.id}")
            Result.failure(e)
        }
    }

    suspend fun cancelPendingVerificationRequest(requestId: String): Result<Unit> {
        val ref = db.collection("verification_requests").document(requestId)
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

    fun sanitizeUsername(input: String): String {
        return input.trim()
            .removePrefix("@")
            .lowercase()
            .replace(Regex("[^a-z0-9._-]"), "")
            .take(36)
    }
}
