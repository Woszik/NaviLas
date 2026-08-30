package pl.navilas.finder.data.osm

import org.json.JSONObject
import java.io.File

/**
 * Locality geocode cache: in-memory LRU + JSON persistence across app restarts.
 */
class PersistentLocalityGeocodeStore(
    private val file: File,
    private val memory: LocalityGeocodeCache = LocalityGeocodeCache(),
) {
    init {
        loadFromDisk()
    }

    fun get(query: String): GeocodedPlace? = memory.get(query)

    fun put(query: String, place: GeocodedPlace) {
        memory.put(query, place)
        persistToDisk()
    }

    fun clear() {
        memory.clear()
        if (file.exists()) file.delete()
    }

    private fun loadFromDisk() {
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = root.getJSONObject(key)
                memory.restore(
                    key = key,
                    place = GeocodedPlace(
                        latitude = item.getDouble("lat"),
                        longitude = item.getDouble("lon"),
                        displayName = item.getString("displayName"),
                        voivodeship = item.optString("voivodeship").trim().ifBlank { null },
                        county = item.optString("county").trim().ifBlank { null },
                    ),
                    storedAtMs = item.getLong("storedAtMs"),
                )
            }
        }
    }

    private fun persistToDisk() {
        file.parentFile?.mkdirs()
        val root = JSONObject()
        memory.snapshotNonExpired().forEach { (key, entry) ->
            val obj = JSONObject()
                .put("lat", entry.place.latitude)
                .put("lon", entry.place.longitude)
                .put("displayName", entry.place.displayName)
                .put("storedAtMs", entry.storedAtMs)
            entry.place.voivodeship?.let { obj.put("voivodeship", it) }
            entry.place.county?.let { obj.put("county", it) }
            root.put(key, obj)
        }
        file.writeText(root.toString())
    }

    companion object {
        const val FILE_NAME = "locality_geocode_cache.json"

        fun fromAppFilesDir(filesDir: File): PersistentLocalityGeocodeStore =
            PersistentLocalityGeocodeStore(File(filesDir, FILE_NAME))
    }
}
