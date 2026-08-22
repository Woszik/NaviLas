package pl.navilas.finder.data.bdl

import org.json.JSONObject
import pl.navilas.finder.domain.SiteFeature

/** Extracts confirmed BDL amenity flags into [SiteFeature] (no invented categories). */
object BdlFeatureExtractor {
    fun fromAttributes(attrs: JSONObject): Set<SiteFeature> {
        val features = linkedSetOf<SiteFeature>()
        if (yes(attrs, "wiata")) features += SiteFeature.WIATA
        if (yes(attrs, "palenisko")) features += SiteFeature.PALENISKO
        if (yes(attrs, "parking")) features += SiteFeature.PARKING
        if (yes(attrs, "woda_pitna")) features += SiteFeature.WODA_PITNA
        if (yes(attrs, "lawostoly")) features += SiteFeature.LAWOSTOLY
        if (yes(attrs, "kuchenka")) features += SiteFeature.KUCHENKA
        if (
            yes(attrs, "toalety_tm") || yes(attrs, "toalety_st") ||
            yes(attrs, "os_toalety") || yes(attrs, "n_toalety")
        ) {
            features += SiteFeature.TOALETY
        }
        if (yes(attrs, "lad_rower")) features += SiteFeature.LAD_ROWER
        if (yes(attrs, "serw_rower")) features += SiteFeature.SERW_ROWER
        if (yes(attrs, "kapielisko")) features += SiteFeature.KAPIELISKO
        if (yes(attrs, "marina")) features += SiteFeature.MARINA
        return features
    }

    fun yes(attrs: JSONObject, field: String): Boolean {
        if (!attrs.has(field) || attrs.isNull(field)) return false
        return BdlMapper.isYes(attrs.optString(field))
    }
}
