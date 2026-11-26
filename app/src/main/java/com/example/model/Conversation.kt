package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participants: List<String> = emptyList(),
    @get:PropertyName("isGroup") @field:PropertyName("isGroup")
    val isGroup: Boolean = false,
    val groupName: String = "",
    val groupDescription: String = "",
    val groupAvatarUrl: String = "",
    val ownerId: String = "",
    val adminIds: List<String> = emptyList(),
    val inviteCode: String = "",
    val onlyAdminsCanMessage: Boolean = false,
    val onlyAdminsCanEditInfo: Boolean = true,
    val participantNames: Map<String, String> = emptyMap(),
    val participantAvatars: Map<String, String> = emptyMap(),
    val participantPublicKeys: Map<String, String> = emptyMap(),
    val wrappedGroupKeys: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageType: String = "text",
    val pinnedMessageId: String = "",
    val pinnedMessagePreview: String = "",
    @ServerTimestamp val lastMessageTimestamp: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val typingUsers: List<String> = emptyList()
)

@IgnoreExtraProperties
data class ConversationPref(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val archived: Boolean = false,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val locked: Boolean = false,
    val markedUnread: Boolean = false,
    val hiddenLocally: Boolean = false,
    val wallpaperId: String = "default",
    val clearedAtMs: Long = 0L,
    val lastReadAtMs: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
