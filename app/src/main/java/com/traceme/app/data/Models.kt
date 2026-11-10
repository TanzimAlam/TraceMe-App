package com.traceme.app.data

data class EmergencyContact(
    val id: String,
    val name: String,
    val phone: String,
    val email: String = "",
    val relation: String = ""
)

data class UserSettings(
    val emergencyNumber: String = "999",
    val countryLabel: String = "Bangladesh",
    val stuckSeconds: Int = 120,
    val responseSeconds: Int = 60,
    val quietMinutes: Int = 30,
    val vibrate: Boolean = true,
    val autoCall: Boolean = true
)

data class Account(
    val name: String,
    val email: String,
    val phone: String = "",
    val provider: String = "local",
    val salt: String = "",
    val passwordHash: String = ""
)

/** Where the watch currently is. */
enum class WatchState { IDLE, WATCHING, PROMPTING, QUIET, SOS }

data class LogEntry(
    val timestamp: Long,
    val kind: String,
    val message: String
)

data class Place(
    val name: String,
    val phone: String,
    val distanceMeters: Int
)

data class NearbyHelp(
    val police: List<Place> = emptyList(),
    val hospitals: List<Place> = emptyList()
)

/** A point on the map: the walk's start or where the user is headed. */
data class Waypoint(
    val latitude: Double,
    val longitude: Double,
    val label: String = ""
) {
    fun coords(): String = "%.5f, %.5f".format(latitude, longitude)
    fun pretty(): String = label.ifBlank { coords() }
}

/** The planned route for tonight's walk. */
data class Trip(
    val start: Waypoint? = null,
    val destination: Waypoint? = null
)
