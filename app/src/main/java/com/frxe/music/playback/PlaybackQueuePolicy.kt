package com.frxe.music.playback

object PlaybackQueuePolicy {
    fun replace(
        entries: List<PlaybackQueueEntry>,
        currentEntryId: String? = entries.firstOrNull()?.entryId
    ): PlaybackQueueState {
        if (entries.isEmpty()) return PlaybackQueueState()
        val index = entries.indexOfFirst { it.entryId == currentEntryId }
            .takeIf { it >= 0 } ?: 0
        return PlaybackQueueState(entries, index)
    }

    fun append(
        state: PlaybackQueueState,
        entry: PlaybackQueueEntry
    ): PlaybackQueueState {
        val next = state.entries + entry
        return if (state.currentIndex >= 0) {
            state.copy(entries = next)
        } else {
            PlaybackQueueState(next, 0)
        }
    }

    fun playNext(
        state: PlaybackQueueState,
        entry: PlaybackQueueEntry
    ): PlaybackQueueState {
        if (
            state.entries.isEmpty() ||
            state.currentIndex !in state.entries.indices
        ) {
            return PlaybackQueueState(
                listOf(entry),
                0
            )
        }

        val insertAt =
            (state.currentIndex + 1)
                .coerceAtMost(state.entries.size)

        val next =
            state.entries.toMutableList()
                .apply {
                    add(insertAt, entry)
                }

        return state.copy(entries = next)
    }

    fun select(
        state: PlaybackQueueState,
        entryId: String
    ): PlaybackQueueState {
        val index = state.entries.indexOfFirst {
            it.entryId == entryId
        }

        return if (index >= 0) {
            state.copy(currentIndex = index)
        } else {
            state
        }
    }

    fun remove(
        state: PlaybackQueueState,
        entryId: String
    ): PlaybackQueueState {
        val removeIndex = state.entries.indexOfFirst {
            it.entryId == entryId
        }
        if (removeIndex < 0) return state

        val currentId = state.current?.entryId
        val next = state.entries.toMutableList()
            .apply {
                removeAt(removeIndex)
            }

        if (next.isEmpty()) {
            return PlaybackQueueState()
        }

        if (currentId != entryId) {
            val currentIndex = next.indexOfFirst {
                it.entryId == currentId
            }

            return PlaybackQueueState(
                next,
                currentIndex.coerceAtLeast(0)
            )
        }

        return PlaybackQueueState(
            next,
            removeIndex.coerceAtMost(next.lastIndex)
        )
    }

    fun move(
        state: PlaybackQueueState,
        fromIndex: Int,
        toIndex: Int
    ): PlaybackQueueState {
        if (
            fromIndex !in state.entries.indices ||
            toIndex !in state.entries.indices ||
            fromIndex == toIndex
        ) {
            return state
        }

        val currentId = state.current?.entryId
        val next = state.entries.toMutableList()
        val moved = next.removeAt(fromIndex)
        next.add(toIndex, moved)

        val currentIndex = currentId
            ?.let { id ->
                next.indexOfFirst {
                    it.entryId == id
                }
            }
            ?: -1

        return PlaybackQueueState(
            next,
            currentIndex
        )
    }

    fun advance(
        state: PlaybackQueueState,
        repeatMode: QueueRepeatMode,
        shufflePickIndex: Int? = null
    ): PlaybackQueueState? {
        if (state.current == null) return null

        if (repeatMode == QueueRepeatMode.One) {
            return state
        }

        val nextIndex = state.currentIndex + 1

        if (nextIndex <= state.entries.lastIndex) {
            val pick = shufflePickIndex

            if (
                pick != null &&
                pick in nextIndex..state.entries.lastIndex
            ) {
                val next = state.entries.toMutableList()
                val selected = next.removeAt(pick)
                next.add(nextIndex, selected)

                return PlaybackQueueState(
                    next,
                    nextIndex
                )
            }

            return state.copy(
                currentIndex = nextIndex
            )
        }

        return if (repeatMode == QueueRepeatMode.All) {
            state.copy(currentIndex = 0)
        } else {
            null
        }
    }

    fun previous(
        state: PlaybackQueueState,
        repeatMode: QueueRepeatMode
    ): PlaybackQueueState? {
        if (state.current == null) return null

        if (repeatMode == QueueRepeatMode.One) {
            return state
        }

        if (state.currentIndex > 0) {
            return state.copy(
                currentIndex = state.currentIndex - 1
            )
        }

        return if (repeatMode == QueueRepeatMode.All) {
            state.copy(
                currentIndex = state.entries.lastIndex
            )
        } else {
            null
        }
    }
}
