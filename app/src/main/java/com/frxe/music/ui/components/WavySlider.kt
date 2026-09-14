package com.frxe.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated seek control. The played segment ripples during playback and settles when paused.
 */
@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    isPlaying: Boolean = true,
    enabled: Boolean = true
) {
    val range = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val normalized = ((value - valueRange.start) / range).coerceIn(0f, 1f)
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val thumb = MaterialTheme.colorScheme.onSurface

    val transition = rememberInfiniteTransition(label = "frxe-wavy-slider")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "frxe-wave-phase"
    )

    val interactive = if (enabled) {
        modifier
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val n = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + n * range)
                }
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures { change, _ ->
                    val n = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + n * range)
                }
            }
    } else modifier

    Canvas(interactive.fillMaxWidth().height(34.dp)) {
        val centerY = size.height / 2f
        val progressX = size.width * normalized
        val strokeWidth = 4.dp.toPx()
        val amplitude = if (isPlaying) 4.5.dp.toPx() else 0f
        val wavelength = 34.dp.toPx().coerceAtLeast(1f)

        drawLine(
            color = inactive,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        if (progressX > 0f) {
            val path = Path().apply { moveTo(0f, centerY) }
            var x = 0f
            val step = 3.dp.toPx().coerceAtLeast(1f)
            while (x <= progressX) {
                val y = centerY + amplitude * sin((x / wavelength) * (2f * PI).toFloat() + phase)
                path.lineTo(x, y)
                x += step
            }
            path.lineTo(progressX, centerY + amplitude * sin((progressX / wavelength) * (2f * PI).toFloat() + phase))
            drawPath(path, color = active, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }

        drawCircle(
            color = thumb,
            radius = 7.dp.toPx(),
            center = Offset(progressX, centerY)
        )
    }
}
