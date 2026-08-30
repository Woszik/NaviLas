package pl.navilas.finder.nav

import pl.navilas.finder.data.bdl.RestSiteRepository
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.NavigationTargetKind
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.RoadAccessClass
import pl.navilas.finder.domain.RoadAssessment
import pl.navilas.finder.domain.RoadSuitability
import pl.navilas.finder.domain.TravelProfile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object NavigationTargets {
    /**
     * CAR navigation always targets the result coordinates (rest site or standalone parking primary).
     * Nearby related parking is not used as a separate destination.
     */
    fun forCar(site: RestSite): Pair<LatLon, NavigationTargetKind> {
        val kind = if (site.sourceLayerId == RestSiteRepository.LAYER_PARKING) {
            NavigationTargetKind.PARKING
        } else {
            NavigationTargetKind.REST_SITE
        }
        return LatLon(site.latitude, site.longitude) to kind
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

/** OsmAnd moto route styles (stringKey from imported NaviLas OSF profiles). */
enum class OsmAndMotoRouteStyle(val profileKey: String) {
    SHORT("brouter_trekking"),
    TWISTY("brouter_moped"),
    STANDARD("motorcycle"),
}

object NavigationLinks {
    const val OSMAND_PROFILE_CAR = "car"
    const val OSMAND_PROFILE_MOTORCYCLE = "motorcycle"
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

    /** OsmAnd map URL with finish (web fallback — avoid on device; opens browser). */
    fun osmAndMapUrl(destination: LatLon, profile: TravelProfile): String {
        val profileKey = osmAndProfileKey(profile)
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

    /**
     * OsmAnd in-app navigation API ([osmand-api-demo](https://github.com/osmandapp/osmand-api-demo)).
     * Opens installed OsmAnd — not the browser.
     */
    fun osmAndNavigateUri(destination: LatLon, label: String, profileKey: String): String {
        val safeName = URLEncoder.encode(label, StandardCharsets.UTF_8.name())
        return String.format(
            Locale.US,
            "osmand.api://navigate?dest_lat=%f&dest_lon=%f&dest_name=%s&profile=%s",
            destination.latitude,
            destination.longitude,
            safeName,
            profileKey,
        )
    }

    fun osmAndNavigateUri(destination: LatLon, label: String, profile: TravelProfile): String =
        osmAndNavigateUri(destination, label, osmAndProfileKey(profile))

    /** Decimal degrees for manual paste into nav apps (e.g. Calimoto). */
    fun gpsCoordinatesText(destination: LatLon): String =
        String.format(Locale.US, "%.6f, %.6f", destination.latitude, destination.longitude)

    private fun osmAndProfileKey(profile: TravelProfile): String = when (profile) {
        TravelProfile.CAR -> OSMAND_PROFILE_CAR
        TravelProfile.MOTORCYCLE -> OSMAND_PROFILE_MOTORCYCLE
    }

    /**
     * Destination GPX for motorcycle nav apps (e.g. calimoto).
     * Includes both a waypoint and a one-point route — calimoto planned-ride
     * import prioritizes waypoints / route points over tracks.
     */
    fun gpxWaypoint(
        name: String,
        destination: LatLon,
        description: String?,
    ): String {
        val desc = xmlEscape(description.orEmpty())
        val safeName = xmlEscape(name)
        val lat = fmt(destination.latitude)
        val lon = fmt(destination.longitude)
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="NaviLas" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata>
                <name>$safeName</name>
                <desc>$desc</desc>
              </metadata>
              <wpt lat="$lat" lon="$lon">
                <name>$safeName</name>
                <desc>$desc</desc>
                <type>Destination</type>
              </wpt>
              <rte>
                <name>$safeName</name>
                <desc>$desc</desc>
                <rtept lat="$lat" lon="$lon">
                  <name>$safeName</name>
                  <desc>$desc</desc>
                </rtept>
              </rte>
            </gpx>
        """.trimIndent()
    }

    /** Safe ASCII-ish basename for a shared GPX file. */
    fun gpxFileBaseName(name: String): String {
        val cleaned = name
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(48)
        return cleaned.ifBlank { "destination" }
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun xmlEscape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
