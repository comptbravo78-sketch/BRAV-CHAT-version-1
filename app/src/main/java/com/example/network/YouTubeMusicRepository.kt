package com.example.network

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class YouTubeTrack(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val durationLabel: String = "",
    val embeddable: Boolean = true
)

data class YouTubeMusicSearchResult(
    val tracks: List<YouTubeTrack>,
    val isUsingConfiguredKey: Boolean,
    val warningMessage: String? = null
)

class YouTubeMusicRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val apiKeyProvider: () -> String = { BuildConfig.YOUTUBE_DATA_API_KEY }
) {

    fun isApiKeyConfigured(): Boolean {
        val key = apiKeyProvider().trim()
        return key.isNotBlank() && !key.contains("YOUR_YOUTUBE_DATA_API_KEY", ignoreCase = true)
    }

    suspend fun searchMusic(
        query: String,
        maxResults: Int = 20
    ): YouTubeMusicSearchResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext fetchTrendingMusic(maxResults = maxResults)
        }

        val apiKey = apiKeyProvider().trim()
        if (isApiKeyConfigured()) {
            val searchUrl = "https://www.googleapis.com/youtube/v3/search".toHttpUrl().newBuilder()
                .addQueryParameter("part", "snippet")
                .addQueryParameter("type", "video")
                .addQueryParameter("videoCategoryId", "10")
                .addQueryParameter("videoEmbeddable", "true")
                .addQueryParameter("videoSyndicated", "true")
                .addQueryParameter("safeSearch", "none")
                .addQueryParameter("maxResults", maxResults.coerceIn(1, 30).toString())
                .addQueryParameter("q", trimmed)
                .addQueryParameter("key", apiKey)
                .build()

            val searchReq = Request.Builder().url(searchUrl).get().build()
            val apiTracks = runCatching {
                httpClient.newCall(searchReq).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "YouTube Data API search HTTP ${resp.code}")
                        null
                    } else {
                        val candidateIds = extractSearchVideoIds(raw)
                        if (candidateIds.isEmpty()) {
                            emptyList()
                        } else {
                            val verified = verifyEmbeddableTracks(candidateIds, apiKey)
                            verified.ifEmpty { parseSearchSnippetFallback(raw) }
                        }
                    }
                }
            }.getOrNull()

            if (!apiTracks.isNullOrEmpty()) {
                return@withContext YouTubeMusicSearchResult(
                    tracks = apiTracks,
                    isUsingConfiguredKey = true
                )
            }
        }

        // Seamless automatic fallback (works immediately even if Secrets key is not injected or quota exhausted)
        val publicTracks = searchPublicYouTube(trimmed, maxResults = maxResults)
        if (publicTracks.isNotEmpty()) {
            return@withContext YouTubeMusicSearchResult(
                tracks = publicTracks,
                isUsingConfiguredKey = isApiKeyConfigured()
            )
        }

        YouTubeMusicSearchResult(
            tracks = emptyList(),
            isUsingConfiguredKey = isApiKeyConfigured(),
            warningMessage = "Impossible de joindre YouTube Musique. Vérifiez votre connexion Internet."
        )
    }

    suspend fun fetchTrendingMusic(
        regionCode: String = "FR",
        maxResults: Int = 20
    ): YouTubeMusicSearchResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (isApiKeyConfigured()) {
            val deviceCountry = Locale.getDefault().country.trim().uppercase().takeIf { it.length == 2 }
            val regionsToTry = listOfNotNull(deviceCountry, regionCode, "FR", "CI", "US").distinct()
            for (region in regionsToTry) {
                val url = "https://www.googleapis.com/youtube/v3/videos".toHttpUrl().newBuilder()
                    .addQueryParameter("part", "snippet,contentDetails,status")
                    .addQueryParameter("chart", "mostPopular")
                    .addQueryParameter("videoCategoryId", "10")
                    .addQueryParameter("regionCode", region)
                    .addQueryParameter("maxResults", maxResults.coerceIn(1, 30).toString())
                    .addQueryParameter("key", apiKey)
                    .build()

                val request = Request.Builder().url(url).get().build()
                val result = runCatching {
                    httpClient.newCall(request).execute().use { resp ->
                        val raw = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            parseVideosResponse(raw, requireEmbeddable = true)
                        } else {
                            Log.w(TAG, "YouTube trending $region HTTP ${resp.code}")
                            null
                        }
                    }
                }.getOrNull()

                if (!result.isNullOrEmpty()) {
                    return@withContext YouTubeMusicSearchResult(
                        tracks = result,
                        isUsingConfiguredKey = true
                    )
                }
            }
        }

        // Fallback to live YouTube music hits search so Trending never stays empty
        val fallbackTracks = searchPublicYouTube("afrobeats hits mix official music", maxResults = maxResults)
        if (fallbackTracks.isNotEmpty()) {
            return@withContext YouTubeMusicSearchResult(
                tracks = fallbackTracks,
                isUsingConfiguredKey = isApiKeyConfigured()
            )
        }

        YouTubeMusicSearchResult(
            tracks = emptyList(),
            isUsingConfiguredKey = isApiKeyConfigured(),
            warningMessage = "Aucun titre disponible pour le moment. Vérifiez votre connexion."
        )
    }

    suspend fun resolveTrackByVideoId(videoId: String): YouTubeTrack? = withContext(Dispatchers.IO) {
        val cleanId = videoId.trim()
        if (cleanId.isBlank()) return@withContext null
        val fallback = YouTubeTrack(
            videoId = cleanId,
            title = "Lecture YouTube",
            channelTitle = "YouTube Musique",
            thumbnailUrl = "https://i.ytimg.com/vi/$cleanId/hqdefault.jpg",
            embeddable = true
        )
        val apiKey = apiKeyProvider().trim()
        if (isApiKeyConfigured()) {
            val tracks = verifyEmbeddableTracks(listOf(cleanId), apiKey)
            if (tracks.isNotEmpty()) return@withContext tracks.first()
        }

        // Public oEmbed fallback to retrieve real title and artist without API key
        val oembedUrl = "https://www.youtube.com/oembed".toHttpUrl().newBuilder()
            .addQueryParameter("url", "https://www.youtube.com/watch?v=$cleanId")
            .addQueryParameter("format", "json")
            .build()
        val oembedReq = Request.Builder().url(oembedUrl).get().build()
        val oembedTrack = runCatching {
            httpClient.newCall(oembedReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val j = JSONObject(resp.body?.string().orEmpty())
                YouTubeTrack(
                    videoId = cleanId,
                    title = j.optString("title", "Lecture YouTube").ifBlank { "Lecture YouTube" },
                    channelTitle = j.optString("author_name", "YouTube Musique").ifBlank { "YouTube Musique" },
                    thumbnailUrl = j.optString("thumbnail_url")
                        .ifBlank { "https://i.ytimg.com/vi/$cleanId/hqdefault.jpg" },
                    embeddable = true
                )
            }
        }.getOrNull()

        oembedTrack ?: fallback
    }

    suspend fun fetchUpNext(
        seedTitle: String,
        seedChannel: String,
        currentVideoId: String,
        excludedIds: Set<String>
    ): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        val cleanKeywords = seedTitle
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace(Regex("""(?i)\b(official|video|audio|lyrics|hd|4k|clip|officiel)\b"""), " ")
            .trim()
        val query = listOf(seedChannel.trim(), cleanKeywords)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "music mix" }

        val res = searchMusic(query = query, maxResults = 15)
        res.tracks.filter { track ->
            track.videoId != currentVideoId && track.videoId !in excludedIds && track.embeddable
        }
    }

    private fun searchPublicYouTube(query: String, maxResults: Int = 20): List<YouTubeTrack> {
        val searchUrl = "https://www.youtube.com/results".toHttpUrl().newBuilder()
            .addQueryParameter("search_query", query)
            .addQueryParameter("sp", "EgIQAQ%3D%3D")
            .build()
        val req = Request.Builder()
            .url(searchUrl)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
            .get()
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val html = resp.body?.string().orEmpty()
                parsePublicSearchHtml(html).take(maxResults)
            }
        }.onFailure { err ->
            Log.w(TAG, "Public YouTube fallback search failed: ${err.message}")
        }.getOrDefault(emptyList())
    }

    private fun verifyEmbeddableTracks(
        videoIds: List<String>,
        apiKey: String
    ): List<YouTubeTrack> {
        if (videoIds.isEmpty()) return emptyList()
        val idsJoined = videoIds.distinct().take(30).joinToString(",")
        val url = "https://www.googleapis.com/youtube/v3/videos".toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet,contentDetails,status")
            .addQueryParameter("id", idsJoined)
            .addQueryParameter("key", apiKey)
            .build()

        val request = Request.Builder().url(url).get().build()
        return runCatching {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) emptyList()
                else parseVideosResponse(resp.body?.string().orEmpty(), requireEmbeddable = true)
            }
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val TAG = "YouTubeMusicRepo"

        fun extractSearchVideoIds(searchJson: String): List<String> {
            return runCatching {
                val root = JSONObject(searchJson)
                val items = root.optJSONArray("items") ?: return emptyList()
                buildList {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val vid = item.optJSONObject("id")?.optString("videoId").orEmpty().trim()
                        if (vid.isNotBlank()) add(vid)
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun parseSearchSnippetFallback(searchJson: String): List<YouTubeTrack> {
            return runCatching {
                val root = JSONObject(searchJson)
                val items = root.optJSONArray("items") ?: return emptyList()
                buildList {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val vid = item.optJSONObject("id")?.optString("videoId").orEmpty().trim()
                        val snippet = item.optJSONObject("snippet") ?: continue
                        if (vid.isBlank()) continue
                        add(
                            YouTubeTrack(
                                videoId = vid,
                                title = decodeHtmlEntities(snippet.optString("title", "Titre YouTube")),
                                channelTitle = decodeHtmlEntities(snippet.optString("channelTitle", "YouTube")),
                                thumbnailUrl = pickThumbnail(snippet, vid),
                                durationLabel = "",
                                embeddable = true
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun parseVideosResponse(videosJson: String, requireEmbeddable: Boolean = true): List<YouTubeTrack> {
            return runCatching {
                val root = JSONObject(videosJson)
                val items = root.optJSONArray("items") ?: return emptyList()
                buildList {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val vid = item.optString("id").trim()
                        if (vid.isBlank()) continue
                        val status = item.optJSONObject("status")
                        val embeddable = status?.optBoolean("embeddable", true) ?: true
                        val privacyStatus = status?.optString("privacyStatus", "public").orEmpty()
                        val uploadStatus = status?.optString("uploadStatus", "processed").orEmpty()
                        if (requireEmbeddable && (
                                !embeddable ||
                                    privacyStatus == "private" ||
                                    uploadStatus in setOf("rejected", "failed", "deleted")
                                )
                        ) {
                            continue
                        }
                        val snippet = item.optJSONObject("snippet") ?: continue
                        val contentDetails = item.optJSONObject("contentDetails")
                        if (requireEmbeddable && isBlockedInCurrentRegion(contentDetails?.optJSONObject("regionRestriction"))) {
                            continue
                        }
                        val isoDuration = contentDetails?.optString("duration").orEmpty()
                        add(
                            YouTubeTrack(
                                videoId = vid,
                                title = decodeHtmlEntities(snippet.optString("title", "Titre YouTube")),
                                channelTitle = decodeHtmlEntities(snippet.optString("channelTitle", "YouTube")),
                                thumbnailUrl = pickThumbnail(snippet, vid),
                                durationLabel = formatIsoDuration(isoDuration),
                                embeddable = embeddable
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun parsePublicSearchHtml(html: String): List<YouTubeTrack> {
            if (html.isBlank()) return emptyList()
            val match = Regex("""var ytInitialData\s*=\s*(\{.*?\});</script>""").find(html)
                ?: return emptyList()
            return runCatching {
                val root = JSONObject(match.groupValues[1])
                val sections = root
                    .optJSONObject("contents")
                    ?.optJSONObject("twoColumnSearchResultsRenderer")
                    ?.optJSONObject("primaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents") ?: return emptyList()

                val out = mutableListOf<YouTubeTrack>()
                val seen = mutableSetOf<String>()
                for (i in 0 until sections.length()) {
                    val items = sections.optJSONObject(i)
                        ?.optJSONObject("itemSectionRenderer")
                        ?.optJSONArray("contents") ?: continue
                    for (j in 0 until items.length()) {
                        val vr = items.optJSONObject(j)?.optJSONObject("videoRenderer") ?: continue
                        val vid = vr.optString("videoId").trim()
                        if (vid.isBlank() || !seen.add(vid)) continue
                        val title = vr.optJSONObject("title")
                            ?.optJSONArray("runs")
                            ?.optJSONObject(0)
                            ?.optString("text")
                            ?.trim()
                            .orEmpty()
                        if (title.isBlank()) continue
                        val channel = vr.optJSONObject("ownerText")
                            ?.optJSONArray("runs")
                            ?.optJSONObject(0)
                            ?.optString("text")
                            ?.trim()
                            .orEmpty()
                            .ifBlank { "YouTube Musique" }
                        val duration = vr.optJSONObject("lengthText")
                            ?.optString("simpleText")
                            ?.trim()
                            .orEmpty()
                        // Skip live streams without fixed duration if ambiguous, keep normal music tracks
                        if (duration.isBlank()) continue
                        val thumbs = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        val thumbUrl = thumbs?.optJSONObject((thumbs.length() - 1).coerceAtLeast(0))
                            ?.optString("url")
                            .orEmpty()
                            .ifBlank { "https://i.ytimg.com/vi/$vid/hqdefault.jpg" }
                        out.add(
                            YouTubeTrack(
                                videoId = vid,
                                title = decodeHtmlEntities(title),
                                channelTitle = decodeHtmlEntities(channel),
                                thumbnailUrl = thumbUrl,
                                durationLabel = duration,
                                embeddable = true
                            )
                        )
                    }
                }
                out
            }.getOrDefault(emptyList())
        }

        fun formatIsoDuration(iso: String): String {
            if (iso.isBlank()) return ""
            val match = Regex("""^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$""").find(iso.trim()) ?: return ""
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            val seconds = match.groupValues[3].toIntOrNull() ?: 0
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }

        private fun pickThumbnail(snippet: JSONObject, videoId: String): String {
            val thumbs = snippet.optJSONObject("thumbnails")
            val candidate = thumbs?.optJSONObject("high")?.optString("url")
                ?.takeIf { it.isNotBlank() }
                ?: thumbs?.optJSONObject("medium")?.optString("url")
                    ?.takeIf { it.isNotBlank() }
                ?: thumbs?.optJSONObject("default")?.optString("url")
                    ?.takeIf { it.isNotBlank() }
            return candidate ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        }

        private fun isBlockedInCurrentRegion(regionRestriction: JSONObject?): Boolean {
            if (regionRestriction == null) return false
            val country = Locale.getDefault().country.trim().uppercase()
            if (country.length != 2) return false
            val blocked = regionRestriction.optJSONArray("blocked")
            if (blocked != null) {
                for (i in 0 until blocked.length()) {
                    if (blocked.optString(i).equals(country, ignoreCase = true)) return true
                }
            }
            val allowed = regionRestriction.optJSONArray("allowed")
            if (allowed != null && allowed.length() > 0) {
                var found = false
                for (i in 0 until allowed.length()) {
                    if (allowed.optString(i).equals(country, ignoreCase = true)) {
                        found = true
                        break
                    }
                }
                if (!found) return true
            }
            return false
        }

        private fun decodeHtmlEntities(input: String): String {
            return input
                .replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        }
    }
}
