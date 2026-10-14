package com.kroma.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kroma.app.engine.StyleState
import com.kroma.app.engine.TextureMode

private const val PIP_COUNT = 11

private data class TextureSlot(
    val mode: TextureMode?,   // null => the Grain toggle
    val label: String,
    val icon: ImageVector,
)

private val SLOTS = listOf(
    TextureSlot(TextureMode.STANDARD, "Standard", Icons.Filled.Block),
    TextureSlot(TextureMode.SOFT_SKIN, "Soft Skin", Icons.Filled.Person),
    TextureSlot(TextureMode.GLOW, "Glow", Icons.Filled.BlurOn),
    TextureSlot(TextureMode.FILM, "Film", Icons.Filled.Movie),
    TextureSlot(null, "Grain", Icons.Filled.Grain),
)

/** Spec §5 — the Texture sub-tab. Texture is applied **after capture**. */
@Composable
fun TexturePanel(
    state: StyleState,
    onStateChange: (StyleState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SLOTS.forEachIndexed { index, slot ->
                if (index == SLOTS.lastIndex) {
                    // divider before GRAIN — it is a toggle, not a slider
                    Box(
                        Modifier
                            .padding(top = 10.dp, start = 2.dp, end = 2.dp)
                            .width(1.dp)
                            .height(34.dp)
                            .background(Color(0x33FFFFFF)),
                    )
                }
                TextureSlotButton(
                    slot = slot,
                    selected = if (slot.mode == null) state.filmGrainEnabled else state.textureMode == slot.mode,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onStateChange(
                            when (val m = slot.mode) {
                                null -> state.copy(filmGrainEnabled = !state.filmGrainEnabled)
                                TextureMode.STANDARD -> state.copy(textureMode = TextureMode.STANDARD)
                                else -> state.copy(
                                    textureMode = m,
                                    // make a fresh selection visible straight away
                                    textureIntensity = if (state.textureIntensity == 0f) 0.5f else state.textureIntensity,
                                )
                            },
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (state.textureMode != TextureMode.STANDARD) {
            StrengthPips(
                value = state.textureIntensity,
                onValueChange = { onStateChange(state.copy(textureIntensity = it)) },
            )
        } else {
            Spacer(Modifier.height(14.dp))
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Live texture preview & capture active.",
            color = KromaColors.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TextureSlotButton(
    slot: TextureSlot,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else KromaColors.PillIdle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = slot.icon,
                contentDescription = slot.label,
                tint = if (selected) Color(0xFF111111) else KromaColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            slot.label.uppercase(),
            color = if (selected) Color.White else KromaColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp,
        )
    }
}

/** Segmented strength bar (~11 pips). Tap or drag to set `textureIntensity`. */
@Composable
private fun StrengthPips(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filled = (value * PIP_COUNT).toInt().coerceIn(0, PIP_COUNT)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(Unit) {
                val w = size.width.toFloat().coerceAtLeast(1f)
                fun set(posX: Float) {
                    val idx = ((posX / w) * PIP_COUNT).toInt().coerceIn(0, PIP_COUNT - 1)
                    onValueChange((idx + 1).toFloat() / PIP_COUNT)
                }
                detectDragGestures(
                    onDragStart = { set(it.x) },
                    onDrag = { change, _ -> set(change.position.x) },
                )
            }
            .pointerInput(Unit) {
                val w = size.width.toFloat().coerceAtLeast(1f)
                detectTapGestures { pos ->
                    val idx = ((pos.x / w) * PIP_COUNT).toInt().coerceIn(0, PIP_COUNT - 1)
                    onValueChange((idx + 1).toFloat() / PIP_COUNT)
                }
            },
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(PIP_COUNT) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < filled) KromaColors.Accent else Color(0x33FFFFFF)),
                )
            }
        }
    }
}

