package com.kroma.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import com.kroma.app.engine.ColorChart
import com.kroma.app.engine.Preset
import com.kroma.app.engine.Presets
import com.kroma.app.engine.StyleEngine
import com.kroma.app.engine.StyleState

/**
 * Apple iOS 18 Photographic Styles Selector:
 * Floating frosted dark card with uppercase tracked title, indicator dot,
 * filmstrip of photographic gradient preview cards, and pill CUSTOMIZE button.
 */
@Composable
fun AppleStylesSelector(
    state: StyleState,
    onSelect: (StyleState) -> Unit,
    onCustomize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialIndex = remember {
        Presets.ALL.indexOfFirst { it.id == state.presetId }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var userSwiping by remember { mutableStateOf(false) }

    val currentPreset = remember(state.presetId) {
        Presets.ALL.firstOrNull { it.id == state.presetId } ?: Presets.byId(Presets.NATIVE_ID)
    }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userSwiping = true
        }
    }

    // Auto-center the selected style card in the filmstrip when changed externally
    LaunchedEffect(state.presetId) {
        val index = Presets.ALL.indexOfFirst { it.id == state.presetId }
        if (index >= 0 && !userSwiping) {
            listState.animateScrollToItem(index)
        }
    }

    // Automatically select style ONLY when the user physically swipes the filmstrip
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
            if (centerIdx != null && userSwiping) {
                val preset = Presets.ALL.getOrNull(centerIdx)
                if (preset != null && preset.id != state.presetId) {
                    onSelect(state.copy(presetId = preset.id, padColor = 0f, padTone = 0f))
                }
                if (!listState.isScrollInProgress) {
                    userSwiping = false
                }
            }
        }
    }

    val cardShape = RoundedCornerShape(32.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(cardShape)
            .background(Color(0xEE161619))
            .border(1.dp, Color(0x35FFFFFF), cardShape)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 12.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // --- Top Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Sleek Apple Chevron Back Button
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(10.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.7f, 0f)
                        lineTo(size.width * 0.2f, size.height * 0.5f)
                        lineTo(size.width * 0.7f, size.height)
                    }
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }

            // Centered Style Title (with two framing lines for Standard)
            val isStandard = currentPreset.id == Presets.NATIVE_ID
            val title = if (isStandard) {
                "STANDARD"
            } else if (currentPreset.name.equals("Vivid", ignoreCase = true)) {
                "VIBRANT"
            } else {
                currentPreset.appleReference.uppercase()
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (isStandard) {
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(1.5.dp)
                            .background(Color(0x99FFFFFF), RoundedCornerShape(1.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                )

                if (isStandard) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(1.5.dp)
                            .background(Color(0x99FFFFFF), RoundedCornerShape(1.dp)),
                    )
                }
            }

            // Balancer spacer to keep title strictly centered
            Spacer(Modifier.size(30.dp))
        }

        Spacer(Modifier.height(4.dp))

        // Small indicator dot directly under the title
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(Color(0xE6FFFFFF)),
        )

        Spacer(Modifier.height(10.dp))

        // --- Photographic Styles Filmstrip Carousel ---
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cardWidth = 50.dp
            val centerPadding = (maxWidth - cardWidth) / 2

            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = centerPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(Presets.ALL, key = { it.id }) { preset ->
                    val isSelected = preset.id == state.presetId
                    val isStandardPreset = preset.id == Presets.NATIVE_ID

                    if (isStandardPreset) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Left vertical divider line framing Standard
                            Box(
                                modifier = Modifier
                                    .width(1.5.dp)
                                    .height(44.dp)
                                    .background(Color(0x80FFFFFF), RoundedCornerShape(1.dp)),
                            )

                            StyleCard(
                                preset = preset,
                                isSelected = isSelected,
                                onClick = {
                                    onSelect(state.copy(presetId = preset.id, padColor = 0f, padTone = 0f))
                                },
                            )

                            // Right vertical divider line framing Standard
                            Box(
                                modifier = Modifier
                                    .width(1.5.dp)
                                    .height(44.dp)
                                    .background(Color(0x80FFFFFF), RoundedCornerShape(1.dp)),
                            )
                        }
                    } else {
                        StyleCard(
                            preset = preset,
                            isSelected = isSelected,
                            onClick = {
                                onSelect(state.copy(presetId = preset.id, padColor = 0f, padTone = 0f))
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // --- Pill CUSTOMIZE Button ---
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x33FFFFFF))
                .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(50))
                .clickable { onCustomize() }
                .padding(horizontal = 22.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "CUSTOMIZE",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
        }
    }
}

/**
 * Individual Photographic Style Preview Card:
 * Portrait aspect ratio (50×72dp) with multi-stop photographic gradient previewing
 * highlights, skin tones, and shadow color cast of that specific style.
 */
@Composable
private fun StyleCard(
    preset: Preset,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1.0f,
        animationSpec = tween(180),
        label = "cardScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.70f,
        animationSpec = tween(180),
        label = "cardAlpha",
    )

    // Render 3-stop photographic look (Sky/Highlight, Skin/Midtone, Shadow) through StyleEngine
    val skyColor = remember(preset.id) {
        if (preset.monochrome) {
            styledColor(0.85f, 0.85f, 0.85f, preset)
        } else {
            styledColor(0.40f, 0.58f, 0.78f, preset)
        }
    }
    val midColor = remember(preset.id) {
        if (preset.monochrome) {
            styledColor(0.48f, 0.48f, 0.48f, preset)
        } else {
            styledColor(0.88f, 0.72f, 0.60f, preset)
        }
    }
    val shadowColor = remember(preset.id) {
        if (preset.monochrome) {
            styledColor(0.12f, 0.12f, 0.12f, preset)
        } else {
            styledColor(0.20f, 0.18f, 0.16f, preset)
        }
    }

    val cardShape = RoundedCornerShape(13.dp)

    Box(
        modifier = Modifier
            .width(50.dp)
            .height(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(alpha)
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(skyColor, midColor, shadowColor),
                ),
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.White else Color(0x35FFFFFF),
                shape = cardShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    )
}

private fun styledColor(r: Float, g: Float, b: Float, preset: Preset): Color {
    val c = StyleEngine.renderSrgb(floatArrayOf(r, g, b), StyleState(presetId = preset.id))
    return Color(
        c[0].coerceIn(0f, 1f),
        c[1].coerceIn(0f, 1f),
        c[2].coerceIn(0f, 1f),
    )
}
