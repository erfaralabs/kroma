package com.kroma.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.abs

data class DeviceOrientation(
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val isLevel: Boolean = false,
    val isTopDown: Boolean = false,
)

/**
 * Listens to device hardware IMU (Rotation Vector / Accelerometer) to track 3D spatial tilt.
 */
@Composable
fun rememberDeviceOrientation(): State<DeviceOrientation> {
    val context = LocalContext.current
    val orientationState = remember { mutableStateOf(DeviceOrientation()) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val pitch: Float
                val roll: Float

                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                    roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
                } else {
                    val gX = event.values[0]
                    val gY = event.values[1]
                    val gZ = event.values[2]
                    roll = Math.toDegrees(kotlin.math.atan2(-gX.toDouble(), gY.toDouble())).toFloat()
                    pitch = Math.toDegrees(kotlin.math.atan2(gZ.toDouble(), kotlin.math.sqrt((gX * gX + gY * gY).toDouble()))).toFloat()
                }

                // Apple criteria: Pitch > 65 deg = shooting downwards at a tabletop/document
                val isTopDown = abs(pitch) > 65f
                val isLevel = if (isTopDown) {
                    abs(roll) < 1.0f && abs(abs(pitch) - 90f) < 1.0f
                } else {
                    abs(roll) <= 0.8f
                }

                orientationState.value = DeviceOrientation(
                    pitch = pitch,
                    roll = roll,
                    isLevel = isLevel,
                    isTopDown = isTopDown,
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return orientationState
}

/**
 * Apple iOS 18 Viewfinder Spatial Horizon Level & Crosshair:
 * 1. Normal View: Two reference ticks + rotating center bar. Turns golden yellow at 0.0° with haptic snap.
 * 2. Top-Down View: Two crosshairs (+). A floating crosshair tracks device tilt into the static crosshair.
 */
@Composable
fun SpatialHorizonLevel(
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return

    val orientation by rememberDeviceOrientation()
    val view = LocalView.current
    var wasLevel by remember { mutableStateOf(false) }
    var levelStartTime by remember { mutableLongStateOf(0L) }
    var visible by remember { mutableStateOf(true) }

    // Haptic feedback & auto-fade after staying level
    LaunchedEffect(orientation.isLevel) {
        if (orientation.isLevel) {
            if (!wasLevel) {
                // Haptic snap when level is achieved
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                wasLevel = true
            }
            levelStartTime = System.currentTimeMillis()
        } else {
            wasLevel = false
            visible = true
        }
    }

    // Smooth animated roll angle
    val targetAngle = if (orientation.isLevel) 0f else -orientation.roll
    val animatedAngle by animateFloatAsState(
        targetValue = targetAngle,
        animationSpec = spring(stiffness = 400f),
        label = "horizonAngle",
    )

    val yellowColor = Color(0xFFFFD60A)
    val whiteColor = Color(0xCCFFFFFF)
    val activeColor = if (orientation.isLevel) yellowColor else whiteColor

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.46f // Centered in viewfinder optical zone

            if (orientation.isTopDown) {
                // --- Top-Down 2D Target Leveling (+) ---
                val armLen = 14.dp.toPx()
                val strokeW = 1.6.dp.toPx()

                // Static Center Crosshair (Yellow)
                drawLine(
                    color = yellowColor.copy(alpha = 0.5f),
                    start = Offset(cx - armLen, cy),
                    end = Offset(cx + armLen, cy),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = yellowColor.copy(alpha = 0.5f),
                    start = Offset(cx, cy - armLen),
                    end = Offset(cx, cy + armLen),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )

                // Floating Tilt Crosshair
                val dx = (orientation.roll * 3.0f).coerceIn(-40f, 40f)
                val dy = ((abs(orientation.pitch) - 90f) * 3.0f).coerceIn(-40f, 40f)
                val fx = cx + dx
                val fy = cy + dy

                drawLine(
                    color = activeColor,
                    start = Offset(fx - armLen, fy),
                    end = Offset(fx + armLen, fy),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = activeColor,
                    start = Offset(fx, fy - armLen),
                    end = Offset(fx, fy + armLen),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )
            } else {
                // --- Apple Horizon Level Bar ---
                val tickWidth = 24.dp.toPx()
                val gap = 20.dp.toPx()
                val barHalfWidth = 22.dp.toPx()
                val strokeW = 1.8.dp.toPx()

                if (orientation.isLevel) {
                    // Unbroken continuous golden yellow bar when level
                    val fullHalf = tickWidth + gap + barHalfWidth
                    drawLine(
                        color = yellowColor,
                        start = Offset(cx - fullHalf, cy),
                        end = Offset(cx + fullHalf, cy),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round,
                    )
                } else {
                    // Left and right reference ticks
                    drawLine(
                        color = Color(0x66FFFFFF),
                        start = Offset(cx - (barHalfWidth + gap + tickWidth), cy),
                        end = Offset(cx - (barHalfWidth + gap), cy),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color(0x66FFFFFF),
                        start = Offset(cx + (barHalfWidth + gap), cy),
                        end = Offset(cx + (barHalfWidth + gap + tickWidth), cy),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round,
                    )

                    // Rotating center leveling bar
                    rotate(degrees = animatedAngle, pivot = Offset(cx, cy)) {
                        drawLine(
                            color = whiteColor,
                            start = Offset(cx - barHalfWidth, cy),
                            end = Offset(cx + barHalfWidth, cy),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}
