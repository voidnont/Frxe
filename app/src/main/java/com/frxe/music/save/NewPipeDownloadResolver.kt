package com.frxe.music.save

import com.frxe.music.source.FrxeNewPipeRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe

internal class NewPipeDownloadResolver {

    suspend fun resolve(
        sourceUrl: String
    ): String? = withContext(Dispatchers.IO) {

        val normalizedUrl =
            sourceUrl.trim()

        if (
            normalizedUrl.isEmpty()
        ) {
            return@withContext null
        }

        /*
         * NewPipe should only receive
         * URLs it can actually resolve.
         *
         * A frxe-catalog:// URI should
         * already have been converted
         * into a normal YouTube watch
         * URL by DownloadRouting.kt.
         */
        if (
            normalizedUrl.startsWith(
                "frxe-catalog://",
                ignoreCase = true
            )
        ) {
            return@withContext null
        }

        runCatching {

            /*
             * Initialize Frxe's custom
             * NewPipe downloader once.
             */
            FrxeNewPipeRuntime
                .initialize()

            /*
             * Detect which NewPipe service
             * owns this URL.
             */
            val service =
                NewPipe.getServiceByUrl(
                    normalizedUrl
                )

            /*
             * Create the stream extractor.
             */
            val extractor =
                service.getStreamExtractor(
                    normalizedUrl
                )

            /*
             * Fetch metadata and available
             * stream URLs.
             */
            extractor.fetchPage()

            /*
             * Get all audio streams.
             *
             * Prefer the highest average
             * bitrate available.
             */
            val audioStreams =
                extractor.audioStreams
                    .asSequence()
                    .sortedWith(
                        compareByDescending {
                                stream ->

                            runCatching {
                                stream.averageBitrate
                            }
                                .getOrDefault(
                                    0
                                )
                        }
                    )

            /*
             * Extract the first usable
             * direct URL.
             */
            val resolvedUrl =
                audioStreams
                    .mapNotNull {
                            stream ->

                        runCatching {
                            stream.content
                        }
                            .getOrNull()
                            ?.trim()
                            ?.takeIf {
                                it.isNotEmpty()
                            }
                    }
                    .firstOrNull {
                            candidate ->

                        isValidResolvedMediaUrl(
                            candidate
                        )
                    }

            resolvedUrl

        }.getOrNull()
    }

    /*
     * NewPipe's final stream URL should
     * normally be HTTP or HTTPS.
     *
     * This prevents another custom,
     * malformed, or empty URI from being
     * passed to the raw downloader.
     */
    private fun isValidResolvedMediaUrl(
        value: String
    ): Boolean {

        val normalized =
            value.trim()

        if (
            normalized.isEmpty()
        ) {
            return false
        }

        return normalized.startsWith(
            "https://",
            ignoreCase = true
        ) ||
        normalized.startsWith(
            "http://",
            ignoreCase = true
        )
    }
}