package com.kroma.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import com.kroma.app.engine.ColorChart
import com.kroma.app.engine.Presets
import com.kroma.app.engine.StyleEngine
import com.kroma.app.engine.StyleState

/**
 * Phase 0 shell: the spec §2 "Selection Strip" plus the §1 HUD readout, with a live CPU-rendered
 * colour chart so the 15 styles can be eyeballed before the GL/camera pipeline lands.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                KromaScreen()
            }
        }
    }
}

@Composable
private fun KromaScreen() {
    var state by remember { mutableStateOf(StyleState()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(16.dp),
    ) {
        Text(
            "KROMA",
            color = Color(0xFFF2F2F2),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = 6.sp,
        )
        Text("Photographic Styles engine · Phase 0", color = Color(0xFF8A8A8A), fontSize = 12.sp)

        Spacer(Modifier.height(20.dp))
        ColorChartView(state)

        Spacer(Modifier.height(20.dp))
        HudReadout(state)

        Spacer(Modifier.height(10.dp))
        IntensityControl(state) { state = it }

        Spacer(Modifier.weight(1f))
        SelectionStrip(state) { state = it }
    }
}

@Composable
private fun ColorChartView(state: StyleState) {
    Column {
        Text(
            "COLOR CHART · live CPU render",
            color = Color(0xFF6F6F6F),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ColorChart.SWATCHES.forEach { swatch ->
                val styled = StyleEngine.renderSrgb(swatch.srgb, state)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(styled[0], styled[1], styled[2])),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(swatch.label, color = Color(0xFF808080), fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun HudReadout(state: StyleState) {
    val p = state.preset
    val tone = (state.padTone + p.anchorY).toInt()
    val color = (state.padColor + p.anchorX).toInt()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(p.category.uppercase(), color = Color(0xFFFFD84D), fontSize = 10.sp, letterSpacing = 1.sp)
        }
        Text("Reference · ${p.appleReference}", color = Color(0xFF9A9A9A), fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tone: ${fmtSigned(tone)}    Color: ${fmtSigned(color)}    Intensity: ${(state.intensity * 100).toInt()}%",
            color = Color(0xFFCFCFCF),
            fontSize = 12.sp,
        )
        Text(
            "anchor(${p.anchorX.toInt()}, ${p.anchorY.toInt()})   beta ${p.beta}   hue ${p.hueBiasDeg}°   toe ${p.shadowToe}",
            color = Color(0xFF6F6F6F),
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun IntensityControl(state: StyleState, onChange: (StyleState) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Intensity", color = Color(0xFF8A8A8A), fontSize = 11.sp)
            Text("${(state.intensity * 100).toInt()}%", color = Color(0xFF8A8A8A), fontSize = 11.sp)
        }
        Slider(
            value = state.intensity,
            onValueChange = { onChange(state.copy(intensity = it)) },
            valueRange = 0f..1f,
        )
    }
}

@Composable
private fun SelectionStrip(state: StyleState, onSelect: (StyleState) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Presets.ALL, key = { it.id }) { preset ->
            val selected = preset.id == state.presetId
            val thumb = StyleEngine.renderSrgb(
                ColorChart.THUMB_SEED,
                StyleState(presetId = preset.id, intensity = 1f),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onSelect(StyleState(presetId = preset.id, intensity = state.intensity))
                    }
                    .padding(6.dp),
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(thumb[0], thumb[1], thumb[2]))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) Color(0xFFFFD84D) else Color(0xFF3A3A3A),
                            shape = RoundedCornerShape(10.dp),
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    preset.name,
                    color = if (selected) Color.White else Color(0xFF9A9A9A),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun fmtSigned(v: Int): String = if (v >= 0) "+$v" else "$v"

