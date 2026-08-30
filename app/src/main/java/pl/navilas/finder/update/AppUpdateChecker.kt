package pl.navilas.finder.update

import okhttp3.OkHttpClient
import pl.navilas.finder.data.preferences.UpdateChannelPreference
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class AppUpdateChecker(
    private val manifestsByTrack: Map<UpdateTrack, String>,
    private val client: OkHttpClient = defaultClient(),
) {
    constructor(
        manifestUrl: String,
        client: OkHttpClient = defaultClient(),
    ) : this(
        manifestsByTrack = mapOf(UpdateTrack.BETA to manifestUrl),
        client = client,
    )

    @Throws(IOException::class)
    fun fetchManifest(nowMs: Long = System.currentTimeMillis()): AppUpdateManifest {
        val url = manifestsByTrack[UpdateTrack.BETA]
            ?: manifestsByTrack.values.firstOrNull { it.isNotBlank() }
            ?: throw IOException("Empty manifest URL")
        return fetchOne(url, UpdateTrack.BETA, nowMs)
            ?: throw IOException("Manifest HTTP 404")
    }

    @Throws(IOException::class)
    fun fetchEligibleManifests(
        preference: UpdateChannelPreference,
        nowMs: Long = System.currentTimeMillis(),
    ): List<AppUpdateManifest> {
        val found = mutableListOf<AppUpdateManifest>()
        var lastError: IOException? = null
        for (track in preference.tracks()) {
            val url = manifestsByTrack[track].orEmpty()
            if (url.isBlank()) continue
            try {
                val manifest = fetchOne(url, track, nowMs) ?: continue
                found += manifest
            } catch (e: IOException) {
                lastError = e
            }
        }
        if (found.isEmpty() && lastError != null) throw lastError
        return found
    }

    @Throws(IOException::class)
    private fun fetchOne(
        url: String,
        fallbackTrack: UpdateTrack,
        nowMs: Long,
    ): AppUpdateManifest? {
        val request = Request.Builder()
            .url(manifestFetchUrl(url, nowMs))
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) {
                throw IOException("Manifest HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty manifest body")
            return AppUpdateManifest.parse(body, fallbackTrack)
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
