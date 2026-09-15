package com.frxe.music.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.frxe.music.data.FrxeDatabase
import com.frxe.music.model.Track
import kotlinx.coroutines.flow.first

class AndroidAutoLibrary(
    context: Context
) {
    private val applicationContext = context.applicationContext
    private val dao
        get() = FrxeDatabase.get(applicationContext).libraryDao()

    fun rootItem(): MediaItem =
        browsableItem(
            mediaId = AndroidAutoBrowsePolicy.ROOT,
            title = "FRXE"
        )

    suspend fun children(
        parentId: String
    ): List<MediaItem> =
        when (val target = AndroidAutoBrowsePolicy.parse(parentId)) {
            AndroidAutoBrowseTarget.Root -> rootCategories()

            AndroidAutoBrowseTarget.Library ->
                AndroidAutoBrowsePolicy.bound(
                    dao.observeAll().first().map { it.asTrack() },
                    CATEGORY_LIMIT
                ).map(::playableTrackItem)

            AndroidAutoBrowseTarget.Recent ->
                AndroidAutoBrowsePolicy.bound(
                    dao.observeHistory().first()
                        .map { it.asTrack() }
                        .distinctBy(Track::id),
                    RECENT_LIMIT
                ).map(::playableTrackItem)

            AndroidAutoBrowseTarget.Playlists ->
                AndroidAutoBrowsePolicy.bound(
                    dao.observePlaylists().first(),
                    CATEGORY_LIMIT
                ).map { playlist ->
                    browsableItem(
                        mediaId = AndroidAutoBrowsePolicy.playlistId(
                            playlist.id
                        ),
                        title = playlist.name
                    )
                }

            AndroidAutoBrowseTarget.Queue ->
                AndroidAutoBrowsePolicy.bound(
                    PlaybackQueueStore.state.value.entries,
                    CATEGORY_LIMIT
                ).map(::playableQueueItem)

            is AndroidAutoBrowseTarget.Playlist ->
                AndroidAutoBrowsePolicy.bound(
                    dao.playlistTracks(target.id)
                        .map { it.asTrack() },
                    CATEGORY_LIMIT
                ).map(::playableTrackItem)

            else -> emptyList()
        }

    suspend fun item(
        mediaId: String
    ): MediaItem? =
        when (val target = AndroidAutoBrowsePolicy.parse(mediaId)) {
            AndroidAutoBrowseTarget.Root -> rootItem()
            AndroidAutoBrowseTarget.Library -> categoryItem(target)
            AndroidAutoBrowseTarget.Recent -> categoryItem(target)
            AndroidAutoBrowseTarget.Playlists -> categoryItem(target)
            AndroidAutoBrowseTarget.Queue -> categoryItem(target)

            is AndroidAutoBrowseTarget.Playlist ->
                dao.playlist(target.id)?.let { playlist ->
                    browsableItem(
                        mediaId = AndroidAutoBrowsePolicy.playlistId(
                            playlist.id
                        ),
                        title = playlist.name
                    )
                }

            is AndroidAutoBrowseTarget.Track ->
                track(mediaId)?.let(::playableTrackItem)

            is AndroidAutoBrowseTarget.QueueItem ->
                PlaybackQueueStore.state.value.entries
                    .firstOrNull { it.entryId == target.entryId }
                    ?.let(::playableQueueItem)

            null -> null
        }

    suspend fun track(
        mediaId: String
    ): Track? =
        when (val target = AndroidAutoBrowsePolicy.parse(mediaId)) {
            is AndroidAutoBrowseTarget.Track ->
                allKnownTracks()
                    .firstOrNull { it.id == target.id }

            is AndroidAutoBrowseTarget.QueueItem ->
                PlaybackQueueStore.state.value.entries
                    .firstOrNull { it.entryId == target.entryId }
                    ?.let(::queueEntryTrack)

            else -> null
        }

    suspend fun search(
        query: String
    ): List<MediaItem> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()

        return AndroidAutoBrowsePolicy.bound(
            allKnownTracks()
                .filter { track ->
                    track.title.contains(
                        normalized,
                        ignoreCase = true
                    ) ||
                        track.artist.contains(
                            normalized,
                            ignoreCase = true
                        ) ||
                        track.album.contains(
                            normalized,
                            ignoreCase = true
                        )
                }
                .distinctBy(Track::id),
            SEARCH_LIMIT
        ).map(::playableTrackItem)
    }

    private fun rootCategories(): List<MediaItem> =
        listOf(
            categoryItem(AndroidAutoBrowseTarget.Library),
            categoryItem(AndroidAutoBrowseTarget.Recent),
            categoryItem(AndroidAutoBrowseTarget.Playlists),
            categoryItem(AndroidAutoBrowseTarget.Queue)
        )

    private fun categoryItem(
        target: AndroidAutoBrowseTarget
    ): MediaItem =
        when (target) {
            AndroidAutoBrowseTarget.Library ->
                browsableItem(
                    AndroidAutoBrowsePolicy.LIBRARY,
                    "Library"
                )

            AndroidAutoBrowseTarget.Recent ->
                browsableItem(
                    AndroidAutoBrowsePolicy.RECENT,
                    "Recently Played"
                )

            AndroidAutoBrowseTarget.Playlists ->
                browsableItem(
                    AndroidAutoBrowsePolicy.PLAYLISTS,
                    "Playlists"
                )

            AndroidAutoBrowseTarget.Queue ->
                browsableItem(
                    AndroidAutoBrowsePolicy.QUEUE,
                    "Queue"
                )

            else -> error("Not a root category: $target")
        }

    private suspend fun allKnownTracks(): List<Track> {
        val library = dao.observeAll().first()
            .map { it.asTrack() }
        val recent = dao.observeHistory().first()
            .map { it.asTrack() }
        val queue = PlaybackQueueStore.state.value.entries
            .map(::queueEntryTrack)
        val playlistTracks = dao.observePlaylists().first()
            .flatMap { playlist ->
                dao.playlistTracks(playlist.id)
                    .map { it.asTrack() }
            }

        return buildList {
            addAll(queue)
            addAll(library)
            addAll(recent)
            addAll(playlistTracks)
        }.distinctBy(Track::id)
    }

    private fun playableTrackItem(
        track: Track
    ): MediaItem =
        playableItem(
            mediaId = AndroidAutoBrowsePolicy.trackId(track.id),
            track = track
        )

    private fun playableQueueItem(
        entry: PlaybackQueueEntry
    ): MediaItem =
        playableItem(
            mediaId = AndroidAutoBrowsePolicy.queueItemId(
                entry.entryId
            ),
            track = queueEntryTrack(entry)
        )

    private fun playableItem(
        mediaId: String,
        track: Track
    ): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .apply {
                        track.artworkUrl?.let { artworkUrl ->
                            setArtworkUri(Uri.parse(artworkUrl))
                        }
                    }
                    .build()
            )
            .build()

    private fun browsableItem(
        mediaId: String,
        title: String
    ): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    private fun queueEntryTrack(
        entry: PlaybackQueueEntry
    ): Track =
        Track(
            id = entry.trackId,
            title = entry.title,
            artist = entry.artist,
            album = entry.album,
            streamUrl = entry.streamUrl,
            durationMs = entry.durationMs,
            artworkSeed = entry.artworkSeed,
            artworkUrl = entry.artworkUrl,
            downloadUrl = entry.downloadUrl,
            originalStreamUrl = entry.originalStreamUrl
        )

    private companion object {
        const val CATEGORY_LIMIT = 100
        const val RECENT_LIMIT = 50
        const val SEARCH_LIMIT = 50
    }
}
