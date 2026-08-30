package pl.navilas.finder.data.bdl

import org.json.JSONObject
import pl.navilas.finder.domain.BdlOverlayPoint
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.util.GeoUtils

/** Maps offline BDL point layers (except rest-site 15/17/19) into browse overlay points. */
object BdlOverlayLoader {
    fun loadAll(
        store: BdlOfflineStore,
        fullAvailable: Boolean,
    ): List<BdlOverlayPoint> {
        val points = ArrayList<BdlOverlayPoint>()
        for (spec in BdlOverlayCatalog.layersToLoad(fullAvailable)) {
            val features = store.loadLayerFeatures(spec.layerId)
            for (feature in features) {
                mapFeature(feature, spec)?.let { points += it }
            }
        }
        return points
    }

    fun mapFeature(feature: JSONObject, spec: BdlOverlayCatalog.LayerSpec): BdlOverlayPoint? {
        val attrs = feature.optJSONObject("attributes") ?: return null
        val point = BdlMapper.pointFromGeometry(feature.optJSONObject("geometry")) ?: return null
        val lon = point.first
        val lat = point.second
        if (!lat.isFinite() || !lon.isFinite()) return null
        val name = attrs.optString("nzw_ob").ifBlank { spec.defaultName }
        val typeCode = attrs.optString("tur_rec_pnt_cd").takeIf { it.isNotBlank() && it != "null" }
        val features = BdlFeatureExtractor.fromAttributes(attrs)
        val uwagi = attrs.optString("uwagi").takeIf { it.isNotBlank() && it != "NIE" && it != "null" }
        val inneAtr = attrs.optString("inne_atr").takeIf { it.isNotBlank() && it != "null" }
        val extraFlags = buildList {
            if (BdlFeatureExtractor.yes(attrs, "zrodlo")) add("Źródło (BDL)")
            if (BdlFeatureExtractor.yes(attrs, "msc_odp")) add("Oznaczone jako miejsce odpoczynku")
        }
        val notes = listOfNotNull(uwagi, inneAtr).joinToString(" · ").ifBlank { null }
        return BdlOverlayPoint(
            id = BdlIdentity.resolve(spec.layerId, attrs),
            name = name,
            latitude = lat,
            longitude = lon,
            layerId = spec.layerId,
            layerName = spec.name,
            group = spec.group,
            typeCode = typeCode,
            features = features,
            notes = notes,
            extraFlags = extraFlags,
        )
    }

    fun inEnvelope(
        points: List<BdlOverlayPoint>,
        envelope: GeoUtils.Envelope,
        groups: Set<pl.navilas.finder.domain.BdlOverlayGroup>,
        centerLat: Double,
        centerLon: Double,
        limit: Int,
    ): List<BdlOverlayPoint> {
        if (groups.isEmpty() || limit <= 0) return emptyList()
        return points.asSequence()
            .filter { it.group in groups }
            .filter {
                it.longitude in envelope.xmin..envelope.xmax &&
                    it.latitude in envelope.ymin..envelope.ymax
            }
            .sortedBy {
                val dLat = it.latitude - centerLat
                val dLon = it.longitude - centerLon
                dLat * dLat + dLon * dLon
            }
            .take(limit)
            .toList()
    }
}
