package com.frxe.music.recommendation

import java.util.Locale

enum class RecommendationQueryKind { FavoriteArtist, Discovery, Trending }

data class RecommendationSignal(
    val id: String,
    val title: String,
    val artist: String
)

data class RecommendationQuery(
    val text: String,
    val kind: RecommendationQueryKind,
    val seedArtist: String? = null
)

data class QueryRecommendationCandidate(  // <-- RENAMED FROM RecommendationCandidate
    val id: String,
    val title: String,
    val artist: String,
    val queryKind: RecommendationQueryKind,
    val seedArtist: String?,
    val providerPosition: Int
)

fun buildRecommendationQueries(
    history: List<RecommendationSignal>,
    library: List<RecommendationSignal>,
    likedIds: Set<String>,
    year: Int
): List<RecommendationQuery> {
    val artistScores = linkedMapOf<String, Double>()
    val canonicalArtists = linkedMapOf<String, String>()

    fun addArtist(rawArtist: String, score: Double) {
        val artist = rawArtist.trim()
        if (artist.isBlank() || artist.equals("YouTube", ignoreCase = true)) return
        val key = artist.normalizedKey()
        canonicalArtists.putIfAbsent(key, artist)
        artistScores[key] = (artistScores[key] ?: 0.0) + score
    }

    history.take(30).forEachIndexed { index, track ->
        val recency = 3.5 / (1.0 + index * 0.18)
        addArtist(track.artist, recency + if (track.id in likedIds) 4.5 else 0.0)
    }
    library.take(50).forEach { track ->
        addArtist(track.artist, 2.2 + if (track.id in likedIds) 4.5 else 0.0)
    }

    val favoriteArtists = artistScores.entries
        .sortedByDescending { it.value }
        .mapNotNull { canonicalArtists[it.key] }
        .distinctBy { it.normalizedKey() }
        .take(2)

    val queries = ArrayList<RecommendationQuery>(5)
    favoriteArtists.forEach { artist ->
        queries += RecommendationQuery(
            text = "$artist songs official audio",
            kind = RecommendationQueryKind.FavoriteArtist,
            seedArtist = artist
        )
    }
    favoriteArtists.firstOrNull()?.let { artist ->
        queries += RecommendationQuery(
            text = "music like $artist",
            kind = RecommendationQueryKind.Discovery,
            seedArtist = artist
        )
    }

    if (favoriteArtists.isEmpty()) {
        queries += RecommendationQuery("top songs $year official audio", RecommendationQueryKind.Trending)
        queries += RecommendationQuery("new music $year official audio", RecommendationQueryKind.Discovery)
        queries += RecommendationQuery("popular music $year", RecommendationQueryKind.Trending)
    } else {
        queries += RecommendationQuery("top songs $year official audio", RecommendationQueryKind.Trending)
        queries += RecommendationQuery("new music $year official audio", RecommendationQueryKind.Discovery)
    }

    return queries.distinctBy { it.text.normalizedKey() }.take(5)
}

fun rankRecommendationCandidates(
    candidates: List<QueryRecommendationCandidate>,  // <-- UPDATED TYPE
    history: List<RecommendationSignal>,
    library: List<RecommendationSignal>,
    likedIds: Set<String>,
    limit: Int
): List<QueryRecommendationCandidate> {  // <-- UPDATED RETURN TYPE
    if (limit <= 0) return emptyList()

    val seenIds = (history.asSequence().map { it.id } + library.asSequence().map { it.id }).toSet()
    val seenTrackKeys = (history.asSequence() + library.asSequence())
        .map { "${it.title.normalizedKey()}|${it.artist.normalizedKey()}" }
        .toSet()
    val artistAffinity = HashMap<String, Double>()

    fun addAffinity(track: RecommendationSignal, amount: Double) {
        val key = track.artist.normalizedKey()
        if (key.isBlank()) return
        artistAffinity[key] = (artistAffinity[key] ?: 0.0) + amount + if (track.id in likedIds) 2.4 else 0.0
    }

    history.take(30).forEachIndexed { index, track ->
        addAffinity(track, 1.2 / (1.0 + index * 0.16))
    }
    library.take(50).forEach { addAffinity(it, 0.9) }

    fun score(candidate: QueryRecommendationCandidate): Double {  // <-- UPDATED PARAMETER TYPE
        var score = when (candidate.queryKind) {
            RecommendationQueryKind.FavoriteArtist -> 5.0
            RecommendationQueryKind.Discovery -> 3.5
            RecommendationQueryKind.Trending -> 2.0
        }
        score += (2.5 - candidate.providerPosition * 0.15).coerceAtLeast(0.0)
        score += artistAffinity[candidate.artist.normalizedKey()] ?: 0.0
        if (candidate.seedArtist != null && candidate.artist.normalizedKey() == candidate.seedArtist.normalizedKey()) score += 2.0
        val trackKey = "${candidate.title.normalizedKey()}|${candidate.artist.normalizedKey()}"
        if (candidate.id in seenIds || trackKey in seenTrackKeys) score -= 6.0 else score += 0.8
        if (looksNonMusic(candidate.title)) score -= 4.5
        return score
    }

    val deduped = candidates
        .filter { it.id.isNotBlank() && it.title.isNotBlank() }
        .distinctBy { it.id }
        .sortedByDescending(::score)

    val artistCounts = HashMap<String, Int>()
    val selected = ArrayList<QueryRecommendationCandidate>(limit)  // <-- UPDATED TYPE
    for (candidate in deduped) {
        val artistKey = candidate.artist.normalizedKey()
        val count = artistCounts[artistKey] ?: 0
        if (artistKey.isNotBlank() && count >= 2) continue
        selected += candidate
        if (artistKey.isNotBlank()) artistCounts[artistKey] = count + 1
        if (selected.size >= limit) break
    }
    return selected
}

fun isDiscoveryCandidate(
    candidate: QueryRecommendationCandidate,  // <-- UPDATED PARAMETER TYPE
    history: List<RecommendationSignal>,
    library: List<RecommendationSignal>
): Boolean {
    val knownArtists = (history.asSequence().map { it.artist.normalizedKey() } +
        library.asSequence().map { it.artist.normalizedKey() }).filter { it.isNotBlank() }.toSet()
    return candidate.id !in history.asSequence().map { it.id }.toSet() &&
        candidate.id !in library.asSequence().map { it.id }.toSet() &&
        candidate.artist.normalizedKey() !in knownArtists
}

private fun looksNonMusic(title: String): Boolean {
    val normalized = title.normalizedKey()
    return listOf(
        "interview", "reaction", "review", "trailer", "teaser", "behind the scenes",
        "podcast", "documentary", "tutorial", "news", "shorts"
    ).any(normalized::contains)
}

private fun String.normalizedKey(): String = trim().lowercase(Locale.ROOT)
    .replace(Regex("\\s+"), " ")
