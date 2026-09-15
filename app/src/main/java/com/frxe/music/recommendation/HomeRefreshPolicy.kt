package com.frxe.music.recommendation

import com.frxe.music.model.Track

object HomeRefreshPolicy {
    fun key(
        history: List<Track>,
        library: List<Track>,
        likedIds: Set<String>
    ): String = buildString {
        append("h:")
        history.asSequence()
            .map(Track::id)
            .distinct()
            .take(24)
            .forEach {
                append(it)
                append('|')
            }
        append(";l:")
        library.asSequence()
            .map(Track::id)
            .distinct()
            .take(24)
            .forEach {
                append(it)
                append('|')
            }
        append(";k:")
        likedIds.sorted()
            .take(64)
            .forEach {
                append(it)
                append('|')
            }
    }

    fun shouldRefresh(
        previousKey: String?,
        nextKey: String
    ): Boolean = previousKey != nextKey
}
