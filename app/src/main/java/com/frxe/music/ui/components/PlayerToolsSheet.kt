package com.frxe.music.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frxe.music.audio.AudioControl
import com.frxe.music.model.PlayerUiState
import com.frxe.music.ui.FrxeViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerToolsSheet(
    player: PlayerUiState,
    viewModel: FrxeViewModel,
    context: Context,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Player controls", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                radius = 26.dp,
                padding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Volume ${(player.volume * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
                    Slider(value = player.volume, onValueChange = viewModel::setVolume, valueRange = 0f..1f)
                }
            }

            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                radius = 26.dp,
                padding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Playback speed ${"%.2fx".format(player.playbackSpeed)}", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            AssistChip(
                                onClick = { viewModel.setPlaybackSpeed(speed) },
                                label = { Text("${speed}x") }
                            )
                        }
                    }
                }
            }

            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                radius = 26.dp,
                padding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Playback", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = viewModel::toggleShuffle,
                            label = { Text(if (player.shuffleEnabled) "Shuffle on" else "Shuffle off") }
                        )
                        AssistChip(
                            onClick = viewModel::cycleRepeatMode,
                            label = { Text("Repeat ${player.repeatMode.name.lowercase()}") }
                        )
                    }
                }
            }

            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                radius = 26.dp,
                padding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sleep timer", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60).forEach { minutes ->
                            AssistChip(onClick = { viewModel.setSleepTimer(minutes) }, label = { Text("$minutes min") })
                        }
                        AssistChip(onClick = { viewModel.setSleepTimer(null) }, label = { Text("Off") })
                    }
                    if (player.sleepTimerRemainingMs > 0L) {
                        Text(
                            "Stops in ${player.sleepTimerRemainingMs / 60_000L + 1L} min",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                radius = 26.dp,
                padding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Visuals", fontWeight = FontWeight.SemiBold)
                    AssistChip(
                        onClick = viewModel::cycleCanvasMode,
                        label = { Text("Canvas: ${player.canvasMode.name}") }
                    )
                }
            }

            Button(onClick = { AudioControl.openEqualizer(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open system equalizer")
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
