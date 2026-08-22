package pl.navilas.finder.data.bdl

import org.json.JSONObject

/**
 * Builds stable internal POI ids from BDL attributes.
 *
 * ## Decision (Checkpoint 1A)
 * Analysed fields: `foreign_key`, `objectid`, `tur_rec_pnt_id` / `tur_sleep_poly_id`, `inv_nr`.
 *
 * - **foreign_key** (UUID): preferred — present on layers 0/15/17 in live samples; looks like a
 *   cross-system business key (Czas w Las / BDL), more stable than ArcGIS OID.
 * - **tur_rec_pnt_id** (15/17) / **tur_sleep_poly_id** (0): secondary domain ids when foreign_key
 *   is missing; field name depends on layer geometry type.
 * - **inv_nr**: often null — not used as primary key.
 * - **objectid**: last resort only — ArcGIS OID may change after republish/reindex.
 *
 * Format: `bdl:{layerId}:{scheme}:{value}` so ids stay unique across layers.
 *
 * See also: docs/POI_IDENTITY.md
 */
object BdlIdentity {
    fun resolve(layerId: Int, attrs: JSONObject): String {
        nonBlank(attrs, "foreign_key")?.let { fk ->
            return format(layerId, "foreign_key", fk)
        }

        val domainField = when (layerId) {
            BdlRepository.LAYER_CAMP -> "tur_sleep_poly_id"
            else -> "tur_rec_pnt_id"
        }
        domainValue(attrs, domainField)?.let { domainId ->
            return format(layerId, domainField, domainId)
        }

        domainValue(attrs, "objectid")?.let { oid ->
            return format(layerId, "objectid", oid)
        }

        error("Brak użytecznego identyfikatora BDL (layer=$layerId)")
    }

    private fun format(layerId: Int, scheme: String, value: String): String =
        "bdl:$layerId:$scheme:$value"

    private fun nonBlank(attrs: JSONObject, field: String): String? =
        attrs.optString(field).takeIf { it.isNotBlank() && !attrs.isNull(field) }

    private fun domainValue(attrs: JSONObject, field: String): String? {
        if (attrs.isNull(field) || !attrs.has(field)) return null
        val raw = attrs.opt(field) ?: return null
        val text = raw.toString().trim()
        return text.takeIf { it.isNotEmpty() && it != "null" }
    }
}
