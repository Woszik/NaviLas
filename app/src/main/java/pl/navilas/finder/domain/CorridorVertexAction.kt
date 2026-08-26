package pl.navilas.finder.domain

/** Pending vertex edit on the corridor polyline (map gestures). */
sealed class CorridorVertexAction {
    data class Move(val index: Int) : CorridorVertexAction()
    data class InsertAfter(val index: Int) : CorridorVertexAction()
}
