package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.LatexMathTopic
import com.example.ai.MathComputationResult
import com.example.ai.MathVerificationSource
import com.example.export.DocTemplateType
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedCard
import com.example.ui.components.LatexFormulaView
import com.example.ui.components.LatexSymbolPalette
import com.example.ui.components.PlotSeriesChart
import com.example.ui.components.RichMathContentView
import com.example.ui.components.bravOutlinedTextFieldColors
import com.example.ui.components.latexToReadableUnicode
import com.example.ui.theme.BravTheme
import com.example.ui.theme.StatusOnlineGreen

@Composable
fun AiMathStudioSection(
    selectedTopic: LatexMathTopic,
    expressionInput: String,
    mathResult: MathComputationResult?,
    mathAiExplanation: String,
    isMathAiLoading: Boolean,
    selectedTemplate: DocTemplateType,
    onSelectTemplate: (DocTemplateType) -> Unit,
    onSelectTopic: (LatexMathTopic) -> Unit,
    onExpressionChange: (String) -> Unit,
    onComputeLocal: () -> Unit,
    onExplainWithCloudflare: () -> Unit,
    onSaveFormula: (MathComputationResult) -> Unit,
    onExportPdf: (MathComputationResult, String, DocTemplateType) -> Unit,
    onExportDocx: (MathComputationResult, String, DocTemplateType) -> Unit,
    onCopyText: (String) -> Unit
) {
    val lux = BravTheme.colors
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("math_studio_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(LatexMathTopic.entries, key = { it.name }) { topic ->
                    FilterChip(
                        selected = selectedTopic == topic,
                        onClick = { onSelectTopic(topic) },
                        label = { Text(topic.displayName, maxLines = 1) },
                        modifier = Modifier.testTag("math_topic_${topic.name.lowercase()}")
                    )
                }
            }
        }

        item {
            OutlinedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = lux.translucentCardSurface),
                border = BorderStroke(0.85.dp, lux.violetContour)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Functions, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Calcul & Formules",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(onClick = { onExpressionChange(selectedTopic.sampleLatex) }) {
                            Text("Exemple")
                        }
                    }

                    LatexSymbolPalette(
                        currentExpression = expressionInput,
                        onExpressionChange = onExpressionChange
                    )

                    OutlinedTextField(
                        value = expressionInput,
                        onValueChange = onExpressionChange,
                        label = { Text("Expression LaTeX (${selectedTopic.displayName})") },
                        placeholder = { Text(selectedTopic.sampleLatex) },
                        minLines = 2,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp),
                        colors = bravOutlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("math_expression_input")
                    )

                    if (expressionInput.isNotBlank()) {
                        LatexFormulaView(
                            latex = expressionInput,
                            useRichWebRenderer = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onComputeLocal,
                            enabled = expressionInput.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("math_compute_button")
                        ) {
                            Icon(Icons.Rounded.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Calculer")
                        }
                        OutlinedButton(
                            onClick = onExplainWithCloudflare,
                            enabled = expressionInput.isNotBlank() && !isMathAiLoading,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("math_cloudflare_explain_button")
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Analyse IA")
                        }
                    }

                    if (isMathAiLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        mathResult?.let { res ->
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (res.isSuccess) lux.translucentCardSurface else MaterialTheme.colorScheme.errorContainer
                    ),
                    border = BorderStroke(0.85.dp, if (res.isSuccess) lux.violetContour else MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    modifier = Modifier.testTag("math_result_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = res.operationLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (res.verificationSource == MathVerificationSource.COMPUTED_LOCAL) {
                                        StatusOnlineGreen.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                                    modifier = Modifier.testTag("math_verification_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (res.verificationSource == MathVerificationSource.COMPUTED_LOCAL) {
                                                Icons.Rounded.CheckCircle
                                            } else {
                                                Icons.Rounded.AutoAwesome
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = if (res.verificationSource == MathVerificationSource.COMPUTED_LOCAL) {
                                                StatusOnlineGreen
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        )
                                        Text(
                                            text = res.verificationSource.labelFr,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (res.verificationSource == MathVerificationSource.COMPUTED_LOCAL) {
                                                StatusOnlineGreen
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    }
                                }
                            }
                            Row {
                                IconButton(onClick = { onCopyText(res.latexFormula.ifBlank { res.primaryResult }) }) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copier résultat")
                                }
                                if (res.isSuccess) {
                                    IconButton(
                                        onClick = { onSaveFormula(res) },
                                        modifier = Modifier.testTag("math_save_formula_button")
                                    ) {
                                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = "Sauvegarder la formule")
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = latexToReadableUnicode(res.primaryResult),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("math_primary_result_text")
                                )
                                LatexFormulaView(
                                    latex = res.latexFormula.ifBlank { res.primaryResult },
                                    fallbackText = res.unicodeFormula.ifBlank { res.primaryResult },
                                    useRichWebRenderer = true
                                )
                            }
                        }

                        res.plotSeries?.let { series ->
                            PlotSeriesChart(series = series)
                        }

                        if (res.steps.isNotEmpty()) {
                            Text("Étapes :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            res.steps.forEachIndexed { idx, step ->
                                Text("${idx + 1}. ${latexToReadableUnicode(step)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (res.relatedFormulas.isNotEmpty()) {
                            HorizontalDivider()
                            res.relatedFormulas.forEach { (label, latex) ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    LatexFormulaView(latex = latex, useRichWebRenderer = true)
                                }
                            }
                        }

                        if (mathAiExplanation.isNotBlank()) {
                            HorizontalDivider()
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "Explication IA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            RichMathContentView(
                                text = mathAiExplanation,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                accentColor = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (res.isSuccess) {
                            HorizontalDivider()
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(DocTemplateType.entries, key = { it.name }) { tpl ->
                                    FilterChip(
                                        selected = selectedTemplate == tpl,
                                        onClick = { onSelectTemplate(tpl) },
                                        label = { Text(tpl.displayName, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onExportPdf(res, mathAiExplanation, selectedTemplate) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("math_export_pdf_button")
                                ) {
                                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("PDF")
                                }
                                OutlinedButton(
                                    onClick = { onExportDocx(res, mathAiExplanation, selectedTemplate) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("math_export_docx_button")
                                ) {
                                    Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Word (.docx)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
