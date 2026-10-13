package com.mindfortress.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindfortress.app.theme.*
import kotlin.math.cos
import kotlin.math.sin

enum class JarvisCoreState {
    STANDBY,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun ArcReactorCore(
    state: JarvisCoreState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcReactorCoreTransition")

    // Outer ring clockwise rotation
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    JarvisCoreState.LISTENING -> 4000
                    JarvisCoreState.THINKING -> 2000
                    JarvisCoreState.SPEAKING -> 5000
                    JarvisCoreState.STANDBY -> 12000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    // Inner ring counter-clockwise rotation
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    JarvisCoreState.LISTENING -> 3000
                    JarvisCoreState.THINKING -> 1500
                    JarvisCoreState.SPEAKING -> 4000
                    JarvisCoreState.STANDBY -> 9000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    // Breathing pulse scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    JarvisCoreState.LISTENING -> 600
                    JarvisCoreState.THINKING -> 400
                    JarvisCoreState.SPEAKING -> 800
                    JarvisCoreState.STANDBY -> 2200
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Primary energy colors based on operational mode
    val (primaryGlow, accentEnergy) = when (state) {
        JarvisCoreState.THINKING -> Pair(QuantumAmber, Color(0xFFFF7700))
        JarvisCoreState.LISTENING -> Pair(ArcReactorCyan, Color(0xFF00FF88))
        JarvisCoreState.SPEAKING -> Pair(ArcReactorCyan, ArcReactorBlue)
        JarvisCoreState.STANDBY -> Pair(ArcReactorCyan, FortressTealLight)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(240.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = (size.minDimension / 2f) * 0.82f

                // 1. Ambient outer energy glow halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = 0.28f * pulseScale),
                            accentEnergy.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = baseRadius * 1.35f * pulseScale
                    ),
                    radius = baseRadius * 1.35f * pulseScale,
                    center = center
                )

                // 2. Outermost Segmented Arc Reactor Ring (Clockwise)
                rotate(outerRotation, pivot = center) {
                    val segments = 8
                    val sweep = 360f / segments
                    val gap = 12f
                    for (i in 0 until segments) {
                        val startAngle = i * sweep + gap / 2f
                        drawArc(
                            color = primaryGlow.copy(alpha = 0.65f),
                            startAngle = startAngle,
                            sweepAngle = sweep - gap,
                            useCenter = false,
                            topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                            size = Size(baseRadius * 2, baseRadius * 2),
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Outer power tick pins
                        val angleRad = Math.toRadians((startAngle + (sweep - gap) / 2.0)).toFloat()
                        val pinInner = baseRadius + 4.dp.toPx()
                        val pinOuter = baseRadius + 9.dp.toPx()
                        drawLine(
                            color = primaryGlow.copy(alpha = 0.9f),
                            start = Offset(center.x + pinInner * cos(angleRad), center.y + pinInner * sin(angleRad)),
                            end = Offset(center.x + pinOuter * cos(angleRad), center.y + pinOuter * sin(angleRad)),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // 3. Middle Counter-Rotating Sub-Harmonic Ring
                val midRadius = baseRadius * 0.72f
                rotate(innerRotation, pivot = center) {
                    drawCircle(
                        color = primaryGlow.copy(alpha = 0.25f),
                        radius = midRadius,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    val nodes = 6
                    for (i in 0 until nodes) {
                        val angle = Math.toRadians((i * (360.0 / nodes))).toFloat()
                        val nodeCenter = Offset(
                            center.x + midRadius * cos(angle),
                            center.y + midRadius * sin(angle)
                        )
                        // Power node core
                        drawCircle(
                            color = accentEnergy,
                            radius = 3.5.dp.toPx(),
                            center = nodeCenter
                        )
                        drawCircle(
                            color = primaryGlow.copy(alpha = 0.4f),
                            radius = 7.dp.toPx(),
                            center = nodeCenter,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }

                // 4. Inner Quantum Power Chamber Ring
                val innerRadius = baseRadius * 0.46f * pulseScale
                drawCircle(
                    color = primaryGlow.copy(alpha = 0.8f),
                    radius = innerRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // 5. Central Arc Reactor Core (Radial Super-Luminescence)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            primaryGlow,
                            accentEnergy.copy(alpha = 0.8f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = innerRadius * 0.88f
                    ),
                    radius = innerRadius * 0.88f,
                    center = center
                )

                // 6. Center Quantum Tri-Ring Glyphs
                val coreSymbolRadius = innerRadius * 0.4f
                rotate(outerRotation * 1.5f, pivot = center) {
                    for (i in 0 until 3) {
                        val angle = Math.toRadians((i * 120.0)).toFloat()
                        val p = Offset(
                            center.x + coreSymbolRadius * cos(angle),
                            center.y + coreSymbolRadius * sin(angle)
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.85f),
                            start = center,
                            end = p,
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }

            // Audio Waveform frequency bars indicator
            if (state == JarvisCoreState.SPEAKING || state == JarvisCoreState.LISTENING) {
                AudioWaveformPulse(
                    isActive = true,
                    glowColor = primaryGlow,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // State Readout Telemetry
        val statusLabel = when (state) {
            JarvisCoreState.STANDBY -> "STANDBY // TAP TO COMMAND"
            JarvisCoreState.LISTENING -> "AUDIO INGEST // LISTENING..."
            JarvisCoreState.THINKING -> "FRONTIER ENGINE // SYNTHESIZING..."
            JarvisCoreState.SPEAKING -> "VOCAL CHANNEL // TRANSMITTING..."
        }

        Text(
            text = statusLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp,
            color = primaryGlow
        )
    }
}

@Composable
fun AudioWaveformPulse(
    isActive: Boolean,
    glowColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveformPulse")

    val bar1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(250, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 18f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(320, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = 28f,
        animationSpec = infiniteRepeatable(tween(190, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 22f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(280, easing = LinearEasing), RepeatMode.Reverse),
        label = "b4"
    )
    val bar5 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(220, easing = LinearEasing), RepeatMode.Reverse),
        label = "b5"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(30.dp)
    ) {
        val heights = listOf(bar1, bar2, bar3, bar4, bar5)
        heights.forEach { h ->
            Canvas(modifier = Modifier.size(width = 3.dp, height = h.dp)) {
                drawLine(
                    color = glowColor,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
