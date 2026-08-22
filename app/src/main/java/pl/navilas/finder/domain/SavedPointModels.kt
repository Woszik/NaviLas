package pl.navilas.finder.domain

enum class ListViewMode {
    /** Current BDL search results. */
    SEARCH,
    /** User-saved points stored on device. */
    SAVED,
}

data class SavedPointCategory(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
)

/**
 * Local bookmark for a [RestSite], with optional user categories and comment.
 * A point may belong to zero, one, or many [SavedPointCategory] entries.
 */
data class SavedPoint(
    val site: RestSite,
    val savedAtMs: Long,
    val categoryIds: Set<String> = emptySet(),
    val userComment: String?,
)
