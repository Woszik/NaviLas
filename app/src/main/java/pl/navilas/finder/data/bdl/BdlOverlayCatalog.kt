package pl.navilas.finder.data.bdl

import pl.navilas.finder.domain.BdlOverlayGroup

/** MapServer layer IDs → overlay group. Rest-site layers (15/17/19) are excluded. */
object BdlOverlayCatalog {
    data class LayerSpec(
        val layerId: Int,
        val name: String,
        val group: BdlOverlayGroup,
        val requiresFullBdl: Boolean,
        val defaultName: String,
    )

    val LAYERS: List<LayerSpec> = listOf(
        LayerSpec(25, RestSiteRepository.LAYER_NAME_VIEWPOINT, BdlOverlayGroup.VIEW, false, "Punkt widokowy"),
        LayerSpec(27, RestSiteRepository.LAYER_NAME_OTHER, BdlOverlayGroup.OTHER, false, "Obiekt BDL"),
        LayerSpec(28, "Ośrodki edukacji ekologicznej", BdlOverlayGroup.OTHER, true, "Ośrodek edukacji"),
        LayerSpec(29, "Izby edukacji leśnej", BdlOverlayGroup.OTHER, true, "Izba edukacji"),
        LayerSpec(30, "Zielona klasa", BdlOverlayGroup.OTHER, true, "Zielona klasa"),
        LayerSpec(31, "Inne kubaturowe obiekty nienoclegowe", BdlOverlayGroup.OTHER, true, "Obiekt edukacyjny"),
        LayerSpec(26, "Punkty wodowania i cumowania sprzętu wodnego", BdlOverlayGroup.WATER, true, "Wodowanie"),
        LayerSpec(21, "Miejsca/place zabaw dla dzieci", BdlOverlayGroup.PLAY, true, "Plac zabaw"),
        LayerSpec(23, "Inne nienoclegowe obiekty rekreacyjne", BdlOverlayGroup.PLAY, true, "Rekreacja"),
        LayerSpec(1, "Ośrodki szkoleniowo-wypoczynkowe", BdlOverlayGroup.LODGING, true, "Ośrodek"),
        LayerSpec(2, "Hotele", BdlOverlayGroup.LODGING, true, "Hotel"),
        LayerSpec(3, "Kwatery myśliwskie", BdlOverlayGroup.LODGING, true, "Kwatera myśliwska"),
        LayerSpec(4, "Pokoje gościnne", BdlOverlayGroup.LODGING, true, "Pokój gościnny"),
        LayerSpec(5, "Schroniska leśne", BdlOverlayGroup.LODGING, true, "Schronisko"),
        LayerSpec(6, "Miejsca biwakowania", BdlOverlayGroup.LODGING, true, "Biwak"),
        LayerSpec(8, "Pola biwakowe", BdlOverlayGroup.LODGING, true, "Pole biwakowe"),
        LayerSpec(10, "Kempingi", BdlOverlayGroup.LODGING, true, "Kemping"),
        LayerSpec(12, "Obozowiska harcerskie", BdlOverlayGroup.LODGING, true, "Obozowisko"),
    )

    private val byLayerId: Map<Int, LayerSpec> = LAYERS.associateBy { it.layerId }

    fun spec(layerId: Int): LayerSpec? = byLayerId[layerId]

    fun groupForLayer(layerId: Int): BdlOverlayGroup? = byLayerId[layerId]?.group

    fun layersToLoad(fullAvailable: Boolean): List<LayerSpec> =
        LAYERS.filter { fullAvailable || !it.requiresFullBdl }
}
