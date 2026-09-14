package com.frxe.music.playback

import android.content.Context
import com.frxe.music.model.Track
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object PlaybackQueueStore {
    private const val PREFS = "frxe_playback_queue"
    private const val KEY_STATE = "queue_v1"

    private val lock = Any()

    @Volatile
    private var initialized = false

    private lateinit var preferences:
        android.content.SharedPreferences

    private val _state = MutableStateFlow(
        PlaybackQueueState()
    )

    val state = _state.asStateFlow()

    fun initialize(context: Context) {
        if (initialized) return

        synchronized(lock) {
            if (initialized) return

            preferences = context.applicationContext
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )

            _state.value = decode(
                preferences.getString(
                    KEY_STATE,
                    null
                )
            )

            initialized = true
        }
    }

    fun replace(
        tracks: List<Track>,
        currentTrackId: String? = null
    ): PlaybackQueueState = synchronized(lock) {
        ensureInitialized()

        val entries = tracks.map(::entryFromTrack)
        val currentEntryId = currentTrackId
            ?.let { id ->
                entries.firstOrNull {
                    it.trackId == id
                }?.entryId
            }
            ?: entries.firstOrNull()?.entryId

        commit(
            PlaybackQueuePolicy.replace(
                entries,
                currentEntryId
            )
        )
    }

    fun playNow(track: Track): PlaybackQueueState =
        replace(
            tracks = listOf(track),
            currentTrackId = track.id
        )

    fun playNext(track: Track): PlaybackQueueState =
        synchronized(lock) {
            ensureInitialized()
            commit(
                PlaybackQueuePolicy.playNext(
                    _state.value,
                    entryFromTrack(track)
                )
            )
        }

    fun append(track: Track): PlaybackQueueState =
        synchronized(lock) {
            ensureInitialized()
            commit(
                PlaybackQueuePolicy.append(
                    _state.value,
                    entryFromTrack(track)
                )
            )
        }

    fun appendAll(
        tracks: List<Track>
    ): PlaybackQueueState = synchronized(lock) {
        ensureInitialized()

        var next = _state.value
        tracks.forEach { track ->
            next = PlaybackQueuePolicy.append(
                next,
                entryFromTrack(track)
            )
        }

        commit(next)
    }

    fun select(entryId: String): PlaybackQueueState =
        synchronized(lock) {
            ensureInitialized()
            commit(
                PlaybackQueuePolicy.select(
                    _state.value,
                    entryId
                )
            )
        }

    fun move(
        fromIndex: Int,
        toIndex: Int
    ): PlaybackQueueState = synchronized(lock) {
        ensureInitialized()
        commit(
            PlaybackQueuePolicy.move(
                _state.value,
                fromIndex,
                toIndex
            )
        )
    }

    fun remove(entryId: String): PlaybackQueueState =
        synchronized(lock) {
            ensureInitialized()
            commit(
                PlaybackQueuePolicy.remove(
                    _state.value,
                    entryId
                )
            )
        }

    fun clear(): PlaybackQueueState = synchronized(lock) {
        ensureInitialized()
        commit(PlaybackQueueState())
    }

    fun advance(
        repeatMode: QueueRepeatMode,
        shuffle: Boolean
    ): PlaybackQueueState? = synchronized(lock) {
        ensureInitialized()

        val current = _state.value
        val shufflePick = if (
            shuffle &&
            current.currentIndex + 1 <= current.entries.lastIndex
        ) {
            Random.nextInt(
                current.currentIndex + 1,
                current.entries.size
            )
        } else {
            null
        }

        PlaybackQueuePolicy.advance(
            current,
            repeatMode,
            shufflePick
        )?.let(::commit)
    }

    fun previous(
        repeatMode: QueueRepeatMode
    ): PlaybackQueueState? = synchronized(lock) {
        ensureInitialized()
        PlaybackQueuePolicy.previous(
            _state.value,
            repeatMode
        )?.let(::commit)
    }

    fun hasManualNext(): Boolean = synchronized(lock) {
        val current = _state.value
        current.currentIndex >= 0 &&
            current.currentIndex < current.entries.lastIndex
    }

    fun tracks(): List<Track> = synchronized(lock) {
        _state.value.entries.map { entry ->
            entry.toTrack()
        }
    }

    fun currentTrack(): Track? = synchronized(lock) {
        _state.value.current?.toTrack()
    }

    fun entryFromTrack(track: Track): PlaybackQueueEntry {
        val source = track.originalStreamUrl
            ?.takeIf(String::isNotBlank)
            ?: track.streamUrl

        return PlaybackQueueEntry(
            entryId = UUID.randomUUID().toString(),
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            streamUrl = source,
            durationMs = track.durationMs,
            artworkSeed = track.artworkSeed,
            artworkUrl = track.artworkUrl,
            downloadUrl = track.downloadUrl,
            originalStreamUrl = track.originalStreamUrl
                ?: source.takeIf {
                    it.startsWith(
                        "frxe-catalog://",
                        ignoreCase = true
                    )
                }
        )
    }

    fun PlaybackQueueEntry.toTrack(): Track = Track(
        id = trackId,
        title = title,
        artist = artist,
        album = album,
        streamUrl = streamUrl,
        durationMs = durationMs,
        artworkSeed = artworkSeed,
        artworkUrl = artworkUrl,
        downloadUrl = downloadUrl,
        originalStreamUrl = originalStreamUrl
    )

    private fun commit(
        state: PlaybackQueueState
    ): PlaybackQueueState {
        _state.value = state
        persist(state)
        return state
    }

    private fun persist(
        state: PlaybackQueueState
    ) {
        if (!::preferences.isInitialized) return

        val root = JSONObject()
            .put(
                "currentIndex",
                state.currentIndex
            )

        val entries = JSONArray()
        state.entries.forEach { item ->
            entries.put(
                JSONObject()
                    .put("entryId", item.entryId)
                    .put("trackId", item.trackId)
                    .put("title", item.title)
                    .put("artist", item.artist)
                    .put("album", item.album)
                    .put("streamUrl", item.streamUrl)
                    .put("durationMs", item.durationMs)
                    .put("artworkSeed", item.artworkSeed)
                    .put(
                        "artworkUrl",
                        item.artworkUrl ?: JSONObject.NULL
                    )
                    .put(
                        "downloadUrl",
                        item.downloadUrl ?: JSONObject.NULL
                    )
                    .put(
                        "originalStreamUrl",
                        item.originalStreamUrl ?: JSONObject.NULL
                    )
            )
        }

        root.put("entries", entries)

        preferences.edit()
            .putString(KEY_STATE, root.toString())
            .apply()
    }

    private fun decode(raw: String?): PlaybackQueueState {
        if (raw.isNullOrBlank()) {
            return PlaybackQueueState()
        }

        return runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("entries")
                ?: return@runCatching PlaybackQueueState()

            val entries = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index)
                        ?: continue

                    val entryId = item.optString("entryId")
                    val trackId = item.optString("trackId")
                    val streamUrl = item.optString("streamUrl")

                    if (
                        entryId.isBlank() ||
                        trackId.isBlank() ||
                        streamUrl.isBlank()
                    ) {
                        continue
                    }

                    add(
                        PlaybackQueueEntry(
                            entryId = entryId,
                            trackId = trackId,
                            title = item.optString("title"),
                            artist = item.optString("artist"),
                            album = item.optString("album"),
                            streamUrl = streamUrl,
                            durationMs = item.optLong("durationMs", 0L),
                            artworkSeed = item.optInt(
                                "artworkSeed",
                                trackId.hashCode()
                            ),
                            artworkUrl = nullableString(
                                item,
                                "artworkUrl"
                            ),
                            downloadUrl = nullableString(
                                item,
                                "downloadUrl"
                            ),
                            originalStreamUrl = nullableString(
                                item,
                                "originalStreamUrl"
                            )
                        )
                    )
                }
            }

            if (entries.isEmpty()) {
                PlaybackQueueState()
            } else {
                PlaybackQueueState(
                    entries = entries,
                    currentIndex = root.optInt(
                        "currentIndex",
                        0
                    ).coerceIn(
                        0,
                        entries.lastIndex
                    )
                )
            }
        }.getOrDefault(
            PlaybackQueueState()
        )
    }

    private fun nullableString(
        json: JSONObject,
        key: String
    ): String? =
        if (json.isNull(key)) {
            null
        } else {
            json.optString(key)
                .takeIf(String::isNotBlank)
        }

    private fun ensureInitialized() {
        check(initialized) {
            "PlaybackQueueStore.initialize(context) must be called before use."
        }
    }
}
