package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.navilas.finder.nav.ExternalNavApps

class ExternalNavAppsTest {
    @Test
    fun play_store_urls() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=net.osmand.plus",
            ExternalNavApps.playStoreHttps(ExternalNavApps.OSMAND_PLUS),
        )
        assertEquals(
            "market://details?id=gr.talent.cruiser",
            ExternalNavApps.playStoreMarketUri(ExternalNavApps.CRUISER),
        )
    }
}
