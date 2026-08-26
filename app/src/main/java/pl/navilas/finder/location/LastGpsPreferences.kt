package pl.navilas.finder.location

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists last successful GPS fix across app restarts (for startup map centering).
 */
class LastGpsPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(latitude: Double, longitude: Double, approximate: Boolean, recordedAtMs: Long) {
        prefs.edit()
            .putBoolean(KEY_HAS, true)
            .putLong(KEY_LAT_BITS, java.lang.Double.doubleToRawLongBits(latitude))
            .putLong(KEY_LON_BITS, java.lang.Double.doubleToRawLongBits(longitude))
            .putBoolean(KEY_APPROX, approximate)
            .putLong(KEY_AT, recordedAtMs)
            .apply()
    }

    fun load(): LastGoodLocationStore.StoredLocation? {
        if (!prefs.getBoolean(KEY_HAS, false)) return null
        return LastGoodLocationStore.StoredLocation(
            latitude = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAT_BITS, 0L)),
            longitude = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LON_BITS, 0L)),
            approximate = prefs.getBoolean(KEY_APPROX, true),
            recordedAtMs = prefs.getLong(KEY_AT, 0L),
        )
    }

    companion object {
        private const val PREFS_NAME = "last_gps"
        private const val KEY_HAS = "has"
        private const val KEY_LAT_BITS = "lat_bits"
        private const val KEY_LON_BITS = "lon_bits"
        private const val KEY_APPROX = "approx"
        private const val KEY_AT = "at_ms"
    }
}
