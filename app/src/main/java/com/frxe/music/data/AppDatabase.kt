package com.frxe.music.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.frxe.music.model.Track
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "library_tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val streamUrl: String,
    val durationMs: Long,
    val artworkSeed: Int,
    val artworkUrl: String? = null,
    val downloadUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun asTrack() = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        streamUrl = streamUrl,
        durationMs = durationMs,
        artworkSeed = artworkSeed,
        artworkUrl = artworkUrl,
        downloadUrl = downloadUrl
    )

    companion object {
        fun from(track: Track) = TrackEntity(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            streamUrl = track.originalStreamUrl
                ?.takeIf(String::isNotBlank)
                ?: track.streamUrl,
            durationMs = track.durationMs,
            artworkSeed = track.artworkSeed,
            artworkUrl = track.artworkUrl,
            downloadUrl = track.downloadUrl
        )
    }
}

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0L,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val streamUrl: String,
    val durationMs: Long,
    val artworkSeed: Int,
    val artworkUrl: String? = null,
    val downloadUrl: String? = null,
    val playedAt: Long = System.currentTimeMillis()
) {
    fun asTrack() = Track(
        id = trackId,
        title = title,
        artist = artist,
        album = album,
        streamUrl = streamUrl,
        durationMs = durationMs,
        artworkSeed = artworkSeed,
        artworkUrl = artworkUrl,
        downloadUrl = downloadUrl
    )

    companion object {
        fun from(track: Track) = PlaybackHistoryEntity(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            streamUrl = track.originalStreamUrl
                ?.takeIf(String::isNotBlank)
                ?: track.streamUrl,
            durationMs = track.durationMs,
            artworkSeed = track.artworkSeed,
            artworkUrl = track.artworkUrl,
            downloadUrl = track.downloadUrl
        )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    indices = [Index("playlistId")]
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0L,
    val playlistId: Long,
    val position: Int,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val streamUrl: String,
    val durationMs: Long,
    val artworkSeed: Int,
    val artworkUrl: String? = null,
    val downloadUrl: String? = null
) {
    fun asTrack() = Track(
        id = trackId,
        title = title,
        artist = artist,
        album = album,
        streamUrl = streamUrl,
        durationMs = durationMs,
        artworkSeed = artworkSeed,
        artworkUrl = artworkUrl,
        downloadUrl = downloadUrl,
        originalStreamUrl = streamUrl.takeIf {
            it.startsWith("frxe-catalog://", ignoreCase = true)
        }
    )

    companion object {
        fun from(
            playlistId: Long,
            position: Int,
            track: Track
        ) = PlaylistTrackEntity(
            playlistId = playlistId,
            position = position,
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            streamUrl = track.originalStreamUrl
                ?.takeIf(String::isNotBlank)
                ?: track.streamUrl,
            durationMs = track.durationMs,
            artworkSeed = track.artworkSeed,
            artworkUrl = track.artworkUrl,
            downloadUrl = track.downloadUrl
        )
    }
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_tracks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM library_tracks WHERE id = :id)")
    suspend fun contains(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(track: TrackEntity)

    @Query("DELETE FROM library_tracks WHERE id = :id")
    suspend fun remove(id: String)

    @Insert
    suspend fun addHistory(event: PlaybackHistoryEntity)

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT 50")
    fun observeHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC, id DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun playlist(playlistId: Long): PlaylistEntity?

    @Insert
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun renamePlaylist(
        playlistId: Long,
        name: String,
        updatedAt: Long
    )

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deletePlaylistTracks(playlistId: Long)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC, rowId ASC")
    fun observePlaylistTracks(
        playlistId: Long
    ): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC, rowId ASC")
    suspend fun playlistTracks(
        playlistId: Long
    ): List<PlaylistTrackEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPlaylistPosition(
        playlistId: Long
    ): Int

    @Insert
    suspend fun addPlaylistTrack(
        track: PlaylistTrackEntity
    ): Long

    @Query("DELETE FROM playlist_tracks WHERE rowId = :rowId")
    suspend fun removePlaylistTrack(rowId: Long)

    @Query("UPDATE playlist_tracks SET position = :position WHERE rowId = :rowId")
    suspend fun updatePlaylistTrackPosition(
        rowId: Long,
        position: Int
    )

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun touchPlaylist(
        playlistId: Long,
        updatedAt: Long
    )
}

@Database(
    entities = [
        TrackEntity::class,
        PlaybackHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class FrxeDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile
        private var instance: FrxeDatabase? = null

        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playback_history (
                        eventId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        trackId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        streamUrl TEXT NOT NULL,
                        durationMs INTEGER NOT NULL,
                        artworkSeed INTEGER NOT NULL,
                        playedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN artworkUrl TEXT")
                db.execSQL("ALTER TABLE library_tracks ADD COLUMN downloadUrl TEXT")
                db.execSQL("ALTER TABLE playback_history ADD COLUMN artworkUrl TEXT")
                db.execSQL("ALTER TABLE playback_history ADD COLUMN downloadUrl TEXT")
            }
        }

        private val migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlist_tracks (
                        rowId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        playlistId INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        trackId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        streamUrl TEXT NOT NULL,
                        durationMs INTEGER NOT NULL,
                        artworkSeed INTEGER NOT NULL,
                        artworkUrl TEXT,
                        downloadUrl TEXT
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlistId ON playlist_tracks (playlistId)"
                )
            }
        }

        fun get(context: Context): FrxeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FrxeDatabase::class.java,
                    "frxe.db"
                )
                    .addMigrations(
                        migration1To2,
                        migration2To3,
                        migration3To4
                    )
                    .build()
                    .also {
                        instance = it
                    }
            }
    }
}
