package pl.navilas.finder.data.osm

import java.util.Locale

/**
 * In-memory cache for Nominatim locality lookups.
 */
class LocalityGeocodeCache(
    private val maxEntries: Int = 100,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val entries = object : LinkedHashMap<String, Entry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
            return size > maxEntries
        }
    }

    fun normalizeKey(query: String): String =
        query.trim().lowercase(Locale.forLanguageTag("pl-PL"))

    fun get(query: String): GeocodedPlace? {
        val key = normalizeKey(query)
        val entry = entries[key] ?: return null
        if (isExpired(entry)) {
            entries.remove(key)
            return null
        }
        return entry.place
    }

    fun put(query: String, place: GeocodedPlace) {
        val key = normalizeKey(query)
        if (key.length < 2) return
        entries[key] = Entry(place = place, storedAtMs = nowMs())
    }

    fun restore(key: String, place: GeocodedPlace, storedAtMs: Long) {
        if (key.length < 2) return
        val entry = Entry(place = place, storedAtMs = storedAtMs)
        if (isExpired(entry)) return
        entries[key] = entry
    }

    fun snapshotNonExpired(): Map<String, LocalityCacheSnapshot> {
        purgeExpired()
        return entries.mapValues { (_, entry) ->
            LocalityCacheSnapshot(place = entry.place, storedAtMs = entry.storedAtMs)
        }
    }

    fun clear() {
        entries.clear()
    }

    private fun isExpired(entry: Entry): Boolean = nowMs() - entry.storedAtMs > ttlMs

    private fun purgeExpired() {
        entries.entries.removeIf { isExpired(it.value) }
    }

    private data class Entry(
        val place: GeocodedPlace,
        val storedAtMs: Long,
    )

    companion object {
        const val DEFAULT_TTL_MS: Long = 30L * 24 * 60 * 60 * 1000
    }
}

data class LocalityCacheSnapshot(
    val place: GeocodedPlace,
    val storedAtMs: Long,
)
