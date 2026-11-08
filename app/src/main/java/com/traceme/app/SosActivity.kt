package com.traceme.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Sms
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.traceme.app.data.EmergencyContact
import com.traceme.app.data.NearbyHelp
import com.traceme.app.data.Place
import com.traceme.app.data.Repo
import com.traceme.app.emergency.EmergencyDispatcher
import com.traceme.app.location.TrackingService
import com.traceme.app.ui.ActionButton
import com.traceme.app.ui.Cream
import com.traceme.app.ui.Danger
import com.traceme.app.ui.Ink
import com.traceme.app.ui.InkSoft
import com.traceme.app.ui.Pink
import com.traceme.app.ui.QuietButton
import com.traceme.app.ui.SafeGreen
import com.traceme.app.ui.SectionLabel
import com.traceme.app.ui.SoftCard
import com.traceme.app.ui.TraceMeTheme
import kotlinx.coroutines.delay

/**
 * The emergency screen: calls the hotline, texts every contact and shows the
 * nearest police and hospital numbers so the caller can arrange help.
 */
class SosActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val autoCall = intent.getBooleanExtra(EXTRA_AUTO_CALL, true)
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Safety check missed"

        setContent {
            TraceMeTheme {
                val context = LocalContext.current
                val repo = remember { Repo.get(context) }
                val settings = remember { repo.loadSettings() }
                var contacts by remember { mutableStateOf(repo.loadContacts()) }
                var message by remember { mutableStateOf(repo.loadSosPayload().first) }
                var help by remember { mutableStateOf(repo.loadSosPayload().second) }
                var countdown by remember { mutableStateOf(if (autoCall) 8 else 0) }
                var dialed by remember { mutableStateOf(false) }

                // The SMS text and nearby services are fetched in the background.
                LaunchedEffect(Unit) {
                    var tries = 0
                    while (tries < 20) {
                        val payload = repo.loadSosPayload()
                        if (payload.first.isNotBlank()) {
                            message = payload.first
                            help = payload.second
                            break
                        }
                        delay(1000)
                        tries++
                    }
                    contacts = repo.loadContacts()
                }

                LaunchedEffect(autoCall) {
                    if (!autoCall) return@LaunchedEffect
                    while (countdown > 0) {
                        delay(1000)
                        countdown--
                    }
                    if (!dialed) {
                        dialed = true
                        EmergencyDispatcher.dial(context, settings.emergencyNumber)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Cream)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "SOS",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Danger
                    )
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft,
                        textAlign = TextAlign.Center
                    )

                    SoftCard(container = Color(0xFFFBE3E1)) {
                        Text(
                            "Calling ${settings.emergencyNumber}" +
                                if (countdown > 0) " in $countdown s" else " now",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                        Text(
                            "Your contacts receive a text with your last location, the nearest police station and the nearest hospitals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft
                        )
                        ActionButton(
                            text = "Call ${settings.emergencyNumber} now",
                            icon = Icons.Rounded.Call,
                            container = Danger
                        ) {
                            dialed = true
                            EmergencyDispatcher.dial(context, settings.emergencyNumber)
                        }
                    }

                    SoftCard {
                        SectionLabel("Alert your contacts")
                        Text(
                            "${contacts.size} contact(s) get the message below. SMS is sent automatically when a check-in is missed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft
                        )
                        ActionButton(
                            text = "Text all contacts again",
                            icon = Icons.Rounded.Sms,
                            container = com.traceme.app.ui.Berry
                        ) {
                            val sent = EmergencyDispatcher.sendToContacts(context, contacts, message)
                            Toast.makeText(
                                context,
                                if (sent > 0) "Message sent to $sent contact(s)" else "SMS permission or number missing",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        contacts.forEach { contact: EmergencyContact ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(contact.name, style = MaterialTheme.typography.bodyMedium, color = Ink)
                                    Text(
                                        contact.phone.ifBlank { contact.email },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkSoft
                                    )
                                }
                                TextButton(onClick = {
                                    EmergencyDispatcher.messageIntent(contact, message, "TraceMe SOS")
                                        ?.let { intent -> context.startActivity(intent) }
                                }) { Text("Send", color = com.traceme.app.ui.Berry) }
                                TextButton(onClick = {
                                    EmergencyDispatcher.dial(context, contact.phone)
                                }) { Text("Call", color = SafeGreen) }
                            }
                        }
                    }

                    SoftCard {
                        SectionLabel("Nearest police & hospitals")
                        if (help.police.isEmpty() && help.hospitals.isEmpty()) {
                            Text(
                                "Looking up nearby stations… if nothing appears, tell the operator your location.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSoft
                            )
                        }
                        help.police.forEach { place: Place -> SosPlaceRow(place, "Police") { EmergencyDispatcher.dial(context, place.phone) } }
                        help.hospitals.forEach { place: Place -> SosPlaceRow(place, "Hospital") { EmergencyDispatcher.dial(context, place.phone) } }
                    }

                    if (message.isNotBlank()) {
                        SoftCard(container = Color(0xFFFFF7EC)) {
                            SectionLabel("Message being sent")
                            Text(message, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                        }
                    }

                    QuietButton("I'm safe — stop the alert", Icons.Rounded.CheckCircle) {
                        TrackingService.confirmSafe(context)
                        finish()
                    }

                    TextButton(onClick = { finish() }) {
                        Text("Close (alerts already sent)", color = InkSoft)
                    }
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
    }

    companion object {
        const val EXTRA_REASON = "reason"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_AUTO_CALL = "autoCall"
    }
}

@Composable
private fun SosPlaceRow(place: Place, kind: String, onCall: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.bodyMedium, color = Ink)
            Text(
                "$kind · ${place.distanceMeters} m · ${place.phone.ifBlank { "no number listed" }}",
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft
            )
        }
        if (place.phone.isNotBlank()) {
            TextButton(onClick = onCall) { Text("Call", color = SafeGreen) }
        }
    }
}
