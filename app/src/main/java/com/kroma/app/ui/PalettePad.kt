package com.kroma.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kroma.app.engine.StyleState

/**
 * iOS Photographic Styles 2D Control Pad:
 * Terracotta-to-cool dark gradient surface with dotted crosshairs,
 * default anchor dot, and movable puck controlling Color (X) and Tone (Y).
 */
@Composable
fun PalettePad(
    state: StyleState,
    onPadChange: (color: Float, tone: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnPadChange by rememberUpdatedState(onPadChange)
    val currentAnchorX by rememberUpdatedState(state.preset.anchorX)
    val currentAnchorY by rememberUpdatedState(state.preset.anchorY)
    val isMonochrome by rememberUpdatedState(state.preset.monochrome)

    fun toPad(sizeW: Float, sizeH: Float, pos: Offset): Pair<Float, Float> {
        val ex = ((pos.x / sizeW) * 200f - 100f).coerceIn(-100f, 100f)
        val ey = (-((pos.y / sizeH) * 200f - 100f)).coerceIn(-100f, 100f) // up = +tone
        val padColor = if (isMonochrome) 0f else (ex - currentAnchorX)
        val padTone = ey - currentAnchorY
        return padColor to padTone
    }

    val padShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .size(110.dp)
            .clip(padShape)
            .drawBehind {
                val gradient = if (isMonochrome) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF9AA4B0), // slate top-left
                            Color(0xFF14171A), // charcoal bottom
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF222834), // cool dark bottom-left
                            Color(0xFF5A4440), // mid transition
                            Color(0xFF9E6454), // warm terracotta top-right
                        ),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, 0f),
                    )
                }
                drawRect(gradient)
            }
            .border(1.dp, Color(0x45FFFFFF), padShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val (c, t) = toPad(size.width.toFloat(), size.height.toFloat(), pos)
                        currentOnPadChange(c, t)
                    },
                    onDrag = { change, _ ->
                        val (c, t) = toPad(size.width.toFloat(), size.height.toFloat(), change.position)
                        currentOnPadChange(c, t)
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val (c, t) = toPad(size.width.toFloat(), size.height.toFloat(), pos)
                    currentOnPadChange(c, t)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun px(v: Float) = (v + 100f) / 200f * w
            fun py(v: Float) = (1f - (v + 100f) / 200f) * h

            // Clean 7x7 uniform dot grid
            val dotColor = Color(0x38FFFFFF)
            val dotRadius = 1.3.dp.toPx()
            val step = 27f
            for (gx in -3..3) {
                for (gy in -3..3) {
                    drawCircle(dotColor, radius = dotRadius, center = Offset(px(gx * step), py(gy * step)))
                }
            }

            // Preset anchor marker (small solid dot at default anchor)
            val ax = px(currentAnchorX)
            val ay = py(currentAnchorY)
            drawCircle(Color(0xCCFFFFFF), radius = 2.dp.toPx(), center = Offset(ax, ay))

            // Movable puck at effective coordinate (anchor + pad)
            val cx = px(state.effectiveColor)
            val cy = py(state.effectiveTone)
            // Soft shadow & glow under puck
            drawCircle(Color(0x59000000), radius = 10.dp.toPx(), center = Offset(cx, cy + 1.5.dp.toPx()))
            drawCircle(Color(0x40FFFFFF), radius = 9.dp.toPx(), center = Offset(cx, cy))
            // Solid white puck
            drawCircle(Color.White, radius = 7.5.dp.toPx(), center = Offset(cx, cy))
            // Subtle hairline border
            drawCircle(Color(0x33000000), radius = 7.5.dp.toPx(), center = Offset(cx, cy), style = Stroke(1.dp.toPx()))
        }
    }
}
