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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The part that actually raises the alarm: opens the SOS screen, texts every
 * emergency contact the location plus the nearest police/hospital numbers,
 * and (optionally) starts the call to the emergency number.
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
        val settings = repo.loadSettings()
        val contacts = repo.loadContacts()
        val accountName = repo.currentAccount()?.name ?: "Your contact"

        repo.log("sos", "SOS triggered · $reason")

        val mapLink = if (latitude != null && longitude != null) {
            "https://maps.google.com/?q=$latitude,$longitude"
        } else {
            null
        }

        val sosIntent = Intent(appContext, SosActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(SosActivity.EXTRA_REASON, reason)
            .putExtra(SosActivity.EXTRA_LAT, latitude ?: 0.0)
            .putExtra(SosActivity.EXTRA_LNG, longitude ?: 0.0)
            .putExtra(SosActivity.EXTRA_ACCURACY, accuracy)
            .putExtra(SosActivity.EXTRA_AUTO_CALL, settings.autoCall)

        val screenShown = try {
            appContext.startActivity(sosIntent)
            true
        } catch (e: Exception) {
            false
        }

        scope.launch {
            val nearby = if (latitude != null && longitude != null) {
                NearbyServices.find(latitude, longitude)
            } else {
                NearbyHelp()
            }
            val message = buildMessage(accountName, reason, mapLink, accuracy, nearby)
            lastMessage = message
            repo.saveSosPayload(message, nearby)

            val sent = sendToContacts(appContext, contacts, message)
            repo.log(
                "sos",
                if (sent > 0) "SOS message sent to $sent contact(s)" else "SOS message could not be sent"
            )
        }

        // If the SOS screen could not be shown, dial straight away so the alert is not lost.
        if (!screenShown && settings.autoCall) {
            dial(appContext, settings.emergencyNumber)
        }
    }

    fun buildMessage(
        name: String,
        reason: String,
        mapLink: String?,
        accuracy: Float,
        nearby: NearbyHelp
    ): String {
        val time = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date())
        val text = StringBuilder()
        text.append("TRACEME SOS ALERT\n")
        text.append("$name stopped moving and did not answer the safety check.\n")
        text.append("Reason: $reason\n")
        text.append("Time: $time\n")
        if (mapLink != null) {
            text.append("Last location: $mapLink")
            if (accuracy > 0f) text.append(" (±${accuracy.roundToInt()} m)")
            text.append("\n")
        } else {
            text.append("Last location: unavailable\n")
        }

        if (nearby.police.isNotEmpty()) {
            text.append("\nNearest police:\n")
            nearby.police.forEach { place -> text.append(placeLine(place)) }
        }
        if (nearby.hospitals.isNotEmpty()) {
            text.append("\nNearby hospitals:\n")
            nearby.hospitals.forEach { place -> text.append(placeLine(place)) }
        }
        text.append("\nPlease call and go check on them now.")
        return text.toString()
    }

    private fun placeLine(place: Place): String {
        val phone = if (place.phone.isNotBlank()) place.phone else "no number listed"
        return "• ${place.name} — $phone (${place.distanceMeters} m)\n"
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
