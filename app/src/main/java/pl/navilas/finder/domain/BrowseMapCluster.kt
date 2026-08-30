package pl.navilas.finder.domain

/** Lightweight client-side marker group used at wide Browse zoom levels. */
data class BrowseMapCluster(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val count: Int,
)
