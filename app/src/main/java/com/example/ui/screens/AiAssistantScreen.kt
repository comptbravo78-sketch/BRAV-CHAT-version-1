package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.LatexMathTopic
import com.example.ai.MathVerificationSource
import com.example.export.DocTemplateType
import com.example.export.DocumentExportManager
import com.example.export.ExportSection
import com.example.export.ExportedDocumentResult
import com.example.network.WorkerAiModel
import com.example.network.WorkerDocumentResponse
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.components.LatexFormulaView
import com.example.ui.components.PlotSeriesChart
import com.example.ui.components.RichMathContentView
import com.example.ui.components.bravOutlinedTextFieldColors
import com.example.ui.components.containsMathFormatting
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravTheme
import com.example.ui.theme.StatusOnlineGreen
import com.example.util.DefaultSmsHelper
import com.example.viewmodel.AiAssistantMessage
import com.example.viewmodel.BravViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: BravViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val appPrefs by viewModel.appPrefs.collectAsStateWithLifecycle()
    val models by viewModel.aiModels.collectAsStateWithLifecycle()
    val messages by viewModel.aiMessages.collectAsStateWithLifecycle()
    val isThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()
    val mathResult by viewModel.mathResult.collectAsStateWithLifecycle()
    val mathAiExplanation by viewModel.mathAiExplanation.collectAsStateWithLifecycle()
    val isMathAiLoading by viewModel.isMathAiLoading.collectAsStateWithLifecycle()
    val savedFormulas by viewModel.savedFormulas.collectAsStateWithLifecycle()
    val autoReplyRules by viewModel.autoReplyRules.collectAsStateWithLifecycle()
    val pendingReplies by viewModel.pendingAutoReplies.collectAsStateWithLifecycle()
    val savedContacts by viewModel.savedContactUsers.collectAsStateWithLifecycle()
    val autoReplyLogs by viewModel.autoReplyLogs.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showModelPickerSheet by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var selectedMathTopic by remember(appPrefs.preferredMathTopic) {
        mutableStateOf(LatexMathTopic.fromId(appPrefs.preferredMathTopic))
    }
    var mathExpressionInput by remember { mutableStateOf(selectedMathTopic.sampleLatex) }
    var selectedTemplate by remember { mutableStateOf(DocTemplateType.RAPPORT) }
    var exportedDoc by remember { mutableStateOf<ExportedDocumentResult?>(null) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val src = exportedDoc?.file
        if (destUri != null && src != null) {
            DocumentExportManager.copyFileToUri(context, src, destUri)
                .onSuccess { viewModel.showMessage("Document enregistré") }
                .onFailure { viewModel.showMessage("Échec d'enregistrement") }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val activeModel = remember(models, appPrefs.preferredAiModel) {
        models.firstOrNull { it.id == appPrefs.preferredAiModel }
    }
    val activeModelShortLabel = remember(activeModel) {
        val raw = activeModel?.label?.substringBefore(" (")?.trim().orEmpty()
        when {
            raw.isBlank() || raw.contains("Auto", ignoreCase = true) -> "Auto"
            raw.length > 14 -> raw.take(13) + "…"
            else -> raw
        }
    }

    val quickPrompts = listOf(
        "Calculer une intégrale étape par étape" to "Calcule ∫₀^π (sin(x) + x²) dx étape par étape avec formule visuelle.",
        "Résoudre y'' - 5y' + 6y = 0" to "Résous l'équation différentielle y'' - 5y' + 6y = 0 étape par étape.",
        "Rapport structuré sur Fourier" to "Génère un rapport structuré sur les séries de Fourier avec les formules essentielles."
    )

    fun launchExport(
        pdf: Boolean,
        title: String,
        subtitle: String,
        sections: List<ExportSection>,
        prefix: String,
        template: DocTemplateType = selectedTemplate
    ) {
        if (sections.isEmpty()) {
            viewModel.showMessage("Aucun contenu à exporter")
            return
        }
        runCatching {
            if (pdf) {
                DocumentExportManager.exportToPdf(context, title, subtitle, sections, prefix, template)
            } else {
                DocumentExportManager.exportToDocx(context, title, subtitle, sections, prefix, template)
            }
        }.onSuccess { result ->
            exportedDoc = result
            viewModel.showMessage("${result.formatLabel} généré : ${result.file.name}")
        }.onFailure { err ->
            viewModel.showMessage(err.localizedMessage ?: "Erreur d'exportation")
        }
    }

    fun exportStructuredAiDoc(pdf: Boolean, doc: WorkerDocumentResponse) {
        val tpl = runCatching { DocTemplateType.valueOf(doc.templateType.uppercase()) }
            .getOrDefault(selectedTemplate)
        val section = ExportSection(
            heading = doc.title,
            topicBadge = tpl.displayName,
            verificationLabel = doc.model.substringAfterLast("/").ifBlank { "BRAV IA" },
            bodyText = doc.bodyMarkdown,
            tableRows = doc.tableRows,
            extraBlocks = doc.keyFormulasLatex.mapIndexed { idx, latex ->
                "Formule ${idx + 1}" to latex
            }
        )
        launchExport(
            pdf = pdf,
            title = doc.title,
            subtitle = doc.subtitle.ifBlank { tpl.displayName },
            sections = listOf(section),
            prefix = "brav_ia_${tpl.name.lowercase()}",
            template = tpl
        )
    }

    val lux = BravTheme.colors
    Scaffold(
        containerColor = lux.ivoryCanvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = lux.translucentCardSurface),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ai_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = {
                    Text("Assistant IA", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    if (messages.isNotEmpty() && selectedTab == 0) {
                        Box {
                            IconButton(
                                onClick = { showChatMenu = true },
                                modifier = Modifier.testTag("ai_chat_more_button")
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Options de conversation")
                            }
                            DropdownMenu(
                                expanded = showChatMenu,
                                onDismissRequest = { showChatMenu = false },
                                containerColor = lux.modalSurface
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Exporter PDF") },
                                    leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) },
                                    onClick = {
                                        showChatMenu = false
                                        val sections = messages.map { m ->
                                            ExportSection(
                                                heading = if (m.isUser) "Question" else "Réponse Assistant IA",
                                                topicBadge = m.model.substringAfterLast("/"),
                                                verificationLabel = m.attachedMath?.verificationSource?.labelFr.orEmpty(),
                                                latexText = m.attachedMath?.latexFormula.orEmpty(),
                                                chartPlot = m.attachedMath?.plotSeries,
                                                bodyText = m.text
                                            )
                                        }
                                        launchExport(true, "Conversation Assistant IA", "BRAV IA", sections, "brav_ai_chat")
                                    },
                                    modifier = Modifier.testTag("ai_export_chat_pdf_button")
                                )
                                DropdownMenuItem(
                                    text = { Text("Exporter Word") },
                                    leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                                    onClick = {
                                        showChatMenu = false
                                        val sections = messages.map { m ->
                                            ExportSection(
                                                heading = if (m.isUser) "Question" else "Réponse Assistant IA",
                                                topicBadge = m.model.substringAfterLast("/"),
                                                verificationLabel = m.attachedMath?.verificationSource?.labelFr.orEmpty(),
                                                latexText = m.attachedMath?.latexFormula.orEmpty(),
                                                chartPlot = m.attachedMath?.plotSeries,
                                                bodyText = m.text
                                            )
                                        }
                                        launchExport(false, "Conversation Assistant IA", "BRAV IA", sections, "brav_ai_chat")
                                    },
                                    modifier = Modifier.testTag("ai_export_chat_docx_button")
                                )
                                DropdownMenuItem(
                                    text = { Text("Effacer") },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        showChatMenu = false
                                        viewModel.clearAiChat()
                                    }
                                )
                            }
                        }
                    }

                    // Compact top-right model selector button
                    Surface(
                        onClick = { showModelPickerSheet = true },
                        shape = RoundedCornerShape(999.dp),
                        color = lux.translucentSubtleSurface,
                        border = BorderStroke(0.75.dp, lux.violetContour),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .heightIn(min = 36.dp)
                            .testTag("ai_model_picker_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = "Changer de modèle IA",
                                tint = BravPurple,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = activeModelShortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(lux.ivoryCanvas)
        ) {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.SOLAR_ADINKRA,
                variant = BravPatternVariant.GOLD_ON_IVORY,
                opacity = 0.095f,
                scale = 1.05f
            )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = lux.translucentCardSurface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Chat", maxLines = 1) },
                    modifier = Modifier.testTag("ai_tab_chat")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Maths", maxLines = 1) },
                    modifier = Modifier.testTag("ai_tab_math")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        val badge = if (pendingReplies.isNotEmpty()) " (${pendingReplies.size})" else ""
                        Text("Auto-IA$badge", maxLines = 1)
                    },
                    modifier = Modifier.testTag("ai_tab_auto")
                )
            }

            when (selectedTab) {
                0 -> {
                    if (isThinking) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.widthIn(max = 460.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(16.dp).size(32.dp)
                                    )
                                }
                                Text(
                                    text = "Comment puis-je vous aider ?",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    quickPrompts.forEach { (label, prompt) ->
                                        SuggestionChip(
                                            onClick = { viewModel.sendAiPrompt(prompt) },
                                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                AiChatBubble(
                                    message = msg,
                                    onCopy = {
                                        clipboard.setText(AnnotatedString(msg.text))
                                        viewModel.showMessage("Réponse copiée")
                                    },
                                    onExportPdf = {
                                        val doc = msg.attachedDocument
                                        if (doc != null) {
                                            exportStructuredAiDoc(true, doc)
                                        } else {
                                            launchExport(
                                                pdf = true,
                                                title = "Note Assistant IA",
                                                subtitle = msg.model.substringAfterLast("/").ifBlank { "BRAV IA" },
                                                sections = listOf(
                                                    ExportSection(
                                                        heading = "Réponse",
                                                        topicBadge = msg.model.substringAfterLast("/"),
                                                        verificationLabel = msg.attachedMath?.verificationSource?.labelFr.orEmpty(),
                                                        latexText = msg.attachedMath?.latexFormula.orEmpty(),
                                                        chartPlot = msg.attachedMath?.plotSeries,
                                                        bodyText = msg.text
                                                    )
                                                ),
                                                prefix = "brav_ai_note"
                                            )
                                        }
                                    },
                                    onExportDocx = {
                                        val doc = msg.attachedDocument
                                        if (doc != null) {
                                            exportStructuredAiDoc(false, doc)
                                        } else {
                                            launchExport(
                                                pdf = false,
                                                title = "Note Assistant IA",
                                                subtitle = msg.model.substringAfterLast("/").ifBlank { "BRAV IA" },
                                                sections = listOf(
                                                    ExportSection(
                                                        heading = "Réponse",
                                                        topicBadge = msg.model.substringAfterLast("/"),
                                                        verificationLabel = msg.attachedMath?.verificationSource?.labelFr.orEmpty(),
                                                        latexText = msg.attachedMath?.latexFormula.orEmpty(),
                                                        chartPlot = msg.attachedMath?.plotSeries,
                                                        bodyText = msg.text
                                                    )
                                                ),
                                                prefix = "brav_ai_note"
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Écrire un message…") },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            colors = bravOutlinedTextFieldColors(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_prompt_input")
                        )
                        IconButton(
                            enabled = inputText.isNotBlank() && !isThinking,
                            onClick = {
                                val prompt = inputText
                                inputText = ""
                                viewModel.sendAiPrompt(prompt)
                            },
                            modifier = Modifier.testTag("ai_send_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Envoyer",
                                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                1 -> {
                    AiMathStudioSection(
                        selectedTopic = selectedMathTopic,
                        expressionInput = mathExpressionInput,
                        mathResult = mathResult,
                        mathAiExplanation = mathAiExplanation,
                        isMathAiLoading = isMathAiLoading,
                        selectedTemplate = selectedTemplate,
                        onSelectTemplate = { selectedTemplate = it },
                        onSelectTopic = { topic ->
                            selectedMathTopic = topic
                            mathExpressionInput = topic.sampleLatex
                            scope.launch { viewModel.prefsRepository.setPreferredMathTopic(topic.name) }
                        },
                        onExpressionChange = { mathExpressionInput = it },
                        onComputeLocal = { viewModel.runMathComputation(mathExpressionInput, selectedMathTopic) },
                        onExplainWithCloudflare = {
                            viewModel.explainMathWithCloudflareAi(mathExpressionInput, selectedMathTopic)
                        },
                        onSaveFormula = { res ->
                            viewModel.saveFormulaRecord(res.operationLabel, res)
                        },
                        onExportPdf = { res, aiExp, tpl ->
                            launchExport(
                                pdf = true,
                                title = "Rapport Mathématique LaTeX",
                                subtitle = res.topic.displayName,
                                sections = listOf(DocumentExportManager.sectionFromMathResult(res, aiExp)),
                                prefix = "brav_latex_${res.topic.name.lowercase()}",
                                template = tpl
                            )
                        },
                        onExportDocx = { res, aiExp, tpl ->
                            launchExport(
                                pdf = false,
                                title = "Rapport Mathématique LaTeX",
                                subtitle = res.topic.displayName,
                                sections = listOf(DocumentExportManager.sectionFromMathResult(res, aiExp)),
                                prefix = "brav_latex_${res.topic.name.lowercase()}",
                                template = tpl
                            )
                        },
                        onCopyText = { text ->
                            clipboard.setText(AnnotatedString(text))
                            viewModel.showMessage("Copié dans le presse-papiers")
                        }
                    )
                }

                else -> {
                    AiAutomationAndFormulasSection(
                        autoReplySmsEnabled = appPrefs.aiAutoReplySmsEnabled,
                        autoReplyBravEnabled = appPrefs.aiAutoReplyBravEnabled,
                        validationRequired = appPrefs.aiAutoReplyValidationRequired,
                        selectedTone = appPrefs.aiAutoReplyTone,
                        autoReplyInstruction = appPrefs.aiAutoReplyInstruction,
                        hasSmsSendPermission = DefaultSmsHelper.hasSmsSendPermission(context),
                        autoReplyRules = autoReplyRules,
                        pendingReplies = pendingReplies,
                        savedContacts = savedContacts,
                        autoReplyLogs = autoReplyLogs,
                        savedFormulas = savedFormulas,
                        selectedTemplate = selectedTemplate,
                        onSelectTemplate = { selectedTemplate = it },
                        onToggleAutoReplySms = { enabled ->
                            scope.launch {
                                viewModel.prefsRepository.setAiAutoReplySms(enabled)
                                viewModel.showMessage(
                                    if (enabled) "Auto-réponse SMS activée" else "Auto-réponse SMS désactivée"
                                )
                            }
                        },
                        onToggleAutoReplyBrav = { enabled ->
                            scope.launch {
                                viewModel.prefsRepository.setAiAutoReplyBrav(enabled)
                                viewModel.showMessage(
                                    if (enabled) "Auto-réponse BRAV Chat activée" else "Auto-réponse BRAV Chat désactivée"
                                )
                            }
                        },
                        onToggleValidationRequired = { required ->
                            scope.launch {
                                viewModel.prefsRepository.setAiAutoReplyValidationRequired(required)
                            }
                        },
                        onSelectTone = { tone ->
                            scope.launch {
                                viewModel.prefsRepository.setAiAutoReplyTone(tone)
                            }
                        },
                        onSaveInstruction = { text ->
                            scope.launch {
                                viewModel.prefsRepository.setAiAutoReplyInstruction(text)
                                viewModel.showMessage("Consigne enregistrée")
                            }
                        },
                        onUpsertContactRule = { rule -> viewModel.upsertAutoReplyContactRule(rule) },
                        onDeleteContactRule = { key -> viewModel.deleteAutoReplyContactRule(key) },
                        onApprovePending = { id, edited -> viewModel.approvePendingAutoReply(id, edited) },
                        onDismissPending = { id -> viewModel.dismissPendingAutoReply(id) },
                        onLoadFormulaInStudio = { rec ->
                            selectedMathTopic = LatexMathTopic.fromId(rec.topicId)
                            mathExpressionInput = rec.inputExpression
                            viewModel.runMathComputation(rec.inputExpression, selectedMathTopic)
                            selectedTab = 1
                        },
                        onDeleteFormula = { id -> viewModel.deleteFormulaRecord(id) },
                        onExportAllFormulasPdf = { tpl ->
                            launchExport(
                                pdf = true,
                                title = "Cahier de Formules LaTeX",
                                subtitle = "${savedFormulas.size} formules",
                                sections = savedFormulas.map(DocumentExportManager::sectionFromFormulaRecord),
                                prefix = "brav_formules",
                                template = tpl
                            )
                        },
                        onExportAllFormulasDocx = { tpl ->
                            launchExport(
                                pdf = false,
                                title = "Cahier de Formules LaTeX",
                                subtitle = "${savedFormulas.size} formules",
                                sections = savedFormulas.map(DocumentExportManager::sectionFromFormulaRecord),
                                prefix = "brav_formules",
                                template = tpl
                            )
                        },
                        onExportSingleFormulaPdf = { rec, tpl ->
                            launchExport(
                                pdf = true,
                                title = rec.title,
                                subtitle = LatexMathTopic.fromId(rec.topicId).displayName,
                                sections = listOf(DocumentExportManager.sectionFromFormulaRecord(rec)),
                                prefix = "brav_formule",
                                template = tpl
                            )
                        },
                        onExportSingleFormulaDocx = { rec, tpl ->
                            launchExport(
                                pdf = false,
                                title = rec.title,
                                subtitle = LatexMathTopic.fromId(rec.topicId).displayName,
                                sections = listOf(DocumentExportManager.sectionFromFormulaRecord(rec)),
                                prefix = "brav_formule",
                                template = tpl
                            )
                        }
                    )
                }
            }
        }
        }
    }

    exportedDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { exportedDoc = null },
            containerColor = lux.modalSurface,
            title = { Text("Document ${doc.formatLabel} prêt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(doc.file.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Taille : ${(doc.file.length() / 1024.0).coerceAtLeast(0.1).let { String.format(java.util.Locale.FRANCE, "%.1f Ko", it) }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(DocumentExportManager.createViewIntent(context, doc))
                            }.onFailure {
                                viewModel.showMessage("Aucune visionneuse installée, utilisez Partager ou Enregistrer.")
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Ouvrir")
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(
                                        DocumentExportManager.createShareIntent(context, doc),
                                        "Partager ${doc.formatLabel}"
                                    )
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Partager")
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { saveFileLauncher.launch(doc.file.name) }) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Enregistrer")
                    }
                    TextButton(onClick = { exportedDoc = null }) {
                        Text("Fermer")
                    }
                }
            }
        )
    }

    if (showModelPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelPickerSheet = false },
            containerColor = lux.modalSurface
        ) {
            AiModelPickerSheetContent(
                models = models,
                selectedModelId = appPrefs.preferredAiModel,
                onSelectModel = { modelId ->
                    showModelPickerSheet = false
                    scope.launch {
                        viewModel.prefsRepository.setPreferredAiModel(modelId)
                    }
                }
            )
        }
    }
}

@Composable
internal fun AiModelPickerSheetContent(
    models: List<WorkerAiModel>,
    selectedModelId: String,
    onSelectModel: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Tous") }
    val categories = remember(models) {
        listOf("Tous") + models.map { it.category }.distinct()
    }
    val filtered = remember(models, selectedCategory) {
        if (selectedCategory == "Tous") models else models.filter { it.category == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
            .testTag("ai_model_picker_sheet"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Modèle IA",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it }) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered, key = { it.id }) { model ->
                val isSelected = selectedModelId == model.id
                val lux = BravTheme.colors
                Surface(
                    onClick = { onSelectModel(model.id) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    else lux.translucentSubtleSurface,
                    border = BorderStroke(
                        0.75.dp,
                        if (isSelected) lux.champagneGold else lux.violetContour
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_model_option_${model.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = model.label.substringBefore(" ("),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = model.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Sélectionné",
                                tint = BravPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiChatBubble(
    message: AiAssistantMessage,
    onCopy: () -> Unit,
    onExportPdf: () -> Unit,
    onExportDocx: () -> Unit
) {
    val lux = BravTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (message.isUser) 18.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 18.dp
            ),
            color = if (message.isUser) MaterialTheme.colorScheme.primary else lux.translucentCardSurface,
            border = if (message.isUser) null else BorderStroke(0.75.dp, lux.violetContour),
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!message.isUser || containsMathFormatting(message.text)) {
                    RichMathContentView(
                        text = message.text,
                        textColor = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                        accentColor = if (message.isUser) Color.White else MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = message.text,
                        color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                message.attachedMath?.let { math ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StatusOnlineGreen.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp), tint = StatusOnlineGreen)
                            Text(
                                text = MathVerificationSource.COMPUTED_LOCAL.labelFr,
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusOnlineGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    LatexFormulaView(
                        latex = math.latexFormula,
                        fallbackText = math.unicodeFormula,
                        useRichWebRenderer = true
                    )
                    math.plotSeries?.let { PlotSeriesChart(series = it) }
                }
                message.attachedDocument?.let { doc ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onExportPdf,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("PDF")
                        }
                        OutlinedButton(
                            onClick = onExportDocx,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Word")
                        }
                    }
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!message.isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message.model.substringAfterLast("/").ifBlank { "BRAV IA" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row {
                            IconButton(onClick = onExportPdf, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Rounded.PictureAsPdf,
                                    contentDescription = "PDF",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onExportDocx, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Rounded.Description,
                                    contentDescription = "Word",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = "Copier",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
