package com.frxe.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frxe.music.model.Track
import com.frxe.music.save.DownloadQueueItemState
import com.frxe.music.save.DownloadQueuePolicy
import com.frxe.music.save.DownloadQueueStore
import com.frxe.music.save.SaveFormat
import com.frxe.music.save.SaveQuality
import com.frxe.music.save.SaveUiState
import com.frxe.music.save.automaticDownloadSource
import com.frxe.music.save.defaultQualityFor
import com.frxe.music.save.qualityOptionsFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDownloadSheet(
    track: Track,
    state: SaveUiState,
    onDismiss: () -> Unit,
    onStart: (format: SaveFormat, quality: SaveQuality) -> Unit,
    onCancel: () -> Unit
) {
    // Keep legacy parameters while 0.6.3 moves actual work into DownloadQueueStore.
    state.stage
    onCancel.hashCode()

    val queue by DownloadQueueStore.items.collectAsState()

    val automaticSource = remember(
        track.id,
        track.downloadUrl,
        track.streamUrl
    ) {
        automaticDownloadSource(
            track.downloadUrl,
            track.streamUrl
        )
    }

    var format by remember(track.id) {
        mutableStateOf(SaveFormat.MP3)
    }

    var quality by remember(track.id) {
        mutableStateOf(defaultQualityFor(SaveFormat.MP3))
    }

    LaunchedEffect(format) {
        if (quality !in qualityOptionsFor(format)) {
            quality = defaultQualityFor(format)
        }
    }

    val existing = automaticSource?.let { source ->
        DownloadQueuePolicy.findDuplicate(
            queue,
            source,
            format.name,
            quality.name
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        dragHandle = null
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            radius = 32.dp,
            padding = PaddingValues(18.dp),
            strong = true
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GeneratedArtwork(track, size = 66.dp, radius = 18.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            track.artist,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Text(
                            "Add it to the background download queue",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    "Choose format",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SaveFormat.entries.forEach { item ->
                        FilterChip(
                            selected = format == item,
                            onClick = { format = item },
                            label = {
                                Text(
                                    item.displayName,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Black.copy(alpha = 0.22f),
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Text("Quality", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    qualityOptionsFor(format).forEach { item ->
                        FilterChip(
                            selected = quality == item,
                            onClick = { quality = item },
                            label = { Text(item.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Black.copy(alpha = 0.22f),
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    radius = 20.dp,
                    padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    strong = false
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Automatic download", fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                existing?.state == DownloadQueueItemState.Complete ->
                                    "Already downloaded"

                                existing != null ->
                                    "Already in queue · ${existing.state.name}"

                                automaticSource != null ->
                                    "InnerTube → NewPipe → yt-dlp → Zexl → Cobalt"

                                else ->
                                    "No compatible downloadable source is attached to this track"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (existing?.state == DownloadQueueItemState.Running) {
                            LinearProgressIndicator(
                                progress = { existing.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                existing.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val interaction = remember { MutableInteractionSource() }
                val enabled = automaticSource != null

                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (enabled) 1f else 0.48f)
                        .springPress(interaction, pressedScale = 0.98f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled
                        ) {
                            onDismiss()
                            onStart(format, quality)
                        },
                    radius = 24.dp,
                    padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                    strong = true
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Text(
                            when (existing?.state) {
                                DownloadQueueItemState.Complete -> "Already downloaded"
                                DownloadQueueItemState.Queued,
                                DownloadQueueItemState.Running,
                                DownloadQueueItemState.Paused -> "Already in queue"
                                else -> "Add ${format.displayName} to queue"
                            },
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
