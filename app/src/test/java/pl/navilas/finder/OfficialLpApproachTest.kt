package pl.navilas.finder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.osm.MotorcycleAccessHint
import pl.navilas.finder.data.osm.OfficialApproachEntry
import pl.navilas.finder.domain.LatLon
import pl.navilas.finder.domain.Road

class OfficialLpApproachTest {
    private val fire45short = road(
        id = "way/1314894438",
        name = "Dojazd pożarowy 45",
        operator = "Nadleśnictwo Koszęcin",
        geometry = listOf(LatLon(50.63036, 18.78480), LatLon(50.63080, 18.78480)),
    )
    private val fire45long = road(
        id = "way/726951332",
        name = "Dojazd pożarowy 45",
        operator = "Nadleśnictwo Koszęcin",
        geometry = listOf(LatLon(50.63100, 18.78480), LatLon(50.63700, 18.78480)),
    )
    private val krywaldTrack = road(
        id = "way/238113803",
        name = null,
        operator = null,
        geometry = listOf(LatLon(50.58821, 18.77893), LatLon(50.58900, 18.77893)),
    )

    @Test
    fun lp_operator_matches_forest_district_forms() {
        assertTrue(MotorcycleAccessHint.isLpOperator("Nadleśnictwo Koszęcin"))
        assertTrue(MotorcycleAccessHint.isLpOperator("Lasy Państwowe"))
        assertTrue(MotorcycleAccessHint.isLpOperator("PGL LP"))
        assertFalse(MotorcycleAccessHint.isLpOperator(null))
        assertFalse(MotorcycleAccessHint.isLpOperator("Gmina"))
        assertFalse(MotorcycleAccessHint.isLpOperator(""))
    }

    @Test
    fun potempowe_stop_with_lp_operator_is_seed() {
        assertTrue(MotorcycleAccessHint.isOfficialParkingSeed(19, fire45short))
        assertTrue(
            MotorcycleAccessHint.isOfficialLpApproach(
                sourceLayerId = 19,
                latitude = 50.63036,
                longitude = 18.78480,
                road = fire45short,
                corridor = MotorcycleAccessHint.collectCorridor(emptyList()),
            ),
        )
    }

    @Test
    fun krywald_rest_site_stays_uncertain() {
        assertFalse(MotorcycleAccessHint.isOfficialParkingSeed(15, krywaldTrack))
        assertTrue(
            MotorcycleAccessHint.isLegalAccessUncertain(krywaldTrack, pl.navilas.finder.domain.RoadAccessClass.MOTO_ALLOWED),
        )
        val corridor = MotorcycleAccessHint.collectCorridor(
            listOf(OfficialApproachEntry(15, krywaldTrack)),
        )
        assertFalse(
            MotorcycleAccessHint.isOfficialLpApproach(
                sourceLayerId = 15,
                latitude = 50.58821,
                longitude = 18.77893,
                road = krywaldTrack,
                corridor = corridor,
            ),
        )
    }

    @Test
    fun parking_flag_on_layer_15_is_not_a_seed() {
        assertFalse(MotorcycleAccessHint.isOfficialParkingSeed(15, fire45short))
    }

    @Test
    fun stop_without_operator_is_not_a_seed() {
        assertFalse(MotorcycleAccessHint.isOfficialParkingSeed(19, krywaldTrack))
    }

    @Test
    fun neighbor_on_same_named_lp_way_inherits() {
        val corridor = MotorcycleAccessHint.collectCorridor(
            listOf(
                OfficialApproachEntry(19, fire45short),
                OfficialApproachEntry(15, fire45long),
            ),
        )
        assertTrue(
            MotorcycleAccessHint.isOfficialLpApproach(
                sourceLayerId = 15,
                latitude = 50.63400,
                longitude = 18.78480,
                road = fire45long,
                corridor = corridor,
            ),
        )
    }

    @Test
    fun neighbor_on_other_track_does_not_inherit() {
        val corridor = MotorcycleAccessHint.collectCorridor(
            listOf(
                OfficialApproachEntry(19, fire45short),
                OfficialApproachEntry(15, krywaldTrack),
            ),
        )
        assertFalse(
            MotorcycleAccessHint.isOfficialLpApproach(
                sourceLayerId = 15,
                latitude = 50.58821,
                longitude = 18.77893,
                road = krywaldTrack,
                corridor = corridor,
            ),
        )
    }

    @Test
    fun neighbor_within_50m_of_seed_geometry_inherits() {
        val sideTrack = road(
            id = "way/side",
            name = null,
            operator = null,
            geometry = listOf(LatLon(50.63050, 18.78520), LatLon(50.63060, 18.78520)),
        )
        val corridor = MotorcycleAccessHint.collectCorridor(
            listOf(OfficialApproachEntry(19, fire45short)),
        )
        assertTrue(
            MotorcycleAccessHint.isOfficialLpApproach(
                sourceLayerId = 15,
                latitude = 50.63050,
                longitude = 18.78510,
                road = sideTrack,
                corridor = corridor,
            ),
        )
    }

    private fun road(
        id: String,
        name: String?,
        operator: String?,
        geometry: List<LatLon>,
    ) = Road(
        id = id,
        type = "track",
        access = null,
        motorVehicle = null,
        motorcycle = null,
        vehicle = null,
        name = name,
        operator = operator,
        geometry = geometry,
    )
}
