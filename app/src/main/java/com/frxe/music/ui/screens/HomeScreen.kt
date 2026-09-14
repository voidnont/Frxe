package com.frxe.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun HomeScreen(
    viewModel: FrxeViewModel,
    isTv: Boolean,
    onOpenPlayer: (Track) -> Unit = {}
) {
    val sections by viewModel.home.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = if (isTv) 56.dp else 20.dp,
            end = if (isTv) 56.dp else 20.dp,
            top = 54.dp,
            bottom = 210.dp
        ),
        verticalArrangement = Arrangement.spacedBy(30.dp)
    ) {
        item {
            Text("FRXE", fontSize = if (isTv) 46.sp else 34.sp, fontWeight = FontWeight.Black)
            Text(
                "Liquid sound. Zero visual noise.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )
        }
        if (sections.isEmpty()) {
            item {
                GlassPanel(radius = 24.dp, strong = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Finding music for you…", fontWeight = FontWeight.Bold)
                        Text(
                            "Frxe is loading real recommendations from your listening signals.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        items(sections.size) { index ->
            val section = sections[index]
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(section.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                section.subtitle?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    section.tracks.forEach { track ->
                        val playable =
                            AudioOnlyPlaybackPolicy.isPlayable(track.streamUrl) ||
                                PlaybackStreamResolver.isCatalogTrack(track.streamUrl)
                        val downloadable = automaticDownloadSource(track.downloadUrl, track.streamUrl) != null
                        TrackCard(
                            track = track,
                            isTv = isTv,
                            playable = playable,
                            downloadable = downloadable,
                            onClick = {
                                onOpenPlayer(track)
                                viewModel.playQueued(track, section.tracks)
                            },
                            onDownload = { viewModel.openDownload(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: Track,
    isTv: Boolean,
    playable: Boolean,
    downloadable: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    val interactionSource = remember(track.id) { MutableInteractionSource() }
    GlassPanel(
        modifier = Modifier
            .size(width = if (isTv) 220.dp else 172.dp, height = if (isTv) 304.dp else 252.dp)
            .springPress(interactionSource, pressedScale = 0.955f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                enabled = playable,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        radius = 24.dp,
        padding = PaddingValues(10.dp)
    ) {
        Column {
            GeneratedArtwork(
                track,
                modifier = Modifier,
                size = if (isTv) 198.dp else 152.dp,
                radius = 20.dp
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (!playable) {
                        Text("Audio unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                if (downloadable) {
                    LiquidIconButton(onClick = onDownload, size = 34.dp) {
                        Icon(Icons.Default.Download, "Download", modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}
