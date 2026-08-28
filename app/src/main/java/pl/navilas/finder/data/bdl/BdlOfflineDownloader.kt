package pl.navilas.finder.data.bdl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.navilas.finder.domain.OfflineBdlConfig
import java.io.File

class BdlOfflineDownloader(
    private val filesDir: File,
    private val client: BdlArcGisClient = BdlArcGisClient(),
) {
    suspend fun download(
        config: OfflineBdlConfig,
        onProgress: (completedSteps: Int, totalSteps: Int, label: String) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        BdlOfflineStore.clearStaging(filesDir)
        val staging = BdlOfflineStore.stagingFromAppFilesDir(filesDir)
        try {
            val layerIds = BdlOfflineStore.layerIdsForScope(config.scope)
            val pageCounts = layerIds.associateWith { layerId ->
                val total = client.countAllFeatures(layerId)
                val pageSize = downloadParamsFor(layerId, config).pageSize
                if (total == 0) 0 else (total + pageSize - 1) / pageSize
            }
            val totalSteps = pageCounts.values.sum().coerceAtLeast(1)
            var completed = 0

            for (layerId in layerIds) {
                val pages = pageCounts[layerId] ?: 0
                if (pages == 0) continue
                val params = downloadParamsFor(layerId, config)
                val outFields = outFieldsForLayer(layerId)
                var offset = 0
                staging.openLayerWriter(layerId).use { writer ->
                    repeat(pages) { pageIndex ->
                        onProgress(
                            completed,
                            totalSteps,
                            "Warstwa $layerId (${pageIndex + 1}/$pages)",
                        )
                        val body = client.queryAllFeaturesPage(
                            layerId = layerId,
                            outFields = outFields,
                            returnGeometry = true,
                            maxAllowableOffset = params.maxAllowableOffset,
                            resultOffset = offset,
                            resultRecordCount = params.pageSize,
                        )
                        val pageFeatures = BdlMapper.parseFeatures(body)
                        writer.appendPage(pageFeatures)
                        offset += params.pageSize
                        completed++
                        onProgress(completed, totalSteps, "Warstwa $layerId (${pageIndex + 1}/$pages)")
                    }
                }
            }

            staging.writeManifest(
                config = config,
                layerIds = layerIds.filter { (pageCounts[it] ?: 0) > 0 },
                downloadedAt = System.currentTimeMillis(),
            )
            onProgress(totalSteps, totalSteps, "Aktywacja nowej bazy…")
            BdlOfflineStore.promoteStagingToLive(filesDir)
            onProgress(totalSteps, totalSteps, "Gotowe")
        } catch (e: Exception) {
            BdlOfflineStore.clearStaging(filesDir)
            throw e
        }
    }

    internal fun downloadParamsFor(layerId: Int, config: OfflineBdlConfig): LayerDownloadParams {
        if (layerId == RestSiteRepository.LAYER_ZANOCUJ) {
            return LayerDownloadParams(
                pageSize = BdlArcGisClient.DOWNLOAD_PAGE_SIZE,
                maxAllowableOffset = BdlArcGisClient.maxAllowableOffsetForZanocuj(config.zanocujQuality),
            )
        }
        if (layerId in BdlArcGisClient.HEAVY_POLYLINE_LAYERS) {
            return LayerDownloadParams(
                pageSize = BdlArcGisClient.HEAVY_LAYER_PAGE_SIZE,
                maxAllowableOffset = BdlArcGisClient.POLYLINE_SIMPLIFIED_OFFSET,
            )
        }
        return LayerDownloadParams(
            pageSize = BdlArcGisClient.DOWNLOAD_PAGE_SIZE,
            maxAllowableOffset = "0",
        )
    }

    private fun outFieldsForLayer(layerId: Int): String = when (layerId) {
        RestSiteRepository.LAYER_ZANOCUJ -> "objectid,foreign_key,tur_sleep_poly_id,inv_nr,nzw_ob"
        RestSiteRepository.LAYER_REST -> RestSiteRepository.OUT_FIELDS_REST
        RestSiteRepository.LAYER_PARKING, RestSiteRepository.LAYER_STOP -> RestSiteRepository.OUT_FIELDS_VEHICLE
        RestSiteRepository.LAYER_VIEWPOINT -> RestSiteRepository.OUT_FIELDS_SATELLITE
        RestSiteRepository.LAYER_OTHER -> RestSiteRepository.OUT_FIELDS_OTHER
        in BdlArcGisClient.HEAVY_POLYLINE_LAYERS ->
            "objectid,foreign_key,inv_nr,nzw_ob"
        else -> "*"
    }

    data class LayerDownloadParams(
        val pageSize: Int,
        val maxAllowableOffset: String,
    )
}
