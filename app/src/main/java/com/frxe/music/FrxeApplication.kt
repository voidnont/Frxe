package com.frxe.music

import android.app.Application
import com.frxe.music.downloads.DownloadSupport
import com.frxe.music.playback.PlaybackPrefetcher
import com.frxe.music.playback.PlaybackQueueStore
import com.frxe.music.save.DownloadQueueStore
import com.frxe.music.source.FrxeNewPipeRuntime
import com.frxe.music.source.ResolverDiagnosticsStore
import com.frxe.music.updates.RuntimeHealthStore
import com.frxe.music.updates.YtDlpRuntimeUpdater

class FrxeApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        FrxeNewPipeRuntime.initialize()
        ResolverDiagnosticsStore.initialize(this)
        RuntimeHealthStore.initialize(this)
        YtDlpRuntimeUpdater.initializeAndSchedule(this)

        DownloadSupport.initialize(this)
        DownloadQueueStore.initialize(this)
        PlaybackQueueStore.initialize(this)
        PlaybackPrefetcher.initialize()
    }
}
