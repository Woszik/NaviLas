package pl.navilas.finder.data.osm

import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.Road
import pl.navilas.finder.domain.RoadAccessClass
import pl.navilas.finder.domain.RoadAssessment
import pl.navilas.finder.util.GeoUtils

/**
 * Legal-access hint for moto UI. Does not change classification or filtering:
 * untagged forest-style roads stay [RoadAccessClass.MOTO_ALLOWED] for ranking,
 * but the card / details mark them as uncertain — unless they are an official
 * LP parking/stop approach (BDL 17/19 + LP operator) or sit on that corridor.
 */
object MotorcycleAccessHint {
    private val UNCERTAIN_WITHOUT_LEGAL_TAGS = setOf("track", "service", "road")

    /** Objects this close to a certified approach way inherit the official-LP hint. */
    const val OFFICIAL_CORRIDOR_MAX_METERS = 50.0

    fun isLegalAccessUncertain(
        road: Road?,
        accessClass: RoadAccessClass?,
    ): Boolean {
        if (accessClass == RoadAccessClass.MOTO_UNKNOWN) return true
        val target = road ?: return false
        if (hasLegalTag(target)) return false
        return target.type.trim().lowercase() in UNCERTAIN_WITHOUT_LEGAL_TAGS
    }

    fun isOfficialVehicleSiteLayer(sourceLayerId: Int): Boolean =
        sourceLayerId == LAYER_PARKING || sourceLayerId == LAYER_STOP

    fun isLpOperator(operator: String?): Boolean {
        val value = operator?.trim()?.lowercase().orEmpty()
        if (value.isEmpty()) return false
        return "nadleśnictwo" in value ||
            "nadlesnictwo" in value ||
            "lasy państwowe" in value ||
            "lasy panstwowe" in value ||
            "państwowe gospodarstwo leśne" in value ||
            "panstwowe gospodarstwo lesne" in value ||
            value.contains("pgl lp") ||
            value.startsWith("pgl")
    }

    fun isOfficialParkingSeed(sourceLayerId: Int, road: Road?): Boolean {
        val target = road ?: return false
        return isOfficialVehicleSiteLayer(sourceLayerId) && isLpOperator(target.operator)
    }

    fun collectCorridor(entries: Iterable<OfficialApproachEntry>): OfficialApproachCorridor {
        val seedRoads = entries.mapNotNull { entry ->
            entry.road?.takeIf { isOfficialParkingSeed(entry.sourceLayerId, it) }
        }
        if (seedRoads.isEmpty()) return OfficialApproachCorridor.EMPTY

        val wayIds = LinkedHashSet<String>()
        val names = LinkedHashSet<String>()
        val geometries = ArrayList<List<LatLon>>()
        fun addRoad(road: Road) {
            wayIds += road.id
            normalizeName(road.name)?.let { names += it }
            if (road.geometry.size >= 2) {
                geometries += road.geometry
            }
        }
        seedRoads.forEach(::addRoad)
        entries.mapNotNull { it.road }.forEach { road ->
            val name = normalizeName(road.name)
            if (name != null && name in names && isLpOperator(road.operator)) {
                addRoad(road)
            }
        }
        return OfficialApproachCorridor(
            wayIds = wayIds,
            names = names,
            geometries = geometries,
        )
    }

    fun isOfficialLpApproach(
        sourceLayerId: Int,
        latitude: Double,
        longitude: Double,
        road: Road?,
        corridor: OfficialApproachCorridor,
    ): Boolean {
        if (isOfficialParkingSeed(sourceLayerId, road)) return true
        return corridor.contains(road, latitude, longitude)
    }

    fun isOfficialLpApproach(
        sourceLayerId: Int,
        latitude: Double,
        longitude: Double,
        assessment: RoadAssessment?,
        corridor: OfficialApproachCorridor,
    ): Boolean = isOfficialLpApproach(
        sourceLayerId = sourceLayerId,
        latitude = latitude,
        longitude = longitude,
        road = assessment?.nearestRoad,
        corridor = corridor,
    )

    private fun hasLegalTag(road: Road): Boolean =
        !road.access.isNullOrBlank() ||
            !road.motorVehicle.isNullOrBlank() ||
            !road.motorcycle.isNullOrBlank() ||
            !road.vehicle.isNullOrBlank()

    internal fun normalizeName(name: String?): String? {
        val value = name?.trim()?.lowercase()?.replace(WHITESPACE, " ").orEmpty()
        return value.takeIf { it.isNotEmpty() }
    }

    private val WHITESPACE = Regex("\\s+")

    /** Same ids as [pl.navilas.finder.data.bdl.RestSiteRepository] parking / stop layers. */
    private const val LAYER_PARKING = 17
    private const val LAYER_STOP = 19
}

data class OfficialApproachEntry(
    val sourceLayerId: Int,
    val road: Road?,
)

data class OfficialApproachCorridor(
    val wayIds: Set<String>,
    val names: Set<String>,
    val geometries: List<List<LatLon>>,
) {
    fun contains(road: Road?, latitude: Double, longitude: Double): Boolean {
        if (road != null) {
            if (road.id in wayIds) return true
            val name = MotorcycleAccessHint.normalizeName(road.name)
            if (name != null && name in names && MotorcycleAccessHint.isLpOperator(road.operator)) {
                return true
            }
        }
        return geometries.any { geometry ->
            val hit = GeoUtils.nearestPointOnPolylineMeters(latitude, longitude, geometry)
            hit != null && hit.distanceMeters <= MotorcycleAccessHint.OFFICIAL_CORRIDOR_MAX_METERS
        }
    }

    companion object {
        val EMPTY = OfficialApproachCorridor(emptySet(), emptySet(), emptyList())
    }
}
