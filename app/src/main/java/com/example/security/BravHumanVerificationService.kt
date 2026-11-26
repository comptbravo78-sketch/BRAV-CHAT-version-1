package com.example.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.FaceDetector
import com.example.model.LiveSelfieAssessment
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Service local de vérification de présence humaine et d'anti-usurpation
 * pour la certification CERTIFICATION par selfie caméra frontale en direct.
 * Aucune pièce d'identité ou vidéo n'est requise.
 */
object BravHumanVerificationService {

    data class AnalyzedSelfiePayload(
        val assessment: LiveSelfieAssessment,
        val jpegBytes: ByteArray
    )

    fun analyzeAndEncodeSelfie(bitmap: Bitmap): AnalyzedSelfiePayload {
        val scaled = scaleForAnalysis(bitmap, maxWidth = 480)
        val rgb565 = if (scaled.config == Bitmap.Config.RGB_565) {
            scaled
        } else {
            scaled.copy(Bitmap.Config.RGB_565, false)
        }

        // 1) Détection faciale native Android (even width requis par FaceDetector)
        val evenWidth = if (rgb565.width % 2 == 0) rgb565.width else rgb565.width - 1
        val detectorBmp = if (evenWidth != rgb565.width) {
            Bitmap.createBitmap(rgb565, 0, 0, evenWidth, rgb565.height)
        } else {
            rgb565
        }
        val faces = arrayOfNulls<FaceDetector.Face>(3)
        val faceCount = runCatching {
            FaceDetector(detectorBmp.width, detectorBmp.height, 3).findFaces(detectorBmp, faces)
        }.getOrDefault(0)

        val primaryConfidence = faces.firstOrNull()?.confidence() ?: 0f
        val eyesDistance = faces.firstOrNull()?.eyesDistance() ?: 0f

        // 2) Métriques optiques (luminosité, contraste, netteté Laplacienne, suspicion écran)
        val w = detectorBmp.width
        val h = detectorBmp.height
        val pixels = IntArray(w * h)
        detectorBmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val luma = DoubleArray(w * h) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            0.299 * r + 0.587 * g + 0.114 * b
        }

        val assessment = evaluateMetrics(
            luma = luma,
            width = w,
            height = h,
            faceCount = faceCount,
            faceConfidence = primaryConfidence,
            eyesDistance = eyesDistance
        )

        // 3) Encodage JPEG propre pour stockage privé R2 uniquement (private/verif/)
        val outStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 86, outStream)
        val jpegBytes = outStream.toByteArray()

        if (detectorBmp !== rgb565 && !detectorBmp.isRecycled) detectorBmp.recycle()
        if (rgb565 !== scaled && !rgb565.isRecycled) rgb565.recycle()
        if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()

        return AnalyzedSelfiePayload(assessment = assessment, jpegBytes = jpegBytes)
    }

    fun analyzeJpegBytes(jpegBytes: ByteArray): LiveSelfieAssessment {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts)
            ?: return LiveSelfieAssessment(
                passed = false,
                spoofRisk = "high",
                feedbackFr = "Capture illisible — veuillez reprendre votre selfie en direct."
            )
        return try {
            analyzeAndEncodeSelfie(bmp).assessment
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    internal fun evaluateMetrics(
        luma: DoubleArray,
        width: Int,
        height: Int,
        faceCount: Int,
        faceConfidence: Float,
        eyesDistance: Float
    ): LiveSelfieAssessment {
        if (luma.isEmpty() || width < 16 || height < 16) {
            return LiveSelfieAssessment(
                passed = false,
                spoofRisk = "high",
                feedbackFr = "Image invalide — réessayez dans un endroit lumineux."
            )
        }

        var sum = 0.0
        var saturatedWhite = 0
        for (v in luma) {
            sum += v
            if (v > 246.0) saturatedWhite++
        }
        val meanLuma = sum / luma.size
        val glareRatio = saturatedWhite.toDouble() / luma.size

        var varianceSum = 0.0
        for (v in luma) {
            val d = v - meanLuma
            varianceSum += d * d
        }
        val stdDev = sqrt(varianceSum / luma.size)

        // Netteté via gradient local (Sobel/Laplacien simplifié)
        var gradSum = 0.0
        var gradCount = 0
        for (y in 1 until height - 1 step 2) {
            val row = y * width
            for (x in 1 until width - 1 step 2) {
                val c = luma[row + x]
                val dx = abs(luma[row + x + 1] - c)
                val dy = abs(luma[(y + 1) * width + x] - c)
                gradSum += (dx + dy)
                gradCount++
            }
        }
        val avgGrad = if (gradCount > 0) gradSum / gradCount else 0.0
        val sharpnessScore = (avgGrad / 24.0).coerceIn(0.0, 1.0)
        val contrastScore = (stdDev / 52.0).coerceIn(0.0, 1.0)
        val eyeRatio = (eyesDistance / width.coerceAtLeast(1)).coerceIn(0f, 1f)

        // Estimation anti-usurpation (détection photo d'écran / trop plate / reflet excessif / visage absent)
        val spoofRisk = when {
            faceCount != 1 -> "high"
            glareRatio > 0.22 || stdDev < 14.0 -> "high"
            sharpnessScore < 0.25 || eyeRatio < 0.06f -> "medium"
            else -> "low"
        }

        val livenessScore = (
            (if (faceCount == 1) 0.42 else 0.0) +
                (faceConfidence.coerceIn(0f, 1f) * 0.18) +
                (sharpnessScore * 0.22) +
                (contrastScore * 0.18)
            ).coerceIn(0.0, 1.0)

        val reason = when {
            faceCount == 0 -> "Aucun visage humain détecté. Placez votre visage bien au centre de l'ovale."
            faceCount > 1 -> "Plusieurs visages détectés. Un seul visage est autorisé."
            meanLuma < 38.0 -> "Éclairage trop faible. Placez-vous face à une source de lumière."
            meanLuma > 234.0 || glareRatio > 0.24 -> "Surexposition ou reflet d'écran détecté. Évitez le contre-jour."
            sharpnessScore < 0.35 -> "Image floue. Tenez le téléphone stable avant de capturer."
            spoofRisk == "high" -> "Vérification anti-usurpation échouée (photo d'écran ou contraste plat détecté)."
            else -> "Présence humaine confirmée — capture nette et conforme."
        }

        val passed = faceCount == 1 &&
            meanLuma in 38.0..234.0 &&
            sharpnessScore >= 0.35 &&
            livenessScore >= 0.65 &&
            spoofRisk != "high"

        return LiveSelfieAssessment(
            passed = passed,
            faceCount = faceCount,
            livenessScore = livenessScore,
            sharpnessScore = sharpnessScore,
            luminance = meanLuma,
            spoofRisk = spoofRisk,
            feedbackFr = reason
        )
    }

    private fun scaleForAnalysis(source: Bitmap, maxWidth: Int): Bitmap {
        if (source.width <= maxWidth) {
            return if (source.width % 2 == 0) source
            else Bitmap.createScaledBitmap(source, (source.width - 1).coerceAtLeast(2), source.height, true)
        }
        val ratio = maxWidth.toFloat() / source.width.toFloat()
        val targetW = if (maxWidth % 2 == 0) maxWidth else maxWidth - 1
        val targetH = (source.height * ratio).toInt().coerceAtLeast(2)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }
}
