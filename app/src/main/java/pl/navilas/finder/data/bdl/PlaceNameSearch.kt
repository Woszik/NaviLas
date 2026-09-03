package pl.navilas.finder.data.bdl

import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.domain.ZanocujStatus
import pl.navilas.finder.util.GeoUtils
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

data class PlaceNameHit(
    val siteId: String,
    val name: String,
    val sourceLayerId: Int,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double?,
)

enum class PlaceNameMatchKind {
    EXACT,
    PREFIX,
    CONTAINS,
    FUZZY,
}

/**
 * Offline name lookup for BDL rest / parking / stop points.
 * Polish diacritic fold + token prefix / substring / small edit-distance typos.
 */
object PlaceNameSearch {
    const val MIN_QUERY_CHARS = 3
    const val MAX_RESULTS = 20
    const val LONG_TOKEN_MIN = 6
    const val FUZZY_SHORT = 1
    const val FUZZY_LONG = 2

    private val TOKEN_SPLIT = Regex("[^a-z0-9]+")

    /** Longest first so „miejsce postoju pojazdow” wins over „miejsce postoju”. */
    private val GENERIC_PREFIXES = listOf(
        "miejsce postoju pojazdow",
        "miejsce postoju",
        "miejsce wypoczynku",
        "miejsce odpoczynku",
        "parking / postoj",
        "parking lesny",
        "parking",
    )

    fun search(
        query: String,
        sites: List<RestSite>,
        originLat: Double? = null,
        originLon: Double? = null,
    ): List<PlaceNameHit> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_CHARS) return emptyList()
        val queryNorm = normalize(trimmed)
        if (queryNorm.length < MIN_QUERY_CHARS) return emptyList()
        val queryTokens = tokenize(queryNorm)
        if (queryTokens.isEmpty()) return emptyList()
        val hasOrigin = originLat != null && originLon != null &&
            originLat.isFinite() && originLon.isFinite()

        val ranked = ArrayList<Ranked>(min(sites.size, 64))
        for (site in sites) {
            val kind = matchKind(site.name, queryNorm, queryTokens) ?: continue
            val distinctive = distinctivePart(normalize(site.name))
            val generic = distinctive.isEmpty()
            val distanceKm = if (hasOrigin) {
                GeoUtils.distanceKm(originLat, originLon, site.latitude, site.longitude)
            } else {
                null
            }
            ranked += Ranked(site, kind, generic, distanceKm)
        }
        ranked.sortWith(
            compareBy<Ranked> { it.kind.ordinal }
                .thenBy { if (it.generic) 1 else 0 }
                .thenBy { it.distanceKm ?: Double.POSITIVE_INFINITY }
                .thenBy { it.site.name.lowercase(Locale.ROOT) },
        )
        return ranked.take(MAX_RESULTS).map { row ->
            PlaceNameHit(
                siteId = row.site.id,
                name = row.site.name,
                sourceLayerId = row.site.sourceLayerId,
                latitude = row.site.latitude,
                longitude = row.site.longitude,
                distanceKm = row.distanceKm,
            )
        }
    }

    fun layerLabelPl(layerId: Int): String = when (layerId) {
        RestSiteRepository.LAYER_REST -> "Wypoczynek"
        RestSiteRepository.LAYER_PARKING -> "Parking"
        RestSiteRepository.LAYER_STOP -> "Postój"
        else -> "BDL"
    }

    fun normalize(raw: String): String {
        val replaced = raw.trim()
            .replace('ł', 'l')
            .replace('Ł', 'L')
        val nfd = Normalizer.normalize(replaced, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.forLanguageTag("pl-PL"))
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    internal fun distinctivePart(normalizedName: String): String {
        if (normalizedName.isEmpty()) return ""
        for (prefix in GENERIC_PREFIXES) {
            if (normalizedName == prefix) return ""
            val withSpace = "$prefix "
            if (normalizedName.startsWith(withSpace)) {
                return normalizedName.removePrefix(withSpace).trim()
            }
        }
        return normalizedName
    }

    internal fun tokenize(normalized: String): List<String> =
        normalized.split(TOKEN_SPLIT).filter { it.isNotEmpty() }

    internal fun matchKind(
        name: String,
        queryNorm: String,
        queryTokens: List<String>,
    ): PlaceNameMatchKind? {
        val full = normalize(name)
        if (full.isEmpty()) return null
        val distinctive = distinctivePart(full)
        val haystacks = buildList {
            add(full)
            if (distinctive.isNotEmpty() && distinctive != full) add(distinctive)
        }
        var best: PlaceNameMatchKind? = null
        for (haystack in haystacks) {
            val kind = matchAgainst(haystack, queryNorm, queryTokens) ?: continue
            if (best == null || kind.ordinal < best.ordinal) best = kind
        }
        return best
    }

    internal fun editDistanceAtMost(a: String, b: String, max: Int): Boolean {
        if (a == b) return true
        if (max <= 0) return false
        if (abs(a.length - b.length) > max) return false
        val m = a.length
        val n = b.length
        var prev = IntArray(n + 1) { it }
        var cur = IntArray(n + 1)
        for (i in 1..m) {
            cur[0] = i
            var rowMin = cur[0]
            val ca = a[i - 1]
            for (j in 1..n) {
                val cost = if (ca == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > max) return false
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[n] <= max
    }

    private fun matchAgainst(
        haystack: String,
        queryNorm: String,
        queryTokens: List<String>,
    ): PlaceNameMatchKind? {
        if (haystack == queryNorm) return PlaceNameMatchKind.EXACT
        val nameTokens = tokenize(haystack)
        if (nameTokens.isEmpty()) return null
        if (haystack.startsWith(queryNorm)) return PlaceNameMatchKind.PREFIX
        val tokenKinds = queryTokens.map { token ->
            tokenMatch(token, haystack, nameTokens) ?: return null
        }
        return tokenKinds.maxBy { it.ordinal }
    }

    private fun tokenMatch(
        queryToken: String,
        haystack: String,
        nameTokens: List<String>,
    ): PlaceNameMatchKind? {
        var best: PlaceNameMatchKind? = null
        fun keep(kind: PlaceNameMatchKind) {
            val current = best
            if (current == null || kind.ordinal < current.ordinal) best = kind
        }
        if (haystack.startsWith(queryToken)) keep(PlaceNameMatchKind.PREFIX)
        for (nameToken in nameTokens) {
            if (nameToken.startsWith(queryToken)) {
                keep(PlaceNameMatchKind.PREFIX)
            } else if (nameToken.contains(queryToken) || haystack.contains(queryToken)) {
                keep(PlaceNameMatchKind.CONTAINS)
            } else {
                val max = if (queryToken.length >= LONG_TOKEN_MIN) FUZZY_LONG else FUZZY_SHORT
                if (queryToken.length >= MIN_QUERY_CHARS &&
                    editDistanceAtMost(queryToken, nameToken, max)
                ) {
                    keep(PlaceNameMatchKind.FUZZY)
                }
            }
            if (best == PlaceNameMatchKind.PREFIX) return PlaceNameMatchKind.PREFIX
        }
        return best
    }

    private data class Ranked(
        val site: RestSite,
        val kind: PlaceNameMatchKind,
        val generic: Boolean,
        val distanceKm: Double?,
    )
}

/** Lightweight 15/17/19 index from the offline pack (no Zanocuj / related-object work). */
object BdlPlaceNameCatalog {
    fun load(store: BdlOfflineStore): List<RestSite> {
        if (!store.isReady()) return emptyList()
        val layers = listOf(
            Triple(
                RestSiteRepository.LAYER_REST,
                RestSiteRepository.LAYER_NAME_REST,
                "Miejsce wypoczynku",
            ),
            Triple(
                RestSiteRepository.LAYER_PARKING,
                RestSiteRepository.LAYER_NAME_PARKING,
                "Parking leśny",
            ),
            Triple(
                RestSiteRepository.LAYER_STOP,
                RestSiteRepository.LAYER_NAME_STOP,
                "Miejsce postoju pojazdów",
            ),
        )
        val out = ArrayList<RestSite>(4096)
        for ((layerId, layerName, defaultName) in layers) {
            for (feature in store.loadLayerFeatures(layerId)) {
                mapSite(feature, layerId, layerName, defaultName)?.let { out += it }
            }
        }
        return out
    }

    internal fun mapSite(
        feature: JSONObject,
        layerId: Int,
        layerName: String,
        defaultName: String,
    ): RestSite? {
        val attrs = feature.optJSONObject("attributes") ?: return null
        val point = BdlMapper.pointFromGeometry(feature.optJSONObject("geometry")) ?: return null
        val lon = point.first
        val lat = point.second
        val id = try {
            BdlIdentity.resolve(layerId, attrs)
        } catch (_: Exception) {
            return null
        }
        val features = BdlFeatureExtractor.fromAttributes(attrs).toMutableSet()
        if (layerId == RestSiteRepository.LAYER_PARKING) {
            features += SiteFeature.PARKING
        }
        return RestSite(
            id = id,
            name = attrs.optString("nzw_ob").ifBlank { defaultName },
            latitude = lat,
            longitude = lon,
            description = attrs.optString("uwagi").takeIf { it.isNotBlank() && it != "NIE" },
            sourceLayerId = layerId,
            sourceLayerName = layerName,
            features = features,
            relatedObjects = emptyList(),
            zanocujStatus = ZanocujStatus.OUTSIDE_ZONE,
        )
    }
}
