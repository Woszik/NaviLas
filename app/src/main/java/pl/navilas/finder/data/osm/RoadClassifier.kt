package pl.navilas.finder.data.osm

import pl.navilas.finder.domain.Road
import pl.navilas.finder.domain.RoadAccessClass

/**
 * Classifies OSM highway ways for motorcycle suitability (Checkpoint 2).
 * Does not treat every `highway=*` geometry as motorable.
 */
object RoadClassifier {
    private val NOT_ROAD_TYPES = setOf(
        "footway",
        "path",
        "cycleway",
        "steps",
        "pedestrian",
        "bridleway",
        "corridor",
        "platform",
        "crossing",
        "bus_stop",
        "elevator",
        "escalator",
        "proposed",
        "abandoned",
        "disused",
        "construction",
        "raceway",
        "busway",
        "bus_guideway",
    )

    private val TYPICALLY_MOTORABLE = setOf(
        "motorway",
        "motorway_link",
        "trunk",
        "trunk_link",
        "primary",
        "primary_link",
        "secondary",
        "secondary_link",
        "tertiary",
        "tertiary_link",
        "unclassified",
        "residential",
        "living_street",
        "service",
        "track",
        "road",
    )

    fun classify(
        highway: String?,
        access: String? = null,
        motorVehicle: String? = null,
        motorcycle: String? = null,
        vehicle: String? = null,
    ): RoadAccessClass {
        val hw = highway?.trim()?.lowercase().orEmpty()
        if (hw.isEmpty() || hw in NOT_ROAD_TYPES) {
            return RoadAccessClass.NOT_ROAD
        }

        if (isExplicitNo(motorcycle)) {
            return RoadAccessClass.MOTO_RESTRICTED
        }
        if (isExplicitNo(motorVehicle) && !isExplicitYes(motorcycle)) {
            return RoadAccessClass.MOTO_RESTRICTED
        }
        if (isExplicitNo(vehicle) && !isExplicitYes(motorcycle)) {
            return RoadAccessClass.MOTO_RESTRICTED
        }
        if (isForbiddenAccess(access) && !isExplicitYes(motorcycle)) {
            return RoadAccessClass.MOTO_RESTRICTED
        }

        if (isExplicitYes(motorcycle)) {
            return RoadAccessClass.MOTO_ALLOWED
        }

        return when (hw) {
            in TYPICALLY_MOTORABLE -> RoadAccessClass.MOTO_ALLOWED
            else -> RoadAccessClass.MOTO_UNKNOWN
        }
    }

    fun describeMotorcycleRoad(road: Road): String {
        val parts = mutableListOf<String>()
        val surface = surfaceLabelPl(road.surface)
        if (surface != null) {
            parts += surface
        } else {
            parts += polishRoadType(road.type)
        }
        tracktypeLabelPl(road.tracktype)?.let { parts += it }
        if (isForestryAccess(road.motorVehicle) || isForestryAccess(road.access)) {
            parts += "leśna (dostęp LP)"
        }
        return parts.joinToString(" · ")
    }

    fun surfaceLabelPl(surface: String?): String? = when (surface?.trim()?.lowercase()) {
        "asphalt", "concrete" -> "asfalt / utwardzona"
        "paved", "paving_stones", "sett", "cobblestone" -> "utwardzona"
        "compacted", "gravel", "fine_gravel", "pebblestone", "chipseal" -> "utwardzona sypko"
        "ground", "dirt", "earth", "unpaved", "grass", "sand", "mud", "wood" -> "gruntowa"
        else -> null
    }

    fun tracktypeLabelPl(tracktype: String?): String? = when (tracktype?.trim()?.lowercase()) {
        "grade1", "grade2" -> "raczej przejezdna"
        "grade3" -> "średnia"
        "grade4", "grade5" -> "raczej trudna"
        else -> null
    }

    private fun isForestryAccess(value: String?): Boolean {
        val v = value?.trim()?.lowercase() ?: return false
        return v in setOf("forestry", "agricultural")
    }

    fun polishRoadType(highway: String?): String {
        return when (highway?.lowercase()) {
            "motorway", "motorway_link" -> "autostrada / łącznica"
            "trunk", "trunk_link" -> "droga ekspresowa / główna"
            "primary", "primary_link" -> "droga główna"
            "secondary", "secondary_link" -> "droga drugorzędna"
            "tertiary", "tertiary_link" -> "droga lokalna / trzeciorzędna"
            "unclassified" -> "droga nieklasyfikowana"
            "residential" -> "droga lokalna"
            "living_street" -> "strefa zamieszkania"
            "service" -> "droga dojazdowa / service"
            "track" -> "droga gruntowa / track"
            "road" -> "droga (road)"
            "footway" -> "chodnik (footway)"
            "path" -> "ścieżka (path)"
            "cycleway" -> "droga rowerowa"
            "steps" -> "schody"
            else -> highway?.ifBlank { "nieznany typ" } ?: "nieznany typ"
        }
    }

    private fun isExplicitNo(value: String?): Boolean {
        val v = value?.trim()?.lowercase() ?: return false
        return v in setOf("no", "private", "destination", "customers", "delivery", "forestry", "agricultural")
    }

    private fun isForbiddenAccess(value: String?): Boolean {
        val v = value?.trim()?.lowercase() ?: return false
        return v in setOf("no", "private", "permit")
    }

    private fun isExplicitYes(value: String?): Boolean {
        val v = value?.trim()?.lowercase() ?: return false
        return v in setOf("yes", "designated", "permissive")
    }
}
