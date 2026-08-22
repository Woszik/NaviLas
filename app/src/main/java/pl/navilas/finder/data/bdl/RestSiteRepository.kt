package pl.navilas.finder.data.bdl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.navilas.finder.data.cache.BdlSearchCacheKey
import pl.navilas.finder.data.cache.BdlSearchSessionCache
import pl.navilas.finder.domain.RelatedBdlObject
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.util.GeoUtils
import java.io.IOException

data class RestSearchBundle(
    val sites: List<RestSite>,
    val zanocujPolygons: List<ZanocujPolygon>,
)

/**
 * Builds [RestSite] results from BDL layer 15, plus amenity stops/parkings (17/19)
 * with wiata/palenisko/lawostoly when not duplicated by a nearby layer-15 site.
 * Enriched with nearby BDL objects and Zanocuj status. OSM is not used here.
 */
class RestSiteRepository(
    private val arcGisClient: BdlArcGisClient = BdlArcGisClient(),
    private val offlineStore: BdlOfflineStore? = null,
    private val sessionCache: BdlSearchSessionCache? = null,
    private val config: SearchConfig = SearchConfig.DEFAULT,
) {
    suspend fun findRestSites(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = config.searchRadiusKm,
    ): RestSearchBundle = findRestSitesWithMeta(latitude, longitude, radiusKm).bundle

    suspend fun findRestSitesWithMeta(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = config.searchRadiusKm,
    ): RestSearchOutcome = withContext(Dispatchers.IO) {
        val cacheKey = sessionCacheKey(latitude, longitude, radiusKm)
        sessionCache?.get(cacheKey)?.let {
            pipelineLog("session cache hit key=$cacheKey")
            return@withContext RestSearchOutcome(bundle = it, fromSessionCache = true)
        }
        val bundle = fetchRestSites(latitude, longitude, radiusKm)
        sessionCache?.put(cacheKey, bundle)
        RestSearchOutcome(bundle = bundle, fromSessionCache = false)
    }

    private suspend fun fetchRestSites(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): RestSearchBundle {
        val envelope = GeoUtils.envelopeAround(latitude, longitude, radiusKm)
        pipelineLog(
            "GPS = $latitude, $longitude\n" +
                "radius = $radiusKm\n" +
                "envelope = minLon=${envelope.xmin} minLat=${envelope.ymin} " +
                "maxLon=${envelope.xmax} maxLat=${envelope.ymax}",
        )
        require(envelope.xmin < envelope.xmax && envelope.ymin < envelope.ymax) {
            "Invalid envelope: $envelope"
        }
        if (offlineStore?.isReady() == true) {
            pipelineLog("offline = true")
            return findRestSitesFromOffline(latitude, longitude, radiusKm)
        }
        return findRestSitesFromNetwork(latitude, longitude, radiusKm, envelope)
    }

    private fun sessionCacheKey(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): BdlSearchCacheKey {
        val offlineVersion = if (offlineStore?.isReady() == true) {
            offlineStore.downloadedAt() ?: 0L
        } else {
            0L
        }
        return BdlSearchSessionCache.key(latitude, longitude, radiusKm, offlineVersion)
    }

    private suspend fun findRestSitesFromOffline(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): RestSearchBundle {
        val store = offlineStore ?: error("offlineStore required")
        val restFeatures = store.filterPointFeatures(LAYER_REST, latitude, longitude, radiusKm)
        val parkingFeatures = store.filterPointFeatures(LAYER_PARKING, latitude, longitude, radiusKm)
        val stopFeatures = store.filterPointFeatures(LAYER_STOP, latitude, longitude, radiusKm)
        val viewpointFeatures = store.filterPointFeatures(LAYER_VIEWPOINT, latitude, longitude, radiusKm)
        val otherFeatures = store.filterPointFeatures(LAYER_OTHER, latitude, longitude, radiusKm)
        val polygons = store.filterZanocujPolygons(
            userLat = latitude,
            userLon = longitude,
            radiusKm = radiusKm,
            nearZoneMarginKm = config.zanocujNearZoneMeters / 1000.0,
        )
        pipelineLog(
            "offline features layer15=${restFeatures.size} " +
                "layer17=${parkingFeatures.size} layer19=${stopFeatures.size}",
        )
        return buildRestSearchBundle(
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            restFeatures = restFeatures,
            parkingFeatures = parkingFeatures,
            stopFeatures = stopFeatures,
            viewpointFeatures = viewpointFeatures,
            otherFeatures = otherFeatures,
            polygons = polygons,
        )
    }

    private suspend fun findRestSitesFromNetwork(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        envelope: GeoUtils.Envelope,
    ): RestSearchBundle = coroutineScope {
        val rests = async { queryPoints(LAYER_REST, envelope, OUT_FIELDS_REST, logLayer15 = true) }
        val parkings = async { queryPoints(LAYER_PARKING, envelope, OUT_FIELDS_VEHICLE) }
        val stops = async { queryPoints(LAYER_STOP, envelope, OUT_FIELDS_VEHICLE) }
        val viewpoints = async { queryPoints(LAYER_VIEWPOINT, envelope, OUT_FIELDS_SATELLITE) }
        val other = async { queryPoints(LAYER_OTHER, envelope, OUT_FIELDS_SATELLITE) }
        val zones = async { queryZanocujPolygons(envelope) }
        val restFeatures = rests.await()
        val parkingFeatures = parkings.await()
        val stopFeatures = stops.await()
        pipelineLog(
            "BDL features layer15=${restFeatures.size} " +
                "layer17=${parkingFeatures.size} layer19=${stopFeatures.size}",
        )
        buildRestSearchBundle(
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            restFeatures = restFeatures,
            parkingFeatures = parkingFeatures,
            stopFeatures = stopFeatures,
            viewpointFeatures = viewpoints.await(),
            otherFeatures = other.await(),
            polygons = zones.await(),
        )
    }

    private fun buildRestSearchBundle(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        restFeatures: List<JSONObject>,
        parkingFeatures: List<JSONObject>,
        stopFeatures: List<JSONObject>,
        viewpointFeatures: List<JSONObject>,
        otherFeatures: List<JSONObject>,
        polygons: List<ZanocujPolygon>,
    ): RestSearchBundle {
        val satellites = buildList {
            addAll(toSatellites(parkingFeatures, LAYER_PARKING, LAYER_NAME_PARKING))
            addAll(toSatellites(stopFeatures, LAYER_STOP, LAYER_NAME_STOP))
            addAll(toSatellites(viewpointFeatures, LAYER_VIEWPOINT, LAYER_NAME_VIEWPOINT))
            addAll(toSatellites(otherFeatures, LAYER_OTHER, LAYER_NAME_OTHER))
        }
        val fromLayer15 = restFeatures.mapNotNull { feature ->
            mapPrimarySite(
                feature = feature,
                layerId = LAYER_REST,
                layerName = LAYER_NAME_REST,
                defaultName = "Miejsce wypoczynku",
                satellites = satellites,
                polygons = polygons,
                userLat = latitude,
                userLon = longitude,
                radiusKm = radiusKm,
            )
        }
        val amenityExtras = buildList {
            addAll(
                mapAmenityVehiclePrimaries(
                    features = parkingFeatures,
                    layerId = LAYER_PARKING,
                    layerName = LAYER_NAME_PARKING,
                    defaultName = "Parking leśny",
                    existingPrimaries = fromLayer15,
                    satellites = satellites,
                    polygons = polygons,
                    userLat = latitude,
                    userLon = longitude,
                    radiusKm = radiusKm,
                ),
            )
            addAll(
                mapAmenityVehiclePrimaries(
                    features = stopFeatures,
                    layerId = LAYER_STOP,
                    layerName = LAYER_NAME_STOP,
                    defaultName = "Miejsce postoju",
                    existingPrimaries = fromLayer15,
                    satellites = satellites,
                    polygons = polygons,
                    userLat = latitude,
                    userLon = longitude,
                    radiusKm = radiusKm,
                ),
            )
        }
        val sites = (fromLayer15 + amenityExtras).sortedBy {
            GeoUtils.distanceMeters(latitude, longitude, it.latitude, it.longitude)
        }
        pipelineLog(
            "primary15 = ${fromLayer15.size}\n" +
                "amenity17_19 = ${amenityExtras.size}\n" +
                "enriched = ${sites.size}\n" +
                "UI results = ${sites.size}",
        )
        return RestSearchBundle(sites = sites, zanocujPolygons = polygons)
    }

    private fun mapAmenityVehiclePrimaries(
        features: List<JSONObject>,
        layerId: Int,
        layerName: String,
        defaultName: String,
        existingPrimaries: List<RestSite>,
        satellites: List<SatellitePoint>,
        polygons: List<ZanocujPolygon>,
        userLat: Double,
        userLon: Double,
        radiusKm: Double,
    ): List<RestSite> = features.mapNotNull { feature ->
        val attrs = feature.optJSONObject("attributes") ?: return@mapNotNull null
        if (!BdlAmenityStopRules.qualifiesAsStandalone(attrs)) return@mapNotNull null
        val point = BdlMapper.pointFromGeometry(feature.optJSONObject("geometry")) ?: return@mapNotNull null
        val lon = point.first
        val lat = point.second
        if (GeoUtils.distanceKm(userLat, userLon, lat, lon) > radiusKm) return@mapNotNull null
        val coveredByRest = existingPrimaries.any { primary ->
            GeoUtils.distanceMeters(lat, lon, primary.latitude, primary.longitude) <=
                config.restLinkRadiusMeters
        }
        if (coveredByRest) return@mapNotNull null
        mapPrimarySite(
            feature = feature,
            layerId = layerId,
            layerName = layerName,
            defaultName = defaultName,
            satellites = satellites,
            polygons = polygons,
            userLat = userLat,
            userLon = userLon,
            radiusKm = radiusKm,
        )
    }

    private fun mapPrimarySite(
        feature: JSONObject,
        layerId: Int,
        layerName: String,
        defaultName: String,
        satellites: List<SatellitePoint>,
        polygons: List<ZanocujPolygon>,
        userLat: Double,
        userLon: Double,
        radiusKm: Double,
    ): RestSite? {
        val attrs = feature.optJSONObject("attributes") ?: return null
        val point = BdlMapper.pointFromGeometry(feature.optJSONObject("geometry")) ?: return null
        val lon = point.first
        val lat = point.second
        if (GeoUtils.distanceKm(userLat, userLon, lat, lon) > radiusKm) return null

        val id = BdlIdentity.resolve(layerId, attrs)
        val related = SpatialLinker.linkNearby(lat, lon, satellites, config)
            .filter { it.id != id }
        val features = BdlFeatureExtractor.fromAttributes(attrs).toMutableSet()
        if (layerId == LAYER_PARKING || related.any { it.layerId == LAYER_PARKING }) {
            features += SiteFeature.PARKING
        }
        val zone = ZanocujClassifier.evaluate(lat, lon, polygons, config)
        val name = attrs.optString("nzw_ob").ifBlank { defaultName }
        return RestSite(
            id = id,
            name = name,
            latitude = lat,
            longitude = lon,
            description = buildDescription(attrs, related),
            sourceLayerId = layerId,
            sourceLayerName = layerName,
            features = features,
            relatedObjects = related,
            zanocujStatus = zone.status,
            distanceToZanocujBoundaryMeters = zone.distanceToBoundaryMeters,
        )
    }

    private fun buildDescription(attrs: JSONObject, related: List<RelatedBdlObject>): String? {
        val parts = mutableListOf<String>()
        attrs.optString("uwagi").takeIf { it.isNotBlank() && it != "NIE" }?.let { parts += it }
        attrs.optString("link").takeIf { it.isNotBlank() }?.let { parts += it }
        if (related.isNotEmpty()) {
            parts += "w pobliżu: " + related.joinToString { it.name }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun toSatellites(
        features: List<JSONObject>,
        layerId: Int,
        layerName: String,
    ): List<SatellitePoint> = features.mapNotNull { feature ->
        val attrs = feature.optJSONObject("attributes") ?: return@mapNotNull null
        val point = BdlMapper.pointFromGeometry(feature.optJSONObject("geometry")) ?: return@mapNotNull null
        SatellitePoint(
            id = BdlIdentity.resolve(layerId, attrs),
            layerId = layerId,
            layerName = layerName,
            name = attrs.optString("nzw_ob").ifBlank { layerName },
            latitude = point.second,
            longitude = point.first,
            typeCode = attrs.optString("tur_rec_pnt_cd").takeIf { it.isNotBlank() },
        )
    }

    private fun queryPoints(
        layerId: Int,
        envelope: GeoUtils.Envelope,
        outFields: String,
        logLayer15: Boolean = false,
    ): List<JSONObject> {
        val results = mutableListOf<JSONObject>()
        var offset = 0
        while (true) {
            val body = arcGisClient.queryEnvelope(
                layerId = layerId,
                envelope = envelope,
                outFields = outFields,
                returnGeometry = true,
                maxAllowableOffset = "0",
                resultOffset = offset,
                resultRecordCount = PAGE_SIZE,
            )
            if (logLayer15 && offset == 0) {
                pipelineLog("BDL layer $layerId page bytes = ${body.length}")
            }
            val features = BdlMapper.parseFeatures(body)
            results += features
            if (features.size < PAGE_SIZE) break
            offset += PAGE_SIZE
            if (offset >= HARD_FETCH_CAP) break
        }
        return results
    }

    private fun queryZanocujPolygons(envelope: GeoUtils.Envelope): List<ZanocujPolygon> {
        val results = mutableListOf<ZanocujPolygon>()
        var offset = 0
        while (true) {
            val body = arcGisClient.queryEnvelope(
                layerId = LAYER_ZANOCUJ,
                envelope = envelope,
                outFields = OUT_FIELDS_ZANOCUJ,
                returnGeometry = true,
                maxAllowableOffset = "0",
                resultOffset = offset,
                resultRecordCount = PAGE_SIZE,
            )
            val features = BdlMapper.parseFeatures(body)
            features.forEach { feature ->
                val attrs = feature.optJSONObject("attributes") ?: return@forEach
                val rings = BdlMapper.ringsFromPolygon(feature.optJSONObject("geometry")) ?: return@forEach
                results += ZanocujPolygon(
                    id = BdlIdentity.resolve(LAYER_ZANOCUJ, attrs),
                    name = attrs.optString("nzw_ob").takeIf { it.isNotBlank() },
                    rings = rings,
                )
            }
            if (features.size < PAGE_SIZE) break
            offset += PAGE_SIZE
            if (offset >= HARD_FETCH_CAP) break
        }
        return results
    }

    companion object {
        fun pipelineLog(message: String) {
            println("$PIPELINE_TAG: $message")
        }
        const val LAYER_ZANOCUJ = 0
        const val LAYER_REST = 15
        const val LAYER_PARKING = 17
        const val LAYER_STOP = 19
        const val LAYER_VIEWPOINT = 25
        const val LAYER_OTHER = 27

        const val LAYER_NAME_REST = "Miejsca wypoczynku - ob. punktowe"
        const val LAYER_NAME_PARKING = "Parkingi leśne - ob. punktowe"
        const val LAYER_NAME_STOP = "Miejsca postoju pojazdów - ob. punktowe"
        const val LAYER_NAME_VIEWPOINT = "Punkty widokowe - ob. punktowe"
        const val LAYER_NAME_OTHER = "Inne punktowe nienoclegowe obiekty rekreacyjno-wypoczynkowe i edukacyjne"

        private const val PIPELINE_TAG = "NaviLasPipeline"
        private const val PAGE_SIZE = 500
        private const val HARD_FETCH_CAP = 1000
        private const val OUT_FIELDS_REST =
            "objectid,foreign_key,tur_rec_pnt_id,inv_nr,nzw_ob,uwagi,link,wiata,palenisko,parking,woda_pitna,lawostoly,kuchenka,toalety_tm,toalety_st,os_toalety,n_toalety,lad_rower,serw_rower,kapielisko,marina"
        const val OUT_FIELDS_VEHICLE =
            "objectid,foreign_key,tur_rec_pnt_id,inv_nr,nzw_ob,tur_rec_pnt_cd,uwagi,link,wiata,palenisko,parking,lawostoly"
        const val OUT_FIELDS_SATELLITE =
            "objectid,foreign_key,tur_rec_pnt_id,inv_nr,nzw_ob,tur_rec_pnt_cd,wiata,palenisko"
        private const val OUT_FIELDS_ZANOCUJ =
            "objectid,foreign_key,tur_sleep_poly_id,inv_nr,nzw_ob"
    }
}
