package pl.navilas.finder.data.bdl

import org.json.JSONArray
import org.json.JSONObject
import pl.navilas.finder.domain.ForestEntryBan
import pl.navilas.finder.domain.ForestEntryBanReason
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.util.GeoUtils
import java.io.File

/** File-backed nationwide pack of BDL forest-entry bans for offline map overlay. */
class ForestEntryBanStore(
    private val dir: File,
) {
    private val file = File(dir, PACK_FILE)

    fun isReady(): Boolean = downloadedAt() != null && file.exists() && file.length() > 0L

    fun downloadedAt(): Long? {
        if (!file.exists()) return null
        return runCatching {
            JSONObject(file.readText()).optLong("downloadedAt").takeIf { it > 0L }
        }.getOrNull()
    }

    fun count(): Int {
        if (!file.exists()) return 0
        return runCatching {
            JSONObject(file.readText()).optJSONArray("bans")?.length() ?: 0
        }.getOrDefault(0)
    }

    fun loadAll(): List<ForestEntryBan> {
        if (!file.exists()) return emptyList()
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("bans") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                parseBan(arr.optJSONObject(i))?.let { add(it) }
            }
        }
    }

    fun saveAll(bans: List<ForestEntryBan>, downloadedAt: Long) {
        dir.mkdirs()
        val arr = JSONArray()
        bans.forEach { ban -> arr.put(toJson(ban)) }
        val payload = JSONObject()
            .put("downloadedAt", downloadedAt)
            .put("bans", arr)
            .toString()
        val tmp = File(dir, "$PACK_FILE.tmp")
        tmp.writeText(payload)
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            file.writeText(payload)
            tmp.delete()
        }
    }

    fun deleteAll() {
        if (dir.exists()) dir.deleteRecursively()
    }

    private fun toJson(ban: ForestEntryBan): JSONObject {
        val rings = JSONArray()
        ban.rings.forEach { ring ->
            val ringJson = JSONArray()
            ring.forEach { p ->
                ringJson.put(
                    JSONObject()
                        .put("lat", p.latitude)
                        .put("lon", p.longitude),
                )
            }
            rings.put(ringJson)
        }
        return JSONObject()
            .put("id", ban.id)
            .put("reason", ban.reason.name)
            .put("forestDistrict", ban.forestDistrict)
            .put("forestry", ban.forestry)
            .put("compartment", ban.compartment)
            .put("validFrom", ban.validFrom)
            .put("validUntil", ban.validUntil)
            .put("rings", rings)
    }

    private fun parseBan(json: JSONObject?): ForestEntryBan? {
        if (json == null) return null
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val reason = runCatching {
            ForestEntryBanReason.valueOf(json.optString("reason"))
        }.getOrDefault(ForestEntryBanReason.OTHER)
        val ringsJson = json.optJSONArray("rings") ?: return null
        val rings = ArrayList<List<LatLon>>(ringsJson.length())
        for (r in 0 until ringsJson.length()) {
            val ringJson = ringsJson.optJSONArray(r) ?: continue
            val ring = ArrayList<LatLon>(ringJson.length())
            for (i in 0 until ringJson.length()) {
                val p = ringJson.optJSONObject(i) ?: continue
                val lat = p.optDouble("lat", Double.NaN)
                val lon = p.optDouble("lon", Double.NaN)
                if (lat.isFinite() && lon.isFinite()) {
                    ring += LatLon(latitude = lat, longitude = lon)
                }
            }
            if (ring.size >= 3) rings += ring
        }
        if (rings.isEmpty()) return null
        return ForestEntryBan(
            id = id,
            reason = reason,
            forestDistrict = json.optString("forestDistrict").takeIf { it.isNotBlank() && it != "null" },
            forestry = json.optString("forestry").takeIf { it.isNotBlank() && it != "null" },
            compartment = json.optString("compartment").takeIf { it.isNotBlank() && it != "null" },
            validFrom = json.optString("validFrom").takeIf { it.isNotBlank() && it != "null" },
            validUntil = json.optString("validUntil").takeIf { it.isNotBlank() && it != "null" },
            rings = rings,
        )
    }

    companion object {
        private const val PACK_FILE = "bans.json"

        fun fromAppFilesDir(filesDir: File): ForestEntryBanStore =
            ForestEntryBanStore(File(filesDir, "entry_bans"))
    }
}

data class ForestEntryBanBounds(
    val ban: ForestEntryBan,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
) {
    fun intersects(envelope: GeoUtils.Envelope): Boolean =
        maxLon >= envelope.xmin &&
            minLon <= envelope.xmax &&
            maxLat >= envelope.ymin &&
            minLat <= envelope.ymax

    fun centerLat(): Double = (minLat + maxLat) / 2.0
    fun centerLon(): Double = (minLon + maxLon) / 2.0

    companion object {
        fun from(ban: ForestEntryBan): ForestEntryBanBounds? {
            var minLat = Double.POSITIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            var minLon = Double.POSITIVE_INFINITY
            var maxLon = Double.NEGATIVE_INFINITY
            var any = false
            for (ring in ban.rings) {
                for (p in ring) {
                    any = true
                    if (p.latitude < minLat) minLat = p.latitude
                    if (p.latitude > maxLat) maxLat = p.latitude
                    if (p.longitude < minLon) minLon = p.longitude
                    if (p.longitude > maxLon) maxLon = p.longitude
                }
            }
            if (!any) return null
            return ForestEntryBanBounds(ban, minLat, maxLat, minLon, maxLon)
        }
    }
}
