package com.frxe.music.source

private val YOUTUBE_VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
private val YOUTUBE_URL_VIDEO_ID = Regex(
    "(?:youtu\\.be/|youtube(?:-nocookie)?\\.com/(?:watch\\?(?:[^#]*&)?v=|shorts/|embed/|live/))([A-Za-z0-9_-]{11})",
    RegexOption.IGNORE_CASE
)

data class SearchHit(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val provider: String
)

fun extractYouTubeVideoId(value: String): String? {
    val candidate = value.trim()
    if (YOUTUBE_VIDEO_ID.matches(candidate)) return candidate
    return YOUTUBE_URL_VIDEO_ID.find(candidate)?.groupValues?.getOrNull(1)
}

fun mergeSearchHits(providerResults: List<List<SearchHit>>, limit: Int = 30): List<SearchHit> {
    if (limit <= 0) return emptyList()
    val merged = ArrayList<SearchHit>(limit)
    val positions = HashMap<String, Int>()
    val providerRanks = HashMap<String, Int>()
    val maxSize = providerResults.maxOfOrNull(List<SearchHit>::size) ?: 0

    loop@ for (index in 0 until maxSize) {
        providerResults.forEachIndexed { providerRank, results ->
            val hit = results.getOrNull(index) ?: return@forEachIndexed
            if (!YOUTUBE_VIDEO_ID.matches(hit.videoId)) return@forEachIndexed
            val existingPosition = positions[hit.videoId]
            if (existingPosition == null) {
                positions[hit.videoId] = merged.size
                providerRanks[hit.videoId] = providerRank
                merged += hit
                if (merged.size >= limit) return@forEachIndexed
            } else if (providerRank < (providerRanks[hit.videoId] ?: Int.MAX_VALUE)) {
                merged[existingPosition] = hit
                providerRanks[hit.videoId] = providerRank
            }
        }
        if (merged.size >= limit) break@loop
    }
    return merged.take(limit)
}

/**
 * Selects the first provider, in caller-supplied priority order, that produced usable results.
 * Lower-priority providers are fallbacks, not result mixers.
 */
fun selectPrioritySearchHits(providerResults: List<List<SearchHit>>, limit: Int = 30): List<SearchHit> {
    if (limit <= 0) return emptyList()
    return providerResults.asSequence()
        .map { results ->
            results.asSequence()
                .filter { YOUTUBE_VIDEO_ID.matches(it.videoId) }
                .distinctBy(SearchHit::videoId)
                .take(limit)
                .toList()
        }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
}
