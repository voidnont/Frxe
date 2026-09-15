package com.frxe.music.source

object CatalogLoadPolicy {
    val providerOrder: List<String> = listOf(
        "innertube",
        "newpipe",
        "yt-dlp"
    )

    const val searchDebounceMs: Long = 60L
    const val homeRemoteDelayMs: Long = 0L
    const val searchCacheTtlMs: Long = 120_000L

    const val primaryProviderTimeoutMs: Long = 2_500L
    const val fallbackProviderTimeoutMs: Long = 4_000L
    const val lastResortProviderTimeoutMs: Long = 6_000L

    const val primaryHedgeDelayMs: Long = 0L
    const val fallbackHedgeDelayMs: Long = 300L
    const val lastResortHedgeDelayMs: Long = 800L

    fun timeoutFor(providerId: String): Long =
        when (providerId) {
            "innertube" -> primaryProviderTimeoutMs
            "newpipe" -> fallbackProviderTimeoutMs
            else -> lastResortProviderTimeoutMs
        }

    fun hedgeDelayFor(providerId: String): Long =
        when (providerId) {
            "innertube" -> primaryHedgeDelayMs
            "newpipe" -> fallbackHedgeDelayMs
            else -> lastResortHedgeDelayMs
        }
}
