package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.example.local.AppPreferencesState
import com.example.media.VoicePlaybackState
import com.example.model.Message
import com.example.model.StatusPost
import com.example.model.User
import com.example.ui.components.BravEmojiCategory
import com.example.ui.components.BravStickerCatalog
import com.example.ui.components.BravStickerPickerSheet
import com.example.ui.components.FullscreenProfileImageViewer
import com.example.ui.components.LuxuryEmojiPickerSheet
import com.example.ui.components.UserPublicationsSection
import com.example.ui.screens.ActusFeedContent
import com.example.ui.screens.ChatMessageBubble
import com.example.ui.screens.FriendProfileModal
import com.example.ui.screens.ProfileEditorialHeroCard
import com.example.ui.screens.SettingsHierarchicalContent
import com.example.ui.theme.BravChatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileSettingsActusMessagingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun profileHero_usesLongPressMenusAndFullscreenViewerWithoutPermanentDeleteButtons() {
        var avatarViewerOpened = false
        var avatarRemoveClicked = false
        var showViewer by mutableStateOf(false)

        composeTestRule.setContent {
            BravChatTheme {
                ProfileEditorialHeroCard(
                    displayName = "Mariam Touré",
                    username = "mariam.t",
                    avatarUrl = "https://example.com/avatar.jpg",
                    coverUrl = "https://example.com/cover.jpg",
                    availabilityStatus = "online",
                    locationSummary = "Abidjan • Cocody",
                    bio = "Design & Culture",
                    isVerified = true,
                    isUploadingAvatar = false,
                    isUploadingCover = false,
                    isOwner = true,
                    onPickAvatar = {},
                    onRemoveAvatar = { avatarRemoveClicked = true },
                    onPickCover = {},
                    onRemoveCover = {},
                    onOpenAvatarViewer = {
                        avatarViewerOpened = true
                        showViewer = true
                    }
                )
                if (showViewer) {
                    FullscreenProfileImageViewer(
                        imageUrl = "https://example.com/avatar.jpg",
                        title = "Mariam Touré",
                        onDismiss = { showViewer = false }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // No permanent deletion buttons or verification banner cluttering the profile hero
        composeTestRule.onNodeWithTag("remove_cover_photo_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("remove_photo_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("profile_verification_card").assertDoesNotExist()

        // Long-press avatar opens context menu with removal action
        composeTestRule.onNodeWithTag("profile_avatar_container").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_avatar_context_menu").assertIsDisplayed()
        composeTestRule.onNodeWithTag("avatar_menu_remove_item").performClick()
        composeTestRule.waitForIdle()
        assertTrue(avatarRemoveClicked)

        // Tap avatar opens fullscreen viewer with zoom controls
        composeTestRule.onNodeWithTag("profile_avatar_container").performClick()
        composeTestRule.waitForIdle()
        assertTrue(avatarViewerOpened)
        composeTestRule.onNodeWithTag("fullscreen_profile_image_viewer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fullscreen_viewer_zoom_slider").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fullscreen_viewer_reset_zoom_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fullscreen_viewer_close_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fullscreen_profile_image_viewer").assertDoesNotExist()
    }

    @Test
    fun friendProfileModal_enforcesPrivacySettingsAndSelectedAudiencePosts() {
        val friend = User(
            userId = "u_friend",
            displayName = "Seydou Bamba",
            username = "seydou.b",
            bio = "Entrepreneur Abidjan",
            locationCity = "Abidjan",
            locationDetails = "Côte d'Ivoire",
            publicBirthDate = "14/05/1994",
            publicAddress = "Rue des Jardins, Cocody",
            publicPhone = "+225 07 11 22 33 44",
            publicEmail = "seydou@example.com",
            privacySettings = mapOf(
                "showCity" to "everyone",
                "showBirthDate" to "nobody",
                "showAddress" to "nobody",
                "showPhone" to "nobody",
                "showEmail" to "nobody",
                "showPosts" to "everyone"
            )
        )
        val publicPost = StatusPost(
            id = "post_pub",
            userId = "u_friend",
            userName = "Seydou Bamba",
            text = "Rencontre tech au Plateau",
            audienceType = "all"
        )
        val hiddenSelectedPost = StatusPost(
            id = "post_priv",
            userId = "u_friend",
            userName = "Seydou Bamba",
            text = "Note confidentielle VIP",
            audienceType = "selected",
            allowedUids = listOf("someone_else")
        )

        composeTestRule.setContent {
            BravChatTheme {
                FriendProfileModal(
                    user = friend,
                    userPosts = listOf(publicPost, hiddenSelectedPost),
                    myUid = "me_uid",
                    isContact = true,
                    isOnline = true,
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("friend_profile_modal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("friend_profile_city").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Abidjan • Côte d'Ivoire").assertExists()
        composeTestRule.onNodeWithTag("publication_grid_item_post_pub").performScrollTo().assertIsDisplayed()

        // Hidden personal fields & restricted audience posts must not be exposed
        composeTestRule.onNodeWithText("14/05/1994").assertDoesNotExist()
        composeTestRule.onNodeWithText("Rue des Jardins, Cocody").assertDoesNotExist()
        composeTestRule.onNodeWithText("+225 07 11 22 33 44").assertDoesNotExist()
        composeTestRule.onNodeWithText("seydou@example.com").assertDoesNotExist()
        composeTestRule.onNodeWithTag("publication_grid_item_post_priv").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pub_edit_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pub_delete_button").assertDoesNotExist()
    }

    @Test
    fun userPublicationsSection_togglesGridListAndEditsDeletesOwnerPost() {
        var editedText = ""
        var deletedId = ""
        val post = StatusPost(
            id = "p_owner_1",
            userId = "me",
            userName = "Fatou Koné",
            text = "Première version du texte",
            type = "text"
        )

        composeTestRule.setContent {
            BravChatTheme {
                UserPublicationsSection(
                    posts = listOf(post),
                    isOwner = true,
                    viewerUid = "me",
                    canViewPosts = true,
                    onEditPost = { _, newText -> editedText = newText },
                    onDeletePost = { deletedId = it.id }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("publications_grid_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("publications_view_list_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("publications_list_container").assertIsDisplayed()

        // Open detail dialog -> Edit -> Save
        composeTestRule.onNodeWithTag("publication_list_item_p_owner_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("publication_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pub_edit_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pub_edit_text_input").performTextReplacement("Texte mis à jour")
        composeTestRule.onNodeWithTag("pub_edit_save_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Texte mis à jour", editedText)

        // Open detail dialog -> Delete -> Confirm
        composeTestRule.onNodeWithTag("publication_list_item_p_owner_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pub_delete_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pub_delete_confirm_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("p_owner_1", deletedId)
    }

    @Test
    fun settingsFivePillars_displaysAccountPrivacyAndCertificationNavigation() {
        var openVerificationClicks = 0
        composeTestRule.setContent {
            BravChatTheme {
                SettingsHierarchicalContent(
                    currentUser = User(userId = "me", displayName = "Jean Kouamé"),
                    firebaseEmail = "jean@bravchat.com",
                    appPrefs = AppPreferencesState(),
                    blockedCount = 0,
                    isAdmin = false,
                    cacheBytes = 1024L,
                    onOpenVerification = { openVerificationClicks++ },
                    onBack = {},
                    onOpenProfile = {},
                    onSignOut = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Account pillar shows Personal Info + Field Privacy toggles
        composeTestRule.onNodeWithTag("settings_cat_account").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_personal_name_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_detail_list").performScrollToNode(hasTestTag("privacy_toggle_showPhoto"))
        composeTestRule.onNodeWithTag("privacy_toggle_showPhoto").assertIsDisplayed()
        composeTestRule.onNodeWithTag("privacy_toggle_showPhone").assertExists()
        composeTestRule.onNodeWithTag("privacy_toggle_showEmail").assertExists()
        composeTestRule.onNodeWithTag("privacy_toggle_showPosts").assertExists()

        // Back -> Certification pillar
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_cat_certification").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_certification_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_detail_list").performScrollToNode(hasTestTag("settings_open_verification_button"))
        composeTestRule.onNodeWithTag("settings_open_verification_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, openVerificationClicks)
    }

    @Test
    fun actusFeed_rendersDirectMessagesShortcutWithUnreadBadge() {
        var discussionsOpened = false
        composeTestRule.setContent {
            BravChatTheme {
                ActusFeedContent(
                    myUid = "me",
                    currentUser = User(userId = "me", displayName = "Awa"),
                    posts = emptyList(),
                    unreadMessagesCount = 7,
                    onOpenDiscussions = { discussionsOpened = true },
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

        composeTestRule.onNodeWithTag("actus_messages_shortcut_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("7").assertIsDisplayed()
        composeTestRule.onNodeWithTag("actus_messages_shortcut_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(discussionsOpened)
    }

    @Test
    fun emojiPicker_supports10CategoriesSearchAndSkinTones() {
        assertEquals(10, BravEmojiCategory.entries.size)
        var pickedEmoji = ""

        composeTestRule.setContent {
            BravChatTheme {
                LuxuryEmojiPickerSheet(
                    onInsertEmoji = { pickedEmoji = it },
                    recentEmojis = listOf("👑"),
                    favoriteEmojis = listOf("💜"),
                    preferredSkinTone = "🏽",
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("emoji_skin_tone_🏽").assertIsDisplayed()
        composeTestRule.onNodeWithTag("emoji_search_input").performTextInput("couronne")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("luxury_emoji_item_👑").performClick()
        composeTestRule.waitForIdle()
        assertEquals("👑", pickedEmoji)
    }

    @Test
    fun stickerPicker_previewsAndSendsSticker() {
        var sentStickerId = ""
        val allPacks = BravStickerCatalog.packs.map { it.id }.toSet()

        composeTestRule.setContent {
            BravChatTheme {
                BravStickerPickerSheet(
                    recentStickerIds = emptyList(),
                    favoriteStickerIds = emptyList(),
                    enabledPackIds = allPacks,
                    onSendSticker = { sentStickerId = it.id },
                    onToggleFavoriteSticker = {},
                    onTogglePackEnabled = { _, _ -> },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sticker_item_st_akwaba").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sticker_preview_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sticker_send_confirm_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("st_akwaba", sentStickerId)
    }

    @Test
    fun chatMessageBubble_rendersStandaloneStickerWithoutBubbleBox() {
        composeTestRule.setContent {
            BravChatTheme {
                ChatMessageBubble(
                    message = Message(
                        id = "st_msg_1",
                        conversationId = "c1",
                        senderId = "u2",
                        messageType = "sticker",
                        stickerId = "st_on_est_ensemble",
                        stickerPackId = "civ_culture",
                        stickerLabel = "ON EST ENSEMBLE"
                    ),
                    displayText = "ON EST ENSEMBLE",
                    translatedText = null,
                    isMine = false,
                    myUid = "me",
                    senderDisplayName = "Awa",
                    isGroup = false,
                    playbackState = VoicePlaybackState(),
                    onLongPress = {},
                    onToggleReaction = {},
                    onOpenMedia = { _, _, _ -> },
                    onToggleVoicePlay = {},
                    onCycleVoiceSpeed = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chat_sticker_bubble_st_msg_1").assertIsDisplayed()
    }
}
