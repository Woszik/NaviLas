package pl.navilas.finder.data.bdl

import pl.navilas.finder.domain.NaturalSpringCertainty
import java.text.Normalizer

/**
 * Classifies natural-spring evidence from BDL text fields (layer 15).
 * Layer-27 `zrodlo=T` is applied separately by the browse loader (nearby → CERTAIN).
 */
object NaturalSpringClassifier {
    private val proximity = listOf(
        "w poblizu",
        "niedalekiej",
        "niedaleko",
        "w odleglosci",
        "w niedalekiej",
    )

    private val weakContext = listOf(
        "rezerwat",
        "historyczn",
        "droga krzyzowa",
        "pomnik przyrody",
    )

    /**
     * @return CERTAIN / UNCERTAIN / null (no spring / rejected proximity-only).
     */
    fun evaluate(
        name: String?,
        uwagi: String?,
        inneAtr: String?,
    ): NaturalSpringCertainty? {
        val atr = normalize(inneAtr)
        val nm = normalize(name)
        val uw = normalize(uwagi)

        if (isProximitySpringMention(atr) || isProximitySpringMention(uw)) {
            return null
        }

        if (isCertainInneAtr(atr)) {
            return NaturalSpringCertainty.CERTAIN
        }

        if (hasSpringToken(nm) || hasSpringToken(uw) || hasSpringToken(atr)) {
            return NaturalSpringCertainty.UNCERTAIN
        }
        return null
    }

    private fun isCertainInneAtr(atr: String): Boolean {
        if (atr.isBlank() || isBlankish(atr)) return false
        if (!hasSpringToken(atr)) return false
        // Reserve / historical place name in inne_atr → not hard amenity confirmation.
        if (weakContext.any { atr.contains(it) }) return false
        // Clear amenity wording (e.g. Krywałd: „Źródełko artezyjskie”).
        return atr.contains("artezyj") || atr.contains("zrodelk")
    }

    private fun isProximitySpringMention(text: String): Boolean {
        if (!hasSpringToken(text)) return false
        return proximity.any { text.contains(it) }
    }

    fun hasSpringToken(text: String): Boolean {
        if (text.isBlank()) return false
        return text.contains("artezyj") ||
            text.contains("zrodel") ||
            text.contains("zrodl")
    }

    private fun isBlankish(text: String): Boolean =
        text == "nie" || text == "brak" || text == "nie dotyczy" || text == "x" || text == "-"

    /** Lowercase ASCII fold for Polish diacritics. */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val replaced = raw.trim()
            .replace('ł', 'l')
            .replace('Ł', 'L')
        val nfd = Normalizer.normalize(replaced, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{Mn}+"), "").lowercase()
    }
}
