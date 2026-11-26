package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.local.AppPreferencesState
import com.example.model.AppNotification
import com.example.model.Conversation
import com.example.model.ConversationPref
import com.example.model.DesignerSyncPolicyState
import com.example.model.NotificationCategory
import com.example.model.NotificationCounters
import com.example.model.User
import com.example.model.VerificationCategory
import com.example.model.VerificationStatus
import com.example.repository.NotificationRepository
import com.example.ui.components.VerificationStatusPill
import com.example.ui.components.VerifiedBlueBadge
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.NotificationsCenterScreen
import com.example.ui.screens.ProfileEditorialHeroCard
import com.example.ui.theme.BravChatTheme
import com.example.ui.theme.BravTheme
import com.google.firebase.Timestamp
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
class NotificationAndLuxurySystemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mergeNotifications_deduplicatesByDedupKeyOverlaysLocalStateAndComputesCounters() {
        val firestoreItems = listOf(
            AppNotification(
                id = "n_msg_1",
                category = NotificationCategory.MESSAGES.raw,
                title = "Amina Diallo",
                body = "Bonjour, tu es disponible ?",
                dedupKey = "msg:c1:m1",
                isRead = false,
                createdAtMs = 1_000L
            ),
            AppNotification(
                id = "n_contact_1",
                category = NotificationCategory.CONTACTS.raw,
                title = "Nouveau contact",
                body = "Koffi Konan vous a ajouté",
                dedupKey = "contact:u2",
                isRead = false,
                createdAtMs = 2_000L
            ),
            AppNotification(
                id = "n_actus_1",
                category = NotificationCategory.ACTUS.raw,
                title = "ACTUS",
                body = "Nouvelle publication",
                dedupKey = "actus:p1",
                isRead = false,
                createdAtMs = 3_000L
            ),
            AppNotification(
                id = "n_deleted",
                category = NotificationCategory.MESSAGES.raw,
                title = "Supprimée",
                body = "Ne doit plus apparaître",
                dedupKey = "msg:deleted",
                isRead = false,
                createdAtMs = 3_500L
            )
        )

        val workerItems = listOf(
            // Duplicate of n_msg_1 marked read on worker -> combined isRead should be true
            AppNotification(
                id = "w_msg_1",
                category = NotificationCategory.MESSAGES.raw,
                title = "Amina Diallo",
                body = "Bonjour, tu es disponible ?",
                dedupKey = "msg:c1:m1",
                isRead = true,
                readAtMs = 1_500L,
                createdAtMs = 1_100L
            ),
            AppNotification(
                id = "n_call_1",
                category = NotificationCategory.CALLS.raw,
                title = "Appel manqué",
                body = "Jean-Claude Mensah",
                dedupKey = "call:c1",
                isRead = false,
                createdAtMs = 4_000L
            ),
            AppNotification(
                id = "n_offer_1",
                category = NotificationCategory.OFFERS_JOBS.raw,
                title = "Offre publiée",
                body = "Développeur Android Senior",
                dedupKey = "offer:o1",
                isRead = false,
                createdAtMs = 5_000L
            ),
            AppNotification(
                id = "n_sec_1",
                category = NotificationCategory.ACCOUNT_SECURITY.raw,
                title = "CERTIFICATION",
                body = "Votre dossier CERTIFICATION est approuvé",
                dedupKey = "cert:u1",
                isRead = false,
                createdAtMs = 6_000L
            )
        )

        val merged = NotificationRepository.mergeNotifications(
            firestoreList = firestoreItems,
            workerList = workerItems,
            locallyReadIds = setOf("n_actus_1"),
            locallyDeletedIds = setOf("n_deleted")
        )

        // 7 unique items minus 1 locally deleted = 6 items, sorted newest first
        assertEquals(6, merged.size)
        assertEquals("n_sec_1", merged.first().id)
        assertFalse(merged.any { it.id == "n_deleted" })

        val msgMerged = merged.first { it.dedupKey == "msg:c1:m1" }
        assertTrue(msgMerged.isRead)

        val actusMerged = merged.first { it.id == "n_actus_1" }
        assertTrue(actusMerged.isRead)

        val counters = NotificationRepository.computeCounters(merged)
        assertEquals(0, counters.messagesUnread)
        assertEquals(1, counters.contactsUnread)
        assertEquals(0, counters.actusUnread)
        assertEquals(1, counters.callsUnread)
        assertEquals(1, counters.offersJobsUnread)
        assertEquals(1, counters.accountSecurityUnread)
        assertEquals(4, counters.totalUnread)
    }

    @Test
    fun appPreferencesState_evaluatesQuietHoursSameDayAndOvernight() {
        val disabled = AppPreferencesState(
            quietHoursEnabled = false,
            quietHoursStartHour = 22,
            quietHoursEndHour = 7
        )
        assertFalse(disabled.isInQuietHours(23))
        assertFalse(disabled.isInQuietHours(3))

        val sameDay = AppPreferencesState(
            quietHoursEnabled = true,
            quietHoursStartHour = 9,
            quietHoursEndHour = 17
        )
        assertFalse(sameDay.isInQuietHours(8))
        assertTrue(sameDay.isInQuietHours(9))
        assertTrue(sameDay.isInQuietHours(13))
        assertFalse(sameDay.isInQuietHours(17))

        val overnight = AppPreferencesState(
            quietHoursEnabled = true,
            quietHoursStartHour = 22,
            quietHoursEndHour = 7
        )
        assertFalse(overnight.isInQuietHours(21))
        assertTrue(overnight.isInQuietHours(22))
        assertTrue(overnight.isInQuietHours(0))
        assertTrue(overnight.isInQuietHours(6))
        assertFalse(overnight.isInQuietHours(7))
    }

    @Test
    fun notificationsCenterScreen_filtersByCategoryAndMarksSelectedCategoryRead() {
        var markedCategory: NotificationCategory? = null
        val notifications = listOf(
            AppNotification(
                id = "m1",
                category = NotificationCategory.MESSAGES.raw,
                title = "Message d'Amina",
                body = "On se retrouve à 15h",
                isRead = false,
                createdAtMs = System.currentTimeMillis() - 60_000L
            ),
            AppNotification(
                id = "s1",
                category = NotificationCategory.ACCOUNT_SECURITY.raw,
                title = "Statut CERTIFICATION",
                body = "Badge bleu activé sur votre compte",
                isRead = false,
                createdAtMs = System.currentTimeMillis() - 120_000L
            )
        )
        val counters = NotificationCounters(
            totalUnread = 2,
            messagesUnread = 1,
            accountSecurityUnread = 1
        )

        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                NotificationsCenterScreen(
                    notifications = notifications,
                    counters = counters,
                    onNotificationClick = {},
                    onMarkAllRead = { markedCategory = it },
                    onDeleteNotification = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("notif_card_m1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notif_card_s1").assertIsDisplayed()

        composeTestRule.onNodeWithTag("notifications_filter_row")
            .performScrollToNode(hasTestTag("notif_tab_account_security"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("notif_tab_account_security").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("notif_card_s1").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Message d'Amina").assertCountEquals(0)

        composeTestRule.onNodeWithTag("notifications_mark_all_read_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(NotificationCategory.ACCOUNT_SECURITY, markedCategory)
    }

    @Test
    fun homeDashboardScreen_showsRealUnreadCountAndUnreadFilter() {
        val now = Timestamp.now()
        val unreadConv = Conversation(
            id = "c1",
            participants = listOf("me", "u2"),
            participantNames = mapOf("me" to "Moi", "u2" to "Fatou Koné"),
            lastMessage = "Tu as vu le dossier ?",
            lastMessageTimestamp = now,
            updatedAt = now
        )
        val readConv = Conversation(
            id = "c2",
            participants = listOf("me", "u3"),
            participantNames = mapOf("me" to "Moi", "u3" to "Moussa Traoré"),
            lastMessage = "Merci beaucoup",
            lastMessageTimestamp = now,
            updatedAt = now
        )

        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                HomeDashboardScreen(
                    currentUserId = "me",
                    conversations = listOf(unreadConv, readConv),
                    prefsMap = mapOf(
                        "c1" to ConversationPref(id = "c1", conversationId = "c1"),
                        "c2" to ConversationPref(id = "c2", conversationId = "c2")
                    ),
                    unreadCountByConversation = mapOf("c1" to 5),
                    notificationCounters = NotificationCounters(totalUnread = 7, messagesUnread = 5, callsUnread = 2),
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
                    onOpenNotifications = {},
                    onOpenProfile = {},
                    onOpenSettings = {},
                    onOpenAdmin = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_notifications_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("7").assertIsDisplayed()
        composeTestRule.onNodeWithText("5").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_conversation_c1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_conversation_c2").assertIsDisplayed()

        composeTestRule.onNodeWithTag("home_filter_unread").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_conversation_c1").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Moussa Traoré").assertCountEquals(0)
    }

    @Test
    fun luxuryThemeAndCertificationBadge_preserveTranslucentSurfacesAndOfficialNaming() {
        var lightCardAlpha = 1f
        var lightSubtleAlpha = 1f
        var lightModalAlpha = 1f
        var darkCardAlpha = 1f

        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                val lux = BravTheme.colors
                lightCardAlpha = lux.translucentCardSurface.alpha
                lightSubtleAlpha = lux.translucentSubtleSurface.alpha
                lightModalAlpha = lux.modalSurface.alpha

                Column {
                    VerifiedBlueBadge(
                        showTooltipOnTap = true,
                        verifiedOrgName = "Africa Inspire",
                        verificationCategory = "Entreprise ou organisation"
                    )
                    VerificationStatusPill(status = VerificationStatus.VERIFIED)
                }
            }
            BravChatTheme(darkTheme = true) {
                darkCardAlpha = BravTheme.colors.translucentCardSurface.alpha
            }
        }
        composeTestRule.waitForIdle()

        assertTrue(lightCardAlpha < 1f)
        assertTrue(lightSubtleAlpha < 1f)
        assertTrue(lightModalAlpha < 1f)
        assertTrue(darkCardAlpha < 1f)

        composeTestRule.onAllNodesWithTag("verified_blue_badge")
            .filterToOne(hasClickAction())
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Compte CERTIFICATION").assertIsDisplayed()
        composeTestRule.onNodeWithText("Entité confirmée : Africa Inspire").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("BRAV VERIFIED", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun designerCertification_normalizesStaleStatePreventsLoopsAndKeepsHeroBadgeStable() {
        val staleDesigner = User(
            userId = "designer_1",
            displayName = "Bravo Boutique Officiel",
            verificationStatus = VerificationStatus.NONE.raw,
            verificationCategory = "",
            verifiedOrgName = "",
            verificationExpiresAtMs = 1_000L
        )
        assertFalse(staleDesigner.isDesignerProfileNormalized())

        val normalized = staleDesigner.withDesignerCertificationIfEligible(isExemptDesigner = true)
        assertTrue(normalized.isVerifiedActive())
        assertTrue(normalized.isDesignerProfileNormalized())
        assertEquals(VerificationCategory.CREATOR.raw, normalized.verificationCategory)
        assertEquals("Concepteur BRAV CHAT", normalized.verifiedOrgName)
        assertEquals(0L, normalized.verificationExpiresAtMs)

        // Regular accounts must never be auto-certified by client normalization
        val regularUnchanged = staleDesigner.withDesignerCertificationIfEligible(isExemptDesigner = false)
        assertFalse(regularUnchanged.isVerifiedActive())

        // Sync policy must prevent optimistic/rollback loops via cooldown and session attempt cap
        var policy = DesignerSyncPolicyState()
        assertTrue(policy.shouldAttemptSync(isAlreadyNormalized = false, nowMs = 10_000L))
        policy = policy.copy(attemptCount = 1, lastAttemptMs = 10_000L)
        assertFalse(policy.shouldAttemptSync(isAlreadyNormalized = false, nowMs = 10_500L))
        assertTrue(policy.shouldAttemptSync(isAlreadyNormalized = false, nowMs = 26_000L))
        policy = policy.copy(attemptCount = 2, lastAttemptMs = 26_000L)
        assertFalse(policy.shouldAttemptSync(isAlreadyNormalized = false, nowMs = 99_000L))

        val completedPolicy = DesignerSyncPolicyState(completedForSession = true, attemptCount = 1, lastAttemptMs = 10_000L)
        assertFalse(completedPolicy.shouldAttemptSync(isAlreadyNormalized = true, nowMs = 50_000L))

        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                ProfileEditorialHeroCard(
                    displayName = "Concepteur Officiel BRAV CHAT International Très Long Nom",
                    username = "bravoboutique225",
                    avatarUrl = "",
                    coverUrl = "",
                    availabilityStatus = "online",
                    locationSummary = "Abidjan • Côte d'Ivoire",
                    bio = "Compte officiel",
                    isVerified = normalized.isVerifiedActive(),
                    verifiedOrgName = normalized.verifiedOrgName,
                    verificationCategory = normalized.verificationCategoryEnum.labelFr,
                    isUploadingAvatar = false,
                    isUploadingCover = false,
                    onPickAvatar = {},
                    onRemoveAvatar = {},
                    onPickCover = {},
                    onRemoveCover = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("profile_editorial_hero").assertIsDisplayed()
        composeTestRule.onNodeWithTag("verified_blue_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Créateur de contenu • Concepteur BRAV CHAT").assertIsDisplayed()
    }
}
