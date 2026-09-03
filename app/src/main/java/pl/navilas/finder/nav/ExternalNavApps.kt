package pl.navilas.finder.nav

import android.content.pm.PackageManager
import android.os.Build

/** Installed third-party navigation apps used by NAWIGUJ. */
object ExternalNavApps {
    const val OSMAND_PLUS = "net.osmand.plus"
    const val OSMAND_FREE = "net.osmand"
    const val CRUISER = "gr.talent.cruiser"
    const val OSMAND_PLUS_GEO_ACTIVITY = "net.osmand.plus.activities.search.GeoIntentActivity"
    const val OSMAND_PROFILES_ASSET = "osmand/NaviLas_osmand_moto_profiles.osf"
    const val OSMAND_PROFILES_FILE = "NaviLas_osmand_moto_profiles.osf"

    fun isInstalled(packageManager: PackageManager, packageName: String): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun installedOsmAndPackage(packageManager: PackageManager): String? = when {
        isInstalled(packageManager, OSMAND_PLUS) -> OSMAND_PLUS
        isInstalled(packageManager, OSMAND_FREE) -> OSMAND_FREE
        else -> null
    }

    fun isCruiserInstalled(packageManager: PackageManager): Boolean =
        isInstalled(packageManager, CRUISER)

    fun playStoreHttps(packageName: String): String =
        "https://play.google.com/store/apps/details?id=$packageName"

    fun playStoreMarketUri(packageName: String): String =
        "market://details?id=$packageName"
}
