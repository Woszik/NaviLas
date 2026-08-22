package pl.navilas.finder.data.osm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import pl.navilas.finder.domain.LatLon
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GeocodedPlace(
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
) {
    fun toLatLon(): LatLon = LatLon(latitude, longitude)
}

/**
 * Resolves Polish locality names to coordinates via OSM Nominatim.
 * Results are cached in [PersistentLocalityGeocodeStore] (RAM + disk).
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
            val place = fetchFromNetwork(trimmed, countryCode) ?: return@withContext null
            localityStore.put(trimmed, place)
            place
        }

    internal fun fetchFromNetwork(query: String, countryCode: String): GeocodedPlace? {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("countrycodes", countryCode)
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "1")
            .addQueryParameter("addressdetails", "0")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Nominatim HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return parseFirstResult(body)
        }
    }

    companion object {
        private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
        private const val USER_AGENT = "NaviLas/0.4-locality (Android; contact@navilas.local)"

        fun parseFirstResult(payload: String): GeocodedPlace? {
            val arr = JSONArray(payload)
            if (arr.length() == 0) return null
            val item = arr.getJSONObject(0)
            val lat = item.getDouble("lat")
            val lon = item.getDouble("lon")
            val name = item.optString("display_name").ifBlank { "Wybrana miejscowość" }
            return GeocodedPlace(latitude = lat, longitude = lon, displayName = name)
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
