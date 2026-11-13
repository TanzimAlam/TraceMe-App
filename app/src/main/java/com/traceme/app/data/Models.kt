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
    /** Pause length in seconds, so it can be set to e.g. 20 min 30 s. */
    val quietSeconds: Int = 1800,
    val vibrate: Boolean = true,
    val autoCall: Boolean = true
) {
    val quietMinutes: Int get() = (quietSeconds / 60).coerceAtLeast(1)
    val stuckLabel: String get() = shortDuration(stuckSeconds)
    val responseLabel: String get() = shortDuration(responseSeconds)
    val quietLabel: String get() = shortDuration(quietSeconds)
}

/** "45 s", "2 min", "20 min 30 s" — never "0 min 45 s". */
fun shortDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val rest = safe % 60
    return when {
        minutes == 0 -> "$rest s"
        rest == 0 -> "$minutes min"
        else -> "$minutes min $rest s"
    }
}

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
    val distanceMeters: Int,
    /** Operator, officer in charge or any other human contact listed on the map. */
    val detail: String = "",
    /** Street / area, when the map has it. */
    val area: String = "",
    /** Extra number tagged on the same place (control room, emergency desk). */
    val altPhone: String = "",
    /** A number that works everywhere in the country, so it is pinned on top. */
    val national: Boolean = false
)

data class NearbyHelp(
    val police: List<Place> = emptyList(),
    val hospitals: List<Place> = emptyList()
) {
    val isEmpty: Boolean get() = police.isEmpty() && hospitals.isEmpty()
}

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

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    return earthRadius * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}
