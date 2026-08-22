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
    fun fetchManifest(): AppUpdateManifest {
        val request = Request.Builder()
            .url(manifestUrl)
            .header("Accept", "application/json")
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
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
