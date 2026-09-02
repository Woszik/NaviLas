package pl.navilas.finder.data.osm

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.OsmWaterFeature
import java.io.IOException

/**
 * Fetches OSM rivers / lakes / ponds in a bbox. Same Overpass endpoint as roads.
 */
class OverpassWaterClient(
    private val client: OkHttpClient = OverpassRoadClient.defaultClient(),
    private val endpoint: String = OverpassRoadClient.DEFAULT_ENDPOINT,
) {
    fun fetchWaterInBbox(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): List<OsmWaterFeature> = parseElements(execute(buildBboxQuery(south, west, north, east)))

    fun buildBboxQuery(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): String {
        val box = "${fmt(south)},${fmt(west)},${fmt(north)},${fmt(east)}"
        return buildString {
            append("[out:json][timeout:60];\n(\n")
            append("  way[\"waterway\"~\"^(river|stream|canal)$\"]($box);\n")
            append("  way[\"natural\"=\"water\"]($box);\n")
            append("  way[\"landuse\"=\"reservoir\"]($box);\n")
            append("  way[\"water\"~\"^(lake|pond|reservoir|oxbow)$\"]($box);\n")
            append("  node[\"natural\"=\"water\"]($box);\n")
            append("  relation[\"natural\"=\"water\"]($box);\n")
            append(");\nout tags geom;")
        }
    }

    fun parseElements(payload: String): List<OsmWaterFeature> {
        val root = JSONObject(payload)
        val elements = root.optJSONArray("elements") ?: JSONArray()
        val out = ArrayList<OsmWaterFeature>(elements.length())
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: JSONObject()
            if (!acceptTags(tags)) continue
            when (el.optString("type")) {
                "way" -> parseWay(el)?.let { out += it }
                "node" -> parseNode(el)?.let { out += it }
                "relation" -> out += parseRelationMembers(el)
            }
        }
        return out
    }

    private fun acceptTags(tags: JSONObject): Boolean = OsmWaterClassifier.accept(
        waterway = tags.optString("waterway").ifBlank { null },
        natural = tags.optString("natural").ifBlank { null },
        water = tags.optString("water").ifBlank { null },
        landuse = tags.optString("landuse").ifBlank { null },
        leisure = tags.optString("leisure").ifBlank { null },
        intermittent = tags.optString("intermittent").ifBlank { null },
        seasonal = tags.optString("seasonal").ifBlank { null },
    )

    private fun parseWay(el: JSONObject): OsmWaterFeature? {
        val geom = readGeometryArray(el.optJSONArray("geometry")) ?: return null
        val id = "way/${el.optLong("id", -1L)}"
        val closed = geom.size >= 3 && isClosedRing(geom)
        return OsmWaterFeature.of(id, polygon = closed, geometry = geom)
    }

    private fun parseNode(el: JSONObject): OsmWaterFeature? {
        if (!el.has("lat") || !el.has("lon")) return null
        val id = "node/${el.optLong("id", -1L)}"
        return OsmWaterFeature.of(
            id,
            polygon = false,
            geometry = listOf(LatLon(el.getDouble("lat"), el.getDouble("lon"))),
        )
    }

    private fun parseRelationMembers(el: JSONObject): List<OsmWaterFeature> {
        val relId = el.optLong("id", -1L)
        val members = el.optJSONArray("members") ?: return emptyList()
        val out = ArrayList<OsmWaterFeature>()
        for (i in 0 until members.length()) {
            val member = members.getJSONObject(i)
            if (member.optString("type") != "way") continue
            val geom = readGeometryArray(member.optJSONArray("geometry")) ?: continue
            val memberId = member.optLong("ref", i.toLong())
            val closed = geom.size >= 3 && isClosedRing(geom)
            OsmWaterFeature.of("relation/$relId/$memberId", polygon = closed, geometry = geom)
                ?.let { out += it }
        }
        return out
    }

    private fun readGeometryArray(geometry: JSONArray?): List<LatLon>? {
        if (geometry == null || geometry.length() < 1) return null
        val points = ArrayList<LatLon>(geometry.length())
        for (g in 0 until geometry.length()) {
            val node = geometry.getJSONObject(g)
            points += LatLon(latitude = node.getDouble("lat"), longitude = node.getDouble("lon"))
        }
        return points
    }

    private fun execute(query: String): String {
        val form = FormBody.Builder().add("data", query).build()
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", OverpassRoadClient.USER_AGENT)
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

    private fun isClosedRing(geom: List<LatLon>): Boolean {
        val a = geom.first()
        val b = geom.last()
        return kotlin.math.abs(a.latitude - b.latitude) < 1e-6 &&
            kotlin.math.abs(a.longitude - b.longitude) < 1e-6
    }

    private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.6f", value)
}
