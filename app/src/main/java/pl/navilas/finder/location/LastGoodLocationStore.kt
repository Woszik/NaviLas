package pl.navilas.finder.location

import android.location.Location

/**
 * Remembers last successful fix for fast GPS path (Package A).
 */
class LastGoodLocationStore(
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private var stored: StoredLocation? = null

    fun record(location: Location, approximate: Boolean) {
        stored = StoredLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            approximate = approximate,
            recordedAtMs = nowMs(),
        )
    }

    fun getIfFresh(): StoredLocation? {
        val entry = stored ?: return null
        if (nowMs() - entry.recordedAtMs > maxAgeMs) {
            stored = null
            return null
        }
        return entry
    }

    fun clear() {
        stored = null
    }

    data class StoredLocation(
        val latitude: Double,
        val longitude: Double,
        val approximate: Boolean,
        val recordedAtMs: Long,
    )

    companion object {
        const val DEFAULT_MAX_AGE_MS: Long = 3L * 60 * 1000
    }
}
