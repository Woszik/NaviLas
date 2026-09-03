package pl.navilas.finder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.data.bdl.ForestAdminCatalog
import pl.navilas.finder.data.bdl.ForestAdminLoader
import pl.navilas.finder.domain.ForestAdmin

class ForestAdminTest {
    @Test
    fun catalog_uses_wms_bdl_admin_layers() {
        assertEquals(
            "https://mapserver.bdl.lasy.gov.pl/arcgis/rest/services/WMS_BDL/MapServer",
            ForestAdminCatalog.BASE_URL,
        )
        assertEquals(1, ForestAdminCatalog.LAYER_INSPECTORATE)
        assertEquals(2, ForestAdminCatalog.LAYER_FORESTRY)
    }

    @Test
    fun latest_attributes_picks_newest_year() {
        val body = """
            {"features":[
              {"attributes":{"inspectorate_name":"Stare","a_year":2024}},
              {"attributes":{"inspectorate_name":"Koszęcin                      ","a_year":2026}}
            ]}
        """.trimIndent()
        val attrs = ForestAdminLoader.latestAttributes(body)!!
        assertEquals("Koszęcin                      ", attrs.getString("inspectorate_name"))
        assertEquals(2026, attrs.getInt("a_year"))
    }

    @Test
    fun merge_trims_and_prefers_inspectorate_address() {
        val inspectorate = JSONObject(
            """{"inspectorate_name":"Koszęcin                      ","inspectorate_adres":null,"a_year":2026}""",
        )
        val forestry = JSONObject(
            """{"forest_range_name":"Piłka                         ","inspectorate_name":"Koszęcin                      ","region_name":"Katowice                      ","a_year":2026}""",
        )
        val admin = ForestAdminLoader.merge(inspectorate, forestry)
        assertEquals("Koszęcin", admin.inspectorateName)
        assertNull(admin.inspectorateAddress)
        assertEquals("Piłka", admin.forestryName)
        assertEquals("Katowice", admin.regionName)
        assertEquals(2026, admin.year)
        assertEquals(
            listOf("Nadleśnictwo Koszęcin", "Leśnictwo Piłka", "RDLP Katowice"),
            admin.linesPl(),
        )
    }

    @Test
    fun empty_admin_has_no_lines() {
        assertTrue(ForestAdmin(null, null, null, null, null).isEmpty())
        assertTrue(ForestAdmin(null, null, null, null, null).linesPl().isEmpty())
    }
}
