package com.kroma.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Capture modes. */
object CameraMode {
    const val VIDEO = 0
    const val PHOTO = 1
    const val PORTRAIT = 2
}

private enum class Glyph { FLASH, LIVE, NIGHT, ASPECT, TIMER, EXPOSURE, STYLES, DEPTH, FORMAT, PRO }

private data class Tile(
    val label: String,
    val glyph: Glyph,
    val value: String,
    val enabled: Boolean,
    val soon: Boolean = false,
)

/**
 * The iOS-style camera **settings grid** opened by the yellow-dots button: FLASH · LIVE · ASPECT /
 * TIMER · EXPOSURE · STYLES / DEPTH · NIGHT MODE · FORMAT. Tiles backed by real hardware behave
 * for real; unsupported ones are dimmed and marked "soon" rather than faking it.
 */
@Composable
fun CameraSettingsGrid(
    flashValue: String,
    timerValue: String,
    exposureValue: String,
    aspectValue: String,
    proEnabled: Boolean,
    onFlash: () -> Unit,
    onTimer: () -> Unit,
    onExposure: () -> Unit,
    onAspect: () -> Unit,
    onPro: () -> Unit,
    onStyles: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tiles = listOf(
        Tile("FLASH", Glyph.FLASH, flashValue, enabled = true),
        Tile("LIVE", Glyph.LIVE, "soon", enabled = false, soon = true),
        Tile("ASPECT", Glyph.ASPECT, aspectValue, enabled = true),
        Tile("TIMER", Glyph.TIMER, timerValue, enabled = true),
        Tile("EXPOSURE", Glyph.EXPOSURE, exposureValue, enabled = true),
        Tile("STYLES", Glyph.STYLES, "Open", enabled = true),
        Tile("DEPTH", Glyph.DEPTH, "soon", enabled = false, soon = true),
        Tile("NIGHT MODE", Glyph.NIGHT, "soon", enabled = false, soon = true),
        Tile("FORMAT", Glyph.FORMAT, "JPEG", enabled = false),
        Tile("PRO", Glyph.PRO, if (proEnabled) "On" else "Off", enabled = true),
    )

    Box(Modifier.fillMaxSize()) {
        // tap-outside scrim (light — the preview stays visible above the panel)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x33000000))
                .clickable { onDismiss() },
        )

        val panelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(panelShape)
                .background(KromaColors.Sheet)
                .border(1.dp, KromaColors.SheetEdge, panelShape)
                .padding(horizontal = 14.dp)
                .padding(top = 12.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .padding(bottom = 10.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x33FFFFFF))
                    .align(Alignment.CenterHorizontally),
            )

            tiles.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { tile ->
                        SettingsTile(
                            tile = tile,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when (tile.glyph) {
                                    Glyph.FLASH -> onFlash()
                                    Glyph.TIMER -> onTimer()
                                    Glyph.EXPOSURE -> onExposure()
                                    Glyph.ASPECT -> onAspect()
                                    Glyph.STYLES -> onStyles()
                                    Glyph.PRO -> onPro()
                                    else -> Unit
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTile(tile: Tile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val contentColor = if (tile.enabled) Color.White else KromaColors.TextTertiary

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x1FFFFFFF))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                .clickable(enabled = tile.enabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            TileGlyph(glyph = tile.glyph, value = tile.value, tint = contentColor)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            tile.label,
            color = contentColor,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        if (tile.value.isNotEmpty() && !tile.soon) {
            Text(
                tile.value,
                color = if (tile.enabled) KromaColors.Accent else KromaColors.TextTertiary,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun TileGlyph(glyph: Glyph, value: String, tint: Color) {
    when (glyph) {
        Glyph.FLASH -> Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            val p = Path().apply {
                moveTo(w * 0.58f, 0f)
                lineTo(w * 0.12f, h * 0.56f)
                lineTo(w * 0.46f, h * 0.56f)
                lineTo(w * 0.38f, h)
                lineTo(w * 0.88f, h * 0.42f)
                lineTo(w * 0.54f, h * 0.42f)
                close()
            }
            drawPath(p, tint)
        }

        Glyph.LIVE -> Canvas(Modifier.size(24.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(tint, radius = size.minDimension * 0.20f, center = c)
            drawCircle(tint, radius = size.minDimension * 0.34f, center = c, style = Stroke(1.6f))
            drawCircle(tint, radius = size.minDimension * 0.46f, center = c, style = Stroke(1.6f))
        }

        Glyph.STYLES -> Canvas(Modifier.size(24.dp)) {
            val r = size.minDimension / 10f
            val step = size.width / 2f
            for (i in 0..2) {
                for (j in 0..2) {
                    drawCircle(KromaColors.Accent, radius = r, center = Offset(i * step, j * step))
                }
            }
        }

        Glyph.NIGHT -> Canvas(Modifier.size(24.dp)) {
            drawCircle(
                tint,
                radius = size.minDimension * 0.45f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
            drawCircle(
                Color(0xFF141416),
                radius = size.minDimension * 0.40f,
                center = Offset(size.width * 0.70f, size.height * 0.34f),
            )
        }

        Glyph.TIMER -> Canvas(Modifier.size(24.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f + size.height * 0.08f)
            drawCircle(tint, radius = size.minDimension * 0.36f, center = c, style = Stroke(2f))
            drawLine(tint, c, Offset(c.x, c.y - size.height * 0.22f), 2f)
            drawLine(tint, c, Offset(c.x + size.width * 0.16f, c.y), 2f)
            drawLine(
                tint,
                Offset(c.x - size.width * 0.12f, c.y - size.height * 0.5f),
                Offset(c.x + size.width * 0.12f, c.y - size.height * 0.5f),
                2f,
            )
        }

        Glyph.EXPOSURE -> Text("+ \u2013", color = tint, fontSize = 18.sp, fontWeight = FontWeight.Medium)

        Glyph.ASPECT -> Canvas(Modifier.size(24.dp)) {
            drawRect(
                color = tint,
                topLeft = Offset(size.width * 0.12f, size.height * 0.24f),
                size = Size(size.width * 0.76f, size.height * 0.52f),
                style = Stroke(2f),
            )
        }

        Glyph.DEPTH -> Text("f", color = tint, fontSize = 20.sp, fontWeight = FontWeight.Light)
        Glyph.FORMAT -> Text(value, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Glyph.PRO -> Text("PRO", color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

