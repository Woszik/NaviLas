package pl.navilas.finder.data.bdl

import org.json.JSONArray
import org.json.JSONObject
import pl.navilas.finder.domain.BdlDataScope
import pl.navilas.finder.domain.OfflineBdlConfig
import pl.navilas.finder.domain.ZanocujPolygonQuality
import pl.navilas.finder.util.GeoUtils
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * File-backed cache of BDL feature JSON per layer.
 * Directory: `{filesDir}/bdl_offline/`.
 */
class BdlOfflineStore(
    private val rootDir: File,
) {
    private val manifestFile = File(rootDir, MANIFEST_FILE)

    fun isReady(): Boolean = readManifest() != null

    fun storedConfig(): OfflineBdlConfig? = readManifest()?.let { manifest ->
        OfflineBdlConfig(
            scope = BdlDataScope.valueOf(manifest.getString("scope")),
            zanocujQuality = ZanocujPolygonQuality.valueOf(manifest.getString("zanocujQuality")),
        )
    }

    fun downloadedAt(): Long? = readManifest()?.optLong("downloadedAt")?.takeIf { it > 0L }

    fun storageBytes(): Long {
        if (!rootDir.exists()) return 0L
        return rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun layerIds(): List<Int> = readManifest()?.optJSONArray("layers")?.let { arr ->
        buildList {
            for (i in 0 until arr.length()) add(arr.getInt(i))
        }
    } ?: emptyList()

    /** Opens a streaming writer — pages are appended without holding the whole layer in RAM. */
    fun openLayerWriter(layerId: Int): LayerWriter {
        rootDir.mkdirs()
        return LayerWriter(layerFile(layerId))
    }

    fun loadLayerFeatures(layerId: Int): List<JSONObject> {
        val file = layerFile(layerId)
        if (!file.exists()) return emptyList()
        val root = JSONObject(file.readText())
        val features = root.optJSONArray("features") ?: JSONArray()
        return buildList {
            for (i in 0 until features.length()) {
                add(features.getJSONObject(i))
            }
        }
    }

    fun writeManifest(config: OfflineBdlConfig, layerIds: List<Int>, downloadedAt: Long) {
        rootDir.mkdirs()
        val manifest = JSONObject()
            .put("scope", config.scope.name)
            .put("zanocujQuality", config.zanocujQuality.name)
            .put("downloadedAt", downloadedAt)
            .put("layers", JSONArray(layerIds))
        manifestFile.writeText(manifest.toString())
    }

    fun deleteAll() {
        if (rootDir.exists()) {
            rootDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        manifestFile.delete()
    }

    fun filterPointFeatures(
        layerId: Int,
        userLat: Double,
        userLon: Double,
        radiusKm: Double,
    ): List<JSONObject> {
        val envelope = GeoUtils.envelopeAround(userLat, userLon, radiusKm)
        return loadLayerFeatures(layerId).filter { feature ->
            val point = BdlMapper.pointFromGeometry(feature.optJSONObject("geometry")) ?: return@filter false
            val lon = point.first
            val lat = point.second
            lon in envelope.xmin..envelope.xmax &&
                lat in envelope.ymin..envelope.ymax &&
                GeoUtils.distanceKm(userLat, userLon, lat, lon) <= radiusKm
        }
    }

    fun filterZanocujPolygons(
        userLat: Double,
        userLon: Double,
        radiusKm: Double,
        nearZoneMarginKm: Double,
    ): List<ZanocujPolygon> {
        val expanded = GeoUtils.envelopeAround(userLat, userLon, radiusKm + nearZoneMarginKm)
        return loadLayerFeatures(RestSiteRepository.LAYER_ZANOCUJ).mapNotNull { feature ->
            val attrs = feature.optJSONObject("attributes") ?: return@mapNotNull null
            val rings = BdlMapper.ringsFromPolygon(feature.optJSONObject("geometry")) ?: return@mapNotNull null
            if (!ringsIntersectsEnvelope(rings, expanded)) return@mapNotNull null
            ZanocujPolygon(
                id = BdlIdentity.resolve(RestSiteRepository.LAYER_ZANOCUJ, attrs),
                name = attrs.optString("nzw_ob").takeIf { it.isNotBlank() },
                rings = rings,
            )
        }
    }

    private fun ringsIntersectsEnvelope(
        rings: List<List<pl.navilas.finder.domain.LatLon>>,
        envelope: GeoUtils.Envelope,
    ): Boolean {
        for (ring in rings) {
            for (point in ring) {
                if (point.longitude in envelope.xmin..envelope.xmax &&
                    point.latitude in envelope.ymin..envelope.ymax
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun readManifest(): JSONObject? {
        if (!manifestFile.exists()) return null
        return runCatching { JSONObject(manifestFile.readText()) }.getOrNull()
    }

    private fun layerFile(layerId: Int) = File(rootDir, "layer_$layerId.json")

    /**
     * Incrementally writes `{"features":[...]}` so large layers (e.g. 35 — szlaki)
     * never need the full layer in memory at once.
     */
    class LayerWriter internal constructor(private val file: File) : AutoCloseable {
        private val writer: BufferedWriter = BufferedWriter(
            OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8),
        )
        private var firstFeature = true

        init {
            writer.write("{\"features\":[")
        }

        fun appendPage(features: List<JSONObject>) {
            for (feature in features) {
                if (!firstFeature) writer.write(",")
                writer.write(feature.toString())
                firstFeature = false
            }
            writer.flush()
        }

        override fun close() {
            writer.write("]}")
            writer.flush()
            writer.close()
        }
    }

    companion object {
        const val DIR_NAME = "bdl_offline"
        private const val MANIFEST_FILE = "manifest.json"

        val FULL_BDL_LAYER_IDS: List<Int> = listOf(
            0, 1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 17, 19, 21, 23, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
        )

        val NAVILAS_LAYER_IDS: List<Int> = listOf(
            RestSiteRepository.LAYER_ZANOCUJ,
            RestSiteRepository.LAYER_REST,
            RestSiteRepository.LAYER_PARKING,
            RestSiteRepository.LAYER_STOP,
            RestSiteRepository.LAYER_VIEWPOINT,
            RestSiteRepository.LAYER_OTHER,
        )

        fun layerIdsForScope(scope: BdlDataScope): List<Int> = when (scope) {
            BdlDataScope.NAVILAS_CORE -> NAVILAS_LAYER_IDS
            BdlDataScope.FULL_BDL -> FULL_BDL_LAYER_IDS
        }

        fun fromAppFilesDir(filesDir: File): BdlOfflineStore =
            BdlOfflineStore(File(filesDir, DIR_NAME))
    }
}
