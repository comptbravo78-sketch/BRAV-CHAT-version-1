package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.ContentModerationGuard
import com.example.model.CertificationEligibility
import com.example.model.CertificationPaymentReceipt
import com.example.model.User
import com.example.model.VerificationCategory
import com.example.model.VerificationRequest
import com.example.model.VerificationStatus
import com.example.security.BravHumanVerificationService
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravGoldHairlineDivider
import com.example.ui.components.BravLuxuryCard
import com.example.ui.components.BravPatternIntensity
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.components.CertificationSelfieCamera
import com.example.ui.components.VerificationStatusPill
import com.example.ui.components.VerifiedBlueBadge
import com.example.ui.components.bravOutlinedTextFieldColors
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravVerifiedBlue
import com.example.ui.theme.BravWhite
import com.example.ui.theme.StatusOnlineGreen
import com.example.viewmodel.BravViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VerificationRequestSheet(
    viewModel: BravViewModel,
    currentUser: User?,
    myRequests: List<VerificationRequest>,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lux = BravTheme.colors

    val serverEligibility by viewModel.certificationEligibility.collectAsStateWithLifecycle()
    val lastReceipt by viewModel.lastCertificationReceipt.collectAsStateWithLifecycle()
    val isBusy by viewModel.isCertificationBusy.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.currentUserId) {
        viewModel.refreshCertificationEligibility()
    }

    val latestRequest = myRequests.firstOrNull()
    val userStatus = currentUser?.effectiveVerificationStatus() ?: VerificationStatus.NONE
    val effectiveStatus = when {
        userStatus == VerificationStatus.VERIFIED -> VerificationStatus.VERIFIED
        latestRequest != null -> VerificationStatus.fromRaw(latestRequest.status)
        else -> userStatus
    }

    val isDesignerExempt = serverEligibility?.exemptDesigner == true ||
        viewModel.userRepository.isVerifiedDesignerAccount() ||
        (effectiveStatus == VerificationStatus.VERIFIED &&
            currentUser?.verifiedOrgName?.contains("Concepteur", ignoreCase = true) == true)

    val currentFollowers = serverEligibility?.followersCount
        ?: (currentUser?.followersCount ?: 0)
    val missingFollowers = (CertificationEligibility.REQUIRED_FOLLOWERS - currentFollowers).coerceAtLeast(0)
    val isEligibleFollowers = isDesignerExempt || currentFollowers >= CertificationEligibility.REQUIRED_FOLLOWERS

    val initialStep = when (effectiveStatus) {
        VerificationStatus.AWAITING_PAYMENT, VerificationStatus.PAYMENT_CONFIRMED -> 4
        VerificationStatus.RETAKE_REQUIRED -> 3
        else -> 1
    }
    var currentStep by remember(effectiveStatus) { mutableIntStateOf(initialStep) }

    var selectedCategory by remember(latestRequest?.category) {
        mutableStateOf(
            latestRequest?.category?.let { VerificationCategory.fromRaw(it) }
                ?: VerificationCategory.CREATOR
        )
    }
    var organizationName by remember(latestRequest?.organizationName) {
        mutableStateOf(latestRequest?.organizationName.orEmpty())
    }
    var roleOrActivity by remember(latestRequest?.roleOrActivity) {
        mutableStateOf(latestRequest?.roleOrActivity.orEmpty())
    }
    var country by remember(latestRequest?.country, currentUser?.locationCity) {
        mutableStateOf(latestRequest?.country?.ifBlank { currentUser?.locationCity }.orEmpty())
    }

    var termsAccepted by remember(latestRequest?.termsAccepted) {
        mutableStateOf(latestRequest?.termsAccepted == true)
    }
    var selfieConsent by remember(latestRequest?.selfieConsent) {
        mutableStateOf(latestRequest?.selfieConsent == true)
    }

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var analyzedPayload by remember { mutableStateOf<BravHumanVerificationService.AnalyzedSelfiePayload?>(null) }
    var activeRequest by remember(latestRequest) { mutableStateOf(latestRequest) }

    var selectedCurrency by remember { mutableStateOf("XOF") }
    var selectedProvider by remember { mutableStateOf("brav_mobile_money") }
    var paymentIdempotencyKey by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var completedReceipt by remember(lastReceipt) { mutableStateOf(lastReceipt) }
    var stepError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = lux.modalSurface,
        contentColor = lux.inkPrimary,
        modifier = Modifier.testTag("verification_request_sheet")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.SOLAR_ADINKRA,
                variant = BravPatternVariant.GOLD_ON_IVORY,
                opacity = BravPatternIntensity.LIGHT_DECOR,
                scale = 0.95f
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VerifiedBlueBadge(size = 22.dp)
                        Text(
                            text = "CERTIFICATION",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = lux.titlePurple
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = lux.inkPrimary)
                    }
                }

                // 1) Reçu électronique après activation instantanée
                if (completedReceipt != null) {
                    val receipt = completedReceipt!!
                    CertificationElectronicReceiptCard(
                        receipt = receipt,
                        onClose = onDismiss
                    )
                    return@Column
                }

                // 2) Exemption stricte serveur pour le compte concepteur officiel
                if (isDesignerExempt) {
                    BravLuxuryCard(
                        shapeRadius = 18.dp,
                        accentBorder = true,
                        modifier = Modifier.fillMaxWidth().testTag("verification_designer_exempt_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                VerifiedBlueBadge(size = 24.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Statut : Certifié",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = lux.titlePurple
                                    )
                                    Text(
                                        text = "Concepteur BRAV CHAT",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = lux.inkSecondary
                                    )
                                }
                            }
                            VerificationStatusPill(status = VerificationStatus.VERIFIED)
                            Button(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) { Text("Fermer", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    return@Column
                }

                // 3) Bannière d'état courant
                BravLuxuryCard(
                    shapeRadius = 16.dp,
                    accentBorder = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeRequest?.caseNumber?.takeIf { it.isNotBlank() }?.let { "Dossier $it" }
                                    ?: "Statut",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = lux.inkPrimary
                            )
                            VerificationStatusPill(status = effectiveStatus)
                        }
                        val statusHint = when (effectiveStatus) {
                            VerificationStatus.VERIFIED -> {
                                val cat = currentUser?.verificationCategoryEnum?.labelFr.orEmpty()
                                val org = currentUser?.verifiedOrgName.orEmpty()
                                listOfNotNull(cat.takeIf { it.isNotBlank() }, org.takeIf { it.isNotBlank() })
                                    .joinToString(" • ")
                                    .ifBlank { "Statut : Certifié — Badge bleu actif." }
                            }
                            VerificationStatus.AWAITING_PAYMENT ->
                                "Selfie validé · Frais : 3 600 F CFA (5,99 USD)"
                            VerificationStatus.RETAKE_REQUIRED ->
                                activeRequest?.reviewerNote?.ifBlank { null }
                                    ?: "Selfie à reprendre en direct"
                            VerificationStatus.REJECTED ->
                                activeRequest?.reviewerNote?.ifBlank { null }
                                    ?: "Demande refusée"
                            VerificationStatus.SUSPENDED ->
                                activeRequest?.reviewerNote?.ifBlank { null }
                                    ?: "Certification suspendue"
                            VerificationStatus.REVOKED ->
                                activeRequest?.reviewerNote?.ifBlank { null }
                                    ?: "Certification révoquée"
                            VerificationStatus.EXPIRED ->
                                "Certification expirée"
                            else ->
                                "5 000 abonnés · Selfie en direct · 3 600 F CFA"
                        }
                        Text(statusHint, style = MaterialTheme.typography.bodySmall, color = lux.inkSecondary)

                        if (activeRequest?.receiptNumber?.isNotBlank() == true) {
                            Text(
                                text = "Reçu : ${activeRequest!!.receiptNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = BravVerifiedBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (effectiveStatus in setOf(VerificationStatus.AWAITING_PAYMENT, VerificationStatus.RETAKE_REQUIRED, VerificationStatus.PENDING) &&
                            activeRequest != null
                        ) {
                            TextButton(
                                onClick = { viewModel.cancelPendingVerificationRequest(activeRequest!!.id) },
                                modifier = Modifier.testTag("cancel_verification_request_button")
                            ) { Text("Annuler la procédure", color = BravRed) }
                        }
                    }
                }

                // Si déjà certifié ou suspendu/révoqué, ne pas réafficher le tunnel
                if (effectiveStatus == VerificationStatus.VERIFIED ||
                    effectiveStatus == VerificationStatus.SUSPENDED ||
                    effectiveStatus == VerificationStatus.REVOKED
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Fermer") }
                    return@Column
                }

                // Tunnel automatisé en 4 étapes couvrant les 14 actions officielles
                LinearProgressIndicator(
                    progress = { currentStep / 4f },
                    modifier = Modifier.fillMaxWidth(),
                    color = BravPurplePrimary,
                    trackColor = lux.champagneGold.copy(alpha = 0.25f)
                )
                Text(
                    text = when (currentStep) {
                        1 -> "1/4 · Éligibilité"
                        2 -> "2/4 · Profil & accord"
                        3 -> "3/4 · Selfie en direct"
                        else -> "4/4 · Paiement"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = lux.titlePurple
                )

                when (currentStep) {
                    // ÉTAPE 1 : Contrôle strict d'éligibilité aux 5 000 abonnés réels
                    1 -> {
                        val ratio = (currentFollowers.toFloat() / CertificationEligibility.REQUIRED_FOLLOWERS.toFloat()).coerceIn(0f, 1f)
                        BravLuxuryCard(
                            shapeRadius = 16.dp,
                            accentBorder = true,
                            modifier = Modifier.fillMaxWidth().testTag("cert_eligibility_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.People, contentDescription = null, tint = lux.titlePurple)
                                    Text(
                                        text = "$currentFollowers / 5 000 abonnés",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = lux.inkPrimary
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    color = if (isEligibleFollowers) StatusOnlineGreen else BravPurplePrimary,
                                    trackColor = lux.translucentSubtleSurface,
                                    modifier = Modifier.fillMaxWidth().height(8.dp)
                                )
                                if (isEligibleFollowers) {
                                    Text(
                                        text = "Seuil d'abonnés validé",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StatusOnlineGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "$missingFollowers abonnés manquants",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BravRed,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.testTag("cert_missing_followers_text")
                                    )
                                }
                            }
                        }

                        BravLuxuryCard(shapeRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "3 600 F CFA (5,99 USD)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = lux.titlePurple
                                    )
                                    Text(
                                        text = "Selfie caméra en direct uniquement · Aucune pièce d'identité",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = lux.inkSecondary
                                    )
                                }
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = lux.champagneGold)
                            }
                        }
                    }

                    // ÉTAPE 2 : Catégorie + infos optionnelles + conditions + consentement selfie
                    2 -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VerificationCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat; stepError = null },
                                    label = { Text(cat.labelFr) },
                                    modifier = Modifier.testTag("verification_category_${cat.raw}")
                                )
                            }
                        }
                        BravLuxuryCard(shapeRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(selectedCategory.hintFr, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "3 600 F CFA (5,99 USD) · Badge bleu immédiat",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = lux.inkSecondary
                                )
                            }
                        }
                        OutlinedTextField(
                            value = organizationName,
                            onValueChange = { organizationName = it; stepError = null },
                            label = { Text("Organisation (optionnel)") },
                            singleLine = true,
                            colors = bravOutlinedTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().testTag("verification_org_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = roleOrActivity,
                                onValueChange = { roleOrActivity = it; stepError = null },
                                label = { Text("Activité") },
                                singleLine = true,
                                colors = bravOutlinedTextFieldColors(),
                                modifier = Modifier.weight(1f).testTag("verification_role_input")
                            )
                            OutlinedTextField(
                                value = country,
                                onValueChange = { country = it },
                                label = { Text("Pays / Ville") },
                                singleLine = true,
                                colors = bravOutlinedTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = termsAccepted,
                                onCheckedChange = { termsAccepted = it; stepError = null },
                                modifier = Modifier.testTag("verification_terms_checkbox")
                            )
                            Text(
                                "Accepter les conditions CERTIFICATION et frais (3 600 F CFA)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selfieConsent,
                                onCheckedChange = { selfieConsent = it; stepError = null },
                                modifier = Modifier.testTag("verification_selfie_consent_checkbox")
                            )
                            Text(
                                "Autoriser le selfie caméra en direct",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // ÉTAPE 3 : Caméra frontale en direct uniquement (zéro galerie)
                    3 -> {
                        CertificationSelfieCamera(
                            capturedPreview = capturedBitmap,
                            assessment = analyzedPayload?.assessment,
                            isProcessing = isBusy,
                            onCaptured = { bmp, payload ->
                                capturedBitmap = bmp
                                analyzedPayload = payload
                                stepError = if (payload.assessment.passed) null else payload.assessment.feedbackFr
                            },
                            onRetake = {
                                capturedBitmap = null
                                analyzedPayload = null
                                stepError = null
                            }
                        )
                        BravLuxuryCard(shapeRadius = 12.dp, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, tint = lux.titlePurple, modifier = Modifier.size(18.dp))
                                Text(
                                    "Selfie privé chiffré · Non visible sur votre profil",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = lux.inkSecondary
                                )
                            }
                        }
                    }

                    // ÉTAPE 4 : Confirmation + Paiement 3 600 F CFA (5,99 USD)
                    4 -> {
                        BravLuxuryCard(
                            shapeRadius = 16.dp,
                            accentBorder = true,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Récapitulatif CERTIFICATION", fontWeight = FontWeight.Bold, color = lux.titlePurple)
                                Text("Titulaire : ${currentUser?.displayName.orEmpty().ifBlank { "Membre BRAV" }}", style = MaterialTheme.typography.bodySmall)
                                Text("Catégorie : ${selectedCategory.labelFr}", style = MaterialTheme.typography.bodySmall)
                                Text("Abonnés : $currentFollowers", style = MaterialTheme.typography.bodySmall, color = StatusOnlineGreen, fontWeight = FontWeight.SemiBold)
                                Text("Selfie live : Validé", style = MaterialTheme.typography.bodySmall, color = StatusOnlineGreen, fontWeight = FontWeight.SemiBold)
                                BravGoldHairlineDivider(withGoldTouch = true)
                                Text(
                                    text = "Total : 3 600 F CFA (5,99 USD)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = lux.inkPrimary
                                )
                            }
                        }

                        Text("Devise d'affichage", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedCurrency == "XOF",
                                onClick = { selectedCurrency = "XOF" },
                                label = { Text("3 600 F CFA (XOF)") },
                                modifier = Modifier.testTag("cert_currency_xof")
                            )
                            FilterChip(
                                selected = selectedCurrency == "USD",
                                onClick = { selectedCurrency = "USD" },
                                label = { Text("5,99 USD") },
                                modifier = Modifier.testTag("cert_currency_usd")
                            )
                        }

                        Text("Moyen de règlement", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "brav_mobile_money" to "Mobile Money (Wave / Orange / MTN)",
                                "brav_card" to "Carte bancaire Visa / Mastercard"
                            ).forEach { (id, label) ->
                                FilterChip(
                                    selected = selectedProvider == id,
                                    onClick = { selectedProvider = id },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                if (!stepError.isNullOrBlank()) {
                    Text(
                        text = stepError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = BravRed,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("cert_step_error")
                    )
                }

                BravGoldHairlineDivider(withGoldTouch = true)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (currentStep > 1 && currentStep < 4) {
                        OutlinedButton(
                            onClick = { currentStep -= 1; stepError = null },
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Précédent") }
                    }

                    when (currentStep) {
                        1 -> {
                            Button(
                                enabled = isEligibleFollowers,
                                colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                                shape = RoundedCornerShape(14.dp),
                                onClick = { currentStep = 2; stepError = null },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("verification_next_button")
                            ) {
                                Text(
                                    text = if (isEligibleFollowers) "Continuer" else "Non éligible (< 5 000)",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        2 -> {
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                                shape = RoundedCornerShape(14.dp),
                                onClick = {
                                    val check = ContentModerationGuard.checkText("$organizationName $roleOrActivity $country")
                                    when {
                                        !termsAccepted -> stepError = "Acceptez les conditions et le tarif de 3 600 F CFA (5,99 USD)."
                                        !selfieConsent -> stepError = "Acceptez le consentement pour le selfie en direct."
                                        !check.allowed -> stepError = check.userMessage
                                        else -> {
                                            stepError = null
                                            currentStep = 3
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("verification_next_button")
                            ) {
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Passer au selfie", fontWeight = FontWeight.Bold)
                            }
                        }

                        3 -> {
                            val payload = analyzedPayload
                            val canSubmitSelfie = payload != null && payload.assessment.passed && !isBusy
                            Button(
                                enabled = canSubmitSelfie,
                                colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                                shape = RoundedCornerShape(14.dp),
                                onClick = {
                                    val ready = analyzedPayload ?: return@Button
                                    viewModel.submitLiveSelfieForCertification(
                                        category = selectedCategory,
                                        organizationName = organizationName,
                                        roleOrActivity = roleOrActivity,
                                        country = country,
                                        payload = ready,
                                        termsAccepted = termsAccepted,
                                        selfieConsent = selfieConsent
                                    ) { created, err ->
                                        if (created != null) {
                                            activeRequest = created
                                            stepError = null
                                            currentStep = 4
                                        } else if (!err.isNullOrBlank()) {
                                            stepError = err
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("verification_submit_selfie_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BravWhite, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Valider le selfie", fontWeight = FontWeight.Bold)
                            }
                        }

                        else -> {
                            Button(
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                                shape = RoundedCornerShape(14.dp),
                                onClick = {
                                    stepError = null
                                    scope.launch {
                                        val initRes = viewModel.initiateCertificationPayment(
                                            idempotencyKey = paymentIdempotencyKey,
                                            currency = selectedCurrency,
                                            provider = selectedProvider
                                        )
                                        if (initRes.isFailure) {
                                            stepError = initRes.exceptionOrNull()?.localizedMessage ?: "Échec d'initialisation du paiement."
                                            return@launch
                                        }
                                        val tx = initRes.getOrThrow()
                                        viewModel.confirmCertificationPayment(
                                            existingRequest = activeRequest,
                                            txRef = tx.txRef,
                                            outcome = "confirmed",
                                            currency = selectedCurrency
                                        ) { receipt, err ->
                                            if (receipt != null) {
                                                completedReceipt = receipt
                                            } else if (!err.isNullOrBlank()) {
                                                stepError = err
                                                paymentIdempotencyKey = UUID.randomUUID().toString()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(50.dp).testTag("verification_pay_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BravWhite, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Vérification serveur...")
                                } else {
                                    Icon(Icons.Rounded.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (selectedCurrency == "USD") "Payer 5,99 USD" else "Payer 3 600 F CFA",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun CertificationElectronicReceiptCard(
    receipt: CertificationPaymentReceipt,
    onClose: () -> Unit
) {
    val lux = BravTheme.colors
    val dateLabel = remember(receipt.verifiedAtMs) {
        val ms = if (receipt.verifiedAtMs > 0L) receipt.verifiedAtMs else System.currentTimeMillis()
        SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(Date(ms))
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = StatusOnlineGreen.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, StatusOnlineGreen.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().testTag("verification_receipt_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = StatusOnlineGreen)
                Text(
                    text = "Badge bleu CERTIFICATION activé",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = lux.inkPrimary
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = lux.titlePurple, modifier = Modifier.size(18.dp))
                Text(
                    text = "Reçu officiel : ${receipt.receiptNumber}",
                    fontWeight = FontWeight.Bold,
                    color = lux.titlePurple
                )
            }
            if (receipt.caseNumber.isNotBlank()) {
                Text("Dossier : ${receipt.caseNumber}", style = MaterialTheme.typography.bodySmall, color = lux.inkPrimary)
            }
            Text("Transaction : ${receipt.txRef}", style = MaterialTheme.typography.bodySmall, color = lux.inkSecondary)
            Text(
                text = "Montant confirmé : ${receipt.amountXof} F CFA (${receipt.amountUsdLabel})",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = lux.inkPrimary
            )
            Text("Validé le $dateLabel", style = MaterialTheme.typography.labelSmall, color = lux.inkSecondary)

            Button(
                onClick = onClose,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Voir mon badge sur mon profil", fontWeight = FontWeight.Bold)
            }
        }
    }
}
