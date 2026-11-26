package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.Call
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.model.Contact
import com.example.model.Conversation
import com.example.model.ConversationPref
import com.example.model.User
import com.example.network.PresenceStatus
import com.example.network.WorkerAiReply
import com.example.telecom.CallDirection
import com.example.telecom.CarrierCallUiState
import com.example.ui.screens.CarrierCallOverlay
import com.example.ui.screens.DialerModalSheet
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.NewSmsComposerSheet
import com.example.ui.theme.BravChatTheme
import com.example.util.DefaultDialerHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeAndDialerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun whatsappHome_displaysConversations_withoutHardcodedConfigText() {
        val conv1 = Conversation(
            id = "c1",
            participants = listOf("me", "u2"),
            participantNames = mapOf("u2" to "Awa Kouassi"),
            lastMessage = "On se voit à 18h ?"
        )
        val conv2 = Conversation(
            id = "c2",
            isGroup = true,
            groupName = "Projet Alpha",
            participants = listOf("me", "u2", "u3"),
            lastMessage = "Document envoyé"
        )
        var dialerOpened = false

        composeTestRule.setContent {
            BravChatTheme {
                HomeDashboardScreen(
                    currentUserId = "me",
                    conversations = listOf(conv1, conv2),
                    prefsMap = mapOf(
                        "c1" to ConversationPref(conversationId = "c1", pinned = true, markedUnread = true)
                    ),
                    presenceMap = mapOf("u2" to PresenceStatus(uid = "u2", online = true, lastSeen = 0L)),
                    typingConversations = emptyMap(),
                    savedContacts = emptyList(),
                    hasPinConfigured = false,
                    isAdmin = false,
                    onVerifyPin = { true },
                    onOpenConversation = {},
                    onCreateGroup = { _, _, _ -> },
                    onJoinGroupByCode = {},
                    onUpdatePref = { _, _, _, _, _, _, _ -> },
                    onDeleteConversation = {},
                    onNavigateToContacts = {},
                    onOpenStatuses = {},
                    onOpenDialer = { dialerOpened = true },
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

        composeTestRule.onNodeWithText("BRAV Chat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Awa Kouassi").assertIsDisplayed()
        composeTestRule.onNodeWithText("On se voit à 18h ?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Projet Alpha").assertIsDisplayed()

        // Ensure hardcoded configuration / telemetry labels are gone
        composeTestRule.onNodeWithText("Synchronisation Firestore", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Temps réel actif", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Bienvenue sur BRAV Chat", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Actions rapides", substring = true).assertDoesNotExist()

        // Filter to Groups only
        composeTestRule.onNodeWithTag("home_filter_groups").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Projet Alpha").assertIsDisplayed()
        composeTestRule.onNodeWithText("Awa Kouassi").assertDoesNotExist()

        // Open search bar and search
        composeTestRule.onNodeWithTag("home_filter_all").performClick()
        composeTestRule.onNodeWithTag("home_search_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_search_input").performTextInput("Awa")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Awa Kouassi").assertIsDisplayed()
        composeTestRule.onNodeWithText("Projet Alpha").assertDoesNotExist()

        // Trigger Dialer from top bar
        composeTestRule.onNodeWithTag("home_dialer_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(dialerOpened)
    }

    @Test
    fun dialerSheet_entersDigits_andMatchesBravUser() {
        val bravFriend = User(
            userId = "u9",
            displayName = "Koffi Yao",
            username = "koffi",
            phoneMasked = "+225 •••• 0304"
        )
        val simContact = Contact(
            id = "u9",
            phoneDisplayName = "Koffi Yao",
            rawPhoneNumber = "+225 07 01 02 03 04",
            normalizedPhoneNumber = "+2250701020304",
            bravUser = bravFriend
        )

        composeTestRule.setContent {
            BravChatTheme {
                DialerModalSheet(
                    initialNumber = "07010203",
                    savedContacts = listOf(bravFriend),
                    phoneContacts = listOf(simContact),
                    searchedUsers = listOf(bravFriend),
                    onLookupNumber = {},
                    onStartBravAudioCall = { _, _ -> },
                    onStartBravVideoCall = { _, _ -> },
                    onFeedback = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialer_key_0").performClick()
        composeTestRule.onNodeWithTag("dialer_key_4").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Koffi Yao").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialer_brav_audio_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialer_carrier_call_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dialer_backspace_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialer_number_display").assertIsDisplayed()
    }

    @Test
    fun defaultDialerHelper_parsesTelIntentAndSanitizesInput() {
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:%2B225%2007-01-02-03-04"))
        assertEquals("+2250701020304", DefaultDialerHelper.extractDialNumberFromIntent(dialIntent))

        val viewTelIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:2722445566"))
        assertEquals("2722445566", DefaultDialerHelper.extractDialNumberFromIntent(viewTelIntent))

        val mainIntent = Intent(Intent.ACTION_MAIN)
        assertNull(DefaultDialerHelper.extractDialNumberFromIntent(mainIntent))

        assertEquals("*123#", DefaultDialerHelper.sanitizeDialInput("*123# "))
        assertEquals("+33612345678", DefaultDialerHelper.sanitizeDialInput("+33 6 12-34+56 78"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        // Just verify default dialer API checks do not crash on Robolectric SDK 36
        DefaultDialerHelper.isDefaultDialerSupported(context)
        DefaultDialerHelper.isBravDefaultDialer(context)
    }

    @Test
    fun carrierCallOverlay_displaysOutgoingCallerIdentityAndFixedCity() {
        val state = CarrierCallUiState(
            hasCall = true,
            number = "+2252722445566",
            formattedNumber = "+225 27 22 44 55 66",
            callerDisplayName = "Mairie de Cocody",
            direction = CallDirection.OUTGOING,
            cityLabel = "Abidjan (Cocody / Angré / Riviera / Deux-Plateaux)",
            localitySummary = "\uD83C\uDDE8\uD83C\uDDEE Côte d'Ivoire · Abidjan · Cocody · Orange Fixe",
            state = Call.STATE_DIALING
        )
        composeTestRule.setContent {
            BravChatTheme {
                CarrierCallOverlay(state = state)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("carrier_call_direction_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Appel sortant identifié").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mairie de Cocody").assertIsDisplayed()
        composeTestRule.onNodeWithTag("carrier_call_location_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("carrier_hangup_button").assertIsDisplayed()
    }

    @Test
    fun homeConversationAiDot_togglesDirectAiRule() {
        val conv1 = Conversation(
            id = "c_ai_1",
            participants = listOf("me", "u_marie"),
            participantNames = mapOf("u_marie" to "Marie Claire"),
            lastMessage = "Tu es libre ?"
        )
        var toggledKey = ""
        var toggledLabel = ""

        composeTestRule.setContent {
            BravChatTheme {
                HomeDashboardScreen(
                    currentUserId = "me",
                    conversations = listOf(conv1),
                    prefsMap = emptyMap(),
                    presenceMap = emptyMap(),
                    typingConversations = emptyMap(),
                    savedContacts = emptyList(),
                    hasPinConfigured = false,
                    isAdmin = false,
                    onToggleConversationAi = { k, l ->
                        toggledKey = k
                        toggledLabel = l
                    },
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
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_conv_ai_dot_c_ai_1").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals("u_marie", toggledKey)
        assertEquals("Marie Claire", toggledLabel)
    }

    @Test
    fun newSmsComposerSheet_selectsContactShowsLocalityAndSendsAiDraft() {
        val phoneContact = Contact(
            id = "c1",
            phoneDisplayName = "Awa Cocody",
            rawPhoneNumber = "+225 27 22 44 55 66",
            normalizedPhoneNumber = "+2252722445566"
        )
        var sentTo = ""
        var sentBody = ""

        composeTestRule.setContent {
            BravChatTheme {
                NewSmsComposerSheet(
                    phoneContacts = listOf(phoneContact),
                    savedUsers = emptyList(),
                    existingThreads = emptyList(),
                    onGenerateAiDraft = { _, _ ->
                        Result.success(WorkerAiReply("@cf/meta/llama-3.1-8b-instruct", "Bonjour Awa, bien reçu pour 18h !"))
                    },
                    onOpenExistingThread = {},
                    onSendSms = { to, body ->
                        sentTo = to
                        sentBody = body
                    },
                    onShowMessage = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("new_sms_recipient_input").performTextInput("Awa")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("new_sms_contact_+2252722445566").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Tapping the small AI dot generates an inline suggestion and sends directly
        composeTestRule.onNodeWithTag("new_sms_ai_dot").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ai_reply_suggestion_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bonjour Awa, bien reçu pour 18h !").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_suggestion_send_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("+225 27 22 44 55 66", sentTo)
        assertEquals("Bonjour Awa, bien reçu pour 18h !", sentBody)
    }

    @Test
    fun peerTypingIndicatorBubble_displaysAnimatedWave() {
        composeTestRule.setContent {
            BravChatTheme {
                com.example.ui.components.PeerTypingIndicatorBubble(
                    senderName = "Paul Yao",
                    isGroup = true
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("peer_typing_indicator_bubble").assertIsDisplayed()
        composeTestRule.onNodeWithText("Paul Yao").assertIsDisplayed()
    }

    @Test
    fun homeDashboard_displaysTypingWaveForTypingPeer() {
        val convTyping = Conversation(
            id = "c_type_1",
            participants = listOf("me", "u_paul"),
            participantNames = mapOf("u_paul" to "Paul Yao"),
            lastMessage = "Salut !",
            typingUsers = listOf("u_paul")
        )

        composeTestRule.setContent {
            BravChatTheme {
                HomeDashboardScreen(
                    currentUserId = "me",
                    conversations = listOf(convTyping),
                    prefsMap = emptyMap(),
                    presenceMap = emptyMap(),
                    typingConversations = emptyMap(),
                    savedContacts = emptyList(),
                    hasPinConfigured = false,
                    isAdmin = false,
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
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Paul Yao").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_typing_indicator_c_type_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("écrit...").assertIsDisplayed()
    }

    @Test
    fun homeDashboard_displaysDirectOffersAndAnnouncementsTileWithDynamicBadge() {
        var offersOpenedCount = 0
        composeTestRule.setContent {
            BravChatTheme {
                HomeDashboardScreen(
                    currentUserId = "me",
                    conversations = emptyList(),
                    prefsMap = emptyMap(),
                    presenceMap = emptyMap(),
                    typingConversations = emptyMap(),
                    savedContacts = emptyList(),
                    hasPinConfigured = false,
                    isAdmin = false,
                    offersCount = 7,
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
                    onOpenOffers = { offersOpenedCount++ },
                    onOpenProfile = {},
                    onOpenSettings = {},
                    onOpenAdmin = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Actions rapides", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_offers_tile").assertIsDisplayed()
        composeTestRule.onNodeWithText("OFFRES ET ANNONCES").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_offers_count_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("7").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_offers_tile").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, offersOpenedCount)
    }
}
