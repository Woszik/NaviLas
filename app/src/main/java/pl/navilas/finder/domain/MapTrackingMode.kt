package pl.navilas.finder.domain

/** Camera follow mode for the live GPS puck while driving. */
enum class MapTrackingMode {
    /** No continuous follow; map is free. */
    OFF,
    /** Camera keeps GPS at a fixed screen point (map moves under the puck). */
    TRACKING,
    /** GPS marker may still update; camera does not follow until resumed. */
    PAUSED,
}
