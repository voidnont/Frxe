package com.frxe.music.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frxe.music.lyrics.LyricsRepository
import com.frxe.music.lyrics.LyricsUiState
import com.frxe.music.model.Track
import com.frxe.music.ui.FrxeViewModel
import com.frxe.music.ui.queueNext
import com.frxe.music.ui.queuePrevious
import com.frxe.music.ui.components.ArtistSheet
import com.frxe.music.ui.components.AudioSheet
import com.frxe.music.ui.components.DetailsSheet
import com.frxe.music.ui.components.GlassPanel
import com.frxe.music.ui.components.LiquidIconButton
import com.frxe.music.ui.components.ListenTogetherSheet
import com.frxe.music.ui.components.LyricsSheet
import com.frxe.music.ui.components.PlayerActionsSheet
import com.frxe.music.ui.components.QueueSheet
import com.frxe.music.ui.components.SleepTimerSheet
import com.frxe.music.ui.components.WavySlider
import com.frxe.music.ui.gestures.PlayerGestureAction
import com.frxe.music.ui.gestures.PlayerGesturePreferences
import com.frxe.music.ui.gestures.playerGestures

private enum class PlayerSheet {
    Actions,
    Queue,
    Audio,
    Sleep,
    Lyrics,
    Details,
    Artist,
    ListenTogether
}

@Composable
fun NowPlayingScreen(
    viewModel: FrxeViewModel,
    onClose: () -> Unit,
    pendingTrack: Track? = null,
    playbackMessage: String? = null,
    playbackCanRetry: Boolean = false,
    onRetry: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTv =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
    val playerGesturesEnabled = remember(context) {
        PlayerGesturePreferences.enabled(context)
    }
    val player by viewModel.playerState.collectAsState()
    val likedIds by viewModel.likedIds.collectAsState()
    val library by viewModel.library.collectAsState()
    val track = pendingTrack ?: player.track
    var sheet by remember {
        mutableStateOf<PlayerSheet?>(null)
    }

    if (track == null) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Nothing playing")
        }
        return
    }

    val lyricsRepository = remember {
        LyricsRepository()
    }
    var lyrics by remember(track.id) {
        mutableStateOf(
            LyricsUiState(isLoading = true)
        )
    }

    LaunchedEffect(track.id) {
        lyrics = LyricsUiState(isLoading = true)
        lyrics = lyricsRepository.lyrics(track)
    }

    val trackReady =
        player.track?.id == track.id
    val isLiked =
        track.id in likedIds
    val isInLibrary =
        library.any {
            it.id == track.id
        }
    val shownPositionMs =
        if (trackReady) {
            player.positionMs
        } else {
            0L
        }
    val shownDurationMs =
        if (trackReady) {
            player.durationMs
        } else {
            track.durationMs
        }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .playerGestures(
                enabled = playerGesturesEnabled,
                isTv = isTv,
                blocked = sheet != null
            ) { action ->
                when (action) {
                    PlayerGestureAction.Previous ->
                        if (trackReady) {
                            viewModel.queuePrevious()
                        }

                    PlayerGestureAction.Next ->
                        if (trackReady) {
                            viewModel.queueNext()
                        }

                    PlayerGestureAction.Collapse ->
                        onClose()

                    PlayerGestureAction.None ->
                        Unit
                }
            }
    ) {
        val artworkBreathingRoom =
            maxHeight * 0.24f

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 28.dp
            ),
            verticalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidIconButton(
                        onClick = onClose,
                        size = 48.dp
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            "Close player",
                            modifier = Modifier.size(31.dp)
                        )
                    }

                    Spacer(
                        Modifier.weight(1f)
                    )

                    Text(
                        "Frxe",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        Modifier.size(10.dp)
                    )

                    LiquidIconButton(
                        onClick = {
                            sheet = PlayerSheet.Actions
                        },
                        size = 48.dp
                    ) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            "More",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            item {
                Spacer(
                    Modifier.height(artworkBreathingRoom)
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        Modifier.weight(1f)
                    ) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 2
                        )

                        Text(
                            track.artist,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 1
                        )

                        Text(
                            text = when {
                                playbackMessage != null ->
                                    playbackMessage

                                !trackReady ->
                                    "Preparing audio…"

                                player.autoDjEnabled ->
                                    "Auto-DJ · analysing…"

                                else ->
                                    "Auto-DJ · off"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color =
                                if (playbackMessage != null) {
                                    Color(0xFFFFB4AB)
                                } else {
                                    Color.White.copy(alpha = 0.66f)
                                }
                        )
                    }

                    if (playbackCanRetry) {
                        LiquidIconButton(
                            onClick = onRetry,
                            size = 52.dp,
                            emphasized = true
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                "Retry audio"
                            )
                        }

                        Spacer(
                            Modifier.size(8.dp)
                        )
                    }

                    LiquidIconButton(
                        onClick = {
                            sheet = PlayerSheet.Actions
                        },
                        size = 52.dp
                    ) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            "Track menu"
                        )
                    }

                    Spacer(
                        Modifier.size(8.dp)
                    )

                    LiquidIconButton(
                        onClick = {
                            viewModel.toggleLike(track)
                        },
                        size = 52.dp,
                        emphasized = isLiked
                    ) {
                        Icon(
                            if (isLiked) {
                                Icons.Default.Favorite
                            } else {
                                Icons.Default.FavoriteBorder
                            },
                            "Like"
                        )
                    }
                }
            }

            item {
                Column {
                    val max =
                        shownDurationMs
                            .coerceAtLeast(1L)
                            .toFloat()

                    WavySlider(
                        value = shownPositionMs
                            .coerceIn(
                                0L,
                                shownDurationMs.coerceAtLeast(1L)
                            )
                            .toFloat(),
                        onValueChange = {
                            if (trackReady) {
                                viewModel.seek(
                                    it.toLong()
                                )
                            }
                        },
                        valueRange = 0f..max,
                        isPlaying =
                            trackReady && player.isPlaying
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatTime(shownPositionMs),
                            color = Color.White.copy(alpha = 0.86f)
                        )

                        Spacer(
                            Modifier.weight(1f)
                        )

                        AutoDjPill(
                            enabled = player.autoDjEnabled,
                            onClick = viewModel::toggleAutoDj
                        )

                        Spacer(
                            Modifier.weight(1f)
                        )

                        Text(
                            formatTime(shownDurationMs),
                            color = Color.White.copy(alpha = 0.86f)
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidIconButton(
                        onClick = {
                            if (trackReady) {
                                viewModel.queuePrevious()
                            }
                        },
                        size = 68.dp
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            "Previous",
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    LiquidIconButton(
                        onClick = {
                            if (trackReady) {
                                viewModel.togglePlayPause()
                            } else if (playbackCanRetry) {
                                onRetry()
                            }
                        },
                        size = 92.dp,
                        emphasized = true
                    ) {
                        Icon(
                            if (trackReady && player.isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            if (trackReady && player.isPlaying) {
                                "Pause"
                            } else if (playbackCanRetry) {
                                "Retry audio"
                            } else {
                                "Play"
                            },
                            modifier = Modifier.size(50.dp)
                        )
                    }

                    LiquidIconButton(
                        onClick = {
                            if (trackReady) {
                                viewModel.queueNext()
                            }
                        },
                        size = 68.dp
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            "Next",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(9.dp)
                ) {
                    PlayerShortcut(
                        Icons.Default.QueueMusic,
                        "Warteschlange",
                        Modifier.weight(1f)
                    ) {
                        sheet = PlayerSheet.Queue
                    }

                    PlayerShortcut(
                        Icons.Default.VolumeUp,
                        "Audio",
                        Modifier.weight(1f)
                    ) {
                        sheet = PlayerSheet.Audio
                    }

                    PlayerShortcut(
                        Icons.Default.Timer,
                        "Sleep-Timer",
                        Modifier.weight(1f)
                    ) {
                        sheet = PlayerSheet.Sleep
                    }

                    PlayerShortcut(
                        Icons.Default.ChatBubbleOutline,
                        "Songtext",
                        Modifier.weight(1f)
                    ) {
                        sheet = PlayerSheet.Lyrics
                    }
                }
            }

            item {
                Spacer(
                    Modifier.navigationBarsPadding()
                )
            }
        }
    }

    when (sheet) {
        PlayerSheet.Actions ->
            PlayerActionsSheet(
                player = player,
                track = track,
                isLiked = isLiked,
                isInLibrary = isInLibrary,
                viewModel = viewModel,
                onDismiss = {
                    sheet = null
                },
                onShowDetails = {
                    sheet = PlayerSheet.Details
                },
                onShowArtist = {
                    sheet = PlayerSheet.Artist
                },
                onShowAdvanced = {
                    sheet = PlayerSheet.Audio
                },
                onShowListenTogether = {
                    sheet = PlayerSheet.ListenTogether
                }
            )

        PlayerSheet.Queue ->
            QueueSheet(
                player,
                viewModel
            ) {
                sheet = null
            }

        PlayerSheet.Audio ->
            AudioSheet(
                player,
                viewModel
            ) {
                sheet = null
            }

        PlayerSheet.Sleep ->
            SleepTimerSheet(
                player,
                viewModel
            ) {
                sheet = null
            }

        PlayerSheet.Lyrics ->
            LyricsSheet(
                lyrics,
                player
            ) {
                sheet = null
            }

        PlayerSheet.Details ->
            DetailsSheet(
                track,
                player
            ) {
                sheet = null
            }

        PlayerSheet.Artist ->
            ArtistSheet(
                track.artist,
                viewModel.artistTracks(track.artist),
                viewModel
            ) {
                sheet = null
            }

        PlayerSheet.ListenTogether ->
            ListenTogetherSheet(
                viewModel,
                track
            ) {
                sheet = null
            }

        null -> Unit
    }
}

@Composable
private fun PlayerShortcut(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassPanel(
        modifier = modifier
            .clip(
                RoundedCornerShape(24.dp)
            )
            .clickable(
                onClick = onClick
            ),
        radius = 24.dp,
        padding = PaddingValues(
            vertical = 13.dp,
            horizontal = 6.dp
        ),
        strong = true
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(28.dp)
            )

            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AutoDjPill(
    enabled: Boolean,
    onClick: () -> Unit
) {
    GlassPanel(
        modifier = Modifier
            .clip(
                RoundedCornerShape(18.dp)
            )
            .clickable(
                onClick = onClick
            ),
        radius = 18.dp,
        padding = PaddingValues(
            horizontal = 14.dp,
            vertical = 6.dp
        ),
        strong = enabled
    ) {
        Text(
            if (enabled) {
                "✦ AUTO"
            } else {
                "AUTO OFF"
            },
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

private fun formatTime(ms: Long): String {
    val total =
        (ms.coerceAtLeast(0L) / 1000L).toInt()

    return "%d:%02d".format(
        total / 60,
        total % 60
    )
}
