package pl.navilas.finder.data.bdl

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import pl.navilas.finder.domain.ZanocujPolygonQuality
import pl.navilas.finder.util.GeoUtils
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Low-level ArcGIS MapServer HTTP client shared by online search and offline sync.
 */
class BdlArcGisClient(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = BdlRepository.BASE_URL,
) {
    fun queryEnvelope(
        layerId: Int,
        envelope: GeoUtils.Envelope,
        outFields: String,
        returnGeometry: Boolean,
        maxAllowableOffset: String,
        resultOffset: Int,
        resultRecordCount: Int,
    ): String = executeQuery(
        layerId = layerId,
        outFields = outFields,
        returnGeometry = returnGeometry,
        maxAllowableOffset = maxAllowableOffset,
        resultOffset = resultOffset,
        resultRecordCount = resultRecordCount,
        geometryJson = envelopeJson(envelope),
    )

    fun queryAllFeaturesPage(
        layerId: Int,
        outFields: String,
        returnGeometry: Boolean,
        maxAllowableOffset: String,
        resultOffset: Int,
        resultRecordCount: Int,
    ): String = executeQuery(
        layerId = layerId,
        outFields = outFields,
        returnGeometry = returnGeometry,
        maxAllowableOffset = maxAllowableOffset,
        resultOffset = resultOffset,
        resultRecordCount = resultRecordCount,
        geometryJson = null,
    )

    fun countAllFeatures(layerId: Int): Int {
        val url = "$baseUrl/$layerId/query".toHttpUrl().newBuilder()
            .addQueryParameter("where", "1=1")
            .addQueryParameter("returnCountOnly", "true")
            .addQueryParameter("f", "json")
            .build()
        val body = get(url.toString())
        val root = JSONObject(body)
        if (root.has("error")) {
            val err = root.getJSONObject("error")
            throw IllegalStateException(err.optString("message", "Błąd BDL"))
        }
        return root.optInt("count", 0)
    }

    private fun executeQuery(
        layerId: Int,
        outFields: String,
        returnGeometry: Boolean,
        maxAllowableOffset: String,
        resultOffset: Int,
        resultRecordCount: Int,
        geometryJson: String?,
    ): String {
        val builder = "$baseUrl/$layerId/query".toHttpUrl().newBuilder()
            .addQueryParameter("where", "1=1")
            .addQueryParameter("outFields", outFields)
            .addQueryParameter("returnGeometry", returnGeometry.toString())
            .addQueryParameter("outSR", "4326")
            .addQueryParameter("maxAllowableOffset", maxAllowableOffset)
            .addQueryParameter("resultOffset", resultOffset.toString())
            .addQueryParameter("resultRecordCount", resultRecordCount.toString())
            .addQueryParameter("f", "json")
        if (geometryJson != null) {
            builder
                .addQueryParameter("geometry", geometryJson)
                .addQueryParameter("geometryType", "esriGeometryEnvelope")
                .addQueryParameter("inSR", "4326")
                .addQueryParameter("spatialRel", "esriSpatialRelIntersects")
        }
        return get(builder.build().toString())
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "NaviLas/${pl.navilas.finder.BuildConfig.VERSION_NAME} (Android; contact: woszi@pm.me)")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(
                    "BDL HTTP ${response.code}; bytes=${body.length}; body=${body.take(400)}",
                )
            }
            if (body.isBlank()) throw IOException("BDL: pusta odpowiedź")
            val root = JSONObject(body)
            if (root.has("error")) {
                val err = root.getJSONObject("error")
                throw IllegalStateException(
                    "${err.optInt("code", -1)}: ${err.optString("message", "Błąd BDL")}",
                )
            }
            return body
        }
    }

    companion object {
        const val DOWNLOAD_PAGE_SIZE = 1000

        /** Szlaki (35) and ścieżki dydaktyczne (34) — full geometry OOMs on mobile (~280 MB layer 35). */
        val HEAVY_POLYLINE_LAYERS: Set<Int> = setOf(34, 35)
        const val HEAVY_LAYER_PAGE_SIZE = 100
        const val POLYLINE_SIMPLIFIED_OFFSET = "0.0003"

        /** ~33 m in WGS84 degrees at mid-latitudes — good size/accuracy trade-off. */
        const val ZANOCUJ_SIMPLIFIED_OFFSET = "0.0003"

        fun maxAllowableOffsetForZanocuj(quality: ZanocujPolygonQuality): String = when (quality) {
            ZanocujPolygonQuality.PRECISE -> "0"
            ZanocujPolygonQuality.SIMPLIFIED -> ZANOCUJ_SIMPLIFIED_OFFSET
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .callTimeout(360, TimeUnit.SECONDS)
            .build()

        private fun envelopeJson(envelope: GeoUtils.Envelope): String =
            """{"xmin":${envelope.xmin},"ymin":${envelope.ymin},"xmax":${envelope.xmax},"ymax":${envelope.ymax},"spatialReference":{"wkid":4326}}"""
    }
}
