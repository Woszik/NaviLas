package pl.navilas.finder.domain

/** Card / list title without doubling BDL prefixes like „Miejsce odpoczynku …”. */
object RestSiteTitles {
    fun cardTitle(name: String): String {
        val trimmed = name.trim()
        if (trimmed.startsWith("Miejsce ", ignoreCase = true)) {
            return trimmed
        }
        return "Miejsce odpoczynku „$trimmed”"
    }
}
