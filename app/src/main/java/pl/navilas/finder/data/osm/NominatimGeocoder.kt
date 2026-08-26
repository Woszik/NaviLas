package pl.navilas.finder.data.osm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import pl.navilas.finder.BuildConfig
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.util.GeoUtils
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GeocodedPlace(
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
) {
    fun toLatLon(): LatLon = LatLon(latitude, longitude)

    /** Shorter label for picker rows (name + up to two address parts). */
    fun shortLabel(maxParts: Int = 3): String =
        displayName.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(maxParts.coerceAtLeast(1))
            .joinToString(", ")
}

/**
 * Resolves Polish locality names to coordinates via OSM Nominatim.
 * Results are cached in [PersistentLocalityGeocodeStore] (RAM + disk) after user choice.
 */
class NominatimGeocoder(
    private val client: OkHttpClient = defaultClient(),
    private val localityStore: PersistentLocalityGeocodeStore,
) {
    suspend fun geocodeLocality(query: String, countryCode: String = "pl"): GeocodedPlace? =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext null
            localityStore.get(trimmed)?.let { return@withContext it }
            val places = fetchCandidatesFromNetwork(trimmed, countryCode)
            places.firstOrNull()?.also { localityStore.put(trimmed, it) }
        }

    /**
     * Up to [limit] distinct settlements for the locality picker.
     * Always queries Nominatim so ambiguous names (e.g. Olsztyn) stay selectable;
     * disk cache is only written after [rememberChoice].
     */
    suspend fun searchLocalities(
        query: String,
        countryCode: String = "pl",
        limit: Int = DEFAULT_LIMIT,
    ): List<GeocodedPlace> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()
        fetchCandidatesFromNetwork(trimmed, countryCode, limit)
    }

    fun rememberChoice(query: String, place: GeocodedPlace) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) localityStore.put(trimmed, place)
    }

    internal fun fetchCandidatesFromNetwork(
        query: String,
        countryCode: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<GeocodedPlace> {
        val fetchLimit = (limit * 2).coerceIn(limit, 10)
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("countrycodes", countryCode)
            .addQueryParameter("format", "json")
            // Settlements only — avoids duplicate admin boundaries for the same city.
            .addQueryParameter("featureType", "settlement")
            .addQueryParameter("limit", fetchLimit.toString())
            .addQueryParameter("addressdetails", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Accept-Language", "pl")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Nominatim HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return parseResults(body).take(limit)
        }
    }

    companion object {
        private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
        private val USER_AGENT = "NaviLas/${BuildConfig.VERSION_NAME} (Android; contact: woszi@pm.me)"
        const val DEFAULT_LIMIT = 8
        /** Drop near-duplicate Nominatim hits (same town, different admin polygon). */
        const val DEDUPE_METERS = 5_000.0

        fun parseFirstResult(payload: String): GeocodedPlace? = parseResults(payload).firstOrNull()

        fun parseResults(payload: String): List<GeocodedPlace> {
            val arr = JSONArray(payload)
            val parsed = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val lat = item.getDouble("lat")
                    val lon = item.getDouble("lon")
                    val name = labelFor(item)
                    add(GeocodedPlace(latitude = lat, longitude = lon, displayName = name))
                }
            }
            return dedupeNearby(parsed)
        }

        internal fun labelFor(item: JSONObject): String {
            val address = item.optJSONObject("address")
            if (address != null) {
                val name = firstAddressName(address)
                    ?: item.optString("display_name").substringBefore(',').trim()
                        .ifBlank { "Wybrana miejscowość" }
                val parts = mutableListOf(name)
                address.optString("county").trim().takeIf { it.isNotEmpty() && !parts.contains(it) }
                    ?.let { parts.add(it) }
                if (parts.size < 3) {
                    address.optString("municipality").trim()
                        .takeIf { it.isNotEmpty() && !parts.contains(it) && !it.equals(name, ignoreCase = true) }
                        ?.let { parts.add(it) }
                }
                if (parts.size < 3) {
                    address.optString("state").trim()
                        .takeIf { it.isNotEmpty() && !parts.contains(it) }
                        ?.let { parts.add(it) }
                }
                return parts.joinToString(", ")
            }
            return item.optString("display_name").ifBlank { "Wybrana miejscowość" }
        }

        private fun firstAddressName(address: JSONObject): String? {
            for (key in listOf("city", "town", "village", "hamlet", "municipality", "suburb")) {
                val value = address.optString(key).trim()
                if (value.isNotEmpty()) return value
            }
            return null
        }

        internal fun dedupeNearby(
            places: List<GeocodedPlace>,
            maxDistanceMeters: Double = DEDUPE_METERS,
        ): List<GeocodedPlace> {
            if (places.size <= 1) return places
            val kept = ArrayList<GeocodedPlace>(places.size)
            for (candidate in places) {
                val duplicate = kept.any { existing ->
                    GeoUtils.distanceMeters(
                        existing.latitude,
                        existing.longitude,
                        candidate.latitude,
                        candidate.longitude,
                    ) <= maxDistanceMeters
                }
                if (!duplicate) kept.add(candidate)
            }
            return kept
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
