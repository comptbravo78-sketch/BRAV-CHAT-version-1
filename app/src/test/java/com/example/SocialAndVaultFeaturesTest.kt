package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
import androidx.compose.ui.test.swipeUp
import com.example.ai.ContentModerationGuard
import com.example.local.AppPreferencesState
import com.example.media.VoicePlaybackState
import com.example.model.BravReactionType
import com.example.model.Conversation
import com.example.model.ConversationPref
import com.example.model.Message
import com.example.model.OfferListing
import com.example.model.StatusComment
import com.example.model.StatusPost
import com.example.model.User
import com.example.repository.SmsThreadSummary
import com.example.ui.components.LuxuryEmojiPickerSheet
import com.example.ui.components.VoiceMessageBubble
import com.example.ui.screens.ActusCommentsSheet
import com.example.ui.screens.ActusComposeDraft
import com.example.ui.screens.ActusComposerSheet
import com.example.ui.screens.ActusDraftMediaItem
import com.example.ui.screens.ActusFeedContent
import com.example.ui.screens.ChatMessageBubble
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.OfferComposeSubmission
import com.example.ui.screens.OfferComposerDialog
import com.example.ui.screens.OfferDraftImage
import com.example.ui.screens.OffersMarketplaceContent
import com.example.ui.screens.PendingCropMedia
import com.example.ui.screens.ProfileEditorialHeroCard
import com.example.ui.screens.ProfileImageCropDialog
import com.example.ui.screens.ProfilePhotoTarget
import com.example.ui.screens.SettingsHierarchicalContent
import com.example.ui.screens.filterSmsThreadsForVault
import com.example.ui.screens.organizeCommentThreads
import com.example.ui.theme.BravChatTheme
import com.example.util.ImageCompressor
import com.google.firebase.Timestamp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SocialAndVaultFeaturesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeDashboard_hidesLockedConversationsEverywhereUntilUnlocked() {
        val visibleConv = Conversation(
            id = "c_public",
            participants = listOf("me", "u1"),
            participantNames = mapOf("u1" to "Awa Kouassi"),
            lastMessage = "Bonjour !"
        )
        val unreadConv = Conversation(
            id = "c_unread",
            participants = listOf("me", "u2"),
            participantNames = mapOf("u2" to "Paul Yao"),
            lastMessage = "À tout à l'heure"
        )
        val archivedConv = Conversation(
            id = "c_archived",
            participants = listOf("me", "u3"),
            participantNames = mapOf("u3" to "Moussa Koné"),
            lastMessage = "Dossier archivé"
        )
        val lockedSecretConv = Conversation(
            id = "c_secret",
            isGroup = true,
            groupName = "Coffre Privé",
            participants = listOf("me", "u4"),
            lastMessage = "Code confidentiel"
        )

        val prefs = mapOf(
            "c_unread" to ConversationPref(conversationId = "c_unread", markedUnread = true),
            "c_archived" to ConversationPref(conversationId = "c_archived", archived = true),
            "c_secret" to ConversationPref(
                conversationId = "c_secret",
                locked = true,
                markedUnread = true,
                archived = true
            )
        )

        composeTestRule.setContent {
            BravChatTheme {
                HomeDashboardScreen(
                    currentUserId = "me",
                    conversations = listOf(visibleConv, unreadConv, archivedConv, lockedSecretConv),
                    prefsMap = prefs,
                    presenceMap = emptyMap(),
                    typingConversations = emptyMap(),
                    savedContacts = emptyList(),
                    hasPinConfigured = true,
                    biometricLockEnabled = false,
                    isAdmin = false,
                    onVerifyPin = { pin -> pin == "2580" },
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
        composeTestRule.waitForIdle()

        // Locked conversation must NEVER appear in Toutes, Non lues, Groupes, Archivées, or search
        composeTestRule.onNodeWithText("Awa Kouassi").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coffre Privé").assertDoesNotExist()

        composeTestRule.onNodeWithTag("home_filter_unread").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Paul Yao").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coffre Privé").assertDoesNotExist()

        composeTestRule.onNodeWithTag("home_filter_groups").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Coffre Privé").assertDoesNotExist()

        composeTestRule.onNodeWithTag("home_filter_row").performScrollToNode(hasTestTag("home_filter_archived"))
        composeTestRule.onNodeWithTag("home_filter_archived").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Moussa Koné").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coffre Privé").assertDoesNotExist()

        // Search must also NOT leak locked conversation
        composeTestRule.onNodeWithTag("home_filter_row").performScrollToNode(hasTestTag("home_filter_all"))
        composeTestRule.onNodeWithTag("home_filter_all").performClick()
        composeTestRule.onNodeWithTag("home_search_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_search_input").performTextInput("Coffre")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Coffre Privé").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_search_button").performClick()

        // Tapping Verrouillées opens ChatLockPromptDialog
        composeTestRule.onNodeWithTag("home_filter_row").performScrollToNode(hasTestTag("home_filter_locked"))
        composeTestRule.onNodeWithTag("home_filter_locked").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_lock_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coffre Privé").assertDoesNotExist()

        // Wrong PIN keeps vault locked
        composeTestRule.onNodeWithTag("chat_lock_pin_input").performTextInput("0000")
        composeTestRule.onNodeWithTag("chat_lock_validate_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Code PIN incorrect").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coffre Privé").assertDoesNotExist()

        // Re-open dialog cleanly to enter exact PIN
        composeTestRule.onNodeWithText("Annuler").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_filter_row").performScrollToNode(hasTestTag("home_filter_locked"))
        composeTestRule.onNodeWithTag("home_filter_locked").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_lock_pin_input").performTextInput("2580")
        composeTestRule.onNodeWithTag("chat_lock_validate_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Coffre Privé").assertIsDisplayed()
        composeTestRule.onNodeWithText("Awa Kouassi").assertDoesNotExist()

        // Switching back to Toutes immediately re-locks vault
        composeTestRule.onNodeWithTag("home_filter_row").performScrollToNode(hasTestTag("home_filter_all"))
        composeTestRule.onNodeWithTag("home_filter_all").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Coffre Privé").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_filter_row").performScrollToNode(hasTestTag("home_filter_locked"))
        composeTestRule.onNodeWithTag("home_filter_locked").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_lock_dialog").assertIsDisplayed()
    }

    @Test
    fun smsVaultFiltering_hidesLockedThreadsFromInboxArchivesAndSearchUntilUnlocked() {
        val tNormal = SmsThreadSummary(
            threadKey = "sms_1",
            threadId = 1L,
            address = "+2250701020304",
            normalizedAddress = "+2250701020304",
            displayName = "Contact Normal",
            photoUri = "",
            localityHint = "Abidjan",
            lastBody = "Salut !",
            timestampMs = 2000L,
            unreadCount = 0,
            messages = emptyList()
        )
        val tArchived = SmsThreadSummary(
            threadKey = "sms_2",
            threadId = 2L,
            address = "+2250501020304",
            normalizedAddress = "+2250501020304",
            displayName = "Fil Archivé",
            photoUri = "",
            localityHint = "",
            lastBody = "Facture",
            timestampMs = 1500L,
            unreadCount = 0,
            messages = emptyList()
        )
        val tLocked = SmsThreadSummary(
            threadKey = "sms_secret",
            threadId = 3L,
            address = "+2250101020304",
            normalizedAddress = "+2250101020304",
            displayName = "SMS Secret",
            photoUri = "",
            localityHint = "",
            lastBody = "Code bancaire 9999",
            timestampMs = 3000L,
            unreadCount = 1,
            messages = emptyList()
        )

        val allThreads = listOf(tNormal, tArchived, tLocked)
        val archived = setOf("sms_2", "sms_secret")
        val locked = setOf("sms_secret")

        // Normal inbox hides both archived and locked
        val inbox = filterSmsThreadsForVault(
            threads = allThreads,
            archivedKeys = archived,
            lockedKeys = locked,
            pinnedKeys = emptySet(),
            showArchived = false,
            showLocked = false,
            lockedVaultUnlocked = false,
            searchQuery = ""
        )
        assertEquals(listOf("sms_1"), inbox.map { it.threadKey })

        // Archives hides locked even if locked thread was archived
        val archives = filterSmsThreadsForVault(
            threads = allThreads,
            archivedKeys = archived,
            lockedKeys = locked,
            pinnedKeys = emptySet(),
            showArchived = true,
            showLocked = false,
            lockedVaultUnlocked = false,
            searchQuery = "Secret"
        )
        assertTrue(archives.isEmpty())

        // Unverified vault shows nothing
        val lockedNotAuthed = filterSmsThreadsForVault(
            threads = allThreads,
            archivedKeys = archived,
            lockedKeys = locked,
            pinnedKeys = emptySet(),
            showArchived = false,
            showLocked = true,
            lockedVaultUnlocked = false,
            searchQuery = ""
        )
        assertTrue(lockedNotAuthed.isEmpty())

        // Unlocked vault shows ONLY locked SMS threads
        val unlockedVault = filterSmsThreadsForVault(
            threads = allThreads,
            archivedKeys = archived,
            lockedKeys = locked,
            pinnedKeys = emptySet(),
            showArchived = false,
            showLocked = true,
            lockedVaultUnlocked = true,
            searchQuery = ""
        )
        assertEquals(listOf("sms_secret"), unlockedVault.map { it.threadKey })
    }

    @Test
    fun contentModerationGuard_strictlyBlocksToxicTextAndBareSkinBitmaps() {
        // Injurious / abusive / obfuscated expressions are blocked
        assertFalse(ContentModerationGuard.checkText("Tu es un connard !").allowed)
        assertFalse(ContentModerationGuard.checkText("f.u.c.k you").allowed)
        assertFalse(ContentModerationGuard.checkText("Vraiment fils de pute").allowed)
        assertFalse(ContentModerationGuard.checkText("Envoie une photo nue stp").allowed)
        assertEquals(
            ContentModerationGuard.TEXT_BLOCKED_MESSAGE,
            ContentModerationGuard.checkText("P.u.t.a.i.n de merde").userMessage
        )

        // Respectful social text is allowed
        assertTrue(ContentModerationGuard.checkText("Excellente journée à toute l'équipe à Abidjan !").allowed)

        // Synthetic bare-skin nude/half-nude bitmap is blocked
        val bareSkinBitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        bareSkinBitmap.eraseColor(AndroidColor.rgb(218, 165, 134))
        val bareVerdict = ContentModerationGuard.analyzeBitmapSafety(bareSkinBitmap)
        assertFalse(bareVerdict.allowed)
        assertEquals("NUDITY_OR_HALF_NUDITY", bareVerdict.reasonCode)
        bareSkinBitmap.recycle()

        // Synthetic clothed portrait (navy background + dark suit torso + face oval at top center) is allowed
        val clothedPortrait = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(clothedPortrait)
        canvas.drawColor(AndroidColor.rgb(22, 34, 62)) // Dark background
        val suitPaint = Paint().apply { color = AndroidColor.rgb(24, 28, 40) }
        canvas.drawRect(25f, 52f, 95f, 120f, suitPaint) // Dark suit torso
        val facePaint = Paint().apply { color = AndroidColor.rgb(214, 160, 128) }
        canvas.drawCircle(60f, 30f, 16f, facePaint) // Portrait head/face only
        val clothedVerdict = ContentModerationGuard.analyzeBitmapSafety(clothedPortrait)
        assertTrue(clothedVerdict.allowed)
        clothedPortrait.recycle()
    }

    @Test
    fun actusComposerSheet_blocksInjuriousPublicationBeforePublish() {
        var publishCalled = false
        var lastPublishedDraft: ActusComposeDraft? = null

        composeTestRule.setContent {
            BravChatTheme {
                ActusComposerSheet(
                    savedContacts = emptyList(),
                    onPublish = { draft ->
                        publishCalled = true
                        lastPublishedDraft = draft
                        Result.success("post_ok")
                    },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Try publishing abusive phrase -> imperative refusal banner & onPublish NOT invoked
        composeTestRule.onNodeWithTag("actus_composer_text_input").performTextInput("Tu es un gros connard")
        composeTestRule.onNodeWithTag("actus_composer_publish_button").performClick()
        composeTestRule.waitForIdle()

        assertFalse(publishCalled)
        composeTestRule.onNodeWithTag("actus_moderation_error_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Publication bloquée", substring = true).assertIsDisplayed()

        // Replacing with valid respectful text clears the moderation block and publishes cleanly
        composeTestRule.onNodeWithTag("actus_composer_text_input").performTextReplacement("Très belle soirée 👑")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("actus_composer_publish_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(publishCalled)
        assertEquals("Très belle soirée 👑", lastPublishedDraft?.text)
        assertFalse(lastPublishedDraft?.isEphemeral24h ?: true)
    }

    @Test
    fun actusFeedContent_rendersEditorialFeedWithLikesCommentsShareAuthorAndMatureReactions() {
        val nowMs = System.currentTimeMillis()
        val nowSec = nowMs / 1000L
        val feedPost = StatusPost(
            id = "post_feed_1",
            userId = "u_alice",
            userName = "Alice Konan",
            authorUsername = "alice.k",
            authorHeadline = "Abidjan",
            type = "text",
            text = "Nouveau projet lancé aujourd'hui avec @marc.b sur BRAV Chat !",
            likedByUids = listOf("u_bob"),
            commentCount = 2L,
            expiresAtMs = nowMs + 3650L * 24L * 3600L * 1000L,
            createdAt = Timestamp(nowSec, 0)
        )
        val storyPost = StatusPost(
            id = "story_24h_1",
            userId = "me",
            userName = "Moi",
            type = "text",
            text = "En route vers Plateau",
            expiresAtMs = nowMs + 24L * 3600L * 1000L,
            createdAt = Timestamp(nowSec, 0)
        )
        assertTrue(feedPost.isSocialFeedPost(nowMs))
        assertFalse(feedPost.isEphemeralStory(nowMs))
        assertTrue(storyPost.isEphemeralStory(nowMs))

        var likedPostId = ""
        var commentedPostId = ""
        var sharedPostId = ""
        var clickedAuthorUid = ""
        var reactedKey = ""

        composeTestRule.setContent {
            BravChatTheme {
                ActusFeedContent(
                    myUid = "me",
                    currentUser = User(userId = "me", displayName = "Brav User"),
                    posts = listOf(feedPost, storyPost),
                    onOpenComposer = {},
                    onOpenStoryGroup = { _, _ -> },
                    onOpenAuthorProfile = { uid, _ -> clickedAuthorUid = uid },
                    onToggleLike = { likedPostId = it.id },
                    onOpenComments = { commentedPostId = it.id },
                    onToggleReaction = { _, rx -> reactedKey = rx.key },
                    onSharePost = { sharedPostId = it.id },
                    onDeletePost = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("actus_feed_list").performScrollToNode(hasTestTag("status_card_post_feed_1"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("status_card_post_feed_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice Konan").assertIsDisplayed()

        // Author header click triggers Author Profile sheet callback
        composeTestRule.onNodeWithTag("actus_author_header_post_feed_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals("u_alice", clickedAuthorUid)

        // Like button works
        composeTestRule.onNodeWithTag("like_status_post_feed_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals("post_feed_1", likedPostId)

        // Comment button works
        composeTestRule.onNodeWithTag("comment_status_post_feed_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals("post_feed_1", commentedPostId)

        // Share button works
        composeTestRule.onNodeWithTag("share_status_post_feed_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals("post_feed_1", sharedPostId)

        // Mature reaction bar opens & selects Approuver reaction
        composeTestRule.onNodeWithTag("emoji_dock_toggle_post_feed_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("actus_feed_list").performTouchInput { swipeUp(startY = bottom - 20f, endY = centerY) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("actus_reaction_thumbs_up_gold_post_feed_1", useUnmergedTree = true).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(BravReactionType.THUMBS_UP_GOLD.key, reactedKey)

        // Filter to Statuts 24h hides permanent social feed post and keeps story
        composeTestRule.onNodeWithTag("actus_tab_stories").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("actus_feed_list").performScrollToNode(hasTestTag("status_card_story_24h_1"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("status_card_story_24h_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("status_card_post_feed_1").assertDoesNotExist()
    }

    @Test
    fun actusMultiPhotoGallery_and_StatusPostHelpers_renderFourPlusGridAndExtractMentions() {
        val nowMs = System.currentTimeMillis()
        val multiPost = StatusPost(
            id = "post_multi_1",
            userId = "u_marc",
            userName = "Marc Bamba",
            type = "image",
            caption = "Architecture à #Abidjan avec @alice.k et @paul_y",
            mediaUrl = "file:///dev/null/1.jpg",
            mediaUrls = listOf(
                "file:///dev/null/1.jpg",
                "file:///dev/null/2.jpg",
                "file:///dev/null/3.jpg",
                "file:///dev/null/4.jpg",
                "file:///dev/null/5.jpg"
            ),
            expiresAtMs = nowMs + 3650L * 24L * 3600L * 1000L,
            createdAt = Timestamp(nowMs / 1000L, 0)
        )
        assertEquals(5, multiPost.resolvedMediaUrls().size)
        assertEquals(listOf("alice.k", "paul_y"), StatusPost.extractMentions(multiPost.effectiveText()))

        var openedIndex = -1
        composeTestRule.setContent {
            BravChatTheme {
                ActusFeedContent(
                    myUid = "me",
                    currentUser = User(userId = "me", displayName = "Brav User"),
                    posts = listOf(multiPost),
                    onOpenComposer = {},
                    onOpenStoryGroup = { _, _ -> },
                    onToggleLike = {},
                    onOpenComments = {},
                    onToggleReaction = { _, _ -> },
                    onOpenMediaGallery = { _, idx -> openedIndex = idx },
                    onDeletePost = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("actus_feed_list").performScrollToNode(hasTestTag("actus_gallery_post_multi_1"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("actus_gallery_post_multi_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("+1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("actus_gallery_item_post_multi_1_1", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, openedIndex)
    }

    @Test
    fun actusComposerSheet_supportsMultiPhotoReorderingMentionsAndLivePreview() {
        val sampleBytesA = byteArrayOf(1, 2, 3, 4)
        val sampleBytesB = byteArrayOf(5, 6, 7, 8)
        val contacts = listOf(
            User(userId = "u_awa", displayName = "Awa Diallo", username = "awa.d")
        )
        var publishedDraft: ActusComposeDraft? = null

        composeTestRule.setContent {
            BravChatTheme {
                ActusComposerSheet(
                    savedContacts = contacts,
                    currentUser = User(userId = "me", displayName = "Jean Kouamé", username = "jean.k"),
                    initialDraftMedia = listOf(
                        ActusDraftMediaItem("m1", null, sampleBytesA, "image/jpeg", false),
                        ActusDraftMediaItem("m2", null, sampleBytesB, "image/jpeg", false)
                    ),
                    onPublish = { draft ->
                        publishedDraft = draft
                        Result.success("ok")
                    },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("composer_media_move_right_0").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("actus_composer_text_input").performTextInput("Bravo @aw")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("actus_composer_mention_suggestions").assertIsDisplayed()
        composeTestRule.onNodeWithText("@awa.d").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("actus_composer_preview_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("actus_composer_preview_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("actus_composer_preview_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("actus_composer_publish_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("Bravo @awa.d", publishedDraft?.text?.trim())
        assertEquals(2, publishedDraft?.mediaItems?.size)
        assertTrue(publishedDraft?.mediaItems?.firstOrNull()?.bytes?.contentEquals(sampleBytesB) == true)
    }

    @Test
    fun actusCommentsSheet_groupsThreadedRepliesAndBlocksAbusiveComment() {
        val parent = StatusComment(
            id = "c_parent",
            statusId = "p1",
            authorId = "u1",
            authorName = "Awa Diallo",
            text = "Très belle initiative !"
        )
        val reply = StatusComment(
            id = "c_reply",
            statusId = "p1",
            authorId = "u2",
            authorName = "Marc Bamba",
            text = "Merci beaucoup Awa",
            parentCommentId = "c_parent",
            replyToAuthorName = "Awa Diallo"
        )
        val threads = organizeCommentThreads(listOf(parent, reply))
        assertEquals(1, threads.size)
        assertEquals(listOf("c_reply"), threads.first().replies.map { it.id })

        var submittedParentId = ""
        var submittedReplyToName = ""
        var submittedText = ""

        composeTestRule.setContent {
            BravChatTheme {
                ActusCommentsSheet(
                    status = StatusPost(id = "p1", userId = "u1", userName = "Awa Diallo"),
                    comments = listOf(parent, reply),
                    myUid = "me",
                    onSubmitComment = { text, _, pId, rName, _, onAdded ->
                        submittedText = text
                        submittedParentId = pId
                        submittedReplyToName = rName
                        onAdded()
                    },
                    onDeleteComment = {},
                    onAuthorClick = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("reply_comment_c_parent").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("actus_comment_reply_banner").assertIsDisplayed()

        composeTestRule.onNodeWithTag("actus_comment_input").performTextInput("connard")
        composeTestRule.onNodeWithTag("actus_comment_send_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("actus_comment_moderation_error").assertIsDisplayed()
        assertEquals("", submittedText)

        composeTestRule.onNodeWithTag("actus_comment_input").performTextReplacement("Félicitations à toute l'équipe !")
        composeTestRule.onNodeWithTag("actus_comment_send_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("c_parent", submittedParentId)
        assertEquals("Awa Diallo", submittedReplyToName)
        assertEquals("Félicitations à toute l'équipe !", submittedText)
    }

    @Test
    fun actusComposerSheet_inlineExpressionPanelSearchesAndInsertsWithoutHidingDraft() {
        var publishedText = ""
        composeTestRule.setContent {
            BravChatTheme {
                ActusComposerSheet(
                    savedContacts = emptyList(),
                    currentUser = User(userId = "me", displayName = "Jean Kouamé"),
                    onPublish = { draft ->
                        publishedText = draft.text
                        Result.success("ok")
                    },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("actus_composer_text_input").performTextInput("Lancement prévu ")
        composeTestRule.onNodeWithTag("actus_composer_expression_toggle").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("actus_expression_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("actus_composer_text_input").assertIsDisplayed()

        composeTestRule.onNodeWithTag("actus_expression_panel").performScrollTo()
        composeTestRule.onNodeWithTag("actus_expression_search").performScrollTo().performTextInput("idee")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("actus_expression_panel").performScrollTo()
        composeTestRule.onNodeWithTag("actus_expr_idee", useUnmergedTree = true).performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("actus_composer_publish_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("Lancement prévu 💡", publishedText)
    }

    @Test
    fun actusCommentsSheet_inlineExpressionPanelInsertsTokenAndSubmitsVectorReaction() {
        var submittedText = ""
        var submittedReaction = ""
        composeTestRule.setContent {
            BravChatTheme {
                ActusCommentsSheet(
                    status = StatusPost(id = "p1", userId = "u1", userName = "Awa Diallo"),
                    comments = emptyList(),
                    myUid = "me",
                    onSubmitComment = { text, reactionKey, _, _, _, onAdded ->
                        if (text.isNotBlank()) submittedText = text
                        if (reactionKey.isNotBlank()) submittedReaction = reactionKey
                        onAdded()
                    },
                    onDeleteComment = {},
                    onAuthorClick = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("actus_comment_emoji_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("actus_expression_panel").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("actus_comment_input").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("actus_expression_panel").performScrollTo()
        composeTestRule.onNodeWithTag("actus_expr_pertinent", useUnmergedTree = true).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("actus_comment_input").performScrollTo().performTextInput(" Bravo")
        composeTestRule.onNodeWithTag("actus_comment_send_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("✨ Bravo", submittedText)

        composeTestRule.onNodeWithTag("actus_comment_emoji_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("actus_expression_panel").performScrollTo()
        composeTestRule.onNodeWithTag("actus_panel_reaction_thumbs_up_gold", useUnmergedTree = true).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(BravReactionType.THUMBS_UP_GOLD.key, submittedReaction)
    }

    @Test
    fun chatMessageBubble_revealsDownloadButtonAfter5SecondHoldAndDownloads() {
        val imgMsg = Message(
            id = "m_img_1",
            conversationId = "c1",
            senderId = "u2",
            messageType = "image",
            mediaUrl = "file:///dev/null/souvenir.jpg",
            mediaName = "souvenir.jpg"
        )
        var downloadedMsgId = ""
        var tappedMediaUrl = ""

        composeTestRule.setContent {
            BravChatTheme {
                ChatMessageBubble(
                    message = imgMsg,
                    displayText = "",
                    translatedText = null,
                    isMine = false,
                    myUid = "me",
                    senderDisplayName = "Awa",
                    isGroup = false,
                    playbackState = VoicePlaybackState(),
                    onLongPress = {},
                    onToggleReaction = {},
                    onOpenMedia = { url, _, _ -> tappedMediaUrl = url },
                    onDownloadMedia = { downloadedMsgId = it.id },
                    longPressDownloadDurationMs = 450L,
                    onToggleVoicePlay = {},
                    onCycleVoiceSpeed = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Press and hold past target threshold using virtual clock
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("chat_image_m_img_1", useUnmergedTree = true).performTouchInput {
            down(center)
        }
        composeTestRule.mainClock.advanceTimeBy(700L)
        composeTestRule.onNodeWithTag("chat_image_m_img_1", useUnmergedTree = true).performTouchInput {
            up()
        }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("image_download_button_m_img_1", useUnmergedTree = true).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals("m_img_1", downloadedMsgId)
        assertEquals("", tappedMediaUrl)
    }

    @Test
    fun luxuryEmojiPickerSheet_insertsHighEndEmojisAndEmitsLuxuryReaction() {
        var insertedEmoji = ""
        var pickedReaction: BravReactionType? = null

        composeTestRule.setContent {
            BravChatTheme {
                LuxuryEmojiPickerSheet(
                    onInsertEmoji = { insertedEmoji = it },
                    onSelectReaction = { pickedReaction = it },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("luxury_emoji_picker_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Émojis & Expressions").assertIsDisplayed()
        composeTestRule.onNodeWithTag("luxury_emoji_item_👑").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals("👑", insertedEmoji)

        composeTestRule.onNodeWithTag("luxury_reaction_diamond_lux", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertEquals(BravReactionType.DIAMOND_LUX, pickedReaction)
    }

    @Test
    fun voiceMessageBubble_rendersSlimTrackAndHandlesPlayAndSpeedToggle() {
        var playToggled = 0
        var speedCycled = 0

        composeTestRule.setContent {
            BravChatTheme {
                VoiceMessageBubble(
                    mediaUrl = "https://example.com/audio.m4a",
                    durationMs = 14_000L,
                    waveform = listOf(18, 42, 75, 50, 30, 88, 60, 24),
                    playbackState = VoicePlaybackState(
                        activeUrl = "https://example.com/audio.m4a",
                        isPlaying = true,
                        positionMs = 4_000L,
                        durationMs = 14_000L,
                        speed = 1.5f
                    ),
                    isMine = true,
                    onTogglePlay = { playToggled++ },
                    onCycleSpeed = { speedCycled++ }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("voice_message_bubble").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_waveform_track").assertIsDisplayed()
        composeTestRule.onNodeWithText("0:04").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.5x").assertIsDisplayed()

        composeTestRule.onNodeWithTag("voice_bubble_play_button").performClick()
        composeTestRule.onNodeWithTag("voice_speed_toggle").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, playToggled)
        assertEquals(1, speedCycled)
    }

    @Test
    fun profileCoverAndCropDialog_supportsCoverActionsPanZoomAndAccurateRatioCrop() = runBlocking {
        var coverPickClicks = 0
        var coverRemoveClicks = 0
        var confirmedZoom = 0f

        val srcBitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(AndroidColor.rgb(44, 62, 110))
        }
        val croppedResult = ImageCompressor.cropAndCompressBitmap(
            source = srcBitmap,
            targetAspectRatio = ProfilePhotoTarget.COVER.aspectRatio,
            zoom = 1.5f,
            panX = 0.25f,
            panY = -0.1f,
            maxDimension = 800,
            targetMaxBytes = 300_000
        )
        assertTrue(croppedResult != null && croppedResult.first.isNotEmpty())
        val decoded = android.graphics.BitmapFactory.decodeByteArray(croppedResult!!.first, 0, croppedResult.first.size)
        val outRatio = decoded.width.toFloat() / decoded.height.toFloat()
        assertTrue(kotlin.math.abs(outRatio - ProfilePhotoTarget.COVER.aspectRatio) < 0.08f)
        decoded.recycle()

        var showCropDialog by mutableStateOf(false)
        composeTestRule.setContent {
            BravChatTheme {
                ProfileEditorialHeroCard(
                    displayName = "Awa Konan",
                    username = "awa.k",
                    avatarUrl = "",
                    coverUrl = "https://example.com/cover.jpg",
                    availabilityStatus = "online",
                    locationSummary = "Abidjan • Plateau",
                    bio = "Architecture & Communication",
                    isUploadingAvatar = false,
                    isUploadingCover = false,
                    onPickAvatar = {},
                    onRemoveAvatar = {},
                    onPickCover = {
                        coverPickClicks++
                        showCropDialog = true
                    },
                    onRemoveCover = { coverRemoveClicks++ }
                )
                if (showCropDialog) {
                    ProfileImageCropDialog(
                        pending = PendingCropMedia(ProfilePhotoTarget.COVER, srcBitmap),
                        isProcessing = false,
                        onConfirmCrop = { z, _, _ ->
                            confirmedZoom = z
                            showCropDialog = false
                        },
                        onDismiss = { showCropDialog = false }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("profile_editorial_hero").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_cover_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_cover_image", useUnmergedTree = true).assertIsDisplayed()

        // Long-press cover opens context menu with remove option
        composeTestRule.onNodeWithTag("profile_cover_container").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cover_menu_remove_item").performClick()
        composeTestRule.waitForIdle()

        // Tap cover button opens crop dialog
        composeTestRule.onNodeWithTag("change_cover_photo_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_crop_viewport").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_cover_upload_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, coverPickClicks)
        assertEquals(1, coverRemoveClicks)
        assertEquals(1f, confirmedZoom)
        srcBitmap.recycle()
    }

    @Test
    fun settingsHierarchicalContent_showsOnlyCategoriesOnLandingAndOpensDetailOnClick() {
        var backCalled = false
        composeTestRule.setContent {
            BravChatTheme {
                SettingsHierarchicalContent(
                    currentUser = User(userId = "me", displayName = "Jean Kouamé"),
                    firebaseEmail = "jean@bravchat.com",
                    appPrefs = AppPreferencesState(),
                    blockedCount = 0,
                    isAdmin = false,
                    cacheBytes = 4096L,
                    onBack = { backCalled = true },
                    onOpenProfile = {},
                    onSignOut = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Main overview displays the 5 streamlined categories without dumping inline sub-switches
        composeTestRule.onNodeWithTag("settings_overview_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_cat_account").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_cat_personalization").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_cat_certification").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_cat_communication").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_overview_list").performScrollToNode(hasTestTag("settings_cat_other"))
        composeTestRule.onNodeWithTag("settings_cat_other").assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_noise_suppression").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_pin_button").assertDoesNotExist()

        // Navigate into Communication secondary page
        composeTestRule.onNodeWithTag("settings_cat_communication").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_detail_list").performScrollToNode(hasTestTag("switch_noise_suppression"))
        composeTestRule.onNodeWithTag("switch_noise_suppression").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_detail_list").performScrollToNode(hasTestTag("test_mic_dsp_button"))
        composeTestRule.onNodeWithTag("test_mic_dsp_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_overview_list").assertDoesNotExist()

        // TopBar back returns to overview instead of exiting Settings
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_overview_list").assertIsDisplayed()
        assertFalse(backCalled)
    }

    @Test
    fun offersMarketplace_displaysProductDetailGalleryAndSupportsModerationComposer() {
        val productOffer = OfferListing(
            id = "off_prod_1",
            userId = "u_seller",
            authorName = "Koffi Boutique",
            offerType = "product",
            title = "Casque Studio Sans Fil",
            category = "Vente",
            condition = "Neuf",
            description = "Réduction de bruit active et étui rigide inclus.",
            locationCity = "Abidjan, Cocody",
            priceOrSalary = "65 000 FCFA",
            contactInfo = "+225 07 00 11 22 33",
            imageUrl = "https://example.com/headphones_1.jpg",
            imageUrls = listOf(
                "https://example.com/headphones_1.jpg",
                "https://example.com/headphones_2.jpg"
            )
        )
        var contactedOfferId = ""

        composeTestRule.setContent {
            BravChatTheme {
                OffersMarketplaceContent(
                    myUid = "me",
                    offers = listOf(productOffer),
                    onContactSeller = { contactedOfferId = it.id },
                    onCreateOffer = { Result.success(Unit) },
                    onUpdateOffer = { _, _ -> Result.success(Unit) },
                    onDeleteOffer = {}
                ) {}
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("offer_card_off_prod_1").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("offer_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("offer_detail_gallery").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 / 2").assertIsDisplayed()
        composeTestRule.onNode(hasText("65 000 FCFA") and hasAnyAncestor(isDialog()))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("offer_detail_contact_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("off_prod_1", contactedOfferId)
    }

    @Test
    fun offerComposerDialog_blocksToxicTextAndSubmitsValidServiceWithImages() {
        var submitted: OfferComposeSubmission? = null
        val sampleImg = OfferDraftImage(id = "d1", bytes = byteArrayOf(1, 2, 3, 4))

        composeTestRule.setContent {
            BravChatTheme {
                OfferComposerDialog(
                    initialDraftImages = listOf(sampleImg),
                    onSubmit = { sub ->
                        submitted = sub
                        Result.success(Unit)
                    },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Switch to Service type
        composeTestRule.onNodeWithTag("offer_type_service_chip").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Enter toxic title -> blocked before submit
        composeTestRule.onNodeWithTag("offer_title_input").performScrollTo().performTextInput("Service connard")
        composeTestRule.onNodeWithTag("offer_submit_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("offer_moderation_error_banner").performScrollTo().assertIsDisplayed()
        assertTrue(submitted == null)

        // Replace with clean service fields -> submits cleanly
        composeTestRule.onNodeWithTag("offer_title_input").performScrollTo().performTextReplacement("Installation Fibre & Réseau")
        composeTestRule.onNodeWithTag("offer_price_input").performScrollTo().performTextInput("25 000 FCFA")
        composeTestRule.onNodeWithTag("offer_description_input").performScrollTo().performTextInput("Configuration routeur et Wi-Fi professionnel.")
        composeTestRule.onNodeWithTag("offer_location_input").performScrollTo().performTextInput("Abidjan Plateau")
        composeTestRule.onNodeWithTag("offer_submit_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(submitted != null)
        assertEquals("service", submitted?.offerType)
        assertEquals("Installation Fibre & Réseau", submitted?.title)
        assertEquals(1, submitted?.newImagePayloads?.size)
    }
}

