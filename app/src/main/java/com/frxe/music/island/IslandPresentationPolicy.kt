package com.frxe.music.island

object IslandPresentationPolicy {
    const val showInApp: Boolean = false

    fun showFloating(
        isForeground: Boolean,
        floatingEnabled: Boolean,
        overlayPermissionGranted: Boolean,
        hasTrack: Boolean,
        dismissed: Boolean
    ): Boolean =
        !isForeground &&
            floatingEnabled &&
            overlayPermissionGranted &&
            hasTrack &&
            !dismissed
}
