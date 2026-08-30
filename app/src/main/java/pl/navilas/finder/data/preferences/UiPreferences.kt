package pl.navilas.finder.data.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

enum class AppThemeMode {
    SYSTEM,
    AMBIENT_LIGHT,
    DAY,
    NIGHT,
}

enum class StartupMode {
    REMEMBER_LAST,
    SEARCH,
    MAP_BROWSE,
}

internal fun parseThemeMode(value: String?): AppThemeMode =
    AppThemeMode.entries.firstOrNull { it.name == value } ?: AppThemeMode.SYSTEM

internal fun parseStartupMode(value: String?): StartupMode =
    StartupMode.entries.firstOrNull { it.name == value } ?: StartupMode.REMEMBER_LAST

class UiPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var themeMode: AppThemeMode
        get() = parseThemeMode(prefs.getString(KEY_THEME_MODE, null))
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    var startupMode: StartupMode
        get() = parseStartupMode(prefs.getString(KEY_STARTUP_MODE, null))
        set(value) = prefs.edit().putString(KEY_STARTUP_MODE, value.name).apply()

    var keepScreenOnWhileTracking: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var ambientLightNightMode: Boolean
        get() = prefs.getBoolean(KEY_AMBIENT_LIGHT_NIGHT, false)
        set(value) = prefs.edit().putBoolean(KEY_AMBIENT_LIGHT_NIGHT, value).apply()

    var bdlRefreshSnoozeUntilMs: Long
        get() = prefs.getLong(KEY_BDL_REFRESH_SNOOZE, 0L)
        set(value) = prefs.edit().putLong(KEY_BDL_REFRESH_SNOOZE, value).apply()

    companion object {
        private const val PREFS_NAME = "navilas_ui"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_STARTUP_MODE = "startup_mode"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on_tracking"
        private const val KEY_AMBIENT_LIGHT_NIGHT = "ambient_light_night"
        private const val KEY_BDL_REFRESH_SNOOZE = "bdl_refresh_snooze_until"
    }
}

object AppThemeApplier {
    fun apply(mode: AppThemeMode, ambientLightNightMode: Boolean = false) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                AppThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                AppThemeMode.AMBIENT_LIGHT -> {
                    if (ambientLightNightMode) {
                        AppCompatDelegate.MODE_NIGHT_YES
                    } else {
                        AppCompatDelegate.MODE_NIGHT_NO
                    }
                }
                AppThemeMode.DAY -> AppCompatDelegate.MODE_NIGHT_NO
                AppThemeMode.NIGHT -> AppCompatDelegate.MODE_NIGHT_YES
            },
        )
    }
}
