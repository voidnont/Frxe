package com.frxe.music.source

import android.app.Application
import com.frxe.music.model.HomeSection
import com.frxe.music.model.Track
import com.frxe.music.playback.catalogMetadataUri

class YouTubeCatalogSource(application: Application) : CatalogSource {
    val enabled: Boolean = true

    private val providersById: Map<String, SearchProvider> = listOf(
        InnerTubeSearchProvider(),
        NewPipeSearchProvider(),
        YtDlpSearchProvider(application)
    ).associateBy(SearchProvider::id)

    private val providers: List<SearchProvider>
        get() = CatalogLoadPolicy.providerOrder
            .mapNotNull(providersById::get)

    private val searchCache =
        CatalogSearchCache<List<Track>>(
            ttlMs = CatalogLoadPolicy.searchCacheTtlMs
        )

    override suspend fun home(): List<HomeSection> = emptyList()

    override suspend fun search(query: String): List<Track> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()

        searchCache.get(normalized)
            ?.let { return it }

        val hits = hedgedFirstNonEmpty(
            providers.map { provider ->
                CatalogHedgeTask(
                    startDelayMs =
                        CatalogLoadPolicy.hedgeDelayFor(provider.id),
                    timeoutMs =
                        CatalogLoadPolicy.timeoutFor(provider.id)
                ) {
                    provider.search(normalized)
                }
            }
        )

        val tracks = selectPrioritySearchHits(
            providerResults = listOf(hits),
            limit = MAX_RESULTS
        ).map { hit ->
            Track(
                id = "yt-${hit.videoId}",
                title = hit.title,
                artist = hit.artist,
                album = "Catalog · ${hit.provider}",
                streamUrl = catalogMetadataUri(hit.videoId),
                durationMs = hit.durationMs,
                artworkSeed = hit.videoId.hashCode(),
                artworkUrl = hit.artworkUrl
                    ?: "https://i.ytimg.com/vi/${hit.videoId}/hqdefault.jpg"
            )
        }

        if (tracks.isNotEmpty()) {
            searchCache.put(normalized, tracks)
        }

        return tracks
    }

    override suspend fun track(id: String): Track? = null

    private companion object {
        const val MAX_RESULTS = 30
    }
}
