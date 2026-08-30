package pl.navilas.finder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.domain.BDL_REFRESH_SNOOZE_MS
import pl.navilas.finder.domain.BDL_REFRESH_STALE_MS
import pl.navilas.finder.domain.shouldOfferBdlRefresh

class BdlRefreshPolicyTest {
    private val now = 1_777_000_000_000L

    @Test
    fun ready_database_older_than_30_days_is_offered() {
        assertTrue(
            shouldOfferBdlRefresh(
                isReady = true,
                downloadedAt = now - BDL_REFRESH_STALE_MS,
                nowMs = now,
                snoozeUntilMs = 0L,
            ),
        )
    }

    @Test
    fun missing_or_unready_database_is_not_offered() {
        assertFalse(
            shouldOfferBdlRefresh(
                isReady = false,
                downloadedAt = now - BDL_REFRESH_STALE_MS * 2,
                nowMs = now,
                snoozeUntilMs = 0L,
            ),
        )
        assertFalse(
            shouldOfferBdlRefresh(
                isReady = true,
                downloadedAt = null,
                nowMs = now,
                snoozeUntilMs = 0L,
            ),
        )
        assertFalse(
            shouldOfferBdlRefresh(
                isReady = true,
                downloadedAt = 0L,
                nowMs = now,
                snoozeUntilMs = 0L,
            ),
        )
    }

    @Test
    fun fresh_database_is_not_offered() {
        assertFalse(
            shouldOfferBdlRefresh(
                isReady = true,
                downloadedAt = now - BDL_REFRESH_STALE_MS + 1,
                nowMs = now,
                snoozeUntilMs = 0L,
            ),
        )
    }

    @Test
    fun active_snooze_hides_offer_until_it_expires() {
        assertFalse(
            shouldOfferBdlRefresh(
                isReady = true,
                downloadedAt = now - BDL_REFRESH_STALE_MS * 2,
                nowMs = now,
                snoozeUntilMs = now + BDL_REFRESH_SNOOZE_MS,
            ),
        )
        assertTrue(
            shouldOfferBdlRefresh(
                isReady = true,
                downloadedAt = now - BDL_REFRESH_STALE_MS * 2,
                nowMs = now,
                snoozeUntilMs = now,
            ),
        )
    }
}
