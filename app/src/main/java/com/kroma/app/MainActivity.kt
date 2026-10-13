package com.kroma.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture
import com.kroma.app.camera.GlesStyleView
import com.kroma.app.camera.StyleParams
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kroma.app.camera.CameraSession
import com.kroma.app.engine.ColorChart
import com.kroma.app.engine.Presets
import com.kroma.app.engine.StyleEngine
import com.kroma.app.engine.StyleState
import com.kroma.app.ui.AeAfLockPill
import com.kroma.app.ui.ApertureButton
import com.kroma.app.ui.CalibrationSheet
import com.kroma.app.ui.CameraMode
import com.kroma.app.ui.CameraSettingsGrid
import com.kroma.app.ui.FlipButton
import com.kroma.app.ui.FocusReticle
import com.kroma.app.ui.KromaColors
import com.kroma.app.ui.LensPicker
import com.kroma.app.ui.ModeSelector
import com.kroma.app.ui.ProControlBar
import com.kroma.app.ui.ShutterButton
import com.kroma.app.ui.StyleDotsButton
import com.kroma.app.ui.ThumbnailButton
import com.kroma.app.ui.ViewfinderGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LENS_ZOOMS = listOf(0.5f, 1f, 2f, 4f, 8f)

/** A real Android camera app: CameraX preview + capture, styled with the Kroma engine. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                KromaApp()
            }
        }
    }
}

@Composable
private fun KromaApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val session = remember { CameraSession(context) }
    var glReady by remember { mutableStateOf(false) }
    val glView = remember {
        GlesStyleView(context).apply { onGlReady = { post { glReady = true } } }
    }

    var state by remember { mutableStateOf(StyleState()) }
    var menuOpen by remember { mutableStateOf(false) }
    var stylesOpen by remember { mutableStateOf(false) }
    var calibrationOpen by remember { mutableStateOf(false) }
    var lens by remember { mutableStateOf(1) }
    var mode by remember { mutableStateOf(CameraMode.PHOTO) }
    var focusLocked by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var flash by remember { mutableStateOf(0) }
    var timer by remember { mutableStateOf(0) }
    var ev by remember { mutableStateOf(0) }
    var aspect by remember { mutableStateOf(0) }
    var proMode by remember { mutableStateOf(false) }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(granted, glReady) {
        if (granted && glReady) {
            session.attach(lifecycleOwner, glView.surfaceProvider) { message = it }
        }
    }

    // Push the live style state to the GL preview pass every time it changes.
    LaunchedEffect(state) { glView.setStyle(StyleParams.from(state)) }
    LaunchedEffect(lens) { session.setZoom(LENS_ZOOMS[lens]) }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            message = null
        }
    }

    // Real frosted glass: blur the live preview behind an open overlay (API 31+).
    LaunchedEffect(menuOpen, calibrationOpen) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = if (menuOpen || calibrationOpen) 55f else 0f
            glView.setRenderEffect(
                if (radius > 0f) {
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                } else {
                    null
                },
            )
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().clickable { focusLocked = !focusLocked }) {
            if (granted) {
                AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
            } else {
                Viewfinder(state)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Camera permission is required", color = Color.White)
                }
            }
            ViewfinderGrid()
            if (focusLocked) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FocusReticle() }
            }
        }

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Spacer(Modifier.height(6.dp))
            if (proMode) {
                ProControlBar()
                Spacer(Modifier.height(12.dp))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    recording -> Text(
                        "\u25CF REC",
                        color = Color(0xFFFF4D4D),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    focusLocked -> AeAfLockPill()
                }
            }

            Spacer(Modifier.weight(1f))

            if (calibrationOpen) {
                CalibrationSheet(
                    state = state,
                    onStateChange = { state = it },
                    onBack = { calibrationOpen = false },
                )
            } else if (!menuOpen) {
                if (stylesOpen) {
                    StylesStrip(
                        state = state,
                        onSelect = { state = it },
                        onCustomize = { calibrationOpen = true },
                        onClose = { stylesOpen = false },
                    )
                    Spacer(Modifier.height(10.dp))
                }

                LensPicker(selected = lens, onSelect = { lens = it })
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ApertureButton(onClick = {})
                    ShutterButton(
                        onClick = {
                            if (mode == CameraMode.VIDEO) {
                                session.toggleRecording(
                                    onSaved = { recording = false; message = "Video saved to Movies/Kroma" },
                                    onError = { recording = false; message = it },
                                )
                                recording = session.isRecording
                            } else {
                                val fire = {
                                    session.takePhoto(
                                        onSaved = { uri ->
                                            message = "Photo saved to Pictures/Kroma"
                                            uri?.let { u ->
                                                scope.launch { thumb = loadThumbnail(context, u) }
                                            }
                                        },
                                        onError = { message = it },
                                    )
                                }
                                if (timer > 0) {
                                    message = "Timer ${timer}s"
                                    scope.launch {
                                        delay(timer * 1000L)
                                        fire()
                                    }
                                } else {
                                    fire()
                                }
                            }
                        },
                    )
                    StyleDotsButton(active = menuOpen || stylesOpen, onClick = { menuOpen = true })
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ThumbnailButton(
                        bitmap = thumb,
                        swatch = styled(0.85f, 0.68f, 0.56f, state),
                        onClick = {},
                    )
                    ModeSelector(selected = mode, onSelect = { mode = it })
                    FlipButton(onClick = { session.flipLens() })
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    AnimatedVisibility(
        visible = menuOpen,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
    ) {
        CameraSettingsGrid(
            flashValue = when (flash) {
                1 -> "On"
                2 -> "Auto"
                else -> "Off"
            },
            timerValue = if (timer == 0) "Off" else "${timer}s",
            exposureValue = if (ev == 0) "0.0" else String.format("%+.1f", ev.toFloat()),
            aspectValue = if (aspect == 0) "4:3" else "16:9",
            proEnabled = proMode,
            onFlash = {
                flash = (flash + 1) % 3
                session.setFlashMode(
                    when (flash) {
                        1 -> ImageCapture.FLASH_MODE_ON
                        2 -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    },
                )
            },
            onTimer = {
                timer = when (timer) {
                    0 -> 3
                    3 -> 10
                    else -> 0
                }
            },
            onExposure = {
                ev = if (ev >= 2) -2 else ev + 1
                session.setExposureCompensation(ev)
            },
            onAspect = {
                aspect = if (aspect == 0) 1 else 0
                session.setAspectRatio(
                    if (aspect == 0) AspectRatio.RATIO_4_3 else AspectRatio.RATIO_16_9,
                )
            },
            onPro = { proMode = !proMode },
            onStyles = {
                menuOpen = false
                stylesOpen = true
            },
            onDismiss = { menuOpen = false },
        )
    }
}


/** Fallback shown while camera permission is pending. */
@Composable
private fun Viewfinder(state: StyleState) {
    Canvas(Modifier.fillMaxSize()) {
        val horizon = size.height * 0.62f
        val skyTop = styled(0.32f, 0.45f, 0.68f, state)
        val skyBottom = styled(0.74f, 0.70f, 0.54f, state)
        val ground = styled(0.22f, 0.20f, 0.17f, state)

        drawRect(
            brush = Brush.verticalGradient(listOf(skyTop, skyBottom), 0f, horizon),
            size = Size(size.width, horizon),
        )
        drawRect(color = ground, topLeft = Offset(0f, horizon), size = Size(size.width, size.height - horizon))
    }
}

private fun styled(r: Float, g: Float, b: Float, state: StyleState): Color {
    val c = StyleEngine.renderSrgb(floatArrayOf(r, g, b), state)
    return Color(c[0], c[1], c[2])
}

/** Spec §2 State 1 (carousel) + Customize (State 2). */
@Composable
private fun StylesStrip(
    state: StyleState,
    onSelect: (StyleState) -> Unit,
    onCustomize: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CustomizeChip(onClick = onCustomize)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close styles",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SelectionStrip(state, onSelect)
    }
}

@Composable
private fun CustomizeChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x33FFFFFF))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Filled.Tune,
            contentDescription = null,
            tint = KromaColors.Accent,
            modifier = Modifier.size(16.dp),
        )
        Text("Customize", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SelectionStrip(state: StyleState, onSelect: (StyleState) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(Presets.ALL, key = { it.id }) { preset ->
            val selected = preset.id == state.presetId
            val thumb = StyleEngine.renderSrgb(ColorChart.THUMB_SEED, StyleState(presetId = preset.id))

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
                            color = if (selected) KromaColors.Accent else Color(0xFF3A3A3A),
                            shape = RoundedCornerShape(10.dp),
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    preset.name,
                    color = if (selected) Color.White else KromaColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}


/** Decodes a downscaled bitmap for the gallery thumbnail (shows the most recent capture). */
private suspend fun loadThumbnail(context: android.content.Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(context.contentResolver, uri),
                ) { decoder, _, _ -> decoder.setTargetSampleSize(4) }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (t: Throwable) {
            null
        }
    }

