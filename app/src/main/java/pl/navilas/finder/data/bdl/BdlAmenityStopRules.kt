package pl.navilas.finder.data.bdl

import org.json.JSONObject

/**
 * Stops/parkings (layers 17/19) that carry rest amenities become standalone search results
 * when they are not already covered by a nearby layer-15 rest site.
 *
 * Rule: `wiata=T` OR `palenisko=T` OR `lawostoly=T`.
 * Layer 27 monuments/etc. stay satellites only.
 */
object BdlAmenityStopRules {
    fun qualifiesAsStandalone(attrs: JSONObject): Boolean =
        BdlFeatureExtractor.yes(attrs, "wiata") ||
            BdlFeatureExtractor.yes(attrs, "palenisko") ||
            BdlFeatureExtractor.yes(attrs, "lawostoly")
}
