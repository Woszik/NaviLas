package pl.navilas.finder.domain

/** Ordered multi-select of map / list sites. Last id is the primary (card) selection. */
object SiteSelection {
    const val MAX = 8

    fun toggle(ids: List<String>, id: String): List<String> =
        if (id in ids) ids.filter { it != id } else add(ids, id)

    fun add(ids: List<String>, id: String): List<String> {
        val next = ids.filter { it != id } + id
        return if (next.size > MAX) next.takeLast(MAX) else next
    }

    fun removeLast(ids: List<String>): List<String> = ids.dropLast(1)
}
