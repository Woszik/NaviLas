package pl.navilas.finder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.saved.SavedPointsBackupCodec
import pl.navilas.finder.data.saved.SavedPointsBackupParseResult
import pl.navilas.finder.data.saved.SavedPointsImportMode
import pl.navilas.finder.data.saved.SavedPointsStore
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.SavedPoint
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.domain.ZanocujStatus
import java.io.File

class SavedPointsBackupTest {
    @Test
    fun export_and_import_merge_roundtrip() {
        val file = File.createTempFile("saved-backup", ".json")
        val store = SavedPointsStore(file)
        val category = store.addCategory("Weekend")
        store.savePoint(
            SavedPoint(
                site = sampleSite("site-a"),
                savedAtMs = 100L,
                categoryIds = setOf(category.id),
                userComment = "Test",
            ),
        )
        val exported = SavedPointsBackupCodec.encodeExport(store, "0.5.5", 200L)
        val parsed = SavedPointsBackupCodec.parseImport(exported) { root ->
            SavedPointsStore(File.createTempFile("parse", ".json")).parseSnapshot(root)
        }
        assertTrue(parsed is SavedPointsBackupParseResult.Success)
        val snapshot = (parsed as SavedPointsBackupParseResult.Success).snapshot
        assertEquals(1, snapshot.points.size)
        assertEquals(1, snapshot.categories.size)

        val targetFile = File.createTempFile("saved-target", ".json")
        val target = SavedPointsStore(targetFile)
        val result = target.importSnapshot(snapshot, SavedPointsImportMode.MERGE)
        assertEquals(1, result.addedPoints)
        assertTrue(target.isSaved("site-a"))
        file.delete()
        targetFile.delete()
    }

    @Test
    fun import_merge_skips_existing_points() {
        val file = File.createTempFile("saved-merge", ".json")
        val store = SavedPointsStore(file)
        store.savePoint(
            SavedPoint(
                site = sampleSite("site-a"),
                savedAtMs = 1L,
                categoryIds = emptySet(),
                userComment = "local",
            ),
        )
        val importedSnapshot = SavedPointsBackupCodec.parseImport(
            SavedPointsBackupCodec.encodeExport(
                SavedPointsStore(file).also {
                    it.savePoint(
                        SavedPoint(
                            site = sampleSite("site-a"),
                            savedAtMs = 2L,
                            categoryIds = emptySet(),
                            userComment = "imported",
                        ),
                    )
                    it.savePoint(
                        SavedPoint(
                            site = sampleSite("site-b"),
                            savedAtMs = 3L,
                            categoryIds = emptySet(),
                            userComment = null,
                        ),
                    )
                },
                "0.5.5",
                1L,
            ),
        ) { root -> store.parseSnapshot(root) }
        val snapshot = (importedSnapshot as SavedPointsBackupParseResult.Success).snapshot
        val mergeResult = store.importSnapshot(snapshot, SavedPointsImportMode.MERGE)
        assertEquals(1, mergeResult.addedPoints)
        assertEquals(1, mergeResult.skippedPoints)
        assertEquals("local", store.getPoint("site-a")!!.userComment)
        assertTrue(store.isSaved("site-b"))
        file.delete()
    }

    @Test
    fun import_replace_overwrites_all() {
        val file = File.createTempFile("saved-replace", ".json")
        val store = SavedPointsStore(file)
        store.savePoint(
            SavedPoint(
                site = sampleSite("old"),
                savedAtMs = 1L,
                categoryIds = emptySet(),
                userComment = null,
            ),
        )
        val exportStore = SavedPointsStore(File.createTempFile("export", ".json"))
        exportStore.savePoint(
            SavedPoint(
                site = sampleSite("new"),
                savedAtMs = 2L,
                categoryIds = emptySet(),
                userComment = "fresh",
            ),
        )
        val exported = SavedPointsBackupCodec.encodeExport(exportStore, "0.5.5", 1L)
        val parsed = SavedPointsBackupCodec.parseImport(exported) { root -> store.parseSnapshot(root) }
        val snapshot = (parsed as SavedPointsBackupParseResult.Success).snapshot
        store.importSnapshot(snapshot, SavedPointsImportMode.REPLACE)
        assertFalse(store.isSaved("old"))
        assertTrue(store.isSaved("new"))
        file.delete()
    }

    @Test
    fun parse_rejects_unsupported_format_version() {
        val json = JSONObject()
            .put("formatVersion", 99)
            .put("categories", JSONObject())
            .put("points", JSONObject())
            .toString()
        val parsed = SavedPointsBackupCodec.parseImport(json) { root ->
            SavedPointsStore(File.createTempFile("v", ".json")).parseSnapshot(root)
        }
        assertTrue(parsed is SavedPointsBackupParseResult.Failure)
    }

    @Test
    fun parse_accepts_legacy_internal_format_without_envelope() {
        val file = File.createTempFile("legacy", ".json")
        file.writeText(
            """
            {
              "categories": [{"id":"cat-old","name":"Stara","sortOrder":0}],
              "points": {
                "site-legacy": {
                  "savedAtMs": 1,
                  "categoryId": "cat-old",
                  "site": {
                    "id": "site-legacy",
                    "name": "Legacy",
                    "latitude": 52.0,
                    "longitude": 21.0,
                    "sourceLayerId": 15,
                    "sourceLayerName": "test",
                    "features": ["WIATA"],
                    "relatedObjects": [],
                    "zanocujStatus": "OUTSIDE_ZONE"
                  }
                }
              }
            }
            """.trimIndent(),
        )
        val text = file.readText()
        val parsed = SavedPointsBackupCodec.parseImport(text) { root ->
            SavedPointsStore(File.createTempFile("legacy-parse", ".json")).parseSnapshot(root)
        }
        assertTrue(parsed is SavedPointsBackupParseResult.Success)
        assertEquals(1, (parsed as SavedPointsBackupParseResult.Success).snapshot.points.size)
        file.delete()
    }

    private fun sampleSite(id: String) = RestSite(
        id = id,
        name = "Test LP",
        latitude = 52.0,
        longitude = 21.0,
        description = null,
        sourceLayerId = 15,
        sourceLayerName = "test",
        features = setOf(SiteFeature.WIATA),
        relatedObjects = emptyList(),
        zanocujStatus = ZanocujStatus.OUTSIDE_ZONE,
    )
}
