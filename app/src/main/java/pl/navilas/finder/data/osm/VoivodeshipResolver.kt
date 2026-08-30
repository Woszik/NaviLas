package pl.navilas.finder.data.osm

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import pl.navilas.finder.BuildConfig
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Fills missing [GeocodedPlace.voivodeship] using county lookup (cached on disk).
 */
internal class VoivodeshipResolver(
    private val client: OkHttpClient,
    private val cacheFile: File,
) {
    private val countyToVoivodeship = loadCache().toMutableMap()

    fun enrich(places: List<GeocodedPlace>): List<GeocodedPlace> {
        if (places.isEmpty()) return places
        val countiesToResolve = places
            .filter { it.voivodeship.isNullOrBlank() }
            .mapNotNull { it.county ?: extractCountyFromDisplayName(it.displayName) }
            .distinct()
        for (county in countiesToResolve) {
            val key = normalizeCountyKey(county)
            if (key.isEmpty() || countyToVoivodeship.containsKey(key)) continue
            resolveVoivodeshipForCounty(county)
            Thread.sleep(NOMINATIM_MIN_INTERVAL_MS)
        }
        val enriched = places.map { place ->
            if (!place.voivodeship.isNullOrBlank()) return@map place
            val county = place.county ?: extractCountyFromDisplayName(place.displayName) ?: return@map place
            val woj = countyToVoivodeship[normalizeCountyKey(county)] ?: return@map place
            place.copy(voivodeship = woj)
        }
        persistCacheIfNeeded()
        return enriched
    }

    private fun resolveVoivodeshipForCounty(county: String): String? {
        val key = normalizeCountyKey(county)
        if (key.isEmpty()) return null
        countyToVoivodeship[key]?.let { return it }
        val fetched = fetchVoivodeshipForCounty(county) ?: return null
        countyToVoivodeship[key] = fetched
        return fetched
    }

    private fun fetchVoivodeshipForCounty(county: String): String? {
        val url = NOMINATIM_SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("county", county)
            .addQueryParameter("countrycodes", "pl")
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "1")
            .addQueryParameter("addressdetails", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                val arr = org.json.JSONArray(body)
                if (arr.length() == 0) return null
                val state = arr.getJSONObject(0)
                    .optJSONObject("address")
                    ?.optString("state")
                    ?.trim()
                    .orEmpty()
                formatVoivodeship(state)?.takeIf { it.isNotEmpty() }
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun persistCacheIfNeeded() {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val root = JSONObject()
            countyToVoivodeship.forEach { (county, woj) -> root.put(county, woj) }
            cacheFile.writeText(root.toString())
        }
    }

    private fun loadCache(): Map<String, String> = runCatching {
        if (!cacheFile.exists()) return emptyMap()
        val root = JSONObject(cacheFile.readText())
        buildMap {
            root.keys().forEach { key ->
                put(key, root.getString(key))
            }
        }
    }.getOrDefault(emptyMap())

    companion object {
        private const val NOMINATIM_SEARCH = "https://nominatim.openstreetmap.org/search"
        private val USER_AGENT = "NaviLas/${BuildConfig.VERSION_NAME} (Android; contact: woszi@pm.me)"

        fun formatVoivodeship(raw: String?): String? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val withoutPrefix = trimmed.removePrefix("województwo ").trim()
            if (withoutPrefix.isEmpty()) return null
            return withoutPrefix.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.forLanguageTag("pl-PL")) else ch.toString()
            }
        }

        fun extractCountyFromDisplayName(displayName: String): String? {
            return displayName.split(',')
                .map { it.trim() }
                .drop(1)
                .firstOrNull { part ->
                    part.startsWith("powiat ", ignoreCase = true) ||
                        part.startsWith("Powiat ", ignoreCase = true)
                }
        }

        fun normalizeCountyKey(county: String): String =
            county.trim().lowercase(Locale.forLanguageTag("pl-PL"))

        fun groupLabel(place: GeocodedPlace): String =
            formatVoivodeship(place.voivodeship) ?: UNKNOWN_GROUP

        const val UNKNOWN_GROUP = "Inne"
        private const val NOMINATIM_MIN_INTERVAL_MS = 1_100L
    }
}
