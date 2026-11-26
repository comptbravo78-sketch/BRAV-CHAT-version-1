package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Contact
import com.example.model.User
import com.example.network.WorkerAiReply
import com.example.repository.SmsThreadSummary
import com.example.ui.components.AiReplyDotButton
import com.example.ui.components.AiReplySuggestionBar
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravGoldHairlineDivider
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.components.bravOutlinedTextFieldColors
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravTheme
import com.example.util.PhoneLocalityResolver
import com.example.util.PhoneUtils
import kotlinx.coroutines.launch

data class SmsContactCandidate(
    val displayName: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val localityBadge: String
)

internal fun computeSmsSegments(body: String): Pair<Int, Int> {
    if (body.isEmpty()) return 0 to 160
    val isGsmSafe = body.all { ch ->
        ch.code <= 127 || ch in "àäéèêëìïòöùüç£¥€@"
    }
    val singleLimit = if (isGsmSafe) 160 else 70
    val multiLimit = if (isGsmSafe) 153 else 67
    val count = if (body.length <= singleLimit) 1 else ((body.length - 1) / multiLimit) + 1
    return count to if (count <= 1) singleLimit else multiLimit * count
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSmsComposerSheet(
    initialRecipient: String = "",
    initialBody: String = "",
    phoneContacts: List<Contact>,
    savedUsers: List<User>,
    existingThreads: List<SmsThreadSummary>,
    onGenerateAiDraft: suspend (recipient: String, hint: String) -> Result<WorkerAiReply>,
    onOpenExistingThread: (SmsThreadSummary) -> Unit,
    onSendSms: (recipient: String, body: String) -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val scope = rememberCoroutineScope()
    var recipientQuery by remember(initialRecipient) { mutableStateOf(initialRecipient) }
    var selectedContactName by remember(initialRecipient) { mutableStateOf<String?>(null) }
    var bodyText by remember(initialBody) { mutableStateOf(initialBody) }
    var numericKeyboard by remember { mutableStateOf(false) }
    var isGeneratingAi by remember { mutableStateOf(false) }
    var aiSuggestion by remember { mutableStateOf<Pair<String, String>?>(null) }

    val allCandidates = remember(phoneContacts, existingThreads) {
        val seen = LinkedHashMap<String, SmsContactCandidate>()
        phoneContacts.forEach { c ->
            val raw = c.rawPhoneNumber.ifBlank { c.normalizedPhoneNumber }
            val norm = PhoneUtils.normalize(raw).ifBlank { raw }
            if (norm.isNotBlank() && !seen.containsKey(norm)) {
                seen[norm] = SmsContactCandidate(
                    displayName = c.preferredDisplayName.ifBlank { raw },
                    phoneNumber = raw,
                    normalizedNumber = norm,
                    localityBadge = PhoneLocalityResolver.resolve(norm).compactBadge
                )
            }
        }
        existingThreads.forEach { t ->
            val raw = t.address
            val norm = t.normalizedAddress.ifBlank { PhoneUtils.normalize(raw) }.ifBlank { raw }
            if (norm.isNotBlank() && !seen.containsKey(norm)) {
                seen[norm] = SmsContactCandidate(
                    displayName = t.displayName.ifBlank { raw },
                    phoneNumber = raw,
                    normalizedNumber = norm,
                    localityBadge = t.localityHint.ifBlank { PhoneLocalityResolver.resolve(norm).compactBadge }
                )
            }
        }
        seen.values.toList()
    }

    val filteredCandidates = remember(allCandidates, recipientQuery) {
        val q = recipientQuery.trim()
        val qDigits = q.filter { it.isDigit() }
        if (q.isBlank()) {
            allCandidates.take(25)
        } else {
            allCandidates.filter { c ->
                c.displayName.contains(q, ignoreCase = true) ||
                    c.phoneNumber.contains(q, ignoreCase = true) ||
                    (qDigits.isNotBlank() && c.normalizedNumber.contains(qDigits))
            }.take(25)
        }
    }

    val activeNorm = remember(recipientQuery) { PhoneUtils.normalize(recipientQuery) }
    val resolvedLocality = remember(recipientQuery) { PhoneLocalityResolver.resolve(recipientQuery) }
    val matchingThread = remember(existingThreads, activeNorm, recipientQuery) {
        if (recipientQuery.isBlank()) null
        else existingThreads.firstOrNull { thread ->
            (activeNorm.isNotBlank() && thread.normalizedAddress == activeNorm) ||
                PhoneUtils.matches(thread.address, recipientQuery)
        }
    }

    val (segmentCount, segmentMax) = remember(bodyText) { computeSmsSegments(bodyText) }
    val canSend = recipientQuery.isNotBlank() && bodyText.isNotBlank()

    fun triggerAiDraft() {
        if (isGeneratingAi) return
        isGeneratingAi = true
        val label = selectedContactName ?: matchingThread?.displayName ?: recipientQuery.ifBlank { "Contact" }
        scope.launch {
            val res = onGenerateAiDraft(recipientQuery, bodyText)
            isGeneratingAi = false
            res.onSuccess { reply ->
                aiSuggestion = reply.reply to reply.model
            }.onFailure { err ->
                onShowMessage(err.localizedMessage ?: "Assistant IA indisponible pour $label")
            }
        }
    }

    val lux = BravTheme.colors
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = lux.ivoryCanvas
    ) {
        Scaffold(
            containerColor = lux.ivoryCanvas,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = lux.translucentCardSurface
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("new_sms_close_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Fermer")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = "Nouveau SMS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (resolvedLocality.compactBadge.isNotBlank()) {
                                Text(
                                    text = resolvedLocality.compactBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BravPurple,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { numericKeyboard = !numericKeyboard },
                            modifier = Modifier.testTag("new_sms_toggle_keypad")
                        ) {
                            Icon(
                                if (numericKeyboard) Icons.Rounded.Person else Icons.Rounded.Dialpad,
                                contentDescription = if (numericKeyboard) "Clavier texte" else "Clavier numérique",
                                tint = BravPurple
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(lux.ivoryCanvas)
            ) {
                BravAfricanPatternCanvas(
                    pattern = BravPatternType.KENTE_DIAMOND,
                    variant = BravPatternVariant.GOLD_ON_IVORY,
                    opacity = 0.095f,
                    scale = 1.05f
                )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .animateContentSize()
            ) {
                // Sleek Recipient Input Bar
                Surface(
                    color = lux.translucentCardSurface,
                    border = BorderStroke(0.75.dp, lux.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "À",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = BravPurple
                            )
                            if (selectedContactName != null) {
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        selectedContactName = null
                                        recipientQuery = ""
                                    },
                                    label = {
                                        Text(
                                            text = "$selectedContactName • $recipientQuery",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(Icons.Rounded.Close, contentDescription = "Changer", modifier = Modifier.size(16.dp))
                                    },
                                    colors = InputChipDefaults.inputChipColors(
                                        selectedContainerColor = BravPurple.copy(alpha = 0.14f),
                                        selectedLabelColor = BravPurple
                                    )
                                )
                            } else {
                                OutlinedTextField(
                                    value = recipientQuery,
                                    onValueChange = { recipientQuery = it },
                                    placeholder = { Text("Nom ou numéro...") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = if (numericKeyboard) KeyboardType.Phone else KeyboardType.Text
                                    ),
                                    trailingIcon = {
                                        if (recipientQuery.isNotEmpty()) {
                                            IconButton(onClick = { recipientQuery = "" }) {
                                                Icon(Icons.Rounded.Close, contentDescription = "Effacer")
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = bravOutlinedTextFieldColors(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("new_sms_recipient_input")
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = resolvedLocality.compactBadge.isNotBlank() || matchingThread != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (resolvedLocality.compactBadge.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = BravPurple.copy(alpha = 0.10f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.LocationOn,
                                                contentDescription = null,
                                                tint = BravPurple,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = resolvedLocality.compactBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = BravPurple
                                            )
                                        }
                                    }
                                }
                                if (matchingThread != null) {
                                    Surface(
                                        onClick = { onOpenExistingThread(matchingThread) },
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.testTag("new_sms_open_existing_thread")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Rounded.Sms, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Text(
                                                text = "Ouvrir le fil",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Recent Threads Pills when recipient not locked
                if (selectedContactName == null && recipientQuery.isBlank() && existingThreads.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(existingThreads.take(8), key = { it.threadKey }) { thread ->
                            Surface(
                                onClick = {
                                    selectedContactName = thread.displayName
                                    recipientQuery = thread.address
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = lux.translucentSubtleSurface,
                                border = BorderStroke(0.75.dp, lux.violetContour)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(BravPurple.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = thread.displayName.firstOrNull()?.uppercase() ?: "#",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = BravPurple
                                        )
                                    }
                                    Text(
                                        text = thread.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Contacts Autocomplete List
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (selectedContactName == null) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            if (recipientQuery.any { it.isDigit() } &&
                                filteredCandidates.none { PhoneUtils.matches(it.phoneNumber, recipientQuery) }
                            ) {
                                item(key = "direct_number_option") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedContactName = recipientQuery.trim() }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = BravPurple.copy(alpha = 0.14f),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Rounded.Dialpad, contentDescription = null, tint = BravPurple)
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Envoyer à ${recipientQuery.trim()}",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (resolvedLocality.compactBadge.isNotBlank()) {
                                                Text(
                                                    text = resolvedLocality.compactBadge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = BravPurple
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 72.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    )
                                }
                            }

                            items(filteredCandidates, key = { it.normalizedNumber }) { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedContactName = contact.displayName
                                            recipientQuery = contact.phoneNumber
                                        }
                                        .padding(horizontal = 16.dp, vertical = 11.dp)
                                        .testTag("new_sms_contact_${contact.normalizedNumber}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = BravPurple.copy(alpha = 0.12f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = contact.displayName.firstOrNull()?.uppercase() ?: "?",
                                                color = BravPurple,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = contact.phoneNumber,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (contact.localityBadge.isNotBlank()) {
                                                Text(
                                                    text = "• ${contact.localityBadge}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = BravPurple,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 72.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                                )
                            }
                        }
                    } else if (matchingThread != null) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(matchingThread.messages.takeLast(8), key = { "${it.id}_${it.timestampMs}" }) { msg ->
                                val isMine = !msg.isIncoming
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isMine) BravPurple.copy(alpha = 0.14f) else lux.translucentCardSurface,
                                        border = BorderStroke(0.75.dp, lux.violetContour)
                                    ) {
                                        Text(
                                            text = msg.body,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AiReplySuggestionBar(
                    visible = aiSuggestion != null,
                    suggestedText = aiSuggestion?.first.orEmpty(),
                    modelLabel = aiSuggestion?.second.orEmpty(),
                    onSendDirect = {
                        val txt = aiSuggestion?.first?.trim().orEmpty()
                        if (recipientQuery.isNotBlank() && txt.isNotBlank()) {
                            aiSuggestion = null
                            onSendSms(recipientQuery, txt)
                        } else if (recipientQuery.isBlank()) {
                            onShowMessage("Choisissez d'abord un destinataire")
                        }
                    },
                    onEditInComposer = {
                        val txt = aiSuggestion?.first.orEmpty()
                        if (txt.isNotBlank()) bodyText = txt
                        aiSuggestion = null
                    },
                    onRegenerate = { triggerAiDraft() },
                    onDismiss = { aiSuggestion = null }
                )

                // Sleek High-End Bottom SMS Composer
                Surface(
                    color = lux.translucentCardSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BravGoldHairlineDivider(withGoldTouch = true)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (bodyText.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${bodyText.length} / $segmentMax caractères",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$segmentCount SMS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BravPurple
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(26.dp),
                                color = lux.translucentSubtleSurface,
                                border = BorderStroke(
                                    width = 0.9.dp,
                                    color = if (bodyText.isNotBlank()) lux.champagneGold.copy(alpha = 0.7f)
                                    else lux.violetContour
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 2.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)
                                ) {
                                    AiReplyDotButton(
                                        isActive = true,
                                        isLoading = isGeneratingAi,
                                        dotSize = 13.dp,
                                        touchSize = 48.dp,
                                        contentDescriptionText = "Rédiger le SMS avec l'IA",
                                        testTagName = "new_sms_ai_dot",
                                        onClick = { triggerAiDraft() }
                                    )
                                    OutlinedTextField(
                                        value = bodyText,
                                        onValueChange = { bodyText = it },
                                        placeholder = { Text("Écrire votre SMS...") },
                                        maxLines = 5,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("new_sms_body_input")
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = if (canSend) BravPurple else lux.translucentSubtleSurface,
                                onClick = {
                                    if (canSend) {
                                        onSendSms(recipientQuery.trim(), bodyText.trim())
                                    }
                                },
                                enabled = canSend,
                                modifier = Modifier
                                    .size(50.dp)
                                    .testTag("new_sms_send_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "Envoyer SMS",
                                        tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
            }
        }
    }
}
