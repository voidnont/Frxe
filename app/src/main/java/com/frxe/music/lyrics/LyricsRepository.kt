package com.frxe.music.lyrics

import com.frxe.music.BuildConfig
import com.frxe.music.model.TimedLyric
import com.frxe.music.model.Track
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class LyricsUiState(
    val isLoading: Boolean = false,
    val lines: List<TimedLyric> = emptyList(),
    val synced: Boolean = false,
    val source: String? = null,
    val message: String? = null
)

class LyricsRepository(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun lyrics(track: Track): LyricsUiState = withContext(Dispatchers.IO) {
        runCatching {
            val identity = normalizeLyricsIdentity(
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs
            )

            val exact = requestExact(identity)

            if (exact == null) {
                delay(250L)
            }

            val payload = exact
                ?: requestSearch(identity)

            if (payload == null) {
                LyricsUiState(
                    message = "Kein Songtext für diesen Titel gefunden."
                )
            } else {
                payload.toUiState(track.durationMs)
            }
        }.getOrElse { error ->
            LyricsUiState(
                message =
                    "Songtext konnte nicht geladen werden: " +
                        (error.message ?: "Netzwerkfehler")
            )
        }
    }

    private fun requestExact(
        identity: LyricsLookupIdentity
    ): JSONObject? {
        val url = buildString {
            append("https://lrclib.net/api/get?track_name=")
            append(enc(identity.title))
            append("&artist_name=")
            append(enc(identity.artist))

            identity.album?.let { album ->
                append("&album_name=")
                append(enc(album))
            }

            identity.durationSeconds?.let { duration ->
                append("&duration=")
                append(duration)
            }
        }

        val response = execute(url)
            ?: return null

        return runCatching {
            JSONObject(response)
        }.getOrNull()
    }

    private suspend fun requestSearch(
        identity: LyricsLookupIdentity
    ): JSONObject? {
        val queries = lyricsSearchQueries(identity)

        queries.forEachIndexed { index, query ->
            val url = when (query) {
                is LyricsSearchQuery.Structured ->
                    "https://lrclib.net/api/search?" +
                        "track_name=${enc(query.title)}&" +
                        "artist_name=${enc(query.artist)}"

                is LyricsSearchQuery.FreeText ->
                    "https://lrclib.net/api/search?q=${enc(query.query)}"

                is LyricsSearchQuery.TitleOnly ->
                    "https://lrclib.net/api/search?" +
                        "track_name=${enc(query.title)}"
            }

            val response = execute(url)
                ?: return@forEachIndexed

            val array = runCatching {
                JSONArray(response)
            }.getOrNull()
                ?: return@forEachIndexed

            val rows = buildList {
                for (itemIndex in 0 until array.length()) {
                    array.optJSONObject(itemIndex)?.let(::add)
                }
            }

            val candidates = rows.map { row ->
                LyricsCandidate(
                    id = row.optLong("id", -1L),
                    title = row.optString("trackName")
                        .ifBlank { row.optString("name") },
                    artist = row.optString("artistName"),
                    album = row.optString("albumName"),
                    durationSeconds = row.optDouble("duration", 0.0)
                        .toLong()
                        .coerceAtLeast(0L),
                    hasSyncedLyrics = row
                        .optString("syncedLyrics")
                        .isNotBlank(),
                    hasPlainLyrics = row
                        .optString("plainLyrics")
                        .isNotBlank()
                )
            }

            val selected = selectLyricsCandidate(
                identity,
                candidates
            )

            if (selected != null) {
                return rows.firstOrNull { row ->
                    row.optLong("id", -1L) == selected.id
                }
            }

            if (index < queries.lastIndex) {
                delay(250L)
            }
        }

        return null
    }

    private fun execute(url: String): String? {
        val clientIdentity =
            "Frxe ${BuildConfig.VERSION_NAME} " +
                "(https://github.com/voidnont/Frxe)"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", clientIdentity)
            .header("Lrclib-Client", clientIdentity)
            .header("Accept", "application/json")
            .build()

        repeat(3) { attempt ->
            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return null
                }

                if (response.code == 429) {
                    if (attempt == 2) {
                        error("LRCLIB rate limit exceeded")
                    }

                    val retrySeconds = response
                        .header("Retry-After")
                        ?.toLongOrNull()
                        ?.coerceAtLeast(1L)
                        ?: 1L

                    Thread.sleep(retrySeconds * 1_000L)
                    return@use
                }

                if (
                    response.code in 500..599 &&
                    attempt < 2
                ) {
                    Thread.sleep(350L * (attempt + 1L))
                    return@use
                }

                if (!response.isSuccessful) {
                    error("LRCLIB HTTP ${response.code}")
                }

                return response.body
                    ?.string()
                    ?: error("LRCLIB returned an empty response")
            }
        }

        error("LRCLIB request failed after retries")
    }

    private fun JSONObject.toUiState(
        durationMs: Long
    ): LyricsUiState {
        if (optBoolean("instrumental", false)) {
            return LyricsUiState(
                source = "LRCLIB",
                message = "Instrumental · kein Songtext."
            )
        }

        val syncedText = optString("syncedLyrics")
            .takeIf(String::isNotBlank)

        if (syncedText != null) {
            val parsed = parseSyncedLyrics(
                syncedText,
                durationMs
            )

            if (parsed.isNotEmpty()) {
                return LyricsUiState(
                    lines = parsed.map {
                        TimedLyric(
                            it.startMs,
                            it.endMs,
                            it.text
                        )
                    },
                    synced = true,
                    source = "LRCLIB"
                )
            }
        }

        val plainText = optString("plainLyrics")
            .takeIf(String::isNotBlank)

        if (plainText != null) {
            val lines = plainText
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .map {
                    TimedLyric(
                        -1L,
                        -1L,
                        it
                    )
                }
                .toList()

            return LyricsUiState(
                lines = lines,
                synced = false,
                source = "LRCLIB"
            )
        }

        return LyricsUiState(
            message = "Kein Songtext für diesen Titel gefunden."
        )
    }

    private fun enc(value: String): String =
        URLEncoder.encode(
            value,
            Charsets.UTF_8.name()
        )
}
