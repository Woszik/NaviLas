package pl.navilas.finder

import android.app.Application
import pl.navilas.finder.data.preferences.AppThemeApplier
import pl.navilas.finder.data.preferences.UiPreferences

class NaviLasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val preferences = UiPreferences(this)
        AppThemeApplier.apply(preferences.themeMode, preferences.ambientLightNightMode)
    }
}
