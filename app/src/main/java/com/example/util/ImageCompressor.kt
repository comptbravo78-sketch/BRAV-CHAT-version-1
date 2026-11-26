package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageCompressor {

    fun measureBytesDimensions(bytes: ByteArray): Pair<Int, Int> {
        if (bytes.isEmpty()) return 0 to 0
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return opts.outWidth.coerceAtLeast(0) to opts.outHeight.coerceAtLeast(0)
    }

    suspend fun compressForUpload(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1600,
        targetMaxBytes: Int = 1_200_000
    ): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rawMime = resolver.getType(uri)?.lowercase().orEmpty()
        if (rawMime == "image/gif") {
            val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            return@withContext raw to "image/gif"
        }

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }

        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
            val fallback = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            return@withContext fallback to rawMime.ifBlank { "image/jpeg" }
        }

        var inSample = 1
        val maxSide = max(boundsOpts.outWidth, boundsOpts.outHeight)
        while ((maxSide / inSample) > maxDimension * 2) {
            inSample *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = inSample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: return@withContext null

        val scaled = if (max(decoded.width, decoded.height) > maxDimension) {
            val ratio = maxDimension.toFloat() / max(decoded.width, decoded.height).toFloat()
            val w = (decoded.width * ratio).toInt().coerceAtLeast(1)
            val h = (decoded.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, w, h, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val out = ByteArrayOutputStream()
        var quality = 86
        do {
            out.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            quality -= 10
        } while (out.size() > targetMaxBytes && quality >= 46)

        scaled.recycle()
        out.toByteArray() to "image/jpeg"
    }

    suspend fun decodePreviewBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1400
    ): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return@withContext null

        var inSample = 1
        val maxSide = max(boundsOpts.outWidth, boundsOpts.outHeight)
        while ((maxSide / inSample) > maxDimension * 2) {
            inSample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = inSample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: return@withContext null

        if (max(decoded.width, decoded.height) > maxDimension) {
            val ratio = maxDimension.toFloat() / max(decoded.width, decoded.height).toFloat()
            val w = (decoded.width * ratio).toInt().coerceAtLeast(1)
            val h = (decoded.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, w, h, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }
    }

    suspend fun cropAndCompressBitmap(
        source: Bitmap,
        targetAspectRatio: Float,
        zoom: Float = 1f,
        panX: Float = 0f,
        panY: Float = 0f,
        maxDimension: Int = 1600,
        targetMaxBytes: Int = 800_000
    ): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return@withContext null
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()
        val ratio = targetAspectRatio.coerceIn(0.4f, 4f)
        val clampedZoom = zoom.coerceIn(1f, 4f)

        val baseCropW: Float
        val baseCropH: Float
        if ((srcW / srcH) >= ratio) {
            baseCropH = srcH
            baseCropW = baseCropH * ratio
        } else {
            baseCropW = srcW
            baseCropH = baseCropW / ratio
        }

        val cropW = (baseCropW / clampedZoom).coerceIn(1f, srcW)
        val cropH = (baseCropH / clampedZoom).coerceIn(1f, srcH)
        val maxShiftX = ((srcW - cropW) / 2f).coerceAtLeast(0f)
        val maxShiftY = ((srcH - cropH) / 2f).coerceAtLeast(0f)

        val centerX = (srcW / 2f) - panX.coerceIn(-1f, 1f) * maxShiftX
        val centerY = (srcH / 2f) - panY.coerceIn(-1f, 1f) * maxShiftY

        val left = (centerX - cropW / 2f).toInt().coerceIn(0, (source.width - 1).coerceAtLeast(0))
        val top = (centerY - cropH / 2f).toInt().coerceIn(0, (source.height - 1).coerceAtLeast(0))
        val width = cropW.toInt().coerceIn(1, (source.width - left).coerceAtLeast(1))
        val height = cropH.toInt().coerceIn(1, (source.height - top).coerceAtLeast(1))

        val cropped = Bitmap.createBitmap(source, left, top, width, height)
        val scaled = if (max(cropped.width, cropped.height) > maxDimension) {
            val scale = maxDimension.toFloat() / max(cropped.width, cropped.height).toFloat()
            val w = (cropped.width * scale).toInt().coerceAtLeast(1)
            val h = (cropped.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(cropped, w, h, true).also {
                if (it !== cropped) cropped.recycle()
            }
        } else {
            cropped
        }

        val out = ByteArrayOutputStream()
        var quality = 88
        do {
            out.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            quality -= 10
        } while (out.size() > targetMaxBytes && quality >= 48)

        scaled.recycle()
        out.toByteArray() to "image/jpeg"
    }
}

