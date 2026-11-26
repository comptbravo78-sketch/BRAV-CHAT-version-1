package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Message(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val messageType: String = "text", // text | image | video | audio | voice | file | location | contact | sticker
    val stickerId: String = "",
    val stickerPackId: String = "",
    val stickerLabel: String = "",
    @get:PropertyName("isEncrypted") @field:PropertyName("isEncrypted")
    val isEncrypted: Boolean = false,
    val encryptionIv: String = "",
    val senderPublicKey: String = "",
    val mediaUrl: String = "",
    val mediaKey: String = "",
    val mediaMimeType: String = "",
    val mediaName: String = "",
    val mediaSize: Long = 0L,
    val durationMs: Long = 0L,
    val waveform: List<Int> = emptyList(),
    val replyToMessageId: String = "",
    val replyToPreview: String = "",
    val forwardedFrom: String = "",
    val editedAt: Timestamp? = null,
    val deletedForAll: Boolean = false,
    val deletedForUsers: List<String> = emptyList(),
    val pinned: Boolean = false,
    val status: String = "sent", // pending | sent | delivered | read
    val reactions: Map<String, List<String>> = emptyMap(),
    @ServerTimestamp val timestamp: Timestamp? = null,
    val readAt: Timestamp? = null
)

enum class BravReactionCategory(val title: String) {
    ALL("Tous"),
    LUXURY("Prestige"),
    FUTURISTIC("Futuriste"),
    FUNNY("Expressif"),
    CLASSIC("Classiques")
}

enum class BravReactionType(
    val key: String,
    val emoji: String,
    val label: String,
    val category: BravReactionCategory
) {
    // Prestige & Luxe
    CROWN_ELITE("crown_elite", "👑", "Excellence", BravReactionCategory.LUXURY),
    DIAMOND_LUX("diamond_lux", "💎", "Précieux", BravReactionCategory.LUXURY),
    HUNDRED_PURE("hundred_pure", "💯", "Parfait", BravReactionCategory.LUXURY),
    THUMBS_UP_GOLD("thumbs_up_gold", "👍", "Approuvé", BravReactionCategory.LUXURY),
    PRAY_GRATEFUL("pray_grateful", "🙏", "Respect", BravReactionCategory.LUXURY),

    // Futuristes
    CYBER_BOT("cyber_bot", "🤖", "Cyber Bot", BravReactionCategory.FUTURISTIC),
    ROCKET_TURBO("rocket_turbo", "🚀", "Propulsion", BravReactionCategory.FUTURISTIC),
    ALIEN_SIGNAL("alien_signal", "👽", "Signal OVNI", BravReactionCategory.FUTURISTIC),
    TECH_GENIUS("tech_genius", "🦾", "Précision", BravReactionCategory.FUTURISTIC),
    STAR_HOLO("star_holo", "✨", "Éclat", BravReactionCategory.FUTURISTIC),

    // Expressifs & Drôles
    ROFL_DYNAMIC("rofl_dynamic", "🤣", "Fou rire", BravReactionCategory.FUNNY),
    CLOWN_MODE("clown_mode", "🤡", "Taquin", BravReactionCategory.FUNNY),
    ZANY_CRAZY("zany_crazy", "🤪", "Délirant", BravReactionCategory.FUNNY),
    MELTING_LOL("melting_lol", "🫠", "Sous le charme", BravReactionCategory.FUNNY),
    COOL_BOSS("cool_boss", "😎", "Charisme", BravReactionCategory.FUNNY),
    LAUGH_TEARS("laugh_tears", "😂", "Éclat de rire", BravReactionCategory.FUNNY),
    MIND_BLOWN("mind_blown", "🤯", "Stupéfait", BravReactionCategory.FUNNY),

    // Classiques
    HEART_PULSE("heart_pulse", "❤️", "Cœur", BravReactionCategory.CLASSIC),
    HEART_EYES("heart_eyes", "😍", "Admiration", BravReactionCategory.CLASSIC),
    FIRE_FLAME("fire_flame", "🔥", "Brillant", BravReactionCategory.CLASSIC),
    CLAP_HANDS("clap_hands", "👏", "Bravo", BravReactionCategory.CLASSIC),
    SHOCK_BOUNCE("shock_bounce", "😱", "Saisissant", BravReactionCategory.CLASSIC),
    CRY_EXPRESSIVE("cry_expressive", "😭", "Émotion", BravReactionCategory.CLASSIC),
    AFFECTION_HEARTS("affection_hearts", "🥰", "Affection", BravReactionCategory.CLASSIC);

    companion object {
        fun fromKey(key: String): BravReactionType? = entries.firstOrNull { it.key == key }
    }
}
