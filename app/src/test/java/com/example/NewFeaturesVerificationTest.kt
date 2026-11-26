package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.launcher.LauncherClockStyle
import com.example.launcher.LauncherThemePalette
import com.example.launcher.LauncherWeatherCity
import com.example.model.CallerReport
import com.example.model.CallerReportCategory
import com.example.model.CertificationEligibility
import com.example.model.Contact
import com.example.model.StatusPost
import com.example.model.User
import com.example.model.VerificationCategory
import com.example.model.VerificationRequest
import com.example.model.VerificationStatus
import com.example.security.BravHumanVerificationService
import com.example.telecom.CallDirection
import com.example.telecom.CallerConfidenceLevel
import com.example.telecom.CallerIdResolver
import com.example.telecom.CallerSafetyCategory
import com.example.ui.components.VerifiedBlueBadge
import com.example.ui.screens.ActusSearchCategory
import com.example.ui.screens.ActusSearchSort
import com.example.ui.screens.SmsLocalDeleteConfirmationDialog
import com.example.ui.screens.extractActusHashtags
import com.example.ui.screens.filterActusPeople
import com.example.ui.screens.filterActusPosts
import com.example.ui.theme.BravChatTheme
import com.example.util.WhatsAppLauncher
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NewFeaturesVerificationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun verificationModels_supportAllStatusesEligibilityAndExpiration() {
        assertEquals(15, VerificationStatus.entries.size)
        assertEquals(5, VerificationCategory.entries.size)
        assertEquals(5000, CertificationEligibility.REQUIRED_FOLLOWERS)
        assertEquals(3600, CertificationEligibility.FEE_XOF)
        assertEquals("5,99 USD", CertificationEligibility.FEE_USD_LABEL)
        assertEquals("bravoboutique225@gmail.com", CertificationEligibility.DESIGNER_EXEMPT_EMAIL)
        VerificationCategory.entries.forEach { cat ->
            assertTrue(cat.requiredDocsFr.isNotBlank())
        }

        val eligLow = CertificationEligibility(followersCount = 4200, eligible = false)
        assertEquals(800, eligLow.missingFollowers)
        val eligOk = CertificationEligibility(followersCount = 5300, eligible = true)
        assertEquals(0, eligOk.missingFollowers)

        val now = 1_750_000_000_000L
        val activeUser = User(
            userId = "u1",
            displayName = "Cabinet Konan",
            followersCount = 5200,
            verificationStatus = VerificationStatus.VERIFIED.raw,
            verificationExpiresAtMs = now + 86_400_000L
        )
        assertTrue(activeUser.isVerifiedActive(now))
        assertEquals(VerificationStatus.VERIFIED, activeUser.effectiveVerificationStatus(now))

        val expiredUser = activeUser.copy(verificationExpiresAtMs = now - 10_000L)
        assertFalse(expiredUser.isVerifiedActive(now))
        assertEquals(VerificationStatus.EXPIRED, expiredUser.effectiveVerificationStatus(now))

        val req = VerificationRequest(
            id = "vr1",
            caseNumber = "BRV-2026-104200",
            userId = "u1",
            fullName = "Koffi Konan",
            category = VerificationCategory.PROFESSIONAL.raw,
            followersCountAtRequest = 5200,
            selfieVerified = true
        )
        assertTrue(req.caseNumber.startsWith("BRV-"))
    }

    @Test
    fun humanVerificationService_enforcesFacePresenceLightingSharpnessAndAntiSpoof() {
        val w = 64
        val h = 64
        val crispDaylightLuma = DoubleArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if ((x + y) % 2 == 0) 165.0 else 105.0
        }
        val good = BravHumanVerificationService.evaluateMetrics(
            luma = crispDaylightLuma,
            width = w,
            height = h,
            faceCount = 1,
            faceConfidence = 0.85f,
            eyesDistance = 14f
        )
        assertTrue(good.passed)
        assertEquals("low", good.spoofRisk)

        val noFace = BravHumanVerificationService.evaluateMetrics(
            luma = crispDaylightLuma,
            width = w,
            height = h,
            faceCount = 0,
            faceConfidence = 0f,
            eyesDistance = 0f
        )
        assertFalse(noFace.passed)
        assertEquals("high", noFace.spoofRisk)

        val multiFace = BravHumanVerificationService.evaluateMetrics(
            luma = crispDaylightLuma,
            width = w,
            height = h,
            faceCount = 2,
            faceConfidence = 0.8f,
            eyesDistance = 12f
        )
        assertFalse(multiFace.passed)

        val darkLuma = DoubleArray(w * h) { 12.0 }
        val tooDark = BravHumanVerificationService.evaluateMetrics(
            luma = darkLuma,
            width = w,
            height = h,
            faceCount = 1,
            faceConfidence = 0.7f,
            eyesDistance = 14f
        )
        assertFalse(tooDark.passed)

        val blurryFlatLuma = DoubleArray(w * h) { 125.0 }
        val blurry = BravHumanVerificationService.evaluateMetrics(
            luma = blurryFlatLuma,
            width = w,
            height = h,
            faceCount = 1,
            faceConfidence = 0.7f,
            eyesDistance = 14f
        )
        assertFalse(blurry.passed)
        assertEquals("high", blurry.spoofRisk)
    }

    @Test
    fun whatsAppLauncher_formatsNumbersAndBuildsSafeWaMeLinks() {
        assertEquals("2250701020304", WhatsAppLauncher.formatDigitsForWaMe("+225 07 01 02 03 04"))
        assertEquals("2250701020304", WhatsAppLauncher.formatDigitsForWaMe("Tel: 07 01 02 03 04", "CI"))
        assertEquals("", WhatsAppLauncher.formatDigitsForWaMe("abc"))

        val url = WhatsAppLauncher.buildWaMeUrl("07 01 02 03 04", "Bonjour BRAV!")
        assertEquals("https://wa.me/2250701020304?text=Bonjour%20BRAV!", url)
        assertNull(WhatsAppLauncher.buildWaMeUrl("12", "Salut"))
    }

    @Test
    fun callerIdResolver_honestLookupVerifiedBadgeAndSpamControls() {
        val hash1 = CallerIdResolver.hashPhoneForLookup("+225 07 01 02 03 04", "CI")
        val hash2 = CallerIdResolver.hashPhoneForLookup("0701020304", "CI")
        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)

        val verifiedUser = User(
            userId = "u_org",
            displayName = "Clinique Horizon",
            phoneMasked = "+2250701020304",
            verificationStatus = VerificationStatus.VERIFIED.raw,
            verifiedOrgName = "Clinique Horizon"
        )
        val simContact = Contact(
            id = "c_org",
            phoneDisplayName = "Clinique Horizon",
            rawPhoneNumber = "+2250701020304",
            normalizedPhoneNumber = "+2250701020304",
            bravUser = verifiedUser
        )

        val verifiedIdentity = CallerIdResolver.resolveFromKnownData(
            rawNumber = "+225 07 01 02 03 04",
            direction = CallDirection.INCOMING,
            savedUsers = listOf(verifiedUser),
            phoneContacts = listOf(simContact),
            callerIdEnabled = true,
            spamWarningsEnabled = true
        )
        assertTrue(verifiedIdentity.isVerifiedBrav)
        assertEquals("Clinique Horizon", verifiedIdentity.verifiedOrgName)
        assertEquals(CallerConfidenceLevel.HIGH, verifiedIdentity.confidence)
        assertEquals("Compte BRAV certifié", verifiedIdentity.sourceLabel)

        // Unknown caller: must never invent a random name
        val unknownIdentity = CallerIdResolver.resolveFromKnownData(
            rawNumber = "+2250544556677",
            direction = CallDirection.INCOMING,
            callerIdEnabled = true,
            spamWarningsEnabled = true
        )
        assertEquals(CallerConfidenceLevel.LOW, unknownIdentity.confidence)
        assertFalse(unknownIdentity.isBravUser)
        assertTrue(unknownIdentity.displayName.contains("05"))

        // Approved community spam warning + privacy toggles
        val spamNumber = "+2250199887766"
        val spamHash = CallerIdResolver.hashPhoneForLookup(spamNumber, "CI")
        val approvedSpam = CallerReport(
            id = "rep_1",
            reporterId = "u2",
            phoneLookupHash = spamHash,
            maskedNumber = "+22501••••66",
            category = CallerReportCategory.SCAM.raw,
            label = "Faux support mobile",
            status = "approved",
            reportCount = 4
        )

        val warnedIdentity = CallerIdResolver.resolveFromKnownData(
            rawNumber = spamNumber,
            approvedReports = listOf(approvedSpam),
            callerIdEnabled = true,
            spamWarningsEnabled = true
        )
        assertEquals(CallerSafetyCategory.SCAM, warnedIdentity.safetyCategory)
        assertTrue(warnedIdentity.safetyCategory.isWarning)
        assertEquals(CallerConfidenceLevel.HIGH, warnedIdentity.confidence)
        assertEquals(4, warnedIdentity.reportCount)

        val mutedWarningsIdentity = CallerIdResolver.resolveFromKnownData(
            rawNumber = spamNumber,
            approvedReports = listOf(approvedSpam),
            callerIdEnabled = true,
            spamWarningsEnabled = false
        )
        assertEquals(CallerSafetyCategory.NONE, mutedWarningsIdentity.safetyCategory)

        val disabledCallerId = CallerIdResolver.resolveFromKnownData(
            rawNumber = "+2250701020304",
            savedUsers = listOf(verifiedUser),
            phoneContacts = listOf(simContact),
            callerIdEnabled = false
        )
        assertFalse(disabledCallerId.isBravUser)
        assertFalse(disabledCallerId.isVerifiedBrav)
        assertEquals(CallerConfidenceLevel.LOW, disabledCallerId.confidence)
    }

    @Test
    fun actusSearchHelpers_filterByCategoryQueryHashtagAndSort() {
        val uVerified = User(
            userId = "u_ver",
            displayName = "Awa Media",
            username = "awamedia",
            verificationStatus = VerificationStatus.VERIFIED.raw,
            verifiedOrgName = "Awa Press"
        )
        val uRegular = User(
            userId = "u_reg",
            displayName = "Koffi Tech",
            username = "koffitech"
        )
        val usersMap = mapOf(uVerified.userId to uVerified, uRegular.userId to uRegular)

        val posts = listOf(
            StatusPost(
                id = "p1",
                userId = "u_ver",
                userName = "Awa Media",
                authorUsername = "awamedia",
                type = "image",
                caption = "Innovation à #Abidjan avec #Tech",
                mediaUrl = "https://example.com/1.jpg",
                likedByUids = listOf("a"),
                commentCount = 1,
                createdAt = Timestamp(1000L, 0)
            ),
            StatusPost(
                id = "p2",
                userId = "u_reg",
                userName = "Koffi Tech",
                authorUsername = "koffitech",
                type = "video",
                caption = "Démo mobile #Tech #BRAV",
                mediaUrl = "https://example.com/2.mp4",
                likedByUids = listOf("a", "b", "c"),
                commentCount = 2,
                createdAt = Timestamp(2000L, 0)
            ),
            StatusPost(
                id = "p3",
                userId = "u_reg",
                userName = "Koffi Tech",
                authorUsername = "koffitech",
                type = "text",
                text = "Bonjour à tous sans média",
                createdAt = Timestamp(3000L, 0)
            )
        )

        val tags = extractActusHashtags(posts)
        assertEquals("#Tech", tags.first())
        assertTrue(tags.contains("#Abidjan"))

        val verifiedPosts = filterActusPosts(posts, usersMap, "", ActusSearchCategory.VERIFIED, ActusSearchSort.RECENT)
        assertEquals(listOf("p1"), verifiedPosts.map { it.id })

        val photoPosts = filterActusPosts(posts, usersMap, "", ActusSearchCategory.PHOTOS, ActusSearchSort.RECENT)
        assertEquals(listOf("p1"), photoPosts.map { it.id })

        val videoPosts = filterActusPosts(posts, usersMap, "", ActusSearchCategory.VIDEOS, ActusSearchSort.RECENT)
        assertEquals(listOf("p2"), videoPosts.map { it.id })

        val popularTech = filterActusPosts(posts, usersMap, "#Tech", ActusSearchCategory.HASHTAGS, ActusSearchSort.POPULAR)
        assertEquals(listOf("p2", "p1"), popularTech.map { it.id })

        val people = filterActusPeople(usersMap, "awa", verifiedOnly = true)
        assertEquals(listOf("u_ver"), people.map { it.userId })
    }

    @Test
    fun launcherModels_includeBravPearlClockStylesAndWeatherCityJson() {
        assertTrue(LauncherThemePalette.entries.contains(LauncherThemePalette.BRAV_PEARL))
        assertEquals(3, LauncherClockStyle.entries.size)
        val city = LauncherWeatherCity("Dakar", 14.71668, -17.46769)
        val parsed = LauncherWeatherCity.fromJson(city.toJson())
        assertEquals(city.name, parsed.name)
        assertEquals(city.latitude, parsed.latitude, 0.0001)
    }

    @Test
    fun verifiedBlueBadge_opensExplanationDialogOnTap() {
        composeTestRule.setContent {
            BravChatTheme {
                VerifiedBlueBadge(
                    showTooltipOnTap = true,
                    verifiedOrgName = "Cabinet Konan",
                    verificationCategory = "Professionnel"
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("verified_blue_badge").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Compte CERTIFICATION").assertIsDisplayed()
        composeTestRule.onNodeWithText("Catégorie : Professionnel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Entité confirmée : Cabinet Konan").assertIsDisplayed()
    }

    @Test
    fun smsLocalDeleteConfirmationDialog_enforcesDefaultSmsGateAndLocalCopyNotice() {
        var requestedRole = false
        var confirmedDelete = false

        // Case 1: Unauthorized (not default SMS app)
        composeTestRule.setContent {
            BravChatTheme {
                SmsLocalDeleteConfirmationDialog(
                    title = "Supprimer ce SMS ?",
                    message = "Ce message sera effacé uniquement sur votre appareil. Le destinataire conserve sa propre copie.",
                    isAuthorizedDefaultSms = false,
                    onRequestDefaultSms = { requestedRole = true },
                    onConfirmDelete = { confirmedDelete = true },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Autorisation requise", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("sms_delete_set_default_button").assertIsDisplayed().performClick()
        assertTrue(requestedRole)
        assertFalse(confirmedDelete)
    }

    @Test
    fun smsLocalDeleteConfirmationDialog_allowsDeleteWhenAuthorizedDefaultSms() {
        var confirmedDelete = false

        composeTestRule.setContent {
            BravChatTheme {
                SmsLocalDeleteConfirmationDialog(
                    title = "Supprimer ce SMS ?",
                    message = "Ce message sera effacé uniquement sur votre appareil. Le destinataire conserve sa propre copie.",
                    isAuthorizedDefaultSms = true,
                    onRequestDefaultSms = {},
                    onConfirmDelete = { confirmedDelete = true },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("destinataire conserve", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_local_sms_delete_button").assertIsDisplayed().performClick()
        assertTrue(confirmedDelete)
    }
}
