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
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.ai.AiAutoReplyManager
import com.example.ai.AutoReplyContactRule
import com.example.ai.AutoReplyLogEntry
import com.example.ai.ContactRuleMode
import com.example.ai.LatexMathTopic
import com.example.ai.MathVerificationSource
import com.example.ai.PendingAutoReply
import com.example.export.DocTemplateType
import com.example.export.FormulaRecord
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedCard
import com.example.model.User
import com.example.ui.components.LatexFormulaView
import com.example.ui.components.bravOutlinedTextFieldColors
import com.example.ui.theme.BravTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiAutomationAndFormulasSection(
    autoReplySmsEnabled: Boolean,
    autoReplyBravEnabled: Boolean,
    validationRequired: Boolean,
    selectedTone: String,
    autoReplyInstruction: String,
    hasSmsSendPermission: Boolean,
    autoReplyRules: List<AutoReplyContactRule>,
    pendingReplies: List<PendingAutoReply>,
    savedContacts: List<User>,
    autoReplyLogs: List<AutoReplyLogEntry>,
    savedFormulas: List<FormulaRecord>,
    selectedTemplate: DocTemplateType,
    onSelectTemplate: (DocTemplateType) -> Unit,
    onToggleAutoReplySms: (Boolean) -> Unit,
    onToggleAutoReplyBrav: (Boolean) -> Unit,
    onToggleValidationRequired: (Boolean) -> Unit,
    onSelectTone: (String) -> Unit,
    onSaveInstruction: (String) -> Unit,
    onUpsertContactRule: (AutoReplyContactRule) -> Unit,
    onDeleteContactRule: (String) -> Unit,
    onApprovePending: (String, String?) -> Unit,
    onDismissPending: (String) -> Unit,
    onLoadFormulaInStudio: (FormulaRecord) -> Unit,
    onDeleteFormula: (String) -> Unit,
    onExportAllFormulasPdf: (DocTemplateType) -> Unit,
    onExportAllFormulasDocx: (DocTemplateType) -> Unit,
    onExportSingleFormulaPdf: (FormulaRecord, DocTemplateType) -> Unit,
    onExportSingleFormulaDocx: (FormulaRecord, DocTemplateType) -> Unit
) {
    var draftInstruction by remember(autoReplyInstruction) { mutableStateOf(autoReplyInstruction) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var editingPending by remember { mutableStateOf<PendingAutoReply?>(null) }
    var editPendingDraft by remember { mutableStateOf("") }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }
    val lux = BravTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (pendingReplies.isNotEmpty()) {
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
                    ),
                    border = BorderStroke(0.85.dp, lux.violetContour)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Validation avant envoi (${pendingReplies.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        pendingReplies.forEach { pending ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = lux.translucentCardSurface,
                                border = BorderStroke(0.75.dp, lux.violetContour),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${pending.channel} → ${pending.senderName.ifBlank { pending.target }}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text("Reçu : ${pending.incomingPreview}", style = MaterialTheme.typography.labelSmall)
                                    Text("Proposition IA : ${pending.suggestedReply}", style = MaterialTheme.typography.bodySmall)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = { onApprovePending(pending.id, null) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("pending_approve_${pending.id}")
                                        ) {
                                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text("Envoyer")
                                        }
                                        IconButton(
                                            onClick = {
                                                editingPending = pending
                                                editPendingDraft = pending.suggestedReply
                                            }
                                        ) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "Modifier")
                                        }
                                        IconButton(onClick = { onDismissPending(pending.id) }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Ignorer")
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Réponses automatiques IA",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Auto-réponse SMS", fontWeight = FontWeight.Medium)
                                if (!hasSmsSendPermission) {
                                    Text(
                                        text = "Permission SMS requise",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        Switch(
                            checked = autoReplySmsEnabled,
                            onCheckedChange = onToggleAutoReplySms,
                            modifier = Modifier.testTag("ai_auto_reply_sms_switch")
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Auto-réponse BRAV Chat", fontWeight = FontWeight.Medium)
                        }
                        Switch(
                            checked = autoReplyBravEnabled,
                            onCheckedChange = onToggleAutoReplyBrav,
                            modifier = Modifier.testTag("ai_auto_reply_brav_switch")
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Valider avant envoi", fontWeight = FontWeight.Medium)
                        }
                        Switch(
                            checked = validationRequired,
                            onCheckedChange = onToggleValidationRequired,
                            modifier = Modifier.testTag("ai_auto_reply_validation_switch")
                        )
                    }

                    Text("Ton des réponses", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AiAutoReplyManager.availableTones, key = { it }) { tone ->
                            FilterChip(
                                selected = selectedTone.equals(tone, ignoreCase = true),
                                onClick = { onSelectTone(tone) },
                                label = { Text(tone) },
                                modifier = Modifier.testTag("tone_chip_${tone.lowercase()}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = draftInstruction,
                        onValueChange = { draftInstruction = it },
                        label = { Text("Consigne personnalisée") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        colors = bravOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { onSaveInstruction(draftInstruction) }) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Enregistrer")
                        }
                    }

                    if (autoReplyLogs.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = "Historique récent (${autoReplyLogs.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        autoReplyLogs.take(5).forEach { log ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = lux.translucentSubtleSurface,
                                border = BorderStroke(0.65.dp, lux.violetContour),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "${log.channel} → ${log.target} (${timeFmt.format(Date(log.timestampMs))})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text("Reçu : ${log.incomingPreview}", style = MaterialTheme.typography.labelSmall)
                                    Text("Réponse IA : ${log.replyText}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Règles par contact (${autoReplyRules.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { showAddRuleDialog = true },
                            modifier = Modifier.testTag("add_autoreply_rule_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Ajouter")
                        }
                    }

                    if (autoReplyRules.isEmpty()) {
                        Text(
                            text = "Aucune règle spécifique.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        autoReplyRules.forEach { rule ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = lux.translucentSubtleSurface,
                                border = BorderStroke(0.65.dp, lux.violetContour),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rule.contactLabel, fontWeight = FontWeight.SemiBold)
                                        val details = buildList {
                                            add(rule.mode.labelFr)
                                            if (rule.toneOverride.isNotBlank()) add("Ton : ${rule.toneOverride}")
                                        }.joinToString(" • ")
                                        Text(
                                            text = details,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { onDeleteContactRule(rule.contactKey) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer la règle")
                                    }
                                }
                            }
                        }
                    }
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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Functions, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Cahier LaTeX & Exports (${savedFormulas.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                        Button(
                            onClick = { onExportAllFormulasPdf(selectedTemplate) },
                            enabled = savedFormulas.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_all_formulas_pdf_button")
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Exporter PDF")
                        }
                        OutlinedButton(
                            onClick = { onExportAllFormulasDocx(selectedTemplate) },
                            enabled = savedFormulas.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_all_formulas_docx_button")
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Word (.docx)")
                        }
                    }
                }
            }
        }

        if (savedFormulas.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = lux.translucentSubtleSurface,
                    border = BorderStroke(0.75.dp, lux.violetContour),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aucune formule LaTeX sauvegardée.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(savedFormulas, key = { it.id }) { formula ->
                val topic = LatexMathTopic.fromId(formula.topicId)
                val verif = runCatching { MathVerificationSource.valueOf(formula.verificationSource) }
                    .getOrDefault(MathVerificationSource.COMPUTED_LOCAL)
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = lux.translucentCardSurface),
                    border = BorderStroke(0.8.dp, lux.violetContour)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(formula.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${topic.displayName} • ${verif.labelFr}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onDeleteFormula(formula.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer la formule")
                            }
                        }
                        LatexFormulaView(
                            latex = formula.latexFormula.ifBlank { formula.inputExpression },
                            fallbackText = formula.unicodeFormula.ifBlank { formula.primaryResult },
                            useRichWebRenderer = false
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(onClick = { onLoadFormulaInStudio(formula) }) {
                                Text("Recharger")
                            }
                            TextButton(onClick = { onExportSingleFormulaPdf(formula, selectedTemplate) }) {
                                Text("PDF")
                            }
                            TextButton(onClick = { onExportSingleFormulaDocx(formula, selectedTemplate) }) {
                                Text("Word")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddRuleDialog) {
        var contactKey by remember { mutableStateOf("") }
        var contactLabel by remember { mutableStateOf("") }
        var ruleMode by remember { mutableStateOf(ContactRuleMode.AUTO) }
        var toneOverride by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            containerColor = lux.modalSurface,
            title = { Text("Règle par contact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (savedContacts.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(savedContacts.take(8), key = { it.userId }) { user ->
                                val candidateKey = user.username.ifBlank { user.userId }
                                FilterChip(
                                    selected = contactKey == candidateKey,
                                    onClick = {
                                        contactKey = candidateKey
                                        contactLabel = user.displayName.ifBlank { user.username }
                                    },
                                    label = { Text(user.displayName.ifBlank { user.username }) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = contactKey,
                        onValueChange = { contactKey = it },
                        label = { Text("Numéro ou nom du contact") },
                        singleLine = true,
                        colors = bravOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contactLabel,
                        onValueChange = { contactLabel = it },
                        label = { Text("Libellé (optionnel)") },
                        singleLine = true,
                        colors = bravOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ContactRuleMode.entries, key = { it.name }) { m ->
                            FilterChip(
                                selected = ruleMode == m,
                                onClick = { ruleMode = m },
                                label = { Text(m.labelFr, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = toneOverride.isBlank(),
                                onClick = { toneOverride = "" },
                                label = { Text("Ton global") }
                            )
                        }
                        items(AiAutoReplyManager.availableTones, key = { it }) { t ->
                            FilterChip(
                                selected = toneOverride == t,
                                onClick = { toneOverride = t },
                                label = { Text(t, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = contactKey.isNotBlank(),
                    onClick = {
                        val key = contactKey.trim()
                        onUpsertContactRule(
                            AutoReplyContactRule(
                                contactKey = key,
                                contactLabel = contactLabel.trim().ifBlank { key },
                                mode = ruleMode,
                                toneOverride = toneOverride
                            )
                        )
                        showAddRuleDialog = false
                    }
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    editingPending?.let { pending ->
        AlertDialog(
            onDismissRequest = { editingPending = null },
            containerColor = lux.modalSurface,
            title = { Text("Modifier la réponse IA") },
            text = {
                OutlinedTextField(
                    value = editPendingDraft,
                    onValueChange = { editPendingDraft = it },
                    minLines = 3,
                    maxLines = 6,
                    colors = bravOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = editPendingDraft.isNotBlank(),
                    onClick = {
                        onApprovePending(pending.id, editPendingDraft)
                        editingPending = null
                    }
                ) {
                    Text("Envoyer")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPending = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}
