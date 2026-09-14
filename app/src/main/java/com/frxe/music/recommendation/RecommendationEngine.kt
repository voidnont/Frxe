package com.frxe.music.recommendation

import kotlin.math.max

/** Lightweight, local taste signal used by the pure recommendation engine. */
data class TasteSignal(
    val trackId: String,
    val title: String,
    val artist: String,
    val timestampMs: Long,
    val saved: Boolean = false
)

data class ArtistTasteCluster(
    val artist: String,
    val score: Double,
    val latestSignalMs: Long
)

data class TasteProfile(
    val artistClusters: List<ArtistTasteCluster>,
    val recentTrack: TasteSignal?
)

data class RecommendationCandidate(
    val trackId: String,
    val artist: String,
    val seedArtist: String?,
    val searchRank: Int,
    val seedScore: Double
)

fun buildTasteProfile(
    signals: List<TasteSignal>,
    nowMs: Long = System.currentTimeMillis(),
    maxClusters: Int = 5
): TasteProfile {
    if (signals.isEmpty()) return TasteProfile(emptyList(), null)

    val grouped = linkedMapOf<String, MutableList<TasteSignal>>()
    signals.filter { it.artist.isNotBlank() }.forEach { signal ->
        grouped.getOrPut(signal.artist.trim()) { mutableListOf() }.add(signal)
    }

    val clusters = grouped.map { (artist, artistSignals) ->
        val score = artistSignals.sumOf { signal ->
            if (signal.saved) {
                7.0
            } else {
                implicitRecencyWeight(nowMs - signal.timestampMs)
            }
        }
        ArtistTasteCluster(
            artist = artist,
            score = score,
            latestSignalMs = artistSignals.maxOfOrNull(TasteSignal::timestampMs) ?: 0L
        )
    }.sortedWith(
        compareByDescending<ArtistTasteCluster> { it.score }
            .thenByDescending { it.latestSignalMs }
    ).take(maxClusters.coerceAtLeast(1))

    val recent = signals
        .filterNot(TasteSignal::saved)
        .maxByOrNull(TasteSignal::timestampMs)
        ?: signals.maxByOrNull(TasteSignal::timestampMs)

    return TasteProfile(clusters, recent)
}

private fun implicitRecencyWeight(ageMs: Long): Double {
    val day = 24L * 60L * 60L * 1000L
    val age = max(0L, ageMs)
    return when {
        age <= day -> 2.75
        age <= 7L * day -> 2.25
        age <= 30L * day -> 1.5
        age <= 90L * day -> 0.75
        else -> 0.4
    }
}

fun rankRecommendationCandidates(
    candidates: List<RecommendationCandidate>,
    seenTrackIds: Set<String>,
    limit: Int,
    maxPerArtist: Int = 2
): List<RecommendationCandidate> {
    if (limit <= 0 || candidates.isEmpty()) return emptyList()

    val deduped = candidates.distinctBy(RecommendationCandidate::trackId)
    val unseen = deduped.filterNot { it.trackId in seenTrackIds }
    val pool = if (unseen.isNotEmpty()) unseen else deduped

    val scored = pool.sortedByDescending { candidate ->
        val rankSignal = 4.0 / (candidate.searchRank.coerceAtLeast(0) + 1.0)
        val sameArtistBonus = if (
            candidate.seedArtist != null &&
            candidate.artist.equals(candidate.seedArtist, ignoreCase = true)
        ) 1.75 else 0.0
        candidate.seedScore + rankSignal + sameArtistBonus
    }

    val result = ArrayList<RecommendationCandidate>(limit)
    val perArtist = mutableMapOf<String, Int>()
    for (candidate in scored) {
        val key = candidate.artist.trim().lowercase()
        val count = perArtist[key] ?: 0
        if (count >= maxPerArtist.coerceAtLeast(1)) continue
        result += candidate
        perArtist[key] = count + 1
        if (result.size >= limit) break
    }

    return result
}

fun coldStartQueries(): List<String> = listOf(
    "New music",
    "Popular songs",
    "Trending music"
)
