package com.example.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.security.MessageDigest
import java.util.UUID

private val Context.bravPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "brav_chat_prefs")

enum class ThemeMode(val key: String, val label: String) {
    LIGHT("light", "Blanc"),
    DARK("dark", "Sombre Noire"),
    SYSTEM("system", "Système");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: LIGHT
    }
}

enum class VideoAutoPlayMode(val key: String, val labelFr: String) {
    WIFI_AND_MOBILE("wifi_and_mobile", "Wi-Fi et données mobiles"),
    WIFI_ONLY("wifi_only", "Wi-Fi uniquement"),
    DISABLED("disabled", "Désactivée");

    companion object {
        fun fromKey(key: String?): VideoAutoPlayMode =
            entries.firstOrNull { it.key == key } ?: WIFI_AND_MOBILE
    }
}

data class AppPreferencesState(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val compactLayoutEnabled: Boolean = false,
    val dataSaverEnabled: Boolean = false,
    val autoDownloadWifiOnly: Boolean = true,
    val videoAutoPlayMode: VideoAutoPlayMode = VideoAutoPlayMode.WIFI_AND_MOBILE,
    val videoSoundEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val notifyMessages: Boolean = true,
    val notifyCalls: Boolean = true,
    val notifyMissedCalls: Boolean = true,
    val notifyGroups: Boolean = true,
    val notifyStatuses: Boolean = true,
    val notifyContacts: Boolean = true,
    val notifyActusPosts: Boolean = true,
    val notifyActusLikesComments: Boolean = true,
    val notifyOffersJobs: Boolean = true,
    val notifySoundEnabled: Boolean = true,
    val notifyVibrationEnabled: Boolean = true,
    val hideNotificationContent: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStartHour: Int = 22,
    val quietHoursEndHour: Int = 7,
    val noiseSuppressionEnabled: Boolean = true,
    val echoCancellationEnabled: Boolean = true,
    val autoGainControlEnabled: Boolean = true,
    val voicePriorityEnabled: Boolean = true,
    val defaultSpeakerAudio: Boolean = false,
    val startCallMuted: Boolean = false,
    val chatLockPinHash: String = "",
    val deviceId: String = "",
    val biometricSessionLockEnabled: Boolean = false,
    val biometricPromptOffered: Boolean = false,
    val preferredAiModel: String = "@cf/meta/llama-3.1-8b-instruct",
    val aiAutoReplySmsEnabled: Boolean = false,
    val aiAutoReplyBravEnabled: Boolean = false,
    val aiAutoReplyInstruction: String = "Réponds poliment, brièvement et utilement en mon nom.",
    val aiAutoReplyTone: String = "Professionnel",
    val aiAutoReplyValidationRequired: Boolean = false,
    val aiAutoReplyContactRulesJson: String = "[]",
    val pendingAutoRepliesJson: String = "[]",
    val preferredMathTopic: String = "ANALYSE",
    val savedFormulasJson: String = "[]",
    val countryIso: String = "",
    val dialCode: String = "",
    val smsArchivedThreads: Set<String> = emptySet(),
    val smsPinnedThreads: Set<String> = emptySet(),
    val smsLockedThreads: Set<String> = emptySet(),
    val launcherEnabled: Boolean = false,
    val launcherThemeId: String = "brav_signature",
    val launcherIconShape: String = "squircle",
    val launcherIconSizeDp: Int = 56,
    val launcherCustomIconsJson: String = "{}",
    val launcherFoldersJson: String = "[]",
    val launcherDockPackagesJson: String = "[]",
    val launcherWidgetsJson: String = "[\"clock\",\"ai_latex\",\"youtube_mini\"]",
    val launcherWallpaperPath: String = "",
    val launcherClockStyle: String = "EDITORIAL",
    val launcherWeatherCityJson: String = "{\"name\":\"Abidjan\",\"lat\":5.35995,\"lon\":-4.00825}",
    val launcherNotificationBadgesEnabled: Boolean = true,
    val actusSearchHistoryJson: String = "[]",
    val callerIdEnabled: Boolean = true,
    val callerIdSpamWarningsEnabled: Boolean = true,
    val callerIdLocalCacheJson: String = "{}",
    val profileCoverPresetId: String = "royal_heritage",
    val launcherAfricanWallpaperId: String = "kente_royal",
    val recentEmojisJson: String = "[]",
    val favoriteEmojisJson: String = "[]",
    val preferredEmojiSkinTone: String = "",
    val recentStickersJson: String = "[]",
    val favoriteStickersJson: String = "[]",
    val enabledStickerPacksJson: String = "[\"afrique_royale\",\"babi_nouchi\",\"emotions_vibes\",\"amour_amitie\",\"celebrations_brav\",\"motivation_reactions\"]"
) {
    fun isInQuietHours(currentHour: Int): Boolean {
        if (!quietHoursEnabled) return false
        val start = quietHoursStartHour.coerceIn(0, 23)
        val end = quietHoursEndHour.coerceIn(0, 23)
        return if (start <= end) {
            currentHour in start until end
        } else {
            currentHour >= start || currentHour < end
        }
    }
}

class AppPreferencesRepository(private val context: Context) {

    private val dataStore = context.applicationContext.bravPrefsDataStore

    val state: Flow<AppPreferencesState> = dataStore.data.map { prefs ->
        AppPreferencesState(
            themeMode = ThemeMode.fromKey(prefs[KEY_THEME_MODE]),
            compactLayoutEnabled = prefs[KEY_COMPACT_LAYOUT] ?: false,
            dataSaverEnabled = prefs[KEY_DATA_SAVER] ?: false,
            autoDownloadWifiOnly = prefs[KEY_WIFI_ONLY_MEDIA] ?: true,
            videoAutoPlayMode = VideoAutoPlayMode.fromKey(prefs[KEY_VIDEO_AUTOPLAY_MODE]),
            videoSoundEnabled = prefs[KEY_VIDEO_SOUND_ENABLED] ?: true,
            notificationsEnabled = prefs[KEY_NOTIF_MASTER] ?: true,
            notifyMessages = prefs[KEY_NOTIFY_MESSAGES] ?: true,
            notifyCalls = prefs[KEY_NOTIFY_CALLS] ?: true,
            notifyMissedCalls = prefs[KEY_NOTIFY_MISSED_CALLS] ?: true,
            notifyGroups = prefs[KEY_NOTIFY_GROUPS] ?: true,
            notifyStatuses = prefs[KEY_NOTIFY_STATUSES] ?: true,
            notifyContacts = prefs[KEY_NOTIFY_CONTACTS] ?: true,
            notifyActusPosts = prefs[KEY_NOTIFY_ACTUS_POSTS] ?: true,
            notifyActusLikesComments = prefs[KEY_NOTIFY_ACTUS_INTERACTIONS] ?: true,
            notifyOffersJobs = prefs[KEY_NOTIFY_OFFERS_JOBS] ?: true,
            notifySoundEnabled = prefs[KEY_NOTIFY_SOUND] ?: true,
            notifyVibrationEnabled = prefs[KEY_NOTIFY_VIBRATION] ?: true,
            hideNotificationContent = prefs[KEY_HIDE_NOTIF_CONTENT] ?: false,
            quietHoursEnabled = prefs[KEY_QUIET_HOURS_ENABLED] ?: false,
            quietHoursStartHour = prefs[KEY_QUIET_HOURS_START] ?: 22,
            quietHoursEndHour = prefs[KEY_QUIET_HOURS_END] ?: 7,
            noiseSuppressionEnabled = prefs[KEY_NOISE_SUPPRESSION] ?: true,
            echoCancellationEnabled = prefs[KEY_ECHO_CANCELLATION] ?: true,
            autoGainControlEnabled = prefs[KEY_AUTO_GAIN_CONTROL] ?: true,
            voicePriorityEnabled = prefs[KEY_VOICE_PRIORITY] ?: true,
            defaultSpeakerAudio = prefs[KEY_DEFAULT_SPEAKER_AUDIO] ?: false,
            startCallMuted = prefs[KEY_START_CALL_MUTED] ?: false,
            chatLockPinHash = prefs[KEY_LOCK_PIN_HASH] ?: "",
            deviceId = prefs[KEY_DEVICE_ID] ?: "",
            biometricSessionLockEnabled = prefs[KEY_BIOMETRIC_SESSION_LOCK] ?: false,
            biometricPromptOffered = prefs[KEY_BIOMETRIC_PROMPT_OFFERED] ?: false,
            preferredAiModel = prefs[KEY_PREFERRED_AI_MODEL] ?: "@cf/meta/llama-3.1-8b-instruct",
            aiAutoReplySmsEnabled = prefs[KEY_AI_AUTO_REPLY_SMS] ?: false,
            aiAutoReplyBravEnabled = prefs[KEY_AI_AUTO_REPLY_BRAV] ?: false,
            aiAutoReplyInstruction = prefs[KEY_AI_AUTO_REPLY_INSTRUCTION]
                ?: "Réponds poliment, brièvement et utilement en mon nom.",
            aiAutoReplyTone = prefs[KEY_AI_AUTO_REPLY_TONE] ?: "Professionnel",
            aiAutoReplyValidationRequired = prefs[KEY_AI_AUTO_REPLY_VALIDATE] ?: false,
            aiAutoReplyContactRulesJson = prefs[KEY_AI_AUTO_REPLY_RULES_JSON] ?: "[]",
            pendingAutoRepliesJson = prefs[KEY_PENDING_AUTO_REPLIES_JSON] ?: "[]",
            preferredMathTopic = prefs[KEY_PREFERRED_MATH_TOPIC] ?: "ANALYSE",
            savedFormulasJson = prefs[KEY_SAVED_FORMULAS_JSON] ?: "[]",
            countryIso = prefs[KEY_COUNTRY_ISO] ?: "",
            dialCode = prefs[KEY_DIAL_CODE] ?: "",
            smsArchivedThreads = prefs[KEY_SMS_ARCHIVED] ?: emptySet(),
            smsPinnedThreads = prefs[KEY_SMS_PINNED] ?: emptySet(),
            smsLockedThreads = prefs[KEY_SMS_LOCKED] ?: emptySet(),
            launcherEnabled = prefs[KEY_LAUNCHER_ENABLED] ?: false,
            launcherThemeId = prefs[KEY_LAUNCHER_THEME_ID] ?: "brav_signature",
            launcherIconShape = prefs[KEY_LAUNCHER_ICON_SHAPE] ?: "squircle",
            launcherIconSizeDp = prefs[KEY_LAUNCHER_ICON_SIZE_DP] ?: 56,
            launcherCustomIconsJson = prefs[KEY_LAUNCHER_CUSTOM_ICONS] ?: "{}",
            launcherFoldersJson = prefs[KEY_LAUNCHER_FOLDERS] ?: "[]",
            launcherDockPackagesJson = prefs[KEY_LAUNCHER_DOCK] ?: "[]",
            launcherWidgetsJson = prefs[KEY_LAUNCHER_WIDGETS] ?: "[\"clock\",\"ai_latex\",\"youtube_mini\"]",
            launcherWallpaperPath = prefs[KEY_LAUNCHER_WALLPAPER_PATH] ?: "",
            launcherClockStyle = prefs[KEY_LAUNCHER_CLOCK_STYLE] ?: "EDITORIAL",
            launcherWeatherCityJson = prefs[KEY_LAUNCHER_WEATHER_CITY] ?: "{\"name\":\"Abidjan\",\"lat\":5.35995,\"lon\":-4.00825}",
            launcherNotificationBadgesEnabled = prefs[KEY_LAUNCHER_BADGES] ?: true,
            actusSearchHistoryJson = prefs[KEY_ACTUS_SEARCH_HISTORY] ?: "[]",
            callerIdEnabled = prefs[KEY_CALLER_ID_ENABLED] ?: true,
            callerIdSpamWarningsEnabled = prefs[KEY_CALLER_ID_SPAM_WARNINGS] ?: true,
            callerIdLocalCacheJson = prefs[KEY_CALLER_ID_LOCAL_CACHE] ?: "{}",
            profileCoverPresetId = prefs[KEY_PROFILE_COVER_PRESET] ?: "royal_heritage",
            launcherAfricanWallpaperId = prefs[KEY_LAUNCHER_AFRICAN_WALLPAPER] ?: "kente_royal",
            recentEmojisJson = prefs[KEY_RECENT_EMOJIS] ?: "[]",
            favoriteEmojisJson = prefs[KEY_FAVORITE_EMOJIS] ?: "[]",
            preferredEmojiSkinTone = prefs[KEY_EMOJI_SKIN_TONE] ?: "",
            recentStickersJson = prefs[KEY_RECENT_STICKERS] ?: "[]",
            favoriteStickersJson = prefs[KEY_FAVORITE_STICKERS] ?: "[]",
            enabledStickerPacksJson = prefs[KEY_ENABLED_STICKER_PACKS]
                ?: "[\"afrique_royale\",\"babi_nouchi\",\"emotions_vibes\",\"amour_amitie\",\"celebrations_brav\",\"motivation_reactions\"]"
        )
    }

    suspend fun getOrCreateDeviceId(): String {
        val current = dataStore.data.first()[KEY_DEVICE_ID]
        if (!current.isNullOrBlank()) return current
        val generated = "android_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        dataStore.edit { it[KEY_DEVICE_ID] = generated }
        return generated
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.key }
    }

    suspend fun setCompactLayout(enabled: Boolean) {
        dataStore.edit { it[KEY_COMPACT_LAYOUT] = enabled }
    }

    suspend fun setDataSaver(enabled: Boolean) {
        dataStore.edit { it[KEY_DATA_SAVER] = enabled }
    }

    suspend fun setAutoDownloadWifiOnly(enabled: Boolean) {
        dataStore.edit { it[KEY_WIFI_ONLY_MEDIA] = enabled }
    }

    suspend fun setVideoAutoPlayMode(mode: VideoAutoPlayMode) {
        dataStore.edit { it[KEY_VIDEO_AUTOPLAY_MODE] = mode.key }
    }

    suspend fun setVideoSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_VIDEO_SOUND_ENABLED] = enabled }
    }

    suspend fun setBiometricSessionLock(enabled: Boolean) {
        dataStore.edit {
            it[KEY_BIOMETRIC_SESSION_LOCK] = enabled
            it[KEY_BIOMETRIC_PROMPT_OFFERED] = true
        }
    }

    suspend fun markBiometricPromptOffered() {
        dataStore.edit { it[KEY_BIOMETRIC_PROMPT_OFFERED] = true }
    }

    suspend fun setPreferredAiModel(modelId: String) {
        dataStore.edit { it[KEY_PREFERRED_AI_MODEL] = modelId }
    }

    suspend fun setAiAutoReplySms(enabled: Boolean) {
        dataStore.edit { it[KEY_AI_AUTO_REPLY_SMS] = enabled }
    }

    suspend fun setAiAutoReplyBrav(enabled: Boolean) {
        dataStore.edit { it[KEY_AI_AUTO_REPLY_BRAV] = enabled }
    }

    suspend fun setAiAutoReplyInstruction(instruction: String) {
        dataStore.edit {
            it[KEY_AI_AUTO_REPLY_INSTRUCTION] = instruction.trim().take(500)
                .ifBlank { "Réponds poliment, brièvement et utilement en mon nom." }
        }
    }

    suspend fun setAiAutoReplyTone(tone: String) {
        dataStore.edit { it[KEY_AI_AUTO_REPLY_TONE] = tone }
    }

    suspend fun setAiAutoReplyValidationRequired(required: Boolean) {
        dataStore.edit { it[KEY_AI_AUTO_REPLY_VALIDATE] = required }
    }

    suspend fun setAiAutoReplyContactRulesJson(json: String) {
        dataStore.edit { it[KEY_AI_AUTO_REPLY_RULES_JSON] = json }
    }

    suspend fun setPendingAutoRepliesJson(json: String) {
        dataStore.edit { it[KEY_PENDING_AUTO_REPLIES_JSON] = json }
    }

    suspend fun setPreferredMathTopic(topicId: String) {
        dataStore.edit { it[KEY_PREFERRED_MATH_TOPIC] = topicId }
    }

    suspend fun setSavedFormulasJson(json: String) {
        dataStore.edit { it[KEY_SAVED_FORMULAS_JSON] = json }
    }

    suspend fun setLauncherEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_LAUNCHER_ENABLED] = enabled }
    }

    suspend fun setLauncherStyle(themeId: String, iconShape: String, iconSizeDp: Int) {
        dataStore.edit {
            it[KEY_LAUNCHER_THEME_ID] = themeId
            it[KEY_LAUNCHER_ICON_SHAPE] = iconShape
            it[KEY_LAUNCHER_ICON_SIZE_DP] = iconSizeDp.coerceIn(40, 76)
        }
    }

    suspend fun setLauncherCustomIconsJson(json: String) {
        dataStore.edit { it[KEY_LAUNCHER_CUSTOM_ICONS] = json }
    }

    suspend fun setLauncherFoldersJson(json: String) {
        dataStore.edit { it[KEY_LAUNCHER_FOLDERS] = json }
    }

    suspend fun setLauncherDockPackagesJson(json: String) {
        dataStore.edit { it[KEY_LAUNCHER_DOCK] = json }
    }

    suspend fun setLauncherWidgetsJson(json: String) {
        dataStore.edit { it[KEY_LAUNCHER_WIDGETS] = json }
    }

    suspend fun setLauncherWallpaperPath(path: String) {
        dataStore.edit { it[KEY_LAUNCHER_WALLPAPER_PATH] = path }
    }

    suspend fun setLauncherClockStyle(style: String) {
        dataStore.edit { it[KEY_LAUNCHER_CLOCK_STYLE] = style }
    }

    suspend fun setLauncherWeatherCityJson(json: String) {
        dataStore.edit { it[KEY_LAUNCHER_WEATHER_CITY] = json }
    }

    suspend fun setLauncherNotificationBadgesEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_LAUNCHER_BADGES] = enabled }
    }

    suspend fun setActusSearchHistoryJson(json: String) {
        dataStore.edit { it[KEY_ACTUS_SEARCH_HISTORY] = json }
    }

    suspend fun setCallerIdEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_CALLER_ID_ENABLED] = enabled }
    }

    suspend fun setCallerIdSpamWarningsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_CALLER_ID_SPAM_WARNINGS] = enabled }
    }

    suspend fun setCallerIdLocalCacheJson(json: String) {
        dataStore.edit { it[KEY_CALLER_ID_LOCAL_CACHE] = json }
    }

    suspend fun clearCallerIdPrivacyData() {
        dataStore.edit {
            it[KEY_CALLER_ID_LOCAL_CACHE] = "{}"
        }
    }

    suspend fun setProfileCoverPresetId(presetId: String) {
        dataStore.edit { it[KEY_PROFILE_COVER_PRESET] = presetId }
    }

    suspend fun setLauncherAfricanWallpaperId(wallpaperId: String) {
        dataStore.edit { it[KEY_LAUNCHER_AFRICAN_WALLPAPER] = wallpaperId }
    }

    suspend fun setCountryAndDialCode(countryIso: String, dialCode: String) {
        dataStore.edit {
            it[KEY_COUNTRY_ISO] = countryIso
            it[KEY_DIAL_CODE] = dialCode
        }
    }

    suspend fun recordRecentEmoji(emoji: String) {
        if (emoji.isBlank()) return
        dataStore.edit { prefs ->
            val list = parseJsonList(prefs[KEY_RECENT_EMOJIS] ?: "[]").toMutableList()
            list.remove(emoji)
            list.add(0, emoji)
            prefs[KEY_RECENT_EMOJIS] = encodeJsonList(list.take(24))
        }
    }

    suspend fun toggleFavoriteEmoji(emoji: String) {
        if (emoji.isBlank()) return
        dataStore.edit { prefs ->
            val list = parseJsonList(prefs[KEY_FAVORITE_EMOJIS] ?: "[]").toMutableList()
            if (emoji in list) list.remove(emoji) else list.add(0, emoji)
            prefs[KEY_FAVORITE_EMOJIS] = encodeJsonList(list.take(36))
        }
    }

    suspend fun setPreferredEmojiSkinTone(tone: String) {
        dataStore.edit { it[KEY_EMOJI_SKIN_TONE] = tone }
    }

    suspend fun recordRecentSticker(stickerId: String) {
        if (stickerId.isBlank()) return
        dataStore.edit { prefs ->
            val list = parseJsonList(prefs[KEY_RECENT_STICKERS] ?: "[]").toMutableList()
            list.remove(stickerId)
            list.add(0, stickerId)
            prefs[KEY_RECENT_STICKERS] = encodeJsonList(list.take(20))
        }
    }

    suspend fun toggleFavoriteSticker(stickerId: String) {
        if (stickerId.isBlank()) return
        dataStore.edit { prefs ->
            val list = parseJsonList(prefs[KEY_FAVORITE_STICKERS] ?: "[]").toMutableList()
            if (stickerId in list) list.remove(stickerId) else list.add(0, stickerId)
            prefs[KEY_FAVORITE_STICKERS] = encodeJsonList(list.take(36))
        }
    }

    suspend fun setStickerPackEnabled(packId: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val defaultPacks = listOf("afrique_royale", "babi_nouchi", "emotions_vibes", "amour_amitie", "celebrations_brav", "motivation_reactions")
            val current = parseJsonList(prefs[KEY_ENABLED_STICKER_PACKS] ?: encodeJsonList(defaultPacks)).toMutableSet()
            if (enabled) current.add(packId) else current.remove(packId)
            prefs[KEY_ENABLED_STICKER_PACKS] = encodeJsonList(current.toList())
        }
    }

    private fun parseJsonList(raw: String): List<String> = runCatching {
        val arr = org.json.JSONArray(raw)
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val item = arr.optString(i)
                if (item.isNotBlank()) add(item)
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeJsonList(items: List<String>): String =
        org.json.JSONArray(items).toString()

    suspend fun toggleSmsThreadArchived(threadKey: String, archived: Boolean) {
        dataStore.edit { prefs ->
            val next = (prefs[KEY_SMS_ARCHIVED] ?: emptySet()).toMutableSet()
            if (archived) next.add(threadKey) else next.remove(threadKey)
            prefs[KEY_SMS_ARCHIVED] = next
        }
    }

    suspend fun toggleSmsThreadPinned(threadKey: String, pinned: Boolean) {
        dataStore.edit { prefs ->
            val next = (prefs[KEY_SMS_PINNED] ?: emptySet()).toMutableSet()
            if (pinned) next.add(threadKey) else next.remove(threadKey)
            prefs[KEY_SMS_PINNED] = next
        }
    }

    suspend fun toggleSmsThreadLocked(threadKey: String, locked: Boolean) {
        dataStore.edit { prefs ->
            val next = (prefs[KEY_SMS_LOCKED] ?: emptySet()).toMutableSet()
            if (locked) next.add(threadKey) else next.remove(threadKey)
            prefs[KEY_SMS_LOCKED] = next
        }
    }

    suspend fun setNotificationSettings(
        master: Boolean? = null,
        messages: Boolean? = null,
        calls: Boolean? = null,
        missedCalls: Boolean? = null,
        groups: Boolean? = null,
        statuses: Boolean? = null,
        contacts: Boolean? = null,
        actusPosts: Boolean? = null,
        actusLikesComments: Boolean? = null,
        offersJobs: Boolean? = null,
        sound: Boolean? = null,
        vibration: Boolean? = null,
        hideContent: Boolean? = null,
        quietHoursEnabled: Boolean? = null,
        quietHoursStartHour: Int? = null,
        quietHoursEndHour: Int? = null
    ) {
        dataStore.edit { prefs ->
            master?.let { prefs[KEY_NOTIF_MASTER] = it }
            messages?.let { prefs[KEY_NOTIFY_MESSAGES] = it }
            calls?.let { prefs[KEY_NOTIFY_CALLS] = it }
            missedCalls?.let { prefs[KEY_NOTIFY_MISSED_CALLS] = it }
            groups?.let { prefs[KEY_NOTIFY_GROUPS] = it }
            statuses?.let { prefs[KEY_NOTIFY_STATUSES] = it }
            contacts?.let { prefs[KEY_NOTIFY_CONTACTS] = it }
            actusPosts?.let { prefs[KEY_NOTIFY_ACTUS_POSTS] = it }
            actusLikesComments?.let { prefs[KEY_NOTIFY_ACTUS_INTERACTIONS] = it }
            offersJobs?.let { prefs[KEY_NOTIFY_OFFERS_JOBS] = it }
            sound?.let { prefs[KEY_NOTIFY_SOUND] = it }
            vibration?.let { prefs[KEY_NOTIFY_VIBRATION] = it }
            hideContent?.let { prefs[KEY_HIDE_NOTIF_CONTENT] = it }
            quietHoursEnabled?.let { prefs[KEY_QUIET_HOURS_ENABLED] = it }
            quietHoursStartHour?.let { prefs[KEY_QUIET_HOURS_START] = it.coerceIn(0, 23) }
            quietHoursEndHour?.let { prefs[KEY_QUIET_HOURS_END] = it.coerceIn(0, 23) }
        }
    }

    suspend fun setCallAudioSettings(
        noiseSuppression: Boolean? = null,
        echoCancellation: Boolean? = null,
        autoGainControl: Boolean? = null,
        voicePriority: Boolean? = null,
        defaultSpeakerAudio: Boolean? = null,
        startMuted: Boolean? = null
    ) {
        dataStore.edit { prefs ->
            noiseSuppression?.let { prefs[KEY_NOISE_SUPPRESSION] = it }
            echoCancellation?.let { prefs[KEY_ECHO_CANCELLATION] = it }
            autoGainControl?.let { prefs[KEY_AUTO_GAIN_CONTROL] = it }
            voicePriority?.let { prefs[KEY_VOICE_PRIORITY] = it }
            defaultSpeakerAudio?.let { prefs[KEY_DEFAULT_SPEAKER_AUDIO] = it }
            startMuted?.let { prefs[KEY_START_CALL_MUTED] = it }
        }
    }

    suspend fun setChatLockPin(rawPin: String) {
        val hash = if (rawPin.isBlank()) "" else hashPin(rawPin)
        dataStore.edit { it[KEY_LOCK_PIN_HASH] = hash }
    }

    suspend fun verifyChatLockPin(rawPin: String): Boolean {
        val stored = dataStore.data.first()[KEY_LOCK_PIN_HASH].orEmpty()
        if (stored.isBlank()) return false
        return stored == hashPin(rawPin)
    }

    fun computeLocalCacheSizeBytes(): Long {
        fun dirSize(dir: File?): Long {
            if (dir == null || !dir.exists()) return 0L
            return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
        return dirSize(context.cacheDir) + dirSize(File(context.filesDir, "brav_media_cache"))
    }

    fun clearLocalMediaCache(): Long {
        var deleted = 0L
        listOf(context.cacheDir, File(context.filesDir, "brav_media_cache")).forEach { dir ->
            if (dir.exists()) {
                dir.listFiles()?.forEach { child ->
                    deleted += if (child.isFile) child.length() else child.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    child.deleteRecursively()
                }
            }
        }
        return deleted
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("brav-pin-v1:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_COMPACT_LAYOUT = booleanPreferencesKey("compact_layout")
        val KEY_DATA_SAVER = booleanPreferencesKey("data_saver")
        val KEY_WIFI_ONLY_MEDIA = booleanPreferencesKey("wifi_only_media")
        val KEY_VIDEO_AUTOPLAY_MODE = stringPreferencesKey("video_autoplay_mode")
        val KEY_VIDEO_SOUND_ENABLED = booleanPreferencesKey("video_sound_enabled")
        val KEY_NOTIF_MASTER = booleanPreferencesKey("notif_master")
        val KEY_NOTIFY_MESSAGES = booleanPreferencesKey("notify_messages")
        val KEY_NOTIFY_CALLS = booleanPreferencesKey("notify_calls")
        val KEY_NOTIFY_MISSED_CALLS = booleanPreferencesKey("notify_missed_calls")
        val KEY_NOTIFY_GROUPS = booleanPreferencesKey("notify_groups")
        val KEY_NOTIFY_STATUSES = booleanPreferencesKey("notify_statuses")
        val KEY_NOTIFY_CONTACTS = booleanPreferencesKey("notify_contacts")
        val KEY_NOTIFY_ACTUS_POSTS = booleanPreferencesKey("notify_actus_posts")
        val KEY_NOTIFY_ACTUS_INTERACTIONS = booleanPreferencesKey("notify_actus_likes_comments")
        val KEY_NOTIFY_OFFERS_JOBS = booleanPreferencesKey("notify_offers_jobs")
        val KEY_NOTIFY_SOUND = booleanPreferencesKey("notify_sound")
        val KEY_NOTIFY_VIBRATION = booleanPreferencesKey("notify_vibration")
        val KEY_HIDE_NOTIF_CONTENT = booleanPreferencesKey("hide_notif_content")
        val KEY_QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val KEY_QUIET_HOURS_START = intPreferencesKey("quiet_hours_start_hour")
        val KEY_QUIET_HOURS_END = intPreferencesKey("quiet_hours_end_hour")
        val KEY_NOISE_SUPPRESSION = booleanPreferencesKey("call_noise_suppression")
        val KEY_ECHO_CANCELLATION = booleanPreferencesKey("call_echo_cancellation")
        val KEY_AUTO_GAIN_CONTROL = booleanPreferencesKey("call_auto_gain_control")
        val KEY_VOICE_PRIORITY = booleanPreferencesKey("call_voice_priority")
        val KEY_DEFAULT_SPEAKER_AUDIO = booleanPreferencesKey("call_default_speaker_audio")
        val KEY_START_CALL_MUTED = booleanPreferencesKey("call_start_muted")
        val KEY_LOCK_PIN_HASH = stringPreferencesKey("chat_lock_pin_hash")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_BIOMETRIC_SESSION_LOCK = booleanPreferencesKey("biometric_session_lock")
        val KEY_BIOMETRIC_PROMPT_OFFERED = booleanPreferencesKey("biometric_prompt_offered")
        val KEY_PREFERRED_AI_MODEL = stringPreferencesKey("preferred_ai_model")
        val KEY_AI_AUTO_REPLY_SMS = booleanPreferencesKey("ai_auto_reply_sms")
        val KEY_AI_AUTO_REPLY_BRAV = booleanPreferencesKey("ai_auto_reply_brav")
        val KEY_AI_AUTO_REPLY_INSTRUCTION = stringPreferencesKey("ai_auto_reply_instruction")
        val KEY_AI_AUTO_REPLY_TONE = stringPreferencesKey("ai_auto_reply_tone")
        val KEY_AI_AUTO_REPLY_VALIDATE = booleanPreferencesKey("ai_auto_reply_validation_required")
        val KEY_AI_AUTO_REPLY_RULES_JSON = stringPreferencesKey("ai_auto_reply_contact_rules_json")
        val KEY_PENDING_AUTO_REPLIES_JSON = stringPreferencesKey("pending_auto_replies_json")
        val KEY_PREFERRED_MATH_TOPIC = stringPreferencesKey("preferred_math_topic")
        val KEY_SAVED_FORMULAS_JSON = stringPreferencesKey("saved_formulas_json")
        val KEY_COUNTRY_ISO = stringPreferencesKey("country_iso")
        val KEY_DIAL_CODE = stringPreferencesKey("dial_code")
        val KEY_SMS_ARCHIVED = stringSetPreferencesKey("sms_archived_threads")
        val KEY_SMS_PINNED = stringSetPreferencesKey("sms_pinned_threads")
        val KEY_SMS_LOCKED = stringSetPreferencesKey("sms_locked_threads")
        val KEY_LAUNCHER_ENABLED = booleanPreferencesKey("launcher_enabled")
        val KEY_LAUNCHER_THEME_ID = stringPreferencesKey("launcher_theme_id")
        val KEY_LAUNCHER_ICON_SHAPE = stringPreferencesKey("launcher_icon_shape")
        val KEY_LAUNCHER_ICON_SIZE_DP = intPreferencesKey("launcher_icon_size_dp")
        val KEY_LAUNCHER_CUSTOM_ICONS = stringPreferencesKey("launcher_custom_icons_json")
        val KEY_LAUNCHER_FOLDERS = stringPreferencesKey("launcher_folders_json")
        val KEY_LAUNCHER_DOCK = stringPreferencesKey("launcher_dock_packages_json")
        val KEY_LAUNCHER_WIDGETS = stringPreferencesKey("launcher_widgets_json")
        val KEY_LAUNCHER_WALLPAPER_PATH = stringPreferencesKey("launcher_wallpaper_path")
        val KEY_LAUNCHER_CLOCK_STYLE = stringPreferencesKey("launcher_clock_style")
        val KEY_LAUNCHER_WEATHER_CITY = stringPreferencesKey("launcher_weather_city_json")
        val KEY_LAUNCHER_BADGES = booleanPreferencesKey("launcher_notification_badges")
        val KEY_ACTUS_SEARCH_HISTORY = stringPreferencesKey("actus_search_history_json")
        val KEY_CALLER_ID_ENABLED = booleanPreferencesKey("caller_id_enabled")
        val KEY_CALLER_ID_SPAM_WARNINGS = booleanPreferencesKey("caller_id_spam_warnings")
        val KEY_CALLER_ID_LOCAL_CACHE = stringPreferencesKey("caller_id_local_cache_json")
        val KEY_PROFILE_COVER_PRESET = stringPreferencesKey("profile_cover_preset_id")
        val KEY_LAUNCHER_AFRICAN_WALLPAPER = stringPreferencesKey("launcher_african_wallpaper_id")
        val KEY_RECENT_EMOJIS = stringPreferencesKey("recent_emojis_json")
        val KEY_FAVORITE_EMOJIS = stringPreferencesKey("favorite_emojis_json")
        val KEY_EMOJI_SKIN_TONE = stringPreferencesKey("preferred_emoji_skin_tone")
        val KEY_RECENT_STICKERS = stringPreferencesKey("recent_stickers_json")
        val KEY_FAVORITE_STICKERS = stringPreferencesKey("favorite_stickers_json")
        val KEY_ENABLED_STICKER_PACKS = stringPreferencesKey("enabled_sticker_packs_json")
    }
}
