package com.example.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import com.example.network.WorkerApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class RecordedVoiceResult(
    val file: File,
    val bytes: ByteArray,
    val durationMs: Long,
    val waveform: List<Int>
)

data class VoicePlaybackState(
    val activeUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val isBuffering: Boolean = false,
    val error: String? = null
)

class VoiceRecorderPlayer(
    private val context: Context,
    private val workerApiClient: WorkerApiClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartMs: Long = 0L
    private var amplitudeJob: Job? = null
    private val recordedWaveform = mutableListOf<Int>()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _liveAmplitudes = MutableStateFlow<List<Int>>(emptyList())
    val liveAmplitudes: StateFlow<List<Int>> = _liveAmplitudes.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playback = MutableStateFlow(VoicePlaybackState())
    val playback: StateFlow<VoicePlaybackState> = _playback.asStateFlow()

    fun startRecording(): Boolean {
        if (_isRecording.value) return false
        return try {
            stopPlayback()
            val outDir = File(context.cacheDir, "voice_records").apply { mkdirs() }
            val file = File(outDir, "brav_voice_${System.currentTimeMillis()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()

            recorder = r
            recordingFile = file
            recordingStartMs = System.currentTimeMillis()
            recordedWaveform.clear()
            _liveAmplitudes.value = emptyList()
            _recordingDurationMs.value = 0L
            _isRecording.value = true

            amplitudeJob?.cancel()
            amplitudeJob = scope.launch {
                while (isActive && _isRecording.value) {
                    val elapsed = System.currentTimeMillis() - recordingStartMs
                    _recordingDurationMs.value = elapsed
                    val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    val normalized = ((amp / 32767f) * 95f + 5f).toInt().coerceIn(5, 100)
                    recordedWaveform.add(normalized)
                    if (recordedWaveform.size > 48) {
                        _liveAmplitudes.value = recordedWaveform.takeLast(48)
                    } else {
                        _liveAmplitudes.value = recordedWaveform.toList()
                    }
                    delay(80L)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice recording", e)
            cancelRecording()
            false
        }
    }

    suspend fun stopRecording(): RecordedVoiceResult? = withContext(Dispatchers.IO) {
        if (!_isRecording.value) return@withContext null
        amplitudeJob?.cancel()
        _isRecording.value = false
        val duration = (System.currentTimeMillis() - recordingStartMs).coerceAtLeast(300L)
        val file = recordingFile
        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder stop warning: ${e.message}")
        }
        runCatching { recorder?.release() }
        recorder = null

        if (file == null || !file.exists() || file.length() == 0L) return@withContext null
        val bytes = file.readBytes()
        val downsampled = downsampleWaveform(recordedWaveform, targetBars = 36)
        RecordedVoiceResult(
            file = file,
            bytes = bytes,
            durationMs = duration,
            waveform = downsampled
        )
    }

    fun cancelRecording() {
        amplitudeJob?.cancel()
        _isRecording.value = false
        _recordingDurationMs.value = 0L
        _liveAmplitudes.value = emptyList()
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        recordingFile?.delete()
        recordingFile = null
    }

    fun togglePlayPause(url: String) {
        val current = _playback.value
        if (current.activeUrl == url && mediaPlayer != null) {
            val mp = mediaPlayer!!
            if (mp.isPlaying) {
                mp.pause()
                _playback.value = current.copy(isPlaying = false, positionMs = mp.currentPosition.toLong())
            } else {
                applyPlaybackSpeed(mp, current.speed)
                mp.start()
                _playback.value = current.copy(isPlaying = true)
                startProgressLoop()
            }
            return
        }
        startNewPlayback(url, current.speed)
    }

    fun seekTo(url: String, positionMs: Long) {
        val mp = mediaPlayer ?: return
        if (_playback.value.activeUrl != url) return
        val clamped = positionMs.toInt().coerceIn(0, mp.duration.coerceAtLeast(0))
        mp.seekTo(clamped)
        _playback.value = _playback.value.copy(positionMs = clamped.toLong())
    }

    fun cyclePlaybackSpeed(): Float {
        val next = when (_playback.value.speed) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _playback.value = _playback.value.copy(speed = next)
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) applyPlaybackSpeed(mp, next)
        }
        return next
    }

    fun stopPlayback() {
        progressJob?.cancel()
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        _playback.value = _playback.value.copy(
            activeUrl = null,
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L,
            isBuffering = false,
            error = null
        )
    }

    private fun startNewPlayback(url: String, speed: Float) {
        stopPlayback()
        _playback.value = VoicePlaybackState(activeUrl = url, isBuffering = true, speed = speed)
        scope.launch {
            val localFile = resolveCachedFile(url)
            if (localFile == null || !localFile.exists()) {
                _playback.value = _playback.value.copy(
                    isBuffering = false,
                    error = "Audio introuvable"
                )
                return@launch
            }
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(localFile.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        _playback.value = _playback.value.copy(
                            isPlaying = false,
                            positionMs = 0L
                        )
                        progressJob?.cancel()
                    }
                }
                mediaPlayer = mp
                applyPlaybackSpeed(mp, speed)
                mp.start()
                _playback.value = _playback.value.copy(
                    activeUrl = url,
                    isPlaying = true,
                    isBuffering = false,
                    positionMs = 0L,
                    durationMs = mp.duration.toLong().coerceAtLeast(1L),
                    error = null
                )
                startProgressLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
                _playback.value = _playback.value.copy(
                    isBuffering = false,
                    isPlaying = false,
                    error = e.localizedMessage ?: "Lecture impossible"
                )
            }
        }
    }

    private suspend fun resolveCachedFile(url: String): File? = withContext(Dispatchers.IO) {
        if (url.startsWith("file://") ||
            url.startsWith("/data/") ||
            url.startsWith(context.cacheDir.absolutePath) ||
            url.startsWith(context.filesDir.absolutePath)
        ) {
            return@withContext File(url.removePrefix("file://"))
        }
        val cacheDir = File(context.filesDir, "brav_media_cache").apply { mkdirs() }
        val key = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
        val cached = File(cacheDir, "audio_$key.m4a")
        if (cached.exists() && cached.length() > 0L) return@withContext cached

        val result = workerApiClient.downloadAuthorizedBytes(url)
        val bytes = result.getOrNull() ?: return@withContext null
        if (bytes.isEmpty()) return@withContext null
        cached.writeBytes(bytes)
        cached
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val mp = mediaPlayer ?: break
                if (!mp.isPlaying) break
                _playback.value = _playback.value.copy(
                    positionMs = mp.currentPosition.toLong(),
                    durationMs = mp.duration.toLong().coerceAtLeast(1L)
                )
                delay(120L)
            }
        }
    }

    private fun applyPlaybackSpeed(mp: MediaPlayer, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val params = mp.playbackParams ?: PlaybackParams()
                params.speed = speed
                mp.playbackParams = params
            }
        }
    }

    private fun downsampleWaveform(samples: List<Int>, targetBars: Int): List<Int> {
        if (samples.isEmpty()) return List(targetBars) { 18 }
        if (samples.size <= targetBars) return samples
        val step = samples.size.toFloat() / targetBars
        return (0 until targetBars).map { i ->
            val start = (i * step).toInt().coerceIn(0, samples.lastIndex)
            val end = ((i + 1) * step).toInt().coerceIn(start + 1, samples.size)
            samples.subList(start, end).average().toInt().coerceIn(5, 100)
        }
    }

    fun release() {
        cancelRecording()
        stopPlayback()
    }

    companion object {
        private const val TAG = "VoiceRecorderPlayer"
    }
}
