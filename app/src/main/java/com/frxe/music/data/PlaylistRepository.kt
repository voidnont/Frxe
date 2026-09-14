package com.frxe.music.data

import android.content.Context
import com.frxe.music.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class FrxePlaylist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class FrxePlaylistTrack(
    val rowId: Long,
    val position: Int,
    val track: Track
)

class PlaylistRepository(
    context: Context
) {
    private val dao =
        FrxeDatabase.get(context).libraryDao()

    val playlists: Flow<List<FrxePlaylist>> =
        dao.observePlaylists()
            .map { rows ->
                rows.map { row ->
                    FrxePlaylist(
                        id = row.id,
                        name = row.name,
                        createdAt = row.createdAt,
                        updatedAt = row.updatedAt
                    )
                }
            }

    fun tracks(
        playlistId: Long
    ): Flow<List<FrxePlaylistTrack>> =
        dao.observePlaylistTracks(playlistId)
            .map { rows ->
                rows.map { row ->
                    FrxePlaylistTrack(
                        rowId = row.rowId,
                        position = row.position,
                        track = row.asTrack()
                    )
                }
            }

    suspend fun create(
        name: String
    ): Long {
        val clean = name.trim()
        require(clean.isNotEmpty()) {
            "Playlist name cannot be empty."
        }

        val now = System.currentTimeMillis()
        return dao.createPlaylist(
            PlaylistEntity(
                name = clean,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun rename(
        playlistId: Long,
        name: String
    ) {
        val clean = name.trim()
        require(clean.isNotEmpty()) {
            "Playlist name cannot be empty."
        }

        dao.renamePlaylist(
            playlistId = playlistId,
            name = clean,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun delete(
        playlistId: Long
    ) {
        dao.deletePlaylistTracks(playlistId)
        dao.deletePlaylist(playlistId)
    }

    suspend fun addTrack(
        playlistId: Long,
        track: Track
    ): Long {
        val nextPosition =
            dao.maxPlaylistPosition(playlistId) + 1

        val rowId = dao.addPlaylistTrack(
            PlaylistTrackEntity.from(
                playlistId = playlistId,
                position = nextPosition,
                track = track
            )
        )

        dao.touchPlaylist(
            playlistId,
            System.currentTimeMillis()
        )

        return rowId
    }

    suspend fun removeTrack(
        playlistId: Long,
        rowId: Long
    ) {
        dao.removePlaylistTrack(rowId)
        normalizePositions(playlistId)
        dao.touchPlaylist(
            playlistId,
            System.currentTimeMillis()
        )
    }

    suspend fun moveTrack(
        playlistId: Long,
        fromIndex: Int,
        toIndex: Int
    ) {
        val rows = dao.playlistTracks(playlistId)
            .toMutableList()

        if (
            fromIndex !in rows.indices ||
            toIndex !in rows.indices ||
            fromIndex == toIndex
        ) {
            return
        }

        val moved = rows.removeAt(fromIndex)
        rows.add(toIndex, moved)

        rows.forEachIndexed { index, row ->
            dao.updatePlaylistTrackPosition(
                row.rowId,
                index
            )
        }

        dao.touchPlaylist(
            playlistId,
            System.currentTimeMillis()
        )
    }

    suspend fun trackList(
        playlistId: Long
    ): List<Track> =
        dao.playlistTracks(playlistId)
            .map(PlaylistTrackEntity::asTrack)

    private suspend fun normalizePositions(
        playlistId: Long
    ) {
        dao.playlistTracks(playlistId)
            .forEachIndexed { index, row ->
                if (row.position != index) {
                    dao.updatePlaylistTrackPosition(
                        row.rowId,
                        index
                    )
                }
            }
    }
}
