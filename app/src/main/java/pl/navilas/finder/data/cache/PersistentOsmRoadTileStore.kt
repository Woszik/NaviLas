package pl.navilas.finder.data.cache

import org.json.JSONArray
import org.json.JSONObject
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.Road
import java.io.File

/**
 * Disk-backed OSM highway tile cache (lazy load into [OsmRoadTileCache] RAM).
 * Budget: [MAX_DISK_BYTES], TTL: [DEFAULT_TTL_MS], eviction: LRU by last access.
 */
class PersistentOsmRoadTileStore(
    private val dir: File,
    private val memory: OsmRoadTileCache = OsmRoadTileCache(
        maxEntries = OsmRoadTileCache.MAX_ENTRIES,
        maxBytes = OsmRoadTileCache.MAX_BYTES,
        ttlMs = OsmRoadTileCache.DEFAULT_TTL_MS,
    ),
    private val maxDiskBytes: Long = MAX_DISK_BYTES,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val indexFile = File(dir, INDEX_FILE)
    private val indexLock = Any()
    /** latIndex|lonIndex -> meta */
    private var index: LinkedHashMap<String, DiskMeta> = LinkedHashMap(64, 0.75f, true)

    init {
        dir.mkdirs()
        loadIndex()
        pruneExpiredAndOverBudget()
    }

    fun get(key: OsmRoadTileKey): List<Road>? {
        memory.get(key)?.let { return it }
        val id = keyId(key)
        val meta = synchronized(indexLock) { index[id] } ?: return null
        if (nowMs() - meta.storedAtMs > ttlMs) {
            removeDiskEntry(key)
            return null
        }
        val roads = readTileFile(key) ?: run {
            removeDiskEntry(key)
            return null
        }
        touchAccess(key, meta)
        memory.put(key, roads)
        return roads
    }

    fun put(key: OsmRoadTileKey, roads: List<Road>) {
        memory.put(key, roads)
        val bytes = OsmRoadTileCache.estimateBytes(roads)
        val now = nowMs()
        writeTileFile(key, roads, now)
        synchronized(indexLock) {
            index.remove(keyId(key))
            index[keyId(key)] = DiskMeta(
                latIndex = key.latIndex,
                lonIndex = key.lonIndex,
                storedAtMs = now,
                lastAccessMs = now,
                bytes = bytes,
            )
        }
        persistIndex()
        pruneExpiredAndOverBudget()
    }

    fun clear() {
        memory.clear()
        synchronized(indexLock) {
            index.keys.toList().forEach { id ->
                val meta = index[id] ?: return@forEach
                tileFile(OsmRoadTileKey(meta.latIndex, meta.lonIndex)).delete()
            }
            index.clear()
        }
        if (indexFile.exists()) indexFile.delete()
    }

    fun diskEntryCount(): Int = synchronized(indexLock) { index.size }

    fun diskBytesHeld(): Long = synchronized(indexLock) { index.values.sumOf { it.bytes } }

    private fun touchAccess(key: OsmRoadTileKey, meta: DiskMeta) {
        val now = nowMs()
        synchronized(indexLock) {
            index.remove(keyId(key))
            index[keyId(key)] = meta.copy(lastAccessMs = now)
        }
        persistIndex()
    }

    private fun pruneExpiredAndOverBudget() {
        val now = nowMs()
        synchronized(indexLock) {
            val expired = index.filterValues { now - it.storedAtMs > ttlMs }.keys.toList()
            expired.forEach { id ->
                val meta = index.remove(id) ?: return@forEach
                tileFile(OsmRoadTileKey(meta.latIndex, meta.lonIndex)).delete()
            }
            var total = index.values.sumOf { it.bytes }
            while (total > maxDiskBytes && index.isNotEmpty()) {
                val eldestId = index.entries.minByOrNull { it.value.lastAccessMs }?.key ?: break
                val meta = index.remove(eldestId) ?: break
                tileFile(OsmRoadTileKey(meta.latIndex, meta.lonIndex)).delete()
                total -= meta.bytes
            }
        }
        persistIndex()
    }

    private fun removeDiskEntry(key: OsmRoadTileKey) {
        synchronized(indexLock) { index.remove(keyId(key)) }
        tileFile(key).delete()
        persistIndex()
    }

    private fun loadIndex() {
        if (!indexFile.exists()) return
        runCatching {
            val root = JSONObject(indexFile.readText())
            val arr = root.optJSONArray("entries") ?: return
            val loaded = LinkedHashMap<String, DiskMeta>(arr.length().coerceAtLeast(16), 0.75f, true)
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val meta = DiskMeta(
                    latIndex = item.getInt("latIndex"),
                    lonIndex = item.getInt("lonIndex"),
                    storedAtMs = item.getLong("storedAtMs"),
                    lastAccessMs = item.optLong("lastAccessMs", item.getLong("storedAtMs")),
                    bytes = item.getLong("bytes"),
                )
                loaded[keyId(OsmRoadTileKey(meta.latIndex, meta.lonIndex))] = meta
            }
            // Restore LRU order by lastAccess ascending then re-insert for access-order map.
            val ordered = loaded.entries.sortedBy { it.value.lastAccessMs }
            synchronized(indexLock) {
                index.clear()
                ordered.forEach { (id, meta) -> index[id] = meta }
            }
        }
    }

    private fun persistIndex() {
        dir.mkdirs()
        val arr = JSONArray()
        synchronized(indexLock) {
            index.values.forEach { meta ->
                arr.put(
                    JSONObject()
                        .put("latIndex", meta.latIndex)
                        .put("lonIndex", meta.lonIndex)
                        .put("storedAtMs", meta.storedAtMs)
                        .put("lastAccessMs", meta.lastAccessMs)
                        .put("bytes", meta.bytes),
                )
            }
        }
        indexFile.writeText(JSONObject().put("entries", arr).toString())
    }

    private fun writeTileFile(key: OsmRoadTileKey, roads: List<Road>, storedAtMs: Long) {
        dir.mkdirs()
        val arr = JSONArray()
        roads.forEach { road ->
            val geom = JSONArray()
            road.geometry.forEach { p ->
                geom.put(JSONArray().put(p.latitude).put(p.longitude))
            }
            arr.put(
                JSONObject()
                    .put("id", road.id)
                    .put("type", road.type)
                    .put("access", road.access)
                    .put("motorVehicle", road.motorVehicle)
                    .put("motorcycle", road.motorcycle)
                    .put("vehicle", road.vehicle)
                    .put("surface", road.surface)
                    .put("tracktype", road.tracktype)
                    .put("name", road.name)
                    .put("geometry", geom),
            )
        }
        tileFile(key).writeText(
            JSONObject()
                .put("storedAtMs", storedAtMs)
                .put("roads", arr)
                .toString(),
        )
    }

    private fun readTileFile(key: OsmRoadTileKey): List<Road>? {
        val file = tileFile(key)
        if (!file.exists()) return null
        return runCatching {
            val root = JSONObject(file.readText())
            val arr = root.getJSONArray("roads")
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val geomArr = item.optJSONArray("geometry") ?: JSONArray()
                    val geometry = buildList {
                        for (g in 0 until geomArr.length()) {
                            val pair = geomArr.getJSONArray(g)
                            add(LatLon(pair.getDouble(0), pair.getDouble(1)))
                        }
                    }
                    add(
                        Road(
                            id = item.getString("id"),
                            type = item.getString("type"),
                            access = item.optNullableString("access"),
                            motorVehicle = item.optNullableString("motorVehicle"),
                            motorcycle = item.optNullableString("motorcycle"),
                            vehicle = item.optNullableString("vehicle"),
                            surface = item.optNullableString("surface"),
                            tracktype = item.optNullableString("tracktype"),
                            name = item.optNullableString("name"),
                            geometry = geometry,
                        ),
                    )
                }
            }
        }.getOrNull()
    }

    private fun tileFile(key: OsmRoadTileKey): File =
        File(dir, "t_${key.latIndex}_${key.lonIndex}.json")

    private fun keyId(key: OsmRoadTileKey): String = "${key.latIndex}|${key.lonIndex}"

    private data class DiskMeta(
        val latIndex: Int,
        val lonIndex: Int,
        val storedAtMs: Long,
        val lastAccessMs: Long,
        val bytes: Long,
    )

    companion object {
        const val DIR_NAME = "osm_road_tiles"
        const val INDEX_FILE = "index.json"
        const val MAX_DISK_BYTES: Long = 100L * 1024 * 1024
        const val DEFAULT_TTL_MS: Long = 30L * 24 * 60 * 60 * 1000

        fun fromAppFilesDir(filesDir: File): PersistentOsmRoadTileStore =
            PersistentOsmRoadTileStore(dir = File(filesDir, DIR_NAME))
    }
}

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    val value = optString(name)
    return value.takeIf { it.isNotBlank() && it != "null" }
}
