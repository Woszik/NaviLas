package pl.navilas.finder

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.navilas.finder.update.AppUpdateChecker

class AppUpdateCheckerTest {
    @Test
    fun manifestFetchUrl_appendsCacheBustQuery() {
        assertEquals(
            "https://example.com/latest.json?t=123",
            AppUpdateChecker.manifestFetchUrl("https://example.com/latest.json", 123L),
        )
    }

    @Test
    fun manifestFetchUrl_preservesExistingQuery() {
        assertEquals(
            "https://example.com/latest.json?ref=main&t=456",
            AppUpdateChecker.manifestFetchUrl("https://example.com/latest.json?ref=main", 456L),
        )
    }
}
