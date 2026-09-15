package com.frxe.music.playback

data class SystemMediaQueueAvailability(
    val previous: Boolean,
    val next: Boolean
)

object SystemMediaQueuePolicy {
    fun availability(
        state: PlaybackQueueState,
        repeatMode: QueueRepeatMode
    ): SystemMediaQueueAvailability {
        if (
            state.current == null ||
            state.entries.size <= 1 ||
            repeatMode == QueueRepeatMode.One
        ) {
            return SystemMediaQueueAvailability(
                previous = false,
                next = false
            )
        }

        return when (repeatMode) {
            QueueRepeatMode.All ->
                SystemMediaQueueAvailability(
                    previous = true,
                    next = true
                )

            QueueRepeatMode.Off ->
                SystemMediaQueueAvailability(
                    previous = state.currentIndex > 0,
                    next = state.currentIndex < state.entries.lastIndex
                )

            QueueRepeatMode.One ->
                SystemMediaQueueAvailability(
                    previous = false,
                    next = false
                )
        }
    }
}
