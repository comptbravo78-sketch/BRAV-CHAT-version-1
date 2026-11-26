package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.StatFs
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.max

data class VideoMetadata(
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
    val durationMs: Long = 0L,
    val bitrate: Int = 0,
    val aspectRatio: Float = 0f,
    val sizeBytes: Long = 0L
) {
    val effectiveWidth: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) height else width

    val effectiveHeight: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) width else height
}

data class TrimRange(
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

object VideoTrimProcessor {

    const val MIN_TRIM_DURATION_MS = 1_000L
    private const val MIN_REQUIRED_FREE_BYTES = 12L * 1024L * 1024L

    /**
     * Pure validation/clamping helper for trim start/end bounds.
     */
    fun validateTrimRange(
        startMs: Long,
        endMs: Long,
        totalDurationMs: Long,
        minDurationMs: Long = MIN_TRIM_DURATION_MS
    ): TrimRange {
        if (totalDurationMs <= 0L) return TrimRange(0L, 0L)
        val effectiveMin = minDurationMs.coerceAtMost(totalDurationMs)
        val clampedStart = startMs.coerceIn(0L, (totalDurationMs - effectiveMin).coerceAtLeast(0L))
        val clampedEnd = endMs.coerceIn(clampedStart + effectiveMin, totalDurationMs)
        return TrimRange(startMs = clampedStart, endMs = clampedEnd)
    }

    fun formatDurationMmSs(durationMs: Long): String {
        val totalSec = (durationMs.coerceAtLeast(0L) / 1000L).toInt()
        val mins = totalSec / 60
        val secs = totalSec % 60
        return "%d:%02d".format(mins, secs)
    }

    suspend fun inspectVideo(context: Context, uri: Uri): VideoMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            if (uri.scheme == "file" && uri.path != null) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val effW = if (rot == 90 || rot == 270) rawH else rawW
            val effH = if (rot == 90 || rot == 270) rawW else rawH
            val ratio = if (effW > 0 && effH > 0) (effW.toFloat() / effH.toFloat()).coerceIn(0.35f, 3.0f) else 0f
            val sizeBytes = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
            }.getOrDefault(0L)
            VideoMetadata(
                width = effW,
                height = effH,
                rotationDegrees = rot,
                durationMs = dur,
                bitrate = bitrate,
                aspectRatio = ratio,
                sizeBytes = sizeBytes
            )
        } catch (_: Exception) {
            VideoMetadata()
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun extractTimelineFrames(
        context: Context,
        uri: Uri,
        count: Int = 8,
        thumbHeightPx: Int = 120
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()
        try {
            if (uri.scheme == "file" && uri.path != null) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) return@withContext emptyList()
            val safeCount = count.coerceIn(2, 16)
            for (i in 0 until safeCount) {
                val timeUs = ((durationMs * i.toLong()) / safeCount.toLong()).coerceAtLeast(0L) * 1000L
                val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (raw != null) {
                    val scale = thumbHeightPx.toFloat() / raw.height.coerceAtLeast(1).toFloat()
                    val w = (raw.width * scale).toInt().coerceIn(36, 220)
                    val h = thumbHeightPx.coerceAtLeast(40)
                    val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
                    if (scaled !== raw) raw.recycle()
                    frames += scaled
                }
            }
            frames
        } catch (_: Exception) {
            frames
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun extractThumbnailJpeg(
        context: Context,
        uri: Uri,
        timeMs: Long = 350L,
        maxDimension: Int = 720
    ): ByteArray? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            if (uri.scheme == "file" && uri.path != null) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val raw = retriever.getFrameAtTime(
                timeMs.coerceAtLeast(0L) * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@withContext null
            val scaled = if (max(raw.width, raw.height) > maxDimension) {
                val scale = maxDimension.toFloat() / max(raw.width, raw.height).toFloat()
                val w = (raw.width * scale).toInt().coerceAtLeast(1)
                val h = (raw.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(raw, w, h, true).also {
                    if (it !== raw) raw.recycle()
                }
            } else {
                raw
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            scaled.recycle()
            out.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun copyUriToTempFile(context: Context, uri: Uri, prefix: String = "video_src"): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                if (uri.scheme == "file" && uri.path != null) {
                    val existing = File(uri.path!!)
                    if (existing.exists() && existing.length() > 0L) {
                        return@withContext Result.success(existing)
                    }
                }
                val dir = File(context.cacheDir, "brav_trim_work").apply { mkdirs() }
                val target = File(dir, "${prefix}_${UUID.randomUUID().toString().take(8)}.mp4")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 32 * 1024)
                    }
                } ?: return@withContext Result.failure(IOException("Impossible de lire la vidéo sélectionnée."))
                if (!target.exists() || target.length() <= 0L) {
                    target.delete()
                    return@withContext Result.failure(IOException("Le fichier vidéo est vide."))
                }
                Result.success(target)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Trims [sourceUri] locally to [startMs, endMs] using Media3 Transformer while preserving
     * original aspect ratio, resolution, frame rate, and synchronized audio.
     * Guarantees no partial/incomplete file is ever returned on failure or cancellation.
     */
    @OptIn(UnstableApi::class)
    suspend fun trimVideoLocally(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        totalDurationMs: Long,
        onProgress: (Float) -> Unit = {}
    ): Result<File> {
        val clamped = validateTrimRange(startMs, endMs, totalDurationMs)
        if (clamped.durationMs < MIN_TRIM_DURATION_MS) {
            return Result.failure(IllegalArgumentException("La durée minimale de découpe est de 1 seconde."))
        }

        val workDir = File(context.cacheDir, "brav_trim_work").apply { mkdirs() }
        val freeBytes = runCatching { StatFs(workDir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (freeBytes < MIN_REQUIRED_FREE_BYTES) {
            return Result.failure(IOException("Espace de stockage insuffisant pour découper cette vidéo."))
        }

        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val tmpFile = File(workDir, "trim_$id.tmp.mp4")
        val finalFile = File(workDir, "trim_$id.mp4")

        return try {
            val exported = withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine<Result<File>> { cont ->
                    var transformerRef: Transformer? = null
                    val progressJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Main.immediate).launch {
                        val holder = ProgressHolder()
                        while (cont.isActive) {
                            val state = transformerRef?.getProgress(holder)
                            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress((holder.progress / 100f).coerceIn(0f, 0.99f))
                            }
                            delay(100L)
                        }
                    }

                    val transformer = Transformer.Builder(context)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                progressJob.cancel()
                                onProgress(1f)
                                if (tmpFile.exists() && tmpFile.length() > 0L) {
                                    val moved = tmpFile.renameTo(finalFile)
                                    val resultFile = if (moved) finalFile else tmpFile.copyTo(finalFile, overwrite = true).also {
                                        tmpFile.delete()
                                    }
                                    if (cont.isActive) cont.resume(Result.success(resultFile))
                                } else {
                                    tmpFile.delete()
                                    if (cont.isActive) {
                                        cont.resume(Result.failure(IOException("Le fichier découpé est vide.")))
                                    }
                                }
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                progressJob.cancel()
                                tmpFile.delete()
                                finalFile.delete()
                                if (cont.isActive) {
                                    cont.resume(
                                        Result.failure(
                                            IOException(
                                                "Échec de la découpe vidéo : ${exportException.localizedMessage ?: "codec non pris en charge"}"
                                            )
                                        )
                                    )
                                }
                            }
                        })
                        .build()
                    transformerRef = transformer

                    val clippedItem = MediaItem.Builder()
                        .setUri(sourceUri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(clamped.startMs)
                                .setEndPositionMs(clamped.endMs)
                                .build()
                        )
                        .build()
                    val editedItem = EditedMediaItem.Builder(clippedItem).build()

                    cont.invokeOnCancellation {
                        progressJob.cancel()
                        runCatching { transformer.cancel() }
                        tmpFile.delete()
                        finalFile.delete()
                    }

                    try {
                        transformer.start(editedItem, tmpFile.absolutePath)
                    } catch (e: Exception) {
                        progressJob.cancel()
                        tmpFile.delete()
                        if (cont.isActive) cont.resume(Result.failure(e))
                    }
                }
            }
            exported
        } catch (ce: CancellationException) {
            tmpFile.delete()
            finalFile.delete()
            throw ce
        } catch (e: Exception) {
            tmpFile.delete()
            finalFile.delete()
            Result.failure(e)
        }
    }
}
