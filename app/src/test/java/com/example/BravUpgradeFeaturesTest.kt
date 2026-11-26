package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.model.CallAudioRoute
import com.example.model.CallLifecycleState
import com.example.model.CallSource
import com.example.model.CallerDirectoryEntry
import com.example.model.Contact
import com.example.model.FollowRelationState
import com.example.model.InviteLink
import com.example.model.StatusPost
import com.example.model.User
import com.example.telecom.CallDirection
import com.example.telecom.CallerConfidenceLevel
import com.example.telecom.CallerIdResolver
import com.example.telecom.CallerSafetyCategory
import com.example.ui.screens.FollowActionButton
import com.example.ui.screens.FriendProfileModal
import com.example.ui.theme.BravChatTheme
import com.example.util.PhoneLocalityResolver
import com.example.util.PhoneUtils
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
class BravUpgradeFeaturesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun phoneUtils_normalizesLocalInternationalAndShortCodesViaLibPhoneNumber() {
        assertEquals("+2250707070707", PhoneUtils.normalize("07 07 07 07 07", "CI"))
        assertEquals("+33612345678", PhoneUtils.normalize("06 12 34 56 78", "FR"))
        assertEquals("+442079460958", PhoneUtils.normalize("+44 20 7946 0958", "CI"))
        assertEquals("180", PhoneUtils.normalize("180", "CI"))
        assertEquals("111", PhoneUtils.normalize("111", "CI"))
        assertEquals("FR", PhoneUtils.regionCodeForNumber("+33 1 42 68 55 00", "CI"))
        assertEquals("CI", PhoneUtils.regionCodeForNumber("0707070707", "CI"))
    }

    @Test
    fun phoneLocalityResolver_identifiesAfricanFrenchAndInternationalRegions() {
        val ciAbidjan = PhoneLocalityResolver.resolve("+225 27 22 44 55 66")
        assertEquals("CI", ciAbidjan.countryIso)
        assertEquals("Côte d'Ivoire", ciAbidjan.countryName)
        assertEquals("Fixe", ciAbidjan.lineType)
        assertEquals("Abidjan", ciAbidjan.cityName)
        assertTrue(ciAbidjan.fixedCityLabel.contains("Abidjan"))

        val frParis = PhoneLocalityResolver.resolve("+33 1 42 68 55 00")
        assertEquals("FR", frParis.countryIso)
        assertEquals("France", frParis.countryName)
        assertEquals("Fixe", frParis.lineType)
        assertEquals("Paris", frParis.cityName)
        assertEquals("Paris (Île-de-France)", frParis.fixedCityLabel)

        val gbLondon = PhoneLocalityResolver.resolve("+44 20 7946 0958")
        assertEquals("GB", gbLondon.countryIso)
        assertEquals("Royaume-Uni", gbLondon.countryName)
        assertEquals("Londres", gbLondon.cityName)
    }

    @Test
    fun callerIdResolver_enforcesFourTierPriorityAllowPhoneLookupAndHonestUnknownFallback() {
        val rawContactNumber = "+2250701020304"
        val rawBravNumber = "+2250504030201"
        val rawPrivateNumber = "+2250109080706"
        val rawDirectoryNumber = "+2252720212223"
        val rawUnknownNumber = "+33699887766"

        val contact = Contact(
            id = "c1",
            phoneDisplayName = "Koffi Répertoire",
            rawPhoneNumber = rawContactNumber,
            normalizedPhoneNumber = rawContactNumber
        )
        val bravMemberOnContact = User(
            userId = "u_koffi",
            displayName = "Koffi Profil BRAV",
            phoneLookupHash = CallerIdResolver.hashPhoneForLookup(rawContactNumber),
            allowPhoneLookup = true
        )
        val bravMemberPublic = User(
            userId = "u_awa",
            displayName = "Awa Koné",
            phoneLookupHash = CallerIdResolver.hashPhoneForLookup(rawBravNumber),
            allowPhoneLookup = true,
            verificationStatus = "verified",
            verifiedOrgName = "Studio Abidjan"
        )
        val bravMemberOptOut = User(
            userId = "u_secret",
            displayName = "Profil Masqué",
            phoneLookupHash = CallerIdResolver.hashPhoneForLookup(rawPrivateNumber),
            allowPhoneLookup = false
        )
        val verifiedBusiness = CallerDirectoryEntry(
            id = "dir_1",
            phoneLookupHash = CallerIdResolver.hashPhoneForLookup(rawDirectoryNumber),
            businessName = "Pharmacie du Plateau",
            category = "business",
            city = "Abidjan",
            countryIso = "CI",
            verified = true
        )
        val unverifiedBusiness = CallerDirectoryEntry(
            id = "dir_unverified",
            phoneLookupHash = CallerIdResolver.hashPhoneForLookup(rawUnknownNumber),
            businessName = "Entrée Non Vérifiée",
            category = "business",
            verified = false
        )

        // Tier 1: Device contact overrides BRAV user display name
        val tier1 = CallerIdResolver.resolveFromKnownData(
            rawNumber = rawContactNumber,
            direction = CallDirection.INCOMING,
            savedUsers = listOf(bravMemberOnContact),
            phoneContacts = listOf(contact)
        )
        assertEquals("Koffi Répertoire", tier1.displayName)
        assertTrue(tier1.isKnownContact)
        assertFalse(tier1.isUnknownCaller)
        assertEquals(CallerConfidenceLevel.HIGH, tier1.confidence)

        // Tier 2: BRAV member with allowPhoneLookup = true
        val tier2 = CallerIdResolver.resolveFromKnownData(
            rawNumber = rawBravNumber,
            direction = CallDirection.INCOMING,
            savedUsers = listOf(bravMemberPublic),
            phoneContacts = emptyList()
        )
        assertEquals("Awa Koné", tier2.displayName)
        assertTrue(tier2.isBravUser)
        assertTrue(tier2.isVerifiedBrav)
        assertFalse(tier2.isUnknownCaller)

        // Tier 2 privacy opt-out: allowPhoneLookup = false must NOT expose BRAV profile
        val tier2OptOut = CallerIdResolver.resolveFromKnownData(
            rawNumber = rawPrivateNumber,
            direction = CallDirection.INCOMING,
            savedUsers = listOf(bravMemberOptOut),
            phoneContacts = emptyList()
        )
        assertTrue(tier2OptOut.isUnknownCaller)
        assertFalse(tier2OptOut.isBravUser)
        assertFalse(tier2OptOut.displayName.contains("Profil Masqué"))

        // Tier 3: Verified directory resolves business name & safety category
        val tier3 = CallerIdResolver.resolveFromKnownData(
            rawNumber = rawDirectoryNumber,
            direction = CallDirection.INCOMING,
            callerDirectory = listOf(verifiedBusiness)
        )
        assertEquals("Pharmacie du Plateau", tier3.displayName)
        assertEquals(CallerSafetyCategory.BUSINESS, tier3.safetyCategory)
        assertEquals("Annuaire officiel BRAV", tier3.sourceLabel)
        assertFalse(tier3.isUnknownCaller)

        // Tier 4: Unverified directory ignored & unknown number never fabricates a fake name
        val tier4 = CallerIdResolver.resolveFromKnownData(
            rawNumber = rawUnknownNumber,
            direction = CallDirection.INCOMING,
            callerDirectory = listOf(unverifiedBusiness)
        )
        assertTrue(tier4.isUnknownCaller)
        assertEquals(CallerConfidenceLevel.LOW, tier4.confidence)
        assertTrue(tier4.sourceLabel.startsWith("Numéro inconnu"))
        assertFalse(tier4.displayName.contains("Entrée Non Vérifiée"))
        assertEquals("+33 6 99 88 77 66", tier4.displayName)
    }

    @Test
    fun callAndFollowModels_coverAllLifecycleStatesAudioRoutesAndInviteUrls() {
        assertEquals("BRAV", CallSource.BRAV_INTERNET.badgeLabelFr)
        assertEquals("Tél", CallSource.GSM_CARRIER.badgeLabelFr)
        assertEquals(CallSource.GSM_CARRIER, CallSource.fromRaw("gsm_carrier"))

        assertEquals(10, CallLifecycleState.entries.size)
        assertTrue(CallLifecycleState.ENDED.isTerminal)
        assertTrue(CallLifecycleState.MISSED.isTerminal)
        assertTrue(CallLifecycleState.CONNECTION_LOST.isTerminal)
        assertFalse(CallLifecycleState.ON_HOLD.isTerminal)

        assertEquals(
            setOf(CallAudioRoute.EARPIECE, CallAudioRoute.SPEAKER, CallAudioRoute.BLUETOOTH),
            CallAudioRoute.entries.toSet()
        )

        assertEquals("S'abonner", FollowRelationState.NOT_FOLLOWING.buttonLabelFr)
        assertEquals("Abonné", FollowRelationState.FOLLOWING.buttonLabelFr)
        assertEquals("Abonnement demandé", FollowRelationState.REQUESTED.buttonLabelFr)

        val invite = InviteLink(id = "inv1", code = "BRAV88XY", inviterUid = "u1")
        assertEquals("https://bravchat.com/invite/BRAV88XY", invite.shareUrl)
    }

    @Test
    fun followActionButton_cyclesStatesAndRequiresConfirmationToUnfollowOrCancel() {
        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                var state by remember { mutableStateOf(FollowRelationState.NOT_FOLLOWING) }
                FollowActionButton(
                    relationState = state,
                    peerName = "Mariam Traoré",
                    onToggle = {
                        state = when (state) {
                            FollowRelationState.NOT_FOLLOWING -> FollowRelationState.REQUESTED
                            FollowRelationState.REQUESTED -> FollowRelationState.FOLLOWING
                            FollowRelationState.FOLLOWING -> FollowRelationState.NOT_FOLLOWING
                            FollowRelationState.SELF -> FollowRelationState.SELF
                        }
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // NOT_FOLLOWING -> tap immediately triggers onToggle -> REQUESTED
        composeTestRule.onNodeWithText("S'abonner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("follow_action_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Abonnement demandé").assertIsDisplayed()

        // REQUESTED -> tap opens confirmation dialog first
        composeTestRule.onNodeWithTag("follow_action_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("follow_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("follow_confirm_action_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Abonné").assertIsDisplayed()

        // FOLLOWING -> tap opens confirmation dialog to unfollow
        composeTestRule.onNodeWithTag("follow_action_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("follow_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("follow_confirm_action_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("S'abonner").assertIsDisplayed()
    }

    @Test
    fun friendProfileModal_hidesPrivatePostsForNonFollowerAndUnlocksWhenFollowing() {
        val privateAuthor = User(
            userId = "u_priv",
            displayName = "Salif Ouattara",
            username = "salif.o",
            isPrivateProfile = true,
            followersCount = 120,
            followingCount = 45
        )
        val privatePost = StatusPost(
            id = "p_priv_1",
            userId = "u_priv",
            userName = "Salif Ouattara",
            text = "Publication réservée aux abonnés validés",
            audienceType = "all"
        )

        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                var relState by remember { mutableStateOf(FollowRelationState.NOT_FOLLOWING) }
                FriendProfileModal(
                    user = privateAuthor,
                    userPosts = listOf(privatePost),
                    myUid = "me_uid",
                    isContact = false,
                    followRelationState = relState,
                    onToggleFollow = { relState = FollowRelationState.FOLLOWING },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("friend_profile_private_badge").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("publication_grid_item_p_priv_1").assertDoesNotExist()

        // Tap "S'abonner" -> switches to FOLLOWING and unlocks the author's posts
        composeTestRule.onNodeWithTag("friend_profile_follow_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("publication_grid_item_p_priv_1").performScrollTo().assertIsDisplayed()
    }
}
