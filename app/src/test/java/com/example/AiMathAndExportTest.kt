package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiAutoReplyManager
import com.example.ai.AutoReplyContactRule
import com.example.ai.ContactRuleMode
import com.example.ai.LatexMathTopic
import com.example.ai.MathComputationEngine
import com.example.ai.MathComputationResult
import com.example.ai.MathVerificationSource
import com.example.export.DocTemplateType
import com.example.export.DocumentExportManager
import com.example.repository.AiTranslationRepository
import com.example.ui.components.ensureMathDelimiters
import com.example.ui.components.latexToReadableUnicode
import com.example.ui.screens.AiAutomationAndFormulasSection
import com.example.ui.screens.AiMathStudioSection
import com.example.ui.theme.BravChatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiMathAndExportTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun cloudflareModelsCatalog_includesAllFreeModels() {
        val models = AiTranslationRepository.defaultFreeModels
        assertTrue(models.size >= 15)
        assertTrue(models.all { it.id.startsWith("@cf/") })
        assertTrue(models.any { it.id.contains("llama-3.3-70b") })
        assertTrue(models.any { it.id.contains("deepseek-r1") })
        assertTrue(models.any { it.id.contains("qwen2.5-coder") })
        assertTrue(models.any { it.id.contains("mistral-7b") })
    }

    @Test
    fun universalLatexMathEngine_computesSymbolicAndNumericalLocally() {
        val diffRes = MathComputationEngine.compute("""\frac{d}{dx}(x^3 + 2x)""", LatexMathTopic.ANALYSE)
        assertTrue(diffRes.isSuccess)
        assertEquals(MathVerificationSource.COMPUTED_LOCAL, diffRes.verificationSource)
        assertTrue(diffRes.primaryResult.contains("3"))
        assertTrue(diffRes.primaryResult.contains("x^2"))

        val defInt = MathComputationEngine.compute("""\int_{0}^{\pi} \sin(x)\, dx""", LatexMathTopic.ANALYSE)
        assertTrue(defInt.isSuccess)
        assertEquals(MathVerificationSource.COMPUTED_LOCAL, defInt.verificationSource)
        assertEquals("2", defInt.primaryResult)
        assertNotNull(defInt.plotSeries)

        val limitRes = MathComputationEngine.compute("""\lim_{x \to 1} \frac{x^2 - 1}{x - 1}""", LatexMathTopic.ANALYSE)
        assertTrue(limitRes.isSuccess)
        assertEquals("2", limitRes.primaryResult)

        val sumRes = MathComputationEngine.compute("""\sum_{k=1}^{10} k^2""", LatexMathTopic.STATISTIQUES)
        assertTrue(sumRes.isSuccess)
        assertEquals("385", sumRes.primaryResult)

        val detRes = MathComputationEngine.compute("""\det\begin{pmatrix}4 & 2 \\ 1 & 3\end{pmatrix}""", LatexMathTopic.ALGEBRE_LINEAIRE)
        assertTrue(detRes.isSuccess)
        assertEquals("10", detRes.primaryResult)

        val odeRes = MathComputationEngine.compute("y'' - 5y' + 6y = 0", LatexMathTopic.EQUATIONS_DIFF)
        assertTrue(odeRes.isSuccess)
        assertTrue(odeRes.primaryResult.contains("e^(2x)") || odeRes.primaryResult.contains("e^(3x)"))
        assertTrue(odeRes.latexFormula.contains("e^{2x}") || odeRes.latexFormula.contains("e^{3x}"))

        val taylorRes = MathComputationEngine.compute("taylor(sin(x), x, 5)", LatexMathTopic.ANALYSE)
        assertTrue(taylorRes.isSuccess)
        assertTrue(taylorRes.primaryResult.contains("x"))
    }

    @Test
    fun autoReplyManager_enforcesContactRulesTonesSafetyAndAntiLoopGuards() {
        assertFalse(AiAutoReplyManager.shouldAutoReplyToSms("MTN", "Votre solde est...", nowMs = 1000L))
        assertFalse(AiAutoReplyManager.shouldAutoReplyToSms("+2250701020304", "Votre code OTP est 1234", nowMs = 1000L))
        assertFalse(AiAutoReplyManager.shouldAutoReplyToSms("+2250701020304", "[Auto-IA] Merci", nowMs = 1000L))
        assertTrue(AiAutoReplyManager.shouldAutoReplyToSms("+2250788990011", "Bonjour, es-tu disponible ?", nowMs = 10_000L))
        assertFalse(AiAutoReplyManager.shouldAutoReplyToSms("+2250788990011", "Allo ?", nowMs = 15_000L))

        val rulesJson = AiAutoReplyManager.serializeContactRules(
            listOf(
                AutoReplyContactRule("+2250700001111", "Patron", ContactRuleMode.VALIDATE, "Professionnel"),
                AutoReplyContactRule("+2250700002222", "Spam", ContactRuleMode.NEVER, ""),
                AutoReplyContactRule("uid_ami", "Ami", ContactRuleMode.AUTO, "Amical")
            )
        )
        val (bossMode, bossTone) = AiAutoReplyManager.resolveRuleForContact(
            contactCandidates = listOf("+225 07 00 00 11 11"),
            rulesJson = rulesJson,
            globalValidationRequired = false,
            defaultTone = "Concis"
        )
        assertEquals(ContactRuleMode.VALIDATE, bossMode)
        assertEquals("Professionnel", bossTone)

        val (blockedMode, _) = AiAutoReplyManager.resolveRuleForContact(
            contactCandidates = listOf("+2250700002222"),
            rulesJson = rulesJson,
            globalValidationRequired = false,
            defaultTone = "Concis"
        )
        assertEquals(ContactRuleMode.NEVER, blockedMode)

        val (friendMode, friendTone) = AiAutoReplyManager.resolveRuleForContact(
            contactCandidates = listOf("uid_ami"),
            rulesJson = rulesJson,
            globalValidationRequired = true,
            defaultTone = "Professionnel"
        )
        assertEquals(ContactRuleMode.AUTO, friendMode)
        assertEquals("Amical", friendTone)

        assertTrue(
            AiAutoReplyManager.isRepetitiveReply(
                "Je suis en réunion, je te rappelle dès que possible.",
                listOf("[Auto-IA] Je suis en réunion, je te rappelle dès que possible.")
            )
        )
        assertFalse(
            AiAutoReplyManager.isRepetitiveReply(
                "Bien reçu pour le document financier, je le valide ce soir.",
                listOf("Je suis en réunion, je te rappelle.")
            )
        )
    }

    @Test
    fun documentExportManager_generatesRichDocxWithChartHeaderFooterAndPdf() {
        val calc = MathComputationEngine.compute("""\int_{0}^{\pi} \sin(x)\, dx""", LatexMathTopic.ANALYSE)
        assertNotNull(calc.plotSeries)
        val chartPng = DocumentExportManager.renderChartPngBytes(calc.plotSeries!!)
        assertTrue(chartPng.size > 100)

        val section = DocumentExportManager.sectionFromMathResult(
            calc,
            "Aire sous la courbe sin(x) sur [0, π] égale à 2."
        )

        val docxBytes = DocumentExportManager.buildDocxBytes(
            title = "Rapport LaTeX BRAV Chat",
            subtitle = "Analyse & Graphiques",
            sections = listOf(section),
            template = DocTemplateType.MEMOIRE
        )
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes()
                entry = zis.nextEntry
            }
        }
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("word/_rels/document.xml.rels"))
        assertTrue(entries.containsKey("word/styles.xml"))
        assertTrue(entries.containsKey("word/header1.xml"))
        assertTrue(entries.containsKey("word/footer1.xml"))
        assertTrue(entries.containsKey("word/media/chart_1.png"))
        assertTrue(entries.containsKey("word/document.xml"))

        val docXmlBytes = entries.getValue("word/document.xml")
        val xmlDoc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(docXmlBytes))
        assertEquals("document", xmlDoc.documentElement.localName)
        val docText = String(docXmlBytes, Charsets.UTF_8)
        assertTrue(docText.contains("<w:tbl>"))
        assertTrue(docText.contains("Calcul intégral") || docText.contains("Analyse"))

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val pdfOut = DocumentExportManager.exportToPdf(
            context = ctx,
            title = "Rapport PDF LaTeX",
            subtitle = "Analyse réelle",
            sections = listOf(section),
            fileNamePrefix = "test_latex_math",
            template = DocTemplateType.FICHE_PEDAGOGIQUE
        )
        assertTrue(pdfOut.file.exists())
        assertTrue(pdfOut.file.length() > 400L)
        val header = pdfOut.file.inputStream().use { inp ->
            val buf = ByteArray(4)
            inp.read(buf)
            String(buf, Charsets.US_ASCII)
        }
        assertEquals("%PDF", header)
    }

    @Test
    fun mathStudioUi_computesLatexAndSavesFormulaWithReactiveState() {
        var computedResult: MathComputationResult? = null
        var savedCount = 0

        composeTestRule.setContent {
            var currentExpr by remember { mutableStateOf("""\sum_{k=1}^{10} k^2""") }
            var computed by remember { mutableStateOf<MathComputationResult?>(null) }
            var topic by remember { mutableStateOf(LatexMathTopic.STATISTIQUES) }
            var template by remember { mutableStateOf(DocTemplateType.RAPPORT) }
            BravChatTheme(darkTheme = false) {
                AiMathStudioSection(
                    selectedTopic = topic,
                    expressionInput = currentExpr,
                    mathResult = computed,
                    mathAiExplanation = "",
                    isMathAiLoading = false,
                    selectedTemplate = template,
                    onSelectTemplate = { template = it },
                    onSelectTopic = { topic = it },
                    onExpressionChange = { currentExpr = it },
                    onComputeLocal = {
                        val res = MathComputationEngine.compute(currentExpr, topic)
                        computed = res
                        computedResult = res
                    },
                    onExplainWithCloudflare = {},
                    onSaveFormula = { savedCount++ },
                    onExportPdf = { _, _, _ -> },
                    onExportDocx = { _, _, _ -> },
                    onCopyText = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("math_expression_input").performScrollTo().performTextReplacement("""\sum_{k=1}^{10} k^2""")
        composeTestRule.onNodeWithTag("math_compute_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("385", computedResult?.primaryResult)

        composeTestRule.onNodeWithTag("math_studio_list").performScrollToNode(hasTestTag("math_primary_result_text"))
        composeTestRule.onNodeWithTag("math_primary_result_text").performScrollTo().assertIsDisplayed().assertTextContains("385")
        composeTestRule.onNodeWithTag("math_studio_list").performScrollToNode(hasTestTag("math_save_formula_button"))
        composeTestRule.onNodeWithTag("math_save_formula_button").performScrollTo().performClick()
        assertEquals(1, savedCount)
    }

    @Test
    fun autoReplySwitchesUi_togglesSmsBravAndValidation() {
        var smsEnabled = false
        var bravEnabled = false
        var validationEnabled = false

        composeTestRule.setContent {
            var sms by remember { mutableStateOf(false) }
            var brav by remember { mutableStateOf(false) }
            var validate by remember { mutableStateOf(false) }
            var tone by remember { mutableStateOf("Professionnel") }
            var template by remember { mutableStateOf(DocTemplateType.RAPPORT) }
            BravChatTheme(darkTheme = false) {
                AiAutomationAndFormulasSection(
                    autoReplySmsEnabled = sms,
                    autoReplyBravEnabled = brav,
                    validationRequired = validate,
                    selectedTone = tone,
                    autoReplyInstruction = "Réponds poliment",
                    hasSmsSendPermission = true,
                    autoReplyRules = emptyList(),
                    pendingReplies = emptyList(),
                    savedContacts = emptyList(),
                    autoReplyLogs = emptyList(),
                    savedFormulas = emptyList(),
                    selectedTemplate = template,
                    onSelectTemplate = { template = it },
                    onToggleAutoReplySms = { sms = it; smsEnabled = it },
                    onToggleAutoReplyBrav = { brav = it; bravEnabled = it },
                    onToggleValidationRequired = { validate = it; validationEnabled = it },
                    onSelectTone = { tone = it },
                    onSaveInstruction = {},
                    onUpsertContactRule = {},
                    onDeleteContactRule = {},
                    onApprovePending = { _, _ -> },
                    onDismissPending = {},
                    onLoadFormulaInStudio = {},
                    onDeleteFormula = {},
                    onExportAllFormulasPdf = {},
                    onExportAllFormulasDocx = {},
                    onExportSingleFormulaPdf = { _, _ -> },
                    onExportSingleFormulaDocx = { _, _ -> }
                )
            }
        }
        composeTestRule.onNodeWithTag("ai_auto_reply_sms_switch").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("ai_auto_reply_brav_switch").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("ai_auto_reply_validation_switch").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(smsEnabled)
        assertTrue(bravEnabled)
        assertTrue(validationEnabled)
    }

    @Test
    fun strictFrenchGuard_stripsThinkTagsAndDetectsEnglishLeaks() {
        val rawWithThink = "<think>First calculate the integral in English...</think>Voici la démonstration en français : $$\\int x dx = \\frac{x^2}{2}$$"
        assertEquals(
            "Voici la démonstration en français : $$\\int x dx = \\frac{x^2}{2}$$",
            AiTranslationRepository.stripThinkingTags(rawWithThink)
        )

        assertTrue(
            AiTranslationRepository.looksPredominantlyEnglish(
                "Hello, here is the solution to the problem you asked with all the steps below."
            )
        )
        assertFalse(
            AiTranslationRepository.looksPredominantlyEnglish(
                "Voici la réponse complète en français avec l'équation $$\\frac{a+b}{2}$$."
            )
        )
    }

    @Test
    fun visualMathFormatter_eliminatesRawLatexAndFormatsReadableMath() {
        val rawVec = """\vec{u} + \vec{v}"""
        val delimitedVec = ensureMathDelimiters(rawVec)
        assertTrue(delimitedVec.contains("$"))

        val readableFrac = latexToReadableUnicode("""\frac{a+b}{c}""")
        assertFalse(readableFrac.contains("\\frac"))
        assertEquals("(a+b)/c", readableFrac)

        val readableSqrt = latexToReadableUnicode("""\sqrt{x^2+y^2}""")
        assertFalse(readableSqrt.contains("\\sqrt"))
        assertTrue(readableSqrt.startsWith("√("))

        val readableDet = latexToReadableUnicode("""\begin{vmatrix} 4 & 2 \\ 1 & 3 \end{vmatrix}""")
        assertFalse(readableDet.contains("\\begin"))
        assertTrue(readableDet.contains("|"))
    }

    @Test
    fun aiModelPickerSheet_selectsModelAndHidesProviderBranding() {
        var selectedId = "auto"
        val models = AiTranslationRepository.defaultFreeModels

        composeTestRule.setContent {
            BravChatTheme(darkTheme = false) {
                com.example.ui.screens.AiModelPickerSheetContent(
                    models = models,
                    selectedModelId = selectedId,
                    onSelectModel = { selectedId = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ai_model_picker_sheet").assertIsDisplayed()
        val targetModel = models.first { it.id != "auto" }
        composeTestRule.onNodeWithTag("ai_model_option_${targetModel.id}").performScrollTo().performClick()
        assertEquals(targetModel.id, selectedId)
    }
}
