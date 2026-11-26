package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class StatusPost(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userAvatar: String = "",
    val authorUsername: String = "",
    val authorHeadline: String = "",
    val type: String = "text", // text | image | video
    val text: String = "",
    val caption: String = "",
    val mediaUrl: String = "",
    val mediaUrls: List<String> = emptyList(),
    val mediaAspectRatio: Float = 0f,
    val mediaWidth: Int = 0,
    val mediaHeight: Int = 0,
    val mediaAspectRatios: List<Float> = emptyList(),
    val thumbnailUrl: String = "",
    val durationMs: Long = 0L,
    val allowComments: Boolean = true,
    val locationLabel: String = "",
    val hashtags: List<String> = emptyList(),
    val mentions: List<String> = emptyList(),
    val backgroundColor: String = "#570CA2",
    val audienceType: String = "all", // all | selected
    val allowedUids: List<String> = emptyList(),
    val viewerIds: List<String> = emptyList(),
    val likedByUids: List<String> = emptyList(),
    val bookmarkedByUids: List<String> = emptyList(),
    val reactions: Map<String, List<String>> = emptyMap(),
    val commentCount: Long = 0L,
    val viewCount: Long = 0L,
    val shareCount: Long = 0L,
    val expiresAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun resolvedMediaUrls(): List<String> {
        val multi = mediaUrls.filter { it.isNotBlank() }
        if (multi.isNotEmpty()) return multi
        return listOfNotNull(mediaUrl.takeIf { it.isNotBlank() })
    }

    fun resolvedPrimaryAspectRatio(fallback: Float = 0f): Float {
        if (mediaAspectRatio > 0.05f) return mediaAspectRatio.coerceIn(0.35f, 3.0f)
        val firstFromList = mediaAspectRatios.firstOrNull { it > 0.05f }
        if (firstFromList != null) return firstFromList.coerceIn(0.35f, 3.0f)
        if (mediaWidth > 0 && mediaHeight > 0) {
            return (mediaWidth.toFloat() / mediaHeight.toFloat()).coerceIn(0.35f, 3.0f)
        }
        return fallback
    }

    fun aspectRatioAt(index: Int, fallback: Float = 0f): Float {
        val fromList = mediaAspectRatios.getOrNull(index)
        if (fromList != null && fromList > 0.05f) return fromList.coerceIn(0.35f, 3.0f)
        return if (index == 0) resolvedPrimaryAspectRatio(fallback) else fallback
    }

    fun resolvedHashtags(): List<String> {
        if (hashtags.isNotEmpty()) return hashtags
        return extractHashtags(effectiveText())
    }

    fun resolvedViewCount(): Long = maxOf(viewCount, viewerIds.size.toLong())

    fun effectiveText(): String = caption.ifBlank { text }

    fun isSocialFeedPost(nowMs: Long = System.currentTimeMillis()): Boolean {
        val refMs = createdAt?.let { it.seconds * 1000L } ?: nowMs
        return (expiresAtMs - refMs) > 48L * 3600L * 1000L
    }

    fun isEphemeralStory(nowMs: Long = System.currentTimeMillis()): Boolean =
        !isSocialFeedPost(nowMs)

    companion object {
        private val mentionRegex = Regex("""@([A-Za-z0-9._-]{2,32})""")
        private val hashtagRegex = Regex("""#([\p{L}\p{N}_]{2,32})""")

        fun extractMentions(content: String): List<String> {
            return mentionRegex.findAll(content)
                .map { it.groupValues[1].lowercase() }
                .distinct()
                .take(15)
                .toList()
        }

        fun extractHashtags(content: String): List<String> {
            return hashtagRegex.findAll(content)
                .map { "#${it.groupValues[1].lowercase()}" }
                .distinct()
                .take(20)
                .toList()
        }
    }
}

@IgnoreExtraProperties
data class StatusComment(
    @DocumentId val id: String = "",
    val statusId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val text: String = "",
    val reactionKey: String = "",
    val parentCommentId: String = "",
    val replyToAuthorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LiveLocationShare(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val recipientUids: List<String> = emptyList(),
    val encryptedCoords: String = "",
    val encryptionIv: String = "",
    val senderPublicKey: String = "",
    @get:PropertyName("isEncrypted") @field:PropertyName("isEncrypted")
    val isEncrypted: Boolean = true,
    val active: Boolean = true,
    val expiresAtMs: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class OfferListing(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val title: String = "",
    val offerType: String = "service", // "product" | "service"
    val category: String = "",
    val description: String = "",
    val condition: String = "",
    val locationCity: String = "",
    val priceOrSalary: String = "",
    val contactInfo: String = "",
    val imageUrl: String = "",
    val imageUrls: List<String> = emptyList(),
    val status: String = "published",
    val paymentStatus: String = "free",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedImageUrls(): List<String> {
        val multi = imageUrls.filter { it.isNotBlank() }
        if (multi.isNotEmpty()) return multi
        return listOfNotNull(imageUrl.takeIf { it.isNotBlank() })
    }

    fun resolvedOfferType(): String {
        if (offerType == "product" || offerType == "service") return offerType
        return if (category.equals("Vente", ignoreCase = true)) "product" else "service"
    }
}

@IgnoreExtraProperties
data class AbuseReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetUid: String = "",
    val conversationId: String = "",
    val messageId: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class CallerReportCategory(val raw: String, val labelFr: String) {
    TELEMARKETING("telemarketing", "Démarchage"),
    SPAM("spam", "Spam"),
    SCAM("scam", "Arnaque"),
    BUSINESS("business", "Entreprise"),
    SAFE("safe", "Fiable");

    companion object {
        fun fromRaw(raw: String?): CallerReportCategory =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: SPAM
    }
}

@IgnoreExtraProperties
data class CallerReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val phoneLookupHash: String = "",
    val maskedNumber: String = "",
    val countryIso: String = "",
    val category: String = CallerReportCategory.SPAM.raw,
    val label: String = "",
    val details: String = "",
    val status: String = "pending", // pending | approved | rejected
    val reportCount: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CallerDirectoryEntry(
    @DocumentId val id: String = "",
    val phoneLookupHash: String = "",
    val businessName: String = "",
    val category: String = "business", // business | official | service | safe
    val city: String = "",
    val countryIso: String = "",
    val verified: Boolean = true
)

