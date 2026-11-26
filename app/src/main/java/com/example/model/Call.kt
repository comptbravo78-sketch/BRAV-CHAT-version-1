package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CallSource(val raw: String, val badgeLabelFr: String) {
    BRAV_INTERNET("brav_internet", "BRAV"),
    GSM_CARRIER("gsm_carrier", "Tél");

    companion object {
        fun fromRaw(raw: String?): CallSource =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: BRAV_INTERNET
    }
}

enum class CallLifecycleState(val raw: String, val labelFr: String, val isTerminal: Boolean = false) {
    LAUNCHING("LAUNCHING", "Lancement..."),
    RINGING("RINGING", "Sonnerie en cours..."),
    ACCEPTED("ACCEPTED", "Appel accepté"),
    ACTIVE("ACTIVE", "En communication"),
    ON_HOLD("ON_HOLD", "En attente"),
    ENDED("ENDED", "Appel terminé", isTerminal = true),
    MISSED("MISSED", "Appel manqué", isTerminal = true),
    FAILED("FAILED", "Échec de l'appel", isTerminal = true),
    UNAVAILABLE("UNAVAILABLE", "Correspondant indisponible", isTerminal = true),
    CONNECTION_LOST("CONNECTION_LOST", "Connexion perdue", isTerminal = true);

    companion object {
        fun fromRaw(raw: String?, legacyStatus: String = "calling", missed: Boolean = false): CallLifecycleState {
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) }?.let { return it }
            if (missed) return MISSED
            return when (legacyStatus.lowercase()) {
                "accepted" -> ACTIVE
                "on_hold" -> ON_HOLD
                "rejected" -> UNAVAILABLE
                "missed" -> MISSED
                "failed" -> FAILED
                "connection_lost" -> CONNECTION_LOST
                "ended" -> ENDED
                else -> RINGING
            }
        }
    }
}

enum class CallAudioRoute(val labelFr: String) {
    EARPIECE("Écouteur"),
    SPEAKER("Haut-parleur"),
    BLUETOOTH("Bluetooth")
}

@IgnoreExtraProperties
data class Call(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerAvatar: String = "",
    val callerUsername: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val receiverAvatar: String = "",
    val receiverUsername: String = "",
    val participantIds: List<String> = emptyList(),
    val callType: String = "audio", // audio | video
    val callSource: String = CallSource.BRAV_INTERNET.raw, // brav_internet | gsm_carrier
    val status: String = "calling", // calling | accepted | on_hold | rejected | ended
    val lifecycleState: String = "",
    val heldByUid: String = "",
    val missed: Boolean = false,
    val durationSeconds: Long = 0L,
    val hiddenForUsers: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    val endedAt: Timestamp? = null
) {
    fun effectiveCallSource(): CallSource = CallSource.fromRaw(callSource)

    fun effectiveLifecycleState(): CallLifecycleState =
        CallLifecycleState.fromRaw(lifecycleState.ifBlank { null }, status, missed)
}

data class Contact(
    val id: String = "",
    val phoneDisplayName: String = "",
    val rawPhoneNumber: String = "",
    val normalizedPhoneNumber: String = "",
    val bravUser: User? = null,
    val phonePhotoUri: String = "",
    val phoneEmail: String = "",
    val localityHint: String = ""
) {
    val isBravUser: Boolean get() = bravUser != null
    val effectiveAvatarUrl: String
        get() = bravUser?.avatarUrl?.takeIf { it.isNotBlank() } ?: phonePhotoUri
    val displayLocation: String
        get() {
            val user = bravUser
            return when {
                user != null && user.locationCity.isNotBlank() && user.locationDetails.isNotBlank() ->
                    "${user.locationCity} (${user.locationDetails})"
                user != null && user.locationCity.isNotBlank() -> user.locationCity
                user != null && user.locationDetails.isNotBlank() -> user.locationDetails
                localityHint.isNotBlank() -> localityHint
                else -> "Localisation non précisée"
            }
        }
    val preferredDisplayName: String
        get() = when {
            phoneDisplayName.isNotBlank() -> phoneDisplayName
            bravUser?.displayName?.isNotBlank() == true -> bravUser.displayName
            else -> normalizedPhoneNumber
        }
}
