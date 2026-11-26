package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.model.CallerReportCategory
import com.example.model.OfferListing
import com.example.model.VerificationCategory
import com.example.model.VerificationStatus
import com.example.ui.components.BravLuxuryCard
import com.example.ui.components.BravLuxuryHeader
import com.example.ui.components.BravPatternIntensity
import com.example.ui.components.BravPatternType
import com.example.ui.components.VerificationStatusPill
import com.example.ui.components.VerifiedBlueBadge
import com.example.ui.components.bravAfricanPattern
import com.example.ui.components.bravOutlinedTextFieldColors
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import com.example.ui.theme.StatusOnlineGreen
import com.example.viewmodel.BravViewModel
import kotlinx.coroutines.launch

@Composable
fun OffersMarketplaceScreen(
    viewModel: BravViewModel,
    onOpenConversation: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offers by viewModel.offers.collectAsStateWithLifecycle()
    val knownUsers by viewModel.knownUsersDirectory.collectAsStateWithLifecycle()
    val verifiedUserIds = remember(knownUsers) {
        knownUsers.filterValues { it.isVerifiedActive() }.keys
    }
    var isSubmitting by remember { mutableStateOf(false) }

    OffersMarketplaceContent(
        myUid = viewModel.currentUserId,
        offers = offers,
        verifiedUserIds = verifiedUserIds,
        onShowMessage = { viewModel.showMessage(it) },
        isSubmitting = isSubmitting,
        onContactSeller = { offer ->
            if (offer.userId.isNotBlank()) {
                scope.launch {
                    val target = viewModel.userRepository.getUserById(offer.userId)
                    if (target != null) {
                        viewModel.openOrCreateDirectChat(target).onSuccess { conv ->
                            onOpenConversation(conv.id)
                        }
                    } else {
                        viewModel.showMessage("Profil de l'annonceur introuvable")
                    }
                }
            }
        },
        onCreateOffer = { sub ->
            isSubmitting = true
            val res = viewModel.createOfferWithImages(
                offerType = sub.offerType,
                title = sub.title,
                category = sub.category,
                description = sub.description,
                condition = sub.condition,
                city = sub.locationCity,
                price = sub.priceOrSalary,
                contactInfo = sub.contactInfo,
                imagePayloads = sub.newImagePayloads
            )
            isSubmitting = false
            res.map { }
        },
        onUpdateOffer = { offerId, sub ->
            isSubmitting = true
            val res = viewModel.updateOfferWithImages(
                offerId = offerId,
                offerType = sub.offerType,
                title = sub.title,
                category = sub.category,
                description = sub.description,
                condition = sub.condition,
                city = sub.locationCity,
                price = sub.priceOrSalary,
                contactInfo = sub.contactInfo,
                keptExistingUrls = sub.keptExistingUrls,
                newImagePayloads = sub.newImagePayloads,
                removedUrls = sub.removedUrls
            )
            isSubmitting = false
            res
        },
        onDeleteOffer = { offerId -> viewModel.deleteListing(offerId) },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OffersMarketplaceContent(
    myUid: String,
    offers: List<OfferListing>,
    verifiedUserIds: Set<String> = emptySet(),
    isSubmitting: Boolean = false,
    onShowMessage: (String) -> Unit = {},
    onContactSeller: (OfferListing) -> Unit,
    onCreateOffer: suspend (OfferComposeSubmission) -> Result<Unit>,
    onUpdateOffer: suspend (String, OfferComposeSubmission) -> Result<Unit>,
    onDeleteOffer: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var selectedFilter by remember { mutableStateOf("Toutes") }
    var searchQuery by remember { mutableStateOf("") }
    var detailOffer by remember { mutableStateOf<OfferListing?>(null) }
    var showComposer by remember { mutableStateOf(false) }
    var editingOffer by remember { mutableStateOf<OfferListing?>(null) }
    var deleteConfirmOffer by remember { mutableStateOf<OfferListing?>(null) }

    val filters = remember { listOf("Toutes", "Vente", "Services", "Emploi", "Immobilier") }
    val visibleOffers = remember(offers, selectedFilter, searchQuery) {
        val byCat = when (selectedFilter) {
            "Toutes" -> offers
            "Vente" -> offers.filter { it.resolvedOfferType() == "product" || it.category.equals("Vente", ignoreCase = true) }
            "Services" -> offers.filter { it.resolvedOfferType() == "service" || it.category.equals("Services", ignoreCase = true) }
            else -> offers.filter { it.category.equals(selectedFilter, ignoreCase = true) }
        }
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) byCat
        else byCat.filter {
            it.title.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.locationCity.lowercase().contains(q) ||
                it.authorName.lowercase().contains(q)
        }
    }

    val lux = BravTheme.colors
    Scaffold(
        containerColor = lux.ivoryCanvas,
        topBar = {
            BravLuxuryHeader(
                title = "Offres & Annonces",
                pattern = BravPatternType.HERITAGE_COVER,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("offers_back_button")) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour",
                            tint = lux.headerOnColor
                        )
                    }
                },
                bottomContent = {
                    Text(
                        text = "${visibleOffers.size} annonce(s) active(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = lux.headerOnColor.copy(alpha = 0.86f)
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingOffer = null
                    showComposer = true
                },
                containerColor = BravPurplePrimary,
                contentColor = BravWhite,
                modifier = Modifier.testTag("offers_fab_create")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Publier une annonce")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(lux.ivoryCanvas)
                .bravAfricanPattern(
                    pattern = BravPatternType.KENTE_DIAMOND,
                    primaryColor = lux.watermarkGold,
                    secondaryColor = lux.watermarkViolet,
                    opacity = BravPatternIntensity.SUBTLE_WATERMARK
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .testTag("offers_list"),
                contentPadding = PaddingValues(top = 12.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = lux.titlePurple) },
                        placeholder = { Text("Rechercher un produit, un service ou une ville") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = bravOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("offers_search_input")
                    )
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filters) { cat ->
                            FilterChip(
                                selected = selectedFilter == cat,
                                onClick = { selectedFilter = cat },
                                label = { Text(cat) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BravPurplePrimary.copy(alpha = 0.14f),
                                    selectedLabelColor = lux.titlePurple
                                ),
                                modifier = Modifier.testTag("offers_filter_$cat")
                            )
                        }
                    }
                }

                if (visibleOffers.isEmpty()) {
                    item {
                        BravLuxuryCard(
                            shapeRadius = 20.dp,
                            accentBorder = true,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Storefront,
                                    contentDescription = null,
                                    tint = lux.titlePurple,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Aucune annonce disponible",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = lux.inkPrimary
                                )
                                Text(
                                    text = "Publiez un produit à vendre ou proposez vos services avec photos en appuyant sur +.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = lux.inkSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(visibleOffers, key = { it.id }) { offer ->
                        OfferMarketplaceCard(
                            offer = offer,
                            isMine = offer.userId == myUid,
                            isAuthorVerified = offer.userId in verifiedUserIds,
                            onOpenDetail = { detailOffer = offer }
                        )
                    }
                }
            }
        }
    }

    detailOffer?.let { activeOffer ->
        // Keep up-to-date snapshot from list when edited
        val currentOffer = offers.find { it.id == activeOffer.id } ?: activeOffer
        OfferDetailDialog(
            offer = currentOffer,
            isMine = currentOffer.userId == myUid,
            isAuthorVerified = currentOffer.userId in verifiedUserIds,
            onShowMessage = onShowMessage,
            onContactSeller = {
                detailOffer = null
                onContactSeller(currentOffer)
            },
            onEditOffer = {
                detailOffer = null
                editingOffer = currentOffer
                showComposer = true
            },
            onDeleteOffer = {
                detailOffer = null
                deleteConfirmOffer = currentOffer
            },
            onDismiss = { detailOffer = null }
        )
    }

    if (showComposer) {
        val currentEditing = editingOffer
        OfferComposerDialog(
            editingOffer = currentEditing,
            isSubmitting = isSubmitting,
            onSubmit = { sub ->
                if (currentEditing == null) onCreateOffer(sub)
                else onUpdateOffer(currentEditing.id, sub)
            },
            onDismiss = {
                showComposer = false
                editingOffer = null
            }
        )
    }

    deleteConfirmOffer?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmOffer = null },
            containerColor = lux.modalSurface,
            title = { Text("Supprimer cette annonce ?") },
            text = { Text("« ${target.title} » et ses photos associées seront définitivement supprimées.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = BravRed, contentColor = Color.White),
                    onClick = {
                        deleteConfirmOffer = null
                        onDeleteOffer(target.id)
                    },
                    modifier = Modifier.testTag("confirm_delete_offer_button")
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmOffer = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminModerationScreen(
    viewModel: BravViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val scope = rememberCoroutineScope()
    val reports by viewModel.adminReports.collectAsStateWithLifecycle()
    val verificationRequests by viewModel.adminVerificationRequests.collectAsStateWithLifecycle()
    val callerReports by viewModel.adminCallerReports.collectAsStateWithLifecycle()
    var verifSearch by remember { mutableStateOf("") }
    var verifFilter by remember { mutableStateOf<String>("all") }
    var decisionDialog by remember { mutableStateOf<Pair<com.example.model.VerificationRequest, VerificationStatus>?>(null) }
    var decisionNote by remember { mutableStateOf("") }

    val awaitingPayCount = remember(verificationRequests) { verificationRequests.count { it.status == VerificationStatus.AWAITING_PAYMENT.raw } }
    val retakeCount = remember(verificationRequests) { verificationRequests.count { it.status == VerificationStatus.RETAKE_REQUIRED.raw } }
    val verifiedCount = remember(verificationRequests) { verificationRequests.count { it.status == VerificationStatus.VERIFIED.raw } }
    val suspendedCount = remember(verificationRequests) { verificationRequests.count { it.status == VerificationStatus.SUSPENDED.raw } }

    val filteredVerifications = remember(verificationRequests, verifSearch, verifFilter) {
        val q = verifSearch.trim().lowercase()
        verificationRequests.filter { req ->
            val filterMatch = verifFilter == "all" || req.status == verifFilter
            val queryMatch = q.isBlank() ||
                req.fullName.lowercase().contains(q) ||
                req.caseNumber.lowercase().contains(q) ||
                req.username.lowercase().contains(q) ||
                req.userId.lowercase().contains(q) ||
                req.receiptNumber.lowercase().contains(q) ||
                req.organizationName.lowercase().contains(q)
            filterMatch && queryMatch
        }
    }

    val lux = BravTheme.colors
    Scaffold(
        containerColor = lux.ivoryCanvas,
        topBar = {
            BravLuxuryHeader(
                title = "Centre CERTIFICATION & Modération",
                showLogo = false,
                pattern = BravPatternType.KENTE_DIAMOND,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour",
                            tint = lux.headerOnColor
                        )
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
                .bravAfricanPattern(
                    pattern = BravPatternType.KENTE_DIAMOND,
                    primaryColor = lux.watermarkGold,
                    secondaryColor = lux.watermarkViolet,
                    opacity = BravPatternIntensity.SUBTLE_WATERMARK
                )
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VerifiedBlueBadge(size = 18.dp)
                    Text(
                        text = "Centre CERTIFICATION (${verificationRequests.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = lux.titlePurple
                    )
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chips = listOf(
                        "all" to "Tous (${verificationRequests.size})",
                        VerificationStatus.VERIFIED.raw to "Vérifiés ($verifiedCount)",
                        VerificationStatus.AWAITING_PAYMENT.raw to "Paiement ($awaitingPayCount)",
                        VerificationStatus.RETAKE_REQUIRED.raw to "À reprendre ($retakeCount)",
                        VerificationStatus.SUSPENDED.raw to "Suspendus ($suspendedCount)",
                        VerificationStatus.REVOKED.raw to "Révoqués"
                    )
                    items(chips, key = { it.first }) { (key, label) ->
                        FilterChip(
                            selected = verifFilter == key,
                            onClick = { verifFilter = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = verifSearch,
                    onValueChange = { verifSearch = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("Rechercher dossier BRV-, reçu REC-BRV-, nom ou @identifiant") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = bravOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("admin_verif_search_input")
                )
            }

            if (filteredVerifications.isEmpty()) {
                item {
                    Text("Aucune demande de certification correspondante.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(filteredVerifications, key = { "verif_${it.id}" }) { req ->
                    val verStatus = VerificationStatus.fromRaw(req.status)
                    BravLuxuryCard(
                        shapeRadius = 18.dp,
                        accentBorder = verStatus == VerificationStatus.VERIFIED,
                        modifier = Modifier.fillMaxWidth().testTag("admin_verif_card_${req.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(req.fullName.ifBlank { req.userId }, fontWeight = FontWeight.Bold)
                                    val meta = listOfNotNull(
                                        req.caseNumber.takeIf { it.isNotBlank() },
                                        req.username.takeIf { it.isNotBlank() }?.let { "@$it" }
                                    ).joinToString(" · ")
                                    if (meta.isNotBlank()) {
                                        Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                VerificationStatusPill(status = verStatus)
                            }
                            Text(
                                text = listOfNotNull(
                                    VerificationCategory.fromRaw(req.category).labelFr,
                                    req.organizationName.takeIf { it.isNotBlank() },
                                    req.roleOrActivity.takeIf { it.isNotBlank() },
                                    req.country.takeIf { it.isNotBlank() }
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.labelMedium,
                                color = BravPurple
                            )
                            Text(
                                text = "Abonnés : ${req.followersCountAtRequest} · Selfie live : ${if (req.selfieVerified) "Validé (${(req.selfieScore * 100).toInt()}% · risque ${req.spoofRisk})" else "Non validé"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (req.selfieVerified && req.followersCountAtRequest >= 5000) StatusOnlineGreen else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (req.designerExempt) {
                                    "Exemption officielle du concepteur BRAV CHAT"
                                } else {
                                    "Paiement : ${req.paymentStatus} · 3 600 F CFA (5,99 USD)" +
                                        (req.receiptNumber.takeIf { it.isNotBlank() }?.let { " · Reçu $it" } ?: "")
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (req.paymentTxRef.isNotBlank()) {
                                Text("Réf transaction : ${req.paymentTxRef}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (req.auditTrail.isNotEmpty()) {
                                Text(
                                    text = "Historique : ${req.auditTrail.last()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (verStatus != VerificationStatus.VERIFIED) {
                                    item {
                                        Button(
                                            onClick = {
                                                viewModel.adminReviewVerificationRequest(
                                                    request = req,
                                                    newStatus = VerificationStatus.VERIFIED,
                                                    reviewerNote = "Certification validée par l'administration BRAV."
                                                )
                                            },
                                            modifier = Modifier.testTag("admin_verif_approve_${req.id}")
                                        ) { Text("Approuver") }
                                    }
                                    item {
                                        OutlinedButton(
                                            onClick = {
                                                decisionDialog = req to VerificationStatus.RETAKE_REQUIRED
                                                decisionNote = ""
                                            }
                                        ) { Text("À reprendre") }
                                    }
                                    item {
                                        OutlinedButton(
                                            onClick = {
                                                decisionDialog = req to VerificationStatus.REJECTED
                                                decisionNote = ""
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BravRed),
                                            modifier = Modifier.testTag("admin_verif_reject_${req.id}")
                                        ) { Text("Refuser") }
                                    }
                                } else {
                                    item {
                                        OutlinedButton(
                                            onClick = {
                                                decisionDialog = req to VerificationStatus.SUSPENDED
                                                decisionNote = ""
                                            }
                                        ) { Text("Suspendre") }
                                    }
                                    item {
                                        OutlinedButton(
                                            onClick = {
                                                decisionDialog = req to VerificationStatus.REVOKED
                                                decisionNote = ""
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BravRed)
                                        ) { Text("Révoquer") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "BRAV Call ID — Signalements numéros (${callerReports.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (callerReports.isEmpty()) {
                item {
                    Text("Aucun signalement de numéro.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(callerReports, key = { "cid_${it.id}" }) { crep ->
                    val cat = CallerReportCategory.fromRaw(crep.category)
                    BravLuxuryCard(
                        shapeRadius = 18.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${crep.maskedNumber} • ${cat.labelFr}",
                                fontWeight = FontWeight.Bold
                            )
                            Text("Libellé : ${crep.label} • Statut : ${crep.status}", style = MaterialTheme.typography.bodySmall)
                            if (crep.details.isNotBlank()) {
                                Text(crep.details, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.adminModerateCallerReport(crep.id, "approved", cat, crep.label)
                                    }
                                ) {
                                    Text("Approuver")
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.adminModerateCallerReport(crep.id, "rejected", cat, crep.label)
                                    }
                                ) {
                                    Text("Rejeter")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Signalements utilisateurs (${reports.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (reports.isEmpty()) {
                item {
                    Text("Aucun signalement en attente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(reports, key = { it.id }) { rep ->
                    BravLuxuryCard(
                        shapeRadius = 18.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Motif : ${rep.reason}", fontWeight = FontWeight.Bold)
                            Text("Statut : ${rep.status} • Cible : ${rep.targetUid.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                            if (rep.details.isNotBlank()) Text(rep.details, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    scope.launch { viewModel.socialRepository.adminUpdateReportStatus(rep.id, "dismissed") }
                                }) { Text("Classer") }
                                if (rep.targetUid.isNotBlank()) {
                                    Button(
                                        colors = ButtonDefaults.buttonColors(containerColor = BravRed, contentColor = Color.White),
                                        onClick = {
                                            scope.launch {
                                                viewModel.socialRepository.adminToggleUserSuspension(rep.targetUid, true)
                                                viewModel.socialRepository.adminUpdateReportStatus(rep.id, "action_taken")
                                                viewModel.showMessage("Compte cible suspendu")
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Verified, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Suspendre")
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

    decisionDialog?.let { (targetReq, nextStatus) ->
        AlertDialog(
            onDismissRequest = { decisionDialog = null },
            containerColor = lux.modalSurface,
            title = {
                Text(
                    when (nextStatus) {
                        VerificationStatus.RETAKE_REQUIRED -> "Demander une reprise du selfie"
                        VerificationStatus.SUSPENDED -> "Suspendre la certification"
                        VerificationStatus.REVOKED -> "Révoquer la certification"
                        else -> "Refuser la certification"
                    }
                )
            },
            text = {
                OutlinedTextField(
                    value = decisionNote,
                    onValueChange = { decisionNote = it },
                    label = { Text("Motif officiel (visible par le titulaire)") },
                    minLines = 2,
                    colors = bravOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = BravRed, contentColor = Color.White),
                    onClick = {
                        viewModel.adminReviewVerificationRequest(
                            request = targetReq,
                            newStatus = nextStatus,
                            reviewerNote = decisionNote.ifBlank {
                                when (nextStatus) {
                                    VerificationStatus.RETAKE_REQUIRED -> "Merci de reprendre votre selfie en direct dans un endroit lumineux."
                                    VerificationStatus.SUSPENDED -> "Certification suspendue pour vérification."
                                    VerificationStatus.REVOKED -> "Certification révoquée."
                                    else -> "Conditions de certification non satisfaites."
                                }
                            }
                        )
                        decisionDialog = null
                    }
                ) { Text("Confirmer") }
            },
            dismissButton = {
                TextButton(onClick = { decisionDialog = null }) { Text("Annuler") }
            }
        )
    }
}
