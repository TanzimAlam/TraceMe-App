package com.traceme.app.emergency

import com.traceme.app.data.NearbyHelp
import com.traceme.app.data.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Finds the closest police stations (thana) and hospitals around the user,
 * together with any phone numbers OpenStreetMap has for them.
 *
 * Uses the Overpass API: no key, no billing, no backend.
 */
object NearbyServices {

    private const val POLICE_RADIUS = 6_000
    private const val HOSPITAL_RADIUS = 8_000
    private const val POLICE_LIMIT = 6
    private const val HOSPITAL_LIMIT = 5

    suspend fun find(latitude: Double, longitude: Double): NearbyHelp = withContext(Dispatchers.IO) {
        val query = """
            [out:json][timeout:25];
            (
              node(around:$POLICE_RADIUS,$latitude,$longitude)[amenity=police];
              way(around:$POLICE_RADIUS,$latitude,$longitude)[amenity=police];
              relation(around:$POLICE_RADIUS,$latitude,$longitude)[amenity=police];
              node(around:$HOSPITAL_RADIUS,$latitude,$longitude)[amenity=hospital];
              way(around:$HOSPITAL_RADIUS,$latitude,$longitude)[amenity=hospital];
              relation(around:$HOSPITAL_RADIUS,$latitude,$longitude)[amenity=hospital];
            );
            out center 120;
        """.trimIndent()

        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
        )

        for (endpoint in endpoints) {
            try {
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 9000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty(
                        "User-Agent",
                        "Overwatch-Android/1.0 (night walk safety; contact: oayankh@gmail.com)"
                    )
                }
                connection.outputStream.use { stream ->
                    stream.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray())
                }
                if (connection.responseCode in 200..299) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    return@withContext parse(body, latitude, longitude)
                }
                connection.disconnect()
            } catch (e: Exception) {
                // try the next mirror
            }
        }
        NearbyHelp()
    }

    private fun parse(body: String, latitude: Double, longitude: Double): NearbyHelp {
        val police = mutableListOf<Place>()
        val hospitals = mutableListOf<Place>()
        try {
            val elements = JSONObject(body).optJSONArray("elements") ?: return NearbyHelp()
            for (index in 0 until elements.length()) {
                val element = elements.getJSONObject(index)
                val tags = element.optJSONObject("tags") ?: continue
                val amenity = tags.optString("amenity")
                if (amenity != "police" && amenity != "hospital") continue

                val placeLatitude: Double
                val placeLongitude: Double
                if (element.has("lat") && element.has("lon")) {
                    placeLatitude = element.optDouble("lat")
                    placeLongitude = element.optDouble("lon")
                } else {
                    val center = element.optJSONObject("center") ?: continue
                    placeLatitude = center.optDouble("lat")
                    placeLongitude = center.optDouble("lon")
                }
                if (placeLatitude.isNaN() || placeLongitude.isNaN()) continue

                val name = tags.optString("name")
                    .ifBlank { tags.optString("name:en") }
                    .ifBlank { tags.optString("official_name") }
                    .ifBlank { if (amenity == "police") "Police station" else "Hospital" }

                // Everything that might be a human being's phone number.
                val numbers = listOf(
                    tags.optString("phone"),
                    tags.optString("contact:phone"),
                    tags.optString("contact:mobile"),
                    tags.optString("mobile"),
                    tags.optString("emergency:phone")
                ).map { it.trim() }
                    .filter { it.isNotBlank() }
                    .flatMap { it.split(';', ',') }
                    .map { it.trim() }
                    .distinct()

                val detail = listOf(
                    tags.optString("operator"),
                    tags.optString("contact:name"),
                    tags.optString("official_name").takeIf { it != name } ?: "",
                    tags.optString("description")
                ).firstOrNull { it.isNotBlank() } ?: ""

                val area = listOf(
                    tags.optString("addr:street"),
                    tags.optString("addr:suburb"),
                    tags.optString("addr:city")
                ).filter { it.isNotBlank() }.joinToString(", ")

                val place = Place(
                    name = name,
                    phone = numbers.firstOrNull() ?: "",
                    distanceMeters = distanceMeters(latitude, longitude, placeLatitude, placeLongitude).toInt(),
                    detail = detail,
                    area = area,
                    altPhone = numbers.drop(1).firstOrNull() ?: ""
                )
                if (amenity == "police") police.add(place) else hospitals.add(place)
            }
        } catch (e: Exception) {
            return NearbyHelp()
        }

        fun ranked(list: List<Place>, limit: Int): List<Place> =
            list.sortedBy { it.distanceMeters }
                .distinctBy { "${it.name.lowercase()}@${it.distanceMeters / 250}" }
                .take(limit)

        return NearbyHelp(
            police = ranked(police, POLICE_LIMIT),
            hospitals = ranked(hospitals, HOSPITAL_LIMIT)
        )
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
