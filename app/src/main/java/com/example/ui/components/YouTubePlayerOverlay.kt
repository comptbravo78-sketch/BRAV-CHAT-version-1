package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.network.YouTubeTrack
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravRed
import com.example.ui.theme.StatusOnlineGreen
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference

enum class YouTubePlaybackStage {
    IFRAME_EMBED,
    MOBILE_WATCH_FALLBACK
}

sealed interface PlayerJsCommand {
    data class LoadVideo(val videoId: String, val id: Long = System.nanoTime()) : PlayerJsCommand
    data class LoadMobileWatch(val videoId: String, val id: Long = System.nanoTime()) : PlayerJsCommand
    data class Play(val id: Long = System.nanoTime()) : PlayerJsCommand
    data class Pause(val id: Long = System.nanoTime()) : PlayerJsCommand
    data class SeekTo(val seconds: Int, val id: Long = System.nanoTime()) : PlayerJsCommand
}

@Stable
class YouTubePlayerController {
    var queue by mutableStateOf<List<YouTubeTrack>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set
    var videoId by mutableStateOf<String?>(null)
        private set
    var videoTitle by mutableStateOf("Musique YouTube")
        private set
    var channelTitle by mutableStateOf("YouTube Musique")
        private set
    var thumbnailUrl by mutableStateOf("")
        private set
    var durationLabel by mutableStateOf("")
        private set
    var isMinimized by mutableStateOf(false)
        private set
    var isVideoHidden by mutableStateOf(false)
        private set
    var isVideoFullscreen by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isPlayerReady by mutableStateOf(false)
        private set
    var pausedByUser by mutableStateOf(false)
        private set
    var pausedForActiveCall by mutableStateOf(false)
        private set
    var autoPlayEnabled by mutableStateOf(true)
        private set
    var repeatOne by mutableStateOf(false)
        private set
    var currentPositionSec by mutableIntStateOf(0)
        private set
    var durationSec by mutableIntStateOf(0)
        private set
    var playbackStatusText by mutableStateOf("Chargement...")
        private set
    var playbackStage by mutableStateOf(YouTubePlaybackStage.IFRAME_EMBED)
        private set
    var consecutiveErrorSkips by mutableIntStateOf(0)
        private set
    var failedVideoIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var playRequestNonce by mutableIntStateOf(0)
        private set
    var pendingCommand by mutableStateOf<PlayerJsCommand?>(null)
        private set
    private var resumePositionOnReadySec by mutableIntStateOf(0)

    val isVisible: Boolean
        get() = !videoId.isNullOrBlank()

    val currentTrack: YouTubeTrack?
        get() = queue.getOrNull(currentIndex)
            ?: videoId?.let { vid ->
                YouTubeTrack(
                    videoId = vid,
                    title = videoTitle,
                    channelTitle = channelTitle,
                    thumbnailUrl = thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/$vid/hqdefault.jpg" },
                    durationLabel = durationLabel
                )
            }

    fun playTrack(
        track: YouTubeTrack,
        queueTracks: List<YouTubeTrack> = listOf(track),
        startMinimized: Boolean = false
    ) {
        val merged = (listOf(track) + queueTracks)
            .distinctBy { it.videoId }
            .filterNot { it.videoId != track.videoId && it.videoId in failedVideoIds }
        val ordered = if (queueTracks.any { it.videoId == track.videoId }) {
            queueTracks.distinctBy { it.videoId }
                .filterNot { it.videoId != track.videoId && it.videoId in failedVideoIds }
        } else {
            merged
        }
        val targetIndex = ordered.indexOfFirst { it.videoId == track.videoId }.coerceAtLeast(0)
        queue = ordered
        currentIndex = targetIndex
        applyTrackMeta(ordered[targetIndex])
        playbackStage = YouTubePlaybackStage.IFRAME_EMBED
        pausedByUser = false
        isPlaying = true
        consecutiveErrorSkips = 0
        isMinimized = startMinimized
        playRequestNonce++
        pendingCommand = PlayerJsCommand.LoadVideo(ordered[targetIndex].videoId)
    }

    fun openUrl(url: String, title: String = "Lecture YouTube"): Boolean {
        val extracted = extractYouTubeVideoId(url) ?: return false
        val track = YouTubeTrack(
            videoId = extracted,
            title = title.ifBlank { "Lecture YouTube" },
            channelTitle = "YouTube Musique",
            thumbnailUrl = "https://i.ytimg.com/vi/$extracted/hqdefault.jpg"
        )
        playTrack(track, listOf(track), startMinimized = false)
        return true
    }

    fun appendUpNext(moreTracks: List<YouTubeTrack>) {
        if (moreTracks.isEmpty()) return
        val existingIds = queue.map { it.videoId }.toSet()
        val additions = moreTracks.filter { it.videoId !in existingIds && it.videoId !in failedVideoIds }
        if (additions.isNotEmpty()) {
            queue = queue + additions
        }
    }

    fun replaceCurrentTrackMeta(updated: YouTubeTrack) {
        if (videoId == updated.videoId) {
            videoTitle = updated.title
            channelTitle = updated.channelTitle
            thumbnailUrl = updated.thumbnailUrl
            durationLabel = updated.durationLabel
        }
        queue = queue.map { if (it.videoId == updated.videoId) updated else it }
    }

    fun togglePlayPause() {
        if (isPlaying) {
            pausePlayback(byUser = true)
        } else {
            playPlayback()
        }
    }

    fun playPlayback() {
        val active = videoId ?: return
        pausedByUser = false
        pausedForActiveCall = false
        isPlaying = true
        playbackStatusText = "Lecture en cours"
        pendingCommand = if (consecutiveErrorSkips >= 6) {
            consecutiveErrorSkips = 0
            playRequestNonce++
            PlayerJsCommand.LoadVideo(active)
        } else {
            PlayerJsCommand.Play()
        }
    }

    fun pausePlayback(byUser: Boolean = false) {
        if (!isVisible) return
        if (!byUser && !pausedForActiveCall) {
            // Strict policy: never pause spontaneously unless user tapped pause or a phone call is active
            return
        }
        pausedByUser = byUser
        isPlaying = false
        playbackStatusText = if (pausedForActiveCall) "En pause (appel en cours)" else "En pause"
        pendingCommand = PlayerJsCommand.Pause()
    }

    fun setActiveCallPresent(active: Boolean) {
        if (!isVisible) {
            pausedForActiveCall = false
            return
        }
        if (active) {
            if (!pausedByUser && isPlaying) {
                pausedForActiveCall = true
                isPlaying = false
                playbackStatusText = "En pause (appel en cours)"
                pendingCommand = PlayerJsCommand.Pause()
            }
        } else if (pausedForActiveCall) {
            pausedForActiveCall = false
            if (!pausedByUser) {
                isPlaying = true
                playbackStatusText = "Lecture en cours"
                pendingCommand = PlayerJsCommand.Play()
            }
        }
    }

    fun seekTo(seconds: Int) {
        if (!isVisible) return
        val clamped = seconds.coerceAtLeast(0)
        currentPositionSec = clamped
        pendingCommand = PlayerJsCommand.SeekTo(clamped)
    }

    fun toggleHideVideo() {
        isVideoHidden = !isVideoHidden
        if (isVideoHidden) isVideoFullscreen = false
    }

    fun toggleFullscreen() {
        isVideoHidden = false
        isVideoFullscreen = !isVideoFullscreen
    }

    fun toggleAutoPlay() {
        autoPlayEnabled = !autoPlayEnabled
    }

    fun toggleRepeatOne() {
        repeatOne = !repeatOne
    }

    fun playNext(fromAutoAdvanceOrError: Boolean = false): Boolean {
        if (queue.isEmpty()) return false
        val candidates = queue.indices.filter { idx ->
            idx > currentIndex && queue[idx].videoId !in failedVideoIds
        }
        val nextIdx = candidates.firstOrNull() ?: run {
            if (!fromAutoAdvanceOrError || autoPlayEnabled) {
                queue.indices.firstOrNull { idx ->
                    idx != currentIndex && queue[idx].videoId !in failedVideoIds
                }
            } else {
                null
            }
        } ?: return false

        val nextTrack = queue[nextIdx]
        currentIndex = nextIdx
        applyTrackMeta(nextTrack)
        playbackStage = YouTubePlaybackStage.IFRAME_EMBED
        pausedByUser = false
        isPlaying = true
        playRequestNonce++
        pendingCommand = PlayerJsCommand.LoadVideo(nextTrack.videoId)
        return true
    }

    fun playPrevious() {
        if (queue.isEmpty()) return
        if (currentPositionSec > 4) {
            seekTo(0)
            return
        }
        val prevIdx = ((currentIndex - 1) downTo 0).firstOrNull { idx ->
            queue[idx].videoId !in failedVideoIds
        } ?: queue.indices.lastOrNull { idx ->
            queue[idx].videoId !in failedVideoIds
        } ?: return

        val prevTrack = queue[prevIdx]
        currentIndex = prevIdx
        applyTrackMeta(prevTrack)
        playbackStage = YouTubePlaybackStage.IFRAME_EMBED
        pausedByUser = false
        isPlaying = true
        playRequestNonce++
        pendingCommand = PlayerJsCommand.LoadVideo(prevTrack.videoId)
    }

    fun minimize() {
        if (isVisible) isMinimized = true
    }

    fun expand() {
        if (isVisible) isMinimized = false
    }

    fun close() {
        pendingCommand = PlayerJsCommand.Pause()
        videoId = null
        queue = emptyList()
        currentIndex = -1
        isPlaying = false
        isPlayerReady = false
        isMinimized = false
        isVideoFullscreen = false
        pausedByUser = false
        pausedForActiveCall = false
        resumePositionOnReadySec = 0
        currentPositionSec = 0
        durationSec = 0
    }

    fun consumeCommand() {
        pendingCommand = null
    }

    fun onPlayerReady() {
        isPlayerReady = true
        val seekSec = resumePositionOnReadySec
        if (seekSec > 1) {
            resumePositionOnReadySec = 0
            currentPositionSec = seekSec
            pendingCommand = PlayerJsCommand.SeekTo(seekSec)
        }
        if (!pausedByUser && !pausedForActiveCall) {
            isPlaying = true
            playbackStatusText = "Lecture en cours"
            if (seekSec <= 1) {
                pendingCommand = PlayerJsCommand.Play()
            }
        }
    }

    fun onPlayerStateChanged(ytState: Int) {
        when (ytState) {
            YT_STATE_UNSTARTED -> {
                playbackStatusText = "Préparation..."
            }
            YT_STATE_ENDED -> {
                isPlaying = false
                if (repeatOne) {
                    currentPositionSec = 0
                    pausedByUser = false
                    pausedForActiveCall = false
                    isPlaying = true
                    pendingCommand = PlayerJsCommand.SeekTo(0)
                } else if (autoPlayEnabled) {
                    val advanced = playNext(fromAutoAdvanceOrError = true)
                    if (!advanced) {
                        playbackStatusText = "Fin de la file d'attente"
                    }
                } else {
                    playbackStatusText = "Terminé"
                }
            }
            YT_STATE_PLAYING -> {
                if (pausedForActiveCall || pausedByUser) {
                    pendingCommand = PlayerJsCommand.Pause()
                    isPlaying = false
                } else {
                    isPlaying = true
                    consecutiveErrorSkips = 0
                    playbackStatusText = if (isVideoHidden) "Audio seul actif" else "Lecture en cours"
                }
            }
            YT_STATE_PAUSED -> {
                if (!pausedByUser && !pausedForActiveCall && isVisible) {
                    // Never auto-pause without explicit user action or active call
                    isPlaying = true
                    playbackStatusText = if (isVideoHidden) "Audio seul actif" else "Lecture en cours"
                    pendingCommand = PlayerJsCommand.Play()
                } else {
                    isPlaying = false
                    playbackStatusText = if (pausedForActiveCall) "En pause (appel en cours)" else "En pause"
                }
            }
            YT_STATE_BUFFERING -> {
                playbackStatusText = "Chargement..."
            }
            YT_STATE_CUED -> {
                if (!pausedByUser && !pausedForActiveCall) {
                    pendingCommand = PlayerJsCommand.Play()
                }
            }
        }
    }

    fun onEmbedErrorOrTimeout(errorCode: Int) {
        val currentVid = videoId ?: return
        val canFallbackSameTrack = playbackStage == YouTubePlaybackStage.IFRAME_EMBED &&
            errorCode !in setOf(2, 100)
        if (canFallbackSameTrack) {
            resumePositionOnReadySec = currentPositionSec
            playbackStage = YouTubePlaybackStage.MOBILE_WATCH_FALLBACK
            isPlayerReady = false
            pausedByUser = false
            isPlaying = !pausedForActiveCall
            playRequestNonce++
            playbackStatusText = "Optimisation du lecteur YouTube..."
            pendingCommand = PlayerJsCommand.LoadMobileWatch(currentVid)
        } else {
            onPlayerError(errorCode)
        }
    }

    fun onPlayerError(@Suppress("UNUSED_PARAMETER") errorCode: Int) {
        val currentVid = videoId ?: return
        failedVideoIds = failedVideoIds + currentVid
        consecutiveErrorSkips += 1
        if (consecutiveErrorSkips < 6) {
            playbackStatusText = "Passage automatique au titre suivant..."
            val skipped = playNext(fromAutoAdvanceOrError = true)
            if (!skipped) {
                isPlaying = false
                playbackStatusText = "Aucun autre titre disponible"
            }
        } else {
            isPlaying = false
            playbackStatusText = "Connexion requise pour lire la musique"
        }
    }

    fun skipStalledTrack() {
        if (pausedByUser || !isVisible) return
        val currentVid = videoId ?: return
        if (playbackStage == YouTubePlaybackStage.IFRAME_EMBED) {
            onEmbedErrorOrTimeout(152)
            return
        }
        if (consecutiveErrorSkips < 4 && queue.size > 1) {
            failedVideoIds = failedVideoIds + currentVid
            consecutiveErrorSkips += 1
            playbackStatusText = "Passage au titre suivant..."
            playNext(fromAutoAdvanceOrError = true)
        }
    }

    fun onProgress(positionSec: Int, totalSec: Int) {
        if (positionSec >= 0) currentPositionSec = positionSec
        if (totalSec > 0) durationSec = totalSec
    }

    private fun applyTrackMeta(track: YouTubeTrack) {
        videoId = track.videoId
        videoTitle = track.title.ifBlank { "Musique YouTube" }
        channelTitle = track.channelTitle.ifBlank { "YouTube Musique" }
        thumbnailUrl = track.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${track.videoId}/hqdefault.jpg" }
        durationLabel = track.durationLabel
        currentPositionSec = 0
        durationSec = 0
        playbackStatusText = "Chargement..."
    }

    companion object {
        const val YT_STATE_UNSTARTED = -1
        const val YT_STATE_ENDED = 0
        const val YT_STATE_PLAYING = 1
        const val YT_STATE_PAUSED = 2
        const val YT_STATE_BUFFERING = 3
        const val YT_STATE_CUED = 5

        private val urlRegex = Regex(
            """https?://(?:www\.|m\.|music\.)?(?:youtube\.com/(?:watch\?[^\s]*v=|shorts/|embed/|live/)|youtu\.be/)([A-Za-z0-9_-]{11})[^\s]*""",
            RegexOption.IGNORE_CASE
        )

        fun findFirstYouTubeUrl(text: String): String? =
            urlRegex.find(text)?.value

        fun extractYouTubeVideoId(url: String): String? {
            val match = urlRegex.find(url.trim())
            if (match != null) return match.groupValues.getOrNull(1)
            val idOnly = url.trim()
            return if (Regex("^[A-Za-z0-9_-]{11}$").matches(idOnly)) idOnly else null
        }
    }
}

@Composable
fun rememberYouTubePlayerController(): YouTubePlayerController =
    remember { YouTubePlayerController() }

/**
 * Custom WebView that keeps Chromium's media timer & audio decoder running
 * even when video is visually hidden or minimized across screen switches.
 */
private class KeepPlayingWebView(context: Context) : WebView(context) {
    var blockTouchWhenHidden: Boolean = false

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility == View.GONE || visibility == View.INVISIBLE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return if (blockTouchWhenHidden) false else super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return if (blockTouchWhenHidden) false else super.onTouchEvent(event)
    }
}

private class BravYouTubeBridge(
    private val controller: YouTubePlayerController
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onReady() {
        mainHandler.post { controller.onPlayerReady() }
    }

    @JavascriptInterface
    fun onStateChange(state: Int) {
        mainHandler.post { controller.onPlayerStateChanged(state) }
    }

    @JavascriptInterface
    fun onPlayerError(code: Int) {
        mainHandler.post { controller.onEmbedErrorOrTimeout(code) }
    }

    @JavascriptInterface
    fun onProgress(posSec: Int, durSec: Int) {
        mainHandler.post { controller.onProgress(posSec, durSec) }
    }
}

/**
 * Single persistent YouTube IFrame API host placed once at the root of BravMainApp
 * so the WebView is NEVER recreated or disposed when switching tabs, minimizing,
 * or hiding the video view.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun YouTubePersistentHost(
    controller: YouTubePlayerController,
    activeCallPresent: Boolean = false,
    minimizedBottomOffset: Dp = 0.dp,
    onNeedMoreTracks: suspend (YouTubeTrack, Set<String>) -> List<YouTubeTrack> = { _, _ -> emptyList() },
    modifier: Modifier = Modifier
) {
    val activeVideoId = controller.videoId ?: return
    val context = LocalContext.current
    val currentController by rememberUpdatedState(controller)

    if (!controller.isMinimized) {
        BackHandler {
            if (controller.isVideoFullscreen) controller.toggleFullscreen()
            else controller.minimize()
        }
    }

    // Pause ONLY for active phone calls and resume automatically once the call ends
    LaunchedEffect(activeCallPresent) {
        currentController.setActiveCallPresent(activeCallPresent)
    }

    // Audio focus & partial wake lock while audio playback is running
    DisposableEffect(controller.isVisible, controller.isPlaying) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
            // Ne jamais mettre en pause sur perte transitoire/système; reprendre si possible
            if (change == AudioManager.AUDIOFOCUS_GAIN && !currentController.pausedByUser && !currentController.pausedForActiveCall) {
                currentController.playPlayback()
            }
        }
        val focusRequest = if (controller.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
        } else {
            null
        }
        if (controller.isPlaying && audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = if (controller.isPlaying && pm != null) {
            runCatching {
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BravChat::YouTubeAudioLock").apply {
                    setReferenceCounted(false)
                    acquire(30 * 60 * 1000L)
                }
            }.getOrNull()
        } else {
            null
        }

        onDispose {
            runCatching {
                if (wakeLock?.isHeld == true) wakeLock.release()
            }
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.abandonAudioFocus(focusListener)
                }
            }
        }
    }

    // Auto-replenish queue when reaching last 2 tracks
    LaunchedEffect(controller.currentIndex, controller.queue.size, activeVideoId) {
        val track = controller.currentTrack ?: return@LaunchedEffect
        val remaining = controller.queue.size - 1 - controller.currentIndex
        if (controller.autoPlayEnabled && remaining <= 2) {
            val excluded = controller.queue.map { it.videoId }.toSet() + controller.failedVideoIds
            val more = runCatching { onNeedMoreTracks(track, excluded) }.getOrDefault(emptyList())
            if (more.isNotEmpty()) {
                controller.appendUpNext(more)
            }
        }
    }

    // Watchdog: Stage 1 stalls (>8s) switch seamlessly to Stage 2 same-track playback;
    // Stage 2 stalls (>10s) move cleanly to next playable track.
    LaunchedEffect(activeVideoId, controller.playRequestNonce, controller.playbackStage, controller.pausedByUser) {
        if (!controller.pausedByUser) {
            val waitMs = if (controller.playbackStage == YouTubePlaybackStage.IFRAME_EMBED) 8_500L else 10_500L
            delay(waitMs)
            if (controller.videoId == activeVideoId && !controller.pausedByUser &&
                controller.currentPositionSec == 0 && !controller.isPlaying
            ) {
                controller.skipStalledTrack()
            }
        }
    }

    val appEmbedOrigin = remember { resolveAppEmbedOrigin() }
    val embedHostUrl = remember(appEmbedOrigin) { "$appEmbedOrigin/brav-yt-embed.html" }
    val currentEmbedVideoId = remember { AtomicReference(activeVideoId) }
    val embedRequestHeaders = remember(appEmbedOrigin) {
        mapOf(
            "Referer" to "$appEmbedOrigin/",
            "Referrer-Policy" to "strict-origin-when-cross-origin"
        )
    }

    val webView = remember {
        KeepPlayingWebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowContentAccess = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.userAgentString = sanitizeWebViewUserAgent(settings.userAgentString)

            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(this, true)
            cm.setCookie(
                "https://www.youtube.com",
                "SOCS=CAESHAgBEhJnd3NfMjAyMzA4MTAtMF9SQzIaAmZyIAEaBgiA_LyaBg; Path=/; Secure; SameSite=Lax"
            )
            cm.setCookie(
                "https://m.youtube.com",
                "SOCS=CAESHAgBEhJnd3NfMjAyMzA4MTAtMF9SQzIaAmZyIAEaBgiA_LyaBg; Path=/; Secure; SameSite=Lax"
            )
            cm.flush()

            addJavascriptInterface(BravYouTubeBridge(controller), "BravYTBridge")

            webChromeClient = object : WebChromeClient() {
                override fun getDefaultVideoPoster(): Bitmap {
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    val protectedMedia = request?.resources
                        ?.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
                        ?.toTypedArray()
                    if (!protectedMedia.isNullOrEmpty()) {
                        request.grant(protectedMedia)
                    } else {
                        request?.deny()
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val reqUrl = request?.url?.toString().orEmpty()
                    if (reqUrl == embedHostUrl || reqUrl.startsWith("$embedHostUrl?")) {
                        val vid = currentEmbedVideoId.get().orEmpty().ifBlank { activeVideoId }
                        val html = buildYouTubeIframePlayerHtml(vid, appEmbedOrigin)
                        val headers = mapOf(
                            "Referrer-Policy" to "strict-origin-when-cross-origin",
                            "Cache-Control" to "no-store"
                        )
                        return WebResourceResponse(
                            "text/html",
                            "utf-8",
                            200,
                            "OK",
                            headers,
                            ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return true
                    val scheme = uri.scheme?.lowercase().orEmpty()
                    if (scheme != "http" && scheme != "https") return true
                    val host = uri.host?.lowercase().orEmpty()
                    val allowed = host.contains("youtube.com") ||
                        host.contains("youtu.be") ||
                        host.contains("youtube-nocookie.com") ||
                        host.contains("googlevideo.com") ||
                        host.contains("ytimg.com") ||
                        host.contains("google.com") ||
                        host.contains("gstatic.com") ||
                        host == Uri.parse(appEmbedOrigin).host?.lowercase()
                    return !allowed
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val loaded = url.orEmpty()
                    if (loaded.startsWith("https://m.youtube.com/watch")) {
                        view?.evaluateJavascript(MOBILE_WATCH_CONTROLLER_JS, null)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        val code = error?.errorCode ?: -4
                        // Ignore aborted/cancelled error (-1) when switching videos
                        if (code != -1) {
                            controller.onEmbedErrorOrTimeout(code)
                        }
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    controller.onEmbedErrorOrTimeout(-4)
                    return true
                }
            }

            currentEmbedVideoId.set(activeVideoId)
            loadUrl(embedHostUrl, embedRequestHeaders)
        }
    }

    webView.blockTouchWhenHidden = controller.isVideoHidden

    // Execute JS playback / stage commands cleanly on the live player instance
    LaunchedEffect(controller.pendingCommand) {
        val cmd = controller.pendingCommand ?: return@LaunchedEffect
        when (cmd) {
            is PlayerJsCommand.LoadVideo -> {
                currentEmbedVideoId.set(cmd.videoId)
                val currentLoadedUrl = webView.url.orEmpty()
                if (!currentLoadedUrl.startsWith(embedHostUrl)) {
                    webView.loadUrl(embedHostUrl, embedRequestHeaders)
                } else {
                    runCatching { webView.evaluateJavascript("window.bravLoadVideo('${cmd.videoId}');", null) }
                }
            }
            is PlayerJsCommand.LoadMobileWatch -> {
                val watchUrl = "https://m.youtube.com/watch?v=${cmd.videoId}&app=m"
                webView.loadUrl(watchUrl)
            }
            is PlayerJsCommand.Play -> {
                runCatching { webView.evaluateJavascript("window.bravPlay && window.bravPlay();", null) }
            }
            is PlayerJsCommand.Pause -> {
                runCatching { webView.evaluateJavascript("window.bravPause && window.bravPause();", null) }
            }
            is PlayerJsCommand.SeekTo -> {
                runCatching { webView.evaluateJavascript("window.bravSeekTo && window.bravSeekTo(${cmd.seconds});", null) }
            }
        }
        controller.consumeCommand()
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView.evaluateJavascript("window.bravPause && window.bravPause();", null)
                webView.stopLoading()
                webView.removeJavascriptInterface("BravYTBridge")
                webView.destroy()
            }
        }
    }

    val movableVideoSurface = remember(webView) {
        movableContentOf { surfaceModifier: Modifier ->
            AndroidView(
                factory = { webView },
                modifier = surfaceModifier
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // When video is hidden ("Masquer vidéo"), keep the WebView attached at 204x118 dp (0.01 alpha,
        // non-touch-blocking) so YouTube's viewport & Chromium's audio decoder stay 100% active.
        if (controller.isVideoHidden) {
            Box(
                modifier = Modifier
                    .size(width = 204.dp, height = 118.dp)
                    .alpha(0.01f)
                    .align(Alignment.BottomStart)
            ) {
                movableVideoSurface(Modifier.fillMaxSize())
            }
        }

        if (!controller.isMinimized) {
            // Expanded compact Music Player Overlay with a VERY SMALL ("tout petit") video viewport
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(95f)
                    .testTag("youtube_full_overlay"),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { controller.minimize() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("youtube_minimize_button")
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Réduire en mini-lecteur")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BRAV Musique",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = controller.playbackStatusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (controller.isPlaying) StatusOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { controller.toggleHideVideo() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("youtube_toggle_hide_video_button")
                        ) {
                            Icon(
                                imageVector = if (controller.isVideoHidden) Icons.Rounded.OndemandVideo else Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (controller.isVideoHidden) "Afficher" else "Masquer",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        if (!controller.isVideoHidden) {
                            IconButton(
                                onClick = { controller.toggleFullscreen() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("youtube_fullscreen_button")
                            ) {
                                Icon(
                                    imageVector = if (controller.isVideoFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                    contentDescription = if (controller.isVideoFullscreen) "Quitter plein écran" else "Agrandir la vidéo"
                                )
                            }
                        }
                        Spacer(Modifier.width(2.dp))
                        IconButton(
                            onClick = { controller.close() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("youtube_close_button")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Fermer le lecteur")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (controller.isVideoFullscreen && !controller.isVideoHidden) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                                .testTag("youtube_expanded_video_viewport")
                        ) {
                            movableVideoSurface(Modifier.fillMaxSize())
                        }
                    }

                    // Compact Now Playing Card — Tiny 112x64 video frame OR clean Audio-Only Cover!
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (!controller.isVideoHidden && !controller.isVideoFullscreen) {
                                    // Tout petit lecteur vidéo (112dp x 64dp)
                                    Box(
                                        modifier = Modifier
                                            .width(112.dp)
                                            .height(64.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black)
                                            .clickable { controller.toggleFullscreen() }
                                            .testTag("youtube_tiny_video_viewport")
                                    ) {
                                        movableVideoSurface(Modifier.fillMaxSize())
                                    }
                                } else {
                                    // Video visually hidden — audio keeps playing
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(BravPurple.copy(alpha = 0.12f))
                                            .testTag("youtube_audio_only_cover"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = controller.thumbnailUrl,
                                            contentDescription = "Pochette audio",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.35f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.GraphicEq,
                                                contentDescription = "Mode audio seul",
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = controller.videoTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = controller.channelTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (controller.isVideoHidden) {
                                        Spacer(Modifier.height(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = StatusOnlineGreen.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = "Vidéo masquée • Audio continu",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StatusOnlineGreen,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Seek Slider + Time labels
                            val totalDur = controller.durationSec.coerceAtLeast(1)
                            val sliderValue = controller.currentPositionSec.coerceIn(0, totalDur).toFloat()
                            Column {
                                Slider(
                                    value = sliderValue,
                                    onValueChange = { controller.seekTo(it.toInt()) },
                                    valueRange = 0f..totalDur.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = BravPurple,
                                        activeTrackColor = BravPurple
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatPlaybackSec(controller.currentPositionSec),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (controller.durationSec > 0) formatPlaybackSec(controller.durationSec)
                                        else controller.durationLabel.ifBlank { "--:--" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Playback Transport Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { controller.toggleAutoPlay() },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("youtube_autoplay_toggle")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoMode,
                                        contentDescription = "Lecture automatique",
                                        tint = if (controller.autoPlayEnabled) BravPurple else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { controller.playPrevious() },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("youtube_prev_button")
                                ) {
                                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Précédent", modifier = Modifier.size(30.dp))
                                }
                                FilledIconButton(
                                    onClick = { controller.togglePlayPause() },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = BravPurple,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .size(56.dp)
                                        .testTag("youtube_play_pause_button")
                                ) {
                                    Icon(
                                        imageVector = if (controller.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (controller.isPlaying) "Pause" else "Lecture",
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { controller.playNext() },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("youtube_next_button")
                                ) {
                                    Icon(Icons.Rounded.SkipNext, contentDescription = "Suivant", modifier = Modifier.size(30.dp))
                                }
                                IconButton(
                                    onClick = { controller.toggleRepeatOne() },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("youtube_repeat_toggle")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RepeatOne,
                                        contentDescription = "Répéter ce titre",
                                        tint = if (controller.repeatOne) BravPurple else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Up Next Queue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "File d'attente (${controller.queue.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (controller.autoPlayEnabled) "Enchaînement auto activé" else "Enchaînement manuel",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (controller.autoPlayEnabled) StatusOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(controller.queue, key = { idx, item -> "${idx}_${item.videoId}" }) { idx, track ->
                            val isActive = idx == controller.currentIndex
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { controller.playTrack(track, controller.queue, startMinimized = false) },
                                color = if (isActive) BravPurple.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
                                tonalElevation = if (isActive) 1.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    AsyncImage(
                                        model = track.thumbnailUrl,
                                        contentDescription = track.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isActive) BravPurple else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = listOf(track.channelTitle, track.durationLabel)
                                                .filter { it.isNotBlank() }
                                                .joinToString(" • "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = "En lecture",
                                            tint = BravPurple,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Minimized Persistent Mini-Player Bar (tiny 56x36 video OR cover when hidden)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = minimizedBottomOffset)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .zIndex(92f)
                    .testTag("youtube_mini_player"),
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { controller.expand() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!controller.isVideoHidden) {
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                            movableVideoSurface(Modifier.fillMaxSize())
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(BravPurple.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = "Audio actif",
                                tint = BravPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = controller.videoTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (controller.isVideoHidden) "Audio seul • ${controller.channelTitle}" else controller.channelTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (controller.isPlaying) StatusOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { controller.togglePlayPause() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("youtube_mini_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (controller.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (controller.isPlaying) "Pause" else "Lecture",
                            tint = BravPurple
                        )
                    }
                    IconButton(
                        onClick = { controller.playNext() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("youtube_mini_next_button")
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Titre suivant")
                    }
                    IconButton(
                        onClick = { controller.toggleHideVideo() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("youtube_mini_hide_video_button")
                    ) {
                        Icon(
                            imageVector = if (controller.isVideoHidden) Icons.Rounded.OndemandVideo else Icons.Rounded.VisibilityOff,
                            contentDescription = if (controller.isVideoHidden) "Afficher la vidéo" else "Masquer la vidéo"
                        )
                    }
                    IconButton(
                        onClick = { controller.expand() },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Agrandir")
                    }
                    IconButton(
                        onClick = { controller.close() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("youtube_mini_close_button")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Arrêter", tint = BravRed)
                    }
                }
            }
        }
    }
}

private fun formatPlaybackSec(sec: Int): String {
    val safe = sec.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}

internal fun resolveAppEmbedOrigin(): String {
    val pkg = BuildConfig.APPLICATION_ID.trim().ifBlank { "bravchat.com" }
    return "https://$pkg"
}

internal fun sanitizeWebViewUserAgent(rawUa: String?): String {
    val base = rawUa?.trim().orEmpty()
    if (base.isBlank()) {
        return "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
    return base
        .replace("; wv)", ")")
        .replace("; wv;", ";")
        .replace(Regex("""\s+Version/\d+\.\d+"""), "")
        .trim()
}

private const val MOBILE_WATCH_CONTROLLER_JS = """
(function() {
  if (window.__bravMobileWatchActive) return;
  window.__bravMobileWatchActive = true;
  try {
    var st = document.getElementById('brav-clean-style');
    if (!st) {
      st = document.createElement('style');
      st.id = 'brav-clean-style';
      st.textContent = 'html,body{margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;} ' +
        'ytm-mobile-topbar-renderer,ytm-comments-entry-point-header-renderer,.related-chips-slot-wrapper,ytm-watch-next-secondary-results-renderer,ytm-item-section-renderer{display:none!important;} ' +
        'video{position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;z-index:2147483647!important;background:#000!important;object-fit:contain!important;}';
      document.head.appendChild(st);
    }
  } catch (e) {}

  var hookedVideo = null;
  var errTicks = 0;
  window.__bravAllowPause = false;

  window.bravPlay = function() {
    window.__bravAllowPause = false;
    var v = document.querySelector('video');
    if (v) { try { v.muted = false; v.volume = 1.0; v.play(); } catch (e) {} }
  };
  window.bravPause = function() {
    window.__bravAllowPause = true;
    var v = document.querySelector('video');
    if (v) { try { v.pause(); } catch (e) {} }
  };
  window.bravSeekTo = function(sec) {
    var v = document.querySelector('video');
    if (v) { try { v.currentTime = sec; } catch (e) {} }
  };

  setInterval(function() {
    try {
      var unmuteBtn = document.querySelector('.ytp-unmute');
      if (unmuteBtn && unmuteBtn.offsetParent !== null) unmuteBtn.click();
      if (location.hostname && location.hostname.indexOf('consent') !== -1) {
        var rejectBtn = document.querySelector('form[action*="consent"] button');
        if (rejectBtn) rejectBtn.click();
      }
      var confirmDialogBtn = document.querySelector('yt-confirm-dialog-renderer #confirm-button button, .ytm-confirm-dialog-renderer button');
      if (confirmDialogBtn) confirmDialogBtn.click();
    } catch (e) {}

    var v = document.querySelector('video');
    if (v && v !== hookedVideo) {
      hookedVideo = v;
      try { if (window.BravYTBridge) BravYTBridge.onReady(); } catch (e) {}
      v.addEventListener('playing', function() {
        window.__bravAllowPause = false;
        try { if (window.BravYTBridge) BravYTBridge.onStateChange(1); } catch (e) {}
      });
      v.addEventListener('pause', function() {
        if (!v.ended) {
          if (!window.__bravAllowPause) {
            setTimeout(function() {
              if (!window.__bravAllowPause && !v.ended && v.paused) {
                try { v.play(); } catch (e) {}
              }
            }, 220);
          }
          try { if (window.BravYTBridge) BravYTBridge.onStateChange(2); } catch (e) {}
        }
      });
      v.addEventListener('ended', function() {
        try { if (window.BravYTBridge) BravYTBridge.onStateChange(0); } catch (e) {}
      });
      v.addEventListener('waiting', function() {
        try { if (window.BravYTBridge) BravYTBridge.onStateChange(3); } catch (e) {}
      });
      v.addEventListener('error', function() {
        try { if (window.BravYTBridge) BravYTBridge.onPlayerError(5); } catch (e) {}
      });
      try {
        v.muted = false;
        v.volume = 1.0;
        var p = v.play();
        if (p && p.catch) {
          p.catch(function() {
            v.muted = true;
            v.play().then(function() { setTimeout(function() { v.muted = false; }, 350); }).catch(function(){});
          });
        }
      } catch (e) {}
    }
    if (v) {
      errTicks = 0;
      if (!window.__bravAllowPause && !v.ended && v.paused && v.readyState >= 2) {
        try { v.play(); } catch (e) {}
      }
      try {
        if (window.BravYTBridge) {
          BravYTBridge.onProgress(Math.floor(v.currentTime || 0), Math.floor(v.duration || 0));
        }
      } catch (e) {}
    } else {
      var errEl = document.querySelector('ytm-player-error-message-renderer, .player-error-screen');
      if (errEl) {
        errTicks++;
        if (errTicks >= 3 && window.BravYTBridge) {
          BravYTBridge.onPlayerError(100);
        }
      }
    }
  }, 800);
})();
"""

internal fun buildYouTubeIframePlayerHtml(
    initialVideoId: String,
    appOrigin: String = resolveAppEmbedOrigin()
): String {
    val escaped = initialVideoId.replace("'", "").replace("\"", "").trim()
    val safeOrigin = appOrigin.trim().removeSuffix("/")
    val encodedOrigin = URLEncoder.encode(safeOrigin, "UTF-8")
    val iframeSrc = "https://www.youtube.com/embed/$escaped" +
        "?enablejsapi=1&autoplay=1&playsinline=1&controls=1&rel=0&modestbranding=1&fs=0&iv_load_policy=3" +
        "&origin=$encodedOrigin&widget_referrer=$encodedOrigin"

    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8"/>
          <meta name="referrer" content="strict-origin-when-cross-origin"/>
          <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
          <style>
            html, body { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
            #player { width:100%; height:100%; border:0; display:block; }
          </style>
        </head>
        <body>
          <iframe
            id="player"
            width="100%"
            height="100%"
            src="$iframeSrc"
            referrerpolicy="strict-origin-when-cross-origin"
            allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
            allowfullscreen></iframe>
          <script>
            var bravPlayer = null;
            var bravReady = false;
            var activeVideoId = '$escaped';
            var pendingVideoId = '$escaped';
            var errorReportedForVid = {};
            window.__bravAllowPause = false;

            function reportErrorOnce(code) {
              if (errorReportedForVid[pendingVideoId]) return;
              errorReportedForVid[pendingVideoId] = true;
              if (window.BravYTBridge) BravYTBridge.onPlayerError(code);
            }

            var tag = document.createElement('script');
            tag.src = 'https://www.youtube.com/iframe_api';
            tag.onerror = function() {
              reportErrorOnce(999);
            };
            var firstScriptTag = document.getElementsByTagName('script')[0];
            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

            function onYouTubeIframeAPIReady() {
              try {
                bravPlayer = new YT.Player('player', {
                  events: {
                    onReady: function(event) {
                      bravReady = true;
                      if (pendingVideoId && pendingVideoId !== activeVideoId) {
                        activeVideoId = pendingVideoId;
                        try { event.target.loadVideoById({ videoId: activeVideoId, startSeconds: 0 }); } catch (e) {}
                      }
                      try { event.target.unMute(); } catch (e) {}
                      try { event.target.playVideo(); } catch (e) {}
                      if (window.BravYTBridge) BravYTBridge.onReady();
                    },
                    onStateChange: function(event) {
                      if (event.data === 1) {
                        window.__bravAllowPause = false;
                      } else if (event.data === 2 && !window.__bravAllowPause) {
                        setTimeout(function() {
                          if (!window.__bravAllowPause && bravPlayer && typeof bravPlayer.playVideo === 'function') {
                            try { bravPlayer.playVideo(); } catch (e) {}
                          }
                        }, 220);
                      }
                      if (window.BravYTBridge) BravYTBridge.onStateChange(event.data);
                    },
                    onError: function(event) {
                      reportErrorOnce(event && event.data ? event.data : 152);
                    }
                  }
                });
              } catch (err) {
                reportErrorOnce(500);
              }
            }

            window.bravLoadVideo = function(vid) {
              if (!vid) return;
              window.__bravAllowPause = false;
              pendingVideoId = vid;
              errorReportedForVid[vid] = false;
              if (bravPlayer && bravReady && typeof bravPlayer.loadVideoById === 'function') {
                try {
                  activeVideoId = vid;
                  bravPlayer.loadVideoById({ videoId: vid, startSeconds: 0 });
                  bravPlayer.unMute();
                  bravPlayer.playVideo();
                  return;
                } catch (e) {}
              }
              var frame = document.getElementById('player');
              if (frame) {
                activeVideoId = vid;
                frame.src = 'https://www.youtube.com/embed/' + encodeURIComponent(vid) +
                  '?enablejsapi=1&autoplay=1&playsinline=1&controls=1&rel=0&modestbranding=1&fs=0&iv_load_policy=3' +
                  '&origin=$encodedOrigin&widget_referrer=$encodedOrigin';
              }
            };

            window.bravPlay = function() {
              window.__bravAllowPause = false;
              if (bravPlayer && bravReady && typeof bravPlayer.playVideo === 'function') {
                try { bravPlayer.unMute(); bravPlayer.playVideo(); } catch (e) {}
              }
            };

            window.bravPause = function() {
              window.__bravAllowPause = true;
              if (bravPlayer && bravReady && typeof bravPlayer.pauseVideo === 'function') {
                try { bravPlayer.pauseVideo(); } catch (e) {}
              }
            };

            window.bravSeekTo = function(sec) {
              if (bravPlayer && bravReady && typeof bravPlayer.seekTo === 'function') {
                try { bravPlayer.seekTo(sec, true); } catch (e) {}
              }
            };

            setInterval(function() {
              if (bravPlayer && bravReady && typeof bravPlayer.getCurrentTime === 'function') {
                try {
                  var pos = Math.floor(bravPlayer.getCurrentTime() || 0);
                  var dur = Math.floor(bravPlayer.getDuration() || 0);
                  if (window.BravYTBridge) BravYTBridge.onProgress(pos, dur);
                } catch (e) {}
              }
            }, 1000);
          </script>
        </body>
        </html>
    """.trimIndent()
}
