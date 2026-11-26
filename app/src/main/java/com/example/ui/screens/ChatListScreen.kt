package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Conversation
import com.example.model.ConversationPref
import com.example.model.User
import com.example.ui.components.bravOutlinedTextFieldColors
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme

@Composable
fun ConversationActionsDialog(
    title: String,
    pref: ConversationPref?,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleUnread: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BravTheme.colors.modalSurface,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onTogglePin) {
                    Text(if (pref?.pinned == true) "Désépingler" else "Épingler en haut")
                }
                TextButton(onClick = onToggleArchive) {
                    Text(if (pref?.archived == true) "Désarchiver" else "Archiver la discussion")
                }
                TextButton(onClick = onToggleMute) {
                    Text(if (pref?.muted == true) "Réactiver notifications" else "Mettre en sourdine")
                }
                TextButton(onClick = onToggleLock) {
                    Text(if (pref?.locked == true) "Déverrouiller" else "Verrouiller (Biométrie/PIN)")
                }
                TextButton(onClick = onToggleUnread) {
                    Text(if (pref?.markedUnread == true) "Marquer comme lu" else "Marquer comme non lu")
                }
                TextButton(onClick = onRequestDelete) {
                    Text("Supprimer la conversation", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

@Composable
fun DeleteConversationDialog(
    conv: Conversation,
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BravTheme.colors.modalSurface,
        title = { Text("Supprimer la conversation ?") },
        text = {
            Text(
                if (conv.isGroup) "Voulez-vous supprimer ou quitter le groupe « $title » ?"
                else "La conversation avec $title et ses messages seront définitivement supprimés."
            )
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = BravRed, contentColor = Color.White),
                onClick = onConfirm
            ) { Text("Supprimer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun CreateGroupDialog(
    savedContacts: List<User>,
    onCreate: (List<User>, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<User>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BravTheme.colors.modalSurface,
        title = { Text("Créer un groupe BRAV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du groupe") },
                    singleLine = true,
                    colors = bravOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    colors = bravOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Membres (${savedContacts.size} contacts) :", style = MaterialTheme.typography.labelLarge)
                if (savedContacts.isEmpty()) {
                    Text("Ajoutez d'abord des contacts dans l'onglet Contacts.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(modifier = Modifier.height(160.dp)) {
                        items(savedContacts, key = { it.userId }) { u ->
                            val checked = selected.any { it.userId == u.userId }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) selected.add(u) else selected.removeAll { it.userId == u.userId }
                                    }
                                )
                                Text(u.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && selected.isNotEmpty(),
                onClick = { onCreate(selected.toList(), name, desc) }
            ) { Text("Créer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun JoinGroupByCodeDialog(
    onJoin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BravTheme.colors.modalSurface,
        title = { Text("Rejoindre un groupe par code") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text("Code d'invitation") },
                singleLine = true,
                colors = bravOutlinedTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                enabled = code.isNotBlank(),
                onClick = { onJoin(code) }
            ) { Text("Rejoindre") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
