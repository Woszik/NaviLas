package pl.navilas.finder.data.bdl

data class RestSearchOutcome(
    val bundle: RestSearchBundle,
    val fromSessionCache: Boolean = false,
)
