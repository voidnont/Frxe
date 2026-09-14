package com.frxe.music.ui.components

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * The backdrop sampled by Frxe glass surfaces. The root scene swaps this between the ambient
 * artwork layer and the full content layer, so panels never record themselves recursively.
 */
val LocalFrxeBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .frxeGlassSurface(shape = shape, strong = strong)
            .padding(padding)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Color(0xFFF9F9FC)
        ) {
            content()
        }
    }
}

@Composable
fun LiquidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    emphasized: Boolean = false,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquid-button-scale"
    )
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .frxeGlassSurface(shape = shape, strong = emphasized)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            content = content
        )
    }
}

@Composable
fun Modifier.springPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.965f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "spring-press"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
private fun Modifier.frxeGlassSurface(
    shape: Shape,
    strong: Boolean
): Modifier {
    val backdrop = LocalFrxeBackdrop.current
    val tint = if (strong) Color(0xFF090B10).copy(alpha = 0.62f) else Color(0xFF0A0C12).copy(alpha = 0.38f)
    val border = if (strong) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.18f)

    val glass = if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(if (strong) 10.dp.toPx() else 14.dp.toPx())
                lens(
                    refractionHeight = if (strong) 20.dp.toPx() else 16.dp.toPx(),
                    refractionAmount = if (strong) 30.dp.toPx() else 22.dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = true
                )
            },
            onDrawSurface = {
                drawRect(tint)
            }
        )
    } else {
        Modifier.background(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF171A22).copy(alpha = if (strong) 0.86f else 0.68f),
                    Color(0xFF080A0F).copy(alpha = if (strong) 0.72f else 0.54f)
                )
            ),
            shape = shape
        )
    }

    return this
        .shadow(if (strong) 24.dp else 18.dp, shape, clip = false)
        .then(glass)
        .border(BorderStroke(1.dp, border), shape)
}
