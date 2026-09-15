package com.frxe.music.playback

sealed interface AndroidAutoBrowseTarget {
    data object Root : AndroidAutoBrowseTarget
    data object Library : AndroidAutoBrowseTarget
    data object Recent : AndroidAutoBrowseTarget
    data object Playlists : AndroidAutoBrowseTarget
    data object Queue : AndroidAutoBrowseTarget
    data class Playlist(val id: Long) : AndroidAutoBrowseTarget
    data class Track(val id: String) : AndroidAutoBrowseTarget
    data class QueueItem(val entryId: String) : AndroidAutoBrowseTarget
}

object AndroidAutoBrowsePolicy {
    const val ROOT = "frxe:auto:root"
    const val LIBRARY = "frxe:auto:library"
    const val RECENT = "frxe:auto:recent"
    const val PLAYLISTS = "frxe:auto:playlists"
    const val QUEUE = "frxe:auto:queue"

    private const val PLAYLIST_PREFIX = "frxe:auto:playlist:"
    private const val TRACK_PREFIX = "frxe:auto:track:"
    private const val QUEUE_ITEM_PREFIX = "frxe:auto:queue-item:"

    val rootCategoryIds = listOf(
        LIBRARY,
        RECENT,
        PLAYLISTS,
        QUEUE
    )

    fun playlistId(id: Long): String =
        "$PLAYLIST_PREFIX$id"

    fun trackId(id: String): String =
        "$TRACK_PREFIX$id"

    fun queueItemId(entryId: String): String =
        "$QUEUE_ITEM_PREFIX$entryId"

    fun parse(mediaId: String): AndroidAutoBrowseTarget? =
        when (mediaId) {
            ROOT -> AndroidAutoBrowseTarget.Root
            LIBRARY -> AndroidAutoBrowseTarget.Library
            RECENT -> AndroidAutoBrowseTarget.Recent
            PLAYLISTS -> AndroidAutoBrowseTarget.Playlists
            QUEUE -> AndroidAutoBrowseTarget.Queue
            else -> parseDynamic(mediaId)
        }

    fun <T> bound(
        items: List<T>,
        limit: Int
    ): List<T> = items.take(limit.coerceAtLeast(0))

    private fun parseDynamic(
        mediaId: String
    ): AndroidAutoBrowseTarget? =
        when {
            mediaId.startsWith(PLAYLIST_PREFIX) ->
                mediaId.removePrefix(PLAYLIST_PREFIX)
                    .toLongOrNull()
                    ?.let(AndroidAutoBrowseTarget::Playlist)

            mediaId.startsWith(TRACK_PREFIX) ->
                mediaId.removePrefix(TRACK_PREFIX)
                    .takeIf(String::isNotBlank)
                    ?.let(AndroidAutoBrowseTarget::Track)

            mediaId.startsWith(QUEUE_ITEM_PREFIX) ->
                mediaId.removePrefix(QUEUE_ITEM_PREFIX)
                    .takeIf(String::isNotBlank)
                    ?.let(AndroidAutoBrowseTarget::QueueItem)

            else -> null
        }
}
