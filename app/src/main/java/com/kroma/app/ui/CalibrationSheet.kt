package com.kroma.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kroma.app.engine.StyleState

enum class SheetTab { PALETTE, TEXTURE }

/**
 * Spec §2 *State 3* — the Calibration Workspace bottom sheet.
 * `‹` collapses to the selection strip; `↺` restores the preset baseline.
 */
@Composable
fun CalibrationSheet(
    state: StyleState,
    onStateChange: (StyleState) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(SheetTab.PALETTE) }
    val shape = RoundedCornerShape(
        topStart = KromaDimens.SheetCorner,
        topEnd = KromaDimens.SheetCorner,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KromaColors.Sheet)
            .border(1.dp, KromaColors.SheetEdge, shape)
            .padding(horizontal = KromaDimens.SheetPadding, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetHeader(
            tab = tab,
            onTabChange = { tab = it },
            onBack = onBack,
            onReset = { onStateChange(state.reset()) },
            showReset = !state.isAtDefaults,
        )

        Spacer(Modifier.height(18.dp))

        when (tab) {
            SheetTab.PALETTE -> PaletteContent(state, onStateChange)
            SheetTab.TEXTURE -> TexturePanel(state, onStateChange)
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
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = KromaColors.TextPrimary,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onBack() },
            )
        }

        Spacer(Modifier.weight(1f))
        SegmentedTabs(tab = tab, onTabChange = onTabChange)
        Spacer(Modifier.weight(1f))

        Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterEnd) {
            if (showReset) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = "Reset",
                    tint = KromaColors.TextPrimary,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onReset() },
                )
            }
        }
    }
}

@Composable
private fun SegmentedTabs(tab: SheetTab, onTabChange: (SheetTab) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x1FFFFFFF))
            .padding(4.dp),
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
            .background(if (selected) Color(0x33FFFFFF) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (selected) KromaColors.Accent else KromaColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = 1.2.sp,
        )
    }
}

/** The PALETTE tab (§1): 2D pad + HUD readout + master intensity. */
@Composable
private fun PaletteContent(state: StyleState, onStateChange: (StyleState) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PalettePad(
            state = state,
            onPadChange = { color, tone ->
                onStateChange(state.copy(padColor = color, padTone = tone))
            },
        )

        Spacer(Modifier.height(18.dp))

        Text(
            "Tone: ${signed(state.effectiveTone.toInt())}     " +
                "Color: ${signed(state.effectiveColor.toInt())}     " +
                "Intensity: ${(state.intensity * 100).toInt()}%",
            color = KromaColors.TextSecondary,
            fontSize = 12.sp,
        )

        Spacer(Modifier.height(4.dp))

        Slider(
            value = state.intensity,
            onValueChange = { onStateChange(state.copy(intensity = it)) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = KromaColors.Accent,
                inactiveTrackColor = Color(0x33FFFFFF),
            ),
        )
    }
}

private fun signed(v: Int): String = if (v >= 0) "+$v" else "$v"

