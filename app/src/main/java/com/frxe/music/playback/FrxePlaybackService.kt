package com.frxe.music.playback

import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.frxe.music.data.FrxeDatabase
import com.frxe.music.data.PlaybackHistoryEntity
import com.frxe.music.downloads.DownloadSupport
import com.frxe.music.island.IslandHubOverlayController
import com.frxe.music.island.IslandHubPreferences
import com.frxe.music.model.Track
import com.frxe.music.source.PlaybackResolutionMonitor
import com.frxe.music.source.PlaybackStreamResolver
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class FrxePlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var playbackStore: PlaybackStateStore
    private var islandOverlay: IslandHubOverlayController? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Main.immediate
        )
    private val playbackResolver = PlaybackStreamResolver()

    private var recoveryAttempts = 0
    private var recoveryPending = false
    private var loadedQueueEntryId: String? = null
    private var initialQueueSyncDone = false
    private var restorePositionEntryId: String? = null
    private var restorePositionMs = 0L
    private var pendingAutoPlayEntryId: String? = null
    private var pendingHistoryEntryId: String? = null
    private var lastRecordedHistoryEntryId: String? = null

    private val islandPreferences by lazy {
        getSharedPreferences(
            IslandHubPreferences.PREFS,
            MODE_PRIVATE
        )
    }

    private val islandPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key == IslandHubPreferences.KEY_FLOATING ||
                key == IslandHubPreferences.KEY_REFRESH
            ) {
                islandOverlay?.refresh()
            }
        }

    private val persistenceTicker = object : Runnable {
        override fun run() {
            if (
                ::player.isInitialized &&
                player.mediaItemCount > 0
            ) {
                playbackStore.save(player)
            }

            mainHandler.postDelayed(
                this,
                5_000L
            )
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(
            player: Player,
            events: Player.Events
        ) {
            if (player.mediaItemCount > 0) {
                playbackStore.save(player)
            }

            islandOverlay?.refresh()
        }

        override fun onPlaybackStateChanged(
            playbackState: Int
        ) {
            if (playbackState == Player.STATE_READY) {
                recoveryAttempts = 0
                recoveryPending = false

                PlaybackResolutionMonitor.ready(
                    player.currentMediaItem?.mediaId
                )

                recordReadyQueueHistory()
            }

            if (playbackState == Player.STATE_ENDED) {
                handleQueueEnded()
            }

            islandOverlay?.refresh()
        }

        override fun onPlayerError(
            error: PlaybackException
        ) {
            PlaybackResolutionMonitor.failed(
                trackId = player.currentMediaItem?.mediaId,
                message = playbackErrorMessage(error)
            )

            maybeRecoverPlayback(error)
            islandOverlay?.refresh()
        }
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future =
                SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()

            val snapshot = playbackStore.restore()
            val queuedTrack = PlaybackQueueStore.currentTrack()

            val originalTrack = queuedTrack
                ?: snapshot
                    ?.items
                    ?.getOrNull(snapshot.startIndex)
                    ?.let(playbackStore::trackForRestore)

            if (originalTrack == null) {
                future.setException(
                    IllegalStateException(
                        "No Frxe playback state is available."
                    )
                )
                return future
            }

            serviceScope.launch {
                try {
                    val resolvedTrack =
                        playbackResolver.resolve(originalTrack)
                            ?: throw IllegalStateException(
                                "Frxe could not refresh the saved audio source."
                            )

                    future.set(
                        MediaSession.MediaItemsWithStartPosition(
                            listOf(resolvedTrack.toMediaItem()),
                            0,
                            snapshot
                                ?.positionMs
                                ?.coerceAtLeast(0L)
                                ?: 0L
                        )
                    )
                } catch (error: Throwable) {
                    future.setException(error)
                }
            }

            return future
        }
    }

    override fun onCreate() {
        super.onCreate()

        DownloadSupport.initialize(this)
        PlaybackQueueStore.initialize(this)
        playbackStore = PlaybackStateStore(this)

        val savedSnapshot = playbackStore.restore()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMsForStreaming(
                30_000,
                180_000,
                1_500,
                3_500
            )
            .setBufferDurationsMsForLocalPlayback(
                5_000,
                30_000,
                250,
                750
            )
            .setBackBuffer(30_000, true)
            .setPrioritizeTimeOverSizeThresholdsForStreaming(true)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(
                DownloadSupport.dataSourceFactory(this)
            )
            .setLoadErrorHandlingPolicy(
                DefaultLoadErrorHandlingPolicy(8)
            )

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .also {
                it.addListener(playerListener)
            }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .build()

        savedSnapshot?.let(::applyPlaybackPreferences)
        migrateSnapshotIntoQueueIfNeeded(savedSnapshot)
        prepareRestorePosition(savedSnapshot)

        serviceScope.launch {
            combine(
                PlaybackQueueStore.state,
                PlaybackQueueStore.playRequest
            ) { state, request ->
                state to request
            }.collectLatest { (state, request) ->
                syncQueueState(state, request)
            }
        }

        islandOverlay = IslandHubOverlayController(
            this,
            player
        )

        islandPreferences.registerOnSharedPreferenceChangeListener(
            islandPreferenceListener
        )

        islandOverlay?.refresh()

        mainHandler.postDelayed(
            persistenceTicker,
            5_000L
        )
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()

        islandPreferences.unregisterOnSharedPreferenceChangeListener(
            islandPreferenceListener
        )

        islandOverlay?.dismiss()
        islandOverlay = null

        if (::player.isInitialized) {
            if (player.mediaItemCount > 0) {
                playbackStore.save(player)
            }
            player.removeListener(playerListener)
        }

        mediaSession?.let {
            it.player.release()
            it.release()
        }

        mediaSession = null
        super.onDestroy()
    }

    private fun migrateSnapshotIntoQueueIfNeeded(
        snapshot: PlaybackSnapshot?
    ) {
        if (
            PlaybackQueueStore.state.value.entries.isNotEmpty() ||
            snapshot == null
        ) {
            return
        }

        val tracks = snapshot.items.map(
            playbackStore::trackForRestore
        )

        if (tracks.isEmpty()) return

        val selectedId = snapshot.items
            .getOrNull(snapshot.startIndex)
            ?.mediaId

        PlaybackQueueStore.replace(
            tracks = tracks,
            currentTrackId = selectedId
        )
    }

    private fun prepareRestorePosition(
        snapshot: PlaybackSnapshot?
    ) {
        if (snapshot == null) return

        val savedId = snapshot.items
            .getOrNull(snapshot.startIndex)
            ?.mediaId
            ?: return

        val current = PlaybackQueueStore.state.value.current
            ?: return

        if (current.trackId == savedId) {
            restorePositionEntryId = current.entryId
            restorePositionMs = snapshot.positionMs
                .coerceAtLeast(0L)
        }
    }

    private suspend fun syncQueueState(
        state: PlaybackQueueState,
        playRequest: PlaybackStartRequest?
    ) {
        val currentEntry = state.current

        if (currentEntry == null) {
            loadedQueueEntryId = null
            pendingHistoryEntryId = null
            initialQueueSyncDone = true
            player.stop()
            player.clearMediaItems()
            return
        }

        val explicitPlayRequested =
            PlaybackStartRequestPolicy.shouldConsume(
                request = playRequest,
                currentEntryId = currentEntry.entryId,
                nowMs = System.currentTimeMillis()
            )

        if (
            currentEntry.entryId == loadedQueueEntryId &&
            player.currentMediaItem != null
        ) {
            initialQueueSyncDone = true
            if (explicitPlayRequested) {
                pendingHistoryEntryId = currentEntry.entryId
                player.play()
                PlaybackQueueStore.consumePlayRequest(currentEntry.entryId)
            }
            return
        }

        val shouldAutoPlay =
            explicitPlayRequested ||
                pendingAutoPlayEntryId == currentEntry.entryId ||
                initialQueueSyncDone

        initialQueueSyncDone = true

        val originalTrack = PlaybackQueueStore.currentTrack()
            ?: return

        val expectedEntryId = currentEntry.entryId
        val resolvedTrack = playbackResolver.resolve(originalTrack)
            ?: return

        if (
            PlaybackQueueStore.state.value.current?.entryId !=
            expectedEntryId
        ) {
            return
        }

        val startPosition = if (
            restorePositionEntryId == expectedEntryId
        ) {
            restorePositionMs
        } else {
            0L
        }

        if (shouldAutoPlay) {
            pendingHistoryEntryId = expectedEntryId
        }

        player.setMediaItem(
            resolvedTrack.toMediaItem(),
            startPosition
        )
        player.prepare()

        loadedQueueEntryId = expectedEntryId

        if (restorePositionEntryId == expectedEntryId) {
            restorePositionEntryId = null
            restorePositionMs = 0L
        }

        if (pendingAutoPlayEntryId == expectedEntryId) {
            pendingAutoPlayEntryId = null
        }

        if (shouldAutoPlay) {
            player.play()
            if (explicitPlayRequested) {
                PlaybackQueueStore.consumePlayRequest(expectedEntryId)
            }
        }
    }

    private fun recordReadyQueueHistory() {
        val currentEntry = PlaybackQueueStore.state.value.current
            ?: return

        if (
            pendingHistoryEntryId != currentEntry.entryId ||
            lastRecordedHistoryEntryId == currentEntry.entryId
        ) {
            return
        }

        val track = PlaybackQueueStore.currentTrack()
            ?: return

        pendingHistoryEntryId = null
        lastRecordedHistoryEntryId = currentEntry.entryId

        serviceScope.launch(Dispatchers.IO) {
            FrxeDatabase.get(applicationContext)
                .libraryDao()
                .addHistory(
                    PlaybackHistoryEntity.from(track)
                )
        }
    }

    private fun handleQueueEnded() {
        val repeatMode = queueRepeatMode()
        val previousEntryId =
            PlaybackQueueStore.state.value.current?.entryId

        val nextState = PlaybackQueueStore.advance(
            repeatMode = repeatMode,
            shuffle = player.shuffleModeEnabled
        ) ?: return

        val nextEntryId = nextState.current?.entryId
            ?: return

        if (nextEntryId == previousEntryId) {
            player.seekTo(0L)
            player.play()
            return
        }

        pendingAutoPlayEntryId = nextEntryId

        // Leave STATE_ENDED immediately so remote MediaController listeners do not
        // treat a manual queued transition as end-of-queue Auto-DJ.
        player.clearMediaItems()
    }

    private fun queueRepeatMode(): QueueRepeatMode =
        when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> QueueRepeatMode.All
            Player.REPEAT_MODE_ONE -> QueueRepeatMode.One
            else -> QueueRepeatMode.Off
        }

    private fun applyPlaybackPreferences(
        snapshot: PlaybackSnapshot
    ) {
        player.shuffleModeEnabled = snapshot.shuffleEnabled
        player.repeatMode = snapshot.repeatMode
        player.setPlaybackSpeed(snapshot.playbackSpeed)
    }

    private fun playbackErrorMessage(
        error: PlaybackException
    ): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "The resolved audio URL was rejected or expired. Refreshing it automatically…"

        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Network playback failed. Check the connection and tap Retry."

        else -> error.message
            ?.lineSequence()
            ?.firstOrNull()
            ?.take(180)
            ?.takeIf(String::isNotBlank)
            ?: "Media3 could not play this audio stream. Tap Retry."
    }

    private fun maybeRecoverPlayback(
        error: PlaybackException
    ) {
        if (
            !::player.isInitialized ||
            !player.playWhenReady ||
            recoveryPending
        ) {
            return
        }

        val currentTrack = PlaybackQueueStore.currentTrack()
            ?: player.currentMediaItem
                ?.let(playbackStore::trackFromMediaItem)

        if (
            PlaybackRecoveryPolicy.shouldReresolve(
                badHttpStatus =
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                originalSource = currentTrack?.streamUrl
            ) &&
            currentTrack != null
        ) {
            reresolveCurrentTrack(currentTrack)
            return
        }

        val recoverable = error.errorCode in setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        )

        if (!recoverable || recoveryAttempts >= 3) {
            return
        }

        recoveryAttempts += 1
        recoveryPending = true

        mainHandler.postDelayed(
            {
                recoveryPending = false
                if (!player.playWhenReady) {
                    return@postDelayed
                }
                player.prepare()
                player.play()
            },
            900L * recoveryAttempts
        )
    }

    private fun reresolveCurrentTrack(
        track: Track
    ) {
        if (recoveryAttempts >= 3) return

        recoveryAttempts += 1
        recoveryPending = true

        val expectedMediaId = track.id
        val expectedQueueEntryId =
            PlaybackQueueStore.state.value.current?.entryId
        val resumePosition = player.currentPosition
            .coerceAtLeast(0L)
        val resumePlaying = player.playWhenReady
        val attempt = recoveryAttempts

        serviceScope.launch {
            delay(900L * attempt)

            try {
                val refreshed = playbackResolver.resolve(track)
                    ?: return@launch

                if (
                    PlaybackQueueStore.state.value.current?.entryId !=
                    expectedQueueEntryId
                ) {
                    return@launch
                }

                if (
                    player.currentMediaItem?.mediaId != expectedMediaId
                ) {
                    return@launch
                }

                player.setMediaItem(
                    refreshed.toMediaItem(),
                    resumePosition
                )
                player.prepare()

                if (resumePlaying) {
                    player.play()
                }
            } finally {
                recoveryPending = false
            }
        }
    }
}
