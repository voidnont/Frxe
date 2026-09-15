package com.frxe.music.source

object CatalogLoadPolicy {
    val providerOrder: List<String> = listOf(
        "innertube",
        "newpipe",
        "yt-dlp"
    )

    const val searchDebounceMs: Long = 100L
    const val homeRemoteDelayMs: Long = 0L
    const val primaryProviderTimeoutMs: Long = 3_000L
    const val fallbackProviderTimeoutMs: Long = 5_000L
    const val lastResortProviderTimeoutMs: Long = 7_000L

    fun timeoutFor(providerId: String): Long =
        when (providerId) {
            "innertube" -> primaryProviderTimeoutMs
            "newpipe" -> fallbackProviderTimeoutMs
            else -> lastResortProviderTimeoutMs
        }
}
