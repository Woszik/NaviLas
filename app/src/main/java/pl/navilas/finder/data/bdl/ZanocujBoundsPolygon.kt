package pl.navilas.finder.data.bdl

import pl.navilas.finder.util.GeoUtils

/** Zanocuj polygon with precomputed bbox for fast viewport queries. */
data class ZanocujBoundsPolygon(
    val polygon: ZanocujPolygon,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
) {
    fun intersects(envelope: GeoUtils.Envelope): Boolean =
        maxLon >= envelope.xmin &&
            minLon <= envelope.xmax &&
            maxLat >= envelope.ymin &&
            minLat <= envelope.ymax

    fun centerLat(): Double = (minLat + maxLat) / 2.0

    fun centerLon(): Double = (minLon + maxLon) / 2.0

    companion object {
        fun from(polygon: ZanocujPolygon, marginDeg: Double = 0.0): ZanocujBoundsPolygon? {
            var minLat = Double.POSITIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            var minLon = Double.POSITIVE_INFINITY
            var maxLon = Double.NEGATIVE_INFINITY
            var any = false
            for (ring in polygon.rings) {
                for (p in ring) {
                    any = true
                    if (p.latitude < minLat) minLat = p.latitude
                    if (p.latitude > maxLat) maxLat = p.latitude
                    if (p.longitude < minLon) minLon = p.longitude
                    if (p.longitude > maxLon) maxLon = p.longitude
                }
            }
            if (!any) return null
            return ZanocujBoundsPolygon(
                polygon = polygon,
                minLat = minLat - marginDeg,
                maxLat = maxLat + marginDeg,
                minLon = minLon - marginDeg,
                maxLon = maxLon + marginDeg,
            )
        }
    }
}
