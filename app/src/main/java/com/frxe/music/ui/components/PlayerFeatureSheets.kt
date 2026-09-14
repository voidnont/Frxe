package com.frxe.music.ui.components

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frxe.music.audio.AudioControl
import com.frxe.music.lyrics.LyricsUiState
import com.frxe.music.model.PlayerUiState
import com.frxe.music.model.Track
import com.frxe.music.playback.PlaybackQueueStore
import com.frxe.music.ui.FrxeViewModel
import com.frxe.music.ui.PlayerAction
import com.frxe.music.ui.addToQueue
import com.frxe.music.ui.addTrackToPlaylist
import com.frxe.music.ui.createPlaylist
import com.frxe.music.ui.moveQueueEntry
import com.frxe.music.ui.playNext
import com.frxe.music.ui.playQueued
import com.frxe.music.ui.playbackQueueState
import com.frxe.music.ui.playlistRepository
import com.frxe.music.ui.playerActionGrid
import com.frxe.music.ui.removeQueueEntry
import com.frxe.music.ui.selectQueueEntry
import com.frxe.music.social.ListenTogetherManager
import com.frxe.music.social.ListenTogetherRole

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerActionsSheet(
    player: PlayerUiState,
    track: Track,
    isLiked: Boolean,
    isInLibrary: Boolean,
    viewModel: FrxeViewModel,
    onDismiss: () -> Unit,
    onShowDetails: () -> Unit,
    onShowArtist: () -> Unit,
    onShowAdvanced: () -> Unit,
    onShowListenTogether: () -> Unit
) {
    val context = LocalContext.current
    val copy = playerActionGrid().associateBy { it.action }
    val playlistRepository = remember(viewModel) {
        viewModel.playlistRepository()
    }
    val playlists by playlistRepository.playlists.collectAsState(initial = emptyList())
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 0.dp
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            item {
                GlassPanel(Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), strong = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, null)
                        Slider(
                            value = player.volume,
                            onValueChange = viewModel::setVolume,
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        )
                        Text("${(player.volume * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                ActionPair(
                    left = ActionTileSpec(Icons.Default.Shuffle, if (player.shuffleEnabled) "Shuffle · Ein" else copy.getValue(PlayerAction.Shuffle).label) { viewModel.toggleShuffle() },
                    right = ActionTileSpec(Icons.Default.Repeat, copy.getValue(PlayerAction.Repeat).label + " · ${player.repeatMode.name}") { viewModel.cycleRepeatMode() }
                )
            }
            item {
                ActionPair(
                    left = ActionTileSpec(Icons.Default.SkipNext, copy.getValue(PlayerAction.PlayNext).label) {
                        viewModel.playNext(track)
                        onDismiss()
                    },
                    right = ActionTileSpec(Icons.Default.QueueMusic, copy.getValue(PlayerAction.AddToQueue).label) {
                        viewModel.addToQueue(track)
                        onDismiss()
                    }
                )
            }
            item {
                ActionTile(
                    ActionTileSpec(Icons.Default.PlaylistAdd, copy.getValue(PlayerAction.AddToPlaylist).label) {
                        showPlaylistPicker = !showPlaylistPicker
                    },
                    Modifier.fillMaxWidth()
                )
            }
            if (showPlaylistPicker) {
                item {
                    GlassPanel(Modifier.fillMaxWidth(), radius = 22.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Add to playlist", fontWeight = FontWeight.Bold)
                            if (playlists.isEmpty()) {
                                Text("Create your first playlist below.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    playlists.forEach { playlist ->
                                        AssistChip(
                                            onClick = {
                                                viewModel.addTrackToPlaylist(playlist.id, track)
                                                onDismiss()
                                            },
                                            label = { Text(playlist.name) }
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = newPlaylistName,
                                onValueChange = { newPlaylistName = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("New playlist") }
                            )
                            AssistChip(
                                enabled = newPlaylistName.isNotBlank(),
                                onClick = {
                                    val name = newPlaylistName.trim()
                                    viewModel.createPlaylist(name) { playlistId ->
                                        viewModel.addTrackToPlaylist(playlistId, track)
                                    }
                                    onDismiss()
                                },
                                label = { Text("Create & add") },
                                leadingIcon = { Icon(Icons.Default.Add, null) }
                            )
                        }
                    }
                }
            }
            item {
                ActionPair(
                    left = ActionTileSpec(Icons.Default.Download, copy.getValue(PlayerAction.Download).label) {
                        onDismiss(); viewModel.openDownload(track)
                    },
                    right = ActionTileSpec(Icons.Default.LibraryAdd, if (isInLibrary) "Aus Bibliothek entfernen" else copy.getValue(PlayerAction.AddToLibrary).label) {
                        viewModel.toggleLibrary(track)
                    }
                )
            }
            item {
                ActionPair(
                    left = ActionTileSpec(Icons.Default.Favorite, if (isLiked) "Liked" else copy.getValue(PlayerAction.Like).label) { viewModel.toggleLike(track) },
                    right = ActionTileSpec(Icons.Default.Info, copy.getValue(PlayerAction.Details).label, copy.getValue(PlayerAction.Details).subtitle) { onDismiss(); onShowDetails() }
                )
            }
            item {
                ActionPair(
                    left = ActionTileSpec(Icons.Default.Person, copy.getValue(PlayerAction.ViewArtist).label, track.artist) { onDismiss(); onShowArtist() },
                    right = ActionTileSpec(Icons.Default.Equalizer, copy.getValue(PlayerAction.Equalizer).label, copy.getValue(PlayerAction.Equalizer).subtitle) { AudioControl.openEqualizer(context) }
                )
            }
            item {
                ActionPair(
                    left = ActionTileSpec(Icons.Default.Group, copy.getValue(PlayerAction.ListenTogether).label) { onDismiss(); onShowListenTogether() },
                    right = ActionTileSpec(Icons.Default.Fullscreen, copy.getValue(PlayerAction.AmbientMode).label, copy.getValue(PlayerAction.AmbientMode).subtitle) { viewModel.cycleCanvasMode() }
                )
            }
            item {
                ActionPair(
                    left = ActionTileSpec(Icons.Default.Refresh, "Retry Stream", "Quelle neu laden") { viewModel.retryStream() },
                    right = ActionTileSpec(Icons.Default.Tune, "Erweitert", "Tempo und Tonhöhe") { onDismiss(); onShowAdvanced() }
                )
            }
            item {
                ActionTile(
                    ActionTileSpec(Icons.Default.Notifications, "Set as Ringtone", "Für lokal gespeicherte Titel") {
                        setAsRingtone(context, track)
                    },
                    Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private data class ActionTileSpec(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit
)

@Composable
private fun ActionPair(left: ActionTileSpec, right: ActionTileSpec) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionTile(left, Modifier.weight(1f))
        ActionTile(right, Modifier.weight(1f))
    }
}

@Composable
private fun ActionTile(spec: ActionTileSpec, modifier: Modifier = Modifier) {
    GlassPanel(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = spec.onClick),
        radius = 22.dp,
        padding = PaddingValues(horizontal = 14.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(spec.icon, null, modifier = Modifier.size(27.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(spec.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                spec.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(player: PlayerUiState, viewModel: FrxeViewModel, onDismiss: () -> Unit) {
    val queue by viewModel.playbackQueueState.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Text("Warteschlange", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                "Long-press and drag to reorder",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                itemsIndexed(
                    queue.entries,
                    key = { _, item -> item.entryId }
                ) { index, item ->
                    val track = PlaybackQueueStore.run { item.toTrack() }
                    var dragY by remember(item.entryId) { mutableFloatStateOf(0f) }

                    GlassPanel(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(item.entryId, index, queue.entries.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragY = 0f },
                                    onDragCancel = { dragY = 0f },
                                    onDragEnd = { dragY = 0f }
                                ) { change, dragAmount ->
                                    change.consume()
                                    dragY += dragAmount.y

                                    if (dragY > 44f && index < queue.entries.lastIndex) {
                                        viewModel.moveQueueEntry(index, index + 1)
                                        dragY = 0f
                                    } else if (dragY < -44f && index > 0) {
                                        viewModel.moveQueueEntry(index, index - 1)
                                        dragY = 0f
                                    }
                                }
                            }
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                viewModel.selectQueueEntry(item.entryId)
                                onDismiss()
                            },
                        radius = 20.dp,
                        padding = PaddingValues(10.dp),
                        strong = index == queue.currentIndex
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GeneratedArtwork(track, size = 48.dp, radius = 12.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            LiquidIconButton(
                                onClick = { viewModel.removeQueueEntry(item.entryId) },
                                size = 38.dp
                            ) {
                                Icon(Icons.Default.Delete, "Remove from queue")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioSheet(player: PlayerUiState, viewModel: FrxeViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Audio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            GlassPanel(Modifier.fillMaxWidth(), radius = 24.dp) {
                Column {
                    Text("Lautstärke ${(player.volume * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
                    Slider(value = player.volume, onValueChange = viewModel::setVolume, valueRange = 0f..1f)
                }
            }
            GlassPanel(Modifier.fillMaxWidth(), radius = 24.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tempo ${"%.2fx".format(player.playbackSpeed)}", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                            AssistChip(onClick = { viewModel.setPlaybackSpeed(value) }, label = { Text("${value}x") })
                        }
                    }
                    Text("Tonhöhe ${"%.2fx".format(player.playbackPitch)}", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.75f, 1f, 1.25f, 1.5f).forEach { value ->
                            AssistChip(onClick = { viewModel.setPlaybackPitch(value) }, label = { Text("${value}x") })
                        }
                    }
                }
            }
            ActionTile(ActionTileSpec(Icons.Default.Equalizer, "System Equalizer") { AudioControl.openEqualizer(context) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(player: PlayerUiState, viewModel: FrxeViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Sleep-Timer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            if (player.sleepTimerRemainingMs > 0) {
                Text("Noch ${player.sleepTimerRemainingMs / 60_000L + 1L} min", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(15, 30, 45, 60, 90).forEach { minutes ->
                    AssistChip(onClick = { viewModel.setSleepTimer(minutes); onDismiss() }, label = { Text("$minutes min") })
                }
                AssistChip(onClick = { viewModel.setSleepTimer(null); onDismiss() }, label = { Text("Aus") })
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(lyrics: LyricsUiState, player: PlayerUiState, onDismiss: () -> Unit) {
    val current = if (lyrics.synced) lyrics.lines.indexOfLast { player.positionMs >= it.startMs } else -1
    ModalBottomSheet(onDismissRequest = onDismiss, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Songtext", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            lyrics.source?.let { source ->
                Text(source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            when {
                lyrics.isLoading -> Text("Songtext wird geladen…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                lyrics.lines.isEmpty() -> Text(lyrics.message ?: "Kein Songtext verfügbar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                    items(lyrics.lines.size) { index ->
                        Text(
                            lyrics.lines[index].text,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = if (index == current) FontWeight.Black else FontWeight.Medium,
                            color = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(track: Track, player: PlayerUiState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            DetailRow("Titel", track.title)
            DetailRow("Künstler", track.artist)
            DetailRow("Album", track.album.ifBlank { "—" })
            DetailRow("Dauer", formatDuration(player.durationMs))
            DetailRow("Quelle", Uri.parse(track.streamUrl).host ?: "Lokal")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    GlassPanel(Modifier.fillMaxWidth(), radius = 20.dp, padding = PaddingValues(12.dp)) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSheet(artist: String, tracks: List<Track>, viewModel: FrxeViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(artist, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Künstler", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                items(tracks, key = { it.id }) { item ->
                    GlassPanel(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable {
                            viewModel.playQueued(item, tracks); onDismiss()
                        },
                        radius = 20.dp,
                        padding = PaddingValues(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GeneratedArtwork(item, size = 48.dp, radius = 12.dp)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(item.album, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherSheet(viewModel: FrxeViewModel, track: Track, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.listenTogetherState.collectAsState()
    var joinCode by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, tonalElevation = 0.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Gemeinsam hören", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                "Synchronisiert Wiedergabe im gleichen WLAN/LAN. Der Host steuert Titel, Position und Play/Pause.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.role == ListenTogetherRole.Off) {
                ActionTile(
                    ActionTileSpec(Icons.Default.Add, "Raum erstellen", "Frxe erzeugt einen 6-stelligen Raumcode") {
                        viewModel.hostListenTogether()
                    },
                    Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { joinCode = ListenTogetherManager.normalizeRoomCode(it) },
                    label = { Text("Raumcode") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ActionTile(
                    ActionTileSpec(Icons.Default.Group, "Raum beitreten", "Host und Gast müssen im gleichen WLAN/LAN sein") {
                        viewModel.joinListenTogether(joinCode)
                    },
                    Modifier.fillMaxWidth()
                )
                if (state.status != "Bereit") {
                    Text(state.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                GlassPanel(Modifier.fillMaxWidth(), radius = 24.dp, strong = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (state.role == ListenTogetherRole.Host) "Host" else "Gast", fontWeight = FontWeight.Bold)
                        Text(state.roomCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text(state.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.role == ListenTogetherRole.Host) {
                    ActionTile(
                        ActionTileSpec(Icons.Default.Share, "Einladung teilen", "Raumcode ${state.roomCode}") {
                            shareListenTogetherInvite(context, state.roomCode, track)
                        },
                        Modifier.fillMaxWidth()
                    )
                }
                ActionTile(
                    ActionTileSpec(Icons.Default.ExitToApp, if (state.role == ListenTogetherRole.Host) "Raum beenden" else "Raum verlassen") {
                        viewModel.leaveListenTogether()
                    },
                    Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

private fun shareListenTogetherInvite(context: Context, roomCode: String, track: Track) {
    val text = "Frxe Gemeinsam hören · Raum $roomCode\n${track.title} — ${track.artist}\nÖffne Frxe im gleichen WLAN/LAN und tritt mit dem Raumcode bei."
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Gemeinsam hören"))
}

private fun setAsRingtone(context: Context, track: Track) {
    val uri = runCatching { Uri.parse(track.streamUrl) }.getOrNull()
    if (uri == null || uri.scheme != "content") {
        Toast.makeText(context, "Titel zuerst herunterladen, dann als Klingelton setzen.", Toast.LENGTH_LONG).show()
        return
    }
    if (!Settings.System.canWrite(context)) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
        Toast.makeText(context, "Erlaube Frxe Systemeinstellungen zu ändern und versuche es erneut.", Toast.LENGTH_LONG).show()
        return
    }
    runCatching {
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
    }.onSuccess {
        Toast.makeText(context, "Als Klingelton gesetzt.", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Klingelton konnte nicht gesetzt werden.", Toast.LENGTH_LONG).show()
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
