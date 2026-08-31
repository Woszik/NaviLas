package pl.navilas.finder.domain

internal const val ENTRY_BAN_REFRESH_STALE_MS = 7L * 24 * 60 * 60 * 1000
internal const val ENTRY_BAN_REFRESH_SNOOZE_MS = 24L * 60 * 60 * 1000

data class EntryBanRefreshOffer(
    val downloadedAt: Long,
    val count: Int,
)
