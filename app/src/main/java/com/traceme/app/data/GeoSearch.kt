package com.traceme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Turns what the user types into map points, using OpenStreetMap's Nominatim
 * search — the same free, keyless service family as the Nearby Help lookup.
 */
object GeoSearch {

    suspend fun suggest(query: String, near: Waypoint? = null): List<Waypoint> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 3) return@withContext emptyList()

            val builder = StringBuilder("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6")
            builder.append("&q=").append(URLEncoder.encode(trimmed, "UTF-8"))
            if (near != null) {
                // Bias (not restrict) results towards the user's surroundings.
                builder.append("&viewbox=")
                    .append(near.longitude - 0.6).append(',')
                    .append(near.latitude + 0.6).append(',')
                    .append(near.longitude + 0.6).append(',')
                    .append(near.latitude - 0.6)
            }

            try {
                val connection = (URL(builder.toString()).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 9000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty(
                        "User-Agent",
                        "Overwatch-Android/1.0 (night walk safety; contact: oayankh@gmail.com)"
                    )
                }
                if (connection.responseCode !in 200..299) {
                    connection.disconnect()
                    return@withContext emptyList()
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                parse(body)
            } catch (e: Exception) {
                emptyList()
            }
        }

    private fun parse(body: String): List<Waypoint> {
        return try {
            val array = JSONArray(body)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val latitude = item.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                val longitude = item.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                val name = item.optString("name").ifBlank {
                    item.optString("display_name").substringBefore(',')
                }
                val place = item.optString("display_name")
                val short = place.split(',').drop(1).take(2)
                    .joinToString(", ") { it.trim() }
                val label = if (short.isBlank()) name else "$name · $short"
                Waypoint(latitude, longitude, label)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
