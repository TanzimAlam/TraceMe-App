package com.traceme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Turns what the user types into map points using OpenStreetMap's Nominatim —
 * the same free, keyless service family as the Nearby Help lookup.
 *
 * This searches *addresses*, not just place names: house numbers, roads and
 * neighbourhoods all come back, and [reverse] turns a dropped pin back into a
 * readable address.
 */
object GeoSearch {

    private const val USER_AGENT =
        "Overwatch-Android/1.1 (night walk safety; contact: oayankh@gmail.com)"

    suspend fun suggest(query: String, near: Waypoint? = null): List<Waypoint> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()

            val builder = StringBuilder("https://nominatim.openstreetmap.org/search")
            builder.append("?format=jsonv2&limit=8&addressdetails=1&accept-language=en")
            builder.append("&q=").append(URLEncoder.encode(trimmed, "UTF-8"))
            if (near != null) {
                // Bias (not restrict) results towards the user's surroundings.
                builder.append("&viewbox=")
                    .append(near.longitude - 0.5).append(',')
                    .append(near.latitude + 0.5).append(',')
                    .append(near.longitude + 0.5).append(',')
                    .append(near.latitude - 0.5)
            }

            val body = get(builder.toString()) ?: return@withContext emptyList()
            parse(body)
        }

    /** Turns a pin on the map into "12/A Road 5, Dhanmondi, Dhaka". */
    suspend fun reverse(latitude: Double, longitude: Double): String =
        withContext(Dispatchers.IO) {
            val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18" +
                "&addressdetails=1&accept-language=en" +
                "&lat=$latitude&lon=$longitude"
            val body = get(url) ?: return@withContext ""
            try {
                val item = JSONObject(body)
                val address = item.optJSONObject("address")
                val parts = mutableListOf<String>()
                address?.let {
                    listOf(
                        "house_number", "road", "path", "neighbourhood", "suburb",
                        "quarter", "city_district", "city", "town", "village", "state_district"
                    ).forEach { key ->
                        val value = it.optString(key)
                        if (value.isNotBlank()) parts.add(value)
                    }
                }
                if (parts.isEmpty()) {
                    item.optString("display_name").split(',').take(3)
                        .map { it.trim() }
                        .forEach { if (it.isNotBlank()) parts.add(it) }
                }
                parts.distinct().take(4).joinToString(", ")
            } catch (e: Exception) {
                ""
            }
        }

    private fun get(url: String): String? {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 9000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                connection.disconnect()
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            body
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(body: String): List<Waypoint> {
        return try {
            val array = JSONArray(body)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val latitude = item.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                val longitude = item.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                val display = item.optString("display_name")
                val name = item.optString("name").ifBlank { display.substringBefore(',') }

                // Keep the house number / road in the label so two "Gulshan" hits
                // are tellable apart at a glance.
                val address = item.optJSONObject("address")
                val street = address?.let {
                    listOf("house_number", "road").map { key -> it.optString(key) }
                        .filter { part -> part.isNotBlank() }
                        .joinToString(" ")
                }.orEmpty()
                val locality = address?.let {
                    listOf("suburb", "neighbourhood", "city_district", "city", "town")
                        .map { key -> it.optString(key) }
                        .firstOrNull { part -> part.isNotBlank() }
                }.orEmpty()

                val tail = listOf(street, locality)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                val label = when {
                    tail.isBlank() -> "$name · ${display.split(',').drop(1).take(2).joinToString(",") { it.trim() }}"
                    name.equals(street, ignoreCase = true) -> "$name · $locality"
                    else -> "$name · $tail"
                }
                Waypoint(latitude, longitude, label.trim().trimStart('·').trim())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
