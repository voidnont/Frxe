package com.frxe.music.save

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRoutingTest {

    @Test
    fun `catalog uri becomes canonical youtube watch url`() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            automaticDownloadSource(
                downloadUrl = null,
                streamUrl = "frxe-catalog://youtube/dQw4w9WgXcQ"
            )
        )
    }

    @Test
    fun `youtube sources use audio resolver backends instead of direct downloader`() {
        val backends = DownloadRoutePolicy.backendsFor(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        )

        assertFalse(backends.contains(DownloadBackend.Direct))
        assertTrue(backends.contains(DownloadBackend.InnerTube))
        assertTrue(backends.contains(DownloadBackend.NewPipe))
        assertTrue(backends.contains(DownloadBackend.YtDlp))
    }
}
