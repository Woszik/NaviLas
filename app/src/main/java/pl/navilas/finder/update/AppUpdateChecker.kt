package pl.navilas.finder.update

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class AppUpdateChecker(
    private val manifestUrl: String,
    private val client: OkHttpClient = defaultClient(),
) {
    @Throws(IOException::class)
    fun fetchManifest(nowMs: Long = System.currentTimeMillis()): AppUpdateManifest {
        val request = Request.Builder()
            .url(manifestFetchUrl(manifestUrl, nowMs))
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Manifest HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty manifest body")
            return AppUpdateManifest.parse(body)
        }
    }

    companion object {
        /** Bypass GitHub raw CDN cache so new releases are visible immediately. */
        fun manifestFetchUrl(baseUrl: String, cacheBustMs: Long): String {
            val separator = if ('?' in baseUrl) '&' else '?'
            return "$baseUrl${separator}t=$cacheBustMs"
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
