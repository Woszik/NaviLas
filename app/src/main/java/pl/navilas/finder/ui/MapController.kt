package pl.navilas.finder.ui

import android.graphics.Color
import android.graphics.PointF
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
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
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import pl.navilas.finder.data.bdl.ZanocujPolygon
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.NavigationTargetKind
import pl.navilas.finder.domain.RestSiteResult
import pl.navilas.finder.domain.TravelProfile
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
    private var clickListenerRegistered = false

    /** Diagnostics: last camera command applied (not fitBounds from selection). */
    var lastCameraCommand: String? = null
        private set

    fun attach(mapLibreMap: MapLibreMap, onReady: () -> Unit) {
        map = mapLibreMap
        mapLibreMap.uiSettings.isRotateGesturesEnabled = true
        mapLibreMap.uiSettings.isScrollGesturesEnabled = true
        mapLibreMap.uiSettings.isZoomGesturesEnabled = true
        mapLibreMap.setMinZoomPreference(MIN_ZOOM)
        mapLibreMap.setMaxZoomPreference(MAX_ZOOM)
        mapLibreMap.setStyle(Style.Builder().fromUri(MapConfig.STYLE_URL)) { loaded ->
            style = loaded
            ensureSourcesAndLayers(loaded)
            ensureClickListener(mapLibreMap)
            onReady()
        }
    }

    fun setOnSiteClickListener(listener: ((String) -> Unit)?) {
        onSiteClick = listener
    }

    fun setOnEmptyMapClickListener(listener: ((latitude: Double, longitude: Double) -> Unit)?) {
        onEmptyMapClick = listener
    }

    fun updateUserLocation(latitude: Double, longitude: Double, @Suppress("UNUSED_PARAMETER") approximate: Boolean) {
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
    ) {
        val s = style ?: return
        val siteFeatures = results.map { result ->
            Feature.fromGeometry(
                Point.fromLngLat(result.site.longitude, result.site.latitude),
            ).apply {
                addStringProperty("id", result.site.id)
                addStringProperty("name", result.site.name)
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

        val selectedFeature = selected?.let {
            Feature.fromGeometry(Point.fromLngLat(it.site.longitude, it.site.latitude))
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_SELECTED)
            ?.setGeoJson(
                if (selectedFeature != null) FeatureCollection.fromFeatures(listOf(selectedFeature))
                else FeatureCollection.fromFeatures(emptyList()),
            )

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
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(result.site.latitude, result.site.longitude),
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
            val hit = querySiteId(PointF(screen.x, screen.y))
            if (hit != null) {
                onSiteClick?.invoke(hit)
                true
            } else {
                onEmptyMapClick?.invoke(latLng.latitude, latLng.longitude)
                true
            }
        }
    }

    private fun querySiteId(screen: PointF): String? {
        val mapLibreMap = map ?: return null
        val layers = arrayOf(LAYER_SELECTED, LAYER_SITES)
        val features = mapLibreMap.queryRenderedFeatures(screen, *layers)
        return features.firstOrNull()?.getStringProperty("id")
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
            style.addLayer(
                CircleLayer(LAYER_SITES, SOURCE_SITES).withProperties(
                    circleRadius(7f),
                    circleColor(Color.parseColor("#2E7D32")),
                    circleStrokeColor(Color.WHITE),
                    circleStrokeWidth(1.5f),
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
    }

    companion object {
        const val MIN_ZOOM = 5.0
        const val MAX_ZOOM = 18.0
        const val POI_ZOOM = 15.0
        const val POI_ZOOM_SINGLE = 14.5

        const val SOURCE_USER = "navilas-user"
        const val SOURCE_SEARCH_PIN = "navilas-search-pin"
        const val SOURCE_SITES = "navilas-sites"
        const val SOURCE_SELECTED = "navilas-selected"
        const val SOURCE_ZANOCUJ = "navilas-zanocuj"
        const val SOURCE_ROAD_TARGET = "navilas-road-target"
        const val SOURCE_HELPER_LINE = "navilas-helper-line"
        const val LAYER_USER = "navilas-user-layer"
        const val LAYER_SEARCH_PIN = "navilas-search-pin-layer"
        const val LAYER_SITES = "navilas-sites-layer"
        const val LAYER_SELECTED = "navilas-selected-layer"
        const val LAYER_ZANOCUJ_FILL = "navilas-zanocuj-fill"
        const val LAYER_ZANOCUJ_LINE = "navilas-zanocuj-line"
        const val LAYER_ROAD_TARGET = "navilas-road-target-layer"
        const val LAYER_HELPER_LINE = "navilas-helper-line-layer"
    }
}
