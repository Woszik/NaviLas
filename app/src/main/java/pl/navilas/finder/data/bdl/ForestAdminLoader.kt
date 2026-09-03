package pl.navilas.finder.data.bdl

import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import pl.navilas.finder.domain.ForestAdmin
import pl.navilas.finder.domain.ForestAdminLookup
import java.util.concurrent.TimeUnit

/**
 * On-demand identify of nadleśnictwo / leśnictwo for a WGS84 point.
 * Uses WMS_BDL polygons; not stored in the offline places pack.
 */
class ForestAdminLoader(
    private val client: BdlArcGisClient = BdlArcGisClient(
        client = interactiveClient(),
        baseUrl = ForestAdminCatalog.BASE_URL,
    ),
) {
    fun lookup(latitude: Double, longitude: Double): ForestAdminLookup {
        val inspectorateError = arrayOfNulls<String>(1)
        val forestryError = arrayOfNulls<String>(1)
        val inspectorate = runCatching {
            latestAttributes(
                client.queryIntersectingPoint(
                    layerId = ForestAdminCatalog.LAYER_INSPECTORATE,
                    latitude = latitude,
                    longitude = longitude,
                    outFields = ForestAdminCatalog.INSPECTORATE_FIELDS,
                ),
            )
        }.onFailure { inspectorateError[0] = it.message }.getOrNull()
        val forestry = runCatching {
            latestAttributes(
                client.queryIntersectingPoint(
                    layerId = ForestAdminCatalog.LAYER_FORESTRY,
                    latitude = latitude,
                    longitude = longitude,
                    outFields = ForestAdminCatalog.FORESTRY_FIELDS,
                ),
            )
        }.onFailure { forestryError[0] = it.message }.getOrNull()

        if (inspectorate == null && forestry == null) {
            val err = inspectorateError[0] ?: forestryError[0]
            return if (err != null) {
                ForestAdminLookup.Failed(err)
            } else {
                ForestAdminLookup.OutsideLp
            }
        }
        val admin = merge(inspectorate, forestry)
        return if (admin.isEmpty()) {
            ForestAdminLookup.OutsideLp
        } else {
            ForestAdminLookup.Found(admin)
        }
    }

    companion object {
        fun latestAttributes(body: String): JSONObject? {
            val root = JSONObject(body)
            val features: JSONArray = root.optJSONArray("features") ?: return null
            var best: JSONObject? = null
            var bestYear = Int.MIN_VALUE
            for (i in 0 until features.length()) {
                val attrs = features.optJSONObject(i)?.optJSONObject("attributes") ?: continue
                val year = attrs.optInt("a_year", 0)
                if (best == null || year >= bestYear) {
                    best = attrs
                    bestYear = year
                }
            }
            return best
        }

        fun merge(inspectorate: JSONObject?, forestry: JSONObject?): ForestAdmin {
            val year = sequenceOf(inspectorate, forestry)
                .mapNotNull { it?.optInt("a_year")?.takeIf { y -> y > 0 } }
                .maxOrNull()
            return ForestAdmin(
                inspectorateName = clean(inspectorate?.optString("inspectorate_name"))
                    ?: clean(forestry?.optString("inspectorate_name")),
                inspectorateAddress = clean(inspectorate?.optString("inspectorate_adres")),
                forestryName = clean(forestry?.optString("forest_range_name")),
                regionName = clean(forestry?.optString("region_name")),
                year = year,
            )
        }

        fun clean(raw: String?): String? = ForestEntryBanLoader.clean(raw)

        fun interactiveClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .build()
    }
}
