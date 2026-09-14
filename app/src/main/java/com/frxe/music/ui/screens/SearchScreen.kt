package com.frxe.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frxe.music.model.Track
import com.frxe.music.playback.AudioOnlyPlaybackPolicy
import com.frxe.music.save.automaticDownloadSource
import com.frxe.music.source.PlaybackStreamResolver
import com.frxe.music.ui.FrxeViewModel
import com.frxe.music.ui.playQueued
import com.frxe.music.ui.components.GeneratedArtwork
import com.frxe.music.ui.components.GlassPanel
import com.frxe.music.ui.components.LiquidIconButton
import com.frxe.music.ui.components.springPress

@Composable
fun SearchScreen(
    viewModel: FrxeViewModel,
    isTv: Boolean,
    onOpenSave: () -> Unit,
    onOpenPlayer: (Track) -> Unit = {}
) {
    val results by viewModel.searchResults.collectAsState()
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = if (isTv) 56.dp else 20.dp,
                end = if (isTv) 56.dp else 20.dp,
                top = 54.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Search", fontSize = if (isTv) 42.sp else 32.sp, fontWeight = FontWeight.Black)
                Text("Search and stream music with Frxe", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LiquidIconButton(
                onClick = onOpenSave,
                size = if (isTv) 52.dp else 44.dp,
                emphasized = true
            ) {
                Icon(Icons.Default.Download, contentDescription = "Open Save converter")
            }
        }

        GlassPanel(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            radius = 24.dp,
            padding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            strong = true
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.search(it)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Search Frxe") },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 14.dp),
            contentPadding = PaddingValues(bottom = 210.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(results, key = { it.id }) { track ->
                val interactionSource = remember(track.id) { MutableInteractionSource() }
                val directPlayable = AudioOnlyPlaybackPolicy.isPlayable(track.streamUrl)
                val catalogResolvable = PlaybackStreamResolver.isCatalogTrack(track.streamUrl)
                val playable = directPlayable || catalogResolvable
                val downloadable = automaticDownloadSource(track.downloadUrl, track.streamUrl) != null

                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .springPress(interactionSource)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            enabled = playable,
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            onOpenPlayer(track)
                            viewModel.playQueued(track, results)
                        },
                    radius = 20.dp,
                    padding = PaddingValues(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GeneratedArtwork(track = track, size = 58.dp, radius = 16.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(track.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${track.artist} • ${track.album}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                when {
                                    catalogResolvable -> "Ready to stream"
                                    directPlayable -> "Ready"
                                    else -> "Audio source unavailable"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (downloadable) {
                            LiquidIconButton(onClick = { viewModel.openDownload(track) }, size = 40.dp) {
                                Icon(Icons.Default.Download, "Download")
                            }
                        }
                    }
                }
            }
        }
    }
}
