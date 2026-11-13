package com.traceme.app.ui

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.traceme.app.data.EmergencyContact
import com.traceme.app.data.GeoSearch
import com.traceme.app.data.NearbyHelp
import com.traceme.app.data.Place
import com.traceme.app.data.Repo
import com.traceme.app.data.Trip
import com.traceme.app.data.WatchState
import com.traceme.app.data.Waypoint
import com.traceme.app.data.distanceMeters
import com.traceme.app.emergency.EmergencyDispatcher
import com.traceme.app.emergency.NearbyServices
import com.traceme.app.location.SpotLocation
import com.traceme.app.location.TrackingService
import com.traceme.app.location.WatchSnapshot
import com.traceme.app.util.Perms
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PoliceBlue = Color(0xFF2E6D8E)

@Composable
fun HomeScreen(
    onOpenContacts: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMap: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(repo.loadSettings()) }
    var contacts by remember { mutableStateOf(repo.loadContacts()) }
    var trip by remember { mutableStateOf(repo.loadTrip()) }
    var tick by remember { mutableStateOf(0L) }
    var me by remember { mutableStateOf<Waypoint?>(null) }
    var showQuiet by remember { mutableStateOf(false) }
    var quietChoice by remember { mutableStateOf(settings.quietSeconds) }
    var nearby by remember { mutableStateOf<NearbyHelp?>(null) }
    var nearbyBusy by remember { mutableStateOf(false) }
    var nearbyFailed by remember { mutableStateOf(false) }
    var locationOff by remember { mutableStateOf(false) }
    var startAfterPermission by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }

    // Destination search — the only place a destination is ever asked for.
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Waypoint>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var editingDestination by remember { mutableStateOf(trip.destination == null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            if (startAfterPermission) {
                startAfterPermission = false
                TrackingService.start(context)
            }
        } else {
            Toast.makeText(context, "Location permission is needed for this.", Toast.LENGTH_LONG).show()
        }
    }

    fun requireLocation(then: () -> Unit) {
        if (Perms.hasLocation(context)) {
            then()
        } else {
            startAfterPermission = false
            permissionLauncher.launch(Perms.location)
        }
    }

    fun findNearbyHelp() {
        scope.launch {
            nearbyBusy = true
            nearbyFailed = false
            val snapshot = TrackingService.snapshot()
            var latitude = snapshot.latitude
            var longitude = snapshot.longitude
            if (!snapshot.hasFix) {
                val spot = SpotLocation.current(context)
                locationOff = spot.locationOff
                spot.location?.let {
                    latitude = it.latitude
                    longitude = it.longitude
                    me = Waypoint(it.latitude, it.longitude)
                }
                if (spot.location == null) {
                    nearbyBusy = false
                    if (!spot.locationOff) {
                        Toast.makeText(
                            context,
                            "No GPS fix yet. Step outside or near a window and try again.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
            }

            // The bundled numbers come from inside the APK, so this line is
            // instant and works with no data at all.
            nearby = NearbyServices.local(latitude, longitude)
            nearbyBusy = false

            // Then, only as a bonus, the map adds the exact thana and hospital
            // around you. It can never block or empty the list above.
            val mapped = NearbyServices.remote(latitude, longitude)
            if (!mapped.isEmpty) {
                nearby = NearbyServices.merge(nearby ?: NearbyHelp(), mapped)
            }
        }
    }

    fun sendSos() {
        if (contacts.isEmpty()) {
            Toast.makeText(
                context,
                "Add an emergency contact first — there is nobody to text.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val snapshot = TrackingService.snapshot()
        val latitude = if (snapshot.hasFix) snapshot.latitude else me?.latitude
        val longitude = if (snapshot.hasFix) snapshot.longitude else me?.longitude
        EmergencyDispatcher.dispatch(
            context,
            latitude,
            longitude,
            snapshot.accuracy,
            "You pressed Get help now"
        )
        Toast.makeText(
            context,
            "SOS text sent to ${contacts.size} contact(s)",
            Toast.LENGTH_LONG
        ).show()
    }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sendSos()
        } else {
            Toast.makeText(
                context,
                "Without SMS permission Overwatch cannot text your contacts.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun requestHelp() {
        if (Perms.hasSms(context)) sendSos() else smsLauncher.launch(Manifest.permission.SEND_SMS)
    }

    fun startWatchNow() {
        if (Perms.hasLocation(context)) {
            TrackingService.start(context)
            Toast.makeText(context, "Night watch started", Toast.LENGTH_SHORT).show()
        } else {
            startAfterPermission = true
            permissionLauncher.launch(Perms.location)
        }
    }

    fun chooseDestination(point: Waypoint) {
        val start = me ?: trip.start
        val updated = Trip(start = start, destination = point)
        trip = updated
        repo.saveTrip(updated)
        editingDestination = false
        query = ""
        results = emptyList()
        Toast.makeText(context, "Destination saved", Toast.LENGTH_SHORT).show()
    }

    // One clock for the whole screen: live position, then a slower settings/trip refresh.
    LaunchedEffect(Unit) {
        var lastSpotAt = 0L
        while (true) {
            delay(1_000)
            tick++
            val snapshot = TrackingService.snapshot()
            if (snapshot.hasFix) {
                me = Waypoint(snapshot.latitude, snapshot.longitude)
            } else if (me == null && SystemClock.elapsedRealtime() - lastSpotAt > 12_000L) {
                lastSpotAt = SystemClock.elapsedRealtime()
                val spot = SpotLocation.current(context, 5_000L)
                locationOff = spot.locationOff
                spot.location?.let { me = Waypoint(it.latitude, it.longitude) }
            }
            if (tick % 5L == 0L) {
                settings = repo.loadSettings()
                contacts = repo.loadContacts()
                trip = repo.loadTrip()
            }
        }
    }

    // Debounced destination search against the map service.
    LaunchedEffect(query) {
        if (query.trim().length < 3) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(450)
        results = GeoSearch.suggest(query, me)
        searching = false
    }

    val snapshot = remember(tick) { TrackingService.snapshot() }
    val account = repo.currentAccount()
    val watching = snapshot.running
    val progress = if (settings.stuckSeconds > 0) {
        snapshot.stillForMillis.toFloat() / (settings.stuckSeconds * 1000f)
    } else {
        0f
    }
    val distanceToGo = me?.let { mine ->
        trip.destination?.let {
            distanceMeters(mine.latitude, mine.longitude, it.latitude, it.longitude).toInt()
        }
    }

    Box(Modifier.fillMaxSize().background(Wash.page)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            /* ---------- header ---------- */
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Good ${greeting()}${if (account != null) ", ${account.name.substringBefore(' ')}" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Clay
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Overwatch",
                        style = MaterialTheme.typography.displaySmall,
                        color = Ink
                    )
                    Text(
                        SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date()),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkFaint
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Glass, CircleShape)
                        .border(1.dp, OutlineWarm, CircleShape)
                        .clickable { onOpenContacts() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.People, contentDescription = "Contacts", tint = Clay)
                }
            }

            /* ---------- destination: asked once, right here ---------- */
            SoftCard(brush = Wash.hero) {
                SectionHeader("Tonight's walk", accent = ClayDark)

                if (!editingDestination || trip.destination == null) {
                    if (trip.destination != null) {
                        DestinationChosen(
                            trip = trip,
                            distanceToGo = distanceToGo,
                            onEdit = { editingDestination = true },
                            onClear = {
                                val cleared = Trip(start = trip.start, destination = null)
                                trip = cleared
                                repo.saveTrip(cleared)
                                editingDestination = true
                                query = ""
                            }
                        )
                    } else {
                        Text(
                            "Where are you headed?",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Ink
                        )
                        Text(
                            "We'll draw the walk on your map and count the distance down as you move.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft
                        )
                    }
                }

                if (editingDestination) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp)),
                        placeholder = {
                            Text(
                                "Search a place, road or landmark",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkFaint
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = Clay)
                        },
                        trailingIcon = {
                            when {
                                searching -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Clay
                                )
                                query.isNotEmpty() -> IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = InkFaint)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.72f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.55f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Clay
                        )
                    )

                    if (results.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.6f))
                                .border(1.dp, OutlineWarm.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                        ) {
                            results.forEachIndexed { index, point ->
                                if (index > 0) {
                                    HorizontalDivider(color = OutlineWarm.copy(alpha = 0.6f))
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { chooseDestination(point) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .background(PeachSoft, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.Place,
                                            contentDescription = null,
                                            tint = Clay,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        point.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Ink,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else if (query.trim().length >= 3 && !searching) {
                        Text(
                            "Nothing found for “$query”. Try a house number, a road or a landmark — or drop a pin instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PillButton(
                            text = "Can't find it? Pin the spot",
                            icon = Icons.Rounded.MyLocation,
                            onClick = { showPin = true }
                        )
                    }
                }
            }

            /* ---------- status ---------- */
            SoftCard(
                brush = when {
                    snapshot.state == WatchState.SOS -> Wash.red
                    snapshot.state == WatchState.PROMPTING -> Wash.amber
                    watching && snapshot.state == WatchState.WATCHING -> Wash.mint
                    else -> Wash.sky
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (snapshot.state) {
                            WatchState.WATCHING -> "Live watch"
                            WatchState.PROMPTING -> "Check-in needed"
                            WatchState.QUIET -> "Paused"
                            WatchState.SOS -> "Emergency"
                            WatchState.IDLE -> "Standby"
                        }.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            snapshot.state == WatchState.SOS -> Color.White
                            snapshot.state == WatchState.PROMPTING -> Color.White
                            watching -> Color(0xFF1F5A49)
                            else -> Color(0xFF2A5568)
                        }
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (watching) "GPS ±${snapshot.accuracy.toInt()} m" else "Watch off",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            snapshot.state == WatchState.SOS || snapshot.state == WatchState.PROMPTING ->
                                Color.White.copy(alpha = 0.85f)
                            else -> InkFaint
                        }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StillnessGauge(
                        progress = if (watching || snapshot.state != WatchState.IDLE) progress else 0f,
                        centerTitle = when {
                            snapshot.state == WatchState.QUIET -> formatDuration(snapshot.quietRemainingMillis)
                            snapshot.state == WatchState.PROMPTING -> "${snapshot.promptRemainingMillis / 1000}s"
                            watching -> formatDuration(snapshot.stillForMillis)
                            else -> "0:00"
                        },
                        centerSubtitle = when {
                            snapshot.state == WatchState.QUIET -> "paused"
                            snapshot.state == WatchState.PROMPTING -> "to answer"
                            watching -> "still"
                            else -> "start"
                        },
                        progressColor = when (snapshot.state) {
                            WatchState.SOS -> Color.White
                            WatchState.PROMPTING -> Color.White
                            WatchState.QUIET -> WarnAmber
                            else -> if (watching) SafeGreen else InkFaint
                        },
                        trackColor = if (snapshot.state == WatchState.SOS || snapshot.state == WatchState.PROMPTING) {
                            Color.White.copy(alpha = 0.35f)
                        } else {
                            Peach
                        },
                        diameter = 132.dp
                    )

                    Spacer(Modifier.width(18.dp))

                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            when {
                                snapshot.state == WatchState.SOS -> "Help is on the way"
                                snapshot.state == WatchState.PROMPTING -> "Are you okay?"
                                snapshot.state == WatchState.QUIET -> "Checks paused"
                                watching -> "You're covered"
                                else -> "Nothing running"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (
                                snapshot.state == WatchState.SOS || snapshot.state == WatchState.PROMPTING
                            ) Color.White else Ink
                        )
                        Text(
                            when {
                                snapshot.state == WatchState.PROMPTING ->
                                    "Answer in ${snapshot.promptRemainingMillis / 1000}s or we alert your contacts."
                                snapshot.state == WatchState.QUIET ->
                                    "Back in ${formatDuration(snapshot.quietRemainingMillis)}."
                                watching ->
                                    "Still for ${formatDuration(snapshot.stillForMillis)} · ask at ${settings.stuckLabel}."
                                else -> "Start a watch before you head out tonight."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (
                                snapshot.state == WatchState.SOS || snapshot.state == WatchState.PROMPTING
                            ) Color.White.copy(alpha = 0.92f) else InkSoft
                        )
                    }
                }

                HorizontalDivider(
                    color = if (
                        snapshot.state == WatchState.SOS || snapshot.state == WatchState.PROMPTING
                    ) Color.White.copy(alpha = 0.3f) else OutlineWarm.copy(alpha = 0.8f)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Contacts", contacts.size.toString())
                    StatCell("Hotline", settings.emergencyNumber)
                    StatCell("Pause", settings.quietLabel)
                }
            }

            /* ---------- controls ---------- */
            if (snapshot.state == WatchState.PROMPTING) {
                ActionButton("I'm safe", Icons.Rounded.CheckCircle, container = SafeGreen) {
                    TrackingService.confirmSafe(context)
                    Toast.makeText(context, "Marked safe. Watch continues.", Toast.LENGTH_SHORT).show()
                }
            }

            if (!watching) {
                ActionButton("Start night watch", Icons.Rounded.PlayArrow) { startWatchNow() }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        QuietButton("Pause", Icons.Rounded.PauseCircle) {
                            quietChoice = settings.quietSeconds
                            showQuiet = true
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        QuietButton("End", Icons.Rounded.Stop) { TrackingService.stop(context) }
                    }
                }
            }

            if (watching && !snapshot.hasFix) {
                Text(
                    "Waiting for a GPS fix. Stand outside or near a window for a moment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnAmber
                )
            }

            if (locationOff) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Location services are off on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Danger,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("Turn on", color = Clay) }
                }
            }

            /* ---------- nearby ---------- */
            SoftCard(container = SurfaceWarm) {
                SectionHeader(
                    "Nearby help",
                    accent = SafeGreen,
                    trailing = if (nearby != null) {
                        { TextButton(onClick = { findNearbyHelp() }) { Text("Refresh", color = Clay) } }
                    } else {
                        null
                    }
                )
                Text(
                    "Police stations (thana) and hospitals around you, nearest first. The numbers that " +
                        "work anywhere are at the top, then the ones closest to you — all of them are " +
                        "stored inside the app, so the list appears even with no signal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )

                when {
                    nearbyBusy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Clay
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Getting your position and looking around you…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSoft
                        )
                    }

                    nearby == null -> QuietButton("Show police & hospital hotlines", Icons.Rounded.LocalPolice) {
                        requireLocation { findNearbyHelp() }
                    }

                    else -> {
                        val help = nearby ?: NearbyHelp()
                        if (help.isEmpty) {
                            Text(
                                if (nearbyFailed) {
                                    "Couldn't reach the map service, or nothing is mapped nearby. Call ${settings.emergencyNumber} instead."
                                } else {
                                    "Nothing mapped within a few kilometres. Call ${settings.emergencyNumber} instead."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink
                            )
                        } else {
                            NearbySection(
                                title = "Police & thana",
                                icon = Icons.Rounded.LocalPolice,
                                accent = PoliceBlue,
                                places = help.police,
                                emptyText = "No police station mapped nearby.",
                                hotline = settings.emergencyNumber,
                                onCall = { EmergencyDispatcher.dial(context, it) }
                            )
                            Spacer(Modifier.height(4.dp))
                            NearbySection(
                                title = "Hospitals & ambulance",
                                icon = Icons.Rounded.LocalHospital,
                                accent = WarnAmber,
                                places = help.hospitals,
                                emptyText = "No hospital mapped nearby.",
                                hotline = null,
                                onCall = { EmergencyDispatcher.dial(context, it) }
                            )
                        }
                    }
                }
            }

            /* ---------- emergency ---------- */
            SoftCard(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFFFE3DC), Color(0xFFFFF4EA))
                )
            ) {
                SectionHeader("If something feels wrong", accent = Danger)
                Text(
                    "One tap texts your ${contacts.size} contact(s): that you're in trouble, your live " +
                        "location, and the police station and hospital/ambulance numbers around you. " +
                        "It does not call anyone — dialling ${settings.emergencyNumber} stays your choice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
                ActionButton("Text my contacts now", Icons.Rounded.Sms, container = Danger) {
                    requestHelp()
                }
                QuietButton("Call ${settings.emergencyNumber} myself", Icons.Rounded.Call) {
                    EmergencyDispatcher.dial(context, settings.emergencyNumber)
                }
            }

            /* ---------- contacts ---------- */
            SoftCard(container = CardWarm) {
                SectionHeader(
                    "Emergency contacts",
                    accent = Clay,
                    trailing = { TextButton(onClick = onOpenContacts) { Text("Manage", color = Clay) } }
                )
                if (contacts.isEmpty()) {
                    Text(
                        "No contacts yet. Add someone who would come looking for you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Danger
                    )
                } else {
                    contacts.forEachIndexed { index, contact: EmergencyContact ->
                        if (index > 0) HorizontalDivider(color = OutlineWarm.copy(alpha = 0.7f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    contact.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Ink,
                                    maxLines = 1
                                )
                                Text(
                                    listOf(contact.relation, contact.phone.ifBlank { contact.email })
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSoft,
                                    maxLines = 1
                                )
                            }
                            if (contact.phone.isNotBlank()) {
                                IconButton(onClick = {
                                    EmergencyDispatcher.dial(context, contact.phone)
                                }) {
                                    Icon(Icons.Rounded.Call, contentDescription = "Call", tint = SafeGreen)
                                }
                            }
                        }
                    }
                }
            }

            /* ---------- map shortcut ---------- */
            SoftCard(container = MistBlue) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Place,
                            contentDescription = null,
                            tint = PoliceBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (trip.destination != null) "Your route is on the map" else "Your live position",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink
                        )
                        Text(
                            trip.destination?.pretty() ?: "See yourself on the map in real time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = onOpenMap) { Text("Open", color = Clay) }
                }
            }

            TextButton(onClick = onOpenActivity) {
                Text("View activity log", color = Clay, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showQuiet) {
        AlertDialog(
            onDismissRequest = { showQuiet = false },
            containerColor = SurfaceWarm,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Pause checks", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "No check-ins and no alerts until the pause ends — for traffic, a long wait or a chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft
                    )
                    StepSlider(
                        label = "Pause for",
                        value = quietChoice,
                        range = 900..3600,
                        step = 30,
                        format = { com.traceme.app.data.shortDuration(it) },
                        hint = "30 second steps",
                        onChange = { quietChoice = it }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    TrackingService.quiet(context, quietChoice)
                    showQuiet = false
                    Toast.makeText(
                        context,
                        "Paused for ${com.traceme.app.data.shortDuration(quietChoice)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }) { Text("Pause", color = Clay) }
            },
            dismissButton = {
                TextButton(onClick = { showQuiet = false }) { Text("Cancel", color = InkSoft) }
            }
        )
    }

    if (showPin) {
        PinPicker(
            initial = trip.destination,
            fallback = me,
            onCancel = { showPin = false },
            onConfirm = { point ->
                showPin = false
                chooseDestination(point)
            }
        )
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun DestinationChosen(
    trip: Trip,
    distanceToGo: Int?,
    onEdit: () -> Unit,
    onClear: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(46.dp)
                .background(Clay, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Place,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                trip.destination?.pretty() ?: "",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                when {
                    distanceToGo != null -> "${formatDistance(distanceToGo)} to go"
                    trip.start != null -> "From ${trip.start.pretty()}"
                    else -> "Destination saved"
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
                maxLines = 1
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Change", icon = Icons.Rounded.Edit, onClick = onEdit)
        PillButton("Clear", icon = Icons.Rounded.Close, onClick = onClear)
    }
}

/**
 * One list of nearby places. Police rows are visually louder than hospitals so
 * the first thing you see in an emergency is a thana.
 */
@Composable
private fun NearbySection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    places: List<Place>,
    emptyText: String,
    hotline: String?,
    onCall: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .background(accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            if (hotline != null) {
                PillButton(
                    text = "Hotline $hotline",
                    filled = true,
                    accent = accent,
                    onClick = { onCall(hotline) }
                )
            }
        }

        if (places.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = InkFaint)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(accent.copy(alpha = 0.05f))
                    .border(1.dp, accent.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            ) {
                places.forEachIndexed { index, place ->
                    if (index > 0) {
                        HorizontalDivider(color = accent.copy(alpha = 0.12f))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = place.phone.isNotBlank()) {
                                if (place.phone.isNotBlank()) onCall(place.phone)
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .background(accent.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                place.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                listOf(
                                    place.area,
                                    if (place.national) "Nationwide" else formatDistance(place.distanceMeters)
                                ).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = InkFaint
                            )
                            if (place.detail.isNotBlank()) {
                                Text(
                                    place.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSoft,
                                    maxLines = 2
                                )
                            }
                            if (place.phone.isNotBlank()) {
                                Text(
                                    place.phone + if (place.altPhone.isNotBlank()) " · ${place.altPhone}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    "No number on the map — hotline is the way in",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkFaint
                                )
                            }
                        }
                        if (place.phone.isNotBlank()) {
                            IconButton(onClick = { onCall(place.phone) }) {
                                Icon(
                                    Icons.Rounded.Call,
                                    contentDescription = "Call ${place.name}",
                                    tint = accent
                                )
                            }
                        }
                    }
                }
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
