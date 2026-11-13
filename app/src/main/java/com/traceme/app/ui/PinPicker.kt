package com.traceme.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.traceme.app.data.GeoSearch
import com.traceme.app.data.Waypoint
import kotlinx.coroutines.delay

/**
 * The fallback for "the address I want isn't in the search results": a real map
 * you can tap, so the destination is a precise point rather than a place name.
 */
@Composable
fun PinPicker(
    initial: Waypoint?,
    fallback: Waypoint?,
    onCancel: () -> Unit,
    onConfirm: (Waypoint) -> Unit
) {
    var pin by remember { mutableStateOf(initial ?: fallback) }
    var address by remember { mutableStateOf(initial?.label ?: "") }
    var resolving by remember { mutableStateOf(false) }
    var center by remember { mutableStateOf(initial ?: fallback) }
    var stamp by remember { mutableStateOf(0L) }

    BackHandler { onCancel() }

    // Turn the pin into a readable address, one lookup at a time.
    LaunchedEffect(pin) {
        val point = pin ?: return@LaunchedEffect
        resolving = true
        delay(350)
        val found = GeoSearch.reverse(point.latitude, point.longitude)
        if (pin == point) {
            address = found.ifBlank { point.label }
            resolving = false
        }
    }

    Box(Modifier.fillMaxSize().background(Cream)) {
        MapCanvas(
            scene = MapScene(
                me = fallback,
                pin = pin,
                center = center,
                zoom = 16.5f,
                stamp = stamp
            ),
            modifier = Modifier.fillMaxSize(),
            onTap = { latitude, longitude ->
                pin = Waypoint(latitude, longitude)
                address = ""
            }
        )

        /* ---------- top bar ---------- */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Glass)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Pin the exact spot",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Tap anywhere on the map to drop the pin",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
            }
            IconButton(onClick = {
                center = pin
                stamp++
            }) {
                Icon(Icons.Rounded.MyLocation, contentDescription = "Centre on pin", tint = Clay)
            }
        }

        /* ---------- bottom card ---------- */
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(SurfaceWarm)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).background(PeachSoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = Clay, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Your destination",
                    style = MaterialTheme.typography.labelSmall,
                    color = Clay
                )
            }

            if (pin == null) {
                Text(
                    "Waiting for the map. If it stays empty, your phone has no location yet — type the area in the search box instead.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
            } else {
                Text(
                    when {
                        resolving -> "Finding the address…"
                        address.isNotBlank() -> address
                        else -> "Custom point"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (resolving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = Clay
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        "%.5f, %.5f".format(pin!!.latitude, pin!!.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkFaint
                    )
                }
                ActionButton(
                    text = "Use this spot",
                    icon = Icons.Rounded.Check,
                    container = SafeGreen
                ) {
                    val point = pin ?: return@ActionButton
                    onConfirm(point.copy(label = address.ifBlank { point.label }))
                }
            }
        }
    }
}
