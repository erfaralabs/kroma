package com.kroma.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ProItem(
    val main: String,
    val sub: String? = null,
    val icon: ImageVector? = null,
    val accent: Boolean = false,
)

private val PRO_ITEMS = listOf(
    ProItem("1/2", icon = Icons.Filled.Timer),
    ProItem("2.8"),
    ProItem("-1.0", sub = "EV", icon = Icons.Filled.Exposure, accent = true),
    ProItem("AF", icon = Icons.Filled.CenterFocusStrong),
    ProItem("RAW", sub = "48"),
)

/** Top Pro control strip — `1/2 · 2.8 · -1.0 EV · AF · RAW 48` (ref 03). */
@Composable
fun ProControlBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        PRO_ITEMS.forEach { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                item.icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = Color(0xFFB8B8B8),
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    item.main,
                    color = if (item.accent) KromaColors.Accent else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                item.sub?.let {
                    Text(it, color = Color(0xFFB8B8B8), fontSize = 10.sp)
                }
            }
        }
    }
}

/** `AE/AF LOCK` — yellow-bordered pill shown once focus/exposure is locked (ref 03). */
@Composable
fun AeAfLockPill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.5.dp, KromaColors.Accent, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            "AE/AF LOCK",
            color = KromaColors.Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
        )
    }
}

/**
 * The focus reticle: a yellow square with edge ticks and a small exposure "sun" slider
 * hanging off its right edge (ref 03).
 */
@Composable
fun FocusReticle(modifier: Modifier = Modifier) {
    val accent = KromaColors.Accent
    Box(modifier.size(150.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val arm = w * 0.18f
            val stroke = 2.5f

            // square
            drawRect(
                color = accent,
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(w, h),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
            )
            // corner ticks
            drawLine(accent, Offset(w / 2f, 0f), Offset(w / 2f, arm), stroke)
            drawLine(accent, Offset(w / 2f, h), Offset(w / 2f, h - arm), stroke)
            drawLine(accent, Offset(0f, h / 2f), Offset(arm, h / 2f), stroke)
            drawLine(accent, Offset(w, h / 2f), Offset(w - arm, h / 2f), stroke)

            // exposure slider on the right edge
            val sx = w + 26f
            drawLine(accent, Offset(sx, h / 2f - 34f), Offset(sx, h / 2f + 34f), strokeWidth = 2f)
            drawCircle(accent, radius = 9f, center = Offset(sx, h / 2f))
        }
    }
}
