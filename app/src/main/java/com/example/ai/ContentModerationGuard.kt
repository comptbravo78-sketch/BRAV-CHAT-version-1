package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.network.WorkerApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

data class ModerationVerdict(
    val allowed: Boolean,
    val reasonCode: String = "",
    val userMessage: String = ""
)

class ContentBlockedException(
    val reasonCode: String,
    message: String
) : IllegalArgumentException(message)

/**
 * Multi-layered strict moderation guard for BRAV Chat social posts, comments, statuses, and offers.
 * Blocks nudity, half-nudity, toxic visuals, and abusive/profane/hateful expressions BEFORE upload/publish.
 */
object ContentModerationGuard {

    const val TEXT_BLOCKED_MESSAGE =
        "Publication bloquée : votre message contient des propos injurieux ou contraires à nos règles et ne peut en aucun cas être publié. Modifiez votre texte avant de publier."

    const val MEDIA_BLOCKED_MESSAGE =
        "Publication bloquée : cette photo a été automatiquement identifiée comme ne respectant pas nos règles (nudité, demi-nudité ou contenu toxique) et ne peut en aucun cas être publiée. Changez de photo avant de publier."

    private val exactToxicTokens = setOf(
        // Français - injures, vulgarités, sexuel explicite, menaces
        "pute", "putes", "putain", "putains", "salope", "salopes", "salop", "salopard",
        "connard", "connards", "connasse", "connasses", "encule", "enculer", "encules", "enculee",
        "fdp", "ntm", "nique", "niquer", "niques", "batard", "batards", "merde",
        "bouffon", "bouffonne", "petasse", "trainee", "bordel", "pd", "pede", "pedes",
        "tapette", "gouine", "bougnoule", "negre", "negro", "youpin", "sale",
        "bite", "bites", "couille", "couilles", "chatte", "chattes", "nichon", "nichons",
        "nibard", "seins", "cul", "fesse", "fesses", "branler", "branlette", "suce", "sucer",
        "baise", "baiser", "baisable", "porn", "porno", "pornographie", "sexe", "sexuel",
        "nude", "nudes", "nudite", "teton", "tetons", "penis", "vagin", "clitoris",
        "orgasme", "viol", "violer", "creve", "crever", "suicide", "suicider", "tuer", "egorger",
        "debile", "abrutis", "abruti", "cretin", "cretine", "ordure", "ordures", "sousmerde",
        // Anglais - profanity, explicit, slurs
        "fuck", "fucking", "fucker", "motherfucker", "mf", "bitch", "bitches", "whore",
        "slut", "sluts", "asshole", "dick", "cock", "pussy", "cunt", "boobs", "tits",
        "nipple", "nipples", "naked", "nude", "nudity", "nsfw", "xxx", "porn", "hentai",
        "bastard", "shit", "bullshit", "faggot", "nigger", "nigga", "retard", "kill", "rape"
    )

    private val toxicPhrases = listOf(
        "va te faire", "nique ta", "nique ton", "fils de pute", "ferme ta gueule", "ta gueule",
        "tg", "va crever", "va mourir", "sale race", "sale chien", "sale chienne",
        "montre tes seins", "envoi des nudes", "envoie des nudes", "photo nue", "a moitie nu",
        "seins nus", "corps nu", "go kill yourself", "kys", "son of a bitch", "suck my"
    )

    fun normalizeForModeration(raw: String): String {
        val decomposed = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        val noDiacritics = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val deLeet = noDiacritics
            .replace('0', 'o')
            .replace('1', 'i')
            .replace('3', 'e')
            .replace('4', 'a')
            .replace('5', 's')
            .replace('7', 't')
            .replace('@', 'a')
            .replace('$', 's')
            .replace('*', ' ')
        return deLeet
            .replace(Regex("(.)\\1{2,}"), "$1$1")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun checkText(rawText: String): ModerationVerdict {
        if (rawText.isBlank()) return ModerationVerdict(allowed = true)
        val normalized = normalizeForModeration(rawText)
        val compact = normalized.replace(" ", "")

        // Check phrases
        for (phrase in toxicPhrases) {
            if (normalized.contains(phrase)) {
                return ModerationVerdict(
                    allowed = false,
                    reasonCode = "TOXIC_EXPRESSION",
                    userMessage = TEXT_BLOCKED_MESSAGE
                )
            }
        }

        // Check obfuscated single tokens written with spaces (e.g. "p u t e", "f d p", "n t m")
        val spacedObfuscated = rawText.lowercase()
            .replace(Regex("[^a-z]"), "")
        val collapsedTokens = listOf("filsdepute", "niquetamere", "fermetagueule", "vactefaire", "motherfucker")
        if (collapsedTokens.any { spacedObfuscated.contains(it) }) {
            return ModerationVerdict(
                allowed = false,
                reasonCode = "TOXIC_EXPRESSION",
                userMessage = TEXT_BLOCKED_MESSAGE
            )
        }

        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        for (token in tokens) {
            if (token in exactToxicTokens) {
                return ModerationVerdict(
                    allowed = false,
                    reasonCode = "TOXIC_EXPRESSION",
                    userMessage = TEXT_BLOCKED_MESSAGE
                )
            }
            // Catch common plural/conjugated prefix stems without breaking safe words
            if (token.startsWith("encul") || token.startsWith("putain") || token.startsWith("connard") ||
                token.startsWith("connass") || token.startsWith("salop") || token.startsWith("batard") ||
                token.startsWith("fuck") || token.startsWith("niquer") || token.startsWith("pornograph")
            ) {
                return ModerationVerdict(
                    allowed = false,
                    reasonCode = "TOXIC_EXPRESSION",
                    userMessage = TEXT_BLOCKED_MESSAGE
                )
            }
        }

        // Also catch obfuscated compact forms like "f.u.c.k" -> "fuck" or "p.u.t.e" -> "pute"
        val rawTokens = rawText.lowercase().split(Regex("\\s+"))
        for (rawTok in rawTokens) {
            val stripped = Normalizer.normalize(rawTok, Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .replace(Regex("[^a-z]"), "")
            if (stripped.length >= 3 && stripped in exactToxicTokens) {
                return ModerationVerdict(
                    allowed = false,
                    reasonCode = "TOXIC_EXPRESSION",
                    userMessage = TEXT_BLOCKED_MESSAGE
                )
            }
        }

        if (compact in exactToxicTokens) {
            return ModerationVerdict(
                allowed = false,
                reasonCode = "TOXIC_EXPRESSION",
                userMessage = TEXT_BLOCKED_MESSAGE
            )
        }

        return ModerationVerdict(allowed = true)
    }

    /**
     * Analyzes a decoded image bitmap for nudity / half-nudity / high bare-skin torso exposure.
     * Supports all skin tones (light, olive, medium, deep melanin-rich) via combined YCbCr + HSV/RGB heuristics
     * and distinguishes clothed portraits (upper-center face only) from exposed torso/body nudity.
     */
    fun analyzeBitmapSafety(bitmap: Bitmap): ModerationVerdict {
        val w = 120
        val h = 120
        val scaled = if (bitmap.width == w && bitmap.height == h) bitmap
        else Bitmap.createScaledBitmap(bitmap, w, h, true)

        var totalSkin = 0
        var lowerHalfSkin = 0
        var centerTorsoSkin = 0
        var borderSkin = 0
        val totalPixels = w * h
        val lowerPixels = (h / 2) * w
        val torsoPixels = ((w * 0.6f).toInt()) * ((h * 0.6f).toInt())
        var borderPixels = 0

        val pixels = IntArray(totalPixels)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val xMin = (w * 0.2f).toInt()
        val xMax = (w * 0.8f).toInt()
        val yMin = (h * 0.25f).toInt()
        val yMax = (h * 0.85f).toInt()

        for (y in 0 until h) {
            for (x in 0 until w) {
                val px = pixels[y * w + x]
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)

                val isBorder = x < 12 || x >= w - 12 || y >= h - 14
                if (isBorder) borderPixels++

                if (isSkinPixel(r, g, b)) {
                    totalSkin++
                    if (y >= h / 2) lowerHalfSkin++
                    if (x in xMin until xMax && y in yMin until yMax) centerTorsoSkin++
                    if (isBorder) borderSkin++
                }
            }
        }

        if (scaled !== bitmap) scaled.recycle()

        val totalRatio = totalSkin.toFloat() / totalPixels.toFloat()
        val lowerRatio = lowerHalfSkin.toFloat() / lowerPixels.coerceAtLeast(1).toFloat()
        val torsoRatio = centerTorsoSkin.toFloat() / torsoPixels.coerceAtLeast(1).toFloat()
        val borderRatio = borderSkin.toFloat() / borderPixels.coerceAtLeast(1).toFloat()

        // Flag full nudity or extensive half-nudity (shirtless/bikini/lingerie/explicit body skin)
        val isUnsafeExposure =
            totalRatio >= 0.50f ||
                (torsoRatio >= 0.52f && lowerRatio >= 0.44f) ||
                (lowerRatio >= 0.56f && borderRatio >= 0.30f)

        return if (isUnsafeExposure) {
            ModerationVerdict(
                allowed = false,
                reasonCode = "NUDITY_OR_HALF_NUDITY",
                userMessage = MEDIA_BLOCKED_MESSAGE
            )
        } else {
            ModerationVerdict(allowed = true)
        }
    }

    private fun isSkinPixel(r: Int, g: Int, b: Int): Boolean {
        val rf = r.toFloat()
        val gf = g.toFloat()
        val bf = b.toFloat()

        // YCbCr chromatic skin plane
        val cb = 128f - 0.168736f * rf - 0.331264f * gf + 0.5f * bf
        val cr = 128f + 0.5f * rf - 0.418688f * gf - 0.081312f * bf

        val ycbcrLightMedium = cb in 77f..127f && cr in 133f..176f && r > 80 && g > 45 && b > 25 && (r - g) >= 12
        // Deep melanin-rich skin tones in warm RGB + YCbCr
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val deepWarmSkin = r in 55..175 &&
            g in 30..130 &&
            b in 18..105 &&
            r > g && g >= b - 4 &&
            (r - b) >= 15 &&
            (maxC - minC) >= 14 &&
            cr in 130f..170f &&
            cb in 82f..128f

        return ycbcrLightMedium || deepWarmSkin
    }

    fun checkImageBytes(bytes: ByteArray): ModerationVerdict {
        if (bytes.isEmpty()) return ModerationVerdict(allowed = true)
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return ModerationVerdict(allowed = true)

        var sample = 1
        while (opts.outWidth / sample > 360 || opts.outHeight / sample > 360) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return ModerationVerdict(allowed = true)
        return try {
            analyzeBitmapSafety(bmp)
        } finally {
            bmp.recycle()
        }
    }

    suspend fun checkVideoUri(context: Context, uri: Uri): ModerationVerdict = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val timestampsUs = listOf(
                0L,
                (durationMs * 300L).coerceAtLeast(500_000L),
                (durationMs * 650L).coerceAtLeast(1_000_000L)
            )
            for (tsUs in timestampsUs) {
                val frame = retriever.getFrameAtTime(tsUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val verdict = analyzeBitmapSafety(frame)
                    frame.recycle()
                    if (!verdict.allowed) return@withContext verdict
                }
            }
            ModerationVerdict(allowed = true)
        } catch (_: Throwable) {
            ModerationVerdict(allowed = true)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Full publication moderation combining deterministic on-device guards and remote AI safety classification.
     */
    suspend fun moderatePublication(
        text: String,
        imageBytes: ByteArray? = null,
        isVideo: Boolean = false,
        videoUri: Uri? = null,
        context: Context? = null,
        workerApiClient: WorkerApiClient? = null
    ): ModerationVerdict {
        val localText = checkText(text)
        if (!localText.allowed) return localText

        if (!isVideo && imageBytes != null && imageBytes.isNotEmpty()) {
            val localImg = withContext(Dispatchers.Default) { checkImageBytes(imageBytes) }
            if (!localImg.allowed) return localImg
        }

        if (isVideo && videoUri != null && context != null) {
            val localVid = checkVideoUri(context, videoUri)
            if (!localVid.allowed) return localVid
        }

        if (text.isNotBlank() && workerApiClient != null) {
            val aiRes = runCatching {
                workerApiClient.aiChat(
                    prompt = """
                        Analyse ce texte destiné à une publication sociale publique.
                        Réponds STRICTEMENT par "SAFE" si le texte est acceptable, ou par "UNSAFE" s'il contient des injures, insultes, harcèlement, haine, violence grave ou contenu sexuel/nudité.
                        Texte : "$text"
                    """.trimIndent(),
                    systemPrompt = "Tu es un filtre de modération automatique strict. Réponds uniquement par un seul mot : SAFE ou UNSAFE.",
                    intent = "moderation"
                )
            }.getOrNull()
            val reply = aiRes?.getOrNull()?.reply?.trim()?.uppercase().orEmpty()
            if (reply.startsWith("UNSAFE") || reply.contains("UNSAFE")) {
                return ModerationVerdict(
                    allowed = false,
                    reasonCode = "AI_TEXT_MODERATION",
                    userMessage = TEXT_BLOCKED_MESSAGE
                )
            }
        }

        return ModerationVerdict(allowed = true)
    }
}
