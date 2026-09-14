package com.frxe.music.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frxe.music.model.Track
import com.frxe.music.save.DownloadQueueActions
import com.frxe.music.save.DownloadQueueItem
import com.frxe.music.save.DownloadQueueItemState
import com.frxe.music.save.DownloadQueueStore
import com.frxe.music.save.FrxeDownloadService
import com.frxe.music.save.SaveFormat
import com.frxe.music.save.SaveQuality
import com.frxe.music.save.SaveRequest
import com.frxe.music.save.defaultQualityFor
import com.frxe.music.save.qualityOptionsFor
import com.frxe.music.ui.FrxeViewModel
import com.frxe.music.ui.components.GeneratedArtwork
import com.frxe.music.ui.components.GlassPanel

@Composable
fun SaveScreen(
    viewModel: FrxeViewModel,
    isTv: Boolean
) {
    // Keep the existing screen signature stable; queue state now lives outside the ViewModel.
    viewModel.playerState

    val context = LocalContext.current
    val queue by DownloadQueueStore.items.collectAsState()
    val wifiOnly by DownloadQueueStore.wifiOnly.collectAsState()

    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(SaveFormat.MP3) }
    var quality by remember { mutableStateOf(SaveQuality.Mp3K320) }
    var enqueueMessage by remember { mutableStateOf<String?>(null) }

    val previewTrack = remember(title, artist, url) {
        Track(
            id = "save-preview",
            title = title.ifBlank { "Frxe Save" },
            artist = artist.ifBlank { "Unknown artist" },
            album = "Frxe Save",
            streamUrl = url,
            durationMs = 0L,
            artworkSeed = (title + artist + url).hashCode()
        )
    }

    LaunchedEffect(format) {
        if (quality !in qualityOptionsFor(format)) {
            quality = defaultQualityFor(format)
        }
    }

    val active = queue.filter { it.state == DownloadQueueItemState.Running }
    val queued = queue.filter { it.state == DownloadQueueItemState.Queued }
    val paused = queue.filter { it.state == DownloadQueueItemState.Paused }
    val failed = queue.filter { it.state == DownloadQueueItemState.Failed }
    val completed = queue.filter { it.state == DownloadQueueItemState.Complete }
        .sortedByDescending { it.updatedAtMs }
        .take(30)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = if (isTv) 56.dp else 20.dp,
                end = if (isTv) 56.dp else 20.dp,
                top = 54.dp,
                bottom = 190.dp
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Downloads",
            fontSize = if (isTv) 42.sp else 32.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Queue multiple tracks. Frxe processes one conversion at a time so playback and the rest of the app stay responsive.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            radius = 26.dp,
            strong = true
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GeneratedArtwork(previewTrack, size = 64.dp, radius = 18.dp)
                    Column(Modifier.weight(1f)) {
                        Text(previewTrack.title, fontWeight = FontWeight.Bold)
                        Text(
                            previewTrack.artist,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                GlassTextField(url, { url = it }, "Media or YouTube URL")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(title, { title = it }, "Title", Modifier.weight(1f))
                    GlassTextField(artist, { artist = it }, "Artist", Modifier.weight(1f))
                }

                Text("Format", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SaveFormat.entries.forEach { item ->
                        FrxeChoiceChip(
                            item.displayName,
                            selected = format == item
                        ) { format = item }
                    }
                }

                Text("Quality", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    qualityOptionsFor(format).forEach { item ->
                        FrxeChoiceChip(
                            item.label,
                            selected = quality == item
                        ) { quality = item }
                    }
                }

                QueueActionChip(
                    label = "Add to download queue",
                    icon = Icons.Default.Download,
                    enabled = url.isNotBlank()
                ) {
                    val result = FrxeDownloadService.enqueue(
                        context,
                        SaveRequest(
                            sourceUrl = url.trim(),
                            title = title.ifBlank { "Frxe export" },
                            artist = artist.ifBlank { "Unknown artist" },
                            format = format,
                            quality = quality
                        )
                    )
                    enqueueMessage = if (result.duplicate) {
                        when (result.item.state) {
                            DownloadQueueItemState.Complete -> "Already downloaded"
                            else -> "Already in download queue"
                        }
                    } else {
                        "Added to queue"
                    }
                }

                enqueueMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            radius = 24.dp,
            strong = false
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    ) {
                        Text("Wi-Fi only", fontWeight = FontWeight.Bold)
                        Text(
                            if (wifiOnly) "Queued downloads wait for Wi-Fi" else "Downloads can use any active network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = {
                            FrxeDownloadService.setWifiOnly(context, it)
                        }
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QueueActionChip(
                        label = "Pause all",
                        icon = Icons.Default.Pause,
                        enabled = active.isNotEmpty() || queued.isNotEmpty()
                    ) {
                        FrxeDownloadService.pauseAll(context)
                    }
                    QueueActionChip(
                        label = "Resume all",
                        icon = Icons.Default.PlayArrow,
                        enabled = paused.isNotEmpty()
                    ) {
                        FrxeDownloadService.resumeAll(context)
                    }
                }
            }
        }

        QueueSection("Active", active, context)
        QueueSection("Queued", queued, context)
        QueueSection("Paused", paused, context)
        QueueSection("Needs attention", failed, context)
        QueueSection("Downloaded", completed, context)

        if (queue.none { it.state != DownloadQueueItemState.Cancelled }) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                radius = 24.dp,
                strong = false
            ) {
                Text(
                    "Your download queue is empty.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QueueSection(
    title: String,
    items: List<DownloadQueueItem>,
    context: android.content.Context
) {
    if (items.isEmpty()) return

    Text(
        "$title · ${items.size}",
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        color = MaterialTheme.colorScheme.onBackground
    )

    items.forEach { item ->
        DownloadQueueCard(item, context)
    }
}

@Composable
private fun DownloadQueueCard(
    item: DownloadQueueItem,
    context: android.content.Context
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        radius = 24.dp,
        strong = item.state == DownloadQueueItemState.Running
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title.ifBlank { "Frxe export" },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        item.artist.ifBlank { "Unknown artist" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    item.state.displayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                buildString {
                    append(formatLabel(item.format))
                    append(" · ")
                    append(qualityLabel(item.quality))
                    item.backend?.let {
                        append(" · ")
                        append(it)
                    }
                    if (item.retryCount > 0) {
                        append(" · retry ")
                        append(item.retryCount)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                item.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (item.state == DownloadQueueItemState.Running) {
                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.14f)
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (item.state) {
                    DownloadQueueItemState.Running,
                    DownloadQueueItemState.Queued -> {
                        QueueActionChip("Pause", Icons.Default.Pause) {
                            FrxeDownloadService.pause(context, item.id)
                        }
                        QueueActionChip("Cancel", Icons.Default.Cancel) {
                            FrxeDownloadService.cancel(context, item.id)
                        }
                    }

                    DownloadQueueItemState.Paused -> {
                        QueueActionChip("Resume", Icons.Default.PlayArrow) {
                            FrxeDownloadService.resume(context, item.id)
                        }
                        QueueActionChip("Cancel", Icons.Default.Cancel) {
                            FrxeDownloadService.cancel(context, item.id)
                        }
                    }

                    DownloadQueueItemState.Failed -> {
                        QueueActionChip("Retry", Icons.Default.Refresh) {
                            FrxeDownloadService.retry(context, item.id)
                        }
                        QueueActionChip("Remove", Icons.Default.Delete) {
                            FrxeDownloadService.remove(item.id)
                        }
                    }

                    DownloadQueueItemState.Complete -> {
                        QueueActionChip("Redownload", Icons.Default.Refresh) {
                            DownloadQueueActions.redownload(context, item.id)
                        }
                        QueueActionChip("Remove file", Icons.Default.Delete) {
                            DownloadQueueActions.removeDownloadedFile(context, item.id)
                        }
                    }

                    DownloadQueueItemState.Cancelled -> {
                        QueueActionChip("Remove", Icons.Default.Delete) {
                            FrxeDownloadService.remove(item.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = {
            Text(
                placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = Color.Black.copy(alpha = 0.16f),
            unfocusedContainerColor = Color.Black.copy(alpha = 0.10f),
            focusedBorderColor = Color.White.copy(alpha = 0.34f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.16f)
        )
    )
}

@Composable
private fun FrxeChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Black.copy(alpha = 0.22f),
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun QueueActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            Icon(icon, contentDescription = null)
        },
        label = {
            Text(label, fontWeight = FontWeight.SemiBold)
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Black.copy(alpha = 0.22f),
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = Color.Black.copy(alpha = 0.10f),
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

private val DownloadQueueItemState.displayLabel: String
    get() = when (this) {
        DownloadQueueItemState.Queued -> "Queued"
        DownloadQueueItemState.Running -> "Downloading"
        DownloadQueueItemState.Paused -> "Paused"
        DownloadQueueItemState.Complete -> "Downloaded"
        DownloadQueueItemState.Failed -> "Failed"
        DownloadQueueItemState.Cancelled -> "Cancelled"
    }

private fun formatLabel(value: String): String =
    runCatching { SaveFormat.valueOf(value).displayName }
        .getOrDefault(value)

private fun qualityLabel(value: String): String =
    runCatching { SaveQuality.valueOf(value).label }
        .getOrDefault(value)
