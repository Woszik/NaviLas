package pl.navilas.finder.update

import org.json.JSONObject

data class AppUpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String,
    val minVersionCode: Int,
    val minAndroidSdk: Int,
    val publishedAt: String?,
) {
    fun isMandatory(currentVersionCode: Int): Boolean =
        currentVersionCode < minVersionCode

    companion object {
        fun parse(json: JSONObject): AppUpdateManifest {
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
            )
        }

        fun parse(jsonText: String): AppUpdateManifest = parse(JSONObject(jsonText))
    }
}

data class AppUpdateOffer(
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val mandatory: Boolean,
    val apkUrl: String,
    val sha256: String,
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
        )
    }
}
