package com.frxe.music.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.frxe.music.data.PlaylistRepository
import com.frxe.music.model.FrxeRepeatMode
import com.frxe.music.model.Track
import com.frxe.music.playback.PlaybackQueueState
import com.frxe.music.playback.PlaybackQueueStore
import com.frxe.music.playback.QueueRepeatMode
import com.frxe.music.save.FrxeDownloadService
import com.frxe.music.save.SaveFormat
import com.frxe.music.save.SaveRequest
import com.frxe.music.save.automaticDownloadSource
import com.frxe.music.save.defaultQualityFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val FrxeViewModel.playbackQueueState:
    StateFlow<PlaybackQueueState>
    get() = PlaybackQueueStore.state

fun FrxeViewModel.playQueued(
    track: Track,
    queue: List<Track> = emptyList()
) {
    val actualQueue = queue.ifEmpty {
        listOf(track)
    }

    PlaybackQueueStore.replaceAndRequestPlay(
        tracks = actualQueue,
        currentTrackId = track.id
    )
}

fun FrxeViewModel.playNext(
    track: Track
) {
    PlaybackQueueStore.playNext(track)
}

fun FrxeViewModel.addToQueue(
    track: Track
) {
    PlaybackQueueStore.append(track)
}

fun FrxeViewModel.selectQueueEntry(
    entryId: String
) {
    PlaybackQueueStore.selectAndRequestPlay(entryId)
}

fun FrxeViewModel.removeQueueEntry(
    entryId: String
) {
    PlaybackQueueStore.remove(entryId)
}

fun FrxeViewModel.moveQueueEntry(
    fromIndex: Int,
    toIndex: Int
) {
    PlaybackQueueStore.move(
        fromIndex,
        toIndex
    )
}

fun FrxeViewModel.clearPlaybackQueue() {
    PlaybackQueueStore.clear()
}

fun FrxeViewModel.queueNext() {
    val player = playerState.value

    PlaybackQueueStore.advance(
        repeatMode = player.repeatMode.toQueueRepeatMode(),
        shuffle = player.shuffleEnabled
    )
}

fun FrxeViewModel.queuePrevious() {
    PlaybackQueueStore.previous(
        playerState.value.repeatMode
            .toQueueRepeatMode()
    )
}

fun FrxeViewModel.playlistRepository():
    PlaylistRepository =
    PlaylistRepository(
        getApplication<Application>()
    )

fun FrxeViewModel.createPlaylist(
    name: String,
    onCreated: (Long) -> Unit = {}
) {
    viewModelScope.launch {
        val id = withContext(Dispatchers.IO) {
            playlistRepository().create(name)
        }
        onCreated(id)
    }
}

fun FrxeViewModel.renamePlaylist(
    playlistId: Long,
    name: String
) {
    viewModelScope.launch(Dispatchers.IO) {
        playlistRepository().rename(
            playlistId,
            name
        )
    }
}

fun FrxeViewModel.deletePlaylist(
    playlistId: Long
) {
    viewModelScope.launch(Dispatchers.IO) {
        playlistRepository().delete(playlistId)
    }
}

fun FrxeViewModel.addTrackToPlaylist(
    playlistId: Long,
    track: Track
) {
    viewModelScope.launch(Dispatchers.IO) {
        playlistRepository().addTrack(
            playlistId,
            track
        )
    }
}

fun FrxeViewModel.removeTrackFromPlaylist(
    playlistId: Long,
    rowId: Long
) {
    viewModelScope.launch(Dispatchers.IO) {
        playlistRepository().removeTrack(
            playlistId,
            rowId
        )
    }
}

fun FrxeViewModel.playPlaylist(
    playlistId: Long
) {
    viewModelScope.launch {
        val tracks = withContext(Dispatchers.IO) {
            playlistRepository().trackList(playlistId)
        }

        tracks.firstOrNull()?.let { first ->
            playQueued(
                first,
                tracks
            )
        }
    }
}

fun FrxeViewModel.addPlaylistToQueue(
    playlistId: Long
) {
    viewModelScope.launch {
        val tracks = withContext(Dispatchers.IO) {
            playlistRepository().trackList(playlistId)
        }

        PlaybackQueueStore.appendAll(tracks)
    }
}

fun FrxeViewModel.downloadPlaylist(
    playlistId: Long
) {
    val app = getApplication<Application>()

    viewModelScope.launch(Dispatchers.IO) {
        val tracks = playlistRepository()
            .trackList(playlistId)

        val format = SaveFormat.MP3
        val quality = defaultQualityFor(format)

        tracks.forEach { track ->
            val source = automaticDownloadSource(
                track.downloadUrl,
                track.streamUrl
            ) ?: return@forEach

            FrxeDownloadService.enqueue(
                app,
                SaveRequest(
                    sourceUrl = source,
                    title = track.title,
                    artist = track.artist,
                    format = format,
                    quality = quality
                )
            )
        }
    }
}

private fun FrxeRepeatMode.toQueueRepeatMode():
    QueueRepeatMode =
    when (this) {
        FrxeRepeatMode.All -> QueueRepeatMode.All
        FrxeRepeatMode.One -> QueueRepeatMode.One
        FrxeRepeatMode.Off -> QueueRepeatMode.Off
    }
