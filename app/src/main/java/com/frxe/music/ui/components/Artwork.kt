package com.frxe.music.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frxe.music.model.CanvasMode
import com.frxe.music.model.Track
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val artworkPalettes = listOf(
    Color(0xFF9BFF65) to Color(0xFF12304A),
    Color(0xFFA78BFA) to Color(0xFF211636),
    Color(0xFFFF5C9E) to Color(0xFF33152A),
    Color(0xFF44DDF0) to Color(0xFF172353),
    Color(0xFFFFBD66) to Color(0xFF542447),
    Color(0xFF6EA8FF) to Color(0xFF10162D)
)

private val artworkCache = ConcurrentHashMap<String, ImageBitmap>()

fun artworkPalette(track: Track?): Pair<Color, Color> {
    if (track == null) return Color(0xFF9BFF65) to Color(0xFF18202B)
    return artworkPalettes[track.artworkSeed.absoluteValue % artworkPalettes.size]
}

private fun normalizeArtworkUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://", ignoreCase = true) -> value
        value.startsWith("https://", ignoreCase = true) -> value
        else -> null
    }
}

@Composable
private fun rememberRemoteArtwork(rawUrl: String?): ImageBitmap? {
    val artworkUrl = remember(rawUrl) { normalizeArtworkUrl(rawUrl) }
    var bitmap by remember(artworkUrl) {
        mutableStateOf(artworkUrl?.let(artworkCache::get))
    }

    LaunchedEffect(artworkUrl) {
        if (artworkUrl == null) {
            bitmap = null
            return@LaunchedEffect
        }

        artworkCache[artworkUrl]?.let { cached ->
            bitmap = cached
            return@LaunchedEffect
        }

        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(artworkUrl).openConnection().apply {
                    connectTimeout = 7_000
                    readTimeout = 10_000
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/152 Mobile Safari/537.36"
                    )
                    setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                }
                connection.getInputStream().use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }

        if (loaded != null) {
            artworkCache[artworkUrl] = loaded
        }
        bitmap = loaded
    }

    return bitmap
}

/**
 * Frxe animated player canvas with local, provider-independent rendering.
 */
@Composable
fun FrxeAmbientBackground(
    track: Track?,
    isPlaying: Boolean,
    canvasMode: CanvasMode,
    modifier: Modifier = Modifier
) {
    val palette = artworkPalette(track)
    val primary by animateColorAsState(
        palette.first,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "ambient-primary"
    )
    val secondary by animateColorAsState(
        palette.second,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "ambient-secondary"
    )
    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.38f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "canvas-energy"
    )

    val motion = rememberInfiniteTransition(label = "frxe-animated-canvas")
    val driftX by motion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "canvas-drift-x"
    )
    val driftY by motion.animateFloat(
        initialValue = 1f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(11_500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "canvas-drift-y"
    )
    val pulse by motion.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(6_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "canvas-pulse"
    )

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050507), Color(0xFF09090D), Color.Black)))
    ) {
        RemoteArtworkBackdrop(track = track)

        when (canvasMode) {
            CanvasMode.Minimal -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(primary.copy(alpha = 0.18f), secondary.copy(alpha = 0.16f), Color.Black)
                            )
                        )
                )
            }

            CanvasMode.Pulse -> {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(520.dp)
                        .graphicsLayer {
                            val scale = 0.82f + (pulse - 0.90f) * 1.4f * energy
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(primary.copy(alpha = 0.66f * energy), secondary.copy(alpha = 0.32f), Color.Transparent)
                            )
                        )
                        .blur(105.dp)
                )
            }

            CanvasMode.Liquid -> {
                Box(
                    Modifier
                        .size((330f * (1f + (pulse - 1f) * energy)).dp)
                        .offset(
                            x = (-80f + driftX * 56f * energy).dp,
                            y = (-70f + driftY * 42f * energy).dp
                        )
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.54f))
                        .blur(110.dp)
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(390.dp)
                        .offset(
                            x = (165f - driftX * 48f * energy).dp,
                            y = (-35f + driftY * 72f * energy).dp
                        )
                        .clip(CircleShape)
                        .background(secondary.copy(alpha = 0.60f))
                        .blur(130.dp)
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .size(360.dp)
                        .offset(
                            x = (driftX * 70f * energy).dp,
                            y = (190f - driftY * 38f * energy).dp
                        )
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.22f))
                        .blur(145.dp)
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.02f),
                            Color.Black.copy(alpha = 0.26f),
                            Color.Black.copy(alpha = 0.56f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun RemoteArtworkBackdrop(track: Track?) {
    val bitmap = rememberRemoteArtwork(track?.artworkUrl)

    bitmap?.let { image ->
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.92f
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.08f),
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.64f)
                        )
                    )
                )
        )
    }
}

@Composable
fun GeneratedArtwork(
    track: Track,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    radius: Dp = 18.dp
) {
    val pair = artworkPalette(track)
    val bitmap = rememberRemoteArtwork(track.artworkUrl)
    val shape = RoundedCornerShape(radius)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(pair.first, pair.second))),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "${track.title} cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = track.title.take(1).uppercase(),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = (size.value * 0.38f).sp
                ),
                color = Color.White.copy(alpha = 0.94f)
            )
        }
    }
}
