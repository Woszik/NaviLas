package pl.navilas.finder.data.bdl

import pl.navilas.finder.domain.BrowseCarFilter
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.NaturalSpringCertainty
import pl.navilas.finder.domain.RestSite
import pl.navilas.finder.domain.SiteFeature
import pl.navilas.finder.domain.ZanocujStatus
import pl.navilas.finder.domain.mergeWith
import pl.navilas.finder.util.GeoUtils
import kotlin.math.cos
import kotlin.math.floor

/**
 * AND-filter for browse + car: selected amenities must exist on the site or within link radius.
 */
object BrowseCarFilterMatcher {
    private const val CELL_DEG = 0.01 // ~1.1 km

    /**
     * When [BrowseCarFilter.requireNearWater] is on, parking/stop points (17/19) join the
     * candidate set so water is a peer amenity to wiata — not only a filter on rest sites.
     */
    fun sitesForWaterFilter(
        restSites: List<RestSite>,
        vehicleSites: List<RestSite>,
        filter: BrowseCarFilter,
        envelope: GeoUtils.Envelope? = null,
    ): List<RestSite> {
        if (!filter.requireNearWater || vehicleSites.isEmpty()) return restSites
        val known = restSites.mapTo(HashSet(restSites.size * 2)) { it.id }
        val padDeg = (filter.waterRadiusMeters() / 111_000.0) * 1.2
        val extras = vehicleSites.filter { site ->
            if (site.id in known) return@filter false
            if (!site.latitude.isFinite() || !site.longitude.isFinite()) return@filter false
            if (envelope == null) return@filter true
            site.latitude in (envelope.ymin - padDeg)..(envelope.ymax + padDeg) &&
                site.longitude in (envelope.xmin - padDeg)..(envelope.xmax + padDeg)
        }
        if (extras.isEmpty()) return restSites
        return restSites + extras
    }

    /**
     * @return `null` when filter inactive (show all); otherwise ids of matching sites.
     * @param excludeSiteIds sites inside a forest-entry ban, used when [BrowseCarFilter.excludeSitesInEntryBan].
     */
    fun matchingIds(
        sites: List<RestSite>,
        filter: BrowseCarFilter,
        excludeSiteIds: Set<String> = emptySet(),
        overlayWaterPoints: List<LatLon> = emptyList(),
        osmWaterHits: Set<String> = emptySet(),
    ): Set<String>? {
        if (!filter.isActive) return null
        if (sites.isEmpty()) return emptySet()

        val byCell = HashMap<Long, MutableList<RestSite>>(sites.size)
        for (site in sites) {
            if (!site.latitude.isFinite() || !site.longitude.isFinite()) continue
            val key = cellKey(site.latitude, site.longitude)
            byCell.getOrPut(key) { ArrayList() }.add(site)
        }

        val amenityRadius = BrowseCarFilter.AMENITY_LINK_METERS
        val parkingRadius = filter.parkingRadiusMeters()
        val waterRadius = filter.waterRadiusMeters()
        val match = HashSet<String>(sites.size / 4 + 1)

        for (site in sites) {
            if (!site.latitude.isFinite() || !site.longitude.isFinite()) continue
            if (filter.requireZanocujInZone && site.zanocujStatus != ZanocujStatus.IN_ZONE) {
                continue
            }
            if (filter.excludeSitesInEntryBan && site.id in excludeSiteIds) {
                continue
            }
            val nearbyAmenity = nearby(site, byCell, amenityRadius)
            if (filter.requireWiata && !hasFeatureNearby(nearbyAmenity, site, SiteFeature.WIATA)) {
                continue
            }
            if (filter.requireLawostoly &&
                !hasFeatureNearby(nearbyAmenity, site, SiteFeature.LAWOSTOLY)
            ) {
                continue
            }
            if (filter.requirePalenisko &&
                !hasFeatureNearby(nearbyAmenity, site, SiteFeature.PALENISKO)
            ) {
                continue
            }
            if (filter.requireWodaPitna &&
                !hasFeatureNearby(nearbyAmenity, site, SiteFeature.WODA_PITNA)
            ) {
                continue
            }
            if (filter.requireZrodlo && springNearby(nearbyAmenity, site) == null) {
                continue
            }
            if (filter.requireParking) {
                val nearbyParking = if (parkingRadius <= amenityRadius) {
                    nearbyAmenity
                } else {
                    nearby(site, byCell, parkingRadius)
                }
                if (!hasParkingNearby(nearbyParking, site)) continue
            }
            if (filter.requireNearWater) {
                val nearbyWater = if (waterRadius <= amenityRadius) {
                    nearbyAmenity
                } else {
                    nearby(site, byCell, waterRadius)
                }
                val bdlWater = hasBdlWater(site, nearbyWater, overlayWaterPoints, waterRadius)
                if (!bdlWater && site.id !in osmWaterHits) continue
            }
            match += site.id
        }
        return match
    }

    /** Best spring evidence on [self] or within already-collected [nearby] amenity sites. */
    fun springNearby(nearby: List<RestSite>, self: RestSite): NaturalSpringCertainty? {
        var best = self.naturalSpring
        for (other in nearby) {
            val otherSpring = other.naturalSpring ?: continue
            best = best?.mergeWith(otherSpring) ?: otherSpring
        }
        return best
    }

    private fun hasFeatureNearby(
        nearby: List<RestSite>,
        self: RestSite,
        feature: SiteFeature,
    ): Boolean {
        if (feature in self.features) return true
        return nearby.any { feature in it.features }
    }

    fun hasBdlWaterAmenity(site: RestSite): Boolean =
        SiteFeature.KAPIELISKO in site.features ||
            SiteFeature.MARINA in site.features ||
            site.sourceLayerId == RestSiteRepository.LAYER_BOAT

    private fun hasBdlWater(
        self: RestSite,
        nearby: List<RestSite>,
        overlayWaterPoints: List<LatLon>,
        radiusMeters: Double,
    ): Boolean {
        if (hasBdlWaterAmenity(self)) return true
        if (nearby.any { hasBdlWaterAmenity(it) }) return true
        return overlayWaterPoints.any { point ->
            GeoUtils.distanceMeters(
                self.latitude,
                self.longitude,
                point.latitude,
                point.longitude,
            ) <= radiusMeters
        }
    }

    private fun hasParkingNearby(nearby: List<RestSite>, self: RestSite): Boolean {
        if (isParkingSite(self)) return true
        return nearby.any { isParkingSite(it) }
    }

    private fun isParkingSite(site: RestSite): Boolean =
        SiteFeature.PARKING in site.features ||
            site.sourceLayerId == RestSiteRepository.LAYER_PARKING ||
            site.sourceLayerId == RestSiteRepository.LAYER_STOP

    private fun nearby(
        origin: RestSite,
        byCell: Map<Long, List<RestSite>>,
        radiusMeters: Double,
    ): List<RestSite> {
        val marginDeg = (radiusMeters / 111_000.0) * 1.2
        val lat0 = origin.latitude
        val lon0 = origin.longitude
        val minLat = lat0 - marginDeg
        val maxLat = lat0 + marginDeg
        val cosLat = cos(Math.toRadians(lat0)).coerceAtLeast(0.2)
        val lonMargin = marginDeg / cosLat
        val minLon = lon0 - lonMargin
        val maxLon = lon0 + lonMargin
        val i0 = tileIndex(minLat)
        val i1 = tileIndex(maxLat)
        val j0 = tileIndex(minLon)
        val j1 = tileIndex(maxLon)
        val out = ArrayList<RestSite>()
        for (i in i0..i1) {
            for (j in j0..j1) {
                val bucket = byCell[pack(i, j)] ?: continue
                for (other in bucket) {
                    if (other.id == origin.id) continue
                    if (other.latitude < minLat || other.latitude > maxLat) continue
                    if (other.longitude < minLon || other.longitude > maxLon) continue
                    val d = GeoUtils.distanceMeters(
                        origin.latitude,
                        origin.longitude,
                        other.latitude,
                        other.longitude,
                    )
                    if (d <= radiusMeters) out += other
                }
            }
        }
        return out
    }

    private fun cellKey(lat: Double, lon: Double): Long =
        pack(tileIndex(lat), tileIndex(lon))

    private fun tileIndex(value: Double): Int = floor(value / CELL_DEG).toInt()

    private fun pack(i: Int, j: Int): Long =
        (i.toLong() shl 32) xor (j.toLong() and 0xffffffffL)
}
