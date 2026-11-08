package com.traceme.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.traceme.app.data.EmergencyContact
import com.traceme.app.data.Repo
import java.util.UUID

@Composable
fun ContactsScreen() {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }
    var contacts by remember { mutableStateOf(repo.loadContacts()) }
    var showAdd by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Emergency contacts",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Text(
            "When a check-in is missed, every contact here gets an SMS with your last location, " +
                "the nearest police station and the nearest hospital numbers.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoft
        )

        contacts.forEach { contact ->
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            contact.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink
                        )
                        Text(
                            listOf(contact.relation, contact.phone, contact.email)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft
                        )
                    }
                    IconButton(onClick = { contacts = repo.removeContact(contact.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = Danger)
                    }
                }
            }
        }

        if (contacts.isEmpty()) {
            SoftCard(container = Sand) {
                Text(
                    "No contacts yet. Add at least one person who would come looking for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
            }
        }

        ActionButton("Add contact", Icons.Rounded.Add) { showAdd = true }

        Text(
            "Tip: add someone who is usually awake at night, and someone nearby.",
            style = MaterialTheme.typography.bodySmall,
            color = InkFaint
        )
    }

    if (showAdd) {
        ContactDialog(
            onDismiss = { showAdd = false },
            onSave = { contact ->
                contacts = repo.addContact(contact)
                showAdd = false
            }
        )
    }
}

@Composable
fun ContactDialog(
    onDismiss: () -> Unit,
    onSave: (EmergencyContact) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { typed ->
                        phone = typed.filter { it.isDigit() || it == '+' || it == ' ' }.take(20)
                    },
                    label = { Text("Mobile number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("Relationship (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = Danger, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "Give this contact a name."
                    return@TextButton
                }
                if (phone.isBlank() && email.isBlank()) {
                    error = "Add a phone number or an email."
                    return@TextButton
                }
                onSave(
                    EmergencyContact(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        phone = phone.trim(),
                        email = email.trim(),
                        relation = relation.trim()
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
