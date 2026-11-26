package com.example.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.network.WorkerApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaDownloadHelper {

    suspend fun saveMediaToGallery(
        context: Context,
        workerApiClient: WorkerApiClient?,
        mediaUrl: String,
        fileNameHint: String = "",
        mimeTypeHint: String = "",
        isVideo: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bytes = when {
                mediaUrl.startsWith("file://") || mediaUrl.startsWith("/") -> {
                    val f = File(mediaUrl.removePrefix("file://"))
                    if (!f.exists()) return@withContext Result.failure(IllegalStateException("Fichier introuvable"))
                    f.readBytes()
                }
                workerApiClient != null -> {
                    val cached = workerApiClient.downloadMediaToCache(context, mediaUrl, fileNameHint)
                    val file = cached.getOrElse { return@withContext Result.failure(it) }
                    file.readBytes()
                }
                else -> return@withContext Result.failure(IllegalStateException("Téléchargement indisponible"))
            }

            if (bytes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Média vide"))
            }

            val ext = resolveExtension(fileNameHint, mediaUrl, mimeTypeHint, isVideo)
            val mime = mimeTypeHint.ifBlank {
                when (ext) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "mp4" -> "video/mp4"
                    else -> if (isVideo) "video/mp4" else "image/jpeg"
                }
            }
            val displayName = fileNameHint.takeIf { it.isNotBlank() && it.contains('.') }
                ?: "BRAV_${System.currentTimeMillis()}.$ext"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = if (isVideo) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                val relativeDir = if (isVideo) {
                    "${Environment.DIRECTORY_MOVIES}/BRAV Chat"
                } else {
                    "${Environment.DIRECTORY_PICTURES}/BRAV Chat"
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values)
                    ?: return@withContext Result.failure(IllegalStateException("Impossible de créer le fichier dans la galerie"))
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                } ?: return@withContext Result.failure(IllegalStateException("Écriture impossible"))

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success(displayName)
            } else {
                val dir = context.getExternalFilesDir(
                    if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                ) ?: context.filesDir
                val targetDir = File(dir, "BRAV Chat").apply { mkdirs() }
                val outFile = File(targetDir, displayName)
                outFile.writeBytes(bytes)
                Result.success(displayName)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun resolveExtension(
        fileNameHint: String,
        mediaUrl: String,
        mimeTypeHint: String,
        isVideo: Boolean
    ): String {
        val fromName = fileNameHint.substringAfterLast('.', "").lowercase()
        if (fromName in setOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "mov", "webm")) {
            return fromName
        }
        val fromMime = mimeTypeHint.substringAfterLast('/', "").lowercase()
        if (fromMime in setOf("jpeg", "jpg", "png", "webp", "mp4", "webm")) {
            return if (fromMime == "jpeg") "jpg" else fromMime
        }
        val fromUrl = mediaUrl.substringBefore('?').substringAfterLast('.', "").lowercase()
        if (fromUrl in setOf("jpg", "jpeg", "png", "webp", "mp4")) {
            return fromUrl
        }
        return if (isVideo) "mp4" else "jpg"
    }
}
