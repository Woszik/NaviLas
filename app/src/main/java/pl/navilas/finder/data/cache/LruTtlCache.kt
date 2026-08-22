package pl.navilas.finder.data.cache

/**
 * LRU cache with optional per-entry TTL (milliseconds).
 */
class LruTtlCache<K, V>(
    private val maxEntries: Int,
    private val ttlMs: Long? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val entries = object : LinkedHashMap<K, Entry<V>>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean {
            return size > maxEntries
        }
    }

    fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (ttlMs != null && nowMs() - entry.storedAtMs > ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    fun put(key: K, value: V) {
        entries[key] = Entry(value = value, storedAtMs = nowMs())
    }

    fun clear() {
        entries.clear()
    }

    fun size(): Int = entries.size

    private data class Entry<V>(
        val value: V,
        val storedAtMs: Long,
    )
}
