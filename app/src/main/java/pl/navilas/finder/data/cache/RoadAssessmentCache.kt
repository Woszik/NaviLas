package pl.navilas.finder.data.cache

import pl.navilas.finder.domain.RoadAssessment

/**
 * Per-site OSM road analysis cache (moto profile).
 */
class RoadAssessmentCache(
    maxEntries: Int = MAX_ENTRIES,
    ttlMs: Long = DEFAULT_TTL_MS,
    nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val cache = LruTtlCache<String, RoadAssessment>(
        maxEntries = maxEntries,
        ttlMs = ttlMs,
        nowMs = nowMs,
    )

    fun get(siteId: String): RoadAssessment? = cache.get(siteId)

    fun put(siteId: String, assessment: RoadAssessment) {
        cache.put(siteId, assessment)
    }

    fun putAll(assessments: Map<String, RoadAssessment>) {
        assessments.forEach { (id, assessment) -> put(id, assessment) }
    }

    fun clear() {
        cache.clear()
    }

    companion object {
        const val MAX_ENTRIES = 2000
        const val DEFAULT_TTL_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}
