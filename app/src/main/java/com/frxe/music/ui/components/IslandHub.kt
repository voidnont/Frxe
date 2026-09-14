package com.frxe.music.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frxe.music.island.IslandLayoutPolicy
import com.frxe.music.island.IslandSwipePolicy
import com.frxe.music.model.FrxeRepeatMode
import com.frxe.music.model.PlayerUiState
import com.frxe.music.playback.PlaybackQueueStore
import com.frxe.music.playback.QueueRepeatMode
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IslandHub(
    player: PlayerUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val track = player.track ?: return
    var expanded by rememberSaveable(track.id) { mutableStateOf(false) }
    var dismissed by rememberSaveable(track.id) { mutableStateOf(false) }
    var swipeDistance by remember(track.id) { mutableFloatStateOf(0f) }
    if (dismissed) return

    val width by animateDpAsState(
        targetValue = if (expanded) 344.dp else 174.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "island-width"
    )

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val displayWidthPx = with(density) {
        configuration.screenWidthDp.dp.roundToPx()
    }
    val windowWidthPx = with(density) { width.roundToPx() }
    val fallbackCameraPx = with(density) { 18.dp.roundToPx() }
    val cameraPaddingPx = with(density) { 10.dp.roundToPx() }
    val legacyParentStartPx = with(density) { 12.dp.roundToPx() }
    val legacyParentTopPx = with(density) { 6.dp.roundToPx() }

    val statusBarTopPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view.rootWindowInsets
            ?.getInsets(android.view.WindowInsets.Type.statusBars())
            ?.top
            ?: 0
    } else {
        @Suppress("DEPRECATION")
        view.rootWindowInsets?.systemWindowInsetTop ?: 0
    }

    val cutoutRect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        view.rootWindowInsets
            ?.displayCutout
            ?.boundingRects
            ?.minByOrNull { rect ->
                abs(rect.centerX() - displayWidthPx / 2)
            }
    } else {
        null
    }

    val safeZone = remember(
        displayWidthPx,
        windowWidthPx,
        cutoutRect?.left,
        cutoutRect?.right
    ) {
        IslandLayoutPolicy.cameraSafeZone(
            displayWidthPx = displayWidthPx,
            windowWidthPx = windowWidthPx,
            cutoutLeftPx = cutoutRect?.left,
            cutoutRightPx = cutoutRect?.right,
            fallbackDiameterPx = fallbackCameraPx,
            paddingPx = cameraPaddingPx
        )
    }

    val topBandHeight = with(density) {
        max(
            cutoutRect?.bottom ?: 0,
            40.dp.roundToPx()
        ).toDp()
    }
    val leftWidth = with(density) { safeZone.leftPx.toDp() }
    val cameraWidth = with(density) { safeZone.widthPx.toDp() }
    val rightWidth = with(density) {
        (windowWidthPx - safeZone.rightPx)
            .coerceAtLeast(0)
            .toDp()
    }

    val centeredWindowXPx = IslandLayoutPolicy.centeredWindowX(
        displayWidthPx = displayWidthPx,
        windowWidthPx = windowWidthPx
    )

    val queueState by PlaybackQueueStore.state.collectAsState()
    val queueLabel = queueState.current?.let {
        "${queueState.currentIndex + 1} of ${queueState.entries.size}"
    } ?: "Queue"
    val queueRepeatMode = when (player.repeatMode) {
        FrxeRepeatMode.All -> QueueRepeatMode.All
        FrxeRepeatMode.One -> QueueRepeatMode.One
        FrxeRepeatMode.Off -> QueueRepeatMode.Off
    }

    val progress = if (player.durationMs > 0L) {
        (player.positionMs.toFloat() / player.durationMs.toFloat())
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(if (expanded) 34.dp else 24.dp)

    Column(
        modifier = Modifier
            .width(width)
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.70f)
            )
            .clip(shape)
            .background(Color.Black)
            .graphicsLayer {
                translationX =
                    centeredWindowXPx.toFloat() -
                        legacyParentStartPx.toFloat()
                translationY =
                    -(
                        statusBarTopPx +
                            legacyParentTopPx
                        ).toFloat()
                alpha = (
                    1f - abs(swipeDistance) / 650f
                ).coerceIn(0.72f, 1f)
            }
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onDragCancel = {
                        swipeDistance = 0f
                    },
                    onDragEnd = {
                        if (
                            IslandSwipePolicy.shouldDismiss(
                                swipeDistance,
                                88.dp.toPx()
                            )
                        ) {
                            dismissed = true
                        }
                        swipeDistance = 0f
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        swipeDistance += amount
                    }
                )
            }
            .springPress(
                interactionSource,
                pressedScale = 0.992f
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (!expanded) {
                        onOpenPlayer()
                    }
                },
                onLongClick = {
                    expanded = !expanded
                }
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        CameraSafeTopBand(
            height = topBandHeight,
            leftWidth = leftWidth,
            cameraWidth = cameraWidth,
            rightWidth = rightWidth,
            player = player,
            track = track
        )

        AnimatedVisibility(expanded) {
            Column(
                modifier = Modifier.padding(
                    start = 14.dp,
                    end = 14.dp,
                    bottom = 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GeneratedArtwork(
                        track = track,
                        size = 46.dp,
                        radius = 14.dp
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 11.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White.copy(alpha = 0.64f)
                        )
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(Color.White)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = queueLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.60f)
                    )
                    Text(
                        text = "Long-press to collapse",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.44f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactIslandButton(
                        onClick = {
                            if (queueState.entries.isNotEmpty()) {
                                PlaybackQueueStore.previous(
                                    queueRepeatMode
                                )
                            } else {
                                onPrevious()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.size(18.dp))
                    CompactIslandButton(
                        onClick = onPlayPause,
                        emphasized = true
                    ) {
                        Icon(
                            if (player.isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription =
                                if (player.isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.size(18.dp))
                    CompactIslandButton(
                        onClick = {
                            if (queueState.entries.isNotEmpty()) {
                                PlaybackQueueStore.advance(
                                    repeatMode = queueRepeatMode,
                                    shuffle = player.shuffleEnabled
                                )
                            } else {
                                onNext()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraSafeTopBand(
    height: Dp,
    leftWidth: Dp,
    cameraWidth: Dp,
    rightWidth: Dp,
    player: PlayerUiState,
    track: com.frxe.music.model.Track
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(leftWidth),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.padding(start = 10.dp)) {
                GeneratedArtwork(
                    track = track,
                    size = 27.dp,
                    radius = 9.dp
                )
            }
        }

        Spacer(
            Modifier
                .width(cameraWidth)
                .height(height)
        )

        Box(
            modifier = Modifier.width(rightWidth),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(Modifier.padding(end = 13.dp)) {
                IslandWaveform(
                    isPlaying = player.isPlaying
                )
            }
        }
    }
}

@Composable
private fun CompactIslandButton(
    onClick: () -> Unit,
    emphasized: Boolean = false,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(if (emphasized) 48.dp else 40.dp)
            .background(
                Color.White.copy(
                    alpha = if (emphasized) 0.20f else 0.10f
                ),
                CircleShape
            )
    ) {
        content()
    }
}

@Composable
private fun IslandWaveform(
    isPlaying: Boolean
) {
    val transition = rememberInfiniteTransition(
        label = "island-wave"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620),
            repeatMode = RepeatMode.Reverse
        ),
        label = "island-wave-pulse"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(13.dp)
    ) {
        listOf(5f, 10f, 7f, 12f).forEachIndexed { index, base ->
            val scale by animateFloatAsState(
                targetValue = if (isPlaying) {
                    0.62f + pulse * (0.22f + index * 0.035f)
                } else {
                    0.35f
                },
                animationSpec = spring(
                    stiffness = Spring.StiffnessLow
                ),
                label = "bar-$index"
            )

            Box(
                Modifier
                    .width(2.dp)
                    .height(base.dp)
                    .graphicsLayer {
                        scaleY = scale
                    }
                    .background(
                        Color.White.copy(alpha = 0.84f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
