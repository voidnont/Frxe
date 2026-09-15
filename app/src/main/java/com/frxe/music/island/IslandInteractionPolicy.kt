package com.frxe.music.island

object IslandInteractionPolicy {
    fun nextExpandedState(currentlyExpanded: Boolean): Boolean =
        !currentlyExpanded
}
