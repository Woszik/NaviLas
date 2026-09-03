package pl.navilas.finder.domain

/** PGL LP unit that manages the forest around a rest site (BDL WMS_BDL polygons). */
data class ForestAdmin(
    val inspectorateName: String?,
    val inspectorateAddress: String?,
    val forestryName: String?,
    val regionName: String?,
    val year: Int?,
) {
    fun isEmpty(): Boolean =
        inspectorateName.isNullOrBlank() && forestryName.isNullOrBlank()

    fun linesPl(): List<String> = buildList {
        inspectorateName?.let { add("Nadleśnictwo $it") }
        inspectorateAddress?.let { add(it) }
        forestryName?.let { add("Leśnictwo $it") }
        regionName?.let { add("RDLP $it") }
    }
}

sealed class ForestAdminLookup {
    data class Found(val admin: ForestAdmin) : ForestAdminLookup()
    data object OutsideLp : ForestAdminLookup()
    data class Failed(val message: String) : ForestAdminLookup()
}
