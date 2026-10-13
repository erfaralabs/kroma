package com.kroma.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

enum class PanoDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
}

/**
 * Panorama Viewfinder Guide Overlay:
 * Matches the Google Camera / standard panoramic guide strip UI:
 * - Full-width horizontal strip bounded by top & bottom white lines
 * - Centered framing box with 3x3 rule-of-thirds grid
 * - Left arrow (←) and Right arrow (→) to select pan direction
 * - Real-time tracking of phone horizontal rotation (azimuth) & tilt (pitch)
 * - Level guide line and sliding capture window during panorama sweep
 */
@Composable
fun PanoramaGuideOverlay(
    isCapturing: Boolean,
    direction: PanoDirection,
    onDirectionChange: (PanoDirection) -> Unit,
    onCaptureComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var azimuthDeg by remember { mutableFloatStateOf(0f) }
    var pitchDeg by remember { mutableFloatStateOf(0f) }
    var initialAzimuth by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Track rotation vector for azimuth (compass yaw) and pitch (tilt)
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

        val rotMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                    SensorManager.getOrientation(rotMatrix, orientation)
                    azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    // Reset or advance progress when capturing
    LaunchedEffect(isCapturing) {
        if (isCapturing) {
            initialAzimuth = azimuthDeg
            progress = 0f
        } else {
            progress = 0f
        }
    }

    // Update progress based on azimuth delta (swept angle over ~75 degrees)
    LaunchedEffect(azimuthDeg, isCapturing) {
        if (isCapturing) {
            var delta = azimuthDeg - initialAzimuth
            // Normalize angular difference across [-180, 180]
            while (delta < -180f) delta += 360f
            while (delta > 180f) delta -= 360f

            val swept = if (direction == PanoDirection.LEFT_TO_RIGHT) {
                // Turning clockwise/right increases delta or decreases depending on coordinate system
                abs(delta)
            } else {
                abs(delta)
            }

            val targetDegrees = 75f
            val currentProgress = (swept / targetDegrees).coerceIn(0f, 1f)
            progress = currentProgress
            if (currentProgress >= 1f) {
                onCaptureComplete()
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Strip height: 210.dp
        val stripHeight = 210.dp
        val frameWidth = 160.dp

        // 1. Horizontal Guide Lines & Viewfinder Box
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 0.dp),
        ) {
            val w = size.width
            val h = size.height
            val cy = h / 2f
            val halfH = stripHeight.toPx() / 2f
            val topY = cy - halfH
            val botY = cy + halfH

            val borderLineColor = Color(0xD9FFFFFF)
            val gridLineColor = Color(0x4DFFFFFF)
            val strokeW = 1.4.dp.toPx()

            // Top boundary line spanning screen width
            drawLine(
                color = borderLineColor,
                start = Offset(0f, topY),
                end = Offset(w, topY),
                strokeWidth = strokeW,
            )

            // Bottom boundary line spanning screen width
            drawLine(
                color = borderLineColor,
                start = Offset(0f, botY),
                end = Offset(w, botY),
                strokeWidth = strokeW,
            )

            // Center framing window
            val cx = w / 2f
            val halfFrameW = frameWidth.toPx() / 2f
            val frameLeft = cx - halfFrameW
            val frameRight = cx + halfFrameW

            // Left and right vertical borders of the center frame
            drawLine(
                color = borderLineColor,
                start = Offset(frameLeft, topY),
                end = Offset(frameLeft, botY),
                strokeWidth = strokeW,
            )
            drawLine(
                color = borderLineColor,
                start = Offset(frameRight, topY),
                end = Offset(frameRight, botY),
                strokeWidth = strokeW,
            )

            // 3x3 grid inside the center frame
            val colStep = (frameRight - frameLeft) / 3f
            val rowStep = (botY - topY) / 3f

            // Vertical grid lines
            drawLine(
                color = gridLineColor,
                start = Offset(frameLeft + colStep, topY),
                end = Offset(frameLeft + colStep, botY),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = gridLineColor,
                start = Offset(frameLeft + colStep * 2f, topY),
                end = Offset(frameLeft + colStep * 2f, botY),
                strokeWidth = 1.dp.toPx(),
            )

            // Horizontal grid lines
            drawLine(
                color = gridLineColor,
                start = Offset(frameLeft, topY + rowStep),
                end = Offset(frameRight, topY + rowStep),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = gridLineColor,
                start = Offset(frameLeft, topY + rowStep * 2f),
                end = Offset(frameRight, topY + rowStep * 2f),
                strokeWidth = 1.dp.toPx(),
            )

            // If capturing, draw moving sweep indicator & level guide
            if (isCapturing) {
                // Moving sweep window offset
                val maxOffset = halfFrameW * 1.5f
                val sweepOffset = if (direction == PanoDirection.LEFT_TO_RIGHT) {
                    (progress * maxOffset)
                } else {
                    -(progress * maxOffset)
                }

                // Level alignment indicator line (yellow when aligned within 2.5 deg)
                val isLevel = abs(pitchDeg) < 3.0f
                val levelColor = if (isLevel) Color(0xFFFFD60A) else Color(0xCCFF453A)
                val tiltOffset = (pitchDeg * 2.5f).coerceIn(-halfH + 10f, halfH - 10f)

                drawLine(
                    color = levelColor,
                    start = Offset(frameLeft + 12.dp.toPx(), cy + tiltOffset),
                    end = Offset(frameRight - 12.dp.toPx(), cy + tiltOffset),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        // 2. Left Arrow (←)
        val arrowSize = 44.dp
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp)
                .size(arrowSize)
                .clip(CircleShape)
                .clickable { onDirectionChange(PanoDirection.RIGHT_TO_LEFT) },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(28.dp, 20.dp)) {
                val arrowColor = if (direction == PanoDirection.RIGHT_TO_LEFT) Color.White else Color(0x80FFFFFF)
                val path = Path().apply {
                    val aw = size.width
                    val ah = size.height
                    val headW = ah * 0.7f
                    val stemH = 3.dp.toPx()
                    // Stem
                    moveTo(headW, (ah - stemH) / 2f)
                    lineTo(aw, (ah - stemH) / 2f)
                    lineTo(aw, (ah + stemH) / 2f)
                    lineTo(headW, (ah + stemH) / 2f)
                    lineTo(headW, ah)
                    lineTo(0f, ah / 2f)
                    lineTo(headW, 0f)
                    close()
                }
                drawPath(path, color = arrowColor)
            }
        }

        // 3. Right Arrow (→)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 28.dp)
                .size(arrowSize)
                .clip(CircleShape)
                .clickable { onDirectionChange(PanoDirection.LEFT_TO_RIGHT) },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(28.dp, 20.dp)) {
                val arrowColor = if (direction == PanoDirection.LEFT_TO_RIGHT) Color.White else Color(0x80FFFFFF)
                val path = Path().apply {
                    val aw = size.width
                    val ah = size.height
                    val headW = ah * 0.7f
                    val stemH = 3.dp.toPx()
                    // Stem pointing right
                    moveTo(0f, (ah - stemH) / 2f)
                    lineTo(aw - headW, (ah - stemH) / 2f)
                    lineTo(aw - headW, 0f)
                    lineTo(aw, ah / 2f)
                    lineTo(aw - headW, ah)
                    lineTo(aw - headW, (ah + stemH) / 2f)
                    lineTo(0f, (ah + stemH) / 2f)
                    close()
                }
                drawPath(path, color = arrowColor)
            }
        }
    }
}
