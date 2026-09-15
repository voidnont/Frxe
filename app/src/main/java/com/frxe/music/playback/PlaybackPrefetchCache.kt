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
    private const val MAX_ENTRIES = 4
    private val lock = Any()
    private val prefetched =
        LinkedHashMap<String, PrefetchedPlaybackTrack>(
            MAX_ENTRIES,
            0.75f,
            true
        )

    fun put(
        track: Track,
        resolver: PlaybackResolverKind,
        headers: Map<String, String>,
        originalSource: String,
        resolvedAtMs: Long = System.currentTimeMillis()
    ) {
        synchronized(lock) {
            prefetched[track.id] =
                PrefetchedPlaybackTrack(
                    track = track,
                    resolver = resolver,
                    headers = headers,
                    originalSource = originalSource,
                    resolvedAtMs = resolvedAtMs
                )

            while (prefetched.size > MAX_ENTRIES) {
                val eldest = prefetched.entries.firstOrNull()?.key ?: break
                prefetched.remove(eldest)
            }
        }
    }

    fun forTrack(
        track: Track,
        nowMs: Long = System.currentTimeMillis()
    ): PrefetchedPlaybackTrack? =
        synchronized(lock) {
            val candidate =
                prefetched[track.id]
                    ?: return@synchronized null

            val originalSource =
                track.originalStreamUrl
                    ?.takeIf(String::isNotBlank)
                    ?: track.streamUrl

            val valid =
                PlaybackPrefetchPolicy.isFresh(
                    resolvedAtMs = candidate.resolvedAtMs,
                    nowMs = nowMs,
                    originalSourceMatches =
                        candidate.originalSource == originalSource
                )

            if (!valid) {
                prefetched.remove(track.id)
                return@synchronized null
            }

            candidate
        }

    fun remove(trackId: String) {
        synchronized(lock) {
            prefetched.remove(trackId)
        }
    }

    fun retain(trackIds: Set<String>) {
        synchronized(lock) {
            val iterator = prefetched.keys.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() !in trackIds) {
                    iterator.remove()
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            prefetched.clear()
        }
    }
}
