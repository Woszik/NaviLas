package pl.navilas.finder.data.bdl

import org.json.JSONObject
import pl.navilas.finder.domain.ForestEntryBan
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.util.GeoUtils

/**
 * Live viewport query of BDL forest-entry bans. Not part of the 30-day offline BDL pack.
 */
class ForestEntryBanLoader(
    private val client: BdlArcGisClient = BdlArcGisClient(baseUrl = ForestEntryBanCatalog.BASE_URL),
) {
    fun queryViewport(
        envelope: GeoUtils.Envelope,
        centerLat: Double,
        centerLon: Double,
        limit: Int = DEFAULT_LIMIT,
    ): List<ForestEntryBan> {
        val collected = LinkedHashMap<String, ForestEntryBan>()
        for (layerId in ForestEntryBanCatalog.QUERY_LAYER_IDS) {
            queryLayer(layerId, envelope).forEach { ban ->
                collected.putIfAbsent(ban.id, ban)
            }
        }
        return collected.values
            .asSequence()
            .sortedBy { ban ->
                val c = centroid(ban.rings) ?: return@sortedBy Double.MAX_VALUE
                val dLat = c.latitude - centerLat
                val dLon = c.longitude - centerLon
                dLat * dLat + dLon * dLon
            }
            .take(limit.coerceAtLeast(1))
            .toList()
    }

    private fun queryLayer(layerId: Int, envelope: GeoUtils.Envelope): List<ForestEntryBan> {
        val results = mutableListOf<ForestEntryBan>()
        var offset = 0
        while (true) {
            val body = client.queryEnvelope(
                layerId = layerId,
                envelope = envelope,
                outFields = ForestEntryBanCatalog.OUT_FIELDS,
                returnGeometry = true,
                maxAllowableOffset = SIMPLIFIED_OFFSET,
                resultOffset = offset,
                resultRecordCount = PAGE_SIZE,
            )
            val features = BdlMapper.parseFeatures(body)
            features.mapNotNull { mapFeature(it, layerId) }.forEach { results += it }
            if (features.size < PAGE_SIZE) break
            offset += PAGE_SIZE
            if (offset >= HARD_FETCH_CAP) break
        }
        return results
    }

    fun mapFeature(feature: JSONObject, layerId: Int): ForestEntryBan? {
        val attrs = feature.optJSONObject("attributes") ?: return null
        val rings = BdlMapper.ringsFromPolygon(feature.optJSONObject("geometry")) ?: return null
        if (rings.isEmpty()) return null
        val objectId = attrs.opt("objectid")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            ?: return null
        return ForestEntryBan(
            id = "ban:$layerId:$objectId",
            reason = ForestEntryBanCatalog.reasonFor(layerId, clean(attrs.optString("kod"))),
            forestDistrict = clean(attrs.optString("nazwa_nadl")),
            forestry = clean(attrs.optString("lesnictwo")),
            compartment = clean(attrs.optString("kod_oddzialu")),
            validFrom = formatBanDate(clean(attrs.optString("data"))),
            validUntil = formatBanDate(clean(attrs.optString("data_koncowa"))),
            rings = rings,
        )
    }

    companion object {
        const val DEFAULT_LIMIT = 80
        const val SIMPLIFIED_OFFSET = "0.0003"
        private const val PAGE_SIZE = 200
        private const val HARD_FETCH_CAP = 400

        fun clean(raw: String?): String? {
            val value = raw?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
            return value.trimEnd()
        }

        fun formatBanDate(raw: String?): String? {
            val value = clean(raw) ?: return null
            val date = value.take(10)
            if (date.length == 10 && date[4] == '-' && date[7] == '-') {
                return "${date.substring(8, 10)}.${date.substring(5, 7)}.${date.substring(0, 4)}"
            }
            return value
        }

        fun centroid(rings: List<List<LatLon>>): LatLon? {
            val outer = rings.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
            return LatLon(
                latitude = outer.map { it.latitude }.average(),
                longitude = outer.map { it.longitude }.average(),
            )
        }
    }
}
