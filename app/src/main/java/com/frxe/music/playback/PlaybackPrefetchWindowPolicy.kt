package com.frxe.music.playback

object PlaybackPrefetchWindowPolicy {
    private const val DEFAULT_WINDOW_SIZE = 2

    fun nextIndexes(
        currentIndex: Int,
        totalCount: Int,
        windowSize: Int = DEFAULT_WINDOW_SIZE
    ): List<Int> {
        if (
            currentIndex < -1 ||
            totalCount <= 0 ||
            windowSize <= 0
        ) {
            return emptyList()
        }

        val start = (currentIndex + 1).coerceAtLeast(0)
        val endExclusive =
            (start + windowSize).coerceAtMost(totalCount)

        if (start >= endExclusive) return emptyList()

        return (start until endExclusive).toList()
    }
}
