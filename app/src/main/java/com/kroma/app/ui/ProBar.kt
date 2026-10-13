package com.kroma.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * iOS-style Unified Pro Control Pill:
 * Single continuous frosted glass pill containing:
 * - SEC (Shutter Speed)
 * - EV (Exposure Compensation)
 * - FOCUS (AF / MF)
 * - RAW (RAW format toggle)
 */
@Composable
fun ProControlBar(
    shutterSpeed: String = "AUTO",
    onCycleShutterSpeed: () -> Unit = {},
    ev: Float = 0f,
    onCycleEv: () -> Unit = {},
    focusMode: String = "AF",
    onCycleFocus: () -> Unit = {},
    rawEnabled: Boolean = false,
    onToggleRaw: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xD918181A))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // 1. Shutter Speed (SEC)
            ProPillSegment(
                label = "SEC",
                value = shutterSpeed,
                active = shutterSpeed != "AUTO",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCycleShutterSpeed()
                },
            )

            ProPillDivider()

            // 2. Exposure (EV)
            val evStr = if (ev > 0) "+${String.format(java.util.Locale.US, "%.1f", ev)}"
                        else String.format(java.util.Locale.US, "%.1f", ev)
            ProPillSegment(
                label = "EV",
                value = evStr,
                active = ev != 0f,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCycleEv()
                },
            )

            ProPillDivider()

            // 3. Focus (FOCUS)
            ProPillSegment(
                label = "FOCUS",
                value = focusMode,
                active = focusMode != "AF",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCycleFocus()
                },
            )

            ProPillDivider()

            // 4. RAW Format Toggle
            ProPillSegment(
                label = "FORMAT",
                value = "RAW",
                active = rawEnabled,
                isRaw = true,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleRaw()
                },
            )
        }
    }
}

@Composable
private fun ProPillSegment(
    label: String,
    value: String,
    active: Boolean = false,
    isRaw: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (active) {
        if (isRaw) Color(0x33FFD60A) else Color(0x2BFFD60A)
    } else {
        Color.Transparent
    }
    val borderCol = if (active) Color(0x80FFD60A) else Color.Transparent

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = if (active) Color(0xCCFFD60A) else Color(0x75FFFFFF),
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = value,
            color = if (active) Color(0xFFFFD60A) else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun ProPillDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(Color(0x26FFFFFF)),
    )
}

/** `AE/AF LOCK` — yellow-bordered pill shown once focus/exposure is locked. */
@Composable
fun AeAfLockPill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x80000000))
            .border(1.5.dp, Color(0xFFFFD60A), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            "AE/AF LOCK",
            color = Color(0xFFFFD60A),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
    }
}

/**
 * Interactive Focus Reticle:
 * Compact 72.dp yellow targeting square.
 * Dragging up/down anywhere on the reticle turns the exposure sun UP or DOWN in real time,
 * providing the authentic iOS tactile exposure compensation scrubber.
 */
@Composable
fun FocusReticle(
    exposureEv: Float = 0f,
    onExposureChange: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val accent = Color(0xFFFFD60A)
    var currentEv by remember(exposureEv) { mutableFloatStateOf(exposureEv) }
    val currentOnExposureChange = androidx.compose.runtime.rememberUpdatedState(onExposureChange)

    Box(
        modifier = modifier
            .size(width = 104.dp, height = 72.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Drag up (negative y) -> increase EV; drag down (positive y) -> decrease EV
                    val sensitivity = 0.04f
                    val newEv = (currentEv - dragAmount.y * sensitivity).coerceIn(-4.0f, 4.0f)
                    val prevStep = (currentEv * 2f).roundToInt()
                    val newStep = (newEv * 2f).roundToInt()
                    if (newStep != prevStep) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    currentEv = newEv
                    currentOnExposureChange.value?.invoke(newEv)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val h = size.height.toFloat()
                    val sliderTrack = 24.dp.toPx()
                    val relY = (h / 2f) - pos.y
                    val fraction = (relY / sliderTrack).coerceIn(-1f, 1f)
                    val newEv = fraction * 4.0f
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentEv = newEv
                    currentOnExposureChange.value?.invoke(newEv)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 72.dp.toPx()
            val h = 72.dp.toPx()
            val arm = w * 0.15f
            val stroke = 1.6.dp.toPx()

            // Reticle square
            drawRect(
                color = accent,
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(w, h),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
            )
            // Center edge ticks
            drawLine(accent, Offset(w / 2f, 0f), Offset(w / 2f, arm), stroke)
            drawLine(accent, Offset(w / 2f, h), Offset(w / 2f, h - arm), stroke)
            drawLine(accent, Offset(0f, h / 2f), Offset(arm, h / 2f), stroke)
            drawLine(accent, Offset(w, h / 2f), Offset(w - arm, h / 2f), stroke)

            // Vertical track for exposure sun
            val sx = w + 16.dp.toPx()
            val sliderTrack = 24.dp.toPx()
            drawLine(
                color = accent.copy(alpha = 0.45f),
                start = Offset(sx, h / 2f - sliderTrack),
                end = Offset(sx, h / 2f + sliderTrack),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Sun position along track (-4f..+4f maps to bottom..top)
            val sunY = (h / 2f) - (currentEv / 4.0f) * sliderTrack

            // Sun central disc
            drawCircle(accent, radius = 3.6.dp.toPx(), center = Offset(sx, sunY))

            // Radiating rays around the sun
            val rayCount = 8
            for (i in 0 until rayCount) {
                val rad = Math.toRadians((i * (360.0 / rayCount)))
                val cos = Math.cos(rad).toFloat()
                val sin = Math.sin(rad).toFloat()
                val innerR = 5.2.dp.toPx()
                val outerR = 7.8.dp.toPx()
                drawLine(
                    color = accent,
                    start = Offset(sx + cos * innerR, sunY + sin * innerR),
                    end = Offset(sx + cos * outerR, sunY + sin * outerR),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

