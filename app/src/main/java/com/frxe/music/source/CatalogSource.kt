package com.frxe.music.source

import com.frxe.music.model.HomeSection
import com.frxe.music.model.Track

interface CatalogSource {
    suspend fun home(): List<HomeSection>
    suspend fun search(query: String): List<Track>
    suspend fun track(id: String): Track?
}
