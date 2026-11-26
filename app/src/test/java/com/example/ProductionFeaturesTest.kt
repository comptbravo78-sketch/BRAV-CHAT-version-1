package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.model.Contact
import com.example.model.User
import com.example.network.YouTubeMusicRepository
import com.example.network.YouTubeTrack
import com.example.repository.AiTranslationRepository
import com.example.telecom.CallDirection
import com.example.telecom.CallerIdResolver
import com.example.ui.components.YouTubePlayerController
import com.example.util.CountryDialCodeHelper
import com.example.util.DefaultDialerHelper
import com.example.util.DefaultSmsHelper
import com.example.util.PhoneLocalityResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProductionFeaturesTest {

    @Test
    fun phoneLocalityResolver_identifiesFixedCitiesLocallyAndInternationally() {
        val orangeCi = PhoneLocalityResolver.resolve("+225 07 01 02 03 04")
        assertEquals("Côte d'Ivoire", orangeCi.countryName)
        assertEquals("CI", orangeCi.countryIso)
        assertEquals("Orange CI", orangeCi.operatorHint)

        // Local 10-digit Ivorian landline without +225 prefix
        val cocodyLocal = PhoneLocalityResolver.resolve("2722445566", defaultCountryIso = "CI")
        assertEquals("+2252722445566", cocodyLocal.normalizedNumber)
        assertEquals("Fixe", cocodyLocal.lineType)
        assertEquals("Abidjan", cocodyLocal.cityName)
        assertTrue(cocodyLocal.localityHint.contains("Cocody"))

        val yakroFixe = PhoneLocalityResolver.resolve("+225 27 30 61 22 33")
        assertEquals("Yamoussoukro", yakroFixe.cityName)

        val bouakeFixe = PhoneLocalityResolver.resolve("2731634455", defaultCountryIso = "CI")
        assertEquals("Bouaké", bouakeFixe.cityName)

        val sanPedroFixe = PhoneLocalityResolver.resolve("+2252734712233")
        assertEquals("San-Pédro", sanPedroFixe.cityName)

        val korhogoFixe = PhoneLocalityResolver.resolve("+2252736861122")
        assertEquals("Korhogo", korhogoFixe.cityName)

        val bassamFixe = PhoneLocalityResolver.resolve("+2252721301122")
        assertEquals("Grand-Bassam", bassamFixe.cityName)

        val urgencyCi = PhoneLocalityResolver.resolve("180", defaultCountryIso = "CI")
        assertEquals("Côte d'Ivoire", urgencyCi.countryName)
        assertTrue(urgencyCi.localityHint.contains("Pompiers"))

        // France local + international fixed city precision
        val parisLocal = PhoneLocalityResolver.resolve("0142685500", defaultCountryIso = "FR")
        assertEquals("France", parisLocal.countryName)
        assertEquals("Paris", parisLocal.cityName)
        assertEquals("Fixe", parisLocal.lineType)

        val lyonFixe = PhoneLocalityResolver.resolve("+33 4 72 10 20 30")
        assertEquals("Lyon", lyonFixe.cityName)

        val marseilleFixe = PhoneLocalityResolver.resolve("+33491102030")
        assertEquals("Marseille", marseilleFixe.cityName)

        val bordeauxFixe = PhoneLocalityResolver.resolve("+33556102030")
        assertEquals("Bordeaux", bordeauxFixe.cityName)

        // West Africa & international fixed cities
        val dakarFixe = PhoneLocalityResolver.resolve("+221 33 821 00 00")
        assertEquals("Dakar", dakarFixe.cityName)

        val yaoundeFixe = PhoneLocalityResolver.resolve("+237 222 20 00 00")
        assertEquals("Yaoundé", yaoundeFixe.cityName)

        val casaFixe = PhoneLocalityResolver.resolve("+212 522 11 22 33")
        assertEquals("Casablanca", casaFixe.cityName)

        val ouagaFixe = PhoneLocalityResolver.resolve("+226 25 30 11 22")
        assertEquals("Ouagadougou", ouagaFixe.cityName)

        val bruxellesFixe = PhoneLocalityResolver.resolve("+32 2 500 11 22")
        assertEquals("Bruxelles", bruxellesFixe.cityName)

        val mtl = PhoneLocalityResolver.resolve("+1 514 555 0199")
        assertEquals("Montréal, QC", mtl.localityHint)
        assertEquals("Canada", mtl.countryName)
    }

    @Test
    fun callerIdResolver_identifiesIncomingAndOutgoingNumbersWithCity() {
        val knownContact = Contact(
            id = "c1",
            phoneDisplayName = "Cabinet Kouakou",
            rawPhoneNumber = "2722445566",
            normalizedPhoneNumber = "+2252722445566"
        )
        val outgoing = CallerIdResolver.resolveFromKnownData(
            rawNumber = "+225 27 22 44 55 66",
            direction = CallDirection.OUTGOING,
            phoneContacts = listOf(knownContact),
            defaultCountryIso = "CI"
        )
        assertEquals(CallDirection.OUTGOING, outgoing.direction)
        assertEquals("Cabinet Kouakou", outgoing.displayName)
        assertTrue(outgoing.isKnownContact)
        assertTrue(outgoing.cityBadge.contains("Abidjan"))
        assertTrue(outgoing.fullLocationSummary.contains("Cocody"))

        val incomingUnknownParis = CallerIdResolver.resolveFromKnownData(
            rawNumber = "+33 1 42 68 55 00",
            direction = CallDirection.INCOMING,
            defaultCountryIso = "CI"
        )
        assertEquals(CallDirection.INCOMING, incomingUnknownParis.direction)
        assertFalse(incomingUnknownParis.isKnownContact)
        assertTrue(incomingUnknownParis.cityBadge.contains("Paris"))
        assertTrue(incomingUnknownParis.fullLocationSummary.contains("Île-de-France"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            DefaultDialerHelper.createDefaultAppsSettingsIntent().action
        )
        DefaultDialerHelper.isCallScreeningSupported(context)
        DefaultDialerHelper.isBravCallScreeningHeld(context)
    }

    @Test
    fun youtubeHelper_extractsVideoIdsAndUrlsFromMessages() {
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubePlayerController.extractYouTubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubePlayerController.extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ?t=12")
        )
        assertEquals(
            "abcDEF12345",
            YouTubePlayerController.extractYouTubeVideoId("https://youtube.com/shorts/abcDEF12345")
        )
        assertEquals(
            "xyz98765432",
            YouTubePlayerController.extractYouTubeVideoId("https://www.youtube.com/embed/xyz98765432")
        )

        val message = "Regarde cette vidéo : https://youtu.be/dQw4w9WgXcQ trop bien !"
        val found = YouTubePlayerController.findFirstYouTubeUrl(message)
        assertNotNull(found)
        assertTrue(found!!.contains("youtu.be/dQw4w9WgXcQ"))
        assertNull(YouTubePlayerController.findFirstYouTubeUrl("Juste un texte normal"))
    }

    @Test
    fun defaultSmsHelper_parsesSmsToAndBodyFromIntent() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+2250701020304")).apply {
            putExtra("sms_body", "Bonjour depuis BRAV")
        }
        val parsed = DefaultSmsHelper.extractSmsTargetFromIntent(intent)
        assertEquals("+2250701020304" to "Bonjour depuis BRAV", parsed)

        val unrelated = Intent(Intent.ACTION_MAIN)
        assertNull(DefaultSmsHelper.extractSmsTargetFromIntent(unrelated))
    }

    @Test
    fun countryHelperAndAiModels_haveValidDefaults() {
        val ci = CountryDialCodeHelper.byIso("ci")
        assertNotNull(ci)
        assertEquals("+225", ci!!.dialCode)

        val fr = CountryDialCodeHelper.byIso("FR")
        assertNotNull(fr)
        assertEquals("+33", fr!!.dialCode)

        assertTrue(AiTranslationRepository.defaultFreeModels.isNotEmpty())
        assertTrue(AiTranslationRepository.defaultFreeModels.any { it.id.contains("llama") })
    }

    @Test
    fun youtubeMusicRepository_parsesIsoDurationsAndFiltersUnembeddableVideos() {
        assertEquals("3:45", YouTubeMusicRepository.formatIsoDuration("PT3M45S"))
        assertEquals("1:02:05", YouTubeMusicRepository.formatIsoDuration("PT1H2M5S"))
        assertEquals("0:42", YouTubeMusicRepository.formatIsoDuration("PT42S"))

        val sampleJson = """
            {
              "items": [
                {
                  "id": "trackGood111",
                  "snippet": {
                    "title": "Abidjan Beat &amp; Flow",
                    "channelTitle": "Studio Bravo",
                    "thumbnails": {
                      "high": { "url": "https://i.ytimg.com/vi/trackGood111/hqdefault.jpg" }
                    }
                  },
                  "contentDetails": { "duration": "PT3M18S" },
                  "status": { "embeddable": true, "privacyStatus": "public" }
                },
                {
                  "id": "trackBlocked",
                  "snippet": {
                    "title": "Blocked Track",
                    "channelTitle": "Restricted"
                  },
                  "contentDetails": { "duration": "PT4M00S" },
                  "status": { "embeddable": false, "privacyStatus": "public" }
                }
              ]
            }
        """.trimIndent()

        val tracks = YouTubeMusicRepository.parseVideosResponse(sampleJson, requireEmbeddable = true)
        assertEquals(1, tracks.size)
        assertEquals("trackGood111", tracks.first().videoId)
        assertEquals("Abidjan Beat & Flow", tracks.first().title)
        assertEquals("3:18", tracks.first().durationLabel)

        val publicHtml = """
            <html><script>var ytInitialData = {"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[{"videoRenderer":{"videoId":"pubTrack001","title":{"runs":[{"text":"Coupe Decale Mix"}]},"ownerText":{"runs":[{"text":"DJ Abidjan"}]},"lengthText":{"simpleText":"4:12"},"thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/pubTrack001/hqdefault.jpg"}]}}}]}}]}}}}};</script></html>
        """.trimIndent()
        val publicTracks = YouTubeMusicRepository.parsePublicSearchHtml(publicHtml)
        assertEquals(1, publicTracks.size)
        assertEquals("pubTrack001", publicTracks.first().videoId)
        assertEquals("Coupe Decale Mix", publicTracks.first().title)
        assertEquals("DJ Abidjan", publicTracks.first().channelTitle)
        assertEquals("4:12", publicTracks.first().durationLabel)
    }

    @Test
    fun youtubePlayerController_autoAdvancesHideVideoAndSkipsOnError() {
        val controller = YouTubePlayerController()
        val t1 = YouTubeTrack("vidOne11111", "Premier Son", "Artiste A", "https://i.ytimg.com/vi/vidOne11111/hqdefault.jpg")
        val t2 = YouTubeTrack("vidTwo22222", "Deuxième Son", "Artiste B", "https://i.ytimg.com/vi/vidTwo22222/hqdefault.jpg")
        val t3 = YouTubeTrack("vidThree333", "Troisième Son", "Artiste C", "https://i.ytimg.com/vi/vidThree333/hqdefault.jpg")

        controller.playTrack(t1, listOf(t1, t2, t3))
        assertEquals("vidOne11111", controller.videoId)
        assertTrue(controller.isPlaying)
        assertFalse(controller.isVideoHidden)

        // Masquer la vidéo ne coupe pas la lecture
        controller.toggleHideVideo()
        assertTrue(controller.isVideoHidden)
        assertTrue(controller.isPlaying)

        // Fin du premier titre -> enchaînement automatique sur le second
        controller.onPlayerStateChanged(YouTubePlayerController.YT_STATE_ENDED)
        assertEquals("vidTwo22222", controller.videoId)
        assertTrue(controller.isPlaying)
        assertTrue(controller.isVideoHidden)

        // Erreur d'intégration sur le titre 2 -> saut silencieux vers le titre 3 sans coupure
        controller.onPlayerError(150)
        assertEquals("vidThree333", controller.videoId)
        assertTrue(controller.failedVideoIds.contains("vidTwo22222"))
        assertTrue(controller.isPlaying)
    }

    @Test
    fun youtubePlayer_preventsError152AndRecoversOnSameTrack() {
        val origin = com.example.ui.components.resolveAppEmbedOrigin()
        assertTrue(origin.startsWith("https://"))
        assertFalse(origin.contains("youtube.com"))

        val html = com.example.ui.components.buildYouTubeIframePlayerHtml("dQw4w9WgXcQ", origin)
        assertTrue(html.contains("strict-origin-when-cross-origin"))
        assertTrue(html.contains("widget_referrer="))
        assertTrue(html.contains("width=\"100%\""))
        assertTrue(html.contains("__bravAllowPause"))
        assertFalse(html.contains("origin=https%3A%2F%2Fwww.youtube.com"))

        val sanitizedUa = com.example.ui.components.sanitizeWebViewUserAgent(
            "Mozilla/5.0 (Linux; Android 14; Pixel 8; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.0.0 Mobile Safari/537.36"
        )
        assertFalse(sanitizedUa.contains("; wv"))
        assertTrue(sanitizedUa.contains("Chrome/130.0.0.0"))

        val controller = YouTubePlayerController()
        val track1 = YouTubeTrack("vidLabel152", "Clip Label", "Artiste", "https://i.ytimg.com/vi/vidLabel152/hqdefault.jpg")
        val track2 = YouTubeTrack("vidNext0002", "Suivant", "Artiste B", "https://i.ytimg.com/vi/vidNext0002/hqdefault.jpg")
        controller.playTrack(track1, listOf(track1, track2))

        // Error 152 / -4 triggers same-track Stage 2 recovery instead of dropping the track immediately
        controller.onEmbedErrorOrTimeout(152)
        assertEquals("vidLabel152", controller.videoId)
        assertEquals(com.example.ui.components.YouTubePlaybackStage.MOBILE_WATCH_FALLBACK, controller.playbackStage)
        assertTrue(controller.pendingCommand is com.example.ui.components.PlayerJsCommand.LoadMobileWatch)
        assertTrue(controller.isPlaying)

        // If Stage 2 also fails (-4), cleanly advance to track2
        controller.onEmbedErrorOrTimeout(-4)
        assertEquals("vidNext0002", controller.videoId)
        assertEquals(com.example.ui.components.YouTubePlaybackStage.IFRAME_EMBED, controller.playbackStage)
        assertTrue(controller.isPlaying)
    }

    @Test
    fun youtubePlayerController_neverAutoPausesUnlessUserOrActiveCall() {
        val controller = YouTubePlayerController()
        val track = YouTubeTrack("vidKeepAlive1", "Continuous Live Track", "Artiste", "https://i.ytimg.com/vi/vidKeepAlive1/hqdefault.jpg")
        controller.playTrack(track, listOf(track))
        controller.consumeCommand()

        // 1. Spontaneous WebView/IFrame pause while user did NOT tap pause -> auto-resumes!
        controller.onPlayerStateChanged(YouTubePlayerController.YT_STATE_PAUSED)
        assertTrue(controller.isPlaying)
        assertFalse(controller.pausedByUser)
        assertTrue(controller.pendingCommand is com.example.ui.components.PlayerJsCommand.Play)
        controller.consumeCommand()

        // 2. Programmatic pausePlayback(byUser = false) without call -> ignored
        controller.pausePlayback(byUser = false)
        assertTrue(controller.isPlaying)
        assertNull(controller.pendingCommand)

        // 3. Phone call begins -> pauses for active call; ends -> auto-resumes
        controller.setActiveCallPresent(true)
        assertFalse(controller.isPlaying)
        assertTrue(controller.pausedForActiveCall)
        assertTrue(controller.pendingCommand is com.example.ui.components.PlayerJsCommand.Pause)
        controller.consumeCommand()

        controller.setActiveCallPresent(false)
        assertTrue(controller.isPlaying)
        assertFalse(controller.pausedForActiveCall)
        assertTrue(controller.pendingCommand is com.example.ui.components.PlayerJsCommand.Play)
        controller.consumeCommand()

        // 4. Explicit user pause -> stays paused
        controller.pausePlayback(byUser = true)
        assertFalse(controller.isPlaying)
        assertTrue(controller.pausedByUser)
        controller.onPlayerStateChanged(YouTubePlayerController.YT_STATE_PAUSED)
        assertFalse(controller.isPlaying)

        // 5. Fallback preserves current playback position
        controller.playPlayback()
        controller.onProgress(42, 240)
        controller.onEmbedErrorOrTimeout(152)
        assertEquals(com.example.ui.components.YouTubePlaybackStage.MOBILE_WATCH_FALLBACK, controller.playbackStage)
        controller.consumeCommand()
        controller.onPlayerReady()
        val cmd = controller.pendingCommand
        assertTrue(cmd is com.example.ui.components.PlayerJsCommand.SeekTo)
        assertEquals(42, (cmd as com.example.ui.components.PlayerJsCommand.SeekTo).seconds)

        // 6. Fullscreen toggle disables hidden-video mode cleanly
        controller.toggleHideVideo()
        assertTrue(controller.isVideoHidden)
        controller.toggleFullscreen()
        assertTrue(controller.isVideoFullscreen)
        assertFalse(controller.isVideoHidden)
    }

    @Test
    fun deviceCallLogRepository_mapsTypesFormatsDurationsAndHandlesMissingPermission() {
        assertEquals(
            CallDirection.OUTGOING to false,
            com.example.telecom.DeviceCallLogRepository.mapCallType(android.provider.CallLog.Calls.OUTGOING_TYPE)
        )
        assertEquals(
            CallDirection.INCOMING to true,
            com.example.telecom.DeviceCallLogRepository.mapCallType(android.provider.CallLog.Calls.MISSED_TYPE)
        )
        assertEquals(
            CallDirection.INCOMING to false,
            com.example.telecom.DeviceCallLogRepository.mapCallType(android.provider.CallLog.Calls.INCOMING_TYPE)
        )
        assertEquals("45s", com.example.telecom.DeviceCallLogRepository.formatDuration(45L))
        assertEquals("2m 5s", com.example.telecom.DeviceCallLogRepository.formatDuration(125L))
        assertEquals("1h 1m", com.example.telecom.DeviceCallLogRepository.formatDuration(3665L))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val logs = com.example.telecom.DeviceCallLogRepository.loadCallLogs(context)
        assertTrue(logs.isEmpty())
    }
}
