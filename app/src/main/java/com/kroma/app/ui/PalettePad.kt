package com.kroma.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val anchorX = state.preset.anchorX
    val anchorY = state.preset.anchorY

    fun toPad(sizeW: Float, sizeH: Float, pos: Offset): Pair<Float, Float> {
        val ex = ((pos.x / sizeW) * 200f - 100f).coerceIn(-100f, 100f)
        val ey = (-((pos.y / sizeH) * 200f - 100f)).coerceIn(-100f, 100f) // up = +tone
        return (ex - anchorX) to (ey - anchorY)
    }

    val padShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .size(140.dp)
            .clip(padShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF222834), // cool dark bottom-left
                        Color(0xFF5A4440), // mid transition
                        Color(0xFF9E6454), // warm terracotta top-right
                    ),
                    start = Offset(0f, 400f),
                    end = Offset(400f, 0f),
                ),
            )
            .border(1.dp, Color(0x38FFFFFF), padShape)
            .pointerInput(state.presetId) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val (c, t) = toPad(size.width.toFloat(), size.height.toFloat(), pos)
                        onPadChange(c, t)
                    },
                    onDrag = { change, _ ->
                        val (c, t) = toPad(size.width.toFloat(), size.height.toFloat(), change.position)
                        onPadChange(c, t)
                    },
                )
            }
            .pointerInput(state.presetId) {
                detectTapGestures { pos ->
                    val (c, t) = toPad(size.width.toFloat(), size.height.toFloat(), pos)
                    onPadChange(c, t)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun px(v: Float) = (v + 100f) / 200f * w
            fun py(v: Float) = (1f - (v + 100f) / 200f) * h

            // Subtle dotted matrix or dots at quadrants
            val dotColor = Color(0x18FFFFFF)
            for (gx in -2..2) {
                for (gy in -2..2) {
                    if (gx != 0 || gy != 0) {
                        drawCircle(dotColor, radius = 1.2f, center = Offset(px(gx * 40f), py(gy * 40f)))
                    }
                }
            }

            // Dotted crosshair through the origin (0, 0)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
            val hairColor = Color(0x38FFFFFF)
            drawLine(hairColor, Offset(px(0f), 0f), Offset(px(0f), h), strokeWidth = 1.2f, pathEffect = dashEffect)
            drawLine(hairColor, Offset(0f, py(0f)), Offset(w, py(0f)), strokeWidth = 1.2f, pathEffect = dashEffect)

            // Preset anchor marker (small solid dot at default anchor)
            val ax = px(anchorX)
            val ay = py(anchorY)
            drawCircle(Color(0xCCFFFFFF), radius = 2.5.dp.toPx(), center = Offset(ax, ay))

            // Movable puck at effective coordinate (anchor + pad)
            val cx = px(state.effectiveColor)
            val cy = py(state.effectiveTone)
            // Soft shadow under puck
            drawCircle(Color(0x59000000), radius = 12.dp.toPx(), center = Offset(cx, cy + 2f))
            // Solid white puck
            drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(cx, cy))
            // Subtle hairline border
            drawCircle(Color(0x33000000), radius = 9.dp.toPx(), center = Offset(cx, cy), style = Stroke(1.dp.toPx()))
        }
    }
}
