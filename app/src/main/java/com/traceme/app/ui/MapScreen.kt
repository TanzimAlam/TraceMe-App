package com.traceme.app.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.traceme.app.data.Repo
import com.traceme.app.data.Waypoint
import com.traceme.app.data.distanceMeters
import com.traceme.app.location.SpotLocation
import com.traceme.app.location.TrackingService
import com.traceme.app.util.Perms
import kotlinx.coroutines.delay

/**
 * The route screen: your pin, your destination and how far is left. The
 * destination itself is only ever asked for on the home screen.
 */
@Composable
fun MapScreen(onChangeDestination: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }

    var trip by remember { mutableStateOf(repo.loadTrip()) }
    var me by remember { mutableStateOf<Waypoint?>(null) }
    var watching by remember { mutableStateOf(TrackingService.isRunning) }
    var locationOff by remember { mutableStateOf(false) }
    var cameraTarget by remember { mutableStateOf<Waypoint?>(null) }
    var stamp by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            val snapshot = TrackingService.snapshot()
            watching = snapshot.running
            val stored = repo.loadTrip()
            if (stored != trip) trip = stored

            if (snapshot.hasFix) {
                me = Waypoint(snapshot.latitude, snapshot.longitude)
            } else {
                val spot = SpotLocation.current(context, 9_000L)
                locationOff = spot.locationOff
                spot.location?.let { me = Waypoint(it.latitude, it.longitude) }
            }

            if (cameraTarget == null) {
                me?.let {
                    cameraTarget = it
                    stamp++
                }
            }
            delay(if (snapshot.running) 1_000L else 7_000L)
        }
    }

    val remaining = me?.let { mine ->
        trip.destination?.let {
            distanceMeters(mine.latitude, mine.longitude, it.latitude, it.longitude).toInt()
        }
    }

    val scene = MapScene(
        start = trip.start,
        destination = trip.destination,
        me = me,
        center = cameraTarget,
        zoom = 16.5f,
        bounds = listOfNotNull(trip.start, trip.destination),
        stamp = stamp
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wash.page)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Your route",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink
                )
                Text(
                    if (watching) "Live · your dot moves as you walk"
                    else "Your position and how much is left",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
            }
            StatusPill(
                text = if (watching) "Live" else "Standby",
                color = if (watching) SafeGreen else InkFaint
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, OutlineWarm, RoundedCornerShape(28.dp))
                .background(MistBlue)
        ) {
            MapCanvas(scene = scene, modifier = Modifier.fillMaxSize())

            if (!googleMapsConfigured) {
                Text(
                    "© OpenStreetMap contributors",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color.White.copy(alpha = 0.7f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(Color.White, CircleShape)
                ) {
                    TextButton(
                        onClick = {
                            val mine = me
                            if (mine == null) {
                                Toast.makeText(context, "Waiting for your location…", Toast.LENGTH_SHORT)
                                    .show()
                            } else {
                                cameraTarget = mine
                                stamp++
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            Icons.Rounded.MyLocation,
                            contentDescription = "Centre on me",
                            tint = Clay,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SoftCard(container = SurfaceWarm) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Place,
                        contentDescription = null,
                        tint = Clay,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Destination",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkFaint
                        )
                        Text(
                            trip.destination?.pretty() ?: "Not set yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    PillButton(
                        text = if (trip.destination == null) "Choose" else "Change",
                        icon = Icons.Rounded.Edit,
                        onClick = onChangeDestination
                    )
                }

                if (remaining != null) {
                    Text(
                        "${formatDistance(remaining)} to go",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Clay
                    )
                } else if (trip.destination != null) {
                    Text(
                        if (me == null) "Waiting for your position…"
                        else "Distance appears once you have a fix.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                }

                if (trip.start != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Flag,
                            contentDescription = null,
                            tint = SafeGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Started at ${trip.start?.pretty() ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (locationOff) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Location is switched off on this phone.",
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

                Spacer(Modifier.height(2.dp))

                if (watching) {
                    ActionButton("End night watch", Icons.Rounded.Stop, container = Danger) {
                        TrackingService.stop(context)
                        watching = false
                    }
                } else {
                    ActionButton("Start night watch", Icons.Rounded.PlayArrow) {
                        if (Perms.hasLocation(context)) {
                            TrackingService.start(context)
                            watching = true
                            Toast.makeText(context, "Night watch started", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Allow location from Settings → Permissions first.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }
}
