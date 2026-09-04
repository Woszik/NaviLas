package pl.navilas.finder.data.bdl

import org.json.JSONObject

/**
 * Stops/parkings (layers 17/19) are peer destinations with layer-15 rest sites.
 *
 * Callers still suppress a duplicate pin when a layer-15 primary is within
 * [pl.navilas.finder.domain.SearchConfig.restLinkRadiusMeters] (search) or the
 * same browse cell (offline browse) — then 17/19 stay satellites / related only.
 *
 * Amenity flags (`wiata` / `palenisko` / `lawostoly`) enrich the site but are
 * **not** required for a standalone pin.
 */
object BdlAmenityStopRules {
    @Suppress("UNUSED_PARAMETER")
    fun qualifiesAsStandalone(attrs: JSONObject): Boolean = true
}
