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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kroma.app.engine.StyleState

/**
 * The spec §1 surface: a 2D touch boundary where **X = Color**, **Y = Tone** (both `[-100,+100]`).
 *
 * Two markers are drawn:
 *  * the **preset anchor** (small accent crosshair) — where the puck *rests* by default, and
 *  * the **puck** — the *effective* coordinate (`pad + anchor`) the shader receives.
 *
 * Dragging writes back the *pad offset* (`effective - anchor`), matching
 * `effectiveCoords = clamp(u_PadCoordinates + u_PresetAnchor)`.
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

    Box(
        modifier = modifier
            .size(KromaDimens.PadSize)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1F26), Color(0xFF0E1116))))
            .border(1.dp, KromaColors.SheetEdge, RoundedCornerShape(18.dp))
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

            // crosshair through the origin
            val hair = Color(0x1FFFFFFF)
            drawLine(hair, Offset(px(0f), 0f), Offset(px(0f), h), strokeWidth = 1.5f)
            drawLine(hair, Offset(0f, py(0f)), Offset(w, py(0f)), strokeWidth = 1.5f)

            // preset anchor marker
            val ax = px(anchorX)
            val ay = py(anchorY)
            drawCircle(KromaColors.Accent.copy(alpha = 0.30f), radius = 12f, center = Offset(ax, ay), style = Stroke(1.5f))
            drawCircle(KromaColors.Accent, radius = 3.5f, center = Offset(ax, ay))

            // puck at the effective (anchor + pad) coordinate
            val cx = px(state.effectiveColor)
            val cy = py(state.effectiveTone)
            drawCircle(Color(0x66000000), radius = 20f, center = Offset(cx, cy + 3f))
            drawCircle(Color.White, radius = 17f, center = Offset(cx, cy))
            drawCircle(Color(0x33000000), radius = 17f, center = Offset(cx, cy), style = Stroke(1.5f))
        }
    }
}
