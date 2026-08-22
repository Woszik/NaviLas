package pl.navilas.finder.update

import android.content.Context
import android.content.SharedPreferences

class AppUpdatePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastCheckAtMs: Long
        get() = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK_AT, value).apply()

    var dismissedVersionCode: Int?
        get() {
            val value = prefs.getInt(KEY_DISMISSED_VERSION, NO_DISMISS)
            return if (value == NO_DISMISS) null else value
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_DISMISSED_VERSION)
                } else {
                    putInt(KEY_DISMISSED_VERSION, value)
                }
            }.apply()
        }

    companion object {
        private const val PREFS_NAME = "app_update"
        private const val KEY_LAST_CHECK_AT = "last_check_at_ms"
        private const val KEY_DISMISSED_VERSION = "dismissed_version_code"
        private const val NO_DISMISS = -1
        const val CHECK_INTERVAL_MS: Long = 24L * 60L * 60L * 1000L
    }
}
