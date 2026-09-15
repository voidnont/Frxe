package com.frxe.music.ui

import android.app.Application
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.frxe.music.core.selectAutoDjId
import com.frxe.music.core.sleepDeadline
import com.frxe.music.core.sleepRemaining
import com.frxe.music.data.FrxeDatabase
import com.frxe.music.data.PlaybackHistoryEntity
import com.frxe.music.data.TrackEntity
import com.frxe.music.island.IslandHubPreferences
import com.frxe.music.island.IslandHubUiState
import com.frxe.music.lyrics.LyricsRepository
import com.frxe.music.lyrics.LyricsUiState
import com.frxe.music.model.CanvasMode
import com.frxe.music.model.EXTRA_ARTWORK_SEED
import com.frxe.music.model.EXTRA_DOWNLOAD_URL
import com.frxe.music.model.EXTRA_DURATION_MS
import com.frxe.music.model.FrxeRepeatMode
import com.frxe.music.model.HomeSection
import com.frxe.music.model.PlayerUiState
import com.frxe.music.model.Track
import com.frxe.music.playback.AudioOnlyPlaybackPolicy
import com.frxe.music.playback.FrxePlaybackService
import com.frxe.music.playback.PlaybackLaunchPolicy
import com.frxe.music.playback.PlaybackQueueStore
import com.frxe.music.recommendation.RecommendationRepository
import com.frxe.music.save.DownloadCoordinator
import com.frxe.music.save.DownloadRoutePolicy
import com.frxe.music.save.SaveFormat
import com.frxe.music.save.SaveQuality
import com.frxe.music.save.SaveRequest
import com.frxe.music.save.SaveStage
import com.frxe.music.save.SaveUiState
import com.frxe.music.save.automaticDownloadSource
import com.frxe.music.social.ListenTogetherManager
import com.frxe.music.social.ListenTogetherPlayback
import com.frxe.music.social.ListenTogetherTrack
import com.frxe.music.social.PlaybackClockSynchronizer
import com.frxe.music.social.SharedPlaybackState
import com.frxe.music.social.SyncAction
import com.frxe.music.social.decideSyncAction
import com.frxe.music.source.CatalogLoadPolicy
import com.frxe.music.source.YouTubeCatalogSource
import com.frxe.music.updates.DependencyReleaseRepository
import com.frxe.music.updates.FrxeUpdateRepository
import com.frxe.music.updates.UpdateCheckResult
import com.frxe.music.updates.UpdaterUiState
import com.frxe.music.voice.VoiceCommand
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FrxeViewModel(application: Application) : AndroidViewModel(application) {

    private val source = YouTubeCatalogSource(application)

    private val recommendationRepository =
        RecommendationRepository(source::search)

    private val lyricsRepository =
        LyricsRepository()

    private val dao =
        FrxeDatabase.get(application).libraryDao()

    private val downloadCoordinator =
        DownloadCoordinator(application)

    private val appUpdateRepository =
        FrxeUpdateRepository()

    private val dependencyReleaseRepository =
        DependencyReleaseRepository()

    private val playerPrefs =
        application.getSharedPreferences(
            "frxe_player_prefs",
            0
        )

    private val _home =
        MutableStateFlow<List<HomeSection>>(
            emptyList()
        )

    val home =
        _home.asStateFlow()

    private val _searchResults =
        MutableStateFlow<List<Track>>(
            emptyList()
        )

    val searchResults =
        _searchResults.asStateFlow()

    private val _playerState =
        MutableStateFlow(
            PlayerUiState()
        )

    val playerState:
        StateFlow<PlayerUiState> =
        _playerState.asStateFlow()

    private val _lyrics =
        MutableStateFlow(
            LyricsUiState()
        )

    val lyrics:
        StateFlow<LyricsUiState> =
        _lyrics.asStateFlow()

    private val _saveState =
        MutableStateFlow(
            SaveUiState()
        )

    val saveState:
        StateFlow<SaveUiState> =
        _saveState.asStateFlow()

    private val _downloadTarget =
        MutableStateFlow<Track?>(
            null
        )

    val downloadTarget:
        StateFlow<Track?> =
        _downloadTarget.asStateFlow()

    private val _updaterState =
        MutableStateFlow(
            UpdaterUiState()
        )

    val updaterState:
        StateFlow<UpdaterUiState> =
        _updaterState.asStateFlow()

    private val _islandHubState =
        MutableStateFlow(
            IslandHubPreferences.state(
                application
            )
        )

    val islandHubState:
        StateFlow<IslandHubUiState> =
        _islandHubState.asStateFlow()

    private val _likedIds =
        MutableStateFlow(
            playerPrefs.getStringSet(
                "liked_track_ids",
                emptySet()
            )
                ?.toSet()
                .orEmpty()
        )

    val likedIds:
        StateFlow<Set<String>> =
        _likedIds.asStateFlow()

    val library:
        StateFlow<List<Track>> =
        dao.observeAll()
            .map { rows ->
                rows.map(
                    TrackEntity::asTrack
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(
                    5_000
                ),
                emptyList()
            )

    val history:
        StateFlow<List<Track>> =
        dao.observeHistory()
            .map { rows ->
                rows.map(
                    PlaybackHistoryEntity::asTrack
                )
                    .distinctBy(
                        Track::id
                    )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(
                    5_000
                ),
                emptyList()
            )

    private val tracksById =
        mutableMapOf<String, Track>()

    private var controller:
        MediaController? = null

    private val playbackClockSynchronizer =
        PlaybackClockSynchronizer()

    private val listenTogetherManager =
        ListenTogetherManager(
            application,
            localPlayback =
                ::localListenTogetherPlayback,
            onRemotePlayback = { remote ->

                viewModelScope.launch(
                    Dispatchers.Main.immediate
                ) {
                    applyRemotePlayback(
                        remote
                    )
                }
            }
        )

    val listenTogetherState =
        listenTogetherManager.state

    private var sleepTimerJob:
        Job? = null

    private var saveJob:
        Job? = null

    private var searchJob:
        Job? = null

    private var lyricsJob:
        Job? = null

    private var loadedLyricsTrackId:
        String? = null

    private var sleepDeadlineMs:
        Long = 0L

    private var canvasMode:
        CanvasMode =
        CanvasMode.Liquid

    private var autoDjEnabled:
        Boolean =
        playerPrefs.getBoolean(
            "auto_dj_enabled",
            true
        )

    private val controllerFuture =
        MediaController.Builder(
            application,
            SessionToken(
                application,
                ComponentName(
                    application,
                    FrxePlaybackService::class.java
                )
            )
        )
            .buildAsync()

    private val playerListener =
        object : Player.Listener {

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                refreshNowPlaying()
            }

            override fun onIsPlayingChanged(
                isPlaying: Boolean
            ) {
                refreshNowPlaying()
            }

            override fun onPlaybackStateChanged(
                playbackState: Int
            ) {

                if (
                    playbackState ==
                    Player.STATE_ENDED &&
                    autoDjEnabled &&
                    !PlaybackQueueStore.hasManualNext()
                ) {
                    playAutoDjNext()
                } else {
                    refreshNowPlaying()
                }
            }

            override fun onPlaybackParametersChanged(
                playbackParameters:
                    PlaybackParameters
            ) {
                refreshNowPlaying()
            }

            override fun onShuffleModeEnabledChanged(
                shuffleModeEnabled:
                    Boolean
            ) {
                refreshNowPlaying()
            }

            override fun onRepeatModeChanged(
                repeatMode: Int
            ) {
                refreshNowPlaying()
            }
        }

    init {

        controllerFuture.addListener(
            {

                controller =
                    runCatching {
                        controllerFuture.get()
                    }
                        .getOrNull()
                        ?.also {
                            it.addListener(
                                playerListener
                            )
                        }

                refreshNowPlaying()
            },
            java.util.concurrent.Executor {
                    command ->

                Handler(
                    Looper.getMainLooper()
                ).post(command)
            }
        )

        viewModelScope.launch {

            combine(
                history,
                library,
                likedIds
            ) { recent, saved, likes ->

                Triple(
                    recent,
                    saved,
                    likes
                )
            }
                .collectLatest {
                        (
                            recent,
                            saved,
                            likes
                        ) ->

                    val localSections =
                        recommendationRepository
                            .localSections(
                                recent,
                                saved
                            )

                    if (
                        localSections
                            .isNotEmpty()
                    ) {

                        _home.value =
                            localSections

                        localSections
                            .flatMap {
                                it.tracks
                            }
                            .forEach {
                                tracksById[
                                    it.id
                                ] = it
                            }
                    }

                    delay(CatalogLoadPolicy.homeRemoteDelayMs)

                    val recommendations =
                        recommendationRepository
                            .home(
                                recent,
                                saved,
                                likes
                            )

                    if (
                        recommendations
                            .isNotEmpty()
                    ) {

                        _home.value =
                            recommendations

                        recommendations
                            .flatMap {
                                it.tracks
                            }
                            .forEach {
                                tracksById[
                                    it.id
                                ] = it
                            }

                    } else if (
                        localSections
                            .isEmpty()
                    ) {

                        _home.value =
                            emptyList()
                    }
                }
        }

        viewModelScope.launch {

            while (true) {

                refreshNowPlaying()

                delay(400)
            }
        }
    }

    fun search(
        query: String
    ) {

        searchJob?.cancel()

        searchJob =
            viewModelScope.launch {

                if (
                    query.isNotBlank()
                ) {
                    delay(CatalogLoadPolicy.searchDebounceMs)
                }

                _searchResults.value =
                    source.search(
                        query
                    )
                        .also { tracks ->

                            tracks.forEach {
                                tracksById[
                                    it.id
                                ] = it
                            }
                        }
            }
    }

    /*
     * Playback flow:
     *
     * Frxe catalog URI
     *      ↓
     * PlaybackStreamResolver
     *      ↓
     * temporary real audio URL
     *      ↓
     * Media3 / ExoPlayer
     */
    fun play(
        track: Track,
        queue: List<Track> = emptyList()
    ) {
        val plan = PlaybackLaunchPolicy.plan(
            selected = track,
            requestedQueue = queue
        )

        plan.tracks.forEach { queuedTrack ->
            tracksById[queuedTrack.id] = queuedTrack
        }

        PlaybackQueueStore.replaceAndRequestPlay(
            tracks = plan.tracks,
            currentTrackId = plan.currentTrackId
        )

        loadLyrics(track)
        refreshNowPlaying()
    }

    fun togglePlayPause() {

        controller?.let {

            if (
                it.isPlaying
            ) {
                it.pause()
            } else {
                it.play()
            }
        }

        refreshNowPlaying()
    }

    fun next() {
        queueNext()
    }

    fun previous() {
        queuePrevious()
    }

    fun seek(
        positionMs: Long
    ) =
        controller
            ?.seekTo(
                positionMs
                    .coerceAtLeast(
                        0L
                    )
            )

    fun setVolume(
        volume: Float
    ) {

        controller?.setVolume(
            volume.coerceIn(
                0f,
                1f
            )
        )

        refreshNowPlaying()
    }

    fun setPlaybackSpeed(
        speed: Float
    ) {

        controller
            ?.setPlaybackSpeed(
                speed.coerceIn(
                    0.5f,
                    2f
                )
            )

        refreshNowPlaying()
    }

    fun setPlaybackPitch(
        pitch: Float
    ) {

        val player =
            controller
                ?: return

        player.setPlaybackParameters(
            PlaybackParameters(
                player
                    .playbackParameters
                    .speed,
                pitch.coerceIn(
                    0.5f,
                    2f
                )
            )
        )

        refreshNowPlaying()
    }

    fun toggleAutoDj() {

        autoDjEnabled =
            !autoDjEnabled

        playerPrefs
            .edit()
            .putBoolean(
                "auto_dj_enabled",
                autoDjEnabled
            )
            .apply()

        refreshNowPlaying()
    }

    fun retryStream() {

        val player =
            controller
                ?: return

        val resumePosition =
            player
                .currentPosition
                .coerceAtLeast(
                    0L
                )

        val resumePlaying =
            player.isPlaying ||
            player.playWhenReady

        player.prepare()

        player.seekTo(
            resumePosition
        )

        if (
            resumePlaying
        ) {
            player.play()
        }

        refreshNowPlaying()
    }

    fun toggleShuffle() {

        controller?.let {

            it.shuffleModeEnabled =
                !it.shuffleModeEnabled
        }

        refreshNowPlaying()
    }

    fun cycleRepeatMode() {

        val player =
            controller
                ?: return

        player.repeatMode =
            when (
                player.repeatMode
            ) {

                Player.REPEAT_MODE_OFF ->
                    Player.REPEAT_MODE_ALL

                Player.REPEAT_MODE_ALL ->
                    Player.REPEAT_MODE_ONE

                else ->
                    Player.REPEAT_MODE_OFF
            }

        refreshNowPlaying()
    }

    fun setSleepTimer(
        minutes: Int?
    ) {

        sleepTimerJob?.cancel()

        sleepTimerJob =
            null

        if (
            minutes == null ||
            minutes <= 0
        ) {

            sleepDeadlineMs =
                0L

            refreshNowPlaying()

            return
        }

        sleepDeadlineMs =
            sleepDeadline(
                System.currentTimeMillis(),
                minutes
            )

        sleepTimerJob =
            viewModelScope.launch {

                delay(
                    minutes *
                    60_000L
                )

                controller
                    ?.pause()

                sleepDeadlineMs =
                    0L

                refreshNowPlaying()
            }

        refreshNowPlaying()
    }

    fun cycleCanvasMode() {

        canvasMode =
            when (
                canvasMode
            ) {

                CanvasMode.Liquid ->
                    CanvasMode.Pulse

                CanvasMode.Pulse ->
                    CanvasMode.Minimal

                CanvasMode.Minimal ->
                    CanvasMode.Liquid
            }

        refreshNowPlaying()
    }

    fun toggleLibrary(
        track: Track
    ) {

        viewModelScope.launch(
            Dispatchers.IO
        ) {

            if (
                dao.contains(
                    track.id
                )
            ) {

                dao.remove(
                    track.id
                )

            } else {

                dao.save(
                    TrackEntity.from(
                        track
                    )
                )
            }
        }
    }

    fun toggleLike(
        track: Track
    ) {

        val next =
            _likedIds.value
                .toMutableSet()
                .apply {

                    if (
                        !add(
                            track.id
                        )
                    ) {

                        remove(
                            track.id
                        )
                    }
                }
                .toSet()

        _likedIds.value =
            next

        playerPrefs
            .edit()
            .putStringSet(
                "liked_track_ids",
                next
            )
            .apply()
    }

    fun artistTracks(
        artist: String
    ): List<Track> =

        (
            _home.value
                .flatMap {
                    it.tracks
                } +
            _searchResults.value +
            library.value +
            tracksById.values
        )
            .filter {

                it.artist.equals(
                    artist,
                    ignoreCase = true
                )
            }
            .distinctBy {
                it.id
            }

    fun playFromCurrentQueue(
        track: Track
    ) {

        play(
            track,
            _playerState
                .value
                .queue
                .ifEmpty {
                    listOf(
                        track
                    )
                }
        )
    }

    fun hostListenTogether():
        String =
        listenTogetherManager
            .host()

    fun joinListenTogether(
        roomCode: String
    ): Boolean =
        listenTogetherManager
            .join(
                roomCode
            )

    fun leaveListenTogether() =
        listenTogetherManager
            .leave()

    private fun localListenTogetherPlayback():
        ListenTogetherPlayback? {

        val player =
            controller
                ?: return null

        val track =
            _playerState
                .value
                .track
                ?: return null

        if (
            !AudioOnlyPlaybackPolicy
                .isPlayable(
                    track.streamUrl
                )
        ) {
            return null
        }

        return ListenTogetherPlayback(
            track =
                ListenTogetherTrack(
                    id =
                        track.id,

                    title =
                        track.title,

                    artist =
                        track.artist,

                    album =
                        track.album,

                    streamUrl =
                        track.streamUrl,

                    durationMs =
                        track.durationMs,

                    artworkSeed =
                        track.artworkSeed,

                    artworkUrl =
                        track.artworkUrl,

                    downloadUrl =
                        track.downloadUrl
                ),

            positionMs =
                player
                    .currentPosition
                    .coerceAtLeast(
                        0L
                    ),

            playing =
                player.isPlaying,

            sentAtMs =
                System
                    .currentTimeMillis()
        )
    }

    private fun applyRemotePlayback(
        remote:
            ListenTogetherPlayback
    ) {

        val player =
            controller
                ?: return

        val shared =
            remote.track

        if (
            !AudioOnlyPlaybackPolicy
                .isPlayable(
                    shared.streamUrl
                )
        ) {
            return
        }

        val track =
            Track(
                id =
                    shared.id,

                title =
                    shared.title,

                artist =
                    shared.artist,

                album =
                    shared.album,

                streamUrl =
                    shared.streamUrl,

                durationMs =
                    shared.durationMs,

                artworkSeed =
                    shared.artworkSeed,

                artworkUrl =
                    shared.artworkUrl,

                downloadUrl =
                    shared.downloadUrl
            )

        tracksById[
            track.id
        ] = track

        val targetPosition =
            playbackClockSynchronizer
                .targetPosition(
                    SharedPlaybackState(
                        roomId =
                            listenTogetherState
                                .value
                                .roomCode,

                        leaderId =
                            "host",

                        trackId =
                            track.id,

                        positionMs =
                            remote.positionMs,

                        playing =
                            remote.playing,

                        serverTimestampMs =
                            remote.sentAtMs
                    ),

                    System
                        .currentTimeMillis()
                )

        if (
            player
                .currentMediaItem
                ?.mediaId !=
            track.id
        ) {

            player.setMediaItem(
                track.toMediaItem(),
                targetPosition
            )

            player.prepare()

            if (
                remote.playing
            ) {
                player.play()
            } else {
                player.pause()
            }

            loadLyrics(
                track
            )

            refreshNowPlaying()

            return
        }

        when (
            val action =
                decideSyncAction(
                    currentPositionMs =
                        player
                            .currentPosition
                            .coerceAtLeast(
                                0L
                            ),

                    targetPositionMs =
                        targetPosition,

                    localPlaying =
                        player.isPlaying,

                    remotePlaying =
                        remote.playing
                )
        ) {

            is SyncAction.Seek ->

                player.seekTo(
                    action.positionMs
                )

            SyncAction.Play -> {

                if (
                    kotlin.math.abs(
                        player.currentPosition -
                        targetPosition
                    ) > 650L
                ) {

                    player.seekTo(
                        targetPosition
                    )
                }

                player.play()
            }

            SyncAction.Pause -> {

                player.pause()

                if (
                    kotlin.math.abs(
                        player.currentPosition -
                        targetPosition
                    ) > 650L
                ) {

                    player.seekTo(
                        targetPosition
                    )
                }
            }

            SyncAction.None ->
                Unit
        }

        refreshNowPlaying()
    }

    private fun loadLyrics(
        track: Track
    ) {

        if (
            loadedLyricsTrackId ==
                track.id &&
            (
                _lyrics.value
                    .isLoading ||
                _lyrics.value
                    .lines
                    .isNotEmpty()
            )
        ) {
            return
        }

        loadedLyricsTrackId =
            track.id

        lyricsJob?.cancel()

        _lyrics.value =
            LyricsUiState(
                isLoading =
                    true
            )

        lyricsJob =
            viewModelScope.launch {

                val result =
                    lyricsRepository
                        .lyrics(
                            track
                        )

                if (
                    loadedLyricsTrackId ==
                    track.id
                ) {

                    _lyrics.value =
                        result
                }
            }
    }

    private fun playAutoDjNext() {

        val current =
            _playerState
                .value
                .track

        val queueIds =
            _playerState
                .value
                .queue
                .map {
                    it.id
                }

        val candidates =
            (
                _home.value
                    .flatMap {
                        it.tracks
                    } +
                _searchResults.value +
                library.value +
                tracksById.values
            )
                .distinctBy {
                    it.id
                }

        val candidateId =
            selectAutoDjId(
                current?.id,
                queueIds,
                candidates.map {
                    it.id
                }
            )
                ?: run {

                    refreshNowPlaying()

                    return
                }

        candidates
            .firstOrNull {
                it.id ==
                candidateId
            }
            ?.let {

                play(
                    it,
                    listOf(
                        it
                    )
                )
            }
    }

    fun clearHistory() {

        viewModelScope.launch(
            Dispatchers.IO
        ) {

            dao.clearHistory()
        }
    }

    fun startSave(
        request: SaveRequest
    ) {

        if (
            _saveState
                .value
                .isBusy
        ) {
            return
        }

        if (
            DownloadRoutePolicy
                .backendsFor(
                    request.sourceUrl
                )
                .isEmpty()
        ) {

            _saveState.value =
                SaveUiState(
                    stage =
                        SaveStage.Failed,

                    progress =
                        0f,

                    message =
                        "No compatible downloadable source is available for this track."
                )

            return
        }

        saveJob?.cancel()

        saveJob =
            viewModelScope.launch {

                try {

                    val result =
                        downloadCoordinator
                            .save(
                                request
                            ) { state ->

                                _saveState.value =
                                    state
                            }

                    val savedTrack =
                        Track(
                            id =
                                "saved-" +
                                "${System.currentTimeMillis()}-" +
                                result.uri.hashCode(),

                            title =
                                result.title,

                            artist =
                                result.artist,

                            album =
                                "Frxe Save · " +
                                result.format.displayName,

                            streamUrl =
                                result.uri,

                            durationMs =
                                0L,

                            artworkSeed =
                                (
                                    result.title +
                                    result.artist
                                ).hashCode()
                        )

                    tracksById[
                        savedTrack.id
                    ] = savedTrack

                    withContext(
                        Dispatchers.IO
                    ) {

                        dao.save(
                            TrackEntity.from(
                                savedTrack
                            )
                        )
                    }

                } catch (
                    _: CancellationException
                ) {

                    _saveState.value =
                        SaveUiState(
                            SaveStage.Cancelled,
                            0f,
                            "Save cancelled"
                        )

                } catch (
                    error: Throwable
                ) {

                    _saveState.value =
                        SaveUiState(
                            SaveStage.Failed,
                            0f,
                            error.message
                                ?.lineSequence()
                                ?.firstOrNull()
                                ?.take(180)
                                ?: "Save failed"
                        )
                }
            }
    }

    fun cancelSave() {

        downloadCoordinator.cancel()

        saveJob?.cancel()

        saveJob =
            null

        _saveState.value =
            SaveUiState(
                SaveStage.Cancelled,
                0f,
                "Save cancelled"
            )
    }

    fun openDownload(
        track: Track
    ) {

        if (
            _saveState
                .value
                .isBusy
        ) {
            return
        }

        _downloadTarget.value =
            track

        _saveState.value =
            SaveUiState()
    }

    fun closeDownload() {

        if (
            _saveState
                .value
                .isBusy
        ) {
            return
        }

        _downloadTarget.value =
            null
    }

    fun startTrackDownload(
        track: Track,
        format: SaveFormat,
        quality: SaveQuality
    ) {

        val sourceUrl =
            automaticDownloadSource(
                track.downloadUrl,
                track.streamUrl
            )

        if (
            sourceUrl == null
        ) {

            _saveState.value =
                SaveUiState(
                    stage =
                        SaveStage.Failed,

                    progress =
                        0f,

                    message =
                        "No compatible downloadable source is available for this track."
                )

            return
        }

        startSave(
            SaveRequest(
                sourceUrl =
                    sourceUrl,

                title =
                    track.title,

                artist =
                    track.artist,

                format =
                    format,

                quality =
                    quality
            )
        )
    }

    fun download(
        track: Track
    ) =
        openDownload(
            track
        )

    fun refreshIslandHubSettings() {

        _islandHubState.value =
            IslandHubPreferences.state(
                getApplication()
            )

        IslandHubPreferences
            .refreshFloatingOverlay(
                getApplication()
            )
    }

    fun setInAppIslandEnabled(
        enabled: Boolean
    ) {

        IslandHubPreferences
            .setInAppEnabled(
                getApplication(),
                enabled
            )

        refreshIslandHubSettings()
    }

    fun setFloatingIslandEnabled(
        enabled: Boolean
    ) {

        IslandHubPreferences
            .setFloatingEnabled(
                getApplication(),
                enabled
            )

        refreshIslandHubSettings()
    }

    fun checkForAppUpdate() {

        if (
            _updaterState
                .value
                .checkingApp
        ) {
            return
        }

        viewModelScope.launch {

            _updaterState.value =
                _updaterState.value
                    .copy(
                        checkingApp =
                            true,

                        appStatus =
                            "Checking GitHub releases…"
                    )

            val result =
                appUpdateRepository
                    .check()

            _updaterState.value =
                when (
                    result
                ) {

                    is UpdateCheckResult.UpdateAvailable ->

                        _updaterState
                            .value
                            .copy(
                                checkingApp =
                                    false,

                                appStatus =
                                    "Update available: ${result.version}",

                                latestAppVersion =
                                    result.version,

                                releasePageUrl =
                                    result.pageUrl
                                        .ifBlank {
                                            FrxeUpdateRepository
                                                .RELEASES_PAGE
                                        }
                            )

                    is UpdateCheckResult.UpToDate ->

                        _updaterState
                            .value
                            .copy(
                                checkingApp =
                                    false,

                                appStatus =
                                    "Up to date",

                                latestAppVersion =
                                    result.latestVersion,

                                releasePageUrl =
                                    FrxeUpdateRepository
                                        .RELEASES_PAGE
                            )

                    UpdateCheckResult.NoPublishedRelease ->

                        _updaterState
                            .value
                            .copy(
                                checkingApp =
                                    false,

                                appStatus =
                                    "No published GitHub release yet",

                                latestAppVersion =
                                    null,

                                releasePageUrl =
                                    FrxeUpdateRepository
                                        .RELEASES_PAGE
                            )

                    is UpdateCheckResult.Error ->

                        _updaterState
                            .value
                            .copy(
                                checkingApp =
                                    false,

                                appStatus =
                                    "Update check failed: ${result.message}"
                            )
                }
        }
    }

    fun checkDependencyReleases() {

        if (
            _updaterState
                .value
                .checkingDependencies
        ) {
            return
        }

        viewModelScope.launch {

            _updaterState.value =
                _updaterState
                    .value
                    .copy(
                        checkingDependencies =
                            true,

                        dependencies =
                            emptyList()
                    )

            val statuses =
                dependencyReleaseRepository
                    .checkAll()

            _updaterState.value =
                _updaterState
                    .value
                    .copy(
                        checkingDependencies =
                            false,

                        dependencies =
                            statuses
                    )
        }
    }

    fun refreshYtDlpVersion() {

        if (
            _updaterState
                .value
                .updatingYtDlp
        ) {
            return
        }

        viewModelScope.launch(
            Dispatchers.IO
        ) {

            val app =
                getApplication<Application>()

            val status =
                runCatching {

                    YoutubeDL
                        .getInstance()
                        .init(
                            app
                        )

                    YoutubeDL
                        .getInstance()
                        .versionName(
                            app
                        )
                        ?: YoutubeDL
                            .getInstance()
                            .version(
                                app
                            )
                        ?: "unknown"
                }
                    .fold(
                        onSuccess = {
                                version ->

                            "Installed yt-dlp: $version"
                        },

                        onFailure = {
                                error ->

                            "yt-dlp version check failed: " +
                            (
                                error.message
                                    ?: "unknown error"
                            )
                        }
                    )

            _updaterState.value =
                _updaterState
                    .value
                    .copy(
                        ytDlpStatus =
                            status
                    )
        }
    }

    fun updateYtDlpEngine() {

        if (
            _updaterState
                .value
                .updatingYtDlp
        ) {
            return
        }

        viewModelScope.launch(
            Dispatchers.IO
        ) {

            _updaterState.value =
                _updaterState
                    .value
                    .copy(
                        updatingYtDlp =
                            true,

                        ytDlpStatus =
                            "Updating yt-dlp engine…"
                    )

            val result =
                runCatching {

                    val app =
                        getApplication<Application>()

                    YoutubeDL
                        .getInstance()
                        .init(
                            app
                        )

                    val updateStatus =
                        YoutubeDL
                            .getInstance()
                            .updateYoutubeDL(
                                app,
                                YoutubeDL
                                    .UpdateChannel
                                    .STABLE
                            )

                    val installedVersion =
                        YoutubeDL
                            .getInstance()
                            .versionName(
                                app
                            )
                            ?: YoutubeDL
                                .getInstance()
                                .version(
                                    app
                                )
                            ?: "unknown"

                    updateStatus to
                    installedVersion
                }

            _updaterState.value =
                _updaterState
                    .value
                    .copy(
                        updatingYtDlp =
                            false,

                        ytDlpStatus =
                            result.fold(

                                onSuccess = {
                                        (
                                            status,
                                            version
                                        ) ->

                                    "Installed yt-dlp: $version · " +
                                    (
                                        status?.name
                                            ?: "already current"
                                    )
                                },

                                onFailure = {
                                        error ->

                                    "yt-dlp update failed: " +
                                    (
                                        error.message
                                            ?: "unknown error"
                                    )
                                }
                            )
                    )
        }
    }

    fun handleVoice(
        command: VoiceCommand
    ) {

        when (
            command
        ) {

            VoiceCommand.Play ->
                controller
                    ?.play()

            VoiceCommand.Pause ->
                controller
                    ?.pause()

            VoiceCommand.Next ->
                next()

            VoiceCommand.Previous ->
                previous()

            is VoiceCommand.SeekBy ->

                seek(
                    (
                        controller
                            ?.currentPosition
                            ?: 0L
                    ) +
                    command.deltaMs
                )

            is VoiceCommand.Search ->

                viewModelScope.launch {

                    val result =
                        source
                            .search(
                                command.query
                            )
                            .firstOrNull()

                    if (
                        result != null
                    ) {

                        play(
                            result
                        )
                    }
                }

            VoiceCommand.Unknown ->
                Unit
        }
    }

    private fun refreshNowPlaying() {

        val player =
            controller

        if (
            player == null
        ) {

            _playerState.value =
                _playerState
                    .value
                    .copy(
                        sleepTimerRemainingMs =
                            if (
                                sleepDeadlineMs >
                                0L
                            ) {

                                sleepRemaining(
                                    sleepDeadlineMs,
                                    System.currentTimeMillis()
                                )

                            } else {

                                0L
                            },

                        canvasMode =
                            canvasMode,

                        autoDjEnabled =
                            autoDjEnabled
                    )

            return
        }

        val mediaItem =
            player.currentMediaItem

        val mediaId =
            mediaItem?.mediaId

        val track =
            mediaId
                ?.let(
                    tracksById::get
                )
                ?: mediaItem
                    ?.toTrackFromSession()
                    ?.also {
                            restored ->

                        tracksById[
                            restored.id
                        ] = restored
                    }

        if (
            track != null &&
            _playerState
                .value
                .track
                ?.id !=
            track.id
        ) {

            loadLyrics(
                track
            )
        }

        _playerState.value =
            PlayerUiState(
                track =
                    track,

                isPlaying =
                    player.isPlaying,

                positionMs =
                    player
                        .currentPosition
                        .coerceAtLeast(
                            0L
                        ),

                durationMs =
                    player.duration
                        .takeIf {
                            it > 0
                        }
                        ?: track
                            ?.durationMs
                        ?: 0L,

                bufferedPercent =
                    player.bufferedPercentage,

                queue =
                    PlaybackQueueStore.tracks(),

                volume =
                    player.volume,

                playbackSpeed =
                    player
                        .playbackParameters
                        .speed,

                playbackPitch =
                    player
                        .playbackParameters
                        .pitch,

                shuffleEnabled =
                    player
                        .shuffleModeEnabled,

                repeatMode =
                    when (
                        player.repeatMode
                    ) {

                        Player.REPEAT_MODE_ALL ->
                            FrxeRepeatMode.All

                        Player.REPEAT_MODE_ONE ->
                            FrxeRepeatMode.One

                        else ->
                            FrxeRepeatMode.Off
                    },

                sleepTimerRemainingMs =
                    if (
                        sleepDeadlineMs >
                        0L
                    ) {

                        sleepRemaining(
                            sleepDeadlineMs,
                            System.currentTimeMillis()
                        )

                    } else {

                        0L
                    },

                canvasMode =
                    canvasMode,

                autoDjEnabled =
                    autoDjEnabled
            )
    }

    private fun MediaItem
        .toTrackFromSession():
        Track? {

        val mediaUri =
            requestMetadata
                .mediaUri
                ?.toString()
                ?: return null

        val metadata =
            mediaMetadata

        val extras =
            metadata.extras

        val restoredId =
            mediaId
                .ifBlank {
                    mediaUri
                }

        return Track(
            id =
                restoredId,

            title =
                metadata
                    .title
                    ?.toString()
                    ?.ifBlank {
                        "Frxe track"
                    }
                    ?: "Frxe track",

            artist =
                metadata
                    .artist
                    ?.toString()
                    ?.ifBlank {
                        "Unknown artist"
                    }
                    ?: "Unknown artist",

            album =
                metadata
                    .albumTitle
                    ?.toString()
                    .orEmpty(),

            streamUrl =
                mediaUri,

            durationMs =
                extras
                    ?.getLong(
                        EXTRA_DURATION_MS,
                        0L
                    )
                    ?: 0L,

            artworkSeed =
                extras
                    ?.getInt(
                        EXTRA_ARTWORK_SEED,
                        restoredId
                            .hashCode()
                    )
                    ?: restoredId
                        .hashCode(),

            artworkUrl =
                metadata
                    .artworkUri
                    ?.toString(),

            downloadUrl =
                extras
                    ?.getString(
                        EXTRA_DOWNLOAD_URL
                    )
        )
    }

    override fun onCleared() {

        sleepTimerJob
            ?.cancel()

        searchJob
            ?.cancel()

        lyricsJob
            ?.cancel()

        downloadCoordinator
            .cancel()

        saveJob
            ?.cancel()

        listenTogetherManager
            .close()

        controller
            ?.removeListener(
                playerListener
            )

        MediaController
            .releaseFuture(
                controllerFuture
            )

        super.onCleared()
    }
}