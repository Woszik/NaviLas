package pl.navilas.finder.update

import org.json.JSONObject

enum class UpdateTrack {
    NIGHTLY,
    BETA,
    FINAL,
}

internal fun parseUpdateTrack(value: String?, fallback: UpdateTrack): UpdateTrack =
    UpdateTrack.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: fallback

data class AppUpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String,
    val minVersionCode: Int,
    val minAndroidSdk: Int,
    val publishedAt: String?,
    val track: UpdateTrack = UpdateTrack.BETA,
) {
    fun isMandatory(currentVersionCode: Int): Boolean =
        currentVersionCode < minVersionCode

    companion object {
        fun parse(json: JSONObject, fallbackTrack: UpdateTrack = UpdateTrack.BETA): AppUpdateManifest {
            val versionCode = json.getInt("versionCode")
            val versionName = json.getString("versionName")
            val apkUrl = json.getString("apkUrl")
            val sha256 = json.getString("sha256").lowercase()
            require(sha256.matches(Regex("[0-9a-f]{64}"))) {
                "sha256 must be 64 hex characters"
            }
            return AppUpdateManifest(
                versionCode = versionCode,
                versionName = versionName,
                apkUrl = apkUrl,
                sha256 = sha256,
                releaseNotes = json.optString("releaseNotes", ""),
                minVersionCode = json.optInt("minVersionCode", 0),
                minAndroidSdk = json.optInt("minAndroidSdk", 0),
                publishedAt = json.optString("publishedAt").takeIf { it.isNotBlank() },
                track = parseUpdateTrack(json.optString("channel").takeIf { it.isNotBlank() }, fallbackTrack),
            )
        }

        fun parse(jsonText: String, fallbackTrack: UpdateTrack = UpdateTrack.BETA): AppUpdateManifest =
            parse(JSONObject(jsonText), fallbackTrack)
    }
}

data class AppUpdateOffer(
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val mandatory: Boolean,
    val apkUrl: String,
    val sha256: String,
    val track: UpdateTrack = UpdateTrack.BETA,
)

object AppUpdateLogic {
    fun evaluateOffer(
        manifest: AppUpdateManifest,
        currentVersionCode: Int,
        dismissedVersionCode: Int?,
    ): AppUpdateOffer? {
        if (manifest.versionCode <= currentVersionCode) return null
        val mandatory = manifest.isMandatory(currentVersionCode)
        if (!mandatory && dismissedVersionCode == manifest.versionCode) return null
        return AppUpdateOffer(
            versionName = manifest.versionName,
            versionCode = manifest.versionCode,
            releaseNotes = manifest.releaseNotes,
            mandatory = mandatory,
            apkUrl = manifest.apkUrl,
            sha256 = manifest.sha256,
            track = manifest.track,
        )
    }

    fun evaluateBestOffer(
        manifests: List<AppUpdateManifest>,
        currentVersionCode: Int,
        dismissedVersionCode: Int?,
    ): AppUpdateOffer? {
        val best = manifests.maxByOrNull { it.versionCode } ?: return null
        return evaluateOffer(best, currentVersionCode, dismissedVersionCode)
    }
}
