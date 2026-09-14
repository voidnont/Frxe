package com.frxe.music.playback

data class PlaybackQueueEntry(
    val entryId: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val streamUrl: String,
    val durationMs: Long,
    val artworkSeed: Int,
    val artworkUrl: String? = null,
    val downloadUrl: String? = null,
    val originalStreamUrl: String? = null
)

data class PlaybackQueueState(
    val entries: List<PlaybackQueueEntry> = emptyList(),
    val currentIndex: Int = -1
) {
    val current: PlaybackQueueEntry?
        get() = entries.getOrNull(currentIndex)
}

data class PlaybackStartRequest(
    val entryId: String,
    val requestedAtMs: Long,
    val requestId: Long
)

enum class QueueRepeatMode { Off, All, One }
