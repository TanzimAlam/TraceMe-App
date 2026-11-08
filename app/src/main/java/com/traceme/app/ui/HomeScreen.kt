package com.traceme.app.ui

import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.traceme.app.data.EmergencyContact
import com.traceme.app.data.NearbyHelp
import com.traceme.app.data.Place
import com.traceme.app.data.Repo
import com.traceme.app.data.WatchState
import com.traceme.app.emergency.EmergencyDispatcher
import com.traceme.app.emergency.NearbyServices
import com.traceme.app.location.TrackingService
import com.traceme.app.util.Perms
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenContacts: () -> Unit,
    onOpenActivity: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(repo.loadSettings()) }
    var contacts by remember { mutableStateOf(repo.loadContacts()) }
    var tick by remember { mutableStateOf(0L) }
    var showQuiet by remember { mutableStateOf(false) }
    var quietChoice by remember { mutableStateOf(30) }
    var nearby by remember { mutableStateOf<NearbyHelp?>(null) }
    var nearbyBusy by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) TrackingService.start(context)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
            if (tick % 5L == 0L) {
                settings = repo.loadSettings()
                contacts = repo.loadContacts()
            }
        }
    }

    val snapshot = remember(tick) { TrackingService.snapshot() }
    val account = repo.currentAccount()
    val watching = snapshot.running
    val progress = if (settings.stuckSeconds > 0) {
        snapshot.stillForMillis.toFloat() / (settings.stuckSeconds * 1000f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Good ${greeting()}${if (account != null) ", ${account.name.substringBefore(' ')}" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
                Text(
                    "TraceMe",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Pink, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.People, contentDescription = null, tint = Berry)
            }
        }

        // ---- status card ----
        SoftCard(
            container = when {
                snapshot.state == WatchState.SOS -> Color(0xFFF8DAD7)
                snapshot.state == WatchState.PROMPTING -> Color(0xFFFBE8CF)
                snapshot.state == WatchState.QUIET -> Sand
                watching -> Color(0xFFE6F2EC)
                else -> PinkSoft
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = when (snapshot.state) {
                        WatchState.WATCHING -> if (watching) "Watching" else "Ready"
                        WatchState.PROMPTING -> "Check-in"
                        WatchState.QUIET -> "Paused"
                        WatchState.SOS -> "SOS"
                        WatchState.IDLE -> "Idle"
                    },
                    color = when (snapshot.state) {
                        WatchState.WATCHING -> SafeGreen
                        WatchState.PROMPTING -> WarnAmber
                        WatchState.QUIET -> WarnAmber
                        WatchState.SOS -> Danger
                        WatchState.IDLE -> InkFaint
                    }
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (watching) "GPS ±${snapshot.accuracy.toInt()} m" else "Watch off",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft
                )
            }

            Text(
                when {
                    snapshot.state == WatchState.SOS -> "Help is on the way"
                    snapshot.state == WatchState.PROMPTING -> "Are you okay?"
                    snapshot.state == WatchState.QUIET -> "Checks paused"
                    watching -> "You're covered"
                    else -> "Not watching yet"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Text(
                when {
                    snapshot.state == WatchState.PROMPTING ->
                        "Answer in ${snapshot.promptRemainingMillis / 1000}s or we alert your contacts."
                    snapshot.state == WatchState.QUIET ->
                        "Resuming in ${formatDuration(snapshot.quietRemainingMillis)}."
                    watching ->
                        "Still for ${formatDuration(snapshot.stillForMillis)} · check-in at ${formatDuration(settings.stuckSeconds * 1000L)}."
                    else -> "Start a watch before you head out tonight."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft
            )

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StillnessGauge(
                    progress = if (watching) progress else 0f,
                    centerTitle = when {
                        snapshot.state == WatchState.QUIET -> formatDuration(snapshot.quietRemainingMillis)
                        snapshot.state == WatchState.PROMPTING -> "${snapshot.promptRemainingMillis / 1000}s"
                        watching -> formatDuration(snapshot.stillForMillis)
                        else -> "0:00"
                    },
                    centerSubtitle = when {
                        snapshot.state == WatchState.QUIET -> "paused"
                        snapshot.state == WatchState.PROMPTING -> "left to answer"
                        watching -> "without moving"
                        else -> "start to begin"
                    },
                    progressColor = when (snapshot.state) {
                        WatchState.SOS -> Danger
                        WatchState.PROMPTING -> WarnAmber
                        WatchState.QUIET -> WarnAmber
                        else -> if (watching) SafeGreen else InkFaint
                    }
                )
            }

            HorizontalDivider(color = OutlineWarm)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCell("Contacts", contacts.size.toString())
                StatCell("Hotline", settings.emergencyNumber)
                StatCell("Pause", "${settings.quietMinutes} min")
            }
        }

        // ---- controls ----
        if (snapshot.state == WatchState.PROMPTING) {
            ActionButton("I'm safe", Icons.Rounded.CheckCircle, container = SafeGreen) {
                TrackingService.confirmSafe(context)
                Toast.makeText(context, "Marked safe. Watch continues.", Toast.LENGTH_SHORT).show()
            }
        }

        if (!watching) {
            ActionButton("Start night watch", Icons.Rounded.PlayArrow) {
                if (Perms.hasLocation(context)) {
                    TrackingService.start(context)
                    Toast.makeText(context, "Night watch started", Toast.LENGTH_SHORT).show()
                } else {
                    permissionLauncher.launch(Perms.location)
                }
            }
        } else {
            QuietButton(
                "Pause checks for ${settings.quietMinutes} min",
                Icons.Rounded.PauseCircle
            ) {
                quietChoice = settings.quietMinutes
                showQuiet = true
            }
            QuietButton("End night watch", Icons.Rounded.Stop) {
                TrackingService.stop(context)
            }
        }

        if (watching && !snapshot.hasFix) {
            Text(
                "Waiting for a GPS fix. Stand outside or near a window for a moment.",
                style = MaterialTheme.typography.bodySmall,
                color = WarnAmber
            )
        }

        // ---- contacts ----
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Emergency contacts")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenContacts) { Text("Manage") }
            }
            if (contacts.isEmpty()) {
                Text(
                    "No contacts yet. Add someone who would come looking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Danger
                )
            } else {
                contacts.forEach { contact: EmergencyContact ->
                    Text(
                        "${contact.name}${if (contact.relation.isNotBlank()) " · ${contact.relation}" else ""} — ${contact.phone.ifBlank { contact.email }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink
                    )
                }
            }
        }

        // ---- emergency ----
        SoftCard(container = Color(0xFFFBE3E1)) {
            SectionLabel("If something feels wrong")
            Text(
                "Calls ${settings.emergencyNumber} and texts your ${contacts.size} contact(s) your location plus the nearest police and hospital numbers.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            ActionButton("Get help now", Icons.Rounded.Call, container = Danger) {
                EmergencyDispatcher.dispatch(
                    context,
                    if (snapshot.hasFix) snapshot.latitude else null,
                    if (snapshot.hasFix) snapshot.longitude else null,
                    snapshot.accuracy,
                    "You pressed Get help now"
                )
            }
        }

        // ---- nearby ----
        SoftCard {
            SectionLabel("Nearby help")
            Text(
                "Police stations and hospitals around you, from OpenStreetMap.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            when {
                nearbyBusy -> Text("Looking around you…", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                nearby == null -> QuietButton(
                    "Find police & hospitals",
                    Icons.Rounded.LocalPolice
                ) {
                    if (!snapshot.hasFix) {
                        Toast.makeText(context, "Waiting for a GPS fix first.", Toast.LENGTH_SHORT).show()
                    } else {
                        nearbyBusy = true
                        scope.launch {
                            nearby = NearbyServices.find(snapshot.latitude, snapshot.longitude)
                            nearbyBusy = false
                        }
                    }
                }
                else -> {
                    val help = nearby ?: NearbyHelp()
                    if (help.police.isEmpty() && help.hospitals.isEmpty()) {
                        Text(
                            "No mapped stations nearby. Call ${settings.emergencyNumber} instead.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink
                        )
                    }
                    help.police.forEach { place -> PlaceRow(place, "Police") { EmergencyDispatcher.dial(context, place.phone) } }
                    help.hospitals.forEach { place -> PlaceRow(place, "Hospital") { EmergencyDispatcher.dial(context, place.phone) } }
                }
            }
        }

        TextButton(onClick = onOpenActivity) {
            Text("View activity log", color = Berry)
        }

        Spacer(Modifier.width(8.dp))
    }

    if (showQuiet) {
        AlertDialog(
            onDismissRequest = { showQuiet = false },
            title = { Text("Pause checks") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "No check-ins and no alerts until the pause ends — for traffic, a long wait, or a chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft
                    )
                    ChipRow(
                        title = "Pause for",
                        options = listOf(15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "1 hr"),
                        selected = quietChoice,
                        onSelect = { quietChoice = it }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    TrackingService.quiet(context, quietChoice)
                    showQuiet = false
                    Toast.makeText(context, "Paused for $quietChoice min", Toast.LENGTH_SHORT).show()
                }) { Text("Pause") }
            },
            dismissButton = {
                TextButton(onClick = { showQuiet = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlaceRow(place: Place, kind: String, onCall: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.bodyMedium, color = Ink)
            Text(
                "$kind · ${place.distanceMeters} m" +
                    if (place.phone.isNotBlank()) " · ${place.phone}" else " · no number listed",
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft
            )
        }
        if (place.phone.isNotBlank()) {
            IconButton(onClick = onCall) {
                Icon(Icons.Rounded.Call, contentDescription = "Call", tint = SafeGreen)
            }
        }
    }
}

private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "morning"
        hour < 17 -> "afternoon"
        hour < 21 -> "evening"
        else -> "night"
    }
}
