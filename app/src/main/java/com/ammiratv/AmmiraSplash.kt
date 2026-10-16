package com.ammiratv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ammiratv.core.ui.theme.AmmiraCoral
import com.ammiratv.core.ui.theme.AmmiraRuby
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Launch animation, about 2.6 s:
 *  1. a ruby glow blooms behind a black screen and shock-wave rings radiate out,
 *  2. the logo drops in with a spring bounce while spinning into place,
 *  3. it "breathes" twice as the glow pulses,
 *  4. it zooms through the screen and fades, revealing the app composed underneath.
 */
@Composable
fun AmmiraSplash(onFinished: () -> Unit) {
    val logoScale = remember { Animatable(0.1f) }
    val logoAlpha = remember { Animatable(0f) }
    val logoRotation = remember { Animatable(-35f) }
    val glow = remember { Animatable(0f) }          // 0..1 radial glow strength
    val ringA = remember { Animatable(0f) }         // 0..1 progress of each radiating ring
    val ringB = remember { Animatable(0f) }
    val fadeOut = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            // Glow blooms first
            launch { glow.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
            // Two shock-wave rings, the second slightly behind the first
            launch { ringA.animateTo(1f, tween(1100, easing = LinearEasing)) }
            launch {
                delay(250)
                ringB.animateTo(1f, tween(1100, easing = LinearEasing))
            }
            // Logo: spring in with overshoot, spinning from -35 degrees
            launch { logoAlpha.animateTo(1f, tween(250, easing = LinearEasing)) }
            launch {
                logoRotation.animateTo(
                    0f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                )
            }
            logoScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        }

        // Breathe twice while the glow pulses
        repeat(2) {
            coroutineScope {
                launch { glow.animateTo(0.55f, tween(260)) }
                logoScale.animateTo(1.09f, tween(260, easing = FastOutSlowInEasing))
            }
            coroutineScope {
                launch { glow.animateTo(1f, tween(260)) }
                logoScale.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            }
        }

        // Zoom through the screen
        coroutineScope {
            launch { fadeOut.animateTo(0f, tween(420, easing = LinearEasing)) }
            launch { glow.animateTo(2.2f, tween(420)) }
            logoScale.animateTo(2.6f, tween(420, easing = FastOutSlowInEasing))
        }
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().alpha(fadeOut.value.coerceIn(0f, 1f))) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val base = minOf(size.width, size.height)

            // Radial glow, ruby core fading to coral and then transparent
            val glowRadius = base * 0.55f * glow.value.coerceAtLeast(0.01f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AmmiraRuby.copy(alpha = 0.55f * glow.value.coerceIn(0f, 1f)),
                        AmmiraCoral.copy(alpha = 0.22f * glow.value.coerceIn(0f, 1f)),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            // Shock-wave rings: radius grows, stroke thins and fades
            listOf(ringA.value to AmmiraRuby, ringB.value to AmmiraCoral).forEach { (p, color) ->
                if (p > 0f && p < 1f) {
                    drawCircle(
                        color = color.copy(alpha = (1f - p) * 0.9f),
                        radius = base * (0.18f + 0.7f * p),
                        center = center,
                        style = Stroke(width = (1f - p) * 14f + 1f)
                    )
                }
            }
        }

        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .widthIn(max = 400.dp)
                // The artwork sits on an opaque black square: round it so the glow wraps the logo
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(percent = 24))
                .rotate(logoRotation.value)
                .scale(logoScale.value)
                .alpha((logoAlpha.value * fadeOut.value).coerceIn(0f, 1f))
        )
    }
}
