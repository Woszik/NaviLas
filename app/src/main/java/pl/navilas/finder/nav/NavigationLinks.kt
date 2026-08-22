package pl.navilas.finder.nav

import pl.navilas.finder.data.bdl.RestSiteRepository
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.NavigationTargetKind
import pl.navilas.finder.domain.RelatedBdlObject
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.RoadAccessClass
import pl.navilas.finder.domain.RoadAssessment
import pl.navilas.finder.domain.RoadSuitability
import pl.navilas.finder.domain.TravelProfile
import java.util.Locale

object NavigationTargets {
    fun forCar(site: RestSite): Pair<LatLon, NavigationTargetKind> {
        if (site.sourceLayerId == RestSiteRepository.LAYER_PARKING) {
            return LatLon(site.latitude, site.longitude) to NavigationTargetKind.PARKING
        }
        val parking = site.relatedObjects
            .filter { it.layerId == RestSiteRepository.LAYER_PARKING }
            .minByOrNull { it.distanceMeters }
        return if (parking != null) {
            LatLon(parking.latitude, parking.longitude) to NavigationTargetKind.PARKING
        } else {
            // Layer 15 rest site or layer 19 amenity stop — navigate to the place itself.
            LatLon(site.latitude, site.longitude) to NavigationTargetKind.REST_SITE
        }
    }

    fun forMotorcycle(assessment: RoadAssessment?): Pair<LatLon, NavigationTargetKind>? {
        val road = assessment?.nearestRoad ?: return null
        if (assessment.roadSuitability == null ||
            assessment.roadSuitability == RoadSuitability.REJECTED
        ) {
            return null
        }
        if (assessment.accessClass == RoadAccessClass.NOT_ROAD ||
            assessment.accessClass == RoadAccessClass.MOTO_RESTRICTED
        ) {
            return null
        }
        val lat = road.latitude ?: return null
        val lon = road.longitude ?: return null
        return LatLon(lat, lon) to NavigationTargetKind.OSM_ROAD
    }
}

object NavigationLinks {
    fun googleMapsDirUrl(destination: LatLon): String =
        String.format(
            Locale.US,
            "https://www.google.com/maps/dir/?api=1&destination=%f,%f",
            destination.latitude,
            destination.longitude,
        )

    /** Official OsmAnd geo intent (Android geo intents documented by OsmAnd). */
    fun osmAndGeoUri(destination: LatLon, label: String): String {
        val safe = label.replace('(', '[').replace(')', ']').replace(' ', '+')
        return String.format(
            Locale.US,
            "geo:%f,%f?q=%f,%f(%s)",
            destination.latitude,
            destination.longitude,
            destination.latitude,
            destination.longitude,
            safe,
        )
    }

    /** OsmAnd map URL with finish (opens OsmAnd when installed). */
    fun osmAndMapUrl(destination: LatLon, profile: TravelProfile): String {
        val profileKey = when (profile) {
            TravelProfile.CAR -> "car"
            TravelProfile.MOTORCYCLE -> "motorcycle"
        }
        return String.format(
            Locale.US,
            "https://osmand.net/map/?finish=%f,%f&profile=%s&pin=%f,%f",
            destination.latitude,
            destination.longitude,
            profileKey,
            destination.latitude,
            destination.longitude,
        )
    }

    fun gpxWaypoint(
        name: String,
        destination: LatLon,
        description: String?,
    ): String {
        val desc = xmlEscape(description.orEmpty())
        val safeName = xmlEscape(name)
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="NaviLas" xmlns="http://www.topografix.com/GPX/1/1">
              <wpt lat="${fmt(destination.latitude)}" lon="${fmt(destination.longitude)}">
                <name>$safeName</name>
                <desc>$desc</desc>
              </wpt>
            </gpx>
        """.trimIndent()
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun xmlEscape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

fun relatedParking(site: RestSite): RelatedBdlObject? =
    site.relatedObjects
        .filter { it.layerId == RestSiteRepository.LAYER_PARKING }
        .minByOrNull { it.distanceMeters }
