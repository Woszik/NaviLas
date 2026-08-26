package pl.navilas.finder.data.bdl

import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.Poi
import pl.navilas.finder.domain.PoiCategory
import pl.navilas.finder.domain.PoiGeometryKind
import pl.navilas.finder.util.GeoUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * Maps ArcGIS JSON features from selected BDL layers into domain [Poi] objects.
 *
 * Layers (verified against live MapServer):
 * - 17 Parkingi leśne (point) → base PARKING; `wiata`/`palenisko` may add REST/FIRE on the same record
 * - 15 Miejsca wypoczynku (point) → base REST; `palenisko` may add FIRE (wiata reinforces REST)
 * - 0 Zanocuj w Lesie (polygon) → base CAMP + AREA geometry; amenities may add REST/FIRE
 *
 * One BDL feature → one [Poi]. Never duplicate rows for wiata/palenisko.
 */
object BdlMapper {
    const val SOURCE = "BDL Czas w Las"

    /** Inclusive WGS84 window used to drop corrupt / swapped BDL point rows. */
    private const val MIN_LAT = 48.0
    private const val MAX_LAT = 56.0
    private const val MIN_LON = 13.0
    private const val MAX_LON = 25.0

    fun mapParkingFeature(feature: JSONObject): Poi? {
        val attrs = feature.optJSONObject("attributes") ?: return null
        val point = pointFromGeometry(feature.optJSONObject("geometry")) ?: return null
        val categories = linkedSetOf(PoiCategory.PARKING)
        addAmenityCategories(attrs, categories)
        return toPoi(
            layerId = BdlRepository.LAYER_PARKING,
            attrs = attrs,
            latitude = point.second,
            longitude = point.first,
            categories = categories,
            geometryKind = PoiGeometryKind.POINT,
            areaRings = emptyList(),
        )
    }

    fun mapRestFeature(feature: JSONObject): Poi? {
        val attrs = feature.optJSONObject("attributes") ?: return null
        val point = pointFromGeometry(feature.optJSONObject("geometry")) ?: return null
        val categories = linkedSetOf(PoiCategory.REST)
        addAmenityCategories(attrs, categories)
        return toPoi(
            layerId = BdlRepository.LAYER_REST,
            attrs = attrs,
            latitude = point.second,
            longitude = point.first,
            categories = categories,
            geometryKind = PoiGeometryKind.POINT,
            areaRings = emptyList(),
        )
    }

    fun mapCampFeature(feature: JSONObject): Poi? {
        val attrs = feature.optJSONObject("attributes") ?: return null
        val geometry = feature.optJSONObject("geometry") ?: return null
        val rings = ringsFromPolygon(geometry) ?: return null
        if (rings.isEmpty()) return null
        val outer = rings.first().map { doubleArrayOf(it.longitude, it.latitude) }
        val centroid = GeoUtils.ringCentroid(outer) ?: return null
        val categories = linkedSetOf(PoiCategory.CAMP)
        addAmenityCategories(attrs, categories)
        return toPoi(
            layerId = BdlRepository.LAYER_CAMP,
            attrs = attrs,
            latitude = centroid.second,
            longitude = centroid.first,
            categories = categories,
            geometryKind = PoiGeometryKind.AREA,
            areaRings = rings,
        )
    }

    /**
     * Adds REST/FIRE from amenity flags without duplicating categories already present.
     * Used especially for layer 17 (parking may also have wiata/palenisko).
     */
    fun addAmenityCategories(attrs: JSONObject, categories: MutableSet<PoiCategory>) {
        if (isYes(attrs.optString("wiata"))) {
            categories.add(PoiCategory.REST)
        }
        if (isYes(attrs.optString("palenisko"))) {
            categories.add(PoiCategory.FIRE)
        }
    }

    private fun toPoi(
        layerId: Int,
        attrs: JSONObject,
        latitude: Double,
        longitude: Double,
        categories: Set<PoiCategory>,
        geometryKind: PoiGeometryKind,
        areaRings: List<List<LatLon>>,
    ): Poi {
        val name = attrs.optString("nzw_ob").ifBlank { "Obiekt BDL" }
        return Poi(
            id = BdlIdentity.resolve(layerId, attrs),
            categories = categories.toSet(),
            name = name,
            latitude = latitude,
            longitude = longitude,
            description = buildDescription(attrs, geometryKind),
            source = "$SOURCE (layer:$layerId)",
            geometryKind = geometryKind,
            areaRings = areaRings,
        )
    }

    private fun buildDescription(attrs: JSONObject, geometryKind: PoiGeometryKind): String? {
        val parts = mutableListOf<String>()
        if (geometryKind == PoiGeometryKind.AREA) {
            parts += "obszar (marker = centroid pomocniczy)"
        }
        if (isYes(attrs.optString("wiata"))) parts += "wiata"
        if (isYes(attrs.optString("palenisko"))) parts += "palenisko"
        if (isYes(attrs.optString("lawostoly"))) parts += "ławostoły"
        attrs.optString("uwagi").takeIf { it.isNotBlank() && it != "NIE" }?.let { parts += it }
        attrs.optString("link").takeIf { it.isNotBlank() }?.let { parts += it }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun isYes(value: String?): Boolean {
        val v = value?.trim()?.uppercase().orEmpty()
        return v == "T" || v == "TAK" || v == "Y" || v == "YES" || v == "1"
    }

    fun pointFromGeometry(geometry: JSONObject?): Pair<Double, Double>? {
        if (geometry == null) return null
        if (!geometry.has("x") || !geometry.has("y")) return null
        if (geometry.isNull("x") || geometry.isNull("y")) return null
        val lon = geometry.optDouble("x", Double.NaN)
        val lat = geometry.optDouble("y", Double.NaN)
        if (!lon.isFinite() || !lat.isFinite()) return null
        // Keep Poland-ish envelope with margin — rejects swapped/corrupt ArcGIS rows.
        if (lat !in MIN_LAT..MAX_LAT || lon !in MIN_LON..MAX_LON) return null
        return lon to lat
    }

    fun ringsFromPolygon(geometry: JSONObject?): List<List<LatLon>>? {
        if (geometry == null) return null
        val ringsJson = geometry.optJSONArray("rings") ?: return null
        if (ringsJson.length() == 0) return null
        val rings = ArrayList<List<LatLon>>(ringsJson.length())
        for (r in 0 until ringsJson.length()) {
            val ringJson = ringsJson.getJSONArray(r)
            val ring = ArrayList<LatLon>(ringJson.length())
            for (i in 0 until ringJson.length()) {
                val pair = ringJson.getJSONArray(i)
                // ArcGIS JSON: [x=lon, y=lat] in outSR=4326
                ring += LatLon(latitude = pair.getDouble(1), longitude = pair.getDouble(0))
            }
            if (ring.size >= 3) rings += ring
        }
        return rings.takeIf { it.isNotEmpty() }
    }

    fun parseFeatures(payload: String): List<JSONObject> {
        val root = JSONObject(payload)
        if (root.has("error")) {
            val err = root.getJSONObject("error")
            throw IllegalStateException(err.optString("message", "Błąd BDL"))
        }
        val features = root.optJSONArray("features") ?: JSONArray()
        return buildList {
            for (i in 0 until features.length()) {
                add(features.getJSONObject(i))
            }
        }
    }
}
