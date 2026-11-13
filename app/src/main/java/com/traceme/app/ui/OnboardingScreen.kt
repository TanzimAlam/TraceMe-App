package com.traceme.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.traceme.app.data.EmergencyContact
import com.traceme.app.data.Repo
import com.traceme.app.util.Perms

private data class PermissionState(
    val location: Boolean,
    val notifications: Boolean,
    val motion: Boolean,
    val sms: Boolean,
    val call: Boolean
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }

    var step by remember { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }
    var contacts by remember { mutableStateOf(repo.loadContacts()) }
    var settings by remember { mutableStateOf(repo.loadSettings()) }
    var showAddContact by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    val perms = remember(refresh) {
        PermissionState(
            location = Perms.hasLocation(context),
            notifications = Perms.hasNotifications(context),
            motion = Perms.hasMotion(context),
            sms = Perms.hasSms(context),
            call = Perms.hasCall(context)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                TextButton(onClick = { step-- }) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
            Text(
                when (step) {
                    0 -> "Permissions"
                    1 -> "Emergency contacts"
                    else -> "Timing & hotline"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(width = 34.dp, height = 5.dp)
                        .background(if (index <= step) Clay else PeachSoft, CircleShape)
                )
            }
        }

        when (step) {
            0 -> {
                Text(
                    "Overwatch needs these to keep you safe. You can change them later in Android settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
                SoftCard {
                    PermissionRow(
                        icon = Icons.Rounded.MyLocation,
                        title = "Location (required)",
                        description = "Tracks you during a watch and pins where you stopped.",
                        granted = perms.location,
                        onRequest = { launcher.launch(Perms.location) }
                    )
                    PermissionRow(
                        icon = Icons.Rounded.Notifications,
                        title = "Notifications",
                        description = "How the check-in reaches you, even on the lock screen.",
                        granted = perms.notifications,
                        onRequest = {
                            val permission = Perms.notifications()
                            if (permission != null) launcher.launch(arrayOf(permission))
                        }
                    )
                    PermissionRow(
                        icon = Icons.Rounded.DirectionsWalk,
                        title = "Motion & steps",
                        description = "Steps and movement tell us you're walking, not stuck.",
                        granted = perms.motion,
                        onRequest = {
                            val permission = Perms.activityRecognition()
                            if (permission != null) launcher.launch(arrayOf(permission))
                        }
                    )
                    PermissionRow(
                        icon = Icons.Rounded.Sms,
                        title = "SMS & calls",
                        description = "Sends the SOS text and dials your emergency number.",
                        granted = perms.sms && perms.call,
                        onRequest = {
                            launcher.launch(
                                arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE)
                            )
                        }
                    )
                }
                if (!perms.location) {
                    Text(
                        "Location is required to start a watch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Danger
                    )
                }
                ActionButton(
                    text = "Continue",
                    icon = Icons.Rounded.CheckCircle,
                    enabled = perms.location
                ) { step = 1 }
            }

            1 -> {
                Text(
                    "Who should we alert? The SOS text goes to every contact here with your last location and the nearest police and hospital numbers. Add how each person is related to you — that is what the guard message says if someone tampers with your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
                contacts.forEach { contact ->
                    SoftCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(contact.name, style = MaterialTheme.typography.titleMedium, color = Ink)
                                Text(
                                    listOf(contact.relation, contact.phone, contact.email)
                                        .filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSoft
                                )
                            }
                            TextButton(onClick = { contacts = repo.removeContact(contact.id) }) {
                                Text("Remove", color = Danger)
                            }
                        }
                    }
                }
                if (contacts.isEmpty()) {
                    SoftCard(container = Sky) {
                        Text(
                            "Add at least one person. A friend, a parent, a roommate — someone who would come looking.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink
                        )
                    }
                }
                QuietButton("Add a contact", Icons.Rounded.Call) { showAddContact = true }
                ActionButton(
                    text = "Continue",
                    icon = Icons.Rounded.CheckCircle,
                    enabled = contacts.isNotEmpty()
                ) { step = 2 }
                if (contacts.isEmpty()) {
                    Text(
                        "One emergency contact is required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Danger
                    )
                }
            }

            else -> {
                EmergencyNumberSection(settings, onChange = { settings = it })
                TimingSection(settings, onChange = { settings = it })
                SoftCard(container = Peach) {
                    Text(
                        "Overwatch will ask “are you okay?” after ${settings.stuckSeconds / 60} min without movement. " +
                            "If you don't answer in ${settings.responseSeconds} s, it texts your contacts your " +
                            "location plus the nearest police and hospital numbers. It never dials for you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink
                    )
                }
                ActionButton("Start using TraceMe", Icons.Rounded.CheckCircle, container = SafeGreen) {
                    repo.saveSettings(settings)
                    repo.onboarded = true
                    repo.log("account", "Setup completed")
                    onFinished()
                }
            }
        }

        Spacer(Modifier.width(4.dp))
        Text(
            "You can change everything later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = InkFaint
        )
    }

    if (showAddContact) {
        ContactDialog(
            onDismiss = { showAddContact = false },
            onSave = { contact: EmergencyContact ->
                contacts = repo.addContact(contact)
                showAddContact = false
            }
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(if (granted) Color(0xFFE1F0E9) else PeachSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (granted) SafeGreen else Clay,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text(description, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        if (granted) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Granted", tint = SafeGreen)
        } else {
            TextButton(onClick = onRequest) { Text("Allow") }
        }
    }
}
