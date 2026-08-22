package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.saved.SavedPointsStore
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.SavedPoint
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.domain.ZanocujStatus
import java.io.File

class SavedPointsStoreTest {
    @Test
    fun save_load_and_multiple_categories() {
        val file = File.createTempFile("saved-points", ".json")
        val store = SavedPointsStore(file)
        val site = sampleSite("site-1")
        val weekend = store.addCategory("Weekend")
        val family = store.addCategory("Rodzina")
        store.savePoint(
            SavedPoint(
                site = site,
                savedAtMs = 1000L,
                categoryIds = setOf(weekend.id, family.id),
                userComment = "Super wiata",
            ),
        )
        val reloaded = SavedPointsStore(file)
        assertTrue(reloaded.isSaved("site-1"))
        val point = reloaded.getPoint("site-1")!!
        assertEquals(setOf(weekend.id, family.id), point.categoryIds)
        assertEquals("Super wiata", point.userComment)
        file.delete()
    }

    @Test
    fun save_without_categories() {
        val file = File.createTempFile("saved-points-none", ".json")
        val store = SavedPointsStore(file)
        store.savePoint(
            SavedPoint(
                site = sampleSite("site-0"),
                savedAtMs = 1L,
                categoryIds = emptySet(),
                userComment = "Bez kategorii OK",
            ),
        )
        val point = SavedPointsStore(file).getPoint("site-0")!!
        assertTrue(point.categoryIds.isEmpty())
        file.delete()
    }

    @Test
    fun delete_category_removes_from_all_points() {
        val file = File.createTempFile("saved-points-cat", ".json")
        val store = SavedPointsStore(file)
        val category = store.addCategory("Do usunięcia")
        val other = store.addCategory("Zostaje")
        store.savePoint(
            SavedPoint(
                site = sampleSite("site-2"),
                savedAtMs = 1L,
                categoryIds = setOf(category.id, other.id),
                userComment = null,
            ),
        )
        store.deleteCategory(category.id)
        val point = store.getPoint("site-2")!!
        assertFalse(category.id in point.categoryIds)
        assertTrue(other.id in point.categoryIds)
        file.delete()
    }

    @Test
    fun migrates_legacy_single_category_id() {
        val file = File.createTempFile("saved-points-legacy", ".json")
        file.writeText(
            """
            {
              "categories": [{"id":"cat-old","name":"Stara","sortOrder":0}],
              "points": {
                "site-legacy": {
                  "savedAtMs": 1,
                  "categoryId": "cat-old",
                  "userComment": "legacy",
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
        val point = SavedPointsStore(file).getPoint("site-legacy")!!
        assertEquals(setOf("cat-old"), point.categoryIds)
        file.delete()
    }

    @Test
    fun remove_point() {
        val file = File.createTempFile("saved-points-rm", ".json")
        val store = SavedPointsStore(file)
        store.savePoint(
            SavedPoint(
                site = sampleSite("site-3"),
                savedAtMs = 1L,
                categoryIds = emptySet(),
                userComment = null,
            ),
        )
        store.removePoint("site-3")
        assertFalse(store.isSaved("site-3"))
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
