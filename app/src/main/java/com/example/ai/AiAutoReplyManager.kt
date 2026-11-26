package com.example.ai

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.example.local.AppPreferencesRepository
import com.example.network.WorkerApiClient
import com.example.repository.AiTranslationRepository
import com.example.util.DefaultSmsHelper
import com.example.util.PhoneUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ContactRuleMode(val labelFr: String) {
    AUTO("Automatique"),
    VALIDATE("Valider avant envoi"),
    NEVER("Désactivé");

    companion object {
        fun fromId(raw: String?): ContactRuleMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTO
    }
}

data class AutoReplyContactRule(
    val contactKey: String,
    val contactLabel: String,
    val mode: ContactRuleMode = ContactRuleMode.AUTO,
    val toneOverride: String = ""
)

data class PendingAutoReply(
    val id: String = UUID.randomUUID().toString(),
    val channel: String, // "SMS" or "BRAV Chat"
    val target: String,
    val conversationId: String = "",
    val senderName: String = "",
    val incomingPreview: String,
    val suggestedReply: String,
    val model: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

data class AutoReplyLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val channel: String, // "SMS" or "BRAV Chat"
    val target: String,
    val incomingPreview: String,
    val replyText: String,
    val model: String,
    val timestampMs: Long = System.currentTimeMillis()
)

sealed interface SmsAutoReplyOutcome {
    data class Sent(val entry: AutoReplyLogEntry) : SmsAutoReplyOutcome
    data class Queued(val pending: PendingAutoReply) : SmsAutoReplyOutcome
}

object AiAutoReplyManager {

    private const val TAG = "AiAutoReplyManager"
    private const val SMS_COOLDOWN_MS = 45_000L
    private const val BRAV_COOLDOWN_MS = 20_000L

    val availableTones = listOf("Professionnel", "Amical", "Concis", "Académique", "Empathique")

    private val recentSmsSenders = ConcurrentHashMap<String, Long>()
    private val recentBravMessageIds = ConcurrentHashMap<String, Long>()
    private val recentBravConversations = ConcurrentHashMap<String, Long>()

    private val _logs = MutableStateFlow<List<AutoReplyLogEntry>>(emptyList())
    val logs: StateFlow<List<AutoReplyLogEntry>> = _logs.asStateFlow()

    private val _pendingReplies = MutableStateFlow<List<PendingAutoReply>>(emptyList())
    val pendingReplies: StateFlow<List<PendingAutoReply>> = _pendingReplies.asStateFlow()

    fun parseContactRules(rawJson: String): List<AutoReplyContactRule> {
        if (rawJson.isBlank() || rawJson == "[]") return emptyList()
        return runCatching {
            val arr = JSONArray(rawJson)
            (0 until arr.length()).mapNotNull { idx ->
                val o = arr.optJSONObject(idx) ?: return@mapNotNull null
                val key = o.optString("contactKey")
                if (key.isBlank()) null
                else AutoReplyContactRule(
                    contactKey = key,
                    contactLabel = o.optString("contactLabel", key),
                    mode = ContactRuleMode.fromId(o.optString("mode")),
                    toneOverride = o.optString("toneOverride")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun serializeContactRules(rules: List<AutoReplyContactRule>): String {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(JSONObject().apply {
                put("contactKey", r.contactKey)
                put("contactLabel", r.contactLabel)
                put("mode", r.mode.name)
                put("toneOverride", r.toneOverride)
            })
        }
        return arr.toString()
    }

    fun resolveRuleForContact(
        contactCandidates: List<String>,
        rulesJson: String,
        globalValidationRequired: Boolean,
        defaultTone: String
    ): Pair<ContactRuleMode, String> {
        val rules = parseContactRules(rulesJson)
        val normalizedCandidates = contactCandidates.flatMap { raw ->
            val trimmed = raw.trim()
            val phoneNorm = PhoneUtils.normalize(trimmed)
            listOfNotNull(
                trimmed.takeIf { it.isNotBlank() }?.lowercase(),
                phoneNorm.takeIf { it.isNotBlank() }?.lowercase()
            )
        }.toSet()

        val matched = rules.firstOrNull { rule ->
            val rk = rule.contactKey.trim().lowercase()
            val rn = PhoneUtils.normalize(rule.contactKey).lowercase()
            rk in normalizedCandidates || (rn.isNotBlank() && rn in normalizedCandidates)
        }

        val mode = when {
            matched?.mode == ContactRuleMode.NEVER -> ContactRuleMode.NEVER
            matched?.mode == ContactRuleMode.VALIDATE -> ContactRuleMode.VALIDATE
            matched?.mode == ContactRuleMode.AUTO -> ContactRuleMode.AUTO
            globalValidationRequired -> ContactRuleMode.VALIDATE
            else -> ContactRuleMode.AUTO
        }
        val tone = matched?.toneOverride?.takeIf { it.isNotBlank() } ?: defaultTone
        return mode to tone
    }

    fun hasExplicitAutoRule(contactCandidates: List<String>, rulesJson: String): Boolean {
        val rules = parseContactRules(rulesJson)
        val normalizedCandidates = contactCandidates.flatMap { raw ->
            val trimmed = raw.trim()
            val phoneNorm = PhoneUtils.normalize(trimmed)
            listOfNotNull(
                trimmed.takeIf { it.isNotBlank() }?.lowercase(),
                phoneNorm.takeIf { it.isNotBlank() }?.lowercase()
            )
        }.toSet()
        return rules.any { rule ->
            val rk = rule.contactKey.trim().lowercase()
            val rn = PhoneUtils.normalize(rule.contactKey).lowercase()
            rule.mode == ContactRuleMode.AUTO &&
                (rk in normalizedCandidates || (rn.isNotBlank() && rn in normalizedCandidates))
        }
    }

    fun isDirectDotActive(
        contactCandidates: List<String>,
        rulesJson: String,
        globalAutoReplyEnabled: Boolean
    ): Boolean {
        val rules = parseContactRules(rulesJson)
        val normalizedCandidates = contactCandidates.flatMap { raw ->
            val trimmed = raw.trim()
            val phoneNorm = PhoneUtils.normalize(trimmed)
            listOfNotNull(
                trimmed.takeIf { it.isNotBlank() }?.lowercase(),
                phoneNorm.takeIf { it.isNotBlank() }?.lowercase()
            )
        }.toSet()
        val matched = rules.firstOrNull { rule ->
            val rk = rule.contactKey.trim().lowercase()
            val rn = PhoneUtils.normalize(rule.contactKey).lowercase()
            rk in normalizedCandidates || (rn.isNotBlank() && rn in normalizedCandidates)
        }
        return when (matched?.mode) {
            ContactRuleMode.AUTO -> true
            ContactRuleMode.NEVER -> false
            ContactRuleMode.VALIDATE -> false
            null -> globalAutoReplyEnabled
        }
    }

    fun isRepetitiveReply(candidate: String, recentReplies: List<String>): Boolean {
        val cleanCand = candidate.trim().lowercase()
        if (cleanCand.isBlank()) return true
        val candTokens = cleanCand.split(Regex("""\W+""")).filter { it.length > 2 }.toSet()
        return recentReplies.any { prev ->
            val cleanPrev = prev.removePrefix("[Auto-IA]").trim().lowercase()
            if (cleanCand == cleanPrev) return@any true
            if (candTokens.size < 3) return@any false
            val prevTokens = cleanPrev.split(Regex("""\W+""")).filter { it.length > 2 }.toSet()
            if (prevTokens.isEmpty()) return@any false
            val intersect = candTokens.intersect(prevTokens).size.toDouble()
            val union = candTokens.union(prevTokens).size.toDouble().coerceAtLeast(1.0)
            (intersect / union) >= 0.80
        }
    }

    fun shouldAutoReplyToSms(sender: String, body: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val cleanSender = sender.trim()
        val cleanBody = body.trim()
        if (cleanSender.isBlank() || cleanBody.isBlank()) return false
        val digits = cleanSender.filter { it.isDigit() }
        if (cleanSender.any { it.isLetter() } || digits.length < 7) return false
        if (cleanBody.startsWith("[Auto-IA]") || cleanBody.contains("🤖")) return false
        val lower = cleanBody.lowercase()
        if (Regex("""\b(otp|code de vérification|verification code|2fa|code secret)\b""").containsMatchIn(lower)) {
            return false
        }
        val key = PhoneUtils.normalize(cleanSender).ifBlank { digits }
        val last = recentSmsSenders[key]
        if (last != null && nowMs - last < SMS_COOLDOWN_MS) return false
        recentSmsSenders[key] = nowMs
        return true
    }

    fun shouldAutoReplyToBravMessage(
        messageId: String,
        conversationId: String,
        senderId: String,
        currentUserId: String,
        preview: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (senderId.isBlank() || senderId == currentUserId) return false
        val clean = preview.trim()
        if (clean.isBlank() || clean.startsWith("[Auto-IA]") || clean.contains("🤖")) return false
        if (messageId.isNotBlank() && recentBravMessageIds.putIfAbsent(messageId, nowMs) != null) return false
        val lastConv = recentBravConversations[conversationId]
        if (lastConv != null && nowMs - lastConv < BRAV_COOLDOWN_MS) return false
        recentBravConversations[conversationId] = nowMs
        return true
    }

    fun enqueuePendingReply(pending: PendingAutoReply) {
        _pendingReplies.value = (listOf(pending) + _pendingReplies.value.filterNot { it.id == pending.id }).take(25)
    }

    fun removePendingReply(pendingId: String): PendingAutoReply? {
        val current = _pendingReplies.value
        val found = current.firstOrNull { it.id == pendingId } ?: return null
        _pendingReplies.value = current.filterNot { it.id == pendingId }
        return found
    }

    fun recordLog(entry: AutoReplyLogEntry) {
        _logs.value = (listOf(entry) + _logs.value).take(40)
    }

    suspend fun handleIncomingSmsAutoReply(
        context: Context,
        sender: String,
        body: String
    ): SmsAutoReplyOutcome? {
        val prefs = runCatching { AppPreferencesRepository(context).state.first() }.getOrNull() ?: return null
        val explicitAuto = hasExplicitAutoRule(listOf(sender), prefs.aiAutoReplyContactRulesJson)
        if (!prefs.aiAutoReplySmsEnabled && !explicitAuto) return null
        if (!DefaultSmsHelper.hasSmsSendPermission(context)) return null
        if (!shouldAutoReplyToSms(sender, body)) return null

        val (effectiveMode, effectiveTone) = resolveRuleForContact(
            contactCandidates = listOf(sender),
            rulesJson = prefs.aiAutoReplyContactRulesJson,
            globalValidationRequired = prefs.aiAutoReplyValidationRequired,
            defaultTone = prefs.aiAutoReplyTone
        )
        if (effectiveMode == ContactRuleMode.NEVER) return null

        return try {
            val recentForTarget = _logs.value.filter { it.target == sender }.take(5).map { it.replyText }
            val aiRepo = AiTranslationRepository(WorkerApiClient())
            val res = aiRepo.generateAutoReply(
                channel = "sms",
                senderName = sender,
                incomingMessage = body,
                instruction = prefs.aiAutoReplyInstruction,
                preferredModel = prefs.preferredAiModel,
                tone = effectiveTone,
                recentReplies = recentForTarget
            )
            val aiReply = res.getOrNull() ?: return null
            val finalSmsText = aiReply.reply.trim().take(320)
            if (finalSmsText.isBlank() || isRepetitiveReply(finalSmsText, recentForTarget)) return null

            if (effectiveMode == ContactRuleMode.VALIDATE) {
                val pending = PendingAutoReply(
                    channel = "SMS",
                    target = sender,
                    senderName = sender,
                    incomingPreview = body.take(140),
                    suggestedReply = finalSmsText,
                    model = aiReply.model
                )
                enqueuePendingReply(pending)
                return SmsAutoReplyOutcome.Queued(pending)
            }

            sendSmsDirect(context, sender, finalSmsText)
            val entry = AutoReplyLogEntry(
                channel = "SMS",
                target = sender,
                incomingPreview = body.take(120),
                replyText = finalSmsText,
                model = aiReply.model
            )
            recordLog(entry)
            SmsAutoReplyOutcome.Sent(entry)
        } catch (e: Exception) {
            Log.w(TAG, "SMS auto-reply failed: ${e.message}")
            null
        }
    }

    fun sendSmsDirect(context: Context, sender: String, text: String): Boolean {
        val smsMgr = context.getSystemService(SmsManager::class.java) ?: return false
        val parts = smsMgr.divideMessage(text)
        if (parts.size > 1) {
            smsMgr.sendMultipartTextMessage(sender, null, parts, null, null)
        } else {
            smsMgr.sendTextMessage(sender, null, text, null, null)
        }
        if (DefaultSmsHelper.isBravDefaultSms(context)) {
            runCatching {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, sender)
                    put(Telephony.Sms.BODY, text)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                }
                context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                context.contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)
            }
        }
        return true
    }
}
