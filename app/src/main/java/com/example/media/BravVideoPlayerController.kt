package com.example.media

import android.content.Context
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.local.VideoAutoPlayMode
import com.example.network.WorkerApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class ViewportVideoCandidate(
    val id: String,
    val visibleFraction: Float,
    val centerDistancePx: Float
)

data class BravVideoPlaybackState(
    val activeSlotKey: String = "",
    val activeUrl: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isMuted: Boolean = false,
    val videoAspectRatio: Float = 0f,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
    val pausedByCallOrLifecycle: Boolean = false
)

val LocalBravVideoController = staticCompositionLocalOf<BravVideoPlayerController?> { null }

/**
 * Pure helper selecting the single best visible video in a scrollable viewport.
 * Ensures at most one video auto-plays at any instant.
 */
fun selectMostVisibleVideoSlot(
    candidates: List<ViewportVideoCandidate>,
    minVisibleFraction: Float = 0.60f
): String? {
    return candidates
        .filter { it.visibleFraction >= minVisibleFraction }
        .maxWithOrNull(
            compareBy<ViewportVideoCandidate> { it.visibleFraction }
                .thenByDescending { -it.centerDistancePx }
        )
        ?.id
}

/**
 * Shared application-scoped ExoPlayer controller with bounded LRU disk cache (Range HTTP streaming),
 * single-active-slot enforcement, audio focus handling, call/lifecycle interruption, and next-video prefetch.
 */
@OptIn(UnstableApi::class)
class BravVideoPlayerController(
    context: Context,
    private val workerApiClient: WorkerApiClient
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _state = MutableStateFlow(BravVideoPlaybackState())
    val state: StateFlow<BravVideoPlaybackState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var cache: SimpleCache? = null
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null
    private var progressJob: Job? = null
    private var prefetchJob: Job? = null
    private var prefetchedUrl: String = ""

    private var callInterrupted: Boolean = false
    private var lifecyclePaused: Boolean = false
    private var userManuallyPausedSlot: String? = null
    private var hasAudioFocus: Boolean = false
    private var focusRequest: AudioFocusRequest? = null

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                player?.pause()
                _state.update { it.copy(isPlaying = false) }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                player?.volume = if (_state.value.isMuted) 0f else 0.25f
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (!callInterrupted && !lifecyclePaused) {
                    player?.volume = if (_state.value.isMuted) 0f else 1f
                }
            }
        }
    }

    private fun ensureCache(): CacheDataSource.Factory {
        cacheDataSourceFactory?.let { return it }
        val cacheDir = File(appContext.cacheDir, "brav_video_cache").apply { mkdirs() }
        val evictor = LeastRecentlyUsedCacheEvictor(100L * 1024L * 1024L)
        val dbProvider = StandaloneDatabaseProvider(appContext)
        val createdCache = SimpleCache(cacheDir, evictor, dbProvider)
        cache = createdCache

        val okHttpFactory = OkHttpDataSource.Factory(workerApiClient.mediaAuthorizedClient)
            .setUserAgent("BravChat-Android/1.0")
        val upstreamFactory = DefaultDataSource.Factory(appContext, okHttpFactory)
        val factory = CacheDataSource.Factory()
            .setCache(createdCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        cacheDataSourceFactory = factory
        return factory
    }

    fun getOrCreatePlayer(): ExoPlayer {
        player?.let { return it }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1_500,
                /* maxBufferMs = */ 15_000,
                /* bufferForPlaybackMs = */ 600,
                /* bufferForPlaybackAfterRebufferMs = */ 1_200
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(ensureCache())
        val created = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    false
                )
                volume = if (_state.value.isMuted) 0f else 1f
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                        if (isPlaying) startProgressLoop() else stopProgressLoop()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _state.update {
                            it.copy(
                                isBuffering = playbackState == Player.STATE_BUFFERING,
                                durationMs = duration.coerceAtLeast(0L),
                                errorMessage = if (playbackState == Player.STATE_READY) null else it.errorMessage
                            )
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            val ratio = (videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio) /
                                videoSize.height.toFloat()
                            _state.update { it.copy(videoAspectRatio = ratio.coerceIn(0.35f, 3.0f)) }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.w(TAG, "ExoPlayer playback error: ${error.message}")
                        _state.update {
                            it.copy(
                                isPlaying = false,
                                isBuffering = false,
                                errorMessage = "Connexion instable — touchez pour réessayer"
                            )
                        }
                    }
                })
            }
        player = created
        return created
    }

    fun playSlot(
        slotKey: String,
        url: String,
        loop: Boolean = true,
        autoPlay: Boolean = true,
        muted: Boolean? = null
    ) {
        if (url.isBlank() || slotKey.isBlank()) return
        if (callInterrupted || lifecyclePaused) {
            _state.update {
                it.copy(
                    activeSlotKey = slotKey,
                    activeUrl = url,
                    pausedByCallOrLifecycle = true
                )
            }
            return
        }
        val exo = getOrCreatePlayer()
        val effectiveMuted = muted ?: _state.value.isMuted
        if (effectiveMuted != _state.value.isMuted) {
            _state.update { it.copy(isMuted = effectiveMuted) }
        }

        val sameSlotAndUrl = _state.value.activeSlotKey == slotKey && _state.value.activeUrl == url
        exo.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

        if (!sameSlotAndUrl) {
            userManuallyPausedSlot = null
            _state.update {
                it.copy(
                    activeSlotKey = slotKey,
                    activeUrl = url,
                    isBuffering = true,
                    errorMessage = null,
                    currentPositionMs = 0L,
                    durationMs = 0L,
                    pausedByCallOrLifecycle = false
                )
            }
            val uri = if (url.startsWith("/data/") || url.startsWith("/storage/")) {
                Uri.fromFile(File(url))
            } else {
                Uri.parse(url)
            }
            exo.setMediaItem(MediaItem.fromUri(uri))
            exo.prepare()
        }

        if (autoPlay && userManuallyPausedSlot != slotKey) {
            applyPlaybackWithAudioFocus(exo, effectiveMuted)
        } else {
            exo.volume = if (effectiveMuted) 0f else 1f
            exo.playWhenReady = false
            exo.pause()
        }
    }

    private fun applyPlaybackWithAudioFocus(exo: ExoPlayer, muted: Boolean) {
        if (muted) {
            exo.volume = 0f
            exo.playWhenReady = true
            return
        }
        val focusGranted = requestAudioFocus()
        if (focusGranted) {
            exo.volume = 1f
            exo.playWhenReady = true
        } else {
            // Silent autoplay fallback if audio focus is denied (e.g., another call/audio holds focus)
            _state.update { it.copy(isMuted = true) }
            exo.volume = 0f
            exo.playWhenReady = true
        }
    }

    fun togglePlayPause(slotKey: String = _state.value.activeSlotKey) {
        val exo = player ?: return
        if (_state.value.activeSlotKey != slotKey) return
        if (exo.isPlaying) {
            userManuallyPausedSlot = slotKey
            exo.pause()
            _state.update { it.copy(isPlaying = false) }
        } else {
            userManuallyPausedSlot = null
            if (!callInterrupted && !lifecyclePaused) {
                applyPlaybackWithAudioFocus(exo, _state.value.isMuted)
            }
        }
    }

    fun setMuted(muted: Boolean) {
        _state.update { it.copy(isMuted = muted) }
        val exo = player ?: return
        if (muted) {
            exo.volume = 0f
            abandonAudioFocus()
        } else if (exo.isPlaying && !callInterrupted && !lifecyclePaused) {
            if (requestAudioFocus()) {
                exo.volume = 1f
            } else {
                _state.update { it.copy(isMuted = true) }
                exo.volume = 0f
            }
        }
    }

    fun toggleMute(): Boolean {
        val next = !_state.value.isMuted
        setMuted(next)
        return next
    }

    fun pauseSlot(slotKey: String) {
        if (_state.value.activeSlotKey == slotKey) {
            player?.pause()
            _state.update { it.copy(isPlaying = false) }
        }
    }

    fun pauseActive() {
        player?.pause()
        _state.update { it.copy(isPlaying = false) }
    }

    fun stopAndClearSlot(slotKey: String? = null) {
        if (slotKey != null && _state.value.activeSlotKey != slotKey) return
        userManuallyPausedSlot = null
        player?.stop()
        player?.clearMediaItems()
        abandonAudioFocus()
        _state.update {
            it.copy(
                activeSlotKey = "",
                activeUrl = "",
                isPlaying = false,
                isBuffering = false,
                errorMessage = null,
                currentPositionMs = 0L,
                durationMs = 0L
            )
        }
    }

    fun retryCurrent() {
        val url = _state.value.activeUrl
        val slot = _state.value.activeSlotKey
        if (url.isBlank() || slot.isBlank()) return
        _state.update { it.copy(activeUrl = "", errorMessage = null) }
        playSlot(slotKey = slot, url = url, autoPlay = true)
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
        _state.update { it.copy(currentPositionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun setCallInterrupted(interrupted: Boolean) {
        if (callInterrupted == interrupted) return
        callInterrupted = interrupted
        if (interrupted) {
            player?.pause()
            abandonAudioFocus()
            _state.update { it.copy(isPlaying = false, pausedByCallOrLifecycle = true) }
        } else {
            _state.update { it.copy(pausedByCallOrLifecycle = lifecyclePaused) }
        }
    }

    fun setLifecyclePaused(paused: Boolean) {
        if (lifecyclePaused == paused) return
        lifecyclePaused = paused
        if (paused) {
            player?.pause()
            abandonAudioFocus()
            _state.update { it.copy(isPlaying = false, pausedByCallOrLifecycle = true) }
        } else {
            _state.update { it.copy(pausedByCallOrLifecycle = callInterrupted) }
        }
    }

    /**
     * Bounded prefetch of ONLY the next video's initial 1.2 MB so swipe feels instant without wasting data.
     */
    fun prefetchNextVideo(url: String, autoPlayMode: VideoAutoPlayMode, dataSaverEnabled: Boolean) {
        if (url.isBlank() || url == prefetchedUrl) return
        if (url.startsWith("file://") || url.startsWith("/data/")) return
        if (!isAutoPlayAllowed(autoPlayMode, dataSaverEnabled)) return
        prefetchedUrl = url
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val factory = ensureCache()
                val ds = factory.createDataSource()
                val dataSpec = DataSpec.Builder()
                    .setUri(Uri.parse(url))
                    .setPosition(0L)
                    .setLength(1_250_000L)
                    .build()
                CacheWriter(ds, dataSpec, null, null).cache()
            }
        }
    }

    fun isAutoPlayAllowed(mode: VideoAutoPlayMode, dataSaverEnabled: Boolean): Boolean {
        if (callInterrupted || lifecyclePaused) return false
        if (mode == VideoAutoPlayMode.DISABLED) return false
        if (dataSaverEnabled && mode == VideoAutoPlayMode.WIFI_AND_MOBILE && !isOnUnmeteredWifi()) {
            return false
        }
        return when (mode) {
            VideoAutoPlayMode.DISABLED -> false
            VideoAutoPlayMode.WIFI_ONLY -> isOnUnmeteredWifi()
            VideoAutoPlayMode.WIFI_AND_MOBILE -> hasNetworkConnection()
        }
    }

    private fun isOnUnmeteredWifi(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun hasNetworkConnection(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        if (hasAudioFocus) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AndroidAudioAttributes.Builder()
                        .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                        .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
                .also { focusRequest = it }
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusListener)
        }
        hasAudioFocus = false
    }

    private fun startProgressLoop() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                val exo = player
                if (exo != null && exo.isPlaying) {
                    _state.update {
                        it.copy(
                            currentPositionMs = exo.currentPosition.coerceAtLeast(0L),
                            durationMs = exo.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(200L)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressLoop()
        prefetchJob?.cancel()
        abandonAudioFocus()
        player?.release()
        player = null
        runCatching { cache?.release() }
        cache = null
        cacheDataSourceFactory = null
        scope.cancel()
    }

    companion object {
        private const val TAG = "BravVideoController"
    }
}
