package com.kroma.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Standard camera app photo capture modes. */
object CameraMode {
    const val PORTRAIT = 0
    const val PHOTO = 1
    const val NIGHT = 2
    const val PANO = 3
    const val PRO = 4

    val ALL = listOf(
        "PORTRAIT",
        "PHOTO",
        "NIGHT",
        "PANO",
        "PRO",
    )
}

/**
 * Supported Camera Aspect Ratios:
 * Default 16:9, plus 4:3, 1:1 (Square), and Full (Device edge-to-edge).
 */
enum class CameraAspect(val label: String) {
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    RATIO_1_1("1:1"),
    RATIO_FULL("Full");

    companion object {
        val ALL = listOf(RATIO_16_9, RATIO_4_3, RATIO_1_1, RATIO_FULL)
        fun fromLabel(label: String): CameraAspect = ALL.firstOrNull { it.label == label } ?: RATIO_16_9
    }
}

private enum class Glyph {
    FLASH,
    ASPECT,
    TIMER,
    EXPOSURE,
    STYLES,
    WB,
    QUALITY,
    GRID,
    PRO,
}

private data class Tile(
    val label: String,
    val glyph: Glyph,
    val value: String,
    val isActive: Boolean = false,
)

/**
 * 100% Functional Camera Settings Overlay:
 * Pure 3×3 grid of 9 real camera controls. Slides in cleanly strictly from below without
 * darkening the top viewfinder and padded above system navigation bars so bottom labels never clip.
 */
@Composable
fun CameraSettingsGrid(
    flashValue: String,
    aspectValue: String,
    timerValue: String,
    exposureValue: String,
    wbValue: String,
    qualityValue: String = "JPEG",
    rawEnabled: Boolean = false,
    gridEnabled: Boolean,
    proEnabled: Boolean,
    onFlash: () -> Unit,
    onAspect: () -> Unit,
    onAspectSelect: (CameraAspect) -> Unit = {},
    onTimer: () -> Unit,
    onExposure: () -> Unit,
    onStyles: () -> Unit,
    onWb: () -> Unit,
    onQuality: () -> Unit = {},
    onRaw: () -> Unit = {},
    onGrid: () -> Unit,
    onPro: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tiles = listOf(
        // Row 1: Shooting Basics
        Tile("FLASH", Glyph.FLASH, flashValue, isActive = flashValue == "On"),
        Tile("ASPECT", Glyph.ASPECT, aspectValue, isActive = true),
        Tile("TIMER", Glyph.TIMER, timerValue, isActive = timerValue != "Off"),

        // Row 2: Style, Exposure & Color Science
        Tile("EXPOSURE", Glyph.EXPOSURE, exposureValue, isActive = exposureValue != "0.0" && exposureValue.isNotEmpty()),
        Tile("STYLES", Glyph.STYLES, "Open", isActive = true),
        Tile("WHITE BALANCE", Glyph.WB, wbValue, isActive = wbValue != "Auto"),

        // Row 3: RAW Capture Format & Viewfinder Aids
        Tile("FORMAT", Glyph.QUALITY, if (rawEnabled) "RAW" else "JPEG", isActive = rawEnabled),
        Tile("GRID", Glyph.GRID, if (gridEnabled) "On" else "Off", isActive = gridEnabled),
        Tile("PRO", Glyph.PRO, if (proEnabled) "On" else "Off", isActive = proEnabled),
    )

    Box(Modifier.fillMaxSize()) {
        // Completely transparent tap-outside dismiss area — creates NO extra dark on top of the viewfinder
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )

        val panelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(panelShape)
                .background(Color(0xDD141416))
                .border(1.dp, Color(0x28FFFFFF), panelShape)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Drag handle pill
            Box(
                Modifier
                    .padding(bottom = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0x38FFFFFF)),
            )

            // Aspect Ratio Segmented Selector: [16:9, 4:3, 1:1, Full]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x28FFFFFF))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CameraAspect.ALL.forEach { asp ->
                    val isSelected = asp.label == aspectValue
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (isSelected) Color(0xFFFFD60A) else Color.Transparent)
                            .clickable { onAspectSelect(asp) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = asp.label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }

            // Pure 3×3 Grid — exactly 3 rows of 3 columns, perfectly fitted without scrolling
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tiles.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        row.forEach { tile ->
                            SettingsTile(
                                tile = tile,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when (tile.glyph) {
                                        Glyph.FLASH -> onFlash()
                                        Glyph.ASPECT -> onAspect()
                                        Glyph.TIMER -> onTimer()
                                        Glyph.EXPOSURE -> onExposure()
                                        Glyph.STYLES -> onStyles()
                                        Glyph.WB -> onWb()
                                        Glyph.QUALITY -> onRaw()
                                        Glyph.GRID -> onGrid()
                                        Glyph.PRO -> onPro()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTile(tile: Tile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isStyles = tile.glyph == Glyph.STYLES
    val isActive = tile.isActive
    val accentColor = Color(0xFFFFD60A)

    val showAutoBadge = (tile.glyph == Glyph.FLASH && tile.value == "Auto")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Circular frosted button
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isStyles -> Color(0x38352A12)
                        isActive -> Color(0x33FFD60A)
                        else -> Color(0x661F1F24)
                    },
                )
                .border(
                    width = 1.dp,
                    color = when {
                        isStyles -> Color(0x88FFB300)
                        isActive -> Color(0x88FFD60A)
                        else -> Color(0x28FFFFFF)
                    },
                    shape = CircleShape,
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            TileGlyph(
                glyph = tile.glyph,
                value = tile.value,
                tint = if (isActive && !isStyles) accentColor else Color(0xFFF2F2F7),
            )

            if (showAutoBadge) {
                AutoBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 5.dp, bottom = 5.dp),
                )
            }
        }

        Spacer(Modifier.height(7.dp))

        // Uppercase tracked label
        Text(
            text = tile.label,
            color = if (isActive) Color(0xFFF2F2F7) else Color(0xFFB0B0B5),
            fontSize = 10.5.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun AutoBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(13.5.dp)
            .clip(CircleShape)
            .background(Color(0xE618181A))
            .border(1.dp, Color(0x38FFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "A",
            color = Color(0xFFF2F2F7),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 8.sp,
        )
    }
}

@Composable
private fun TileGlyph(glyph: Glyph, value: String, tint: Color) {
    when (glyph) {
        Glyph.FLASH -> Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(24.dp)) {
                val w = size.width
                val h = size.height
                val p = Path().apply {
                    moveTo(w * 0.54f, h * 0.08f)
                    lineTo(w * 0.22f, h * 0.52f)
                    lineTo(w * 0.48f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.92f)
                    lineTo(w * 0.80f, h * 0.42f)
                    lineTo(w * 0.54f, h * 0.42f)
                    close()
                }
                drawPath(p, tint)
                if (value == "Off") {
                    drawLine(
                        color = Color(0xFFE5E5EA),
                        start = Offset(w * 0.16f, h * 0.84f),
                        end = Offset(w * 0.84f, h * 0.16f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        Glyph.ASPECT -> Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(26.dp, 22.dp)) {
                val strokeW = 1.6.dp.toPx()
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(strokeW / 2f, strokeW / 2f),
                    size = Size(size.width - strokeW, size.height - strokeW),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(strokeW),
                )
            }
            Text(
                text = if (value.isNotEmpty()) value else "4:3",
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
            )
        }

        Glyph.TIMER -> Canvas(Modifier.size(24.dp)) {
            val c = Offset(size.width / 2f, size.height * 0.54f)
            val r = size.minDimension * 0.35f
            val strokeW = 1.8.dp.toPx()
            drawCircle(tint, radius = r, center = c, style = Stroke(strokeW))
            drawLine(tint, Offset(c.x, c.y - r), Offset(c.x, c.y - r - 3.dp.toPx()), strokeW, StrokeCap.Round)
            drawLine(tint, Offset(c.x - 3.dp.toPx(), c.y - r - 3.dp.toPx()), Offset(c.x + 3.dp.toPx(), c.y - r - 3.dp.toPx()), strokeW, StrokeCap.Round)
            drawLine(tint, c, Offset(c.x, c.y - r * 0.65f), strokeW, StrokeCap.Round)
            drawLine(tint, c, Offset(c.x + r * 0.45f, c.y), strokeW, StrokeCap.Round)

            if (value == "Off") {
                drawLine(
                    color = Color(0xFFE5E5EA),
                    start = Offset(size.width * 0.18f, size.height * 0.86f),
                    end = Offset(size.width * 0.82f, size.height * 0.18f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        Glyph.EXPOSURE -> Box(contentAlignment = Alignment.Center) {
            if (value != "0.0" && value.isNotEmpty()) {
                Text(
                    text = value,
                    color = tint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text = "\u00B1",
                    color = tint,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 24.sp,
                )
            }
        }

        // 3×3 golden amber dot grid for Photographic Styles
        Glyph.STYLES -> Canvas(Modifier.size(24.dp)) {
            val margin = size.width * 0.18f
            val step = (size.width - 2 * margin) / 2f
            val dotRadius = 2.2.dp.toPx()
            val glowRadius = 3.6.dp.toPx()
            for (i in 0..2) {
                for (j in 0..2) {
                    val center = Offset(margin + i * step, margin + j * step)
                    drawCircle(Color(0x33FFB300), radius = glowRadius, center = center)
                    drawCircle(Color(0xFFFFD60A), radius = dotRadius, center = center)
                }
            }
        }

        // Real Camera2 Hardware White Balance & Color Temperature
        Glyph.WB -> Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (value) {
                    "5500K" -> Canvas(Modifier.size(18.dp)) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = 3.8.dp.toPx()
                        drawCircle(tint, r, c)
                        for (i in 0..7) {
                            val rad = Math.toRadians((i * 45.0))
                            val x1 = c.x + (r * 1.5f * kotlin.math.cos(rad)).toFloat()
                            val y1 = c.y + (r * 1.5f * kotlin.math.sin(rad)).toFloat()
                            val x2 = c.x + (r * 2.2f * kotlin.math.cos(rad)).toFloat()
                            val y2 = c.y + (r * 2.2f * kotlin.math.sin(rad)).toFloat()
                            drawLine(tint, Offset(x1, y1), Offset(x2, y2), 1.4.dp.toPx(), StrokeCap.Round)
                        }
                    }
                    "6500K" -> Canvas(Modifier.size(20.dp, 15.dp)) {
                        val w = size.width
                        val h = size.height
                        val path = Path().apply {
                            moveTo(w * 0.25f, h * 0.85f)
                            lineTo(w * 0.80f, h * 0.85f)
                            arcTo(Rect(w * 0.60f, h * 0.45f, w * 0.95f, h * 0.85f), 90f, -180f, false)
                            arcTo(Rect(w * 0.35f, h * 0.15f, w * 0.75f, h * 0.65f), 0f, -180f, false)
                            arcTo(Rect(w * 0.05f, h * 0.40f, w * 0.45f, h * 0.85f), -90f, -180f, false)
                            close()
                        }
                        drawPath(path, tint, style = Stroke(1.4.dp.toPx()))
                    }
                    "3200K" -> Canvas(Modifier.size(16.dp, 19.dp)) {
                        val w = size.width
                        val h = size.height
                        val c = Offset(w / 2f, h * 0.36f)
                        val r = 4.8.dp.toPx()
                        drawCircle(tint, r, c, style = Stroke(1.4.dp.toPx()))
                        drawLine(tint, Offset(w * 0.32f, h * 0.72f), Offset(w * 0.68f, h * 0.72f), 1.4.dp.toPx(), StrokeCap.Round)
                        drawLine(tint, Offset(w * 0.38f, h * 0.86f), Offset(w * 0.62f, h * 0.86f), 1.4.dp.toPx(), StrokeCap.Round)
                    }
                    "4000K" -> Canvas(Modifier.size(20.dp, 14.dp)) {
                        val w = size.width
                        val h = size.height
                        drawRoundRect(tint, Offset(0f, h * 0.35f), Size(w, h * 0.30f), CornerRadius(2.dp.toPx()), Stroke(1.4.dp.toPx()))
                        drawLine(tint, Offset(w * 0.25f, 0f), Offset(w * 0.25f, h * 0.20f), 1.2.dp.toPx(), StrokeCap.Round)
                        drawLine(tint, Offset(w * 0.75f, 0f), Offset(w * 0.75f, h * 0.20f), 1.2.dp.toPx(), StrokeCap.Round)
                    }
                    "7500K" -> Canvas(Modifier.size(18.dp)) {
                        val w = size.width
                        val h = size.height
                        val path = Path().apply {
                            arcTo(Rect(0f, 0f, w, h * 0.75f), 180f, 180f, true)
                            lineTo(w / 2f, h * 0.38f)
                            close()
                        }
                        drawPath(path, tint, style = Stroke(1.4.dp.toPx()))
                        drawLine(tint, Offset(w / 2f, h * 0.38f), Offset(w / 2f, h * 0.90f), 1.4.dp.toPx(), StrokeCap.Round)
                    }
                    else -> {
                        Text("AWB", color = tint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (value.isNotEmpty()) value else "Auto",
                    color = tint,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 10.sp,
                )
            }
        }

        // Capture Format (RAW / JPEG)
        Glyph.QUALITY -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value,
                color = tint,
                fontSize = if (value == "RAW") 13.5.sp else 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }

        // Live 3×3 Rule-of-Thirds Grid Toggle
        Glyph.GRID -> Canvas(Modifier.size(24.dp)) {
            val strokeW = 1.4.dp.toPx()
            val w = size.width
            val h = size.height
            drawRoundRect(tint, Offset.Zero, Size(w, h), CornerRadius(3.dp.toPx()), Stroke(strokeW))
            drawLine(tint, Offset(w * 0.33f, 0f), Offset(w * 0.33f, h), strokeW)
            drawLine(tint, Offset(w * 0.67f, 0f), Offset(w * 0.67f, h), strokeW)
            drawLine(tint, Offset(0f, h * 0.33f), Offset(w, h * 0.33f), strokeW)
            drawLine(tint, Offset(0f, h * 0.67f), Offset(w, h * 0.67f), strokeW)
        }

        // Pro Controls Readout Strip Toggle
        Glyph.PRO -> Text(
            text = "PRO",
            color = tint,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}
