package pl.navilas.finder.ui

import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMap.OnCameraMoveStartedListener
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import pl.navilas.finder.data.bdl.ZanocujPolygon
import pl.navilas.finder.domain.BdlOverlayPoint
import pl.navilas.finder.domain.BrowseMapCluster
import pl.navilas.finder.domain.ForestEntryBan
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.NavigationTargetKind
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.RestSiteResult
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.domain.TravelProfile
import pl.navilas.finder.domain.ZanocujStatus
import pl.navilas.finder.map.MapConfig

/**
 * MapLibre rendering + explicit camera commands.
 * [showAllResultsOnMap] / [showPoiOnMap] are never called from a generic UI observer.
 */
class MapController {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var onSiteClick: ((String) -> Unit)? = null
    private var onEmptyMapClick: ((Double, Double) -> Unit)? = null
    private var onEntryBanClick: ((String) -> Unit)? = null
    private var onCorridorVertexClick: ((Int) -> Unit)? = null
    private var onCameraIdle: ((Double, Double, Double, Double, Double, Double) -> Unit)? = null
    private var onGestureCameraMoveStarted: (() -> Unit)? = null
    private var clickListenerRegistered = false
    private var cameraIdleRegistered = false
    private var cameraMoveStartedRegistered = false
    private var applyingFollowCamera = false
    private var browseModeActive = false
    private var lastBrowseRevision: Long = -1L
    private var lastBrowseZanocujCount: Int = -1
    private var lastBrowseZanocujIdsHash: Int = 0
    private var lastOverlayIdsHash: Int = 0
    private var lastEntryBanIdsHash: Int = 0
    private var darkMode: Boolean = false

    /** Diagnostics: last camera command applied (not fitBounds from selection). */
    var lastCameraCommand: String? = null
        private set

    fun attach(mapLibreMap: MapLibreMap, darkMode: Boolean, onReady: () -> Unit) {
        map = mapLibreMap
        this.darkMode = darkMode
        mapLibreMap.uiSettings.isRotateGesturesEnabled = true
        mapLibreMap.uiSettings.isScrollGesturesEnabled = true
        mapLibreMap.uiSettings.isZoomGesturesEnabled = true
        mapLibreMap.setMinZoomPreference(MIN_ZOOM)
        mapLibreMap.setMaxZoomPreference(MAX_ZOOM)
        loadStyle(mapLibreMap, darkMode, onReady)
    }

    /** Reload base style when night-map preference changes without Activity recreate. */
    fun setDarkMode(darkMode: Boolean, onReady: (() -> Unit)? = null) {
        val mapLibreMap = map ?: return
        if (this.darkMode == darkMode && style != null) {
            onReady?.invoke()
            return
        }
        this.darkMode = darkMode
        loadStyle(mapLibreMap, darkMode, onReady ?: {})
    }

    private fun loadStyle(mapLibreMap: MapLibreMap, darkMode: Boolean, onReady: () -> Unit) {
        mapLibreMap.setStyle(Style.Builder().fromUri(MapConfig.styleUrl(darkMode))) { loaded ->
            style = loaded
            lastEntryBanIdsHash = 0
            lastOverlayIdsHash = 0
            lastBrowseZanocujCount = -1
            lastBrowseRevision = -1L
            ensureSourcesAndLayers(loaded)
            ensureClickListener(mapLibreMap)
            ensureCameraIdleListener(mapLibreMap)
            ensureCameraMoveStartedListener(mapLibreMap)
            onReady()
        }
    }

    fun setOnSiteClickListener(listener: ((String) -> Unit)?) {
        onSiteClick = listener
    }

    fun setOnEmptyMapClickListener(listener: ((latitude: Double, longitude: Double) -> Unit)?) {
        onEmptyMapClick = listener
    }

    fun setOnEntryBanClickListener(listener: ((String) -> Unit)?) {
        onEntryBanClick = listener
    }

    fun setOnCorridorVertexClickListener(listener: ((index: Int) -> Unit)?) {
        onCorridorVertexClick = listener
    }

    fun setOnGestureCameraMoveStartedListener(listener: (() -> Unit)?) {
        onGestureCameraMoveStarted = listener
    }

    fun mapWidthPx(): Int = map?.width?.toInt() ?: 0

    fun mapHeightPx(): Int = map?.height?.toInt() ?: 0

    fun currentBearing(): Double = map?.cameraPosition?.bearing ?: 0.0

    fun currentZoom(): Double = map?.cameraPosition?.zoom ?: 12.0

    /** Screen position of a geographic point, or null if map not ready. */
    fun screenLocationOf(latitude: Double, longitude: Double): PointF? {
        val mapLibreMap = map ?: return null
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        return mapLibreMap.projection.toScreenLocation(LatLng(latitude, longitude))
    }

    /**
     * Moves the camera so [latitude]/[longitude] appear at [focalScreenX]/[focalScreenY],
     * keeping [bearing] and [zoom]. Used for live GPS follow while driving.
     */
    fun followUserAtScreenPoint(
        latitude: Double,
        longitude: Double,
        focalScreenX: Float,
        focalScreenY: Float,
        bearing: Double,
        zoom: Double,
    ) {
        val mapLibreMap = map ?: return
        if (!latitude.isFinite() || !longitude.isFinite()) return
        if (mapLibreMap.width <= 0 || mapLibreMap.height <= 0) return
        val z = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        applyingFollowCamera = true
        try {
            val gps = LatLng(latitude, longitude)
            mapLibreMap.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(gps)
                        .zoom(z)
                        .bearing(bearing)
                        .tilt(0.0)
                        .build(),
                ),
            )
            val opposite = PointF(
                mapLibreMap.width - focalScreenX,
                mapLibreMap.height - focalScreenY,
            )
            val newTarget = mapLibreMap.projection.fromScreenLocation(opposite)
            mapLibreMap.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(newTarget)
                        .zoom(z)
                        .bearing(bearing)
                        .tilt(0.0)
                        .build(),
                ),
            )
            lastCameraCommand = "followUser"
        } finally {
            applyingFollowCamera = false
        }
    }

    fun centerOn(latitude: Double, longitude: Double, zoom: Double = 12.0) {
        if (!latitude.isFinite() || !longitude.isFinite()) {
            lastCameraCommand = "centerOn:invalid"
            return
        }
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(latitude, longitude),
                zoom.coerceIn(MIN_ZOOM, MAX_ZOOM),
            ),
        )
        lastCameraCommand = "centerOn"
    }

    fun updateUserLocation(latitude: Double, longitude: Double, @Suppress("UNUSED_PARAMETER") approximate: Boolean) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        val s = style ?: return
        s.getSourceAs<GeoJsonSource>(SOURCE_USER)
            ?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(longitude, latitude)))
    }

    fun updateSearchPin(pin: LatLon?) {
        val s = style ?: return
        val source = s.getSourceAs<GeoJsonSource>(SOURCE_SEARCH_PIN) ?: return
        if (pin == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } else {
            source.setGeoJson(
                Feature.fromGeometry(Point.fromLngLat(pin.longitude, pin.latitude)),
            )
        }
    }

    /** Updates markers / overlays only — no camera change. */
    fun renderResults(
        results: List<RestSiteResult>,
        selected: RestSiteResult?,
        profile: TravelProfile,
        zanocujPolygons: List<ZanocujPolygon> = emptyList(),
        selectedAll: List<RestSiteResult> = emptyList(),
    ) {
        if (browseModeActive) {
            renderBrowseSelection(selectedAll.ifEmpty { listOfNotNull(selected) }, selected, profile)
            return
        }
        val s = style ?: return
        val siteFeatures = results.map { result ->
            Feature.fromGeometry(
                Point.fromLngLat(result.site.longitude, result.site.latitude),
            ).apply {
                addStringProperty("id", result.site.id)
                addStringProperty("name", result.site.name)
                addStringProperty(PROP_IS_CLUSTER, CLUSTER_FLAG_SITE)
            }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_SITES)
            ?.setGeoJson(FeatureCollection.fromFeatures(siteFeatures))

        val areaFeatures = zanocujPolygons.mapNotNull { poly ->
            val rings = poly.rings.map { ring ->
                ring.map { Point.fromLngLat(it.longitude, it.latitude) }
            }
            runCatching { Polygon.fromLngLats(rings) }.getOrNull()?.let { geometry ->
                Feature.fromGeometry(geometry).apply {
                    addStringProperty("id", poly.id)
                }
            }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_ZANOCUJ)
            ?.setGeoJson(FeatureCollection.fromFeatures(areaFeatures))

        renderBrowseSelection(selectedAll.ifEmpty { listOfNotNull(selected) }, selected, profile)
    }

    /**
     * MapBrowse: load all sites once; site visibility later via [applyBrowseFilters].
     * Zanocuj fills come from [setBrowseZanocujPolygons] (viewport subset only).
     */
    fun setBrowseLayer(
        sites: List<RestSite>,
        revision: Long,
    ) {
        val s = style ?: return
        if (revision == lastBrowseRevision && browseModeActive) return
        lastBrowseRevision = revision
        browseModeActive = true
        val features = sites.mapNotNull { site ->
            if (!site.latitude.isFinite() || !site.longitude.isFinite()) return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(site.longitude, site.latitude)).apply {
                addStringProperty("id", site.id)
                addStringProperty("name", site.name)
                addStringProperty(PROP_IS_CLUSTER, CLUSTER_FLAG_SITE)
                addStringProperty(
                    PROP_ZANOCUJ,
                    when (site.zanocujStatus) {
                        ZanocujStatus.IN_ZONE -> "IN"
                        ZanocujStatus.NEAR_ZONE -> "NEAR"
                        ZanocujStatus.OUTSIDE_ZONE -> "OUT"
                    },
                )
                addNumberProperty(
                    PROP_PARKING,
                    if (SiteFeature.PARKING in site.features) 1 else 0,
                )
                addNumberProperty(PROP_FILTER_MATCH, 1)
            }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_SITES)
            ?.setGeoJson(FeatureCollection.fromFeatures(features))
        restoreSiteLayerFilter(s)
    }

    fun setBrowseLayerMatchFlags(sites: List<RestSite>, matchingIds: Set<String>?) {
        val s = style ?: return
        if (!browseModeActive) return
        val visibleSites = if (matchingIds == null) sites else sites.filter { it.id in matchingIds }
        val features = visibleSites.mapNotNull { site ->
            if (!site.latitude.isFinite() || !site.longitude.isFinite()) return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(site.longitude, site.latitude)).apply {
                addStringProperty("id", site.id)
                addStringProperty("name", site.name)
                addStringProperty(PROP_IS_CLUSTER, CLUSTER_FLAG_SITE)
                addStringProperty(
                    PROP_ZANOCUJ,
                    when (site.zanocujStatus) {
                        ZanocujStatus.IN_ZONE -> "IN"
                        ZanocujStatus.NEAR_ZONE -> "NEAR"
                        ZanocujStatus.OUTSIDE_ZONE -> "OUT"
                    },
                )
                addNumberProperty(
                    PROP_PARKING,
                    if (SiteFeature.PARKING in site.features) 1 else 0,
                )
                addNumberProperty(PROP_FILTER_MATCH, 1)
            }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_SITES)
            ?.setGeoJson(FeatureCollection.fromFeatures(features))
        restoreSiteLayerFilter(s)
    }

    fun setBrowseZanocujPolygons(polygons: List<ZanocujPolygon>) {
        if (!browseModeActive) return
        val s = style ?: return
        val idsHash = polygons.fold(0) { acc, p -> acc * 31 + p.id.hashCode() }
        if (polygons.size == lastBrowseZanocujCount && idsHash == lastBrowseZanocujIdsHash) return
        lastBrowseZanocujCount = polygons.size
        lastBrowseZanocujIdsHash = idsHash
        val areaFeatures = polygons.mapNotNull { poly ->
            val rings = poly.rings.map { ring ->
                ring.map { Point.fromLngLat(it.longitude, it.latitude) }
            }
            runCatching { Polygon.fromLngLats(rings) }.getOrNull()?.let { geometry ->
                Feature.fromGeometry(geometry).apply {
                    addStringProperty("id", poly.id)
                }
            }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_ZANOCUJ)
            ?.setGeoJson(FeatureCollection.fromFeatures(areaFeatures))
    }

    fun setOnCameraIdleListener(
        listener: ((west: Double, south: Double, east: Double, north: Double, zoom: Double, bearing: Double) -> Unit)?,
    ) {
        onCameraIdle = listener
    }

    fun exitBrowseMode() {
        if (!browseModeActive) return
        browseModeActive = false
        lastBrowseRevision = -1L
        lastBrowseZanocujCount = -1
        lastBrowseZanocujIdsHash = 0
        style?.let { restoreSiteLayerFilter(it) }
        style?.getSourceAs<GeoJsonSource>(SOURCE_ZANOCUJ)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }

    fun setBrowseOverlayPoints(points: List<BdlOverlayPoint>) {
        val s = style ?: return
        val idsHash = points.fold(0) { acc, p -> acc * 31 + p.id.hashCode() }
        if (idsHash == lastOverlayIdsHash) return
        lastOverlayIdsHash = idsHash
        val features = points.mapNotNull { point ->
            if (!point.latitude.isFinite() || !point.longitude.isFinite()) return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
                addStringProperty("id", point.id)
                addStringProperty("name", point.name)
                addStringProperty(PROP_OVERLAY_GROUP, point.group.name)
            }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_BDL_OVERLAY)
            ?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun setEntryBanPolygons(bans: List<ForestEntryBan>) {
        val s = style ?: return
        val idsHash = bans.fold(0) { acc, ban -> acc * 31 + ban.id.hashCode() }
        if (idsHash == lastEntryBanIdsHash) return
        lastEntryBanIdsHash = idsHash
        val features = bans.mapNotNull { ban ->
            val rings = ban.rings.map { ring ->
                ring.map { Point.fromLngLat(it.longitude, it.latitude) }
            }
            runCatching { Polygon.fromLngLats(rings) }.getOrNull()?.let { geometry ->
                Feature.fromGeometry(geometry).apply {
                    addStringProperty("id", ban.id)
                }
            }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_ENTRY_BAN)
            ?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun setBrowseClusters(clusters: List<BrowseMapCluster>, revision: Long) {
        val s = style ?: return
        if (revision == lastBrowseRevision && browseModeActive) return
        lastBrowseRevision = revision
        browseModeActive = true
        val features = clusters.map { cluster ->
            Feature.fromGeometry(Point.fromLngLat(cluster.longitude, cluster.latitude)).apply {
                addStringProperty(PROP_CLUSTER_COUNT_LABEL, cluster.count.toString())
                addStringProperty(PROP_IS_CLUSTER, CLUSTER_FLAG_CLUSTER)
            }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_SITES)
            ?.setGeoJson(FeatureCollection.fromFeatures(features))
        restoreSiteLayerFilter(s)
    }

    fun applyBrowseFilters(zanocujOnly: Boolean, parkingOnly: Boolean) {
        val s = style ?: return
        val layer = s.getLayerAs<CircleLayer>(LAYER_SITES) ?: return
        if (!browseModeActive) return
        val parts = mutableListOf<Expression>()
        if (zanocujOnly) {
            parts += Expression.eq(Expression.get(PROP_ZANOCUJ), Expression.literal("IN"))
        }
        if (parkingOnly) {
            parts += Expression.eq(Expression.get(PROP_PARKING), Expression.literal(1))
        }
        parts += clusterFlagExpression(isCluster = false)
        val filter = if (parts.size == 1) {
            parts[0]
        } else {
            Expression.all(*parts.toTypedArray())
        }
        layer.setFilter(filter)
    }

    private fun renderBrowseSelection(
        selectedAll: List<RestSiteResult>,
        primary: RestSiteResult?,
        profile: TravelProfile,
    ) {
        val s = style ?: return
        val selectedFeatures = selectedAll.mapNotNull { item ->
            if (!item.site.latitude.isFinite() || !item.site.longitude.isFinite()) return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(item.site.longitude, item.site.latitude)).apply {
                addStringProperty("id", item.site.id)
            }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_SELECTED)
            ?.setGeoJson(FeatureCollection.fromFeatures(selectedFeatures))
        val selected = primary ?: selectedAll.lastOrNull()

        if (profile == TravelProfile.MOTORCYCLE &&
            selected?.navigationTargetKind == NavigationTargetKind.OSM_ROAD
        ) {
            val roadPoint = Point.fromLngLat(
                selected.navigationTarget.longitude,
                selected.navigationTarget.latitude,
            )
            s.getSourceAs<GeoJsonSource>(SOURCE_ROAD_TARGET)
                ?.setGeoJson(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(roadPoint))))
            val helper = LineString.fromLngLats(
                listOf(
                    Point.fromLngLat(selected.site.longitude, selected.site.latitude),
                    roadPoint,
                ),
            )
            s.getSourceAs<GeoJsonSource>(SOURCE_HELPER_LINE)
                ?.setGeoJson(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(helper))))
        } else {
            s.getSourceAs<GeoJsonSource>(SOURCE_ROAD_TARGET)
                ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            s.getSourceAs<GeoJsonSource>(SOURCE_HELPER_LINE)
                ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    /** After a new search / new result set only. */
    fun showAllResultsOnMap(
        results: List<RestSiteResult>,
        user: UserPosition?,
        paddingPx: Int = 96,
    ) {
        if (results.isEmpty()) {
            lastCameraCommand = "showAll:empty"
            return
        }
        if (results.size == 1) {
            showPoiOnMap(results[0], POI_ZOOM_SINGLE)
            lastCameraCommand = "showAll:single->showPoi"
            return
        }
        val builder = LatLngBounds.Builder()
        results.forEach { builder.include(LatLng(it.site.latitude, it.site.longitude)) }
        user?.let { builder.include(LatLng(it.latitude, it.longitude)) }
        runCatching {
            map?.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), paddingPx))
            lastCameraCommand = "showAll:fitBounds"
        }.onFailure {
            lastCameraCommand = "showAll:fitBounds-failed"
        }
    }

    /** Focus one POI — never fitBounds of the whole set. */
    fun showPoiOnMap(result: RestSiteResult, zoom: Double = POI_ZOOM) {
        val lat = result.site.latitude
        val lon = result.site.longitude
        if (!lat.isFinite() || !lon.isFinite()) {
            lastCameraCommand = "showPoi:invalid:${result.site.id}"
            return
        }
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(lat, lon),
                zoom.coerceIn(MIN_ZOOM, MAX_ZOOM),
            ),
        )
        lastCameraCommand = "showPoi:${result.site.id}"
    }

    private fun ensureClickListener(mapLibreMap: MapLibreMap) {
        if (clickListenerRegistered) return
        clickListenerRegistered = true
        mapLibreMap.addOnMapClickListener { latLng ->
            val screen = mapLibreMap.projection.toScreenLocation(latLng)
            val vertexIndex = queryCorridorVertexIndex(PointF(screen.x, screen.y))
            if (vertexIndex != null) {
                onCorridorVertexClick?.invoke(vertexIndex)
                true
            } else if (zoomIntoCluster(PointF(screen.x, screen.y))) {
                true
            } else {
                val hit = querySiteId(PointF(screen.x, screen.y))
                if (hit != null) {
                    onSiteClick?.invoke(hit)
                    true
                } else {
                    val banId = queryEntryBanId(PointF(screen.x, screen.y))
                    if (banId != null) {
                        onEntryBanClick?.invoke(banId)
                        true
                    } else {
                        onEmptyMapClick?.invoke(latLng.latitude, latLng.longitude)
                        true
                    }
                }
            }
        }
    }

    private fun ensureCameraIdleListener(mapLibreMap: MapLibreMap) {
        if (cameraIdleRegistered) return
        cameraIdleRegistered = true
        mapLibreMap.addOnCameraIdleListener {
            val bounds = mapLibreMap.projection.visibleRegion.latLngBounds
            onCameraIdle?.invoke(
                bounds.longitudeWest,
                bounds.latitudeSouth,
                bounds.longitudeEast,
                bounds.latitudeNorth,
                mapLibreMap.cameraPosition.zoom,
                mapLibreMap.cameraPosition.bearing,
            )
        }
    }

    private fun ensureCameraMoveStartedListener(mapLibreMap: MapLibreMap) {
        if (cameraMoveStartedRegistered) return
        cameraMoveStartedRegistered = true
        mapLibreMap.addOnCameraMoveStartedListener { reason ->
            if (applyingFollowCamera) return@addOnCameraMoveStartedListener
            if (reason == OnCameraMoveStartedListener.REASON_API_GESTURE) {
                onGestureCameraMoveStarted?.invoke()
            }
        }
    }

    private fun queryCorridorVertexIndex(screen: PointF): Int? {
        val mapLibreMap = map ?: return null
        val features = mapLibreMap.queryRenderedFeatures(screen, LAYER_CORRIDOR_VERTICES)
        val raw = features.firstOrNull()?.getNumberProperty("index") ?: return null
        return raw.toInt()
    }

    private fun querySiteId(screen: PointF): String? {
        val mapLibreMap = map ?: return null
        // Prefer sites with a real id — LAYER_SELECTED used to omit "id", and
        // firstOrNull()?.getStringProperty("id") then returned null → empty-map path.
        // Pad hit box: single-pixel taps often miss 7px circles.
        val pad = HIT_PAD_PX
        val box = RectF(screen.x - pad, screen.y - pad, screen.x + pad, screen.y + pad)
        val restHits = mapLibreMap.queryRenderedFeatures(box, LAYER_SELECTED, LAYER_SITES)
        restHits.asSequence()
            .mapNotNull { feature ->
                feature.getStringProperty("id")?.takeIf { it.isNotBlank() }
            }
            .firstOrNull()
            ?.let { return it }
        val overlayHits = mapLibreMap.queryRenderedFeatures(box, LAYER_BDL_OVERLAY)
        return overlayHits.asSequence()
            .mapNotNull { feature ->
                feature.getStringProperty("id")?.takeIf { it.isNotBlank() }
            }
            .firstOrNull()
    }

    private fun queryEntryBanId(screen: PointF): String? {
        val mapLibreMap = map ?: return null
        val pad = HIT_PAD_PX
        val box = RectF(screen.x - pad, screen.y - pad, screen.x + pad, screen.y + pad)
        return mapLibreMap.queryRenderedFeatures(box, LAYER_ENTRY_BAN_FILL)
            .asSequence()
            .mapNotNull { feature -> feature.getStringProperty("id")?.takeIf { it.isNotBlank() } }
            .firstOrNull()
    }

    private fun zoomIntoCluster(screen: PointF): Boolean {
        val mapLibreMap = map ?: return false
        val feature = mapLibreMap.queryRenderedFeatures(
            screen,
            LAYER_SITE_CLUSTER_COUNT,
            LAYER_SITE_CLUSTERS,
            LAYER_SITES,
        ).firstOrNull {
            it.getStringProperty(PROP_IS_CLUSTER) == CLUSTER_FLAG_CLUSTER
        } ?: return false
        val point = feature.geometry() as? Point ?: return false
        mapLibreMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(point.latitude(), point.longitude()),
                (mapLibreMap.cameraPosition.zoom + 2.0).coerceAtMost(CLUSTER_MAX_ZOOM + 1.0),
            ),
        )
        lastCameraCommand = "zoomCluster"
        return true
    }

    private fun restoreSiteLayerFilter(style: Style) {
        style.getLayerAs<CircleLayer>(LAYER_SITES)
            ?.setFilter(clusterFlagExpression(isCluster = false))
        style.getLayerAs<CircleLayer>(LAYER_SITE_CLUSTERS)
            ?.setFilter(clusterFlagExpression(isCluster = true))
        style.getLayerAs<SymbolLayer>(LAYER_SITE_CLUSTER_COUNT)
            ?.setFilter(clusterFlagExpression(isCluster = true))
    }

    private fun clusterFlagExpression(isCluster: Boolean): Expression =
        Expression.eq(
            Expression.get(PROP_IS_CLUSTER),
            Expression.literal(if (isCluster) CLUSTER_FLAG_CLUSTER else CLUSTER_FLAG_SITE),
        )

    private fun Style.addOnTop(layer: Layer) {
        val topId = layers.lastOrNull()?.id
        if (topId == null) {
            addLayer(layer)
        } else {
            addLayerAbove(layer, topId)
        }
    }

    private fun ensureSourcesAndLayers(style: Style) {
        if (style.getSource(SOURCE_ZANOCUJ) == null) {
            style.addSource(GeoJsonSource(SOURCE_ZANOCUJ, FeatureCollection.fromFeatures(emptyList())))
            style.addLayer(
                FillLayer(LAYER_ZANOCUJ_FILL, SOURCE_ZANOCUJ).withProperties(
                    fillColor(Color.parseColor("#81C784")),
                    fillOpacity(0.18f),
                ),
            )
            style.addLayer(
                LineLayer(LAYER_ZANOCUJ_LINE, SOURCE_ZANOCUJ).withProperties(
                    lineColor(Color.parseColor("#2E7D32")),
                    lineWidth(1.5f),
                ),
            )
        }
        if (style.getSource(SOURCE_ENTRY_BAN) == null) {
            style.addSource(GeoJsonSource(SOURCE_ENTRY_BAN, FeatureCollection.fromFeatures(emptyList())))
            style.addLayer(
                FillLayer(LAYER_ENTRY_BAN_FILL, SOURCE_ENTRY_BAN).withProperties(
                    fillColor(Color.parseColor("#C62828")),
                    fillOpacity(0.22f),
                ),
            )
            style.addLayer(
                LineLayer(LAYER_ENTRY_BAN_LINE, SOURCE_ENTRY_BAN).withProperties(
                    lineColor(Color.parseColor("#B71C1C")),
                    lineWidth(1.8f),
                ),
            )
        }
        if (style.getSource(SOURCE_USER) == null) {
            style.addSource(GeoJsonSource(SOURCE_USER, FeatureCollection.fromFeatures(emptyList())))
            style.addLayer(
                CircleLayer(LAYER_USER, SOURCE_USER).withProperties(
                    circleRadius(8f),
                    circleColor(Color.parseColor("#1565C0")),
                    circleStrokeColor(Color.WHITE),
                    circleStrokeWidth(2f),
                    circleOpacity(0.95f),
                ),
            )
        }
        if (style.getSource(SOURCE_SEARCH_PIN) == null) {
            style.addSource(GeoJsonSource(SOURCE_SEARCH_PIN, FeatureCollection.fromFeatures(emptyList())))
            style.addLayer(
                CircleLayer(LAYER_SEARCH_PIN, SOURCE_SEARCH_PIN).withProperties(
                    circleRadius(10f),
                    circleColor(Color.parseColor("#E65100")),
                    circleStrokeColor(Color.WHITE),
                    circleStrokeWidth(2.5f),
                    circleOpacity(0.95f),
                ),
            )
        }
        if (style.getSource(SOURCE_SITES) == null) {
            style.addSource(GeoJsonSource(SOURCE_SITES, FeatureCollection.fromFeatures(emptyList())))
            style.addOnTop(
                CircleLayer(LAYER_SITES, SOURCE_SITES)
                    .withFilter(clusterFlagExpression(isCluster = false))
                    .withProperties(
                        circleRadius(7f),
                        circleColor(Color.parseColor("#2E7D32")),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(1.5f),
                    ),
            )
            style.addOnTop(
                CircleLayer(LAYER_SITE_CLUSTERS, SOURCE_SITES)
                    .withFilter(clusterFlagExpression(isCluster = true))
                    .withProperties(
                        circleRadius(18f),
                        circleColor(Color.parseColor("#1B5E20")),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(2f),
                    ),
            )
            style.addOnTop(
                SymbolLayer(LAYER_SITE_CLUSTER_COUNT, SOURCE_SITES)
                    .withFilter(clusterFlagExpression(isCluster = true))
                    .withProperties(
                        textField(Expression.get(PROP_CLUSTER_COUNT_LABEL)),
                        textColor(Color.WHITE),
                        textSize(12f),
                        textIgnorePlacement(true),
                        textAllowOverlap(true),
                    ),
            )
        }
        if (style.getSource(SOURCE_BDL_OVERLAY) == null) {
            style.addSource(GeoJsonSource(SOURCE_BDL_OVERLAY, FeatureCollection.fromFeatures(emptyList())))
            style.addLayer(
                CircleLayer(LAYER_BDL_OVERLAY, SOURCE_BDL_OVERLAY).withProperties(
                    circleRadius(6.5f),
                    circleColor(
                        Expression.match(
                            Expression.get(PROP_OVERLAY_GROUP),
                            Expression.literal("VIEW"),
                            Expression.color(Color.parseColor("#1565C0")),
                            Expression.literal("OTHER"),
                            Expression.color(Color.parseColor("#6D4C41")),
                            Expression.literal("WATER"),
                            Expression.color(Color.parseColor("#00838F")),
                            Expression.literal("PLAY"),
                            Expression.color(Color.parseColor("#EF6C00")),
                            Expression.literal("LODGING"),
                            Expression.color(Color.parseColor("#880E4F")),
                            Expression.color(Color.parseColor("#757575")),
                        ),
                    ),
                    circleStrokeColor(Color.WHITE),
                    circleStrokeWidth(1.5f),
                    circleOpacity(0.95f),
                ),
            )
        }
        if (style.getSource(SOURCE_SELECTED) == null) {
            style.addSource(GeoJsonSource(SOURCE_SELECTED, FeatureCollection.fromFeatures(emptyList())))
            style.addLayer(
                CircleLayer(LAYER_SELECTED, SOURCE_SELECTED).withProperties(
                    circleRadius(11f),
                    circleColor(Color.parseColor("#F9A825")),
                    circleStrokeColor(Color.WHITE),
                    circleStrokeWidth(2f),
                ),
            )
        }
        if (style.getSource(SOURCE_ROAD_TARGET) == null) {
            style.addSource(GeoJsonSource(SOURCE_ROAD_TARGET, FeatureCollection.fromFeatures(emptyList())))
            style.addLayer(
                CircleLayer(LAYER_ROAD_TARGET, SOURCE_ROAD_TARGET).withProperties(
                    circleRadius(8f),
                    circleColor(Color.parseColor("#6A1B9A")),
                    circleStrokeColor(Color.WHITE),
                    circleStrokeWidth(2f),
                ),
            )
        }
        if (style.getSource(SOURCE_HELPER_LINE) == null) {
            style.addSource(GeoJsonSource(SOURCE_HELPER_LINE, FeatureCollection.fromFeatures(emptyList())))
            style.addLayer(
                LineLayer(LAYER_HELPER_LINE, SOURCE_HELPER_LINE).withProperties(
                    lineColor(Color.parseColor("#6A1B9A")),
                    lineWidth(2f),
                    lineDasharray(arrayOf(1.5f, 1.5f)),
                ),
            )
        }
        if (style.getSource(SOURCE_CORRIDOR) == null) {
            style.addSource(GeoJsonSource(SOURCE_CORRIDOR, FeatureCollection.fromFeatures(emptyList())))
            style.addLayer(
                LineLayer(LAYER_CORRIDOR, SOURCE_CORRIDOR).withProperties(
                    lineColor(Color.parseColor("#E65100")),
                    lineWidth(3.5f),
                ),
            )
        }
        if (style.getSource(SOURCE_CORRIDOR_VERTICES) == null) {
            style.addSource(GeoJsonSource(SOURCE_CORRIDOR_VERTICES, FeatureCollection.fromFeatures(emptyList())))
            style.addLayer(
                CircleLayer(LAYER_CORRIDOR_VERTICES, SOURCE_CORRIDOR_VERTICES).withProperties(
                    circleRadius(10f),
                    circleColor(Color.parseColor("#BF360C")),
                    circleStrokeColor(Color.WHITE),
                    circleStrokeWidth(2f),
                ),
            )
        }
    }

    fun updateCorridorLine(points: List<LatLon>) {
        val s = style ?: return
        val lineSource = s.getSourceAs<GeoJsonSource>(SOURCE_CORRIDOR) ?: return
        val vertexSource = s.getSourceAs<GeoJsonSource>(SOURCE_CORRIDOR_VERTICES) ?: return
        if (points.isEmpty()) {
            lineSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            vertexSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        vertexSource.setGeoJson(
            FeatureCollection.fromFeatures(
                points.mapIndexed { index, p ->
                    Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude)).apply {
                        addNumberProperty("index", index)
                    }
                },
            ),
        )
        if (points.size >= 2) {
            lineSource.setGeoJson(
                Feature.fromGeometry(
                    LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) }),
                ),
            )
        } else {
            lineSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    companion object {
        const val MIN_ZOOM = 5.0
        const val MAX_ZOOM = 18.0
        const val POI_ZOOM = 15.0
        const val POI_ZOOM_SINGLE = 14.5
        /** Default GPS puck: ~38% of map height from the bottom (driving look-ahead). */
        const val DEFAULT_FOLLOW_FOCAL_Y_FROM_TOP = 0.62f
        private const val HIT_PAD_PX = 28f
        private const val CLUSTER_MAX_ZOOM = 9.0

        const val SOURCE_USER = "navilas-user"
        const val SOURCE_SEARCH_PIN = "navilas-search-pin"
        const val SOURCE_SITES = "navilas-sites"
        const val SOURCE_BDL_OVERLAY = "navilas-bdl-overlay"
        const val SOURCE_SELECTED = "navilas-selected"
        const val SOURCE_ZANOCUJ = "navilas-zanocuj"
        const val SOURCE_ENTRY_BAN = "navilas-entry-ban"
        const val SOURCE_ROAD_TARGET = "navilas-road-target"
        const val SOURCE_HELPER_LINE = "navilas-helper-line"
        const val SOURCE_CORRIDOR = "navilas-corridor"
        const val SOURCE_CORRIDOR_VERTICES = "navilas-corridor-vertices"
        const val LAYER_USER = "navilas-user-layer"
        const val LAYER_SEARCH_PIN = "navilas-search-pin-layer"
        const val LAYER_SITES = "navilas-sites-layer"
        const val LAYER_SITE_CLUSTERS = "navilas-site-clusters"
        const val LAYER_SITE_CLUSTER_COUNT = "navilas-site-cluster-count"
        const val LAYER_BDL_OVERLAY = "navilas-bdl-overlay-layer"
        const val LAYER_SELECTED = "navilas-selected-layer"
        const val LAYER_ZANOCUJ_FILL = "navilas-zanocuj-fill"
        const val LAYER_ZANOCUJ_LINE = "navilas-zanocuj-line"
        const val LAYER_ENTRY_BAN_FILL = "navilas-entry-ban-fill"
        const val LAYER_ENTRY_BAN_LINE = "navilas-entry-ban-line"
        const val LAYER_ROAD_TARGET = "navilas-road-target-layer"
        const val LAYER_HELPER_LINE = "navilas-helper-line-layer"
        const val LAYER_CORRIDOR = "navilas-corridor-layer"
        const val LAYER_CORRIDOR_VERTICES = "navilas-corridor-vertices-layer"
        const val PROP_ZANOCUJ = "zanocuj"
        const val PROP_PARKING = "parking"
        const val PROP_FILTER_MATCH = "filter_match"
        const val PROP_OVERLAY_GROUP = "overlay_group"
        private const val PROP_CLUSTER_COUNT_LABEL = "count"
        private const val PROP_IS_CLUSTER = "is_cluster"
        private const val CLUSTER_FLAG_SITE = "0"
        private const val CLUSTER_FLAG_CLUSTER = "1"
    }
}
