package pl.navilas.finder.data.bdl

/**
 * BDL [WMS_BDL](https://mapserver.bdl.lasy.gov.pl/arcgis/rest/services/WMS_BDL/MapServer)
 * administrative polygons (not Czas w Las tourist points).
 */
object ForestAdminCatalog {
    const val BASE_URL =
        "https://mapserver.bdl.lasy.gov.pl/arcgis/rest/services/WMS_BDL/MapServer"

    const val LAYER_INSPECTORATE = 1
    const val LAYER_FORESTRY = 2

    const val INSPECTORATE_FIELDS =
        "inspectorate_name,inspectorate_adres,adress_forest,a_year,region_cd"
    const val FORESTRY_FIELDS =
        "forest_range_name,inspectorate_name,region_name,a_year,adress_forest"
}
