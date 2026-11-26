package com.example

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiAutoReplyManager
import com.example.ai.AutoReplyContactRule
import com.example.ai.ContactRuleMode
import com.example.crypto.E2eeCryptoManager
import com.example.launcher.BravLauncherScreen
import com.example.launcher.LauncherFolder
import com.example.launcher.LauncherIconShape
import com.example.launcher.LauncherJsonCodec
import com.example.launcher.LauncherRepository
import com.example.launcher.LauncherThemePalette
import com.example.launcher.LauncherThemeStudioScreen
import com.example.local.AppPreferencesRepository
import com.example.local.ThemeMode
import com.example.telecom.CallDirection
import com.example.telecom.CallerIdResolver
import com.example.ui.screens.computeSmsSegments
import com.example.ui.theme.BravChatTheme
import com.example.ui.theme.BravIvory
import com.example.ui.theme.SlateBackgroundDark
import com.example.ui.theme.SlateBackgroundLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
class LauncherCallAndE2eeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun twoAccountE2eeExchange_encryptsDecryptsAndDetectsTampering() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = E2eeCryptoManager(context)

        val uidA = "user_account_alpha_001"
        val uidB = "user_account_beta_002"
        val conversationId = "conv_alpha_beta"

        val pubA = crypto.getPublicKeyBase64(uidA)
        val pubB = crypto.getPublicKeyBase64(uidB)
        assertTrue(pubA.isNotBlank())
        assertTrue(pubB.isNotBlank())
        assertNotEquals(pubA, pubB)

        // Symmetric safety fingerprint verification between both accounts
        val fpA = crypto.computeSafetyFingerprint(pubA, pubB)
        val fpB = crypto.computeSafetyFingerprint(pubB, pubA)
        assertEquals(fpA, fpB)

        // Account A sends an E2EE message to Account B
        val messageFromA = "Bonjour Compte B ! Voici la formule LaTeX : \\int_0^1 x^2 dx = 1/3"
        val encryptedFromA = crypto.encryptForPeer(
            myUid = uidA,
            peerPublicKeyBase64 = pubB,
            contextId = conversationId,
            plaintext = messageFromA
        )
        assertNotEquals(messageFromA, encryptedFromA.ciphertextBase64)

        // Account B decrypts Account A's payload
        val decryptedByB = crypto.decryptFromPeer(
            myUid = uidB,
            peerPublicKeyBase64 = encryptedFromA.senderPublicKeyBase64,
            contextId = conversationId,
            ciphertextBase64 = encryptedFromA.ciphertextBase64,
            ivBase64 = encryptedFromA.ivBase64
        )
        assertEquals(messageFromA, decryptedByB)

        // Account B replies to Account A
        val replyFromB = "Bien reçu Compte A, vérifié à 100% !"
        val encryptedFromB = crypto.encryptForPeer(
            myUid = uidB,
            peerPublicKeyBase64 = pubA,
            contextId = conversationId,
            plaintext = replyFromB
        )
        val decryptedByA = crypto.decryptFromPeer(
            myUid = uidA,
            peerPublicKeyBase64 = encryptedFromB.senderPublicKeyBase64,
            contextId = conversationId,
            ciphertextBase64 = encryptedFromB.ciphertextBase64,
            ivBase64 = encryptedFromB.ivBase64
        )
        assertEquals(replyFromB, decryptedByA)

        // Wrong context or tampered ciphertext must fail authentication (GCM tag mismatch)
        val wrongContextResult = crypto.decryptFromPeer(
            myUid = uidB,
            peerPublicKeyBase64 = pubA,
            contextId = "other_conv_id",
            ciphertextBase64 = encryptedFromA.ciphertextBase64,
            ivBase64 = encryptedFromA.ivBase64
        )
        assertNull(wrongContextResult)

        // Group symmetric AES-GCM encryption between both accounts
        val groupKey = crypto.generateGroupKeyBase64()
        val (grpCipher, grpIv) = crypto.encryptWithSymmetricKey(groupKey, "Message de groupe chiffré")
        assertEquals("Message de groupe chiffré", crypto.decryptWithSymmetricKey(groupKey, grpCipher, grpIv))
    }

    @Test
    fun launcherModelsAndJsonCodecs_preserveUnifiedThemeAndFolders() {
        assertEquals(LauncherThemePalette.BRAV_VIOLET, LauncherThemePalette.fromId("unknown"))
        assertEquals(LauncherThemePalette.EMERALD_GLASS, LauncherThemePalette.fromId("EMERALD_GLASS"))
        assertEquals(LauncherIconShape.CIRCLE, LauncherIconShape.fromId("CIRCLE"))
        assertEquals(LauncherIconShape.SQUIRCLE, LauncherIconShape.fromId(null))

        val folders = listOf(
            LauncherFolder(
                id = "f1",
                name = "Social BRAV",
                packageNames = listOf("bravchat.com", "com.android.dialer")
            )
        )
        val rawFolders = LauncherJsonCodec.serializeFolders(folders)
        val parsedFolders = LauncherJsonCodec.parseFolders(rawFolders)
        assertEquals(1, parsedFolders.size)
        assertEquals("Social BRAV", parsedFolders.first().name)
        assertEquals(listOf("bravchat.com", "com.android.dialer"), parsedFolders.first().packageNames)

        val customIcons = mapOf("bravchat.com" to "/data/user/0/bravchat.com/files/custom_icons/icon.png")
        assertEquals(customIcons, LauncherJsonCodec.parseStringMap(LauncherJsonCodec.serializeStringMap(customIcons)))

        val dock = listOf("bravchat.com", "com.android.contacts", "com.android.camera2")
        assertEquals(dock, LauncherJsonCodec.parseStringList(LauncherJsonCodec.serializeStringList(dock)))
    }

    @Test
    fun incomingAndOutgoingCarrierCalls_resolveExactFixedCityLocality() {
        val outgoingYamoussoukro = CallerIdResolver.resolveFromKnownData(
            rawNumber = "+225 27 30 61 00 00",
            direction = CallDirection.OUTGOING,
            defaultCountryIso = "CI"
        )
        assertEquals(CallDirection.OUTGOING, outgoingYamoussoukro.direction)
        assertNotNull(outgoingYamoussoukro.cityBadge)
        assertTrue(outgoingYamoussoukro.cityBadge.contains("Yamoussoukro"))

        val incomingAbidjan = CallerIdResolver.resolveFromKnownData(
            rawNumber = "2721301122",
            direction = CallDirection.INCOMING,
            defaultCountryIso = "CI"
        )
        assertEquals(CallDirection.INCOMING, incomingAbidjan.direction)
        assertTrue(incomingAbidjan.cityBadge.contains("Grand-Bassam"))
    }

    @Test
    fun launcherComponentIsEnabledByDefault_andTogglesCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(LauncherRepository.isLauncherComponentEnabled(context))

        LauncherRepository.setLauncherComponentEnabled(context, false)
        assertFalse(LauncherRepository.isLauncherComponentEnabled(context))

        LauncherRepository.setLauncherComponentEnabled(context, true)
        assertTrue(LauncherRepository.isLauncherComponentEnabled(context))
    }

    @Test
    fun perConversationAiDotRules_overrideGlobalAutoReplyState() {
        val rulesJson = AiAutoReplyManager.serializeContactRules(
            listOf(
                AutoReplyContactRule(contactKey = "u_vip", contactLabel = "VIP", mode = ContactRuleMode.AUTO),
                AutoReplyContactRule(contactKey = "+2250701020304", contactLabel = "Muted", mode = ContactRuleMode.NEVER)
            )
        )

        // Explicit AUTO activates even if global auto-reply is OFF
        assertTrue(AiAutoReplyManager.hasExplicitAutoRule(listOf("u_vip"), rulesJson))
        assertTrue(AiAutoReplyManager.isDirectDotActive(listOf("u_vip"), rulesJson, globalAutoReplyEnabled = false))

        // Explicit NEVER suppresses even if global auto-reply is ON
        assertFalse(AiAutoReplyManager.hasExplicitAutoRule(listOf("+225 07 01 02 03 04"), rulesJson))
        assertFalse(AiAutoReplyManager.isDirectDotActive(listOf("+225 07 01 02 03 04"), rulesJson, globalAutoReplyEnabled = true))

        // No rule falls back to global setting
        assertTrue(AiAutoReplyManager.isDirectDotActive(listOf("unknown_peer"), rulesJson, globalAutoReplyEnabled = true))
        assertFalse(AiAutoReplyManager.isDirectDotActive(listOf("unknown_peer"), rulesJson, globalAutoReplyEnabled = false))
    }

    @Test
    fun computeSmsSegments_handlesGsmAndUnicodeCorrectly() {
        assertEquals(0 to 160, computeSmsSegments(""))
        assertEquals(1 to 160, computeSmsSegments("Bonjour"))
        assertEquals(2 to 306, computeSmsSegments("a".repeat(170)))
        assertEquals(2 to 134, computeSmsSegments("Bonjour \uD83D\uDE00 " + "b".repeat(75)))
    }

    @Test
    fun launcherHomeAppDrawerAndSystemBackground_workCleanly() {
        assertEquals("Blanc", ThemeMode.LIGHT.label)
        assertEquals("Sombre Noire", ThemeMode.DARK.label)
        assertEquals(BravIvory, SlateBackgroundLight)
        assertEquals(Color(0xFF100D16), SlateBackgroundDark)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferencesRepository(context)
        composeTestRule.setContent {
            BravChatTheme {
                BravLauncherScreen(prefsRepository = prefs)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("brav_launcher_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("launcher_home_wallpaper_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("launcher_open_app_drawer_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("brav_app_drawer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Menu des applications").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_drawer_search_input").assertIsDisplayed()
    }
}
