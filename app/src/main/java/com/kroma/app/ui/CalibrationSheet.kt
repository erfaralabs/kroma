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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kroma.app.engine.StyleState

enum class SheetTab { PALETTE, TEXTURE }

/**
 * iOS Photographic Styles Customization Panel:
 * Floating rounded frosted card containing segmented [PALETTE | TEXTURE] header,
 * 2D terracotta color/tone pad, and iOS tick-bar intensity scrubber (- |||||||| +).
 */
@Composable
fun CalibrationSheet(
    state: StyleState,
    onStateChange: (StyleState) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(SheetTab.PALETTE) }
    val cardShape = RoundedCornerShape(32.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(Color(0xEE16171B))
                .border(1.dp, Color(0x35FFFFFF), cardShape)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetHeader(
                tab = tab,
                onTabChange = { tab = it },
                onBack = onBack,
                onReset = { onStateChange(state.reset()) },
                showReset = !state.isAtDefaults,
            )

            Spacer(Modifier.height(14.dp))

            when (tab) {
                SheetTab.PALETTE -> PaletteContent(state, onStateChange)
                SheetTab.TEXTURE -> TexturePanel(state, onStateChange)
            }
        }
    }
}

@Composable
private fun SheetHeader(
    tab: SheetTab,
    onTabChange: (SheetTab) -> Unit,
    onBack: () -> Unit,
    onReset: () -> Unit,
    showReset: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Back Chevron button
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }

        // Segmented Capsule Tabs: [ PALETTE | TEXTURE ]
        SegmentedTabs(tab = tab, onTabChange = onTabChange)

        // Reset button
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (showReset) Color(0x22FFFFFF) else Color.Transparent)
                .clickable(enabled = showReset) { onReset() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.RestartAlt,
                contentDescription = "Reset",
                tint = if (showReset) Color.White else Color(0x35FFFFFF),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun SegmentedTabs(tab: SheetTab, onTabChange: (SheetTab) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x24FFFFFF))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabLabel("PALETTE", tab == SheetTab.PALETTE) { onTabChange(SheetTab.PALETTE) }
        TabLabel("TEXTURE", tab == SheetTab.TEXTURE) { onTabChange(SheetTab.TEXTURE) }
    }
}

@Composable
private fun TabLabel(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0x40FFFFFF) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFFFF9E45) else Color(0x8CFFFFFF),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 1.sp,
        )
    }
}

/** The PALETTE tab: 2D pad + iOS tick-bar intensity scrubber. */
@Composable
private fun PaletteContent(state: StyleState, onStateChange: (StyleState) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Spec §1: Persistent top-level HUD real-time integer readouts (Tone: ±N, Color: ±N, Intensity: N%)
        val toneInt = kotlin.math.round(state.effectiveTone).toInt()
        val colorInt = kotlin.math.round(state.effectiveColor).toInt()
        val intensityPct = kotlin.math.round(state.intensity * 100f).toInt()

        Row(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x33000000))
                .padding(horizontal = 14.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tone: ${if (toneInt > 0) "+$toneInt" else "$toneInt"}",
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            )
            Box(Modifier.size(3.dp).clip(CircleShape).background(Color(0x55FFFFFF)))
            Text(
                text = "Color: ${if (colorInt > 0) "+$colorInt" else "$colorInt"}",
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            )
            Box(Modifier.size(3.dp).clip(CircleShape).background(Color(0x55FFFFFF)))
            Text(
                text = "Intensity: $intensityPct%",
                color = Color(0xFFFF9E45),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            )
        }

        PalettePad(
            state = state,
            onPadChange = { color, tone ->
                onStateChange(state.copy(padColor = color, padTone = tone))
            },
        )

        Spacer(Modifier.height(14.dp))

        // iOS - |||||||||||||||| + intensity tick scrubber
        IntensityTickSlider(
            intensity = state.intensity,
            onIntensityChange = { onStateChange(state.copy(intensity = it)) },
        )
    }
}

/**
 * iOS-authentic Intensity Tick Bar Slider:
 * Horizontal row with "-" on left, "+" on right, and 18 vertical ticks illuminated in warm amber.
 * Draggable and tap-scrubbable with haptic feedback.
 */
@Composable
private fun IntensityTickSlider(
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val tickCount = 18

    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "−",
            color = Color(0x99FFFFFF),
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .clickable {
                    val next = (intensity - 0.05f).coerceIn(0f, 1f)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onIntensityChange(next)
                }
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )

        Spacer(Modifier.width(4.dp))

        Canvas(
            modifier = Modifier
                .width(136.dp)
                .height(20.dp)
                .pointerInput(Unit) {
                    fun updateAt(x: Float) {
                        val fraction = (x / size.width.toFloat()).coerceIn(0f, 1f)
                        val prevTick = (intensity * (tickCount - 1)).toInt()
                        val newTick = (fraction * (tickCount - 1)).toInt()
                        if (newTick != prevTick) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onIntensityChange(fraction)
                    }
                    detectTapGestures { pos -> updateAt(pos.x) }
                }
                .pointerInput(Unit) {
                    fun updateAt(x: Float) {
                        val fraction = (x / size.width.toFloat()).coerceIn(0f, 1f)
                        val prevTick = (intensity * (tickCount - 1)).toInt()
                        val newTick = (fraction * (tickCount - 1)).toInt()
                        if (newTick != prevTick) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onIntensityChange(fraction)
                    }
                    detectDragGestures(
                        onDragStart = { pos -> updateAt(pos.x) },
                        onDrag = { change, _ -> updateAt(change.position.x) },
                    )
                },
        ) {
            val totalW = size.width
            val totalH = size.height
            val barW = 2.5.dp.toPx()
            val barH = 14.dp.toPx()
            val activeBars = (intensity * (tickCount - 1)).toInt().coerceIn(0, tickCount - 1)
            val spacing = (totalW - (tickCount * barW)) / (tickCount - 1)
            val topY = (totalH - barH) / 2f
            val corner = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx())

            val activeColor = Color(0xFFFF9500)
            val inactiveColor = Color(0x38FFFFFF)

            for (i in 0 until tickCount) {
                val leftX = i * (barW + spacing)
                val color = if (i <= activeBars) activeColor else inactiveColor
                drawRoundRect(
                    color = color,
                    topLeft = Offset(leftX, topY),
                    size = Size(barW, barH),
                    cornerRadius = corner,
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        Text(
            text = "+",
            color = Color(0x99FFFFFF),
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .clickable {
                    val next = (intensity + 0.05f).coerceIn(0f, 1f)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onIntensityChange(next)
                }
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}


