package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Contact
import com.example.model.User
import com.example.ui.components.BravAfricanPatternCanvas
import com.example.ui.components.BravLuxuryCard
import com.example.ui.components.BravLuxuryHeader
import com.example.ui.components.BravPatternIntensity
import com.example.ui.components.BravPatternType
import com.example.ui.components.BravPatternVariant
import com.example.ui.components.BravSearchBarField
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.components.VerifiedBlueBadge
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import com.example.ui.theme.CallActionGreen
import com.example.util.WhatsAppLauncher
import com.example.viewmodel.BravViewModel
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(
    viewModel: BravViewModel,
    onOpenConversation: (String) -> Unit,
    onOpenSms: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val savedContacts by viewModel.savedContactUsers.collectAsStateWithLifecycle()
    val incomingRequests by viewModel.incomingRequests.collectAsStateWithLifecycle()
    val phoneContacts by viewModel.phoneContacts.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncingContacts.collectAsStateWithLifecycle()
    val presenceMap by viewModel.presenceMap.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val followingUserIds by viewModel.followingUserIds.collectAsStateWithLifecycle()
    val outgoingFollowRequests by viewModel.outgoingFollowRequests.collectAsStateWithLifecycle()
    val incomingFollowRequests by viewModel.incomingFollowRequests.collectAsStateWithLifecycle()
    val followerUsers by viewModel.followerUsers.collectAsStateWithLifecycle()
    val followingUsers by viewModel.followingUsers.collectAsStateWithLifecycle()
    val myInvites by viewModel.myInvites.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var hasContactsPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingCallTarget by remember { mutableStateOf<Pair<User, Boolean>?>(null) }
    var pendingWhatsAppContact by remember { mutableStateOf<Pair<String, String>?>(null) }
    var inspectedFriend by remember { mutableStateOf<User?>(null) }
    var showFollowSheet by remember { mutableStateOf(false) }
    var showInviteSheet by remember { mutableStateOf(false) }

    val contactsPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasContactsPerm = granted
        if (granted) viewModel.syncDeviceContacts()
        else viewModel.showMessage("Permission répertoire refusée.")
    }

    val callPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val pending = pendingCallTarget ?: return@rememberLauncherForActivityResult
        pendingCallTarget = null
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camGranted = !pending.second || grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (micGranted && camGranted) {
            viewModel.startOutgoingCall(pending.first.userId, pending.first.displayName, isVideo = pending.second)
        } else {
            viewModel.showMessage("Autorisation micro/caméra requise pour appeler.")
        }
    }

    fun startBravCall(user: User, isVideo: Boolean) {
        val needMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        val needCam = isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        if (!needMic && !needCam) {
            viewModel.startOutgoingCall(user.userId, user.displayName, isVideo = isVideo)
        } else {
            pendingCallTarget = user to isVideo
            val perms = buildList {
                if (needMic) add(Manifest.permission.RECORD_AUDIO)
                if (needCam) add(Manifest.permission.CAMERA)
            }.toTypedArray()
            callPermLauncher.launch(perms)
        }
    }

    LaunchedEffect(hasContactsPerm) {
        if (hasContactsPerm && phoneContacts.isEmpty()) {
            viewModel.syncDeviceContacts()
        }
    }

    val normalizedQuery = remember(query) { query.trim().lowercase() }
    val filteredPhoneContacts = remember(phoneContacts, normalizedQuery) {
        if (normalizedQuery.isBlank()) phoneContacts
        else phoneContacts.filter { c ->
            c.preferredDisplayName.lowercase().contains(normalizedQuery) ||
                c.rawPhoneNumber.lowercase().contains(normalizedQuery) ||
                c.normalizedPhoneNumber.lowercase().contains(normalizedQuery) ||
                c.phoneEmail.lowercase().contains(normalizedQuery) ||
                c.localityHint.lowercase().contains(normalizedQuery)
        }
    }
    val phoneBravMatches = remember(filteredPhoneContacts) {
        filteredPhoneContacts.filter { it.isBravUser }
    }
    val nonBravPhoneContacts = remember(filteredPhoneContacts) {
        filteredPhoneContacts.filter { !it.isBravUser }
    }

    val lux = BravTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lux.ivoryCanvas)
    ) {
        BravLuxuryHeader(
            title = "Contacts",
            showLogo = true,
            pattern = BravPatternType.KENTE_DIAMOND,
            actions = {
                IconButton(onClick = {
                    val idText = "@${currentUser?.username.orEmpty().ifBlank { viewModel.currentUserId.take(8) }}"
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("BRAV ID", idText))
                    viewModel.showMessage("Identifiant $idText copié")
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copier mon identifiant", tint = lux.headerOnColor)
                }
                IconButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Rejoins-moi sur BRAV Chat ! Mon identifiant : @${currentUser?.username.orEmpty()}"
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Partager mon profil BRAV"))
                }) {
                    Icon(Icons.Rounded.Share, contentDescription = "Partager mon profil", tint = lux.headerOnColor)
                }
                IconButton(
                    enabled = !isSyncing,
                    onClick = {
                        if (hasContactsPerm) viewModel.syncDeviceContacts()
                        else contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS)
                    },
                    modifier = Modifier.testTag("contacts_sync_button")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = lux.headerOnColor)
                    } else {
                        Icon(Icons.Rounded.Sync, contentDescription = "Synchroniser le répertoire", tint = lux.headerOnColor)
                    }
                }
            }
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BravAfricanPatternCanvas(
                pattern = BravPatternType.KENTE_DIAMOND,
                variant = BravPatternVariant.GOLD_ON_IVORY,
                opacity = BravPatternIntensity.SUBTLE_WATERMARK,
                scale = 1.05f
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Répertoire & BRAV",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = lux.titlePurple
                        )
                        Text(
                            text = "${phoneContacts.size} répertoire • ${savedContacts.size} BRAV",
                            style = MaterialTheme.typography.labelSmall,
                            color = lux.inkSecondary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    BravSearchBarField(
                        value = query,
                        onValueChange = {
                            query = it
                            viewModel.searchUsers(it)
                        },
                        placeholder = "Rechercher nom, numéro, e-mail, @username...",
                        inputTestTag = "contacts_search_input",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { showFollowSheet = true },
                            shape = RoundedCornerShape(999.dp),
                            color = lux.translucentSubtleSurface,
                            border = BorderStroke(0.85.dp, lux.violetContour),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("contacts_manage_follows_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                            ) {
                                Icon(Icons.Rounded.Group, contentDescription = null, tint = lux.titlePurple, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (incomingFollowRequests.isNotEmpty()) {
                                        "Abonnés (${incomingFollowRequests.size})"
                                    } else {
                                        "Abonnés & Demandes"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = lux.titlePurple,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Surface(
                            onClick = { showInviteSheet = true },
                            shape = RoundedCornerShape(999.dp),
                            color = lux.champagneSoftTint,
                            border = BorderStroke(0.85.dp, lux.champagneGold),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("contacts_invite_friends_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                            ) {
                                Icon(Icons.Rounded.Link, contentDescription = null, tint = lux.inkPrimary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Inviter des amis",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = lux.inkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (!hasContactsPerm) {
                    item {
                        BravLuxuryCard(
                            shapeRadius = 18.dp,
                            accentBorder = true,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Rounded.Contacts, contentDescription = null, tint = lux.titlePurple)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Accès au répertoire téléphonique", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = lux.inkPrimary)
                                        Text(
                                            "Autorisez l'accès aux contacts pour afficher photos, numéros, appels directs et détecter vos proches sur BRAV Chat.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = lux.inkSecondary
                                        )
                                    }
                                }
                                Button(
                                    onClick = { contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("contacts_grant_permission_button")
                                ) {
                                    Text("Autoriser et synchroniser le répertoire")
                                }
                            }
                        }
                    }
                }

        if (incomingRequests.isNotEmpty()) {
            item {
                Text(
                    "Invitations reçues (${incomingRequests.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(incomingRequests, key = { it.id }) { req ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(req.fromName, fontWeight = FontWeight.Bold)
                            Text("@${req.fromUsername}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.declineRequest(req) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Refuser", tint = BravRed)
                        }
                        IconButton(onClick = { viewModel.acceptRequest(req) }) {
                            Icon(Icons.Rounded.Check, contentDescription = "Accepter", tint = CallActionGreen)
                        }
                    }
                }
            }
        }

        if (normalizedQuery.isNotBlank()) {
            item {
                Text(
                    "Membres BRAV trouvés (${searchResults.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            if (searchResults.isEmpty()) {
                item {
                    Text(
                        "Aucun utilisateur BRAV trouvé pour \"$query\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(searchResults, key = { "search_${it.userId}" }) { user ->
                    UserDiscoveryRow(
                        user = user,
                        avatarOverride = "",
                        isOnline = presenceMap[user.userId]?.online == true,
                        isSaved = savedContacts.any { it.userId == user.userId },
                        onOpenProfile = { inspectedFriend = user },
                        onChat = {
                            scope.launch {
                                viewModel.openOrCreateDirectChat(user).onSuccess { conv ->
                                    onOpenConversation(conv.id)
                                }
                            }
                        },
                        onAudioCall = { startBravCall(user, isVideo = false) },
                        onVideoCall = { startBravCall(user, isVideo = true) },
                        onSms = user.phoneMasked.takeIf { it.isNotBlank() && !it.contains("•") }?.let { num ->
                            { onOpenSms(num) }
                        },
                        onInvite = { viewModel.sendContactRequest(user) },
                        onBlock = { viewModel.blockUser(user.userId) }
                    )
                }
            }
        }

        if (phoneBravMatches.isNotEmpty()) {
            item {
                Text(
                    "Sur BRAV Chat (${phoneBravMatches.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            items(phoneBravMatches, key = { "phone_brav_${it.id}" }) { contact ->
                val u = contact.bravUser!!
                val subtitle = listOfNotNull(
                    contact.rawPhoneNumber.takeIf { it.isNotBlank() },
                    contact.localityHint.takeIf { it.isNotBlank() },
                    contact.phoneEmail.takeIf { it.isNotBlank() }
                ).joinToString(" • ").ifBlank { "@${u.username}" }
                UserDiscoveryRow(
                    user = u.copy(displayName = contact.preferredDisplayName),
                    avatarOverride = contact.effectiveAvatarUrl,
                    isOnline = presenceMap[u.userId]?.online == true,
                    isSaved = savedContacts.any { it.userId == u.userId },
                    subtitleOverride = subtitle,
                    onOpenProfile = { inspectedFriend = u },
                    onChat = {
                        scope.launch {
                            viewModel.openOrCreateDirectChat(u).onSuccess { onOpenConversation(it.id) }
                        }
                    },
                    onAudioCall = { startBravCall(u, isVideo = false) },
                    onVideoCall = { startBravCall(u, isVideo = true) },
                    onSms = contact.normalizedPhoneNumber.takeIf { it.isNotBlank() }?.let { num ->
                        { onOpenSms(num) }
                    },
                    onInvite = {
                        scope.launch {
                            viewModel.contactRepository.addContactDirectly(viewModel.currentUserId, u)
                            viewModel.showMessage("${u.displayName} ajouté aux contacts BRAV")
                        }
                    },
                    onBlock = { viewModel.blockUser(u.userId) }
                )
            }
        }

        if (savedContacts.isNotEmpty() && normalizedQuery.isBlank()) {
            item {
                Text(
                    "Mes contacts BRAV (${savedContacts.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            items(savedContacts, key = { "saved_${it.userId}" }) { user ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = lux.cardSurface,
                    border = BorderStroke(0.75.dp, lux.hairline),
                    onClick = { inspectedFriend = user },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_card_${user.userId}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatarWithStatus(
                            name = user.displayName,
                            avatarUrl = user.avatarUrl,
                            availabilityStatus = user.availabilityStatus,
                            isOnlineRealtime = presenceMap[user.userId]?.online == true,
                            size = 46.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    user.displayName,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (user.isVerifiedActive()) {
                                    VerifiedBlueBadge(size = 15.dp)
                                }
                            }
                            Text(
                                text = listOfNotNull(
                                    user.username.takeIf { it.isNotBlank() }?.let { "@$it" },
                                    user.locationCity.takeIf { it.isNotBlank() }
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { startBravCall(user, isVideo = false) }) {
                            Icon(Icons.Rounded.Call, contentDescription = "Appel audio", tint = CallActionGreen)
                        }
                        IconButton(onClick = { startBravCall(user, isVideo = true) }) {
                            Icon(Icons.Rounded.Videocam, contentDescription = "Appel vidéo", tint = BravPurple)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                viewModel.openOrCreateDirectChat(user).onSuccess { onOpenConversation(it.id) }
                            }
                        }) {
                            Icon(Icons.Rounded.ChatBubble, contentDescription = "Discuter", tint = BravPurple)
                        }
                        IconButton(onClick = { viewModel.removeContact(user.userId) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Supprimer contact", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (nonBravPhoneContacts.isNotEmpty()) {
            item {
                Text(
                    "Répertoire téléphonique (${nonBravPhoneContacts.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(nonBravPhoneContacts, key = { "device_${it.id}_${it.normalizedPhoneNumber}" }) { contact ->
                val rawPhone = contact.normalizedPhoneNumber.ifBlank { contact.rawPhoneNumber }
                DeviceContactRow(
                    contact = contact,
                    onDial = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(rawPhone)}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(dialIntent) }
                    },
                    onSms = {
                        if (rawPhone.isNotBlank()) onOpenSms(rawPhone)
                    },
                    onWhatsApp = if (WhatsAppLauncher.hasValidPhoneDigits(rawPhone)) {
                        {
                            pendingWhatsAppContact = contact.preferredDisplayName to rawPhone
                        }
                    } else null,
                    onInvite = {
                        scope.launch {
                            val link = myInvites.firstOrNull()?.shareUrl
                                ?: viewModel.createOrGetPersonalInvite("contacts").getOrNull()?.shareUrl
                                ?: "https://bravchat.com"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Salut ${contact.phoneDisplayName}, rejoins-moi sur BRAV Chat pour des appels HD et messages chiffrés : $link"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Inviter ${contact.phoneDisplayName}"))
                        }
                    }
                )
            }
        } else if (hasContactsPerm && phoneContacts.isEmpty() && savedContacts.isEmpty() && normalizedQuery.isBlank()) {
            item {
                OutlinedButton(
                    onClick = { viewModel.syncDeviceContacts() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Icon(Icons.Rounded.Sync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Actualiser le répertoire")
                }
            }
        }
    }
    }
    }

    pendingWhatsAppContact?.let { (name, rawPhone) ->
        AlertDialog(
            onDismissRequest = { pendingWhatsAppContact = null },
            containerColor = lux.modalSurface,
            title = { Text("Contacter sur WhatsApp") },
            text = {
                Text("Quitter BRAV CHAT pour ouvrir la discussion WhatsApp avec $name ($rawPhone) ?")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White),
                    onClick = {
                        pendingWhatsAppContact = null
                        WhatsAppLauncher.openWhatsAppChat(context, rawPhone)
                            .onFailure { err -> viewModel.showMessage(err.localizedMessage ?: "WhatsApp indisponible") }
                    },
                    modifier = Modifier.testTag("confirm_contact_whatsapp_button")
                ) {
                    Text("Ouvrir WhatsApp")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingWhatsAppContact = null }) { Text("Annuler") }
            }
        )
    }
    inspectedFriend?.let { friend ->
        val friendPosts = remember(statuses, friend.userId) {
            statuses.filter { it.userId == friend.userId }
        }
        val relationState = remember(followingUserIds, outgoingFollowRequests, friend.userId) {
            viewModel.followRelationStateFor(friend.userId)
        }
        FriendProfileModal(
            user = friend,
            userPosts = friendPosts,
            myUid = viewModel.currentUserId,
            isContact = savedContacts.any { it.userId == friend.userId },
            isOnline = presenceMap[friend.userId]?.online == true || friend.isOnline,
            isFollowing = friend.userId in followingUserIds,
            followRelationState = relationState,
            onToggleFollow = { target -> viewModel.toggleFollowUser(target) },
            onSendMessage = { target ->
                scope.launch {
                    viewModel.openOrCreateDirectChat(target).onSuccess { conv ->
                        inspectedFriend = null
                        onOpenConversation(conv.id)
                    }
                }
            },
            onStartAudioCall = { target ->
                inspectedFriend = null
                startBravCall(target, isVideo = false)
            },
            onDismiss = { inspectedFriend = null }
        )
    }

    if (showFollowSheet) {
        FollowManagementSheet(
            followers = followerUsers,
            following = followingUsers,
            incomingRequests = incomingFollowRequests,
            outgoingRequests = outgoingFollowRequests,
            initialTab = if (incomingFollowRequests.isNotEmpty()) {
                FollowManagementTab.INCOMING
            } else {
                FollowManagementTab.FOLLOWERS
            },
            onMessageUser = { target ->
                scope.launch {
                    viewModel.openOrCreateDirectChat(target).onSuccess { conv ->
                        showFollowSheet = false
                        onOpenConversation(conv.id)
                    }
                }
            },
            onOpenUserProfile = { target ->
                showFollowSheet = false
                inspectedFriend = target
            },
            onRemoveFollower = { follower ->
                viewModel.removeFollower(follower.userId, follower.displayName)
            },
            onUnfollowUser = { target ->
                viewModel.toggleFollowUser(target)
            },
            onBlockUser = { target ->
                viewModel.blockUser(target.userId)
            },
            onAcceptRequest = { req -> viewModel.acceptFollowRequest(req) },
            onDeclineRequest = { req -> viewModel.declineFollowRequest(req) },
            onCancelOutgoingRequest = { req -> viewModel.cancelOutgoingFollowRequest(req.toUid) },
            onDismiss = { showFollowSheet = false }
        )
    }

    if (showInviteSheet) {
        InviteShareSheet(
            myInvites = myInvites,
            onEnsureInviteLink = { viewModel.createOrGetPersonalInvite("contacts") },
            onFeedback = { msg -> viewModel.showMessage(msg) },
            onDismiss = { showInviteSheet = false }
        )
    }
}

@Composable
private fun UserDiscoveryRow(
    user: User,
    avatarOverride: String,
    isOnline: Boolean,
    isSaved: Boolean,
    subtitleOverride: String? = null,
    onOpenProfile: (() -> Unit)? = null,
    onChat: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onSms: (() -> Unit)? = null,
    onInvite: () -> Unit,
    onBlock: () -> Unit
) {
    val lux = BravTheme.colors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = lux.cardSurface,
        border = BorderStroke(0.75.dp, lux.hairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onOpenProfile != null) Modifier.clickable(onClick = onOpenProfile)
                        else Modifier
                    )
            ) {
                UserAvatarWithStatus(
                    name = user.displayName,
                    avatarUrl = avatarOverride.ifBlank { user.avatarUrl },
                    availabilityStatus = user.availabilityStatus,
                    isOnlineRealtime = isOnline,
                    size = 46.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            user.displayName,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (user.isVerifiedActive()) {
                            VerifiedBlueBadge(size = 15.dp)
                        }
                    }
                    Text(
                        text = subtitleOverride ?: "@${user.username.ifBlank { user.userId.take(6) }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!isSaved) {
                    TextButton(onClick = onInvite) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ajouter", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAudioCall) {
                    Icon(Icons.Rounded.Call, contentDescription = "Appel audio", tint = CallActionGreen)
                }
                IconButton(onClick = onVideoCall) {
                    Icon(Icons.Rounded.Videocam, contentDescription = "Appel vidéo", tint = BravPurple)
                }
                IconButton(onClick = onChat) {
                    Icon(Icons.Rounded.ChatBubble, contentDescription = "Message BRAV", tint = BravPurple)
                }
                if (onSms != null) {
                    IconButton(onClick = onSms) {
                        Icon(Icons.Rounded.Sms, contentDescription = "Envoyer SMS", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = onBlock) {
                    Icon(Icons.Rounded.Block, contentDescription = "Bloquer", tint = BravRed)
                }
            }
        }
    }
}

@Composable
private fun DeviceContactRow(
    contact: Contact,
    onDial: () -> Unit,
    onSms: () -> Unit,
    onWhatsApp: (() -> Unit)? = null,
    onInvite: () -> Unit
) {
    val lux = BravTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = lux.cardSurface,
        border = BorderStroke(0.75.dp, lux.hairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatarWithStatus(
                name = contact.preferredDisplayName,
                avatarUrl = contact.effectiveAvatarUrl,
                size = 44.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.preferredDisplayName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        contact.rawPhoneNumber.takeIf { it.isNotBlank() },
                        contact.localityHint.takeIf { it.isNotBlank() }
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (contact.phoneEmail.isNotBlank()) {
                    Text(
                        text = contact.phoneEmail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDial) {
                Icon(Icons.Rounded.Call, contentDescription = "Appeler", tint = CallActionGreen)
            }
            IconButton(onClick = onSms) {
                Icon(Icons.Rounded.Sms, contentDescription = "Envoyer SMS", tint = BravPurple)
            }
            if (onWhatsApp != null) {
                IconButton(
                    onClick = onWhatsApp,
                    modifier = Modifier.testTag("contact_whatsapp_button_${contact.id}")
                ) {
                    Icon(Icons.Rounded.Forum, contentDescription = "Ouvrir sur WhatsApp", tint = Color(0xFF16A34A))
                }
            }
            TextButton(onClick = onInvite) {
                Text("Inviter", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
