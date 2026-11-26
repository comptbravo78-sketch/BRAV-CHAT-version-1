package com.example.repository

import android.util.Log
import com.example.local.AiCacheEntity
import com.example.local.BravLocalDatabase
import com.example.network.WorkerAiModel
import com.example.network.WorkerAiReply
import com.example.network.WorkerApiClient
import com.example.network.WorkerDocumentResponse
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest

class AiTranslationRepository(
    private val workerApiClient: WorkerApiClient? = null,
    private val localDb: BravLocalDatabase? = null
) {

    suspend fun getAvailableModels(): List<WorkerAiModel> {
        val remote = workerApiClient?.fetchAiModels()?.getOrNull().orEmpty()
        if (remote.isEmpty()) return defaultFreeModels
        val byId = LinkedHashMap<String, WorkerAiModel>()
        remote.forEach { byId[it.id] = it }
        defaultFreeModels.forEach { fallback ->
            byId.putIfAbsent(fallback.id, fallback)
        }
        return byId.values.toList()
    }

    private suspend fun ensureFrenchReply(client: WorkerApiClient, rawText: String): String {
        val cleaned = stripThinkingTags(rawText)
        if (cleaned.isBlank() || !looksPredominantlyEnglish(cleaned)) return cleaned
        val translated = runCatching {
            client.aiTranslate(cleaned, "Français").getOrNull()?.let { stripThinkingTags(it) }
        }.getOrNull()
        return if (!translated.isNullOrBlank()) translated else cleaned
    }

    suspend fun askAssistant(
        prompt: String,
        history: List<Pair<String, String>> = emptyList(),
        preferredModel: String = "@cf/meta/llama-3.1-8b-instruct",
        systemPrompt: String = "",
        intent: String = ""
    ): Result<WorkerAiReply> {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            return Result.failure(IllegalArgumentException("Message vide"))
        }

        val enforcedSystemPrompt = if (systemPrompt.isBlank()) {
            "$STRICT_FRENCH_SYSTEM_RULE $MATH_PEDAGOGY_SYSTEM_RULE Tu es l'assistant intelligent et professeur de mathématiques de BRAV Chat."
        } else {
            "$STRICT_FRENCH_SYSTEM_RULE $systemPrompt"
        }

        val cacheKey = sha256("cf_chat_fr_v3:$preferredModel:$intent:$enforcedSystemPrompt:$cleanPrompt")
        if (history.isEmpty()) {
            val cached = runCatching { localDb?.aiCacheDao()?.get(cacheKey) }.getOrNull()
            if (cached != null && cached.responseText.isNotBlank() && !looksPredominantlyEnglish(cached.responseText)) {
                return Result.success(WorkerAiReply(cached.model, stripThinkingTags(cached.responseText), cached = true))
            }
        }

        val client = workerApiClient
            ?: return Result.failure(IllegalStateException("Assistant IA non initialisé."))

        return try {
            val workerRes = client.aiChat(
                prompt = cleanPrompt,
                history = history,
                model = preferredModel,
                systemPrompt = enforcedSystemPrompt,
                intent = intent
            )
            if (workerRes.isSuccess) {
                val reply = workerRes.getOrThrow()
                val frenchText = ensureFrenchReply(client, reply.reply)
                if (frenchText.isBlank()) {
                    Result.failure(IllegalStateException("Réponse vide de l'Assistant IA."))
                } else {
                    val sanitized = reply.copy(reply = frenchText)
                    if (history.isEmpty()) {
                        runCatching {
                            localDb?.aiCacheDao()?.put(
                                AiCacheEntity(cacheKey = cacheKey, model = sanitized.model, responseText = sanitized.reply)
                            )
                        }
                    }
                    Result.success(sanitized)
                }
            } else {
                val err = workerRes.exceptionOrNull()
                Log.w(TAG, "Cloudflare Workers AI error: ${err?.message}")
                Result.failure(
                    IllegalStateException(
                        err?.localizedMessage ?: "Assistant IA momentanément indisponible."
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cloudflare Assistant error", e)
            Result.failure(
                IllegalStateException(
                    e.localizedMessage ?: "Assistant IA momentanément indisponible."
                )
            )
        }
    }

    suspend fun generateAutoReply(
        channel: String,
        senderName: String,
        incomingMessage: String,
        instruction: String,
        preferredModel: String = "@cf/meta/llama-3.1-8b-instruct",
        tone: String = "Professionnel",
        recentReplies: List<String> = emptyList(),
        history: List<Pair<String, String>> = emptyList()
    ): Result<WorkerAiReply> {
        val cleanMsg = incomingMessage.trim()
        if (cleanMsg.isBlank()) {
            return Result.failure(IllegalArgumentException("Message entrant vide"))
        }
        val client = workerApiClient
            ?: return Result.failure(IllegalStateException("Assistant IA non initialisé."))
        return try {
            val res = client.aiAutoReply(
                channel = channel,
                senderName = senderName.ifBlank { "Contact" },
                incomingMessage = cleanMsg,
                instruction = "$STRICT_FRENCH_SYSTEM_RULE $instruction",
                model = preferredModel,
                tone = tone,
                recentReplies = recentReplies,
                history = history
            )
            res.mapCatching { reply ->
                val text = ensureFrenchReply(client, reply.reply)
                if (text.isBlank()) error("Réponse automatique vide")
                reply.copy(reply = text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cloudflare auto-reply error", e)
            Result.failure(e)
        }
    }

    suspend fun solveMathWithCloudflareAi(
        topicName: String,
        expression: String,
        localSummary: String = "",
        preferredModel: String = "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b"
    ): Result<WorkerAiReply> {
        val clean = expression.trim()
        if (clean.isBlank()) {
            return Result.failure(IllegalArgumentException("Expression mathématique vide"))
        }
        val cacheKey = sha256("cf_math_latex_fr_v3:$preferredModel:$topicName:$clean:$localSummary")
        val cached = runCatching { localDb?.aiCacheDao()?.get(cacheKey) }.getOrNull()
        if (cached != null && cached.responseText.isNotBlank() && !looksPredominantlyEnglish(cached.responseText)) {
            return Result.success(WorkerAiReply(cached.model, stripThinkingTags(cached.responseText), cached = true))
        }

        val client = workerApiClient
            ?: return Result.failure(IllegalStateException("Assistant IA non initialisé."))
        return try {
            val res = client.aiMathExplain(
                topic = topicName,
                expression = clean,
                localSummary = localSummary,
                model = preferredModel
            )
            if (res.isSuccess) {
                val reply = res.getOrThrow()
                val frenchText = ensureFrenchReply(client, reply.reply)
                if (frenchText.isNotBlank()) {
                    val sanitized = reply.copy(reply = frenchText)
                    runCatching {
                        localDb?.aiCacheDao()?.put(
                            AiCacheEntity(cacheKey = cacheKey, model = sanitized.model, responseText = sanitized.reply)
                        )
                    }
                    Result.success(sanitized)
                } else {
                    Result.failure(IllegalStateException("Réponse mathématique vide."))
                }
            } else {
                Result.failure(res.exceptionOrNull() ?: IllegalStateException("Erreur Assistant IA Math"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cloudflare AI Math error", e)
            Result.failure(e)
        }
    }

    suspend fun generateDocumentWithCloudflareAi(
        prompt: String,
        templateType: String = "RAPPORT",
        preferredModel: String = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
    ): Result<WorkerDocumentResponse> {
        val client = workerApiClient
            ?: return Result.failure(IllegalStateException("Assistant IA non initialisé."))
        return try {
            val res = client.aiGenerateDocument(
                prompt = "$prompt (Rédige obligatoirement l'intégralité du document en français uniquement.)",
                templateType = templateType,
                model = preferredModel
            )
            res.map { doc ->
                val frenchBody = ensureFrenchReply(client, doc.bodyMarkdown)
                val frenchTitle = ensureFrenchReply(client, doc.title)
                doc.copy(title = frenchTitle, bodyMarkdown = frenchBody)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun translateMessage(
        text: String,
        targetLanguage: String,
        preferredModel: String = "@cf/meta/llama-3.1-8b-instruct"
    ): Result<String> {
        if (text.isBlank()) return Result.failure(IllegalArgumentException("Message vide"))
        val cacheKey = sha256("cf_tr:$preferredModel:$targetLanguage:$text")
        val cached = runCatching { localDb?.aiCacheDao()?.get(cacheKey) }.getOrNull()
        if (cached != null && cached.responseText.isNotBlank()) {
            return Result.success(cached.responseText)
        }

        val client = workerApiClient
            ?: return Result.failure(IllegalStateException("Assistant IA non initialisé."))

        return try {
            val workerTr = client.aiTranslate(text, targetLanguage, preferredModel)
            val translated = workerTr.getOrNull()?.trim().orEmpty()
            if (translated.isNotBlank()) {
                runCatching {
                    localDb?.aiCacheDao()?.put(
                        AiCacheEntity(cacheKey = cacheKey, model = preferredModel, responseText = translated)
                    )
                }
                Result.success(translated)
            } else {
                Result.failure(
                    workerTr.exceptionOrNull()
                        ?: IllegalStateException("Traduction IA indisponible.")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cloudflare translation failed", e)
            Result.failure(e)
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "AiTranslationRepo"

        const val STRICT_FRENCH_SYSTEM_RULE =
            "RÈGLE ABSOLUE ET IMPÉRATIVE : Réponds OBLIGATOIREMENT et EXCLUSIVEMENT en français. Interdiction formelle d'utiliser l'anglais."

        const val MATH_PEDAGOGY_SYSTEM_RULE =
            "DIRECTIVE PÉDAGOGIQUE MATHÉMATIQUE : Explique toujours les mathématiques étape par étape comme un professeur clair et patient (1. Énoncé et compréhension, 2. Données, 3. Méthode utilisée, 4. Résolution détaillée ligne par ligne, 5. Résultat final, 6. Interprétation). Pour les vecteurs, présente d'abord les vecteurs et leurs coordonnées puis additionne coordonnée par coordonnée. Encadre les formules uniquement avec $...$ ou $$...$$ pour le rendu visuel et n'affiche jamais de commandes LaTeX brutes hors rendu ni de code informatique."

        fun stripThinkingTags(rawText: String): String {
            return rawText
                .replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<think>[\\s\\S]*$", RegexOption.IGNORE_CASE), "")
                .replace("</think>", "", ignoreCase = true)
                .trim()
        }

        fun looksPredominantlyEnglish(rawText: String): Boolean {
            val withoutMath = rawText
                .replace(Regex("\\$\\$[\\s\\S]*?\\$\\$"), " ")
                .replace(Regex("\\$[^$\\n]+\\$"), " ")
                .replace(Regex("```[\\s\\S]*?```"), " ")
                .lowercase()
            val enHits = Regex(
                "\\b(the|and|that|with|this|you|your|is|are|was|were|have|has|will|would|could|should|here|answer|solution|please|hello|hi|thank|thanks|sorry|welcome|sure|therefore|however|because|below|above|following|using|calculate|step)\\b"
            ).findAll(withoutMath).count()
            val frHits = Regex(
                "([éèêëàâäîïôöùûüç]|\\b(le|la|les|des|une|est|sont|pour|avec|dans|sur|par|pas|nous|vous|ils|elles|cette|ceci|cela|donc|car|mais|bonjour|merci|voici|étape|résultat|réponse|calcul|donne|soit)\\b)"
            ).findAll(withoutMath).count()
            if (enHits >= 3 && frHits == 0) return true
            if (enHits >= 4 && enHits >= frHits * 2) return true
            return false
        }

        val defaultFreeModels = listOf(
            WorkerAiModel("@cf/meta/llama-3.3-70b-instruct-fp8-fast", "Llama 3.3 70B Fast (Pro)", "Général"),
            WorkerAiModel("@cf/meta/llama-3.1-8b-instruct", "Llama 3.1 8B (Rapide & Polyvalent)", "Général"),
            WorkerAiModel("@cf/meta/llama-3.1-8b-instruct-fast", "Llama 3.1 8B Fast", "Rapide"),
            WorkerAiModel("@cf/meta/llama-3-8b-instruct", "Llama 3 8B Instruct", "Général"),
            WorkerAiModel("@cf/deepseek-ai/deepseek-r1-distill-qwen-32b", "DeepSeek R1 Qwen 32B (Maths & Logique)", "Maths"),
            WorkerAiModel("@cf/qwen/qwq-32b", "Qwen QwQ 32B (Raisonnement Formel)", "Maths"),
            WorkerAiModel("@cf/qwen/qwen2.5-coder-32b-instruct", "Qwen 2.5 Coder 32B (LaTeX & Code)", "Code & Maths"),
            WorkerAiModel("@cf/qwen/qwen1.5-14b-chat-awq", "Qwen 1.5 14B Chat", "Multilingue"),
            WorkerAiModel("@cf/qwen/qwen1.5-7b-chat-awq", "Qwen 1.5 7B Chat", "Général"),
            WorkerAiModel("@cf/mistral/mistral-7b-instruct-v0.2", "Mistral 7B v0.2 Instruct", "Rédaction"),
            WorkerAiModel("@cf/mistral/mistral-7b-instruct-v0.1", "Mistral 7B v0.1 Instruct", "Général"),
            WorkerAiModel("@cf/google/gemma-7b-it", "Gemma 7B IT", "Général"),
            WorkerAiModel("@cf/google/gemma-2b-it-lora", "Gemma 2B IT (Ultra-Léger)", "Rapide"),
            WorkerAiModel("@cf/meta/llama-3.2-3b-instruct", "Llama 3.2 3B Instruct", "Rapide"),
            WorkerAiModel("@cf/meta/llama-3.2-1b-instruct", "Llama 3.2 1B Instantané", "Rapide"),
            WorkerAiModel("@cf/microsoft/phi-2", "Phi-2 (Sciences & Logique)", "Maths"),
            WorkerAiModel("@cf/tinyllama/tinyllama-1.1b-chat-v1.0", "TinyLlama 1.1B Chat", "Rapide")
        )

        val supportedLanguages = listOf(
            "Français",
            "Anglais",
            "Espagnol",
            "Arabe",
            "Portugais",
            "Allemand",
            "Chinois"
        )
    }
}
