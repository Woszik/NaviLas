package pl.navilas.finder.data.bdl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.navilas.finder.domain.Poi
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.util.GeoUtils
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Reads tourist features from the official BDL ArcGIS MapServer (Czas w Las).
 *
 * Query strategy:
 * 1. Server-side spatial filter: envelope from the caller-selected radius (WGS84).
 * 2. Client-side haversine filter to the same selected radius.
 */
class BdlRepository(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = BASE_URL,
) {
    suspend fun findNearby(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = DEFAULT_RADIUS_KM,
    ): List<Poi> = withContext(Dispatchers.IO) {
        val envelope = GeoUtils.envelopeAround(latitude, longitude, radiusKm)
        coroutineScope {
            val parking = async { queryLayer(LAYER_PARKING, envelope, BdlMapper::mapParkingFeature) }
            val rest = async { queryLayer(LAYER_REST, envelope, BdlMapper::mapRestFeature) }
            val camp = async { queryLayer(LAYER_CAMP, envelope, BdlMapper::mapCampFeature) }
            listOf(parking, rest, camp).awaitAll().flatten()
        }
            .distinctBy { it.id }
            .filter { GeoUtils.isWithinRadiusKm(latitude, longitude, it, radiusKm) }
            .sortedBy { GeoUtils.distanceToPoiKm(latitude, longitude, it) }
    }

    private fun queryLayer(
        layerId: Int,
        envelope: GeoUtils.Envelope,
        mapper: (org.json.JSONObject) -> Poi?,
    ): List<Poi> {
        val results = mutableListOf<Poi>()
        var offset = 0
        while (true) {
            val body = executeQuery(layerId, envelope, offset, PAGE_SIZE)
            val features = BdlMapper.parseFeatures(body)
            features.mapNotNullTo(results, mapper)
            // Stop when the server returned a partial page — no need to over-fetch.
            if (features.size < PAGE_SIZE) break
            offset += PAGE_SIZE
            if (offset >= HARD_FETCH_CAP) break
        }
        return results
    }

    private fun executeQuery(
        layerId: Int,
        envelope: GeoUtils.Envelope,
        resultOffset: Int,
        resultRecordCount: Int,
    ): String {
        val simplifyOffset = if (layerId == LAYER_CAMP) "250" else "0"
        val outFields = when (layerId) {
            LAYER_CAMP -> OUT_FIELDS_CAMP
            else -> OUT_FIELDS_POINT
        }
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
            .addQueryParameter("maxAllowableOffset", simplifyOffset)
            .addQueryParameter("resultOffset", resultOffset.toString())
            .addQueryParameter("resultRecordCount", resultRecordCount.toString())
            .addQueryParameter("f", "json")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "NaviLas/${pl.navilas.finder.BuildConfig.VERSION_NAME} (Android; contact: woszi@pm.me)")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("BDL HTTP ${response.code}")
            }
            return response.body?.string().orEmpty().ifBlank {
                throw IOException("Pusta odpowiedź BDL")
            }
        }
    }

    companion object {
        const val BASE_URL =
            "https://mapserver.bdl.lasy.gov.pl/arcgis/rest/services/Czas_w_las/WFS_BDL_czas_w_las/MapServer"
        const val DEFAULT_RADIUS_KM = SearchConfig.DEFAULT_SEARCH_RADIUS_KM
        const val MAX_RADIUS_KM = SearchConfig.MAX_SEARCH_RADIUS_KM
        const val LAYER_CAMP = 0
        const val LAYER_REST = 15
        const val LAYER_PARKING = 17

        /** Page size kept moderate; spatial envelope already limits result sets. */
        private const val PAGE_SIZE = 500
        /** Safety cap (~2 pages) — avoids runaway downloads if the service ignores paging. */
        private const val HARD_FETCH_CAP = 1000

        private const val OUT_FIELDS_POINT =
            "objectid,foreign_key,tur_rec_pnt_id,inv_nr,nzw_ob,uwagi,link,wiata,palenisko,lawostoly"
        private const val OUT_FIELDS_CAMP =
            "objectid,foreign_key,tur_sleep_poly_id,inv_nr,nzw_ob,uwagi,link,wiata,palenisko,lawostoly"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
