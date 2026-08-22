package pl.navilas.finder.data.saved

import org.json.JSONArray
import org.json.JSONObject
import pl.navilas.finder.domain.RelatedBdlObject
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.SavedPoint
import pl.navilas.finder.domain.SavedPointCategory
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.domain.ZanocujStatus
import java.io.File
import java.util.UUID

class SavedPointsStore(
    private val file: File,
) {
    private val points = linkedMapOf<String, SavedPoint>()
    private val categories = linkedMapOf<String, SavedPointCategory>()

    init {
        loadFromDisk()
    }

    fun allPoints(): List<SavedPoint> = points.values.toList()

    fun getPoint(siteId: String): SavedPoint? = points[siteId]

    fun isSaved(siteId: String): Boolean = points.containsKey(siteId)

    fun allCategories(): List<SavedPointCategory> =
        categories.values.sortedWith(compareBy({ it.sortOrder }, { it.name }))

    fun savePoint(point: SavedPoint) {
        points[point.site.id] = point
        persistToDisk()
    }

    fun removePoint(siteId: String) {
        if (points.remove(siteId) != null) persistToDisk()
    }

    fun addCategory(name: String): SavedPointCategory {
        val trimmed = name.trim()
        require(trimmed.length >= 2) { "Category name too short" }
        val category = SavedPointCategory(
            id = "cat-${UUID.randomUUID()}",
            name = trimmed,
            sortOrder = categories.size,
        )
        categories[category.id] = category
        persistToDisk()
        return category
    }

    fun renameCategory(categoryId: String, newName: String) {
        val existing = categories[categoryId] ?: return
        val trimmed = newName.trim()
        if (trimmed.length < 2) return
        categories[categoryId] = existing.copy(name = trimmed)
        persistToDisk()
    }

    fun deleteCategory(categoryId: String) {
        if (!categories.containsKey(categoryId)) return
        categories.remove(categoryId)
        points.replaceAll { _, point ->
            point.copy(categoryIds = point.categoryIds - categoryId)
        }
        persistToDisk()
    }

    fun reorderCategory(categoryId: String, newSortOrder: Int) {
        val existing = categories[categoryId] ?: return
        categories[categoryId] = existing.copy(sortOrder = newSortOrder)
        persistToDisk()
    }

    private fun loadFromDisk() {
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            categories.clear()
            root.optJSONArray("categories")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val category = SavedPointCategory(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        sortOrder = item.optInt("sortOrder", i),
                    )
                    categories[category.id] = category
                }
            }
            points.clear()
            val pointsObj = root.optJSONObject("points") ?: return
            val keys = pointsObj.keys()
            while (keys.hasNext()) {
                val siteId = keys.next()
                parsePoint(pointsObj.getJSONObject(siteId))?.let { points[siteId] = it }
            }
        }
    }

    private fun persistToDisk() {
        file.parentFile?.mkdirs()
        val root = JSONObject()
        val categoriesArr = JSONArray()
        allCategories().forEach { category ->
            categoriesArr.put(
                JSONObject()
                    .put("id", category.id)
                    .put("name", category.name)
                    .put("sortOrder", category.sortOrder),
            )
        }
        root.put("categories", categoriesArr)
        val pointsObj = JSONObject()
        points.values.forEach { point ->
            pointsObj.put(point.site.id, toJson(point))
        }
        root.put("points", pointsObj)
        file.writeText(root.toString())
    }

    private fun toJson(point: SavedPoint): JSONObject {
        val site = point.site
        val features = JSONArray()
        site.features.forEach { features.put(it.name) }
        val related = JSONArray()
        site.relatedObjects.forEach { obj ->
            related.put(
                JSONObject()
                    .put("id", obj.id)
                    .put("layerId", obj.layerId)
                    .put("layerName", obj.layerName)
                    .put("name", obj.name)
                    .put("latitude", obj.latitude)
                    .put("longitude", obj.longitude)
                    .put("distanceMeters", obj.distanceMeters)
                    .put("typeCode", obj.typeCode),
            )
        }
        val categoryIds = JSONArray()
        point.categoryIds.forEach { categoryIds.put(it) }
        return JSONObject()
            .put("savedAtMs", point.savedAtMs)
            .put("categoryIds", categoryIds)
            .put("userComment", point.userComment)
            .put(
                "site",
                JSONObject()
                    .put("id", site.id)
                    .put("name", site.name)
                    .put("latitude", site.latitude)
                    .put("longitude", site.longitude)
                    .put("description", site.description)
                    .put("sourceLayerId", site.sourceLayerId)
                    .put("sourceLayerName", site.sourceLayerName)
                    .put("features", features)
                    .put("relatedObjects", related)
                    .put("zanocujStatus", site.zanocujStatus.name)
                    .put("distanceToZanocujBoundaryMeters", site.distanceToZanocujBoundaryMeters),
            )
    }

    private fun parsePoint(json: JSONObject): SavedPoint? {
        val siteJson = json.optJSONObject("site") ?: return null
        val features = buildSet {
            siteJson.optJSONArray("features")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching { add(SiteFeature.valueOf(arr.getString(i))) }
                }
            }
        }
        val related = buildList {
            siteJson.optJSONArray("relatedObjects")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    add(
                        RelatedBdlObject(
                            id = item.getString("id"),
                            layerId = item.getInt("layerId"),
                            layerName = item.getString("layerName"),
                            name = item.getString("name"),
                            latitude = item.getDouble("latitude"),
                            longitude = item.getDouble("longitude"),
                            distanceMeters = item.getDouble("distanceMeters"),
                            typeCode = item.optString("typeCode").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }
        val site = RestSite(
            id = siteJson.getString("id"),
            name = siteJson.getString("name"),
            latitude = siteJson.getDouble("latitude"),
            longitude = siteJson.getDouble("longitude"),
            description = siteJson.optString("description").takeIf { it.isNotBlank() },
            sourceLayerId = siteJson.getInt("sourceLayerId"),
            sourceLayerName = siteJson.getString("sourceLayerName"),
            features = features,
            relatedObjects = related,
            zanocujStatus = runCatching {
                ZanocujStatus.valueOf(siteJson.getString("zanocujStatus"))
            }.getOrDefault(ZanocujStatus.OUTSIDE_ZONE),
            distanceToZanocujBoundaryMeters = siteJson.optDouble("distanceToZanocujBoundaryMeters")
                .takeIf { !it.isNaN() },
        )
        return SavedPoint(
            site = site,
            savedAtMs = json.getLong("savedAtMs"),
            categoryIds = parseCategoryIds(json),
            userComment = json.optString("userComment").takeIf { it.isNotBlank() },
        )
    }

    private fun parseCategoryIds(json: JSONObject): Set<String> = buildSet {
        json.optJSONArray("categoryIds")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
        if (isEmpty()) {
            json.optString("categoryId").takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }

    companion object {
        const val FILE_NAME = "saved_points.json"

        fun fromAppFilesDir(filesDir: File): SavedPointsStore =
            SavedPointsStore(File(filesDir, FILE_NAME))
    }
}
