package com.frxe.music.source

import android.app.Application
import com.frxe.music.model.HomeSection
import com.frxe.music.model.Track
import com.frxe.music.playback.catalogMetadataUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeCatalogSource(application: Application) : CatalogSource {
    val enabled: Boolean = true

    private val providers: List<SearchProvider> = listOf(
        NewPipeSearchProvider(),
        InnerTubeSearchProvider(),
        YtDlpSearchProvider(application)
    )

    override suspend fun home(): List<HomeSection> = emptyList()

    override suspend fun search(query: String): List<Track> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()

        val providerResults = ArrayList<List<SearchHit>>(providers.size)
        for (provider in providers) {
            val hits = withContext(Dispatchers.IO) {
                runCatching { provider.search(normalized) }.getOrDefault(emptyList())
            }
            providerResults += hits
            if (hits.isNotEmpty()) break
        }

        return selectPrioritySearchHits(providerResults, limit = MAX_RESULTS).map { hit ->
            Track(
                id = "yt-${hit.videoId}",
                title = hit.title,
                artist = hit.artist,
                album = "Catalog · ${hit.provider}",
                streamUrl = catalogMetadataUri(hit.videoId),
                durationMs = hit.durationMs,
                artworkSeed = hit.videoId.hashCode(),
                artworkUrl = hit.artworkUrl ?: "https://i.ytimg.com/vi/${hit.videoId}/hqdefault.jpg"
            )
        }
    }

    override suspend fun track(id: String): Track? = null

    private companion object {
        const val MAX_RESULTS = 30
    }
}
