package pl.navilas.finder.domain

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Builds a Google web query from a BDL place name (name-first).
 * Strips generic BDL prefixes; keeps distinctive cores; enriches thin cores
 * with optional nadleśnictwo when already known.
 */
object SiteWebSearch {
    private val PREFIXES = listOf(
        Regex("""^Miejsce\s+postoju\s+pojazd[oó]w[\s\-–—:]*""", RegexOption.IGNORE_CASE),
        Regex("""^Miejsce\s+postoju[\s\-–—:]*""", RegexOption.IGNORE_CASE),
        Regex("""^Miejsce\s+odpoczynku\s+""", RegexOption.IGNORE_CASE),
        Regex("""^Miejsce\s+odpocynku\s+""", RegexOption.IGNORE_CASE),
        Regex("""^Miejsce\s+wypoczynku\s+""", RegexOption.IGNORE_CASE),
        Regex("""^Parking\s+le[sś]ny\s+""", RegexOption.IGNORE_CASE),
        Regex("""^Parking\s+""", RegexOption.IGNORE_CASE),
    )

    private val RICH_HINT = Regex(
        """uroczysk|źród|zrod|ścieżk|sciezk|jezior|jeziork|edukacyjn|rezerwat|pomnik""",
        RegexOption.IGNORE_CASE,
    )

    fun coreName(rawName: String): String {
        var name = rawName.trim()
        if (name.isEmpty()) return name
        for (prefix in PREFIXES) {
            name = name.replace(prefix, "").trim()
        }
        name = name.trim('"', '„', '”', '\'', ' ', '\t')
        return name.ifBlank { rawName.trim() }
    }

    fun isRichCore(core: String): Boolean {
        val trimmed = core.trim()
        if (trimmed.isEmpty()) return false
        if (RICH_HINT.containsMatchIn(trimmed)) return true
        val tokens = trimmed.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.size >= 3) return true
        if (tokens.size >= 2 && trimmed.length >= 16) return true
        return false
    }

    /**
     * @param inspectorateName raw BDL/WMS name without "Nadleśnictwo" prefix, or null.
     */
    fun buildQuery(rawName: String, inspectorateName: String? = null): String {
        val raw = rawName.trim()
        if (raw.isEmpty()) return raw
        val core = coreName(raw)
        if (isRichCore(core)) return core
        val base = if (core.equals(raw, ignoreCase = true)) raw else raw
        val admin = inspectorateName?.trim()?.takeIf { it.isNotEmpty() } ?: return base
        if (base.contains(admin, ignoreCase = true)) return base
        return "$base Nadleśnictwo $admin"
    }

    fun googleSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        return "https://www.google.com/search?hl=pl&q=$encoded"
    }
}
