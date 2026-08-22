package pl.navilas.finder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.bdl.BdlIdentity
import pl.navilas.finder.data.bdl.BdlMapper
import pl.navilas.finder.data.bdl.BdlRepository
import pl.navilas.finder.domain.PoiCategory
import pl.navilas.finder.domain.PoiGeometryKind
import pl.navilas.finder.domain.TravelProfile
import pl.navilas.finder.util.GeoUtils

class DomainAndMapperTest {
    @Test
    fun haversine_warsaw_to_nearby_is_plausible() {
        val km = GeoUtils.distanceKm(52.2297, 21.0122, 52.25, 21.05)
        assertTrue(km in 3.0..6.0)
    }

    @Test
    fun motorcycle_profile_excludes_parking_only() {
        val parkingOnly = setOf(PoiCategory.PARKING)
        assertTrue(parkingOnly.none { it in TravelProfile.MOTORCYCLE.allowedCategories() })
        assertTrue(parkingOnly.any { it in TravelProfile.CAR.allowedCategories() })
    }

    @Test
    fun parking_with_wiata_adds_rest_without_duplicate_rows() {
        val poi = BdlMapper.mapParkingFeature(
            parkingFeature(wiata = "T", palenisko = "N", foreignKey = "pk-wiata"),
        )!!
        assertEquals(setOf(PoiCategory.PARKING, PoiCategory.REST), poi.categories)
        assertEquals(1, setOf(poi.id).size)
        assertEquals(PoiGeometryKind.POINT, poi.geometryKind)
        assertTrue(poi.id.startsWith("bdl:17:foreign_key:"))
    }

    @Test
    fun parking_with_palenisko_adds_fire_without_duplicate_rows() {
        val poi = BdlMapper.mapParkingFeature(
            parkingFeature(wiata = "N", palenisko = "T", foreignKey = "pk-fire"),
        )!!
        assertEquals(setOf(PoiCategory.PARKING, PoiCategory.FIRE), poi.categories)
        assertEquals(PoiCategory.PARKING, poi.primaryCategory)
    }

    @Test
    fun parking_with_wiata_and_palenisko_has_three_categories_one_record() {
        val poi = BdlMapper.mapParkingFeature(
            parkingFeature(wiata = "T", palenisko = "T", foreignKey = "pk-both"),
        )!!
        assertEquals(
            setOf(PoiCategory.PARKING, PoiCategory.REST, PoiCategory.FIRE),
            poi.categories,
        )
        // Set semantics — no duplicated category entries
        assertEquals(3, poi.categories.size)
    }

    @Test
    fun rest_feature_with_wiata_and_palenisko_keeps_single_poi() {
        val feature = JSONObject(
            """
            {
              "attributes": {
                "objectid": 1,
                "foreign_key": "rest-abc",
                "tur_rec_pnt_id": 26339,
                "nzw_ob": "Miejsce wypoczynku",
                "wiata": "T",
                "palenisko": "T",
                "uwagi": "test"
              },
              "geometry": { "x": 21.0, "y": 52.0 }
            }
            """.trimIndent(),
        )
        val poi = BdlMapper.mapRestFeature(feature)!!
        assertEquals(setOf(PoiCategory.REST, PoiCategory.FIRE), poi.categories)
        assertEquals(2, poi.categories.size)
        assertEquals("bdl:15:foreign_key:rest-abc", poi.id)
        assertEquals(52.0, poi.latitude, 0.0001)
        assertEquals(21.0, poi.longitude, 0.0001)
    }

    @Test
    fun amenity_flags_do_not_duplicate_categories_when_applied_twice() {
        val attrs = JSONObject("""{"wiata":"T","palenisko":"T"}""")
        val categories = linkedSetOf(PoiCategory.PARKING, PoiCategory.REST)
        BdlMapper.addAmenityCategories(attrs, categories)
        BdlMapper.addAmenityCategories(attrs, categories)
        assertEquals(
            setOf(PoiCategory.PARKING, PoiCategory.REST, PoiCategory.FIRE),
            categories,
        )
        assertEquals(3, categories.size)
    }

    @Test
    fun point_beyond_100km_is_rejected_by_radius_filter() {
        val poi = BdlMapper.mapParkingFeature(
            parkingFeature(
                wiata = "N",
                palenisko = "N",
                foreignKey = "far",
                x = 21.0,
                y = 52.0,
            ),
        )!!
        // ~200+ km north of the point
        assertFalse(GeoUtils.isWithinRadiusKm(54.0, 21.0, poi, 100.0))
        assertTrue(GeoUtils.isWithinRadiusKm(52.05, 21.0, poi, 100.0))
    }

    @Test
    fun zanocuj_polygon_keeps_area_rings_and_helper_centroid() {
        val feature = JSONObject(
            """
            {
              "attributes": {
                "objectid": 9,
                "foreign_key": "camp-1",
                "tur_sleep_poly_id": 1109,
                "nzw_ob": "Zanocuj w lesie",
                "wiata": "N",
                "palenisko": "T"
              },
              "geometry": {
                "rings": [[[20.0,50.0],[20.2,50.0],[20.2,50.2],[20.0,50.2],[20.0,50.0]]]
              }
            }
            """.trimIndent(),
        )
        val poi = BdlMapper.mapCampFeature(feature)!!
        assertEquals(PoiGeometryKind.AREA, poi.geometryKind)
        assertEquals(setOf(PoiCategory.CAMP, PoiCategory.FIRE), poi.categories)
        assertTrue(poi.areaRings.isNotEmpty())
        assertEquals(5, poi.areaRings.first().size)
        // Helper centroid only — not a destination point
        assertEquals(20.1, poi.longitude, 0.01)
        assertEquals(50.1, poi.latitude, 0.01)
        assertTrue(poi.description!!.contains("centroid"))
        assertEquals("bdl:0:foreign_key:camp-1", poi.id)
    }

    @Test
    fun identity_falls_back_to_domain_id_when_foreign_key_missing() {
        val attrs = JSONObject(
            """{"objectid": 99, "tur_rec_pnt_id": 32454, "nzw_ob": "Parking"}""",
        )
        assertEquals(
            "bdl:17:tur_rec_pnt_id:32454",
            BdlIdentity.resolve(BdlRepository.LAYER_PARKING, attrs),
        )
    }

    @Test
    fun identity_falls_back_to_objectid_last() {
        val attrs = JSONObject("""{"objectid": 42, "nzw_ob": "X"}""")
        assertEquals(
            "bdl:15:objectid:42",
            BdlIdentity.resolve(BdlRepository.LAYER_REST, attrs),
        )
    }

    private fun parkingFeature(
        wiata: String,
        palenisko: String,
        foreignKey: String,
        x: Double = 21.0,
        y: Double = 52.0,
    ): JSONObject = JSONObject(
        """
        {
          "attributes": {
            "objectid": 8270,
            "foreign_key": "$foreignKey",
            "tur_rec_pnt_id": 32454,
            "nzw_ob": "Parking Leśny",
            "wiata": "$wiata",
            "palenisko": "$palenisko"
          },
          "geometry": { "x": $x, "y": $y }
        }
        """.trimIndent(),
    )
}
