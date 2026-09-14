package com.frxe.music.recommendation

import com.frxe.music.model.HomeSection
import com.frxe.music.model.Track
import java.time.Year
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class RecommendationRepository(
    private val search: suspend (String) -> List<Track>
) {
    fun localSections(history: List<Track>, library: List<Track>): List<HomeSection> = buildList {
        if (history.isNotEmpty()) {
            add(
                HomeSection(
                    title = "Recently played",
                    subtitle = "Your real listening history",
                    tracks = history.distinctBy(Track::id).take(12)
                )
            )
        }
        val historyIds = history.mapTo(HashSet(), Track::id)
        val libraryOnly = library.filterNot { it.id in historyIds }.distinctBy(Track::id).take(12)
        if (libraryOnly.isNotEmpty()) {
            add(
                HomeSection(
                    title = "From your library",
                    subtitle = "Saved music you can jump back into",
                    tracks = libraryOnly
                )
            )
        }
    }

    suspend fun home(
        history: List<Track>,
        library: List<Track>,
        likedIds: Set<String>
    ): List<HomeSection> = coroutineScope {
        val historySignals = history.map(Track::toRecommendationSignal)
        val librarySignals = library.map(Track::toRecommendationSignal)
        val queries = buildRecommendationQueries(
            history = historySignals,
            library = librarySignals,
            likedIds = likedIds,
            year = Year.now().value
        )

        val queryResults = queries.map { query ->
            async(Dispatchers.IO) {
                query to runCatching { search(query.text) }.getOrDefault(emptyList())
            }
        }.awaitAll()

        val tracksById = LinkedHashMap<String, Track>()
        val candidates = ArrayList<QueryRecommendationCandidate>()
        val sourceQueryById = HashMap<String, RecommendationQuery>()

        queryResults.forEach { (query, tracks) ->
            tracks.forEachIndexed { index, track ->
                tracksById.putIfAbsent(track.id, track)
                sourceQueryById.putIfAbsent(track.id, query)
                candidates += QueryRecommendationCandidate(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    queryKind = query.kind,
                    seedArtist = query.seedArtist,
                    providerPosition = index
                )
            }
        }

        val ranked = rankRecommendationCandidates(
            candidates = candidates,
            history = historySignals,
            library = librarySignals,
            likedIds = likedIds,
            limit = 36
        )
        val rankedTracks = ranked.mapNotNull { tracksById[it.id] }

        val sections = ArrayList<HomeSection>()
        sections += localSections(history, library)

        if (rankedTracks.isNotEmpty()) {
            sections += HomeSection(
                title = if (history.isEmpty() && library.isEmpty()) "Popular for you" else "Made for you",
                subtitle = if (history.isEmpty() && library.isEmpty()) {
                    "Real tracks to start shaping your recommendations"
                } else {
                    "Ranked from your recent listening, likes and library"
                },
                tracks = rankedTracks.take(12)
            )
        }

        val favoriteQuery = queries.firstOrNull { it.kind == RecommendationQueryKind.FavoriteArtist }
        if (favoriteQuery?.seedArtist != null) {
            val favoriteTracks = ranked
                .filter { sourceQueryById[it.id]?.seedArtist == favoriteQuery.seedArtist }
                .mapNotNull { tracksById[it.id] }
                .filterNot { candidate -> history.any { it.title.equals(candidate.title, true) && it.artist.equals(candidate.artist, true) } }
                .distinctBy(Track::id)
                .take(12)
            if (favoriteTracks.isNotEmpty()) {
                sections += HomeSection(
                    title = "Because you listen to ${favoriteQuery.seedArtist}",
                    subtitle = "More from an artist Frxe sees in your taste",
                    tracks = favoriteTracks
                )
            }
        }

        val discoveryTracks = ranked
            .filter { candidate ->
                candidate.queryKind == RecommendationQueryKind.Discovery ||
                    isDiscoveryCandidate(candidate, historySignals, librarySignals)
            }
            .mapNotNull { tracksById[it.id] }
            .filterNot { track -> rankedTracks.take(8).any { it.id == track.id } }
            .distinctBy(Track::id)
            .take(12)

        if (discoveryTracks.isNotEmpty()) {
            sections += HomeSection(
                title = "Discover something new",
                subtitle = "A little farther from what you already play",
                tracks = discoveryTracks
            )
        }

        sections
            .map { section -> section.copy(tracks = section.tracks.distinctBy(Track::id)) }
            .filter { it.tracks.isNotEmpty() }
            .take(5)
    }
}

private fun Track.toRecommendationSignal() = RecommendationSignal(
    id = id,
    title = title,
    artist = artist
)
