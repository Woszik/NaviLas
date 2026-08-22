package pl.navilas.finder.data.osm

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.Road
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches OSM highway ways via Overpass API for local buffers around POIs.
 * See docs/OSM_ROADS.md.
 */
class OverpassRoadClient(
    private val client: OkHttpClient = defaultClient(),
    private val endpoint: String = DEFAULT_ENDPOINT,
) : HighwayAroundFetcher, OverpassBboxFetcher {
    override fun fetchHighwaysAround(
        points: List<LatLon>,
        radiusMeters: Double,
    ): List<Road> {
        if (points.isEmpty()) return emptyList()
        val roads = LinkedHashMap<String, Road>()
        points.chunked(BATCH_SIZE).forEach { batch ->
            val query = buildAroundQuery(batch, radiusMeters)
            val payload = execute(query)
            parseWays(payload).forEach { road ->
                roads.putIfAbsent(road.id, road)
            }
        }
        return roads.values.toList()
    }

    override fun fetchHighwaysInBbox(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): List<Road> {
        val query = buildBboxQuery(south, west, north, east)
        return parseWays(execute(query))
    }

    fun buildBboxQuery(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): String = buildString {
        append("[out:json][timeout:60];\n(\n")
        append("  way[\"highway\"](")
        append(formatCoord(south))
        append(',')
        append(formatCoord(west))
        append(',')
        append(formatCoord(north))
        append(',')
        append(formatCoord(east))
        append(");\n")
        append(");\nout tags geom;")
    }

    fun buildAroundQuery(points: List<LatLon>, radiusMeters: Double): String {
        val body = buildString {
            append("[out:json][timeout:60];\n(\n")
            points.forEach { p ->
                append("  way(around:")
                append(radiusMeters.toInt())
                append(',')
                append(formatCoord(p.latitude))
                append(',')
                append(formatCoord(p.longitude))
                append(")[highway];\n")
            }
            append(");\nout tags geom;")
        }
        return body
    }

    private fun execute(query: String): String {
        val form = FormBody.Builder()
            .add("data", query)
            .build()
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(form)
            .build()
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
    }

    fun parseWays(payload: String): List<Road> {
        val root = JSONObject(payload)
        if (root.has("remark") && !root.has("elements")) {
            // soft error remark with empty elements still ok
        }
        val elements = root.optJSONArray("elements") ?: JSONArray()
        val roads = ArrayList<Road>(elements.length())
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            if (el.optString("type") != "way") continue
            val tags = el.optJSONObject("tags") ?: JSONObject()
            val highway = tags.optString("highway").takeIf { it.isNotBlank() } ?: continue
            val geometry = el.optJSONArray("geometry") ?: JSONArray()
            if (geometry.length() < 2) continue
            val points = ArrayList<LatLon>(geometry.length())
            for (g in 0 until geometry.length()) {
                val node = geometry.getJSONObject(g)
                points += LatLon(latitude = node.getDouble("lat"), longitude = node.getDouble("lon"))
            }
            val osmId = el.optLong("id", -1L)
            roads += Road(
                id = "way/$osmId",
                type = highway,
                access = tags.optString("access").takeIf { it.isNotBlank() },
                motorVehicle = tags.optString("motor_vehicle").takeIf { it.isNotBlank() },
                motorcycle = tags.optString("motorcycle").takeIf { it.isNotBlank() },
                vehicle = tags.optString("vehicle").takeIf { it.isNotBlank() },
                name = tags.optString("name").takeIf { it.isNotBlank() },
                geometry = points,
            )
        }
        return roads
    }

    private fun formatCoord(value: Double): String = String.format(java.util.Locale.US, "%.6f", value)

    companion object {
        const val DEFAULT_ENDPOINT = "https://overpass-api.de/api/interpreter"
        const val DEFAULT_RADIUS_METERS = 400.0
        const val BATCH_SIZE = 20
        const val USER_AGENT = "NaviLas/0.2-checkpoint2 (Android; prototype; contact: local-dev)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(100, TimeUnit.SECONDS)
            .build()
    }
}
