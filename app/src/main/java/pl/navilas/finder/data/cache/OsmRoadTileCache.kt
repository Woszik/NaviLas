package pl.navilas.finder.data.cache

import pl.navilas.finder.domain.Road

/**
 * LRU + TTL cache for parsed OSM highways per map tile.
 */
class OsmRoadTileCache(
    maxEntries: Int = MAX_ENTRIES,
    maxBytes: Long = MAX_BYTES,
    ttlMs: Long = DEFAULT_TTL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val ttlMsLimit = ttlMs
    private val entries = object : LinkedHashMap<OsmRoadTileKey, Entry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<OsmRoadTileKey, Entry>?): Boolean {
            return size > maxEntries
        }
    }
    private var totalBytes: Long = 0L
    private val maxBytesLimit = maxBytes

    fun get(key: OsmRoadTileKey): List<Road>? {
        val entry = entries[key] ?: return null
        if (nowMs() - entry.storedAtMs > ttlMsLimit) {
            removeEntry(key)
            return null
        }
        return entry.roads
    }

    fun put(key: OsmRoadTileKey, roads: List<Road>) {
        removeEntry(key)
        val bytes = estimateBytes(roads)
        entries[key] = Entry(roads = roads, storedAtMs = nowMs(), bytes = bytes)
        totalBytes += bytes
        evictUntilWithinBudget()
    }

    fun clear() {
        entries.clear()
        totalBytes = 0L
    }

    fun size(): Int = entries.size

    fun totalBytesHeld(): Long = totalBytes

    private fun removeEntry(key: OsmRoadTileKey) {
        val removed = entries.remove(key) ?: return
        totalBytes -= removed.bytes
    }

    private fun evictUntilWithinBudget() {
        while (totalBytes > maxBytesLimit && entries.isNotEmpty()) {
            val eldest = entries.entries.first()
            removeEntry(eldest.key)
        }
    }

    private data class Entry(
        val roads: List<Road>,
        val storedAtMs: Long,
        val bytes: Long,
    )

    companion object {
        /** Hot in-memory layer (disk budget is larger — see [PersistentOsmRoadTileStore]). */
        const val MAX_ENTRIES = 40
        const val MAX_BYTES: Long = 20L * 1024 * 1024
        const val DEFAULT_TTL_MS: Long = 30L * 24 * 60 * 60 * 1000

        fun estimateBytes(roads: List<Road>): Long = roads.sumOf { road ->
            200L + road.geometry.size * 32L
        }
    }
}
