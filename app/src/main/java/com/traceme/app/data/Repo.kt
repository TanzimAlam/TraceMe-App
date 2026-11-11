package com.traceme.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Tiny persistence layer on top of SharedPreferences. Everything the app needs
 * survives restarts: accounts, session, emergency contacts, timings and the log.
 */
class Repo private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------- session ----------

    var sessionEmail: String?
        get() = prefs.getString(KEY_SESSION, null)
        set(value) {
            prefs.edit().putString(KEY_SESSION, value).apply()
        }

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()
        }

    fun currentAccount(): Account? {
        val email = sessionEmail ?: return null
        return loadAccounts().firstOrNull { it.email.equals(email, ignoreCase = true) }
    }

    // ---------- accounts ----------

    fun loadAccounts(): List<Account> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                Account(
                    name = item.optString("name"),
                    email = item.optString("email"),
                    phone = item.optString("phone"),
                    provider = item.optString("provider", "local"),
                    salt = item.optString("salt"),
                    passwordHash = item.optString("passwordHash")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAccounts(accounts: List<Account>) {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject().apply {
                    put("name", account.name)
                    put("email", account.email)
                    put("phone", account.phone)
                    put("provider", account.provider)
                    put("salt", account.salt)
                    put("passwordHash", account.passwordHash)
                }
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    // ---------- emergency contacts ----------

    fun loadContacts(): List<EmergencyContact> {
        val raw = prefs.getString(KEY_CONTACTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                EmergencyContact(
                    id = item.optString("id", UUID.randomUUID().toString()),
                    name = item.optString("name"),
                    phone = item.optString("phone"),
                    email = item.optString("email"),
                    relation = item.optString("relation")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveContacts(contacts: List<EmergencyContact>) {
        val array = JSONArray()
        contacts.forEach { contact ->
            array.put(
                JSONObject().apply {
                    put("id", contact.id)
                    put("name", contact.name)
                    put("phone", contact.phone)
                    put("email", contact.email)
                    put("relation", contact.relation)
                }
            )
        }
        prefs.edit().putString(KEY_CONTACTS, array.toString()).apply()
    }

    fun addContact(contact: EmergencyContact): List<EmergencyContact> {
        val updated = loadContacts().toMutableList().apply { add(contact) }
        saveContacts(updated)
        return updated
    }

    fun removeContact(id: String): List<EmergencyContact> {
        val updated = loadContacts().filterNot { it.id == id }
        saveContacts(updated)
        return updated
    }

    // ---------- settings ----------

    fun loadSettings(): UserSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return UserSettings()
        return try {
            val item = JSONObject(raw)
            UserSettings(
                emergencyNumber = item.optString("emergencyNumber", "999"),
                countryLabel = item.optString("countryLabel", "Bangladesh"),
                stuckSeconds = item.optInt("stuckSeconds", 120),
                responseSeconds = item.optInt("responseSeconds", 60),
                quietSeconds = if (item.has("quietSeconds")) {
                    item.optInt("quietSeconds", 1800)
                } else {
                    // Older installs stored whole minutes only.
                    item.optInt("quietMinutes", 30) * 60
                },
                vibrate = item.optBoolean("vibrate", true),
                autoCall = item.optBoolean("autoCall", true)
            )
        } catch (e: Exception) {
            UserSettings()
        }
    }

    fun saveSettings(settings: UserSettings) {
        val json = JSONObject().apply {
            put("emergencyNumber", settings.emergencyNumber)
            put("countryLabel", settings.countryLabel)
            put("stuckSeconds", settings.stuckSeconds)
            put("responseSeconds", settings.responseSeconds)
            put("quietSeconds", settings.quietSeconds)
            put("vibrate", settings.vibrate)
            put("autoCall", settings.autoCall)
        }
        prefs.edit().putString(KEY_SETTINGS, json.toString()).apply()
    }

    // ---------- activity log ----------

    fun loadLog(): List<LogEntry> {
        val raw = prefs.getString(KEY_LOG, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                LogEntry(
                    timestamp = item.optLong("timestamp"),
                    kind = item.optString("kind"),
                    message = item.optString("message")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun log(kind: String, message: String) {
        val entries = loadLog().toMutableList()
        entries.add(0, LogEntry(System.currentTimeMillis(), kind, message))
        val array = JSONArray()
        entries.take(MAX_LOG).forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("timestamp", entry.timestamp)
                    put("kind", entry.kind)
                    put("message", entry.message)
                }
            )
        }
        prefs.edit().putString(KEY_LOG, array.toString()).apply()
    }

    fun clearLog() {
        prefs.edit().remove(KEY_LOG).apply()
    }

    // ---------- last SOS payload ----------

    fun saveSosPayload(message: String, help: NearbyHelp) {
        val json = JSONObject().apply {
            put("message", message)
            put("police", placeArray(help.police))
            put("hospitals", placeArray(help.hospitals))
        }
        prefs.edit().putString(KEY_SOS, json.toString()).apply()
    }

    fun loadSosPayload(): Pair<String, NearbyHelp> {
        val raw = prefs.getString(KEY_SOS, null) ?: return "" to NearbyHelp()
        return try {
            val item = JSONObject(raw)
            val help = NearbyHelp(
                police = readPlaces(item.optJSONArray("police")),
                hospitals = readPlaces(item.optJSONArray("hospitals"))
            )
            item.optString("message") to help
        } catch (e: Exception) {
            "" to NearbyHelp()
        }
    }

    private fun placeArray(places: List<Place>): JSONArray {
        val array = JSONArray()
        places.forEach { place ->
            array.put(
                JSONObject().apply {
                    put("name", place.name)
                    put("phone", place.phone)
                    put("distanceMeters", place.distanceMeters)
                    put("detail", place.detail)
                    put("area", place.area)
                    put("altPhone", place.altPhone)
                }
            )
        }
        return array
    }

    private fun readPlaces(array: JSONArray?): List<Place> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Place(
                name = item.optString("name"),
                phone = item.optString("phone"),
                distanceMeters = item.optInt("distanceMeters"),
                detail = item.optString("detail"),
                area = item.optString("area"),
                altPhone = item.optString("altPhone")
            )
        }
    }

    // ---------- planned trip ----------

    fun loadTrip(): Trip {
        val raw = prefs.getString(KEY_TRIP, null) ?: return Trip()
        return try {
            val item = JSONObject(raw)
            Trip(
                start = readWaypoint(item.optJSONObject("start")),
                destination = readWaypoint(item.optJSONObject("destination"))
            )
        } catch (e: Exception) {
            Trip()
        }
    }

    fun saveTrip(trip: Trip) {
        val json = JSONObject()
        trip.start?.let { json.put("start", waypointJson(it)) }
        trip.destination?.let { json.put("destination", waypointJson(it)) }
        prefs.edit().putString(KEY_TRIP, json.toString()).apply()
    }

    private fun waypointJson(point: Waypoint): JSONObject = JSONObject().apply {
        put("latitude", point.latitude)
        put("longitude", point.longitude)
        put("label", point.label)
    }

    private fun readWaypoint(item: JSONObject?): Waypoint? {
        if (item == null) return null
        val lat = item.optDouble("latitude", Double.NaN)
        val lon = item.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        return Waypoint(lat, lon, item.optString("label"))
    }

    fun resetEverything() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE = "traceme"
        private const val KEY_SESSION = "sessionEmail"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_CONTACTS = "contacts"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_LOG = "log"
        private const val KEY_SOS = "lastSos"
        private const val KEY_TRIP = "trip"
        private const val MAX_LOG = 150

        @Volatile
        private var instance: Repo? = null

        fun get(context: Context): Repo {
            return instance ?: synchronized(this) {
                instance ?: Repo(context).also { instance = it }
            }
        }
    }
}
