package com.frxe.music.playback

import com.frxe.music.model.Track

data class PlaybackLaunchPlan(
    val tracks: List<Track>,
    val currentTrackId: String
)

object PlaybackLaunchPolicy {
    fun plan(
        selected: Track,
        requestedQueue: List<Track>
    ): PlaybackLaunchPlan {
        val base = requestedQueue
            .filter { it.id.isNotBlank() }
            .distinctBy(Track::id)
            .toMutableList()

        if (base.none { it.id == selected.id }) {
            base.add(0, selected)
        }

        if (base.isEmpty()) {
            base += selected
        }

        return PlaybackLaunchPlan(
            tracks = base,
            currentTrackId = selected.id
        )
    }
}
