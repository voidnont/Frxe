package com.frxe.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frxe.music.data.FrxePlaylist
import com.frxe.music.data.FrxePlaylistTrack
import com.frxe.music.model.Track
import com.frxe.music.ui.FrxeViewModel
import com.frxe.music.ui.addPlaylistToQueue
import com.frxe.music.ui.createPlaylist
import com.frxe.music.ui.deletePlaylist
import com.frxe.music.ui.downloadPlaylist
import com.frxe.music.ui.playPlaylist
import com.frxe.music.ui.playQueued
import com.frxe.music.ui.playlistRepository
import com.frxe.music.ui.removeTrackFromPlaylist
import com.frxe.music.ui.renamePlaylist
import com.frxe.music.ui.components.GeneratedArtwork
import com.frxe.music.ui.components.GlassPanel
import com.frxe.music.ui.components.LiquidIconButton
import com.frxe.music.ui.components.springPress

@Composable
fun LibraryScreen(
    viewModel: FrxeViewModel,
    isTv: Boolean,
    onOpenPlayer: (Track) -> Unit = {}
) {
    val library by viewModel.library.collectAsState()
    val history by viewModel.history.collectAsState()
    val playlistRepository = remember(viewModel) {
        viewModel.playlistRepository()
    }
    val playlists by playlistRepository.playlists.collectAsState(initial = emptyList())

    var creatingPlaylist by remember {
        mutableStateOf(false)
    }
    var newPlaylistName by remember {
        mutableStateOf("")
    }
    var selectedPlaylist by remember {
        mutableStateOf<FrxePlaylist?>(null)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(
            start = if (isTv) 56.dp else 20.dp,
            end = if (isTv) 56.dp else 20.dp,
            top = 54.dp
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Library", fontSize = if (isTv) 42.sp else 32.sp, fontWeight = FontWeight.Black)
                Text("Playlists, saved and recently played", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LiquidIconButton(
                onClick = { creatingPlaylist = true },
                size = 44.dp,
                emphasized = true
            ) {
                Icon(Icons.Default.Add, "Create playlist")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 16.dp),
            contentPadding = PaddingValues(bottom = 210.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Playlists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            if (playlists.isEmpty()) {
                item {
                    GlassPanel(Modifier.fillMaxWidth(), radius = 24.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlaylistAdd, null)
                            Column(Modifier.padding(start = 14.dp)) {
                                Text("No playlists yet", fontWeight = FontWeight.SemiBold)
                                Text("Tap + to create one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                items(playlists, key = { "playlist-${it.id}" }) { playlist ->
                    GlassPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedPlaylist = playlist },
                        radius = 20.dp,
                        padding = PaddingValues(12.dp),
                        strong = true
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlaylistAdd, null)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(playlist.name, fontWeight = FontWeight.Bold)
                                Text("Local Frxe playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LiquidIconButton(
                                onClick = { viewModel.playPlaylist(playlist.id) },
                                size = 40.dp
                            ) {
                                Icon(Icons.Default.PlayArrow, "Play playlist")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Saved",
                    modifier = Modifier.padding(top = 18.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            if (library.isEmpty()) {
                item {
                    GlassPanel(Modifier.fillMaxWidth(), radius = 24.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FavoriteBorder, null)
                            Column(Modifier.padding(start = 14.dp)) {
                                Text("Nothing saved yet", fontWeight = FontWeight.SemiBold)
                                Text("Open a track and tap Save to add it here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            items(library, key = { "saved-${it.id}" }) { track ->
                LibraryTrackRow(
                    track = track,
                    queue = library,
                    viewModel = viewModel,
                    onOpenPlayer = onOpenPlayer
                )
            }

            if (history.isNotEmpty()) {
                item {
                    Text(
                        "Recently played",
                        modifier = Modifier.padding(top = 18.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(history, key = { "history-${it.id}" }) { track ->
                    LibraryTrackRow(
                        track = track,
                        queue = history,
                        viewModel = viewModel,
                        onOpenPlayer = onOpenPlayer
                    )
                }
            }
        }
    }

    if (creatingPlaylist) {
        AlertDialog(
            onDismissRequest = { creatingPlaylist = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newPlaylistName.isNotBlank(),
                    onClick = {
                        viewModel.createPlaylist(newPlaylistName)
                        newPlaylistName = ""
                        creatingPlaylist = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { creatingPlaylist = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedPlaylist?.let { playlist ->
        PlaylistDetailSheet(
            playlist = playlist,
            viewModel = viewModel,
            onDismiss = { selectedPlaylist = null },
            onOpenPlayer = onOpenPlayer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetailSheet(
    playlist: FrxePlaylist,
    viewModel: FrxeViewModel,
    onDismiss: () -> Unit,
    onOpenPlayer: (Track) -> Unit
) {
    val repository = remember(viewModel) {
        viewModel.playlistRepository()
    }
    val tracks by repository.tracks(playlist.id)
        .collectAsState(initial = emptyList())
    var renameValue by remember(playlist.id, playlist.name) {
        mutableStateOf(playlist.name)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        tonalElevation = 0.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(playlist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("${tracks.size} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { viewModel.playPlaylist(playlist.id) },
                    enabled = tracks.isNotEmpty(),
                    label = { Text("Play") },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, null) }
                )
                AssistChip(
                    onClick = { viewModel.addPlaylistToQueue(playlist.id) },
                    enabled = tracks.isNotEmpty(),
                    label = { Text("Queue all") },
                    leadingIcon = { Icon(Icons.Default.QueueMusic, null) }
                )
                AssistChip(
                    onClick = { viewModel.downloadPlaylist(playlist.id) },
                    enabled = tracks.isNotEmpty(),
                    label = { Text("Download all") },
                    leadingIcon = { Icon(Icons.Default.Download, null) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Playlist name") }
                )
                AssistChip(
                    enabled = renameValue.isNotBlank(),
                    onClick = {
                        viewModel.renamePlaylist(playlist.id, renameValue)
                    },
                    label = { Text("Rename") }
                )
            }

            if (tracks.isEmpty()) {
                GlassPanel(Modifier.fillMaxWidth(), radius = 22.dp) {
                    Text("This playlist is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(tracks, key = { it.rowId }) { row ->
                        PlaylistTrackRow(
                            row = row,
                            allTracks = tracks.map { it.track },
                            viewModel = viewModel,
                            playlistId = playlist.id,
                            onOpenPlayer = onOpenPlayer,
                            onDismiss = onDismiss
                        )
                    }
                }
            }

            AssistChip(
                onClick = {
                    viewModel.deletePlaylist(playlist.id)
                    onDismiss()
                },
                label = { Text("Delete playlist") },
                leadingIcon = { Icon(Icons.Default.Delete, null) }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    row: FrxePlaylistTrack,
    allTracks: List<Track>,
    viewModel: FrxeViewModel,
    playlistId: Long,
    onOpenPlayer: (Track) -> Unit,
    onDismiss: () -> Unit
) {
    val track = row.track
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                onOpenPlayer(track)
                viewModel.playQueued(track, allTracks)
                onDismiss()
            },
        radius = 20.dp,
        padding = PaddingValues(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GeneratedArtwork(track, size = 48.dp, radius = 12.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, fontWeight = FontWeight.SemiBold)
                Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LiquidIconButton(
                onClick = {
                    viewModel.removeTrackFromPlaylist(
                        playlistId,
                        row.rowId
                    )
                },
                size = 38.dp
            ) {
                Icon(Icons.Default.Delete, "Remove from playlist")
            }
        }
    }
}

@Composable
private fun LibraryTrackRow(
    track: Track,
    queue: List<Track>,
    viewModel: FrxeViewModel,
    onOpenPlayer: (Track) -> Unit
) {
    val interactionSource = remember(track.id, queue.size) { MutableInteractionSource() }
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .springPress(interactionSource)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onOpenPlayer(track)
                viewModel.playQueued(track, queue)
            },
        radius = 20.dp,
        padding = PaddingValues(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GeneratedArtwork(track, size = 58.dp, radius = 16.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, fontWeight = FontWeight.SemiBold)
                Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LiquidIconButton(onClick = { viewModel.openDownload(track) }, size = 40.dp) {
                Icon(Icons.Default.Download, "Download")
            }
        }
    }
}
