package com.kroma.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Rule-of-thirds grid drawn over the viewfinder (ref 02). */
@Composable
fun ViewfinderGrid(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val c = Color(0x40FFFFFF)
        val w = size.width
        val h = size.height
        drawLine(c, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.5f)
        drawLine(c, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = 1.5f)
        drawLine(c, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.5f)
        drawLine(c, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = 1.5f)
    }
}

private fun zoomLabel(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString()
    else String.format(java.util.Locale.US, "%.1f", v).removePrefix("0")

/** Zoom picker — stops come from the device's own zoom range. Active stop sits on a dark pill. */
@Composable
fun LensPicker(
    stops: List<Float>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stops.forEachIndexed { i, zoom ->
            val active = i == selected
            val label = zoomLabel(zoom)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) Color(0x8A2A2A2A) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 15.dp, vertical = 6.dp),
            ) {
                Text(
                    if (active) label + "x" else label,
                    color = if (active) KromaColors.Accent else Color(0xFFE6E6E6),
                    fontSize = if (active) 16.sp else 14.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

/** The `ƒ` aperture button — Apple draws it as an italic serif glyph. */
@Composable
fun ApertureButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0x59000000))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "f",
            color = Color.White,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 22.sp,
        )
    }
}

/** Yellow ring + 3×3 dots — the Photographic Styles entry point (refs 02 / 03). */
@Composable
fun StyleDotsButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (active) Color(0x33FFD84D) else Color.Transparent)
            .border(2.dp, KromaColors.Accent, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val r = size.minDimension / 10f
            val step = size.width / 2f
            for (i in 0..2) {
                for (j in 0..2) {
                    drawCircle(Color.White, radius = r, center = Offset(i * step, j * step))
                }
            }
        }
    }
}

/** Big white shutter: a thin translucent outer ring around a solid white disc (refs 02 / 03). */
@Composable
fun ShutterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(78.dp)
            .clip(CircleShape)
            .border(2.dp, Color(0x66FFFFFF), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(66.dp).clip(CircleShape).background(Color.White))
    }
}

/** Circular gallery thumbnail showing the most recent capture (falls back to a swatch). */
@Composable
fun ThumbnailButton(
    bitmap: Bitmap?,
    swatch: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(swatch)
            .border(2.dp, Color(0xCCFFFFFF), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Last photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val MODES = listOf("VIDEO", "PHOTO", "PORTRAIT")

/** `VIDEO | PHOTO | PORTRAIT` — the active mode gets its own pill + accent text. */
@Composable
fun ModeSelector(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x59000000))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MODES.forEachIndexed { i, mode ->
            val active = i == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) Color(0x8A3A3A3A) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(
                    mode,
                    color = if (active) KromaColors.Accent else Color(0xFFB0B0B0),
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    letterSpacing = 0.8.sp,
                )
            }
        }
    }
}

/** Front/rear flip — two curved arrows (no chip behind it, as Apple draws it). */
@Composable
fun FlipButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Autorenew,
            contentDescription = "Flip camera",
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

