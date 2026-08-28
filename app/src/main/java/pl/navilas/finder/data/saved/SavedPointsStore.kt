package pl.navilas.finder.data.saved

import org.json.JSONArray
import org.json.JSONObject
import pl.navilas.finder.domain.NaturalSpringCertainty
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

    fun writeSnapshotTo(root: JSONObject) {
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
    }

    fun parseSnapshot(root: JSONObject): SavedPointsBackupParseResult {
        val parsedCategories = linkedMapOf<String, SavedPointCategory>()
        root.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val name = item.optString("name").trim()
                val id = item.optString("id").trim()
                if (id.isBlank() || name.length < 2 || name.length > 80) continue
                parsedCategories[id] = SavedPointCategory(
                    id = id,
                    name = name,
                    sortOrder = item.optInt("sortOrder", i),
                )
            }
        }
        val parsedPoints = linkedMapOf<String, SavedPoint>()
        var skippedPoints = 0
        val pointsObj = root.optJSONObject("points")
        if (pointsObj != null) {
            val keys = pointsObj.keys()
            while (keys.hasNext()) {
                val siteId = keys.next()
                val point = parsePoint(pointsObj.getJSONObject(siteId))
                if (point == null) {
                    skippedPoints++
                    continue
                }
                if (point.site.id != siteId) {
                    skippedPoints++
                    continue
                }
                parsedPoints[siteId] = point
            }
        }
        return SavedPointsBackupParseResult.Success(
            snapshot = SavedPointsBackupSnapshot(
                categories = parsedCategories.values.toList(),
                points = parsedPoints.values.toList(),
            ),
            skippedPoints = skippedPoints,
        )
    }

    fun importSnapshot(snapshot: SavedPointsBackupSnapshot, mode: SavedPointsImportMode): SavedPointsImportResult {
        if (mode == SavedPointsImportMode.REPLACE) {
            categories.clear()
            points.clear()
        }
        var addedCategories = 0
        snapshot.categories.forEach { imported ->
            if (imported.id !in categories) {
                categories[imported.id] = imported
                addedCategories++
            }
        }
        var addedPoints = 0
        var updatedPoints = 0
        var skippedPoints = 0
        snapshot.points.forEach { imported ->
            val siteId = imported.site.id
            val existing = points[siteId]
            when {
                existing == null -> {
                    val validCategoryIds = imported.categoryIds.filter { it in categories }.toSet()
                    points[siteId] = imported.copy(categoryIds = validCategoryIds)
                    addedPoints++
                }
                mode == SavedPointsImportMode.REPLACE -> {
                    val validCategoryIds = imported.categoryIds.filter { it in categories }.toSet()
                    points[siteId] = imported.copy(categoryIds = validCategoryIds)
                    updatedPoints++
                }
                else -> skippedPoints++
            }
        }
        persistToDisk()
        return SavedPointsImportResult(
            addedPoints = addedPoints,
            updatedPoints = updatedPoints,
            skippedPoints = skippedPoints,
            addedCategories = addedCategories,
        )
    }

    private fun loadFromDisk() {
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            categories.clear()
            points.clear()
            when (val parsed = parseSnapshot(root)) {
                is SavedPointsBackupParseResult.Success -> {
                    parsed.snapshot.categories.forEach { categories[it.id] = it }
                    parsed.snapshot.points.forEach { points[it.site.id] = it }
                }
                is SavedPointsBackupParseResult.Failure -> Unit
            }
        }
    }

    private fun persistToDisk() {
        file.parentFile?.mkdirs()
        val root = JSONObject()
        writeSnapshotTo(root)
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
                    .put("distanceToZanocujBoundaryMeters", site.distanceToZanocujBoundaryMeters)
                    .put(
                        "naturalSpring",
                        site.naturalSpring?.name,
                    ),
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
            naturalSpring = siteJson.optString("naturalSpring").takeIf { it.isNotBlank() }?.let {
                runCatching { NaturalSpringCertainty.valueOf(it) }.getOrNull()
            },
        )
        val comment = json.optString("userComment").takeIf { it.isNotBlank() }
        if (comment != null && comment.length > 2000) return null
        return SavedPoint(
            site = site,
            savedAtMs = json.optLong("savedAtMs", System.currentTimeMillis()),
            categoryIds = parseCategoryIds(json),
            userComment = comment,
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
