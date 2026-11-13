package com.traceme.app.emergency

import com.traceme.app.data.NearbyHelp
import com.traceme.app.data.Place
import com.traceme.app.data.distanceMeters
import kotlin.math.roundToInt

enum class ServiceKind(val label: String) {
    POLICE("Police"),
    HOSPITAL("Hospital"),
    AMBULANCE("Ambulance")
}

/**
 * One number that a person can actually dial, with the place it belongs to.
 * `latitude`/`longitude` are only needed for the nearby sort; numbers that work
 * everywhere are marked [nationwide] and always sit at the top of their list.
 */
data class DirectoryEntry(
    val name: String,
    val kind: ServiceKind,
    val phone: String,
    val altPhone: String = "",
    val area: String = "",
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val nationwide: Boolean = false
)

/**
 * The numbers that ship inside the APK, so the help list appears instantly with
 * no network at all.
 *
 * Coverage is deliberately honest: the nationwide hotlines and the published
 * control-room numbers are complete, while individual thana landlines are only
 * included where the number is published on a government source. The live
 * OpenStreetMap lookup tops the list up with the exact station and hospital
 * around the user, including whatever local number the map has.
 *
 * Sources: 999.gov.bd, dmp.gov.bd / dmpnews.org hot-number table, police.gov.bd,
 * DGHS Shastho Batayon (16263), Fire Service & Civil Defence (199/102).
 */
object EmergencyDirectory {

    private const val POLICE_LIMIT = 8
    private const val HOSPITAL_LIMIT = 6

    val entries: List<DirectoryEntry> = listOf(
        /* ---------------- police: nationwide ---------------- */
        DirectoryEntry(
            name = "National Emergency Service",
            kind = ServiceKind.POLICE,
            phone = "999",
            area = "All Bangladesh",
            note = "Police, fire and ambulance in one call · free · 24/7",
            latitude = 23.8103,
            longitude = 90.4125,
            nationwide = true
        ),
        DirectoryEntry(
            name = "DMP Hot Number",
            kind = ServiceKind.POLICE,
            phone = "100",
            altPhone = "9619999",
            area = "Dhaka Metropolitan Police",
            note = "Round-the-clock DMP information and dispatch desk",
            latitude = 23.7256,
            longitude = 90.4106
        ),
        DirectoryEntry(
            name = "DMP Control Room",
            kind = ServiceKind.POLICE,
            phone = "9575500",
            altPhone = "01817602050",
            area = "Minto Road, Dhaka",
            note = "Metropolitan control room — ask for the thana covering your street",
            latitude = 23.7256,
            longitude = 90.4106
        ),
        DirectoryEntry(
            name = "DMP Traffic Control Room",
            kind = ServiceKind.POLICE,
            phone = "9575501",
            altPhone = "01711000990",
            area = "Dhaka",
            note = "Traffic crashes and blocked roads",
            latitude = 23.7256,
            longitude = 90.4106
        ),
        DirectoryEntry(
            name = "DMP Crime Command & Control",
            kind = ServiceKind.POLICE,
            phone = "9551188",
            altPhone = "01713398311",
            area = "Dhaka",
            note = "Crime in progress inside the city",
            latitude = 23.7256,
            longitude = 90.4106
        ),
        DirectoryEntry(
            name = "Police Headquarters Control Room",
            kind = ServiceKind.POLICE,
            phone = "029555555",
            area = "Phoenix Road, Dhaka",
            note = "National police headquarters",
            latitude = 23.7335,
            longitude = 90.4026
        ),
        DirectoryEntry(
            name = "Highway Police",
            kind = ServiceKind.POLICE,
            phone = "029354321",
            area = "All highways",
            note = "Anything that happens on a highway or expressway",
            latitude = 23.8103,
            longitude = 90.4125
        ),
        DirectoryEntry(
            name = "Tourist Police",
            kind = ServiceKind.POLICE,
            phone = "01320000888",
            area = "All Bangladesh",
            note = "Tourist police hotline",
            latitude = 23.8103,
            longitude = 90.4125
        ),
        DirectoryEntry(
            name = "RAB Headquarters",
            kind = ServiceKind.POLICE,
            phone = "01777772000",
            altPhone = "029661105",
            area = "Kurmitola, Dhaka",
            note = "Rapid Action Battalion control room",
            latitude = 23.8195,
            longitude = 90.4037
        ),
        DirectoryEntry(
            name = "Coast Guard",
            kind = ServiceKind.POLICE,
            phone = "16111",
            area = "Rivers and coast",
            note = "Drowning, river and coastal rescue",
            latitude = 23.8103,
            longitude = 90.4125,
            nationwide = true
        ),
        DirectoryEntry(
            name = "CMP Control Room",
            kind = ServiceKind.POLICE,
            phone = "01320057998",
            altPhone = "01676123456",
            area = "Chattogram",
            note = "Chattogram Metropolitan Police control room",
            latitude = 22.3569,
            longitude = 91.7832
        ),
        DirectoryEntry(
            name = "Women & Children Helpline",
            kind = ServiceKind.POLICE,
            phone = "109",
            altPhone = "10921",
            area = "All Bangladesh",
            note = "Harassment, stalking and violence against women and children",
            latitude = 23.8103,
            longitude = 90.4125,
            nationwide = true
        ),
        DirectoryEntry(
            name = "Child Helpline",
            kind = ServiceKind.POLICE,
            phone = "1098",
            area = "All Bangladesh",
            note = "A child in danger, right now",
            latitude = 23.8103,
            longitude = 90.4125,
            nationwide = true
        ),
        DirectoryEntry(
            name = "Disaster Management Helpline",
            kind = ServiceKind.POLICE,
            phone = "1090",
            area = "All Bangladesh",
            note = "Flood, cyclone, building collapse",
            latitude = 23.8103,
            longitude = 90.4125,
            nationwide = true
        ),

        /* ---------------- hospitals & ambulances ---------------- */
        DirectoryEntry(
            name = "Emergency Ambulance",
            kind = ServiceKind.AMBULANCE,
            phone = "999",
            area = "All Bangladesh",
            note = "Free ambulance dispatch through the national emergency service",
            latitude = 23.8103,
            longitude = 90.4125,
            nationwide = true
        ),
        DirectoryEntry(
            name = "Shastho Batayon — DGHS",
            kind = ServiceKind.AMBULANCE,
            phone = "16263",
            area = "All Bangladesh",
            note = "Doctor advice on the phone and the ambulance directory for your district",
            latitude = 23.8103,
            longitude = 90.4125,
            nationwide = true
        ),
        DirectoryEntry(
            name = "Fire Service & Civil Defence Ambulance",
            kind = ServiceKind.AMBULANCE,
            phone = "199",
            altPhone = "102",
            area = "All Bangladesh",
            note = "Ambulance and rescue from the fire service",
            latitude = 23.8103,
            longitude = 90.4125,
            nationwide = true
        ),
        DirectoryEntry(
            name = "Dhaka Medical College Hospital",
            kind = ServiceKind.HOSPITAL,
            phone = "0255165001",
            area = "Bakshi Bazar, Dhaka",
            note = "Government emergency and trauma",
            latitude = 23.7256,
            longitude = 90.3963
        ),
        DirectoryEntry(
            name = "Combined Military Hospital (CMH)",
            kind = ServiceKind.HOSPITAL,
            phone = "9871469",
            area = "Agargaon, Dhaka",
            note = "24/7 emergency department",
            latitude = 23.7756,
            longitude = 90.3825
        ),
        DirectoryEntry(
            name = "Apollo Hospitals Dhaka",
            kind = ServiceKind.HOSPITAL,
            phone = "10678",
            area = "Bashundhara, Dhaka",
            note = "Private emergency department",
            latitude = 23.8223,
            longitude = 90.4265
        ),
        DirectoryEntry(
            name = "United Hospital",
            kind = ServiceKind.HOSPITAL,
            phone = "10666",
            area = "Gulshan-2, Dhaka",
            note = "Private emergency department",
            latitude = 23.8040,
            longitude = 90.4155
        ),
        DirectoryEntry(
            name = "Labaid Hospital",
            kind = ServiceKind.HOSPITAL,
            phone = "10606",
            area = "Dhanmondi, Dhaka",
            note = "Private hospital, cardiac and general emergency",
            latitude = 23.7461,
            longitude = 90.3742
        ),
        DirectoryEntry(
            name = "Police Blood Bank",
            kind = ServiceKind.HOSPITAL,
            phone = "9362573",
            altPhone = "01713398386",
            area = "Dhaka",
            note = "Blood for a hospital that is asking for it",
            latitude = 23.7335,
            longitude = 90.4026
        )
    )

    /**
     * Instant, offline answer: the numbers that cover this place, nearest first.
     * Nationwide hotlines are pinned above the local ones so the list is never
     * useless, even in a district with no bundled station.
     */
    fun nearby(latitude: Double, longitude: Double): NearbyHelp {
        fun ranked(kinds: Set<ServiceKind>, limit: Int): List<Place> =
            entries.asSequence()
                .filter { it.kind in kinds }
                .map { it.toPlace(latitude, longitude) }
                .sortedWith(compareBy({ !it.national }, { it.distanceMeters }))
                .distinctBy { it.name.lowercase() }
                .take(limit)
                .toList()

        return NearbyHelp(
            police = ranked(setOf(ServiceKind.POLICE), POLICE_LIMIT),
            hospitals = ranked(setOf(ServiceKind.HOSPITAL, ServiceKind.AMBULANCE), HOSPITAL_LIMIT)
        )
    }

    private fun DirectoryEntry.toPlace(latitude: Double, longitude: Double): Place {
        val pointLat = this.latitude
        val pointLon = this.longitude
        val meters = if (pointLat != null && pointLon != null) {
            distanceMeters(latitude, longitude, pointLat, pointLon).roundToInt()
        } else {
            0
        }
        return Place(
            name = name,
            phone = phone,
            distanceMeters = meters,
            detail = note,
            area = area,
            altPhone = altPhone,
            national = nationwide
        )
    }
}
