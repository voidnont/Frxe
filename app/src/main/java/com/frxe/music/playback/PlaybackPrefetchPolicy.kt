package com.frxe.music.playback

import com.frxe.music.source.PlaybackResolverKind
import com.frxe.music.source.PlaybackResolverOrder

object PlaybackPrefetchPolicy {
    const val MAX_AGE_MS:
        Long = 120_000L

    val resolverOrder: List<PlaybackResolverKind>
        get() = PlaybackResolverOrder.local

    fun isFresh(
        resolvedAtMs: Long,
        nowMs: Long,
        originalSourceMatches: Boolean
    ): Boolean {
        if (
            !originalSourceMatches ||
            resolvedAtMs <= 0L
        ) {
            return false
        }

        val age =
            nowMs - resolvedAtMs

        return age in
            0L..MAX_AGE_MS
    }
}
