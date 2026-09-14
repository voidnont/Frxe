package com.frxe.music.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frxe.music.model.Track
import com.frxe.music.source.PlaybackResolutionMonitor
import com.frxe.music.source.PlaybackResolutionStage
import com.frxe.music.ui.components.FrxeAmbientBackground
import com.frxe.music.ui.components.GeneratedArtwork
import com.frxe.music.ui.components.GlassPanel
import com.frxe.music.ui.components.IslandHub
import com.frxe.music.ui.components.LiquidIconButton
import com.frxe.music.ui.components.LocalFrxeBackdrop
import com.frxe.music.ui.components.TrackDownloadSheet
import com.frxe.music.ui.components.springPress
import com.frxe.music.ui.screens.HomeScreen
import com.frxe.music.ui.screens.LibraryScreen
import com.frxe.music.ui.screens.NowPlayingScreen
import com.frxe.music.ui.screens.SaveScreen
import com.frxe.music.ui.screens.SearchScreen
import com.frxe.music.ui.screens.SettingsScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun FrxeApp(viewModel: FrxeViewModel) {
    val context = LocalContext.current
    val uiModeManager =
        context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    val isTv =
        uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

    var tab by remember {
        mutableStateOf(FrxeTab.Home)
    }
    var playerOpen by remember {
        mutableStateOf(false)
    }
    var pendingTrack by remember {
        mutableStateOf<Track?>(null)
    }

    val player by viewModel.playerState.collectAsState()
    val downloadTarget by viewModel.downloadTarget.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val islandHub by viewModel.islandHubState.collectAsState()
    val resolution by PlaybackResolutionMonitor.state.collectAsState()

    val openPlayerForTrack: (Track) -> Unit = { track ->
        pendingTrack = track
        playerOpen = true
    }

    LaunchedEffect(
        player.track?.id,
        pendingTrack?.id,
        resolution.trackId,
        resolution.stage
    ) {
        val pending = pendingTrack
            ?: return@LaunchedEffect

        if (
            player.track?.id == pending.id &&
            resolution.trackId == pending.id &&
            resolution.stage == PlaybackResolutionStage.Ready
        ) {
            pendingTrack = null
        }
    }

    val shownTrack = pendingTrack ?: player.track
    val shownResolution = resolution.takeIf {
        it.trackId == shownTrack?.id
    }

    val playbackMessage = when (shownResolution?.stage) {
        PlaybackResolutionStage.Resolving,
        PlaybackResolutionStage.Resolved,
        PlaybackResolutionStage.VerificationRequired,
        PlaybackResolutionStage.Failed ->
            shownResolution.message

        else -> null
    }

    val playbackCanRetry =
        shownResolution?.canRetry == true

    val ambientBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop {
        drawRect(Color(0xFF07070A))
        drawContent()
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(ambientBackdrop)
        ) {
            FrxeAmbientBackground(
                track = shownTrack,
                isPlaying = player.isPlaying,
                canvasMode = player.canvasMode
            )
        }

        CompositionLocalProvider(
            LocalFrxeBackdrop provides ambientBackdrop
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentBackdrop)
            ) {
                AnimatedContent(
                    targetState = playerOpen,
                    transitionSpec = {
                        (
                            fadeIn(
                                spring(
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) +
                                scaleIn(
                                    initialScale = 0.985f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            ) togetherWith
                            (
                                fadeOut(
                                    spring(
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) +
                                    scaleOut(
                                        targetScale = 1.015f
                                    )
                                )
                    },
                    label = "player-spring-transition"
                ) { open ->
                    if (open) {
                        NowPlayingScreen(
                            viewModel = viewModel,
                            onClose = {
                                playerOpen = false
                            },
                            pendingTrack = pendingTrack,
                            playbackMessage = playbackMessage,
                            playbackCanRetry = playbackCanRetry,
                            onRetry = {
                                shownTrack?.let { track ->
                                    pendingTrack = track
                                    viewModel.play(
                                        track,
                                        listOf(track)
                                    )
                                }
                            }
                        )
                    } else {
                        when (tab) {
                            FrxeTab.Home ->
                                HomeScreen(
                                    viewModel = viewModel,
                                    isTv = isTv,
                                    onOpenPlayer = openPlayerForTrack
                                )

                            FrxeTab.Search ->
                                SearchScreen(
                                    viewModel = viewModel,
                                    isTv = isTv,
                                    onOpenSave = {
                                        tab = FrxeTab.Save
                                    },
                                    onOpenPlayer = openPlayerForTrack
                                )

                            FrxeTab.Save ->
                                SaveScreen(
                                    viewModel,
                                    isTv
                                )

                            FrxeTab.Library ->
                                LibraryScreen(
                                    viewModel = viewModel,
                                    isTv = isTv,
                                    onOpenPlayer = openPlayerForTrack
                                )

                            FrxeTab.Settings ->
                                SettingsScreen(
                                    viewModel,
                                    isTv
                                )
                        }
                    }
                }
            }
        }

        if (!playerOpen) {
            CompositionLocalProvider(
                LocalFrxeBackdrop provides contentBackdrop
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            horizontal = if (isTv) 48.dp else 14.dp,
                            vertical = 10.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnimatedVisibility(
                        player.track != null
                    ) {
                        MiniPlayer(
                            viewModel = viewModel,
                            onOpen = {
                                playerOpen = true
                            }
                        )
                    }

                    BottomGlassNav(
                        tab = tab,
                        onTab = {
                            tab = it
                        },
                        isTv = isTv
                    )
                }
            }
        }

        if (
            !isTv &&
            !playerOpen &&
            islandHub.inAppEnabled &&
            player.track != null
        ) {
            CompositionLocalProvider(
                LocalFrxeBackdrop provides contentBackdrop
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(
                            start = 12.dp,
                            top = 6.dp
                        )
                ) {
                    IslandHub(
                        player = player,
                        onPlayPause = viewModel::togglePlayPause,
                        onPrevious = viewModel::previous,
                        onNext = viewModel::next,
                        onOpenPlayer = {
                            playerOpen = true
                        }
                    )
                }
            }
        }

        downloadTarget?.let { track ->
            CompositionLocalProvider(
                LocalFrxeBackdrop provides contentBackdrop
            ) {
                TrackDownloadSheet(
                    track = track,
                    state = saveState,
                    onDismiss = viewModel::closeDownload,
                    onStart = { format, quality ->
                        viewModel.startTrackDownload(
                            track,
                            format,
                            quality
                        )
                    },
                    onCancel = viewModel::cancelSave
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    viewModel: FrxeViewModel,
    onOpen: () -> Unit
) {
    val state by viewModel.playerState.collectAsState()
    val track = state.track ?: return
    val interactionSource = remember {
        MutableInteractionSource()
    }

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .springPress(
                interactionSource,
                pressedScale = 0.985f
            )
            .clip(
                RoundedCornerShape(22.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpen
            ),
        radius = 22.dp,
        padding = PaddingValues(10.dp),
        strong = true
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            GeneratedArtwork(
                track,
                size = 48.dp,
                radius = 14.dp
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    track.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    track.artist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            LiquidIconButton(
                onClick = {
                    viewModel.openDownload(track)
                },
                size = 38.dp
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Download"
                )
            }

            Spacer(
                Modifier.size(6.dp)
            )

            LiquidIconButton(
                onClick = viewModel::togglePlayPause,
                size = 42.dp
            ) {
                Icon(
                    imageVector =
                        if (state.isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                    contentDescription =
                        if (state.isPlaying) {
                            "Pause"
                        } else {
                            "Play"
                        }
                )
            }
        }
    }
}

@Composable
private fun BottomGlassNav(
    tab: FrxeTab,
    onTab: (FrxeTab) -> Unit,
    isTv: Boolean
) {
    val icons = mapOf(
        FrxeTab.Home to Icons.Default.Home,
        FrxeTab.Library to Icons.Default.LibraryMusic,
        FrxeTab.Settings to Icons.Default.Settings,
        FrxeTab.Search to Icons.Default.Search
    )

    val selectedPrimary =
        if (tab == FrxeTab.Save) {
            FrxeTab.Search
        } else {
            tab
        }

    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        radius = 32.dp,
        padding = PaddingValues(
            horizontal = 7.dp,
            vertical = 7.dp
        ),
        strong = true
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            primaryNavigationTabs().forEach { item ->
                val icon = requireNotNull(
                    icons[item]
                )
                val selected =
                    item == selectedPrimary
                val interactionSource = remember(item) {
                    MutableInteractionSource()
                }
                val scale by animateFloatAsState(
                    targetValue =
                        if (selected) 1.02f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "adaptive-nav-${item.name}"
                )

                val itemModifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .springPress(
                        interactionSource,
                        pressedScale = 0.91f
                    )
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    .clip(
                        RoundedCornerShape(24.dp)
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            onTab(item)
                        }
                    )

                if (selected) {
                    GlassPanel(
                        modifier = itemModifier,
                        radius = 24.dp,
                        padding = PaddingValues(
                            horizontal =
                                if (isTv) 24.dp else 16.dp,
                            vertical =
                                if (isTv) 12.dp else 10.dp
                        ),
                        strong = true
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icon,
                                contentDescription = item.name,
                                tint = Color.White
                            )

                            Spacer(
                                Modifier.size(8.dp)
                            )

                            Text(
                                text = primaryNavigationLabel(item).orEmpty(),
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = itemModifier.padding(
                            horizontal =
                                if (isTv) 20.dp else 14.dp,
                            vertical =
                                if (isTv) 12.dp else 10.dp
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = item.name,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
