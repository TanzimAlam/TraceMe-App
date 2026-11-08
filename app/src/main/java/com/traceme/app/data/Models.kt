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
