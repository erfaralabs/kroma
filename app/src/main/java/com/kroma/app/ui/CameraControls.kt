package com.kroma.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Rule-of-thirds grid drawn over the viewfinder (ref 02). */
@Composable
fun ViewfinderGrid(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val c = Color(0x40FFFFFF)
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        drawLine(c, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = stroke)
        drawLine(c, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = stroke)
        drawLine(c, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = stroke)
        drawLine(c, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = stroke)
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
    val scrubMin = stops.firstOrNull() ?: 1f
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
    var isDragging by remember { mutableStateOf(false) }
    var lastEvent by remember { mutableLongStateOf(0L) }

    // Safety net: collapse the ruler shortly after the last interaction.
    LaunchedEffect(scrubbing, isDragging, lastEvent) {
        if (scrubbing && !isDragging) {
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
                var anchor = scrubMin
                var acc = 0f

                fun report(z: Float) {
                    val clamped = z.coerceIn(scrubMin, scrubMax)
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
                        isDragging = true
                        anchor = currentZoom.value.coerceIn(scrubMin, scrubMax)
                        acc = 0f
                        lastEvent = System.currentTimeMillis()
                    },
                    onDragEnd = {
                        isDragging = false
                        lastEvent = System.currentTimeMillis()
                    },
                    onDragCancel = {
                        isDragging = false
                        lastEvent = System.currentTimeMillis()
                    },
                ) { change, dragAmount ->
                    change.consume()
                    lastEvent = System.currentTimeMillis()
                    acc += dragAmount
                    // Full width of travel = the device's whole zoom range.
                    val zoomRange = (scrubMax - scrubMin).coerceAtLeast(0.1f)
                    report(anchor + (acc / w) * zoomRange)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (scrubbing) {
            ZoomRuler(stops = stops, liveZoom = liveZoom, minZoom = scrubMin, maxZoom = scrubMax)
        } else {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x59000000))
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chips.forEach { zoom ->
                    val i = stops.indexOfFirst { kotlin.math.abs(it - zoom) < 0.001f }
                    val active = kotlin.math.abs(zoom - liveZoom) < 0.05f
                    val num = when {
                        zoom < 1f -> {
                            val formatted = String.format(java.util.Locale.US, "%.1f", zoom)
                            if (formatted.startsWith("0.")) formatted.removePrefix("0") else formatted
                        }
                        kotlin.math.abs(zoom - zoom.toInt()) < 0.05f -> zoom.toInt().toString()
                        else -> String.format(java.util.Locale.US, "%.1f", zoom)
                    }
                    val label = if (active && zoom == 1f) "1x" else if (active) "${num}x" else num

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (active) Color(0x99202024) else Color.Transparent)
                            .clickable {
                                if (i >= 0) {
                                    onSelect(i)
                                } else {
                                    scrubbing = true
                                    lastEvent = System.currentTimeMillis()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (active) Color(0xFFFFD60A) else Color(0xCCFFFFFF),
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
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
            val lineWidth = 2.dp.toPx()
            val activeLineWidth = 3.dp.toPx()
            val tickHeight = 15.dp.toPx()
            drawLine(Color(0x55FFFFFF), Offset(0f, y), Offset(size.width, y), strokeWidth = lineWidth)
            drawLine(Color.White, Offset(0f, y), Offset(size.width * progress, y), strokeWidth = activeLineWidth)
            // Ticks placed by each stop's real position within the device's zoom range.
            stops.forEach { s ->
                val x = if (maxZoom > minZoom) size.width * (s - minZoom) / (maxZoom - minZoom) else 0f
                drawLine(
                    if (s <= liveZoom + 0.01f) Color.White else Color(0x55FFFFFF),
                    Offset(x, y - tickHeight),
                    Offset(x, y),
                    strokeWidth = lineWidth,
                )
            }
        }

        val labelWidth = 48.dp
        stops.forEach { zoom ->
            val fraction = if (maxZoom > minZoom) (zoom - minZoom) / (maxZoom - minZoom) else 0f
            val active = kotlin.math.abs(zoom - liveZoom) < 0.05f
            val xOffset = (trackWidth * fraction - labelWidth / 2)
                .coerceIn(0.dp, (trackWidth - labelWidth).coerceAtLeast(0.dp))

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = xOffset)
                    .width(labelWidth)
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    zoomLabel(zoom),
                    color = if (active) Color.White else Color(0x99FFFFFF),
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "${(24f * zoom).roundToInt()}mm",
                    color = Color(0x66FFFFFF),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                )
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

/** The `ƒ` aperture button — frosted circle with white/yellow italic serif glyph. */
@Composable
fun ApertureButton(
    onClick: () -> Unit,
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (active) Color(0x33FFD60A) else Color(0x66000000))
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) Color(0xFFFFD60A) else Color(0x33FFFFFF),
                shape = CircleShape,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "f",
            color = if (active) Color(0xFFFFD60A) else Color.White,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * iOS-style Portrait Aperture (Depth of Field) slider overlay.
 * Slides in above the shutter bar when `f` is clicked in Portrait mode.
 */
@Composable
fun PortraitApertureOverlay(
    aperture: Float,
    onApertureChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val stops = remember { listOf(1.4f, 1.8f, 2.0f, 2.4f, 2.8f, 3.2f, 4.0f, 5.6f, 8.0f, 11.0f, 16.0f) }
    val majorStops = remember { listOf(1.4f, 2.0f, 2.8f, 4.0f, 5.6f, 8.0f, 16.0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xD9141416))
            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "APERTURE",
                color = Color(0x99FFFFFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "f",
                    color = Color(0xFFFFD60A),
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    String.format(java.util.Locale.US, "%.1f", aperture),
                    color = Color(0xFFFFD60A),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "Done",
                color = Color(0xFFFFD60A),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClose()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(4.dp))

        // Aperture Slider
        Slider(
            value = aperture,
            onValueChange = { newVal ->
                val nearest = stops.minByOrNull { kotlin.math.abs(it - newVal) } ?: newVal
                val snapped = if (kotlin.math.abs(nearest - newVal) < 0.15f) nearest else ((newVal * 10).toInt() / 10f)
                if (snapped != aperture) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onApertureChange(snapped)
                }
            },
            valueRange = 1.4f..16.0f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFD60A),
                activeTrackColor = Color(0xFFFFD60A),
                inactiveTrackColor = Color(0x33FFFFFF),
            ),
            modifier = Modifier.fillMaxWidth().height(36.dp),
        )

        // Major stops selector chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            majorStops.forEach { stop ->
                val selected = kotlin.math.abs(stop - aperture) < 0.15f
                Text(
                    text = if (stop == 16f) "16" else String.format(java.util.Locale.US, "%.1f", stop),
                    color = if (selected) Color(0xFFFFD60A) else Color(0x80FFFFFF),
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onApertureChange(stop)
                    },
                )
            }
        }
    }
}

/** Yellow ring + 6 dots (2 rows of 3) — Quick controls / styles entry point (matches iOS screenshot). */
@Composable
fun StyleDotsButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val borderColor = if (active) Color(0xFFFFD60A) else Color(0x33FFFFFF)
    val dotColor = if (active) Color(0xFFFFD60A) else Color.White

    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .border(if (active) 2.dp else 1.dp, borderColor, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp, 10.dp)) {
            val r = 1.6.dp.toPx()
            val cols = 3
            val rows = 2
            val stepX = size.width / (cols - 1)
            val stepY = size.height / (rows - 1)
            for (i in 0 until cols) {
                for (j in 0 until rows) {
                    drawCircle(dotColor, radius = r, center = Offset(i * stepX, j * stepY))
                }
            }
        }
    }
}

/** Shutter button: white circle for photo, red circle for video, rounded red square during recording. */
@Composable
fun ShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    recording: Boolean = false,
) {
    val innerSize by animateDpAsState(
        targetValue = if (recording) 32.dp else 66.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "shutterInnerSize",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (recording) 8.dp else 33.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "shutterCornerRadius",
    )
    val innerColor = if (recording) Color(0xFFFF3B30) else Color.White

    Box(
        modifier = modifier
            .size(78.dp)
            .clip(CircleShape)
            .border(2.dp, Color(0x66FFFFFF), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(cornerRadius))
                .background(innerColor),
        )
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
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(swatch)
            .border(2.dp, Color.White, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Last photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Pill Selector for Camera Modes:
 * Shows exactly 3 modes in one frosted glass pill container at a time.
 * Supports fluid horizontal scrolling/swiping across all 8 modes, snapping, and direct tapping.
 */
@Composable
fun ModeSelector(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val initialIdx = remember { selected.coerceIn(0, CameraMode.ALL.size - 1) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIdx)
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var userSwiping by remember { mutableStateOf(false) }
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userSwiping = true
        }
    }

    // Auto-center the selected item in the viewport when selected changes externally
    LaunchedEffect(selected) {
        if (!userSwiping) {
            listState.animateScrollToItem(selected)
        }
    }

    // Automatically detect which mode is centered ONLY when the user physically scrolls the pill carousel
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) null
            else {
                val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visible.minByOrNull { item ->
                    kotlin.math.abs((item.offset + item.size / 2) - center)
                }?.index
            }
        }.collect { centerIdx ->
            if (centerIdx != null && centerIdx != currentSelected && userSwiping) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentOnSelect(centerIdx)
            }
            if (!listState.isScrollInProgress) {
                userSwiping = false
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0x80141416))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        val slotWidth = maxWidth / 3f

        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = slotWidth),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(CameraMode.ALL) { index, modeName ->
                val active = index == selected
                val isPano = modeName == "PANO" || modeName == "PANORAMA"

                Box(
                    modifier = Modifier
                        .width(slotWidth)
                        .height(44.dp)
                        .clickable {
                            if (index != currentSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) {
                        val pillBg = if (isPano) Color(0xFF68C5E8) else Color(0x662C2C30)
                        val textCol = if (isPano) Color(0xFF0F2634) else Color(0xFFE5F94B)
                        val borderCol = if (isPano) Color.Transparent else Color(0x38FFFFFF)

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(pillBg)
                                .border(1.dp, borderCol, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = modeName,
                                color = textCol,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Text(
                            text = modeName,
                            color = Color(0xB3FFFFFF),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** Top bar for PORTRAIT mode (Aperture / Depth control). */
@Composable
fun PortraitTopBar(
    aperture: Float,
    onAperture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x33FFFFFF))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                "PORTRAIT",
                color = Color(0xFFFFD60A),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }

        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x33FFFFFF))
                .clickable { onAperture() }
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Text(
                "f/${String.format(java.util.Locale.US, "%.1f", aperture)}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Top bar for NIGHT mode. */
@Composable
fun NightTopBar(
    seconds: Int,
    onToggleSeconds: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x33FFFFFF))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                "NIGHT SIGHT",
                color = Color(0xFFFFD60A),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }

        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x33FFFFFF))
                .clickable { onToggleSeconds() }
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Text(
                "${seconds}s Max",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Top bar for PANORAMA mode. */
@Composable
fun PanoTopBar(capturing: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x66000000))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                if (capturing) "Hold steady and pan slowly" else "PANORAMA • Tap arrow to change direction",
                color = Color.White,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

/** Front/rear flip button with frosted circular background (matches iOS screenshot). */
@Composable
fun FlipButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .border(1.dp, Color(0x33FFFFFF), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Autorenew,
            contentDescription = "Flip camera",
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

