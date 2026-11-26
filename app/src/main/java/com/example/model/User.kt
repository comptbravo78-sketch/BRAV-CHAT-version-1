package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class PrivacySettings(
    val showPhoto: String = "everyone",       // everyone | contacts | nobody
    val showCover: String = "everyone",       // everyone | contacts | nobody
    val showStatus: String = "everyone",      // everyone | contacts | nobody
    val showLastSeen: String = "everyone",    // everyone | contacts | nobody
    val whoCanAddToGroup: String = "everyone",
    val whoCanCall: String = "everyone",
    val whoCanRequest: String = "everyone",
    val showInfo: String = "everyone",
    val showCity: String = "everyone",
    val showBirthDate: String = "contacts",
    val showAddress: String = "nobody",
    val showPhone: String = "contacts",
    val showEmail: String = "nobody",
    val showPosts: String = "everyone"
)

enum class VerificationStatus(val raw: String, val labelFr: String) {
    NONE("none", "Non vérifié"),
    NOT_ELIGIBLE("not_eligible", "Non éligible"),
    ELIGIBLE("eligible", "Éligible"),
    AWAITING_SELFIE("awaiting_selfie", "Selfie requis"),
    AWAITING_PAYMENT("awaiting_payment", "Paiement requis"),
    PAYMENT_CONFIRMED("payment_confirmed", "Paiement confirmé"),
    PENDING("pending", "En attente"),
    IN_REVIEW("in_review", "En cours d'examen"),
    INFO_REQUIRED("info_required", "Compléments requis"),
    VERIFIED("verified", "Vérifié"),
    RETAKE_REQUIRED("retake_required", "À reprendre"),
    REJECTED("rejected", "Refusé"),
    SUSPENDED("suspended", "Suspendu"),
    REVOKED("revoked", "Révoqué"),
    EXPIRED("expired", "Expiré");

    companion object {
        fun fromRaw(raw: String?): VerificationStatus =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class VerificationCategory(val raw: String, val labelFr: String, val hintFr: String, val requiredDocsFr: String) {
    PUBLIC_FIGURE(
        "public_figure",
        "Personnalité publique",
        "Artiste, sportif, personnalité médiatique ou figure publique",
        "5 000 abonnés réels + selfie caméra live + frais 3 600 F CFA (5,99 USD)"
    ),
    BUSINESS(
        "business",
        "Entreprise ou organisation",
        "Société, institution, ONG, établissement ou marque reconnue",
        "5 000 abonnés réels + selfie caméra live + frais 3 600 F CFA (5,99 USD)"
    ),
    PROFESSIONAL(
        "professional",
        "Professionnel",
        "Commerçant, entrepreneur, médecin, avocat, consultant ou expert",
        "5 000 abonnés réels + selfie caméra live + frais 3 600 F CFA (5,99 USD)"
    ),
    CREATOR(
        "creator",
        "Créateur de contenu",
        "Journaliste, vidéaste, producteur numérique ou média actif",
        "5 000 abonnés réels + selfie caméra live + frais 3 600 F CFA (5,99 USD)"
    ),
    PERSONAL(
        "personal",
        "Compte personnel authentifié",
        "Profil personnel actif certifié par présence humaine en direct",
        "5 000 abonnés réels + selfie caméra live + frais 3 600 F CFA (5,99 USD)"
    );

    companion object {
        fun fromRaw(raw: String?): VerificationCategory =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: CREATOR
    }
}

data class CertificationEligibility(
    val followersCount: Int = 0,
    val requiredFollowers: Int = REQUIRED_FOLLOWERS,
    val eligible: Boolean = false,
    val exemptDesigner: Boolean = false,
    val status: VerificationStatus = VerificationStatus.NONE,
    val caseNumber: String = "",
    val feeXof: Int = FEE_XOF,
    val feeUsdLabel: String = FEE_USD_LABEL,
    val receiptNumber: String = "",
    val paymentTxRef: String = ""
) {
    val missingFollowers: Int
        get() = (requiredFollowers - followersCount).coerceAtLeast(0)

    companion object {
        const val REQUIRED_FOLLOWERS = 5000
        const val FEE_XOF = 3600
        const val FEE_USD_CENTS = 599
        const val FEE_USD_LABEL = "5,99 USD"
        const val DESIGNER_EXEMPT_EMAIL = "bravoboutique225@gmail.com"
    }
}

data class LiveSelfieAssessment(
    val passed: Boolean = false,
    val faceCount: Int = 0,
    val livenessScore: Double = 0.0,
    val sharpnessScore: Double = 0.0,
    val luminance: Double = 0.0,
    val spoofRisk: String = "unknown", // low | medium | high
    val feedbackFr: String = ""
)

data class CertificationPaymentReceipt(
    val receiptNumber: String = "",
    val txRef: String = "",
    val caseNumber: String = "",
    val amountXof: Int = CertificationEligibility.FEE_XOF,
    val amountUsdLabel: String = CertificationEligibility.FEE_USD_LABEL,
    val currency: String = "XOF",
    val verifiedAtMs: Long = 0L
)

@IgnoreExtraProperties
data class User(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val phoneLookupHash: String = "",
    val emailLookupHash: String = "",
    val phoneMasked: String = "",
    val bio: String = "Disponible sur BRAV Chat",
    val locationCity: String = "",
    val locationDetails: String = "",
    val publicEmail: String = "",
    val publicPhone: String = "",
    val publicBirthDate: String = "",
    val publicAddress: String = "",
    val avatarUrl: String = "",
    val coverUrl: String = "",
    val availabilityStatus: String = "online", // online | offline | busy | dnd | invisible
    @get:PropertyName("isOnline") @field:PropertyName("isOnline")
    val isOnline: Boolean = false,
    val lastSeenAt: Timestamp? = null,
    val publicKey: String = "",
    val privacySettings: Map<String, String> = mapOf(
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
    val role: String = "user",
    @get:PropertyName("isSuspended") @field:PropertyName("isSuspended")
    val isSuspended: Boolean = false,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    @get:PropertyName("isPrivateProfile") @field:PropertyName("isPrivateProfile")
    val isPrivateProfile: Boolean = false,
    val allowPhoneLookup: Boolean = true,
    val defaultCountryIso: String = "CI",
    val verificationStatus: String = "none",
    val verificationCategory: String = "",
    val verifiedOrgName: String = "",
    val verificationExpiresAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun privacyLevelFor(key: String, defaultValue: String = "everyone"): String =
        privacySettings[key] ?: defaultValue

    fun isFieldVisibleTo(key: String, isContact: Boolean, isOwner: Boolean): Boolean {
        if (isOwner) return true
        val fallback = when (key) {
            "showBirthDate", "showPhone" -> "contacts"
            "showAddress", "showEmail" -> "nobody"
            "showCity" -> privacySettings["showInfo"] ?: "everyone"
            else -> "everyone"
        }
        return when (privacyLevelFor(key, fallback)) {
            "everyone" -> true
            "contacts" -> isContact
            "nobody" -> false
            else -> true
        }
    }
    fun effectiveVerificationStatus(nowMs: Long = System.currentTimeMillis()): VerificationStatus {
        val st = VerificationStatus.fromRaw(verificationStatus)
        if (st == VerificationStatus.VERIFIED && verificationExpiresAtMs > 0L && verificationExpiresAtMs < nowMs) {
            return VerificationStatus.EXPIRED
        }
        return st
    }

    val verificationCategoryEnum: VerificationCategory
        get() = VerificationCategory.fromRaw(verificationCategory)

    fun isVerifiedActive(nowMs: Long = System.currentTimeMillis()): Boolean =
        effectiveVerificationStatus(nowMs) == VerificationStatus.VERIFIED

    fun isDesignerProfileNormalized(): Boolean =
        isVerifiedActive() &&
            verificationExpiresAtMs == 0L &&
            verificationCategory == VerificationCategory.CREATOR.raw &&
            verifiedOrgName == "Concepteur BRAV CHAT"

    fun withDesignerCertificationIfEligible(isExemptDesigner: Boolean): User {
        if (!isExemptDesigner) return this
        if (isDesignerProfileNormalized()) return this
        return copy(
            verificationStatus = VerificationStatus.VERIFIED.raw,
            verificationCategory = verificationCategory.ifBlank { VerificationCategory.CREATOR.raw },
            verifiedOrgName = verifiedOrgName.ifBlank { "Concepteur BRAV CHAT" },
            verificationExpiresAtMs = 0L
        )
    }
}

data class DesignerSyncPolicyState(
    val completedForSession: Boolean = false,
    val attemptCount: Int = 0,
    val lastAttemptMs: Long = 0L
) {
    fun shouldAttemptSync(
        isAlreadyNormalized: Boolean,
        nowMs: Long,
        maxAttempts: Int = 2,
        cooldownMs: Long = 15_000L
    ): Boolean {
        if (isAlreadyNormalized && completedForSession) return false
        if (attemptCount >= maxAttempts) return false
        if (attemptCount > 0 && nowMs - lastAttemptMs < cooldownMs) return false
        return true
    }
}

@IgnoreExtraProperties
data class VerificationRequest(
    @DocumentId val id: String = "",
    val caseNumber: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val fullName: String = "",
    val category: String = VerificationCategory.CREATOR.raw,
    val organizationName: String = "",
    val roleOrActivity: String = "",
    val country: String = "",
    val officialWebsite: String = "",
    val justification: String = "",
    val followersCountAtRequest: Int = 0,
    val selfieVerified: Boolean = false,
    val selfieScore: Double = 0.0,
    val spoofRisk: String = "unknown",
    val selfieStorageKey: String = "",
    val selfieConsent: Boolean = false,
    val termsAccepted: Boolean = false,
    val paymentTxRef: String = "",
    val paymentAmountXof: Int = 0,
    val paymentCurrency: String = "XOF",
    val paymentStatus: String = "unpaid",
    val receiptNumber: String = "",
    val designerExempt: Boolean = false,
    val status: String = VerificationStatus.AWAITING_SELFIE.raw,
    val reviewerNote: String = "",
    val reviewedBy: String = "",
    val auditTrail: List<String> = emptyList(),
    val expiresAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserPrivateInfo(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val birthDate: String = "",
    val homeAddress: String = "",
    val phoneVerified: Boolean = false,
    val securityPinHash: String = "",
    val encryptedBackupJson: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserDevice(
    @DocumentId val id: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val platform: String = "android",
    val revoked: Boolean = false,
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ContactRequest(
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val fromUsername: String = "",
    val toUid: String = "",
    val toName: String = "",
    val status: String = "pending", // pending | accepted | declined
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BlockedEntry(
    @DocumentId val id: String = "",
    val blockedUid: String = "",
    val reason: String = "",
    @ServerTimestamp val blockedAt: Timestamp? = null
)

enum class FollowRelationState(val raw: String, val buttonLabelFr: String) {
    NOT_FOLLOWING("not_following", "S'abonner"),
    FOLLOWING("following", "Abonné"),
    REQUESTED("requested", "Abonnement demandé"),
    SELF("self", "Vous");

    companion object {
        fun fromRaw(raw: String?): FollowRelationState =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: NOT_FOLLOWING
    }
}

@IgnoreExtraProperties
data class FollowRequest(
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val fromUsername: String = "",
    val fromAvatar: String = "",
    val toUid: String = "",
    val toName: String = "",
    val toAvatar: String = "",
    val status: String = "pending", // pending | accepted | declined
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InviteLink(
    @DocumentId val id: String = "",
    val code: String = "",
    val inviterUid: String = "",
    val inviterDisplayName: String = "",
    val inviterAvatarUrl: String = "",
    val channel: String = "share",
    val clicks: Long = 0L,
    val signups: Long = 0L,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val shareUrl: String
        get() = "https://bravchat.com/invite/${code.ifBlank { id }}"
}

