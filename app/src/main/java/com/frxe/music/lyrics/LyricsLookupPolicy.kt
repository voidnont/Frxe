package com.frxe.music.lyrics

data class LyricsLookupIdentity(
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Long?
)

sealed interface LyricsSearchQuery {
    data class Structured(
        val title: String,
        val artist: String
    ) : LyricsSearchQuery

    data class FreeText(
        val query: String
    ) : LyricsSearchQuery

    data class TitleOnly(
        val title: String
    ) : LyricsSearchQuery
}

data class LyricsCandidate(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Long,
    val hasSyncedLyrics: Boolean,
    val hasPlainLyrics: Boolean
)

private val youtubeSuffixRegex = Regex(
    "\\s*[\\[(](?:official\\s+)?(?:music\\s+)?(?:audio|video|lyric\\s+video|lyrics?|visualizer)(?:\\s+video)?[\\])]\\s*$",
    RegexOption.IGNORE_CASE
)

private val topicArtistRegex = Regex(
    "\\s*-\\s*topic\\s*$",
    RegexOption.IGNORE_CASE
)

fun normalizeLyricsIdentity(
    title: String,
    artist: String,
    album: String,
    durationMs: Long
): LyricsLookupIdentity {
    val cleanedArtist = artist
        .trim()
        .replace(topicArtistRegex, "")
        .trim()
        .ifBlank { artist.trim() }

    var cleanedTitle = title.trim()
    while (youtubeSuffixRegex.containsMatchIn(cleanedTitle)) {
        cleanedTitle = cleanedTitle
            .replace(youtubeSuffixRegex, "")
            .trim()
    }

    val artistPrefix = "$cleanedArtist - "
    if (cleanedTitle.startsWith(artistPrefix, ignoreCase = true)) {
        cleanedTitle = cleanedTitle
            .substring(artistPrefix.length)
            .trim()
    }

    if (cleanedTitle.isBlank()) {
        cleanedTitle = title.trim()
    }

    val cleanedAlbum = album
        .trim()
        .takeUnless {
            it.startsWith("Catalog ·", ignoreCase = true) ||
                it.equals("Catalog", ignoreCase = true)
        }
        ?.takeIf(String::isNotBlank)

    val durationSeconds = (durationMs / 1_000L)
        .takeIf { it in 1L..3_600L }

    return LyricsLookupIdentity(
        title = cleanedTitle,
        artist = cleanedArtist,
        album = cleanedAlbum,
        durationSeconds = durationSeconds
    )
}

fun lyricsSearchQueries(
    identity: LyricsLookupIdentity
): List<LyricsSearchQuery> = buildList {
    add(
        LyricsSearchQuery.Structured(
            identity.title,
            identity.artist
        )
    )

    if (identity.artist.isNotBlank()) {
        add(
            LyricsSearchQuery.FreeText(
                "${identity.title} ${identity.artist}".trim()
            )
        )
    }

    add(
        LyricsSearchQuery.TitleOnly(
            identity.title
        )
    )
}.distinct()

fun selectLyricsCandidate(
    target: LyricsLookupIdentity,
    candidates: List<LyricsCandidate>
): LyricsCandidate? = candidates
    .asSequence()
    .filter {
        it.hasSyncedLyrics || it.hasPlainLyrics
    }
    .map { candidate ->
        val normalized = normalizeLyricsIdentity(
            title = candidate.title,
            artist = candidate.artist,
            album = candidate.album,
            durationMs = candidate.durationSeconds
                .coerceAtLeast(0L) * 1_000L
        )

        val targetTitle = comparable(target.title)
        val candidateTitle = comparable(normalized.title)
        val targetArtist = comparable(target.artist)
        val candidateArtist = comparable(normalized.artist)

        val titleExact = candidateTitle == targetTitle
        val artistExact = candidateArtist == targetArtist
        val titleRelated = titleExact ||
            candidateTitle.contains(targetTitle) ||
            targetTitle.contains(candidateTitle)

        var score = 0

        if (titleExact) {
            score += 100
        } else if (titleRelated) {
            score += 45
        }

        if (artistExact) {
            score += 70
        } else if (
            candidateArtist.contains(targetArtist) ||
            targetArtist.contains(candidateArtist)
        ) {
            score += 25
        }

        if (
            target.album != null &&
            normalized.album != null &&
            comparable(normalized.album) == comparable(target.album)
        ) {
            score += 20
        }

        val targetDuration = target.durationSeconds
        if (targetDuration != null && candidate.durationSeconds > 0L) {
            val difference = kotlin.math.abs(
                candidate.durationSeconds - targetDuration
            )

            score += when {
                difference <= 2L -> 30
                difference <= 5L -> 15
                difference <= 10L -> 5
                else -> -difference
                    .coerceAtMost(30L)
                    .toInt()
            }
        }

        if (candidate.hasSyncedLyrics) score += 10
        if (candidate.hasPlainLyrics) score += 5

        Triple(
            candidate,
            score,
            titleExact || artistExact
        )
    }
    .filter { (_, score, identityMatch) ->
        score >= 60 && identityMatch
    }
    .maxByOrNull { (_, score, _) -> score }
    ?.first

private fun comparable(value: String): String = value
    .lowercase()
    .replace(
        Regex("[^\\p{L}\\p{N}]+"),
        " "
    )
    .trim()
