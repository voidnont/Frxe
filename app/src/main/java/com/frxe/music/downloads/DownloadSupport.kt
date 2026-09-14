package com.frxe.music.downloads

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.frxe.music.core.STREAMING_CACHE_BYTES
import com.frxe.music.playback.ResolvedStreamRequestHeaders
import com.frxe.music.source.YOUTUBE_WEB_USER_AGENT
import java.io.File

@OptIn(UnstableApi::class)
object DownloadSupport {
    @Volatile
    private var ready = false

    private lateinit var appContext: Context

    lateinit var streamCache: SimpleCache
        private set

    lateinit var upstream: DefaultHttpDataSource.Factory
        private set

    fun initialize(context: Context) {
        if (ready) return

        synchronized(this) {
            if (ready) return

            appContext = context.applicationContext

            val databaseProvider =
                StandaloneDatabaseProvider(appContext)

            streamCache = SimpleCache(
                File(
                    appContext.cacheDir,
                    "frxe_stream_cache"
                ),
                LeastRecentlyUsedCacheEvictor(
                    STREAMING_CACHE_BYTES
                ),
                databaseProvider
            )

            upstream = DefaultHttpDataSource.Factory()
                .setUserAgent(YOUTUBE_WEB_USER_AGENT)
                .setAllowCrossProtocolRedirects(true)

            ready = true
        }
    }

    fun dataSourceFactory(
        context: Context
    ): CacheDataSource.Factory {
        initialize(context)

        val resolvingUpstream = ResolvingDataSource.Factory(
            upstream
        ) { dataSpec ->
            val requestHeaders =
                ResolvedStreamRequestHeaders.forUrl(
                    dataSpec.uri.toString()
                )

            if (requestHeaders.isEmpty()) {
                dataSpec
            } else {
                dataSpec.withRequestHeaders(requestHeaders)
            }
        }

        return CacheDataSource.Factory()
            .setCache(streamCache)
            .setUpstreamDataSourceFactory(resolvingUpstream)
            .setFlags(
                CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
            )
    }
}
