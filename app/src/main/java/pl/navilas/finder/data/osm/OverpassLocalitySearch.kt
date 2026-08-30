package pl.navilas.finder.data.osm

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import pl.navilas.finder.BuildConfig
import java.io.IOException

/**
 * Exact settlement lookup via OSM Overpass (complements Nominatim's ~50-result cap).
 * Finds all [place=city|town|village|hamlet] nodes in Poland with a given [name].
 */
internal object OverpassLocalitySearch {
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    private val USER_AGENT = "NaviLas/${BuildConfig.VERSION_NAME} (Android; contact: woszi@pm.me)"

    fun buildQuery(settlementName: String): String {
        val escaped = settlementName
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return """
            [out:json][timeout:60];
            area["ISO3166-1"="PL"]["admin_level"="2"]->.pl;
            (
              node["place"~"^(city|town|village|hamlet)$"]["name"="$escaped"](area.pl);
            );
            out body;
        """.trimIndent()
    }

    fun fetch(
        client: OkHttpClient,
        settlementName: String,
        normalizedQuery: String,
    ): List<GeocodedPlace> {
        if (settlementName.isBlank()) return emptyList()
        val body = FormBody.Builder().add("data", buildQuery(settlementName)).build()
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("User-Agent", USER_AGENT)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Overpass HTTP ${response.code}")
            }
            val payload = response.body?.string().orEmpty()
            return parseResponse(payload, normalizedQuery)
        }
    }

    fun parseResponse(payload: String, normalizedQuery: String): List<GeocodedPlace> {
        val root = JSONObject(payload)
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val places = buildList {
            for (i in 0 until elements.length()) {
                val item = elements.getJSONObject(i)
                if (item.optString("type") != "node") continue
                val tags = item.optJSONObject("tags") ?: continue
                val name = tags.optString("name").trim()
                if (name.isEmpty()) continue
                if (NominatimGeocoder.normalizeLocalityName(name) != normalizedQuery) continue
                val lat = item.optDouble("lat")
                val lon = item.optDouble("lon")
                if (lat == 0.0 && lon == 0.0) continue
                val county = tags.optString("is_in:county").trim()
                    .ifBlank { countyFromWikipedia(tags.optString("wikipedia")).orEmpty() }
                    .ifBlank { null }
                val voivodeship = tags.optString("is_in:province").trim().ifBlank { null }
                add(
                    GeocodedPlace(
                        latitude = lat,
                        longitude = lon,
                        displayName = labelFromTags(tags, name),
                        voivodeship = voivodeship,
                        county = county,
                    ),
                )
            }
        }
        return places.sortedWith(NominatimGeocoder.localitySortOrder())
    }

    internal fun labelFromTags(tags: JSONObject, settlementName: String): String {
        val parts = mutableListOf(settlementName)
        tags.optString("is_in:county").trim()
            .takeIf { it.isNotEmpty() && !parts.contains(it) }
            ?.let { parts.add(it) }
        tags.optString("is_in:municipality").trim()
            .takeIf { it.isNotEmpty() && !parts.contains(it) && !it.equals(settlementName, ignoreCase = true) }
            ?.let { parts.add(it) }
        if (parts.size < 3) {
            tags.optString("is_in:province").trim()
                .takeIf { it.isNotEmpty() && !parts.contains(it) }
                ?.let { parts.add(it) }
        }
        if (parts.size == 1) {
            countyFromWikipedia(tags.optString("wikipedia"))?.let { parts.add(it) }
        }
        return parts.take(3).joinToString(", ")
    }

    /** e.g. pl:Władysławów (powiat turecki) → powiat turecki */
    internal fun countyFromWikipedia(wikipedia: String): String? {
        val trimmed = wikipedia.trim()
        if (trimmed.isEmpty()) return null
        val open = trimmed.lastIndexOf('(')
        val close = trimmed.lastIndexOf(')')
        if (open < 0 || close <= open) return null
        return trimmed.substring(open + 1, close).trim().takeIf { it.isNotEmpty() }
    }
}
