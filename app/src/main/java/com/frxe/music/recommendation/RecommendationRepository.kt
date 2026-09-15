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
    fun localSections(
        history: List<Track>,
        library: List<Track>
    ): List<HomeSection> = buildList {
        if (history.isNotEmpty()) {
            add(
                HomeSection(
                    title = "Recently played",
                    subtitle = "Your real listening history",
                    tracks = history
                        .distinctBy(Track::id)
                        .take(12)
                )
            )
        }

        val historyIds = history
            .mapTo(HashSet(), Track::id)
        val libraryOnly = library
            .filterNot { it.id in historyIds }
            .distinctBy(Track::id)
            .take(12)

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
        val historySignals = history
            .map(Track::toRecommendationSignal)
        val librarySignals = library
            .map(Track::toRecommendationSignal)

        val queries = buildRecommendationQueries(
            history = historySignals,
            library = librarySignals,
            likedIds = likedIds,
            year = Year.now().value
        )

        val queryResults = queries.map { query ->
            async(Dispatchers.IO) {
                query to runCatching {
                    search(query.text)
                }.getOrDefault(emptyList())
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
            limit = 48
        )
        val rankedTracks = ranked
            .mapNotNull { tracksById[it.id] }

        val favoriteQuery = queries
            .firstOrNull {
                it.kind == RecommendationQueryKind.FavoriteArtist
            }

        val favoriteTracks = ranked
            .filter { candidate ->
                candidate.queryKind == RecommendationQueryKind.FavoriteArtist &&
                    (
                        favoriteQuery?.seedArtist == null ||
                            sourceQueryById[candidate.id]
                                ?.seedArtist == favoriteQuery.seedArtist
                        )
            }
            .mapNotNull { tracksById[it.id] }
            .filterNot { candidate ->
                history.any {
                    it.title.equals(candidate.title, true) &&
                        it.artist.equals(candidate.artist, true)
                }
            }
            .distinctBy(Track::id)
            .take(12)
            .ifEmpty {
                rankedTracks.take(12)
            }

        val discoveryTracks = ranked
            .filter { candidate ->
                candidate.queryKind == RecommendationQueryKind.Discovery ||
                    isDiscoveryCandidate(
                        candidate,
                        historySignals,
                        librarySignals
                    )
            }
            .mapNotNull { tracksById[it.id] }
            .filterNot { track ->
                favoriteTracks.any { it.id == track.id }
            }
            .distinctBy(Track::id)
            .take(12)

        val trendingTracks = ranked
            .filter {
                it.queryKind == RecommendationQueryKind.Trending
            }
            .mapNotNull { tracksById[it.id] }
            .distinctBy(Track::id)
            .take(12)

        val generatedPlaylists =
            PersonalizedPlaylistPolicy.build(
                favoriteArtist = favoriteQuery?.seedArtist,
                favoriteTracks = favoriteTracks,
                discoveryTracks = discoveryTracks,
                trendingTracks = trendingTracks
            )

        val sections = ArrayList<HomeSection>()
        sections += localSections(history, library)
        sections += generatedPlaylists

        if (
            generatedPlaylists.isEmpty() &&
            rankedTracks.isNotEmpty()
        ) {
            sections += HomeSection(
                title = "FRXE Mix",
                subtitle = "A fresh blend from the catalog",
                tracks = rankedTracks.take(12)
            )
        }

        sections
            .map { section ->
                section.copy(
                    tracks = section.tracks
                        .distinctBy(Track::id)
                )
            }
            .filter { it.tracks.isNotEmpty() }
            .take(6)
    }
}

private fun Track.toRecommendationSignal() =
    RecommendationSignal(
        id = id,
        title = title,
        artist = artist
    )
