package com.traceme.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.traceme.app.BuildConfig
import com.traceme.app.R
import com.traceme.app.data.Waypoint
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polyline as OsmPolyline

/** Everything the map has to draw, in one comparable value. */
data class MapScene(
    val start: Waypoint? = null,
    val destination: Waypoint? = null,
    /** A point the user is choosing by hand, drawn with a dedicated pin. */
    val pin: Waypoint? = null,
    val me: Waypoint? = null,
    val center: Waypoint? = null,
    val zoom: Float = 16f,
    val bounds: List<Waypoint> = emptyList(),
    /** Bump this to move the camera. */
    val stamp: Long = 0L
)

val googleMapsConfigured: Boolean get() = BuildConfig.MAPS_API_KEY.isNotBlank()

/**
 * Google Maps when an API key is configured, OpenStreetMap otherwise, so the
 * route screen is never a blank grey rectangle.
 */
@Composable
fun MapCanvas(
    scene: MapScene,
    modifier: Modifier = Modifier,
    onTap: ((Double, Double) -> Unit)? = null
) {
    if (googleMapsConfigured) {
        GoogleScene(scene, modifier, onTap)
    } else {
        OsmScene(scene, modifier, onTap)
    }
}

private fun Waypoint.toLatLng(): LatLng = LatLng(latitude, longitude)

private const val FALLBACK_LAT = 23.8103
private const val FALLBACK_LON = 90.4125

/* ------------------------------------------------------------------ *
 * Google Maps
 * ------------------------------------------------------------------ */

@Composable
private fun GoogleScene(
    scene: MapScene,
    modifier: Modifier,
    onTap: ((Double, Double) -> Unit)?
) {
    val camera = rememberCameraPositionState {
        val first = scene.center ?: scene.me ?: scene.bounds.firstOrNull()
        position = CameraPosition.fromLatLngZoom(
            first?.toLatLng() ?: LatLng(FALLBACK_LAT, FALLBACK_LON),
            scene.zoom
        )
    }

    LaunchedEffect(scene.stamp) {
        val bounds = scene.bounds
        when {
            bounds.size >= 2 -> {
                val builder = LatLngBounds.builder()
                bounds.forEach { builder.include(it.toLatLng()) }
                camera.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 150))
            }
            scene.center != null -> camera.animate(
                CameraUpdateFactory.newLatLngZoom(scene.center.toLatLng(), scene.zoom)
            )
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = camera,
        onMapClick = { point ->
            if (onTap == null) {
                false
            } else {
                onTap(point.latitude, point.longitude)
                true
            }
        },
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = true,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            tiltGesturesEnabled = false
        )
    ) {
        val start = scene.start
        val destination = scene.destination
        val me = scene.me

        if (start != null && destination != null) {
            Polyline(
                points = listOf(start.toLatLng(), destination.toLatLng()),
                color = Clay,
                width = 7f,
                pattern = listOf(Dash(26f), Gap(18f))
            )
        }
        if (me != null && destination != null) {
            Polyline(
                points = listOf(me.toLatLng(), destination.toLatLng()),
                color = SafeGreen,
                width = 7f
            )
        }
        if (start != null) {
            Marker(
                state = remember(start) { MarkerState(position = start.toLatLng()) },
                title = "Start",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
            )
        }
        if (destination != null) {
            Marker(
                state = remember(destination) { MarkerState(position = destination.toLatLng()) },
                title = destination.pretty(),
                snippet = "Your destination"
            )
        }
        val pin = scene.pin
        if (pin != null) {
            Marker(
                state = remember(pin) { MarkerState(position = pin.toLatLng()) },
                title = pin.pretty(),
                snippet = "Tap the map to move this pin",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE)
            )
        }
        if (me != null) {
            Circle(
                center = me.toLatLng(),
                radius = 22.0,
                fillColor = Color(0x40356C8A),
                strokeColor = Color(0xFF2E6D8E),
                strokeWidth = 3f
            )
            Marker(
                state = remember(me) { MarkerState(position = me.toLatLng()) },
                title = "You",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            )
        }
    }
}

/* ------------------------------------------------------------------ *
 * OpenStreetMap fallback
 * ------------------------------------------------------------------ */

@Composable
private fun OsmScene(
    scene: MapScene,
    modifier: Modifier,
    onTap: ((Double, Double) -> Unit)?
) {
    val context = LocalContext.current

    // The tap receiver is installed once per MapView, so read the callback
    // through a holder instead of capturing a stale lambda.
    val tapHolder = remember { arrayOfNulls<(Double, Double) -> Unit>(1) }
    tapHolder[0] = onTap

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setTilesScaledToDpi(true)
            controller.setZoom(scene.zoom.toDouble())
            controller.setCenter(GeoPoint(FALLBACK_LAT, FALLBACK_LON))
        }
    }
    val startMarker = remember {
        OsmMarker(mapView).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_pin_start)
            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
            isEnabled = false
        }
    }
    val destMarker = remember {
        OsmMarker(mapView).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_pin_dest)
            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
            isEnabled = false
        }
    }
    val meMarker = remember {
        OsmMarker(mapView).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_pin_me)
            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
            isEnabled = false
        }
    }
    val pinMarker = remember {
        OsmMarker(mapView).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_pin_dest)
            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
            isEnabled = false
        }
    }
    val routeLine = remember {
        OsmPolyline(mapView).apply {
            outlinePaint.color = 0xFFA2542F.toInt()
            outlinePaint.strokeWidth = 8f
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(22f, 16f), 0f)
            isEnabled = false
        }
    }
    val liveLine = remember {
        OsmPolyline(mapView).apply {
            outlinePaint.color = 0xFF2F7D63.toInt()
            outlinePaint.strokeWidth = 10f
            isEnabled = false
        }
    }

    remember(mapView) {
        mapView.overlays.add(routeLine)
        mapView.overlays.add(liveLine)
        mapView.overlays.add(startMarker)
        mapView.overlays.add(destMarker)
        mapView.overlays.add(pinMarker)
        mapView.overlays.add(meMarker)
        mapView.overlays.add(
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    val handler = tapHolder[0] ?: return false
                    handler(p.latitude, p.longitude)
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean {
                    val handler = tapHolder[0] ?: return false
                    handler(p.latitude, p.longitude)
                    return true
                }
            })
        )
        true
    }

    LaunchedEffect(scene) {
        val start = scene.start
        val destination = scene.destination
        val me = scene.me

        startMarker.isEnabled = start != null
        start?.let { startMarker.position = GeoPoint(it.latitude, it.longitude) }
        destMarker.isEnabled = destination != null
        destination?.let { destMarker.position = GeoPoint(it.latitude, it.longitude) }
        meMarker.isEnabled = me != null
        me?.let { meMarker.position = GeoPoint(it.latitude, it.longitude) }

        val pin = scene.pin
        pinMarker.isEnabled = pin != null
        pin?.let { pinMarker.position = GeoPoint(it.latitude, it.longitude) }

        routeLine.isEnabled = start != null && destination != null
        if (start != null && destination != null) {
            routeLine.setPoints(
                listOf(
                    GeoPoint(start.latitude, start.longitude),
                    GeoPoint(destination.latitude, destination.longitude)
                )
            )
        }
        liveLine.isEnabled = me != null && destination != null
        if (me != null && destination != null) {
            liveLine.setPoints(
                listOf(
                    GeoPoint(me.latitude, me.longitude),
                    GeoPoint(destination.latitude, destination.longitude)
                )
            )
        }
        mapView.invalidate()
    }

    LaunchedEffect(scene.stamp) {
        val bounds = scene.bounds
        when {
            bounds.size >= 2 -> try {
                mapView.zoomToBoundingBox(
                    org.osmdroid.util.BoundingBox.fromGeoPoints(
                        bounds.map { GeoPoint(it.latitude, it.longitude) }
                    ),
                    true,
                    140
                )
            } catch (e: Exception) {
                // not laid out yet
            }
            scene.center != null -> {
                mapView.controller.setZoom(scene.zoom.toDouble())
                mapView.controller.animateTo(
                    GeoPoint(scene.center.latitude, scene.center.longitude)
                )
            }
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier.fillMaxSize())
}
