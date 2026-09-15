package com.frxe.music.recommendation

import com.frxe.music.model.HomeSection
import com.frxe.music.model.Track

object PersonalizedPlaylistPolicy {
    fun build(
        favoriteArtist: String?,
        favoriteTracks: List<Track>,
        discoveryTracks: List<Track>,
        trendingTracks: List<Track>
    ): List<HomeSection> {
        val favorite = favoriteTracks.distinctBy(Track::id).take(12)
        val discovery = discoveryTracks.distinctBy(Track::id).take(12)
        val trending = trendingTracks.distinctBy(Track::id).take(12)
        val broadMix = interleaveDistinct(
            favorite,
            discovery,
            trending,
            limit = 12
        )

        return buildList {
            if (favorite.isNotEmpty()) {
                add(
                    HomeSection(
                        title = "FRXE Mix 1",
                        subtitle = favoriteArtist
                            ?.takeIf(String::isNotBlank)
                            ?.let { "Built around $it and nearby sounds" }
                            ?: "Built from the artists you play most",
                        tracks = favorite
                    )
                )
            }

            if (broadMix.isNotEmpty()) {
                add(
                    HomeSection(
                        title = "FRXE Mix 2",
                        subtitle = "A wider blend of your taste and new picks",
                        tracks = broadMix
                    )
                )
            }

            if (discovery.isNotEmpty()) {
                add(
                    HomeSection(
                        title = "Discovery Mix",
                        subtitle = "Artists and tracks just outside your usual rotation",
                        tracks = discovery
                    )
                )
            }

            if (trending.isNotEmpty()) {
                add(
                    HomeSection(
                        title = "Trending Mix",
                        subtitle = "Popular tracks blended into the FRXE catalog",
                        tracks = trending
                    )
                )
            }
        }
    }

    private fun interleaveDistinct(
        first: List<Track>,
        second: List<Track>,
        third: List<Track>,
        limit: Int
    ): List<Track> {
        if (limit <= 0) return emptyList()

        val groups = listOf(first, second, third)
        val positions = IntArray(groups.size)
        val seen = HashSet<String>()
        val output = ArrayList<Track>(limit)

        while (output.size < limit) {
            var added = false

            groups.forEachIndexed { index, group ->
                while (positions[index] < group.size) {
                    val track = group[positions[index]++]
                    if (seen.add(track.id)) {
                        output += track
                        added = true
                        break
                    }
                }

                if (output.size >= limit) {
                    return output
                }
            }

            if (!added) break
        }

        return output
    }
}
