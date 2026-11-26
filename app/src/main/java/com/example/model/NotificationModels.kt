package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class NotificationCategory(val raw: String, val labelFr: String) {
    ALL("all", "Toutes"),
    MESSAGES("messages", "Messages"),
    CONTACTS("contacts", "Contacts"),
    ACTUS("actus", "ACTUS"),
    CALLS("calls", "Appels"),
    OFFERS_JOBS("offers_jobs", "Offres et emploi"),
    ACCOUNT_SECURITY("account_security", "Compte et sécurité");

    companion object {
        fun fromRaw(raw: String?): NotificationCategory =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: MESSAGES

        val filterTabs: List<NotificationCategory> = listOf(
            ALL,
            MESSAGES,
            CONTACTS,
            ACTUS,
            CALLS,
            OFFERS_JOBS,
            ACCOUNT_SECURITY
        )
    }
}

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderAvatar: String = "",
    val category: String = NotificationCategory.MESSAGES.raw,
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val refId: String = "",
    val refType: String = "",
    val subRefId: String = "",
    val dedupKey: String = "",
    val priority: String = "normal",
    @get:PropertyName("isRead") @field:PropertyName("isRead")
    val isRead: Boolean = false,
    val readAtMs: Long = 0L,
    val createdAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val categoryEnum: NotificationCategory
        get() = NotificationCategory.fromRaw(category)

    val effectiveTimestampMs: Long
        get() = when {
            createdAtMs > 0L -> createdAtMs
            createdAt != null -> createdAt.seconds * 1000L + createdAt.nanoseconds / 1_000_000L
            else -> 0L
        }
}

data class NotificationCounters(
    val totalUnread: Int = 0,
    val messagesUnread: Int = 0,
    val contactsUnread: Int = 0,
    val actusUnread: Int = 0,
    val callsUnread: Int = 0,
    val offersJobsUnread: Int = 0,
    val accountSecurityUnread: Int = 0
) {
    fun countFor(category: NotificationCategory): Int = when (category) {
        NotificationCategory.ALL -> totalUnread
        NotificationCategory.MESSAGES -> messagesUnread
        NotificationCategory.CONTACTS -> contactsUnread
        NotificationCategory.ACTUS -> actusUnread
        NotificationCategory.CALLS -> callsUnread
        NotificationCategory.OFFERS_JOBS -> offersJobsUnread
        NotificationCategory.ACCOUNT_SECURITY -> accountSecurityUnread
    }
}
