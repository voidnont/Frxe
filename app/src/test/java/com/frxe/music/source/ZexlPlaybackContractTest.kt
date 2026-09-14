package com.frxe.music.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZexlPlaybackContractTest {
    @Test
    fun normalizesBaseUrl() {
        assertEquals(
            "https://zexl.example",
            ZexlPlaybackContract.normalizeBaseUrl(" https://zexl.example/ ")
        )
    }

    @Test
    fun resolvesRelativeAndAbsoluteDownloadUrls() {
        assertEquals(
            "https://zexl.example/api/jobs/abc/file",
            ZexlPlaybackContract.fileUrl(
                baseUrl = "https://zexl.example",
                downloadUrl = "/api/jobs/abc/file",
                jobId = "abc"
            )
        )
        assertEquals(
            "https://cdn.example/audio.mp3",
            ZexlPlaybackContract.fileUrl(
                baseUrl = "https://zexl.example",
                downloadUrl = "https://cdn.example/audio.mp3",
                jobId = "abc"
            )
        )
    }

    @Test
    fun fallsBackToJobFileRouteWhenDownloadUrlIsMissing() {
        assertEquals(
            "https://zexl.example/api/jobs/abc/file",
            ZexlPlaybackContract.fileUrl(
                baseUrl = "https://zexl.example",
                downloadUrl = null,
                jobId = "abc"
            )
        )
    }

    @Test
    fun validatesEndpointScheme() {
        assertTrue(ZexlPlaybackContract.isConfigured("https://zexl.example"))
        assertTrue(ZexlPlaybackContract.isConfigured("http://localhost:3000"))
        assertFalse(ZexlPlaybackContract.isConfigured("ftp://zexl.example"))
        assertFalse(ZexlPlaybackContract.isConfigured(""))
    }

    @Test
    fun buildsAuthorizationHeaderOnlyForNonBlankApiKey() {
        assertEquals(
            mapOf("Authorization" to "Bearer secret"),
            ZexlPlaybackContract.authorizationHeaders(" secret ")
        )
        assertTrue(ZexlPlaybackContract.authorizationHeaders(" ").isEmpty())
        assertTrue(ZexlPlaybackContract.authorizationHeaders(null).isEmpty())
    }
}
