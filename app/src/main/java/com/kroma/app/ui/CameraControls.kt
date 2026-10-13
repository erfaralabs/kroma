package com.kroma.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.camera.video.Quality
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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

/**
 * Zoom control matching the reference:
 *  * idle  — a dark pill holding three chips (`0.6 | 1× | 2`); the active one has a white ring
 *  * dragging horizontally — expands into a full-width tick ruler with mm labels, a white progress
 *    line and a floating `Nx` badge. The gesture is owned by the outer box, so the hand-off from
 *    pill → ruler is seamless.
 */
@Composable
fun LensPicker(
    stops: List<Float>,
    selected: Int,
    liveZoom: Float,
    onSelect: (Int) -> Unit,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scrubMax = stops.lastOrNull() ?: 1f
    val currentZoom = rememberUpdatedState(liveZoom)
    val currentOnZoom = rememberUpdatedState(onZoom)
    // Pill is `1 | 2 | <third>`. The third slot shows the zoom you're ON; it only falls back
    // to the standard `3` when you're sitting on one of the first two stops.
    val base = stops.take(3)
    val onFirstTwo = base.size >= 2 &&
        (kotlin.math.abs(liveZoom - base[0]) < 0.05f || kotlin.math.abs(liveZoom - base[1]) < 0.05f)
    val chips = if (base.size == 3) {
        listOf(base[0], base[1], if (onFirstTwo) base[2] else liveZoom)
    } else {
        base
    }

    var scrubbing by remember { mutableStateOf(false) }
    var lastEvent by remember { mutableLongStateOf(0L) }

    // Safety net: collapse the ruler shortly after the last interaction.
    LaunchedEffect(scrubbing, lastEvent) {
        if (scrubbing) {
            delay(3500)
            scrubbing = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (scrubbing) 74.dp else 52.dp)
            .pointerInput(stops) {
                // Tap to OPEN the ruler overlay (the "grid" UI).
                detectTapGestures {
                    scrubbing = !scrubbing
                    lastEvent = System.currentTimeMillis()
                }
            }
            .pointerInput(stops) {
                val w = size.width.toFloat().coerceAtLeast(1f)
                var lastTick = -1
                var anchor = 1f
                var acc = 0f

                fun report(z: Float) {
                    val clamped = z.coerceIn(1f, scrubMax)
                    currentOnZoom.value(clamped)
                    val whole = clamped.roundToInt()
                    if (whole != lastTick) {
                        lastTick = whole
                        val major = whole == 1 || whole == 2 || whole == 5 || whole == 10
                        haptic.performHapticFeedback(
                            if (major) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
                        )
                    }
                }

                detectHorizontalDragGestures(
                    onDragStart = {
                        // RELATIVE scrub: grabs from the CURRENT zoom, so it never jumps.
                        scrubbing = true
                        anchor = currentZoom.value.coerceIn(1f, scrubMax)
                        acc = 0f
                        lastEvent = System.currentTimeMillis()
                    },
                    onDragEnd = { scrubbing = false },
                    onDragCancel = { scrubbing = false },
                ) { change, dragAmount ->
                    change.consume()
                    lastEvent = System.currentTimeMillis()
                    acc += dragAmount
                    // Full width of travel = the device's whole zoom range.
                    report(anchor + (acc / w) * (scrubMax - 1f))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (scrubbing) {
            ZoomRuler(stops = stops, liveZoom = liveZoom, minZoom = 1f, maxZoom = scrubMax)
        } else {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x73000000))
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chips.forEach { zoom ->
                    val i = stops.indexOfFirst { kotlin.math.abs(it - zoom) < 0.001f }
                    val active = kotlin.math.abs(zoom - liveZoom) < 0.05f
                    val num = if (kotlin.math.abs(zoom - zoom.toInt()) < 0.05f) {
                        zoom.toInt().toString()
                    } else {
                        String.format(java.util.Locale.US, "%.1f", zoom)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .then(
                                if (active) {
                                    Modifier.border(1.5.dp, Color.White, RoundedCornerShape(50))
                                } else {
                                    Modifier
                                },
                            )
                            .clickable(enabled = i >= 0) { if (i >= 0) onSelect(i) }
                            .padding(horizontal = 20.dp, vertical = 9.dp),
                    ) {
                        Text(
                            if (active) num + "x" else num,
                            color = if (active) Color.White else Color(0x99FFFFFF),
                            fontSize = 15.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

/** Expanded ruler shown while scrubbing: ticks, mm labels, progress line and a floating badge. */
@Composable
private fun ZoomRuler(stops: List<Float>, liveZoom: Float, minZoom: Float, maxZoom: Float) {
    val raw = if (maxZoom > minZoom) (liveZoom - minZoom) / (maxZoom - minZoom) else 0f
    // Glide so scrubbing feels smooth rather than steppy.
    val progress by animateFloatAsState(
        targetValue = raw.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "zoomProgress",
    )

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val trackWidth = maxWidth

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .align(Alignment.BottomCenter),
        ) {
            val y = size.height
            drawLine(Color(0x55FFFFFF), Offset(0f, y), Offset(size.width, y), 2f)
            drawLine(Color.White, Offset(0f, y), Offset(size.width * progress, y), 3f)
            // Ticks placed by each stop's real position within the device's zoom range.
            stops.forEach { s ->
                val x = if (maxZoom > minZoom) size.width * (s - minZoom) / (maxZoom - minZoom) else 0f
                drawLine(
                    if (s <= liveZoom + 0.01f) Color.White else Color(0x55FFFFFF),
                    Offset(x, y - 15f),
                    Offset(x, y),
                    2f,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            stops.forEach { zoom ->
                val active = kotlin.math.abs(zoom - liveZoom) < 0.05f
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        zoomLabel(zoom),
                        color = if (active) Color.White else Color(0x99FFFFFF),
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text("${(24f * zoom).roundToInt()}mm", color = Color(0x66FFFFFF), fontSize = 9.sp)
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (trackWidth * progress - 24.dp).coerceIn(0.dp, (trackWidth - 48.dp).coerceAtLeast(0.dp)))
                .height(28.dp)
                .widthIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                String.format(java.util.Locale.US, "%.1fx", liveZoom),
                color = Color(0xFF111111),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
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

/** Top bar shown in VIDEO mode: REC state + real recording-quality selection. */
@Composable
fun VideoTopBar(
    quality: Quality,
    recording: Boolean,
    onQuality: (Quality) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (recording) Color(0xFFFF3B30) else Color(0x59FFFFFF)),
            )
            Text(
                if (recording) "REC" else "STANDBY",
                color = if (recording) Color(0xFFFF3B30) else KromaColors.TextSecondary,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Quality.UHD, Quality.FHD, Quality.HD).forEach { q ->
                val label = when (q) {
                    Quality.UHD -> "4K"
                    Quality.FHD -> "1080p"
                    else -> "720p"
                }
                val active = q == quality
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Color(0x8A3A3A3A) else Color(0x33FFFFFF))
                        .clickable { onQuality(q) }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        label,
                        color = if (active) KromaColors.Accent else Color(0xFFB0B0B0),
                        fontSize = 12.sp,
                    )
                }
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

