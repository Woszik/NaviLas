package pl.navilas.finder.data.osm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import pl.navilas.finder.domain.RelatedBdlObject
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.domain.ZanocujStatus
import pl.navilas.finder.util.GeoUtils
import java.io.IOException
import java.util.Locale
import kotlin.math.min

/**
 * Experimental CZ border add-on: OSM picnic/shelter with a public parking within 300 m.
 * Only objects inside Czechia (`ISO3166-1=CZ`). Not an official LČR catalog.
 */
class CzechOsmRestClient(
    private val client: OkHttpClient = OverpassRoadClient.defaultClient(),
    private val endpoints: List<String> = OverpassRoadClient.DEFAULT_ENDPOINTS,
) {
    suspend fun findRestSites(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): List<RestSite> = withContext(Dispatchers.IO) {
        if (!envelopeMayHitCzechia(latitude, longitude, radiusKm)) return@withContext emptyList()
        val radiusM = (radiusKm * 1000.0).toInt().coerceAtLeast(1_000)
        val restElements = parseElements(execute(restQuery(latitude, longitude, radiusM)))
        val parkingElements = parseElements(execute(parkingQuery(latitude, longitude, radiusM)))
        buildSites(
            originLat = latitude,
            originLon = longitude,
            radiusKm = radiusKm,
            restElements = restElements,
            parkingElements = parkingElements,
        )
    }

    fun buildSites(
        originLat: Double,
        originLon: Double,
        radiusKm: Double,
        restElements: List<JSONObject>,
        parkingElements: List<JSONObject>,
    ): List<RestSite> {
        val rests = restElements.mapNotNull { toPoi(it) }
            .filter { GeoUtils.distanceKm(originLat, originLon, it.lat, it.lon) <= radiusKm }
        val parkings = parkingElements.mapNotNull { toPoi(it) }
            .filter { accessOk(it.tags) }
            .filter { GeoUtils.distanceKm(originLat, originLon, it.lat, it.lon) <= radiusKm }

        val withParking = rests.mapNotNull { rest ->
            var best: Pair<OsmPoi, Double>? = null
            for (park in parkings) {
                val d = GeoUtils.distanceMeters(rest.lat, rest.lon, park.lat, park.lon)
                if (d <= PARKING_LINK_METERS && (best == null || d < best.second)) {
                    best = park to d
                }
            }
            best?.let { rest to it }
        }
        if (withParking.isEmpty()) return emptyList()

        // Collapse near-duplicate picnic nodes that share the same parking.
        val sorted = withParking.sortedBy { it.second.second }
        val kept = ArrayList<Pair<OsmPoi, Pair<OsmPoi, Double>>>(sorted.size)
        for (candidate in sorted) {
            val nearDup = kept.any { existing ->
                GeoUtils.distanceMeters(
                    candidate.first.lat,
                    candidate.first.lon,
                    existing.first.lat,
                    existing.first.lon,
                ) <= DEDUP_METERS
            }
            if (!nearDup) kept += candidate
        }

        return kept.map { (rest, parkPair) ->
            val (park, dist) = parkPair
            val name = rest.name?.takeIf { it.isNotBlank() }
                ?: park.name?.takeIf { it.isNotBlank() }
                ?: DEFAULT_NAME
            val kindLabel = when {
                rest.tags.optString("tourism") == "picnic_site" -> "picnic_site"
                else -> "shelter (${rest.tags.optString("shelter_type").ifBlank { "?" }})"
            }
            RestSite(
                id = "osm-cz/${rest.type}/${rest.id}",
                name = name,
                latitude = rest.lat,
                longitude = rest.lon,
                description = "OSM · CZ · $kindLabel · parking ~${dist.toInt()} m · " +
                    "wjazd/postój w lesie w CZ zabroniony — nawiguj na parking",
                sourceLayerId = LAYER_CZ_REST,
                sourceLayerName = LAYER_NAME_CZ_REST,
                features = setOf(SiteFeature.PARKING, SiteFeature.WIATA).let { base ->
                    if (rest.tags.optString("tourism") == "picnic_site" ||
                        rest.tags.optString("shelter_type") == "picnic_shelter"
                    ) {
                        base
                    } else {
                        setOf(SiteFeature.PARKING)
                    }
                },
                relatedObjects = listOf(
                    RelatedBdlObject(
                        id = "osm-cz/${park.type}/${park.id}",
                        layerId = LAYER_CZ_PARKING,
                        layerName = LAYER_NAME_CZ_PARKING,
                        name = park.name?.takeIf { it.isNotBlank() } ?: "Parking",
                        latitude = park.lat,
                        longitude = park.lon,
                        distanceMeters = dist,
                        typeCode = "osm_parking",
                    ),
                ),
                zanocujStatus = ZanocujStatus.OUTSIDE_ZONE,
            )
        }.sortedBy {
            GeoUtils.distanceMeters(originLat, originLon, it.latitude, it.longitude)
        }
    }

    private fun restQuery(lat: Double, lon: Double, radiusM: Int): String = """
        [out:json][timeout:90];
        area["ISO3166-1"="CZ"]["admin_level"="2"]->.cz;
        (
          nwr(area.cz)(around:$radiusM,$lat,$lon)["tourism"="picnic_site"];
          nwr(area.cz)(around:$radiusM,$lat,$lon)["amenity"="shelter"]["shelter_type"~"^(picnic_shelter|weather_shelter|lean_to)$"];
        );
        out center tags;
    """.trimIndent()

    private fun parkingQuery(lat: Double, lon: Double, radiusM: Int): String = """
        [out:json][timeout:90];
        area["ISO3166-1"="CZ"]["admin_level"="2"]->.cz;
        nwr(area.cz)(around:$radiusM,$lat,$lon)["amenity"="parking"];
        out center tags;
    """.trimIndent()

    private fun execute(query: String): String {
        val form = FormBody.Builder().add("data", query).build()
        var lastError: IOException? = null
        for (ep in endpoints) {
            val request = Request.Builder()
                .url(ep)
                .header("User-Agent", OverpassRoadClient.USER_AGENT)
                .header("Accept", "application/json")
                .post(form)
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.code == 429) {
                        throw IOException("Overpass limit (HTTP 429) — spróbuj ponownie za chwilę.")
                    }
                    if (!response.isSuccessful) {
                        throw IOException("Overpass HTTP ${response.code}")
                    }
                    return response.body?.string().orEmpty().ifBlank {
                        throw IOException("Pusta odpowiedź Overpass")
                    }
                }
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Overpass: brak odpowiedzi")
    }

    private fun parseElements(payload: String): List<JSONObject> {
        val root = JSONObject(payload)
        val elements = root.optJSONArray("elements") ?: JSONArray()
        val out = ArrayList<JSONObject>(elements.length())
        for (i in 0 until elements.length()) {
            out += elements.getJSONObject(i)
        }
        return out
    }

    private fun toPoi(el: JSONObject): OsmPoi? {
        val type = el.optString("type").ifBlank { return null }
        val id = el.optLong("id", -1L)
        if (id < 0L) return null
        val tags = el.optJSONObject("tags") ?: JSONObject()
        val lat: Double
        val lon: Double
        when {
            el.has("lat") && el.has("lon") -> {
                lat = el.getDouble("lat")
                lon = el.getDouble("lon")
            }
            else -> {
                val center = el.optJSONObject("center") ?: return null
                lat = center.optDouble("lat", Double.NaN)
                lon = center.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) return null
            }
        }
        val name = sequenceOf("name", "name:cs", "name:pl", "name:en")
            .map { tags.optString(it) }
            .firstOrNull { it.isNotBlank() }
        return OsmPoi(type = type, id = id, lat = lat, lon = lon, name = name, tags = tags)
    }

    private fun accessOk(tags: JSONObject): Boolean {
        val access = tags.optString("access").lowercase(Locale.ROOT)
        val motor = tags.optString("motor_vehicle").lowercase(Locale.ROOT)
        if (access in RESTRICTED_ACCESS) return false
        if (motor in setOf("no", "private")) return false
        return true
    }

    private data class OsmPoi(
        val type: String,
        val id: Long,
        val lat: Double,
        val lon: Double,
        val name: String?,
        val tags: JSONObject,
    )

    companion object {
        const val LAYER_CZ_REST = 9015
        const val LAYER_CZ_PARKING = 9017
        const val LAYER_NAME_CZ_REST = "OSM · Czechy (test)"
        const val LAYER_NAME_CZ_PARKING = "OSM parking CZ"
        const val PARKING_LINK_METERS = 300.0
        private const val DEDUP_METERS = 35.0
        private const val DEFAULT_NAME = "Miejsce odpoczynku (CZ)"

        /** Loose WGS84 window for Czechia — skip Overpass when search cannot reach CZ. */
        private const val CZ_MIN_LAT = 48.55
        private const val CZ_MAX_LAT = 51.10
        private const val CZ_MIN_LON = 12.05
        private const val CZ_MAX_LON = 18.90

        private val RESTRICTED_ACCESS = setOf("private", "no", "customers", "permit")

        fun envelopeMayHitCzechia(lat: Double, lon: Double, radiusKm: Double): Boolean {
            val env = GeoUtils.envelopeAround(lat, lon, radiusKm)
            return env.xmax >= CZ_MIN_LON && env.xmin <= CZ_MAX_LON &&
                env.ymax >= CZ_MIN_LAT && env.ymin <= CZ_MAX_LAT
        }

        fun mergePreferringBdl(bdl: List<RestSite>, czech: List<RestSite>): List<RestSite> {
            if (czech.isEmpty()) return bdl
            if (bdl.isEmpty()) return czech
            val merged = ArrayList<RestSite>(bdl.size + czech.size)
            merged.addAll(bdl)
            for (cz in czech) {
                val dup = bdl.any {
                    GeoUtils.distanceMeters(it.latitude, it.longitude, cz.latitude, cz.longitude) <=
                        min(DEDUP_METERS, 50.0)
                }
                if (!dup) merged += cz
            }
            return merged
        }
    }
}
