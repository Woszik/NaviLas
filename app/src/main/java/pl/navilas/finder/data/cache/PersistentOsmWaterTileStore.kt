package pl.navilas.finder.data.cache

import org.json.JSONArray
import org.json.JSONObject
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.OsmWaterFeature
import java.io.File

/**
 * Disk + RAM cache of OSM water tiles (same ~5 km grid as highways).
 */
class PersistentOsmWaterTileStore(
    private val dir: File,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxDiskBytes: Long = MAX_DISK_BYTES,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val memory = LinkedHashMap<OsmRoadTileKey, List<OsmWaterFeature>>(16, 0.75f, true)
    private val indexFile = File(dir, INDEX_FILE)
    private val indexLock = Any()
    private var index: LinkedHashMap<String, DiskMeta> = LinkedHashMap(32, 0.75f, true)

    init {
        dir.mkdirs()
        loadIndex()
    }

    fun get(key: OsmRoadTileKey): List<OsmWaterFeature>? {
        synchronized(indexLock) {
            memory[key]
        }?.let { return it }
        val id = keyId(key)
        val meta = synchronized(indexLock) { index[id] } ?: return null
        if (nowMs() - meta.storedAtMs > ttlMs) {
            remove(key)
            return null
        }
        val features = readTile(key) ?: run {
            remove(key)
            return null
        }
        synchronized(indexLock) {
            memory[key] = features
            index.remove(id)
            index[id] = meta.copy(lastAccessMs = nowMs())
        }
        return features
    }

    fun put(key: OsmRoadTileKey, features: List<OsmWaterFeature>) {
        val now = nowMs()
        val bytes = estimateBytes(features)
        writeTile(key, features, now)
        synchronized(indexLock) {
            memory[key] = features
            index.remove(keyId(key))
            index[keyId(key)] = DiskMeta(key.latIndex, key.lonIndex, now, now, bytes)
            while (memory.size > MAX_MEMORY_TILES) {
                val eldest = memory.entries.first()
                memory.remove(eldest.key)
            }
        }
        persistIndex()
        prune()
    }

    private fun remove(key: OsmRoadTileKey) {
        synchronized(indexLock) {
            memory.remove(key)
            index.remove(keyId(key))
        }
        tileFile(key).delete()
        persistIndex()
    }

    private fun prune() {
        val now = nowMs()
        synchronized(indexLock) {
            index.filterValues { now - it.storedAtMs > ttlMs }.keys.toList().forEach { id ->
                val meta = index.remove(id) ?: return@forEach
                tileFile(OsmRoadTileKey(meta.latIndex, meta.lonIndex)).delete()
            }
            var total = index.values.sumOf { it.bytes }
            while (total > maxDiskBytes && index.isNotEmpty()) {
                val eldest = index.entries.minByOrNull { it.value.lastAccessMs } ?: break
                val meta = index.remove(eldest.key) ?: break
                tileFile(OsmRoadTileKey(meta.latIndex, meta.lonIndex)).delete()
                total -= meta.bytes
            }
        }
        persistIndex()
    }

    private fun loadIndex() {
        if (!indexFile.exists()) return
        runCatching {
            val arr = JSONObject(indexFile.readText()).optJSONArray("entries") ?: return
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val meta = DiskMeta(
                    item.getInt("latIndex"),
                    item.getInt("lonIndex"),
                    item.getLong("storedAtMs"),
                    item.optLong("lastAccessMs", item.getLong("storedAtMs")),
                    item.getLong("bytes"),
                )
                index[keyId(OsmRoadTileKey(meta.latIndex, meta.lonIndex))] = meta
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

    private fun writeTile(key: OsmRoadTileKey, features: List<OsmWaterFeature>, storedAtMs: Long) {
        dir.mkdirs()
        val arr = JSONArray()
        features.forEach { feature ->
            val geom = JSONArray()
            feature.geometry.forEach { p ->
                geom.put(JSONArray().put(p.latitude).put(p.longitude))
            }
            arr.put(
                JSONObject()
                    .put("id", feature.id)
                    .put("polygon", feature.polygon)
                    .put("geometry", geom),
            )
        }
        tileFile(key).writeText(
            JSONObject().put("storedAtMs", storedAtMs).put("features", arr).toString(),
        )
    }

    private fun readTile(key: OsmRoadTileKey): List<OsmWaterFeature>? {
        val file = tileFile(key)
        if (!file.exists()) return null
        return runCatching {
            val arr = JSONObject(file.readText()).optJSONArray("features") ?: return@runCatching emptyList()
            val out = ArrayList<OsmWaterFeature>(arr.length())
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val geomArr = item.getJSONArray("geometry")
                val geom = ArrayList<LatLon>(geomArr.length())
                for (g in 0 until geomArr.length()) {
                    val pair = geomArr.getJSONArray(g)
                    geom += LatLon(pair.getDouble(0), pair.getDouble(1))
                }
                OsmWaterFeature.of(item.getString("id"), item.optBoolean("polygon"), geom)?.let { out += it }
            }
            out
        }.getOrNull()
    }

    private fun tileFile(key: OsmRoadTileKey) = File(dir, "w_${key.latIndex}_${key.lonIndex}.json")

    private fun keyId(key: OsmRoadTileKey) = "${key.latIndex}|${key.lonIndex}"

    private data class DiskMeta(
        val latIndex: Int,
        val lonIndex: Int,
        val storedAtMs: Long,
        val lastAccessMs: Long,
        val bytes: Long,
    )

    companion object {
        const val DIR_NAME = "osm_water_tiles"
        const val INDEX_FILE = "index.json"
        const val MAX_MEMORY_TILES = 24
        const val MAX_DISK_BYTES: Long = 50L * 1024 * 1024
        const val DEFAULT_TTL_MS: Long = 30L * 24 * 60 * 60 * 1000

        fun fromAppFilesDir(filesDir: File) =
            PersistentOsmWaterTileStore(dir = File(filesDir, DIR_NAME))

        fun estimateBytes(features: List<OsmWaterFeature>): Long =
            features.sumOf { 80L + it.geometry.size * 16L }
    }
}
