package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.launcher.BravLauncherScreen
import com.example.launcher.LauncherAfricanWallpaper
import com.example.launcher.LauncherThemeStudioScreen
import com.example.local.AppPreferencesRepository
import com.example.model.Conversation
import com.example.model.StatusPost
import com.example.model.User
import com.example.model.VerificationCategory
import com.example.model.VerificationStatus
import com.example.ui.components.BravPatternIntensity
import com.example.ui.components.BravPatternType
import com.example.ui.components.ProfileCoverArtPreset
import com.example.ui.screens.ActusFeedContent
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.ProfileEditorialHeroCard
import com.example.ui.theme.BravChampagneGold
import com.example.ui.theme.BravChatTheme
import com.example.ui.theme.BravIvory
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRedSecondary
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravVerifiedBlue
import com.example.ui.theme.BravWhite
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AfricanLuxuryDesignTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun brandTokensAndPatternPresets_matchAfricanLuxurySpecification() {
        assertEquals(Color(0xFF570CA2), BravPurplePrimary)
        assertEquals(Color(0xFFEA0D1B), BravRedSecondary)
        assertEquals(Color(0xFFFAF8F3), BravIvory)
        assertEquals(Color(0xFFFFFFFF), BravWhite)
        assertEquals(Color(0xFFC6A66B), BravChampagneGold)
        assertEquals(Color(0xFF1D9BF0), BravVerifiedBlue)

        assertEquals(4, BravPatternType.entries.size)
        assertEquals(4, ProfileCoverArtPreset.entries.size)
        assertTrue(BravPatternIntensity.SUBTLE_WATERMARK in 0.08f..0.18f)
        assertTrue(BravPatternIntensity.LIGHT_DECOR in 0.08f..0.18f)
        assertTrue(BravPatternIntensity.HEADER_TONE in 0.08f..0.18f)
        assertEquals(ProfileCoverArtPreset.ROYAL_HERITAGE, ProfileCoverArtPreset.fromId("unknown"))
        assertEquals(ProfileCoverArtPreset.SOLAR_IVORY, ProfileCoverArtPreset.fromId("solar_ivory"))

        assertEquals(5, LauncherAfricanWallpaper.entries.size)
        assertEquals(LauncherAfricanWallpaper.KENTE_ROYAL, LauncherAfricanWallpaper.fromId("unknown"))
        assertEquals(LauncherAfricanWallpaper.SOLAR_ADINKRA_GOLD, LauncherAfricanWallpaper.fromId("solar_adinkra"))
        assertNull(LauncherAfricanWallpaper.NONE.pattern)
        assertNotNull(LauncherAfricanWallpaper.KENTE_ROYAL.pattern)

        var lightCardBg = Color.Transparent
        var lightCanvasBg = Color.Transparent
        var darkCardBg = Color.Transparent
        var darkCanvasBg = Color.Transparent

        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                lightCardBg = BravTheme.colors.cardSurface
                lightCanvasBg = BravTheme.colors.ivoryCanvas
                assertFalse(BravTheme.colors.isDark)
                assertEquals(BravIvory, MaterialTheme.colorScheme.background)
            }
            BravChatTheme(darkTheme = true) {
                darkCardBg = BravTheme.colors.cardSurface
                darkCanvasBg = BravTheme.colors.ivoryCanvas
                assertTrue(BravTheme.colors.isDark)
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(Color(0xB8FAF8F3), lightCardBg)
        assertTrue(lightCardBg.alpha < 1f)
        assertEquals(BravIvory, lightCanvasBg)
        assertEquals(Color(0xBF191423), darkCardBg)
        assertTrue(darkCardBg.alpha < 1f)
        assertEquals(Color(0xFF100D16), darkCanvasBg)
    }

    @Test
    fun homeDashboard_rendersLuxuryHeaderFiltersAndWideAdaptiveContainer() {
        val conv = Conversation(
            id = "c_lux_1",
            participants = listOf("me", "u_amina"),
            participantNames = mapOf("u_amina" to "Amina Diallo"),
            lastMessage = "Très bien, merci !"
        )

        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                Box(modifier = ModifierWidth(840.dp)) {
                    HomeDashboardScreen(
                        currentUserId = "me",
                        conversations = listOf(conv),
                        prefsMap = emptyMap(),
                        presenceMap = emptyMap(),
                        typingConversations = emptyMap(),
                        savedContacts = emptyList(),
                        hasPinConfigured = false,
                        isAdmin = false,
                        verifiedUserIds = setOf("u_amina"),
                        onVerifyPin = { true },
                        onOpenConversation = {},
                        onCreateGroup = { _, _, _ -> },
                        onJoinGroupByCode = {},
                        onUpdatePref = { _, _, _, _, _, _, _ -> },
                        onDeleteConversation = {},
                        onNavigateToContacts = {},
                        onOpenStatuses = {},
                        onOpenDialer = {},
                        onRequestDefaultDialer = {},
                        onOpenMap = {},
                        onOpenOffers = {},
                        onOpenProfile = {},
                        onOpenSettings = {},
                        onOpenAdmin = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("BRAV Chat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discussions").assertIsDisplayed()
        composeTestRule.onNodeWithText("Amina Diallo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_filter_all").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_filter_groups").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_search_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_search_input").assertIsDisplayed()
    }

    @Test
    fun actusFeedAndProfileHero_renderLuxuryCardsCoverPresetsAndOfficialBlueBadge() {
        val nowMs = System.currentTimeMillis()
        val verifiedPost = StatusPost(
            id = "post_lux_1",
            userId = "u_amina",
            userName = "BRAV Afrique",
            authorUsername = "brav.afrique",
            authorHeadline = "Dakar, Sénégal",
            type = "text",
            text = "Une Afrique connectée, innovante et solidaire.",
            likedByUids = listOf("u1", "u2"),
            commentCount = 3L,
            expiresAtMs = nowMs + 3650L * 24L * 3600L * 1000L,
            createdAt = Timestamp(nowMs / 1000L, 0)
        )
        val verifiedAuthor = User(
            userId = "u_amina",
            displayName = "BRAV Afrique",
            username = "brav.afrique",
            verificationStatus = VerificationStatus.VERIFIED.raw,
            verificationCategory = VerificationCategory.CREATOR.raw,
            verifiedOrgName = "BRAV Afrique"
        )

        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                ActusFeedContent(
                    myUid = "me",
                    currentUser = User(userId = "me", displayName = "Moi"),
                    posts = listOf(verifiedPost),
                    knownUsers = mapOf("u_amina" to verifiedAuthor),
                    onOpenComposer = {},
                    onOpenStoryGroup = { _, _ -> },
                    onToggleLike = {},
                    onOpenComments = {},
                    onToggleReaction = { _, _ -> },
                    onDeletePost = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("actus_feed_list").performScrollToNode(hasTestTag("status_card_post_lux_1"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("status_card_post_lux_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("BRAV Afrique").assertIsDisplayed()
        composeTestRule.onNodeWithTag("verified_blue_badge", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun profileEditorialHeroCard_rendersCoverPresetStatsAndVerifiedBadge() {
        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                ProfileEditorialHeroCard(
                    displayName = "Amina Diallo",
                    username = "amina.d",
                    avatarUrl = "",
                    coverUrl = "",
                    availabilityStatus = "online",
                    locationSummary = "Dakar, Sénégal",
                    bio = "Créatrice de contenu • Africa Inspire",
                    isVerified = true,
                    verifiedOrgName = "Africa Inspire",
                    verificationCategory = "Créateur de contenu",
                    isUploadingAvatar = false,
                    isUploadingCover = false,
                    onPickAvatar = {},
                    onRemoveAvatar = {},
                    onPickCover = {},
                    onRemoveCover = {},
                    coverPreset = ProfileCoverArtPreset.SOLAR_IVORY,
                    postsCount = 42,
                    likesCount = 1280,
                    contactsCount = 19
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("profile_editorial_hero").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_cover_container").assertIsDisplayed()
        composeTestRule.onNodeWithText("Amina Diallo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("verified_blue_badge", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun launcherAndThemeStudio_selectAfricanWallpaperPresetAndPersist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferencesRepository(context)

        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                LauncherThemeStudioScreen(
                    prefsRepository = prefs,
                    installedApps = emptyList(),
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("launcher_studio_list")
            .performScrollToNode(hasTestTag("studio_african_wallpaper_row"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_african_wallpaper_row")
            .performScrollToNode(hasTestTag("studio_african_wallpaper_solar_adinkra"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_african_wallpaper_solar_adinkra")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            runBlocking { prefs.state.first().launcherAfricanWallpaperId == "solar_adinkra" }
        }
        val savedId = runBlocking { prefs.state.first().launcherAfricanWallpaperId }
        assertEquals("solar_adinkra", savedId)
    }

    @Test
    fun bravLauncherHome_rendersCleanlyWithAfricanPatternWallpaper() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferencesRepository(context)

        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                BravLauncherScreen(prefsRepository = prefs)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("brav_launcher_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("launcher_open_theme_studio").assertIsDisplayed()
    }
}

private fun ModifierWidth(width: androidx.compose.ui.unit.Dp) =
    androidx.compose.ui.Modifier.width(width)
