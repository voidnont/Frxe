package com.frxe.music.save

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedTrackFactoryTest {

    @Test
    fun `track download keeps original identity and metadata`() {
        val request = SaveRequest(
            sourceUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
            format = SaveFormat.MP3,
            quality = SaveQuality.Mp3K320,
            trackId = "yt-dQw4w9WgXcQ",
            album = "Whenever You Need Somebody",
            durationMs = 213_000L,
            artworkSeed = 42,
            artworkUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        )
        val result = SaveResult(
            uri = "content://media/external/audio/media/42",
            title = request.title,
            artist = request.artist,
            format = request.format
        )

        val track = savedTrackFrom(
            request = request,
            result = result,
            fallbackId = "saved-fallback"
        )

        assertEquals("yt-dQw4w9WgXcQ", track.id)
        assertEquals(result.uri, track.streamUrl)
        assertEquals(request.sourceUrl, track.downloadUrl)
        assertEquals(request.album, track.album)
        assertEquals(request.durationMs, track.durationMs)
        assertEquals(request.artworkSeed, track.artworkSeed)
        assertEquals(request.artworkUrl, track.artworkUrl)
    }

    @Test
    fun `manual save still gets generated identity and save album`() {
        val request = SaveRequest(
            sourceUrl = "https://example.com/audio.mp3",
            title = "Manual Save",
            artist = "Artist",
            format = SaveFormat.MP3,
            quality = SaveQuality.Mp3K192
        )
        val result = SaveResult(
            uri = "content://media/external/audio/media/99",
            title = request.title,
            artist = request.artist,
            format = request.format
        )

        val track = savedTrackFrom(
            request = request,
            result = result,
            fallbackId = "saved-fallback"
        )

        assertEquals("saved-fallback", track.id)
        assertEquals("Frxe Save · MP3", track.album)
        assertEquals(result.uri, track.streamUrl)
    }
}
