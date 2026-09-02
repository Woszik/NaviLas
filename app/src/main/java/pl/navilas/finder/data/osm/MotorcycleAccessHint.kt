package pl.navilas.finder.data.osm

import pl.navilas.finder.domain.Road
import pl.navilas.finder.domain.RoadAccessClass

/**
 * Legal-access hint for moto UI. Does not change classification or filtering:
 * untagged forest-style roads stay [RoadAccessClass.MOTO_ALLOWED] for ranking,
 * but the card / details mark them as uncertain.
 */
object MotorcycleAccessHint {
    private val UNCERTAIN_WITHOUT_LEGAL_TAGS = setOf("track", "service", "road")

    fun isLegalAccessUncertain(
        road: Road?,
        accessClass: RoadAccessClass?,
    ): Boolean {
        if (accessClass == RoadAccessClass.MOTO_UNKNOWN) return true
        val target = road ?: return false
        if (hasLegalTag(target)) return false
        return target.type.trim().lowercase() in UNCERTAIN_WITHOUT_LEGAL_TAGS
    }

    private fun hasLegalTag(road: Road): Boolean =
        !road.access.isNullOrBlank() ||
            !road.motorVehicle.isNullOrBlank() ||
            !road.motorcycle.isNullOrBlank() ||
            !road.vehicle.isNullOrBlank()
}
