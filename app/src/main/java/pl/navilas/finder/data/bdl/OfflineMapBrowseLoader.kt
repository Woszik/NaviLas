package pl.navilas.finder.data.bdl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.navilas.finder.domain.BrowseCarFilter
import pl.navilas.finder.domain.NaturalSpringCertainty
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.domain.mergeWith
import pl.navilas.finder.util.GeoUtils
import kotlin.math.cos
import kotlin.math.floor

/**
 * Loads **all** offline rest-site points for MapBrowse mode.
 * Zanocuj status uses the same [ZanocujClassifier] rules as search (IN / NEAR / OUT).
 * Related-object linking is skipped for speed; amenity 17/19 use a coarse cell de-dupe.
 * Natural springs: text on layer 15 + layer-27 `zrodlo=T` within [BrowseCarFilter.AMENITY_LINK_METERS].
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

    private data class SpringPoint(val latitude: Double, val longitude: Double)

    suspend fun loadAll(): Bundle = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        require(store.isReady()) { "BDL offline not ready" }

        var skippedInvalid = 0
        val onInvalid: () -> Unit = { skippedInvalid++ }

        val restFeatures = store.loadLayerFeatures(RestSiteRepository.LAYER_REST)
        val parkingFeatures = store.loadLayerFeatures(RestSiteRepository.LAYER_PARKING)
        val stopFeatures = store.loadLayerFeatures(RestSiteRepository.LAYER_STOP)
        val springPoints = loadSpringPoints(onInvalid)
        val indexed = loadIndexedZanocuj()

        val from15 = restFeatures.mapNotNull { feature ->
            mapSimpleSite(
                feature = feature,
                layerId = RestSiteRepository.LAYER_REST,
                layerName = RestSiteRepository.LAYER_NAME_REST,
                defaultName = "Miejsce wypoczynku",
                indexed = indexed,
                forceParking = false,
                springPoints = springPoints,
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
                    springPoints = springPoints,
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
                    springPoints = springPoints,
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

    private fun loadSpringPoints(onInvalidGeometry: () -> Unit): Map<Long, MutableList<SpringPoint>> {
        val features = store.loadLayerFeatures(RestSiteRepository.LAYER_OTHER)
        val byCell = HashMap<Long, MutableList<SpringPoint>>()
        for (feature in features) {
            val attrs = feature.optJSONObject("attributes") ?: continue
            if (!BdlFeatureExtractor.yes(attrs, "zrodlo")) continue
            val point = BdlMapper.pointFromGeometry(feature.optJSONObject("geometry"))
            if (point == null) {
                if (feature.optJSONObject("geometry") != null) onInvalidGeometry()
                continue
            }
            val lon = point.first
            val lat = point.second
            if (!lat.isFinite() || !lon.isFinite()) continue
            val key = springCellKey(lat, lon)
            byCell.getOrPut(key) { ArrayList() }.add(SpringPoint(lat, lon))
        }
        return byCell
    }

    private fun mapAmenityExtras(
        features: List<JSONObject>,
        layerId: Int,
        layerName: String,
        defaultName: String,
        indexed: List<ZanocujBoundsPolygon>,
        occupied: MutableSet<Long>,
        forceParking: Boolean,
        springPoints: Map<Long, List<SpringPoint>>,
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
            springPoints = springPoints,
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
        springPoints: Map<Long, List<SpringPoint>>,
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
        val name = attrs.optString("nzw_ob").ifBlank { defaultName }
        val uwagi = attrs.optString("uwagi").takeIf { it.isNotBlank() && it != "NIE" }
        val inneAtr = attrs.optString("inne_atr").takeIf { it.isNotBlank() }
        var spring = NaturalSpringClassifier.evaluate(name, uwagi, inneAtr)
        if (hasZrodloNearby(lat, lon, springPoints)) {
            spring = NaturalSpringCertainty.CERTAIN.mergeWith(spring)
        }
        val zone = evaluateZanocuj(lat, lon, indexed)
        return RestSite(
            id = BdlIdentity.resolve(layerId, attrs),
            name = name,
            latitude = lat,
            longitude = lon,
            description = uwagi,
            sourceLayerId = layerId,
            sourceLayerName = layerName,
            features = features,
            relatedObjects = emptyList(),
            zanocujStatus = zone.status,
            distanceToZanocujBoundaryMeters = zone.distanceToBoundaryMeters,
            naturalSpring = spring,
        )
    }

    private fun hasZrodloNearby(
        lat: Double,
        lon: Double,
        springPoints: Map<Long, List<SpringPoint>>,
    ): Boolean {
        if (springPoints.isEmpty()) return false
        val radius = BrowseCarFilter.AMENITY_LINK_METERS
        val marginDeg = (radius / 111_000.0) * 1.2
        val minLat = lat - marginDeg
        val maxLat = lat + marginDeg
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(0.2)
        val lonMargin = marginDeg / cosLat
        val minLon = lon - lonMargin
        val maxLon = lon + lonMargin
        val i0 = springTileIndex(minLat)
        val i1 = springTileIndex(maxLat)
        val j0 = springTileIndex(minLon)
        val j1 = springTileIndex(maxLon)
        for (i in i0..i1) {
            for (j in j0..j1) {
                val bucket = springPoints[packSpring(i, j)] ?: continue
                for (sp in bucket) {
                    if (sp.latitude < minLat || sp.latitude > maxLat) continue
                    if (sp.longitude < minLon || sp.longitude > maxLon) continue
                    val d = GeoUtils.distanceMeters(lat, lon, sp.latitude, sp.longitude)
                    if (d <= radius) return true
                }
            }
        }
        return false
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

    private fun springCellKey(lat: Double, lon: Double): Long =
        packSpring(springTileIndex(lat), springTileIndex(lon))

    private fun springTileIndex(value: Double): Int =
        floor(value / SPRING_CELL_DEG).toInt()

    private fun packSpring(i: Int, j: Int): Long =
        (i.toLong() shl 32) xor (j.toLong() and 0xffffffffL)

    companion object {
        private const val SPRING_CELL_DEG = 0.01
    }
}
