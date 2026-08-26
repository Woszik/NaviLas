package pl.navilas.finder.data.bdl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.domain.SiteFeature

/**
 * Loads **all** offline rest-site points for MapBrowse mode.
 * Zanocuj status uses the same [ZanocujClassifier] rules as search (IN / NEAR / OUT).
 * Related-object linking is skipped for speed; amenity 17/19 use a coarse cell de-dupe.
 */
class OfflineMapBrowseLoader(
    private val store: BdlOfflineStore,
    private val config: SearchConfig = SearchConfig.DEFAULT,
) {
    data class Bundle(
        val sites: List<RestSite>,
        /**
         * Full Zanocuj index for viewport overlay queries.
         * Do **not** push this entire list into MapLibre — only a bbox subset.
         */
        val zanocujIndex: List<ZanocujBoundsPolygon>,
        val loadMs: Long,
        /** Point features skipped due to missing/invalid coordinates. */
        val skippedInvalidGeometry: Int = 0,
    )

    suspend fun loadAll(): Bundle = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        require(store.isReady()) { "BDL offline not ready" }

        var skippedInvalid = 0
        val onInvalid: () -> Unit = { skippedInvalid++ }

        val restFeatures = store.loadLayerFeatures(RestSiteRepository.LAYER_REST)
        val parkingFeatures = store.loadLayerFeatures(RestSiteRepository.LAYER_PARKING)
        val stopFeatures = store.loadLayerFeatures(RestSiteRepository.LAYER_STOP)
        val indexed = loadIndexedZanocuj()

        val from15 = restFeatures.mapNotNull { feature ->
            mapSimpleSite(
                feature = feature,
                layerId = RestSiteRepository.LAYER_REST,
                layerName = RestSiteRepository.LAYER_NAME_REST,
                defaultName = "Miejsce wypoczynku",
                indexed = indexed,
                forceParking = false,
                onInvalidGeometry = onInvalid,
            )
        }

        val occupied = HashSet<Long>(from15.size * 2)
        from15.forEach { occupied += cellKey(it.latitude, it.longitude) }

        val amenityExtras = buildList {
            addAll(
                mapAmenityExtras(
                    parkingFeatures,
                    RestSiteRepository.LAYER_PARKING,
                    RestSiteRepository.LAYER_NAME_PARKING,
                    "Parking / postój",
                    indexed,
                    occupied,
                    forceParking = true,
                    onInvalidGeometry = onInvalid,
                ),
            )
            addAll(
                mapAmenityExtras(
                    stopFeatures,
                    RestSiteRepository.LAYER_STOP,
                    RestSiteRepository.LAYER_NAME_STOP,
                    "Postój",
                    indexed,
                    occupied,
                    forceParking = false,
                    onInvalidGeometry = onInvalid,
                ),
            )
        }

        Bundle(
            sites = from15 + amenityExtras,
            zanocujIndex = indexed,
            loadMs = System.currentTimeMillis() - started,
            skippedInvalidGeometry = skippedInvalid,
        )
    }

    private fun mapAmenityExtras(
        features: List<JSONObject>,
        layerId: Int,
        layerName: String,
        defaultName: String,
        indexed: List<ZanocujBoundsPolygon>,
        occupied: MutableSet<Long>,
        forceParking: Boolean,
        onInvalidGeometry: () -> Unit,
    ): List<RestSite> = features.mapNotNull { feature ->
        val attrs = feature.optJSONObject("attributes") ?: return@mapNotNull null
        if (!BdlAmenityStopRules.qualifiesAsStandalone(attrs)) return@mapNotNull null
        val point = BdlMapper.pointFromGeometry(feature.optJSONObject("geometry"))
        if (point == null) {
            if (feature.optJSONObject("geometry") != null) onInvalidGeometry()
            return@mapNotNull null
        }
        val lon = point.first
        val lat = point.second
        val key = cellKey(lat, lon)
        if (key in occupied) return@mapNotNull null
        occupied += key
        mapSimpleSite(
            feature = feature,
            layerId = layerId,
            layerName = layerName,
            defaultName = defaultName,
            indexed = indexed,
            forceParking = forceParking,
            onInvalidGeometry = onInvalidGeometry,
        )
    }

    private fun mapSimpleSite(
        feature: JSONObject,
        layerId: Int,
        layerName: String,
        defaultName: String,
        indexed: List<ZanocujBoundsPolygon>,
        forceParking: Boolean,
        onInvalidGeometry: () -> Unit = {},
    ): RestSite? {
        val attrs = feature.optJSONObject("attributes") ?: return null
        val point = BdlMapper.pointFromGeometry(feature.optJSONObject("geometry"))
        if (point == null) {
            if (feature.optJSONObject("geometry") != null) onInvalidGeometry()
            return null
        }
        val lon = point.first
        val lat = point.second
        val features = BdlFeatureExtractor.fromAttributes(attrs).toMutableSet()
        if (forceParking || layerId == RestSiteRepository.LAYER_PARKING) {
            features += SiteFeature.PARKING
        }
        val zone = evaluateZanocuj(lat, lon, indexed)
        return RestSite(
            id = BdlIdentity.resolve(layerId, attrs),
            name = attrs.optString("nzw_ob").ifBlank { defaultName },
            latitude = lat,
            longitude = lon,
            description = attrs.optString("uwagi").takeIf { it.isNotBlank() && it != "NIE" },
            sourceLayerId = layerId,
            sourceLayerName = layerName,
            features = features,
            relatedObjects = emptyList(),
            zanocujStatus = zone.status,
            distanceToZanocujBoundaryMeters = zone.distanceToBoundaryMeters,
        )
    }

    private fun evaluateZanocuj(
        lat: Double,
        lon: Double,
        indexed: List<ZanocujBoundsPolygon>,
    ): ZanocujEvaluation {
        if (indexed.isEmpty()) {
            return ZanocujEvaluation(pl.navilas.finder.domain.ZanocujStatus.OUTSIDE_ZONE, null)
        }
        var best = ZanocujEvaluation(pl.navilas.finder.domain.ZanocujStatus.OUTSIDE_ZONE, null)
        for (item in indexed) {
            if (lat < item.minLat || lat > item.maxLat || lon < item.minLon || lon > item.maxLon) {
                continue
            }
            val candidate = ZanocujClassifier.evaluateAgainstPolygon(lat, lon, item.polygon.rings, config)
            best = ZanocujClassifier.better(best, candidate)
            if (best.status == pl.navilas.finder.domain.ZanocujStatus.IN_ZONE) return best
        }
        return best
    }

    private fun loadIndexedZanocuj(): List<ZanocujBoundsPolygon> {
        val nearMarginDeg = (config.zanocujNearZoneMeters / 111_000.0) * 1.15
        val raw = store.loadLayerFeatures(RestSiteRepository.LAYER_ZANOCUJ)
        return raw.mapNotNull { feature ->
            val attrs = feature.optJSONObject("attributes") ?: return@mapNotNull null
            val rings = BdlMapper.ringsFromPolygon(feature.optJSONObject("geometry")) ?: return@mapNotNull null
            if (rings.isEmpty()) return@mapNotNull null
            val id = BdlIdentity.resolve(RestSiteRepository.LAYER_ZANOCUJ, attrs)
            val poly = ZanocujPolygon(
                id = id,
                name = attrs.optString("nzw_ob").takeIf { it.isNotBlank() },
                rings = rings,
            )
            ZanocujBoundsPolygon.from(poly, nearMarginDeg)
        }
    }

    /** ~110 m cells — enough to approximate restLinkRadius without O(n²). */
    private fun cellKey(lat: Double, lon: Double): Long {
        val y = (lat * 1000.0).toInt()
        val x = (lon * 1000.0).toInt()
        return (y.toLong() shl 32) xor (x.toLong() and 0xffffffffL)
    }
}
