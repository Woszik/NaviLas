package pl.navilas.finder.data.bdl

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.util.GeoUtils
import java.util.concurrent.TimeUnit

/**
 * Temporary offline-of-UI diagnostics for the BDL rest-site search pipeline.
 * Uses a fixed GPS sample so results do not depend on the device location provider.
 */
object RestSiteSearchDiagnostics {
    /** Known point near a layer-15 rest site (Warsaw / Wawer area). */
    const val SAMPLE_LAT = 52.202265
    const val SAMPLE_LON = 21.181408

    data class RadiusReport(
        val radiusKm: Double,
        val envelope: GeoUtils.Envelope,
        val layer15Http: Int,
        val layer15Bytes: Int,
        val layer15ContentType: String?,
        val layer15Url: String,
        val jsonValid: Boolean,
        val arcgisError: String?,
        val bdlFeatures: Int,
        val hasGeometryCount: Int,
        val withinRadius: Int,
        val satelliteNotes: List<String>,
    )

    fun diagnoseAllPresets(
        latitude: Double = SAMPLE_LAT,
        longitude: Double = SAMPLE_LON,
        client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build(),
        baseUrl: String = BdlRepository.BASE_URL,
    ): List<RadiusReport> =
        SearchConfig.SEARCH_RADIUS_PRESETS_KM.map { radius ->
            diagnoseOne(latitude, longitude, radius, client, baseUrl)
        }

    fun diagnoseOne(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build(),
        baseUrl: String = BdlRepository.BASE_URL,
    ): RadiusReport {
        val envelope = GeoUtils.envelopeAround(latitude, longitude, radiusKm)
        val layer15 = fetchLayer(
            client = client,
            baseUrl = baseUrl,
            layerId = RestSiteRepository.LAYER_REST,
            envelope = envelope,
            outFields = OUT_FIELDS_REST_DIAG,
            maxAllowableOffset = "0",
        )
        val features = if (layer15.arcgisError == null && layer15.jsonValid) {
            BdlMapper.parseFeatures(layer15.body)
        } else {
            emptyList()
        }
        var withGeom = 0
        var within = 0
        for (feature in features) {
            val point = BdlMapper.pointFromGeometry(feature.optJSONObject("geometry")) ?: continue
            withGeom++
            if (GeoUtils.distanceKm(latitude, longitude, point.second, point.first) <= radiusKm) {
                within++
            }
        }
        val satelliteNotes = listOf(
            RestSiteRepository.LAYER_PARKING,
            RestSiteRepository.LAYER_STOP,
            RestSiteRepository.LAYER_VIEWPOINT,
            RestSiteRepository.LAYER_OTHER,
        ).map { layerId ->
            val page = fetchLayer(
                client = client,
                baseUrl = baseUrl,
                layerId = layerId,
                envelope = envelope,
                outFields = RestSiteRepository.OUT_FIELDS_SATELLITE,
                maxAllowableOffset = "0",
            )
            val n = if (page.arcgisError == null && page.jsonValid) {
                BdlMapper.parseFeatures(page.body).size
            } else {
                0
            }
            "layer $layerId: HTTP=${page.http} features=$n error=${page.arcgisError}"
        }
        return RadiusReport(
            radiusKm = radiusKm,
            envelope = envelope,
            layer15Http = layer15.http,
            layer15Bytes = layer15.body.length,
            layer15ContentType = layer15.contentType,
            layer15Url = layer15.url,
            jsonValid = layer15.jsonValid,
            arcgisError = layer15.arcgisError,
            bdlFeatures = features.size,
            hasGeometryCount = withGeom,
            withinRadius = within,
            satelliteNotes = satelliteNotes,
        )
    }

    fun formatReport(report: RadiusReport): String = buildString {
        appendLine("GPS = $SAMPLE_LAT, $SAMPLE_LON")
        appendLine("radius = ${report.radiusKm}")
        appendLine(
            "envelope = minLon=${report.envelope.xmin} minLat=${report.envelope.ymin} " +
                "maxLon=${report.envelope.xmax} maxLat=${report.envelope.ymax}",
        )
        appendLine("BDL layer 15 URL = ${report.layer15Url}")
        appendLine("BDL layer 15 HTTP = ${report.layer15Http}")
        appendLine("content-type = ${report.layer15ContentType}; bytes = ${report.layer15Bytes}")
        appendLine("JSON valid = ${report.jsonValid}")
        if (report.arcgisError != null) {
            appendLine("BDL ArcGIS error = ${report.arcgisError}")
        }
        appendLine("BDL features = ${report.bdlFeatures}")
        appendLine("mapped (geometry present) = ${report.hasGeometryCount}")
        appendLine("withinRadius = ${report.withinRadius}")
        report.satelliteNotes.forEach { appendLine("  $it") }
        appendLine("enriched ≈ ${report.withinRadius} (before Zanocuj/UI filters)")
        appendLine("UI results ≈ ${report.withinRadius}")
    }

    private data class FetchResult(
        val url: String,
        val http: Int,
        val contentType: String?,
        val body: String,
        val jsonValid: Boolean,
        val arcgisError: String?,
    )

    private fun fetchLayer(
        client: OkHttpClient,
        baseUrl: String,
        layerId: Int,
        envelope: GeoUtils.Envelope,
        outFields: String,
        maxAllowableOffset: String,
    ): FetchResult {
        val url = "$baseUrl/$layerId/query".toHttpUrl().newBuilder()
            .addQueryParameter("where", "1=1")
            .addQueryParameter(
                "geometry",
                """{"xmin":${envelope.xmin},"ymin":${envelope.ymin},"xmax":${envelope.xmax},"ymax":${envelope.ymax},"spatialReference":{"wkid":4326}}""",
            )
            .addQueryParameter("geometryType", "esriGeometryEnvelope")
            .addQueryParameter("inSR", "4326")
            .addQueryParameter("spatialRel", "esriSpatialRelIntersects")
            .addQueryParameter("outFields", outFields)
            .addQueryParameter("returnGeometry", "true")
            .addQueryParameter("outSR", "4326")
            .addQueryParameter("maxAllowableOffset", maxAllowableOffset)
            .addQueryParameter("resultOffset", "0")
            .addQueryParameter("resultRecordCount", "500")
            .addQueryParameter("f", "json")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "NaviLas/diag")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            var jsonValid = false
            var arcgisError: String? = null
            if (body.isNotBlank()) {
                try {
                    val root = JSONObject(body)
                    jsonValid = true
                    if (root.has("error")) {
                        val err = root.getJSONObject("error")
                        arcgisError = "${err.optInt("code")}: ${err.optString("message")}"
                    }
                } catch (_: Exception) {
                    jsonValid = false
                    arcgisError = "malformed JSON"
                }
            }
            return FetchResult(
                url = url.toString(),
                http = response.code,
                contentType = response.header("Content-Type"),
                body = body,
                jsonValid = jsonValid,
                arcgisError = arcgisError,
            )
        }
    }

    private const val OUT_FIELDS_REST_DIAG =
        "objectid,foreign_key,tur_rec_pnt_id,inv_nr,nzw_ob,uwagi,link,wiata,palenisko,parking,woda_pitna,lawostoly,kuchenka,toalety_tm,toalety_st,os_toalety,n_toalety,lad_rower,serw_rower,kapielisko,marina"
}
