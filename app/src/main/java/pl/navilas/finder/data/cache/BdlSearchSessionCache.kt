package pl.navilas.finder.data.cache

import pl.navilas.finder.data.bdl.RestSearchBundle
import kotlin.math.round

/**
 * Session cache for enriched BDL search results (origin + radius + data source).
 */
class BdlSearchSessionCache(
    maxEntries: Int = MAX_ENTRIES,
    ttlMs: Long = SESSION_TTL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val cache = LruTtlCache<BdlSearchCacheKey, RestSearchBundle>(
        maxEntries = maxEntries,
        ttlMs = ttlMs,
        nowMs = nowMs,
    )

    fun get(key: BdlSearchCacheKey): RestSearchBundle? = cache.get(key)

    fun put(key: BdlSearchCacheKey, bundle: RestSearchBundle) {
        cache.put(key, bundle)
    }

    fun clear() {
        cache.clear()
    }

    companion object {
        const val MAX_ENTRIES = 5
        /** Online/session TTL — BDL updates are infrequent during a forest trip. */
        const val SESSION_TTL_MS: Long = 30L * 60 * 1000

        /** ~100 m grid for cache key stability. */
        fun roundCoord(value: Double): Double = round(value * 1000.0) / 1000.0

        fun key(
            latitude: Double,
            longitude: Double,
            radiusKm: Double,
            offlineDataVersion: Long,
        ): BdlSearchCacheKey = BdlSearchCacheKey(
            lat = roundCoord(latitude),
            lon = roundCoord(longitude),
            radiusKm = radiusKm,
            offlineDataVersion = offlineDataVersion,
        )
    }
}

data class BdlSearchCacheKey(
    val lat: Double,
    val lon: Double,
    val radiusKm: Double,
    /** 0 = online; else [BdlOfflineStore.downloadedAt]. */
    val offlineDataVersion: Long,
)
