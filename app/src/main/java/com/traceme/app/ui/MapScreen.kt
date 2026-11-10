package com.traceme.app.ui

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.traceme.app.R
import com.traceme.app.data.Repo
import com.traceme.app.data.Trip
import com.traceme.app.data.Waypoint
import com.traceme.app.location.SpotLocation
import com.traceme.app.location.TrackingService
import com.traceme.app.util.Perms
import kotlinx.coroutines.delay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val FALLBACK_LAT = 23.8103
private const val FALLBACK_LON = 90.4125

private enum class PickTarget { NONE, START, DEST }

/**
 * The route map: pin where the walk starts and where it ends, then watch your
 * own position move across the map while the night watch is running.
 */
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }

    var trip by remember { mutableStateOf(repo.loadTrip()) }
    var me by remember { mutableStateOf<Waypoint?>(null) }
    var pickTarget by remember { mutableStateOf(PickTarget.NONE) }
    var watching by remember { mutableStateOf(TrackingService.isRunning) }
    var locationOff by remember { mutableStateOf(false) }
    var fixAt by remember { mutableStateOf(0L) }
    var startAfterPermission by remember { mutableStateOf(false) }

    fun applyPoint(isStart: Boolean, latitude: Double, longitude: Double, label: String = "") {
        val fallback = if (isStart) "Start point" else "Destination"
        val point = Waypoint(latitude, longitude, label.ifBlank { fallback })
        val updated = if (isStart) trip.copy(start = point) else trip.copy(destination = point)
        trip = updated
        repo.saveTrip(updated)
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setTilesScaledToDpi(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(FALLBACK_LAT, FALLBACK_LON))
        }
    }

    val startMarker = remember {
        Marker(mapView).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_pin_start)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            infoWindow = null
            isEnabled = false
        }
    }
    val destMarker = remember {
        Marker(mapView).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_pin_dest)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            infoWindow = null
            isEnabled = false
        }
    }
    val meMarker = remember {
        Marker(mapView).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_pin_me)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            infoWindow = null
            isEnabled = false
        }
    }
    val routeLine = remember {
        Polyline(mapView).apply {
            outlinePaint.color = 0xFFA2542F.toInt()
            outlinePaint.strokeWidth = 8f
            outlinePaint.pathEffect =
                android.graphics.DashPathEffect(floatArrayOf(22f, 16f), 0f)
            isEnabled = false
        }
    }
    val liveLine = remember {
        Polyline(mapView).apply {
            outlinePaint.color = 0xFF2F7D63.toInt()
            outlinePaint.strokeWidth = 10f
            isEnabled = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            if (startAfterPermission) {
                startAfterPermission = false
                TrackingService.start(context)
                watching = true
            }
        } else {
            Toast.makeText(
                context,
                "Location permission is needed to show you on the map.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun startWatchNow() {
        if (Perms.hasLocation(context)) {
            TrackingService.start(context)
            watching = true
            Toast.makeText(context, "Night watch started", Toast.LENGTH_SHORT).show()
        } else {
            startAfterPermission = true
            permissionLauncher.launch(Perms.location)
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(mapView) {
        mapView.overlays.add(routeLine)
        mapView.overlays.add(liveLine)
        mapView.overlays.add(startMarker)
        mapView.overlays.add(destMarker)
        mapView.overlays.add(meMarker)
        mapView.overlays.add(
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean =
                    when (pickTarget) {
                        PickTarget.START -> {
                            applyPoint(true, p.latitude, p.longitude)
                            pickTarget = PickTarget.NONE
                            true
                        }
                        PickTarget.DEST -> {
                            applyPoint(false, p.latitude, p.longitude)
                            pickTarget = PickTarget.NONE
                            true
                        }
                        PickTarget.NONE -> false
                    }

                override fun longPressHelper(p: GeoPoint): Boolean = false
            })
        )
        mapView.invalidate()
    }

    // Live position: from the running watch when it has one, otherwise a fresh fix.
    LaunchedEffect(Unit) {
        while (true) {
            val snapshot = TrackingService.snapshot()
            watching = snapshot.running
            if (snapshot.hasFix) {
                me = Waypoint(snapshot.latitude, snapshot.longitude)
            } else {
                val spot = SpotLocation.current(context, 9_000L)
                locationOff = spot.locationOff
                spot.location?.let {
                    me = Waypoint(it.latitude, it.longitude)
                    fixAt = SystemClock.elapsedRealtime()
                }
            }
            delay(if (snapshot.running) 1_000L else 7_000L)
        }
    }

    LaunchedEffect(trip, me) {
        val start = trip.start
        val destination = trip.destination
        startMarker.isEnabled = start != null
        start?.let { startMarker.position = GeoPoint(it.latitude, it.longitude) }
        destMarker.isEnabled = destination != null
        destination?.let { destMarker.position = GeoPoint(it.latitude, it.longitude) }
        meMarker.isEnabled = me != null
        me?.let { meMarker.position = GeoPoint(it.latitude, it.longitude) }

        routeLine.isEnabled = start != null && destination != null
        if (start != null && destination != null) {
            routeLine.setPoints(
                listOf(
                    GeoPoint(start.latitude, start.longitude),
                    GeoPoint(destination.latitude, destination.longitude)
                )
            )
        }

        val mine = me
        liveLine.isEnabled = mine != null && destination != null
        if (mine != null && destination != null) {
            liveLine.setPoints(
                listOf(
                    GeoPoint(mine.latitude, mine.longitude),
                    GeoPoint(destination.latitude, destination.longitude)
                )
            )
        }
        mapView.invalidate()
    }

    LaunchedEffect(trip.start, trip.destination) {
        val points = listOfNotNull(trip.start, trip.destination)
            .map { GeoPoint(it.latitude, it.longitude) }
        if (points.size >= 2) {
            try {
                mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, 150)
            } catch (e: Exception) {
                // The map may not be laid out yet; the next interaction will fit it.
            }
        }
    }

    LaunchedEffect(me != null) {
        if (me != null && trip.start == null && trip.destination == null) {
            me?.let {
                mapView.controller.setZoom(16.5)
                mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
            }
        }
    }

    val remaining = me?.let { mine ->
        trip.destination?.let { distanceMeters(mine.latitude, mine.longitude, it.latitude, it.longitude) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Your route",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Text(
                    if (watching) "Live · your position moves on the map"
                    else "Pin where you start and where you're going",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
            }
            StatusPill(
                text = if (watching) "Live" else "Planning",
                color = if (watching) SafeGreen else InkFaint
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(Sky, RoundedCornerShape(20.dp))
        ) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            if (pickTarget != PickTarget.NONE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(10.dp)
                        .background(CardWarm, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (pickTarget == PickTarget.START) "Tap the map to place your start"
                        else "Tap the map to place your destination",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink
                    )
                }
            }

            Text(
                "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )

            OutlinedButton(
                onClick = {
                    val mine = me
                    if (mine == null) {
                        Toast.makeText(context, "Waiting for your location…", Toast.LENGTH_SHORT).show()
                    } else {
                        mapView.controller.setZoom(17.0)
                        mapView.controller.animateTo(GeoPoint(mine.latitude, mine.longitude))
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Me")
            }
        }

        Column(Modifier.padding(12.dp)) {
            SoftCard(container = SurfaceWarm) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = SafeGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Start", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                        Text(
                            trip.start?.pretty() ?: "Not set",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Place,
                        contentDescription = null,
                        tint = Clay,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Destination", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                        Text(
                            trip.destination?.pretty() ?: "Not set",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink
                        )
                    }
                }

                if (remaining != null) {
                    Text(
                        "${formatMeters(remaining)} to your destination",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Clay,
                        fontWeight = FontWeight.Medium
                    )
                } else if (trip.destination != null) {
                    Text(
                        if (me == null) "Waiting for your position…" else "Distance to destination appears once you have a fix.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
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
                        }) { Text("Turn on") }
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { pickTarget = PickTarget.START }) { Text("Pin start") }
                    OutlinedButton(onClick = { pickTarget = PickTarget.DEST }) { Text("Pin destination") }
                    OutlinedButton(onClick = {
                        val mine = me
                        if (mine == null) {
                            Toast.makeText(context, "Waiting for your location…", Toast.LENGTH_SHORT).show()
                        } else {
                            applyPoint(true, mine.latitude, mine.longitude, "My start")
                        }
                    }) { Text("Start = me") }
                    if (trip.start != null || trip.destination != null) {
                        TextButton(onClick = {
                            trip = Trip()
                            repo.saveTrip(trip)
                            pickTarget = PickTarget.NONE
                        }) { Text("Clear") }
                    }
                }

                if (watching) {
                    ActionButton("End night watch", Icons.Rounded.Stop, container = Danger) {
                        TrackingService.stop(context)
                        watching = false
                    }
                } else {
                    ActionButton("Start night watch", Icons.Rounded.PlayArrow) { startWatchNow() }
                }
                if (trip.destination == null) {
                    Text(
                        "Tip: pin a destination so you can see how far you still have to go.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                }
            }
        }
    }
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun formatMeters(meters: Double): String =
    if (meters >= 1000) "%.1f km".format(meters / 1000.0) else "${meters.toInt()} m"
