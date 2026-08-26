package pl.navilas.finder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.bdl.BdlArcGisClient
import pl.navilas.finder.data.bdl.BdlOfflineDownloader
import pl.navilas.finder.data.bdl.BdlOfflineStore
import pl.navilas.finder.data.bdl.RestSiteRepository
import pl.navilas.finder.domain.BdlDataScope
import pl.navilas.finder.domain.OfflineBdlConfig
import pl.navilas.finder.domain.ZanocujPolygonQuality
import pl.navilas.finder.domain.estimatedSizeLabel
import java.io.File

class OfflineBdlStoreTest {

    @Test
    fun manifest_roundtrip_and_delete() {
        val dir = createTempDir(prefix = "navilas-offline-test")
        val store = BdlOfflineStore(File(dir, "bdl_offline"))
        val config = OfflineBdlConfig(BdlDataScope.NAVILAS_CORE, ZanocujPolygonQuality.SIMPLIFIED)
        store.openLayerWriter(15).use { writer ->
            writer.appendPage(listOf(samplePointFeature(52.0, 21.0)))
        }
        store.writeManifest(config, listOf(15), downloadedAt = 1_700_000_000_000L)

        assertTrue(store.isReady())
        assertEquals(config, store.storedConfig())
        assertEquals(1_700_000_000_000L, store.downloadedAt())
        assertTrue(store.storageBytes() > 0L)

        store.deleteAll()
        assertFalse(store.isReady())
        assertEquals(0L, store.storageBytes())
    }

    @Test
    fun filter_point_features_within_radius() {
        val dir = createTempDir(prefix = "navilas-offline-filter")
        val store = BdlOfflineStore(File(dir, "bdl_offline"))
        store.openLayerWriter(15).use { writer ->
            writer.appendPage(
                listOf(
                    samplePointFeature(52.202265, 21.181408),
                    samplePointFeature(50.0, 14.0),
                ),
            )
        }

        val hits = store.filterPointFeatures(15, 52.202265, 21.181408, radiusKm = 25.0)
        assertEquals(1, hits.size)
    }

    @Test
    fun layer_writer_supports_multiple_pages() {
        val dir = createTempDir(prefix = "navilas-offline-pages")
        val store = BdlOfflineStore(File(dir, "bdl_offline"))
        store.openLayerWriter(15).use { writer ->
            writer.appendPage(listOf(samplePointFeature(52.0, 21.0)))
            writer.appendPage(listOf(samplePointFeature(53.0, 22.0)))
        }
        assertEquals(2, store.loadLayerFeatures(15).size)
    }

    @Test
    fun heavy_polyline_layers_use_smaller_pages_and_simplified_geometry() {
        val downloader = BdlOfflineDownloader(filesDir = createTempDir())
        val config = OfflineBdlConfig(BdlDataScope.FULL_BDL, ZanocujPolygonQuality.SIMPLIFIED)
        val trailParams = downloader.downloadParamsFor(35, config)
        assertEquals(BdlArcGisClient.HEAVY_LAYER_PAGE_SIZE, trailParams.pageSize)
        assertEquals(BdlArcGisClient.POLYLINE_SIMPLIFIED_OFFSET, trailParams.maxAllowableOffset)
        val restParams = downloader.downloadParamsFor(RestSiteRepository.LAYER_REST, config)
        assertEquals(BdlArcGisClient.DOWNLOAD_PAGE_SIZE, restParams.pageSize)
        assertEquals("0", restParams.maxAllowableOffset)
    }

    @Test
    fun promote_staging_replaces_live_and_keeps_old_until_success() {
        val filesDir = createTempDir(prefix = "navilas-offline-promote")
        val live = BdlOfflineStore.fromAppFilesDir(filesDir)
        val configOld = OfflineBdlConfig(BdlDataScope.NAVILAS_CORE, ZanocujPolygonQuality.SIMPLIFIED)
        live.openLayerWriter(15).use { it.appendPage(listOf(samplePointFeature(52.0, 21.0))) }
        live.writeManifest(configOld, listOf(15), downloadedAt = 1L)
        assertTrue(live.isReady())

        val staging = BdlOfflineStore.stagingFromAppFilesDir(filesDir)
        staging.openLayerWriter(15).use { it.appendPage(listOf(samplePointFeature(53.0, 22.0))) }
        staging.writeManifest(
            OfflineBdlConfig(BdlDataScope.NAVILAS_CORE, ZanocujPolygonQuality.PRECISE),
            listOf(15),
            downloadedAt = 2L,
        )

        BdlOfflineStore.promoteStagingToLive(filesDir)
        val after = BdlOfflineStore.fromAppFilesDir(filesDir)
        assertTrue(after.isReady())
        assertEquals(2L, after.downloadedAt())
        assertEquals(ZanocujPolygonQuality.PRECISE, after.storedConfig()?.zanocujQuality)
        assertEquals(1, after.loadLayerFeatures(15).size)
        assertFalse(File(filesDir, BdlOfflineStore.STAGING_DIR_NAME).exists())
    }

    @Test
    fun clear_staging_does_not_touch_live() {
        val filesDir = createTempDir(prefix = "navilas-offline-clear-staging")
        val live = BdlOfflineStore.fromAppFilesDir(filesDir)
        live.openLayerWriter(15).use { it.appendPage(listOf(samplePointFeature(52.0, 21.0))) }
        live.writeManifest(
            OfflineBdlConfig(BdlDataScope.NAVILAS_CORE, ZanocujPolygonQuality.SIMPLIFIED),
            listOf(15),
            downloadedAt = 1L,
        )
        val staging = BdlOfflineStore.stagingFromAppFilesDir(filesDir)
        staging.openLayerWriter(15).use { it.appendPage(listOf(samplePointFeature(50.0, 20.0))) }
        BdlOfflineStore.clearStaging(filesDir)
        assertTrue(BdlOfflineStore.fromAppFilesDir(filesDir).isReady())
        assertFalse(File(filesDir, BdlOfflineStore.STAGING_DIR_NAME).exists())
    }

    @Test
    fun config_change_requires_different_manifest() {
        val a = OfflineBdlConfig(BdlDataScope.NAVILAS_CORE, ZanocujPolygonQuality.PRECISE)
        val b = OfflineBdlConfig(BdlDataScope.NAVILAS_CORE, ZanocujPolygonQuality.SIMPLIFIED)
        assertFalse(a.matches(b))
        assertTrue(a.matches(a))
    }

    @Test
    fun estimated_size_labels() {
        assertEquals(
            "~15–25 MB",
            OfflineBdlConfig(BdlDataScope.NAVILAS_CORE, ZanocujPolygonQuality.SIMPLIFIED).estimatedSizeLabel(),
        )
        assertEquals(
            "~55–60 MB",
            OfflineBdlConfig(BdlDataScope.NAVILAS_CORE, ZanocujPolygonQuality.PRECISE).estimatedSizeLabel(),
        )
    }

    private fun samplePointFeature(lat: Double, lon: Double): JSONObject = JSONObject(
        """
        {
          "attributes": {"objectid": 1, "nzw_ob": "Test"},
          "geometry": {"x": $lon, "y": $lat}
        }
        """.trimIndent(),
    )
}
