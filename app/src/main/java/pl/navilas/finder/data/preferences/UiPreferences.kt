package pl.navilas.finder.data.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import pl.navilas.finder.update.UpdateTrack

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

/** Map tiles while the app UI is in night mode. Day mode always uses the light style. */
enum class NightMapStyle {
    LIGHT,
    DARK,
}

/** GitHub in-app updates: chosen floor and everything more stable above it. */
enum class UpdateChannelPreference {
    NIGHTLY,
    BETA,
    FINAL,
    ;

    fun tracks(): List<UpdateTrack> = when (this) {
        NIGHTLY -> listOf(UpdateTrack.NIGHTLY, UpdateTrack.BETA, UpdateTrack.FINAL)
        BETA -> listOf(UpdateTrack.BETA, UpdateTrack.FINAL)
        FINAL -> listOf(UpdateTrack.FINAL)
    }
}

internal fun parseThemeMode(value: String?): AppThemeMode =
    AppThemeMode.entries.firstOrNull { it.name == value } ?: AppThemeMode.SYSTEM

internal fun parseStartupMode(value: String?): StartupMode =
    StartupMode.entries.firstOrNull { it.name == value } ?: StartupMode.REMEMBER_LAST

internal fun parseUpdateChannelPreference(value: String?): UpdateChannelPreference =
    UpdateChannelPreference.entries.firstOrNull { it.name == value } ?: UpdateChannelPreference.BETA

internal fun parseNightMapStyle(value: String?): NightMapStyle =
    NightMapStyle.entries.firstOrNull { it.name == value } ?: NightMapStyle.LIGHT

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

    /** Base map when app chrome is night: light (Liberty) by default, or OpenFreeMap Dark. */
    var nightMapStyle: NightMapStyle
        get() = parseNightMapStyle(prefs.getString(KEY_NIGHT_MAP_STYLE, null))
        set(value) = prefs.edit().putString(KEY_NIGHT_MAP_STYLE, value.name).apply()

    var bdlRefreshSnoozeUntilMs: Long
        get() = prefs.getLong(KEY_BDL_REFRESH_SNOOZE, 0L)
        set(value) = prefs.edit().putLong(KEY_BDL_REFRESH_SNOOZE, value).apply()

    var entryBanRefreshSnoozeUntilMs: Long
        get() = prefs.getLong(KEY_ENTRY_BAN_REFRESH_SNOOZE, 0L)
        set(value) = prefs.edit().putLong(KEY_ENTRY_BAN_REFRESH_SNOOZE, value).apply()

    var updateChannel: UpdateChannelPreference
        get() = parseUpdateChannelPreference(prefs.getString(KEY_UPDATE_CHANNEL, null))
        set(value) = prefs.edit().putString(KEY_UPDATE_CHANNEL, value.name).apply()

    var pendingOsmAndSetup: Boolean
        get() = prefs.getBoolean(KEY_PENDING_OSMAND_SETUP, false)
        set(value) = prefs.edit().putBoolean(KEY_PENDING_OSMAND_SETUP, value).apply()

    companion object {
        private const val PREFS_NAME = "navilas_ui"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_STARTUP_MODE = "startup_mode"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on_tracking"
        private const val KEY_AMBIENT_LIGHT_NIGHT = "ambient_light_night"
        private const val KEY_NIGHT_MAP_STYLE = "night_map_style"
        private const val KEY_BDL_REFRESH_SNOOZE = "bdl_refresh_snooze_until"
        private const val KEY_ENTRY_BAN_REFRESH_SNOOZE = "entry_ban_refresh_snooze_until"
        private const val KEY_UPDATE_CHANNEL = "update_channel"
        private const val KEY_PENDING_OSMAND_SETUP = "pending_osmand_setup"
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
