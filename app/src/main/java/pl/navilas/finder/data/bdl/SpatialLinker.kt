package pl.navilas.finder.data.bdl

import pl.navilas.finder.domain.RelatedBdlObject
import pl.navilas.finder.domain.SearchConfig
import pl.navilas.finder.util.GeoUtils

data class SatellitePoint(
    val id: String,
    val layerId: Int,
    val layerName: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val typeCode: String?,
)

object SpatialLinker {
    fun linkNearby(
        siteLat: Double,
        siteLon: Double,
        satellites: List<SatellitePoint>,
        config: SearchConfig = SearchConfig.DEFAULT,
    ): List<RelatedBdlObject> {
        return satellites
            .mapNotNull { sat ->
                val meters = GeoUtils.distanceMeters(siteLat, siteLon, sat.latitude, sat.longitude)
                if (meters > config.restLinkRadiusMeters) return@mapNotNull null
                RelatedBdlObject(
                    id = sat.id,
                    layerId = sat.layerId,
                    layerName = sat.layerName,
                    name = sat.name,
                    latitude = sat.latitude,
                    longitude = sat.longitude,
                    distanceMeters = meters,
                    typeCode = sat.typeCode,
                )
            }
            .distinctBy { it.id }
            .sortedBy { it.distanceMeters }
    }
}
