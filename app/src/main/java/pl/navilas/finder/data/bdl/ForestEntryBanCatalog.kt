package pl.navilas.finder.data.bdl

import pl.navilas.finder.domain.ForestEntryBanReason

/**
 * BDL [Mapa_zakazow_wstepu_do_lasu](https://mapserver.bdl.lasy.gov.pl/arcgis/rest/services/Mapa_zakazow_wstepu_do_lasu/MapServer)
 * feature layers that carry current periodic entry bans.
 *
 * Scale-dependent pairs (coarse / detail) duplicate the same bans; we query the detail LOD only.
 * Layer 0 (litter moisture) is fire-weather context, not a legal ban.
 */
object ForestEntryBanCatalog {
    const val BASE_URL =
        "https://mapserver.bdl.lasy.gov.pl/arcgis/rest/services/Mapa_zakazow_wstepu_do_lasu/MapServer"

    val QUERY_LAYER_IDS: List<Int> = listOf(
        6, // inne przyczyny (detail)
        7, // zabiegi SOR (detail)
        2, // zagrożenie pożarowe (detail)
    )

    const val OUT_FIELDS =
        "objectid,kod_nadl,nazwa_nadl,data,opis,kod,nazwa_rdlp,lesnictwo,kod_oddzialu,data_koncowa"

    fun reasonFor(layerId: Int, kod: String?): ForestEntryBanReason = when (layerId) {
        1, 6 -> ForestEntryBanReason.OTHER
        3, 7 -> ForestEntryBanReason.PESTICIDE
        4, 2 -> ForestEntryBanReason.FIRE
        else -> reasonFromKod(kod)
    }

    fun reasonFromKod(kod: String?): ForestEntryBanReason {
        val normalized = kod.orEmpty().trim().lowercase()
        return when {
            normalized.contains("pożar") || normalized.contains("pozar") ->
                ForestEntryBanReason.FIRE
            normalized.contains("środków ochrony") ||
                normalized.contains("srodkow ochrony") ||
                normalized.contains("zabieg") ->
                ForestEntryBanReason.PESTICIDE
            else -> ForestEntryBanReason.OTHER
        }
    }
}
