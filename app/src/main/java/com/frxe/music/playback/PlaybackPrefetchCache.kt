package com.frxe.music.playback

import com.frxe.music.model.Track
import com.frxe.music.source.PlaybackResolverKind

data class PrefetchedPlaybackTrack(
    val track: Track,
    val resolver: PlaybackResolverKind,
    val headers: Map<String, String>,
    val originalSource: String,
    val resolvedAtMs: Long
)

object PlaybackPrefetchCache {
    private val lock = Any()

    private var prefetched:
        PrefetchedPlaybackTrack? = null

    fun put(
        track: Track,
        resolver: PlaybackResolverKind,
        headers: Map<String, String>,
        originalSource: String,
        resolvedAtMs: Long =
            System.currentTimeMillis()
    ) {
        synchronized(lock) {
            prefetched =
                PrefetchedPlaybackTrack(
                    track = track,
                    resolver = resolver,
                    headers = headers,
                    originalSource = originalSource,
                    resolvedAtMs = resolvedAtMs
                )
        }
    }

    fun forTrack(
        track: Track,
        nowMs: Long =
            System.currentTimeMillis()
    ): PrefetchedPlaybackTrack? =
        synchronized(lock) {
            val candidate =
                prefetched
                    ?: return@synchronized null

            val originalSource =
                track.originalStreamUrl
                    ?.takeIf(
                        String::isNotBlank
                    )
                    ?: track.streamUrl

            candidate.takeIf {
                it.track.id == track.id &&
                    PlaybackPrefetchPolicy
                        .isFresh(
                            resolvedAtMs =
                                it.resolvedAtMs,
                            nowMs = nowMs,
                            originalSourceMatches =
                                it.originalSource ==
                                originalSource
                        )
            }
        }

    fun clear() {
        synchronized(lock) {
            prefetched = null
        }
    }
}
