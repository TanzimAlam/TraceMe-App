package com.traceme.app.emergency

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.traceme.app.SosActivity
import com.traceme.app.data.EmergencyContact
import com.traceme.app.data.NearbyHelp
import com.traceme.app.data.Place
import com.traceme.app.data.Repo
import com.traceme.app.data.shortDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The part that actually raises the alarm: texts every emergency contact the
 * live location, the nearest police stations and the hospital/ambulance
 * numbers, then opens the SOS screen.
 *
 * It never dials by itself. The call to 999 is always a deliberate tap, so a
 * pocket press cannot start a call the user did not want.
 */
object EmergencyDispatcher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var lastMessage: String = ""

    fun dispatch(
        context: Context,
        latitude: Double?,
        longitude: Double?,
        accuracy: Float,
        reason: String
    ) {
        val appContext = context.applicationContext
        val repo = Repo.get(appContext)
        val contacts = repo.loadContacts()
        val accountName = repo.currentAccount()?.name ?: "Your contact"

        repo.log("sos", "SOS triggered · $reason")

        val mapLink = if (latitude != null && longitude != null) {
            "https://maps.google.com/?q=$latitude,$longitude"
        } else {
            null
        }

        // The bundled directory answers instantly, so the text goes out now
        // instead of waiting for a map server.
        val nearby = if (latitude != null && longitude != null) {
            NearbyServices.local(latitude, longitude)
        } else {
            NearbyHelp()
        }
        val message = buildMessage(accountName, reason, mapLink, accuracy, nearby)
        lastMessage = message
        repo.saveSosPayload(message, nearby)

        val sosIntent = Intent(appContext, SosActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(SosActivity.EXTRA_REASON, reason)
            .putExtra(SosActivity.EXTRA_LAT, latitude ?: 0.0)
            .putExtra(SosActivity.EXTRA_LNG, longitude ?: 0.0)
            .putExtra(SosActivity.EXTRA_ACCURACY, accuracy)
            .putExtra(SosActivity.EXTRA_AUTO_CALL, false)

        try {
            appContext.startActivity(sosIntent)
        } catch (e: Exception) {
            // Background activity starts can be blocked; the SMS still goes.
        }

        val sent = sendToContacts(appContext, contacts, message)
        repo.log(
            "sos",
            if (sent > 0) "SOS message sent to $sent contact(s)" else "SOS message could not be sent (SMS permission or number missing)"
        )
    }

    /**
     * Someone is interfering with the phone while a watch is running: data or
     * Wi-Fi killed, or a power-off. Each contact gets the message written with
     * *their* relationship to the owner.
     */
    fun guardianAlert(
        context: Context,
        trigger: String,
        latitude: Double?,
        longitude: Double?
    ) {
        val appContext = context.applicationContext
        val repo = Repo.get(appContext)
        val contacts = repo.loadContacts().filter { it.phone.isNotBlank() }
        if (contacts.isEmpty()) {
            repo.log("guard", "Guard alert had nobody to tell")
            return
        }
        val owner = repo.currentAccount()?.name?.substringBefore(' ') ?: "your contact"
        val mapLink = if (latitude != null && longitude != null) {
            "https://maps.google.com/?q=$latitude,$longitude"
        } else {
            null
        }
        val time = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date())

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            repo.log("guard", "Guard alert could not be sent: no SMS permission")
            return
        }

        var sent = 0
        contacts.forEach { contact ->
            val relation = contact.relation.ifBlank { "friend" }.lowercase(Locale.getDefault())
            val text = buildString {
                append("OVERWATCH GUARD ALERT\n")
                append("I think someone is trying to take your $relation's phone.\n")
                append("$trigger while $owner's night walk was running.\n")
                append("Time: $time\n")
                if (mapLink != null) {
                    append("$owner was last at: $mapLink\n")
                } else {
                    append("$owner's last location: unavailable\n")
                }
                append("Try calling $owner right now. If the phone is off, call 999 and report it.\n")
            }
            if (sendSms(appContext, contact.phone, text)) sent++
        }
        repo.log("guard", "Guard alert sent to $sent contact(s) · $trigger")
    }

    fun buildMessage(
        name: String,
        reason: String,
        mapLink: String?,
        accuracy: Float,
        nearby: NearbyHelp,
        keepQuietSeconds: Int = 0
    ): String {
        val time = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date())
        val text = StringBuilder()
        text.append("OVERWATCH SOS ALERT\n")
        text.append("$name may be in trouble.\n")
        text.append("Reason: $reason\n")
        text.append("Time: $time\n")
        if (keepQuietSeconds > 0) {
            text.append("The phone was silenced by the app for ${shortDuration(keepQuietSeconds)}.\n")
        }
        if (mapLink != null) {
            text.append("Live/last location: $mapLink")
            if (accuracy > 0f) text.append(" (±${accuracy.roundToInt()} m)")
            text.append("\n")
        } else {
            text.append("Location: unavailable — the phone had no GPS fix.\n")
        }

        if (nearby.police.isNotEmpty()) {
            text.append("\nPolice numbers near them:\n")
            nearby.police.forEach { text.append(placeLine(it)) }
        }
        if (nearby.hospitals.isNotEmpty()) {
            text.append("\nHospitals & ambulance near them:\n")
            nearby.hospitals.forEach { text.append(placeLine(it)) }
        }
        text.append("\nPlease call $name, then call 999 if they don't answer.")
        return text.toString()
    }

    private fun placeLine(place: Place): String {
        val number = when {
            place.phone.isNotBlank() && place.altPhone.isNotBlank() -> "${place.phone} / ${place.altPhone}"
            place.phone.isNotBlank() -> place.phone
            else -> "no number listed"
        }
        val where = if (place.national) "nationwide" else "${place.distanceMeters} m"
        return "• ${place.name} — $number ($where)\n"
    }

    fun sendToContacts(context: Context, contacts: List<EmergencyContact>, message: String): Int {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return 0
        }
        var sent = 0
        contacts.forEach { contact ->
            if (contact.phone.isBlank()) return@forEach
            if (sendSms(context, contact.phone, message)) sent++
        }
        return sent
    }

    @Suppress("DEPRECATION")
    private fun sendSms(context: Context, phone: String, message: String): Boolean {
        return try {
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            } ?: return false
            val parts = manager.divideMessage(message)
            manager.sendMultipartTextMessage(phone, null, parts, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Opens the dialer (or places the call when CALL_PHONE is granted). */
    fun dial(context: Context, number: String) {
        if (number.isBlank()) return
        val call = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (call) Intent.ACTION_CALL else Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:" + Uri.encode(number))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // No dialer on the device.
        }
    }

    fun messageIntent(contact: EmergencyContact, message: String, subject: String): Intent? {
        val phone = contact.phone
        val email = contact.email
        return when {
            email.isNotBlank() -> Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:" + Uri.encode(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            phone.isNotBlank() -> Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:" + Uri.encode(phone))
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            else -> null
        }
    }
}
