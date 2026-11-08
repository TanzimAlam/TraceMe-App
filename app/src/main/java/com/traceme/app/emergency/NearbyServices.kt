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
 * Finds the closest police stations and hospitals with phone numbers using
 * OpenStreetMap's Overpass API. No API key, no backend, no cost.
 */
object NearbyServices {

    suspend fun find(latitude: Double, longitude: Double): NearbyHelp = withContext(Dispatchers.IO) {
        val query = """
            [out:json][timeout:20];
            (
              node(around:4000,$latitude,$longitude)[amenity=police];
              way(around:4000,$latitude,$longitude)[amenity=police];
              node(around:5000,$latitude,$longitude)[amenity=hospital];
              way(around:5000,$latitude,$longitude)[amenity=hospital];
            );
            out center 40;
        """.trimIndent()

        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )

        for (endpoint in endpoints) {
            try {
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "TraceMe-Android/1.0 (night walk safety)")
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
                    .ifBlank { if (amenity == "police") "Police station" else "Hospital" }
                val phone = tags.optString("phone")
                    .ifBlank { tags.optString("contact:phone") }
                    .ifBlank { tags.optString("emergency:phone") }

                val place = Place(
                    name = name,
                    phone = phone,
                    distanceMeters = distanceMeters(latitude, longitude, placeLatitude, placeLongitude).toInt()
                )
                if (amenity == "police") police.add(place) else hospitals.add(place)
            }
        } catch (e: Exception) {
            return NearbyHelp()
        }
        return NearbyHelp(
            police = police.sortedBy { it.distanceMeters }.take(3),
            hospitals = hospitals.sortedBy { it.distanceMeters }.take(4)
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
