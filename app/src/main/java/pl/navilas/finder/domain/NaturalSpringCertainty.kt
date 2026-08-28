package pl.navilas.finder.domain

/** Evidence that a natural spring is linked to a rest site (BDL text / layer 27). */
enum class NaturalSpringCertainty {
    /** Structured `zrodlo=T` nearby or clear on-site amenity text in `inne_atr`. */
    CERTAIN,
    /** Spring hinted only by name / weak description — show as uncertain. */
    UNCERTAIN,
}

fun NaturalSpringCertainty.labelPl(): String = when (this) {
    NaturalSpringCertainty.CERTAIN -> "Źródło"
    NaturalSpringCertainty.UNCERTAIN -> "Źródło (niepewne)"
}

/** BDL feature flags plus optional natural-spring badge for list / card UI. */
fun RestSite.featureSummaryPl(blankFallback: String = "Cechy BDL: brak flag"): String {
    val parts = features.map { it.labelPl }.toMutableList()
    naturalSpring?.let { parts += it.labelPl() }
    return parts.joinToString(" · ").ifBlank { blankFallback }
}

/** Prefer stronger evidence when combining self + nearby amenity. */
fun NaturalSpringCertainty.mergeWith(other: NaturalSpringCertainty?): NaturalSpringCertainty {
    if (other == null) return this
    if (this == NaturalSpringCertainty.CERTAIN || other == NaturalSpringCertainty.CERTAIN) {
        return NaturalSpringCertainty.CERTAIN
    }
    return NaturalSpringCertainty.UNCERTAIN
}
