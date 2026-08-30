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
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GeocodedPlace(
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
    val voivodeship: String? = null,
    val county: String? = null,
) {
    fun toLatLon(): LatLon = LatLon(latitude, longitude)

    /** Shorter label for picker rows (name + up to two address parts). */
    fun shortLabel(maxParts: Int = 3): String =
        displayName.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(maxParts.coerceAtLeast(1))
            .joinToString(", ")

    /** Row label when results are grouped by [voivodeship] (omit województwo part). */
    fun pickerRowLabel(): String {
        val parts = displayName.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return displayName
        val woj = voivodeship?.let { VoivodeshipResolver.formatVoivodeship(it) }
        val filtered = if (woj != null) {
            parts.filterNot { part ->
                part.equals(voivodeship, ignoreCase = true) ||
                    part.equals("województwo $woj", ignoreCase = true) ||
                    part.equals(woj, ignoreCase = true)
            }
        } else {
            parts
        }
        return filtered.take(3).joinToString(", ").ifBlank { parts.first() }
    }
}

/**
 * Resolves Polish locality names to coordinates via OSM Nominatim.
 * Results are cached in [PersistentLocalityGeocodeStore] (RAM + disk) after user choice.
 */
class NominatimGeocoder(
    private val client: OkHttpClient = defaultClient(),
    private val localityStore: PersistentLocalityGeocodeStore,
    voivodeshipCacheFile: File? = null,
) {
    private val voivodeshipResolver = voivodeshipCacheFile?.let {
        VoivodeshipResolver(client, it)
    }
    suspend fun geocodeLocality(query: String, countryCode: String = "pl"): GeocodedPlace? =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext null
            localityStore.get(trimmed)?.let { return@withContext it }
            val places = fetchCandidatesFromNetwork(trimmed, countryCode)
            places.firstOrNull()?.also { localityStore.put(trimmed, it) }
        }

    /**
     * All distinct settlements matching [query] for the locality picker (Nominatim + Overpass).
     * Disk cache is only written after [rememberChoice].
     */
    suspend fun searchLocalities(
        query: String,
        countryCode: String = "pl",
    ): List<GeocodedPlace> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()
        val normalized = normalizeLocalityName(trimmed)
        val nominatim = try {
            fetchCandidatesFromNetwork(trimmed, countryCode)
        } catch (_: IOException) {
            emptyList()
        }
        val overpassName = resolveCanonicalSettlementName(trimmed, nominatim, normalized)
        val overpass = try {
            OverpassLocalitySearch.fetch(client, overpassName, normalized)
        } catch (_: IOException) {
            emptyList()
        }
        val merged = mergeLocalityResults(overpass, nominatim)
        voivodeshipResolver?.enrich(merged) ?: merged
    }

    fun rememberChoice(query: String, place: GeocodedPlace) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) localityStore.put(trimmed, place)
    }

    internal fun fetchCandidatesFromNetwork(
        query: String,
        countryCode: String,
    ): List<GeocodedPlace> {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("countrycodes", countryCode)
            .addQueryParameter("format", "json")
            // Settlements only — avoids duplicate admin boundaries for the same city.
            .addQueryParameter("featureType", "settlement")
            .addQueryParameter("limit", NOMINATIM_MAX_LIMIT.toString())
            .addQueryParameter("dedupe", "0")
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
            return parseResults(body, localityQuery = query)
        }
    }

    companion object {
        private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
        private val USER_AGENT = "NaviLas/${BuildConfig.VERSION_NAME} (Android; contact: woszi@pm.me)"
        /** Nominatim API cap per request; Overpass fills gaps for duplicate names. */
        const val NOMINATIM_MAX_LIMIT = 50
        /** Drop near-duplicate Nominatim hits (same town, different admin polygon). */
        const val DEDUPE_METERS = 5_000.0
        /** When merging Overpass + Nominatim, treat closer hits as the same settlement. */
        const val MERGE_DEDUPE_METERS = 2_000.0

        internal fun resolveCanonicalSettlementName(
            query: String,
            nominatim: List<GeocodedPlace>,
            normalizedQuery: String,
        ): String {
            for (place in nominatim) {
                val primary = place.displayName.substringBefore(',').trim()
                if (normalizeLocalityName(primary) == normalizedQuery) return primary
            }
            return query.trim()
        }

        internal fun mergeLocalityResults(
            overpass: List<GeocodedPlace>,
            nominatim: List<GeocodedPlace>,
        ): List<GeocodedPlace> {
            val merged = ArrayList<GeocodedPlace>(overpass.size + nominatim.size)
            for (candidate in overpass + nominatim) {
                val existing = merged.firstOrNull { item ->
                    GeoUtils.distanceMeters(
                        item.latitude,
                        item.longitude,
                        candidate.latitude,
                        candidate.longitude,
                    ) <= MERGE_DEDUPE_METERS
                }
                if (existing == null) {
                    merged.add(candidate)
                } else {
                    val index = merged.indexOf(existing)
                    merged[index] = existing.mergeMissing(candidate)
                }
            }
            return merged.sortedWith(localitySortOrder())
        }

        private fun GeocodedPlace.mergeMissing(other: GeocodedPlace): GeocodedPlace = copy(
            voivodeship = voivodeship ?: other.voivodeship,
            county = county ?: other.county,
        )

        internal fun localitySortOrder(): Comparator<GeocodedPlace> =
            compareBy(
                { VoivodeshipResolver.groupLabel(it) },
                { it.county ?: it.displayName.substringAfter(',', "") },
                { it.displayName.lowercase() },
            )

        fun parseFirstResult(payload: String): GeocodedPlace? =
            parseResults(payload).firstOrNull()

        fun parseResults(payload: String, localityQuery: String? = null): List<GeocodedPlace> {
            val arr = JSONArray(payload)
            val normalizedQuery = localityQuery?.let(::normalizeLocalityName)?.takeIf { it.isNotEmpty() }
            val parsed = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    if (normalizedQuery != null && !matchesLocalityQuery(item, normalizedQuery)) continue
                    add(placeFromNominatimItem(item))
                }
            }
            return dedupeNearby(parsed)
        }

        /** Case- and diacritic-insensitive match on settlement name (city/town/village/…). */
        internal fun matchesLocalityQuery(item: JSONObject, normalizedQuery: String): Boolean {
            val settlement = settlementName(item) ?: return false
            return normalizeLocalityName(settlement) == normalizedQuery
        }

        internal fun settlementName(item: JSONObject): String? {
            val address = item.optJSONObject("address") ?: return null
            return firstAddressName(address)
        }

        internal fun normalizeLocalityName(raw: String): String {
            val replaced = raw.trim()
                .replace('ł', 'l')
                .replace('Ł', 'L')
            val nfd = Normalizer.normalize(replaced, Normalizer.Form.NFD)
            return nfd.replace(Regex("\\p{Mn}+"), "")
                .lowercase(Locale.forLanguageTag("pl-PL"))
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

        internal fun placeFromNominatimItem(item: JSONObject): GeocodedPlace {
            val address = item.optJSONObject("address")
            return GeocodedPlace(
                latitude = item.getDouble("lat"),
                longitude = item.getDouble("lon"),
                displayName = labelFor(item),
                voivodeship = address?.optString("state")?.trim()?.takeIf { it.isNotEmpty() },
                county = address?.optString("county")?.trim()?.takeIf { it.isNotEmpty() },
            )
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
