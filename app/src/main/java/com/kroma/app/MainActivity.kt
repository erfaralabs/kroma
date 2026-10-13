package com.kroma.app

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.RenderEffect
import android.graphics.Shader
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.ui.draw.clipToBounds
import com.kroma.app.camera.GlesStyleView
import com.kroma.app.camera.StyleParams
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kroma.app.camera.CameraSession
import com.kroma.app.engine.ColorChart
import com.kroma.app.engine.Presets
import com.kroma.app.engine.StyleEngine
import com.kroma.app.engine.StyleState
import com.kroma.app.ui.AeAfLockPill
import com.kroma.app.ui.ApertureButton
import com.kroma.app.ui.AppleStylesSelector
import com.kroma.app.ui.CalibrationSheet
import com.kroma.app.ui.CameraAspect
import com.kroma.app.ui.CameraMode
import com.kroma.app.ui.CameraSettingsGrid
import com.kroma.app.ui.FlipButton
import com.kroma.app.ui.FocusReticle
import com.kroma.app.ui.KromaColors
import com.kroma.app.ui.LensPicker
import com.kroma.app.ui.ModeSelector
import com.kroma.app.ui.NightTopBar
import com.kroma.app.ui.PanoramaGuideOverlay
import com.kroma.app.ui.PanoDirection
import com.kroma.app.ui.PanoTopBar
import com.kroma.app.ui.PortraitTopBar
import com.kroma.app.ui.PortraitApertureOverlay
import com.kroma.app.ui.ProControlBar
import com.kroma.app.ui.ShutterButton
import com.kroma.app.ui.SpatialHorizonLevel
import com.kroma.app.ui.StyleDotsButton
import com.kroma.app.ui.ThumbnailButton
import com.kroma.app.ui.ViewfinderGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class WbOption(val label: String, val tempKelvin: String, val c2Mode: Int)

private val WB_OPTIONS = listOf(
    WbOption("Auto", "Auto", CaptureRequest.CONTROL_AWB_MODE_AUTO),
    WbOption("Daylight", "5500K", CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT),
    WbOption("Cloudy", "6500K", CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
    WbOption("Shade", "7500K", CaptureRequest.CONTROL_AWB_MODE_SHADE),
    WbOption("Tungsten", "3200K", CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT),
    WbOption("Fluorescent", "4000K", CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT),
)

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
    var lens by remember { mutableStateOf(0) }
    var liveZoom by remember { mutableStateOf(1f) }
    var zoomStops by remember { mutableStateOf(listOf(1f)) }
    var mode by remember { mutableStateOf(CameraMode.PHOTO) }
    var focusLocked by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var flash by remember { mutableStateOf(0) }
    var timer by remember { mutableStateOf(0) }
    var ev by remember { mutableStateOf(0) }
    var exposureEv by remember { mutableFloatStateOf(0f) }
    var proShutterSpeed by remember { mutableStateOf("AUTO") }
    var proFocusMode by remember { mutableStateOf("AF") }
    var aspect by remember { mutableStateOf(CameraAspect.RATIO_16_9) }
    var proMode by remember { mutableStateOf(false) }
    var rawEnabled by remember { mutableStateOf(false) }
    var apertureOverlayOpen by remember { mutableStateOf(false) }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    var lastPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var gridEnabled by remember { mutableStateOf(true) }
    var wbIndex by remember { mutableStateOf(0) }
    var timerCountdown by remember { mutableStateOf<Int?>(null) }
    var qualityLabel by remember { mutableStateOf(session.currentQuality?.label ?: "12 MP") }
    var portraitAperture by remember { mutableStateOf(2.8f) }
    var nightSeconds by remember { mutableStateOf(2) }
    var nightCapturing by remember { mutableStateOf(false) }
    var panoCapturing by remember { mutableStateOf(false) }
    var panoDirection by remember { mutableStateOf(PanoDirection.LEFT_TO_RIGHT) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(granted, glReady) {
        if (granted && glReady) {
            session.attach(lifecycleOwner, glView.surfaceProvider) { message = it }
        }
    }

    LaunchedEffect(mode) {
        if (mode != CameraMode.PORTRAIT) {
            apertureOverlayOpen = false
        }
    }

    // Load the most recent photo in Gallery on startup for the thumbnail
    LaunchedEffect(granted) {
        if (granted) {
            fetchLatestPhoto(context)?.let { (u, b) ->
                lastPhotoUri = u
                thumb = b
            }
        }
    }

    // Android system back button handler: close sheets/menus first instead of exiting app
    BackHandler(enabled = menuOpen || stylesOpen || calibrationOpen || apertureOverlayOpen) {
        when {
            apertureOverlayOpen -> apertureOverlayOpen = false
            calibrationOpen -> calibrationOpen = false
            stylesOpen -> stylesOpen = false
            menuOpen -> menuOpen = false
        }
    }

    // Push the live style state to the GL preview pass every time it changes.
    LaunchedEffect(state) { glView.setStyle(StyleParams.from(state)) }
    LaunchedEffect(Unit) { session.onZoomStopsChanged = { zoomStops = it } }
    LaunchedEffect(Unit) {
        session.onQualitiesChanged = {
            qualityLabel = session.currentQuality?.label ?: "12 MP"
        }
    }

    // Keep the picker on the 1x stop when the device's zoom range arrives.
    LaunchedEffect(zoomStops) {
        val defaultIdx = zoomStops.indexOfFirst { it in 0.99f..1.01f }.coerceAtLeast(0)
        lens = defaultIdx
        liveZoom = zoomStops.getOrElse(defaultIdx) { 1f }
        android.util.Log.i("KromaZoom", "MainActivity lens=$lens stops=$zoomStops liveZoom=$liveZoom")
    }
    LaunchedEffect(aspect) {
        focusLocked = false
        focusPoint = null
    }
    // Zoom is now driven by the picker (continuous during scrub, exact on chip tap).
    LaunchedEffect(message) {
        if (message != null) {
            android.util.Log.i("KromaStatus", message ?: "")
            delay(1500L)
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
        if (granted) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())

                val sw = maxWidth
                val sh = maxHeight
                val isPortrait = sh >= sw
                val maskHeight = remember(aspect, sw, sh) {
                    when (aspect) {
                        CameraAspect.RATIO_1_1 -> ((sh - sw) / 2f).coerceAtLeast(0.dp)
                        CameraAspect.RATIO_4_3 -> if (isPortrait) ((sh - sw * 4f / 3f) / 2f).coerceAtLeast(0.dp) else 0.dp
                        CameraAspect.RATIO_16_9 -> if (isPortrait) ((sh - sw * 16f / 9f) / 2f).coerceAtLeast(0.dp) else 0.dp
                        CameraAspect.RATIO_FULL -> 0.dp
                    }
                }
                val viewfinderHeight = remember(sh, maskHeight) {
                    (sh - maskHeight * 2).coerceAtLeast(0.dp)
                }
                val canInteractWithViewfinder = !menuOpen && !stylesOpen && !calibrationOpen && mode != CameraMode.PANO

                // Active Viewfinder Area: strictly scoped between the aspect masks.
                // AE/AF focus lock is ONLY clickable inside this active viewfinder area,
                // and NEVER when menus, styles, or calibration overlays are open.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(viewfinderHeight)
                        .align(Alignment.Center)
                        .clipToBounds()
                        .pointerInput(canInteractWithViewfinder, aspect, maskHeight) {
                            if (canInteractWithViewfinder) {
                                detectTapGestures(
                                    onTap = { localOffset ->
                                        val maskPx = ((sh.toPx() - size.height) / 2f).coerceAtLeast(0f)
                                        val viewW = size.width.toFloat()
                                        val viewH = sh.toPx()
                                        focusPoint = localOffset
                                        focusLocked = true
                                        session.focusAt(
                                            x = localOffset.x,
                                            y = localOffset.y + maskPx,
                                            viewWidth = viewW,
                                            viewHeight = viewH,
                                            lock = true,
                                        )
                                    },
                                    onLongPress = { localOffset ->
                                        val maskPx = ((sh.toPx() - size.height) / 2f).coerceAtLeast(0f)
                                        val viewW = size.width.toFloat()
                                        val viewH = sh.toPx()
                                        focusPoint = localOffset
                                        focusLocked = true
                                        session.focusAt(
                                            x = localOffset.x,
                                            y = localOffset.y + maskPx,
                                            viewWidth = viewW,
                                            viewHeight = viewH,
                                            lock = true,
                                        )
                                    },
                                )
                            }
                        },
                ) {
                    if (gridEnabled && mode != CameraMode.PANO) {
                        ViewfinderGrid()
                        SpatialHorizonLevel()
                    }
                    if (focusLocked && focusPoint != null) {
                        val pt = focusPoint!!
                        val densityPx = LocalDensity.current
                        val halfReticle = with(densityPx) { 36.dp.toPx() }
                        val reticleW = with(densityPx) { 104.dp.toPx() }
                        val reticleH = with(densityPx) { 72.dp.toPx() }
                        val swPx = with(densityPx) { sw.toPx() }
                        val vhPx = with(densityPx) { viewfinderHeight.toPx() }
                        val maxOffsetX = (swPx - reticleW).coerceAtLeast(0f)
                        val maxOffsetY = (vhPx - reticleH).coerceAtLeast(0f)
                        val posX = (pt.x - halfReticle).coerceIn(0f, maxOffsetX)
                        val posY = (pt.y - halfReticle).coerceIn(0f, maxOffsetY)
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        posX.roundToInt(),
                                        posY.roundToInt(),
                                    )
                                },
                        ) {
                            FocusReticle(
                                exposureEv = exposureEv,
                                onExposureChange = { newEv ->
                                    exposureEv = newEv
                                    session.setExposureEv(newEv)
                                },
                            )
                        }
                    }
                }

                // Black letterbox bars top and bottom
                if (maskHeight > 0.dp && mode != CameraMode.PANO) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(maskHeight)
                            .align(Alignment.TopCenter)
                            .background(Color.Black),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(maskHeight)
                            .align(Alignment.BottomCenter)
                            .background(Color.Black),
                    )
                }

                if (mode == CameraMode.PANO) {
                    PanoramaGuideOverlay(
                        isCapturing = panoCapturing,
                        direction = panoDirection,
                        onDirectionChange = { panoDirection = it },
                        onCaptureComplete = {
                            panoCapturing = false
                            session.takePhoto(
                                state = state,
                                aspect = aspect,
                                onSaved = { uri ->
                                    message = "Panorama photo saved to Pictures/Kroma"
                                    uri?.let { u ->
                                        lastPhotoUri = u
                                        scope.launch { thumb = loadThumbnail(context, u) }
                                    }
                                },
                                onError = {
                                    panoCapturing = false
                                    message = it
                                },
                            )
                        },
                    )
                }
            }
        } else {
            Viewfinder(state)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera permission is required", color = Color.White)
            }
        }

        timerCountdown?.let { count ->
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .border(2.dp, Color(0xFFFFD60A), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$count",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Spacer(Modifier.height(6.dp))
            val shutterOptions = listOf("AUTO", "1/1000", "1/500", "1/250", "1/125", "1/60", "1/30", "1/2s")
            val evOptions = listOf(0f, 0.7f, 1.3f, 2.0f, -2.0f, -1.3f, -0.7f)
            val renderProBar = @Composable {
                ProControlBar(
                    shutterSpeed = proShutterSpeed,
                    onCycleShutterSpeed = {
                        val next = shutterOptions[(shutterOptions.indexOf(proShutterSpeed) + 1) % shutterOptions.size]
                        proShutterSpeed = next
                        session.setShutterSpeed(next)
                        message = "Pro Shutter: $next"
                    },
                    ev = exposureEv,
                    onCycleEv = {
                        val idx = evOptions.indexOfFirst { kotlin.math.abs(it - exposureEv) < 0.1f }
                        val next = evOptions[if (idx >= 0) (idx + 1) % evOptions.size else 0]
                        exposureEv = next
                        session.setExposureEv(next)
                        message = "Pro EV: ${if (next > 0) "+$next" else "$next"}"
                    },
                    focusMode = proFocusMode,
                    onCycleFocus = {
                        proFocusMode = if (proFocusMode == "AF") "MF" else "AF"
                        session.setFocusMode(proFocusMode)
                        message = "Pro Focus: $proFocusMode"
                    },
                    rawEnabled = rawEnabled,
                    onToggleRaw = {
                        rawEnabled = !rawEnabled
                        message = if (rawEnabled) "Pro RAW: ON (DNG Sensor Capture)" else "Pro RAW: OFF (Standard JPEG)"
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
            when (mode) {
                CameraMode.PORTRAIT -> {
                    PortraitTopBar(
                        aperture = portraitAperture,
                        onAperture = {
                            apertureOverlayOpen = !apertureOverlayOpen
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                CameraMode.NIGHT -> {
                    NightTopBar(
                        seconds = nightSeconds,
                        onToggleSeconds = {
                            nightSeconds = if (nightSeconds == 2) 5 else 2
                            message = "Night Sight: ${nightSeconds}s exposure"
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                CameraMode.PANO -> {
                    PanoTopBar(capturing = panoCapturing)
                    Spacer(Modifier.height(12.dp))
                }
                CameraMode.PRO -> {
                    renderProBar()
                }
                else -> {
                    if (proMode) {
                        renderProBar()
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    nightCapturing || panoCapturing -> Text(
                        "● CAPTURING",
                        color = Color(0xFFFF4D4D),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    focusLocked -> AeAfLockPill(
                        modifier = Modifier.clickable {
                            focusLocked = false
                            focusPoint = null
                            exposureEv = 0f
                            session.setExposureEv(0f)
                            session.cancelFocusAndMetering()
                        },
                    )
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
                val onShutterClick: () -> Unit = {
                    if (mode == CameraMode.NIGHT) {
                        if (!nightCapturing) {
                            nightCapturing = true
                            message = "Hold steady... capturing Night Sight"
                            scope.launch {
                                for (s in nightSeconds downTo 1) {
                                    timerCountdown = s
                                    delay(1000L)
                                }
                                timerCountdown = null
                                session.takePhoto(
                                    state = state,
                                    aspect = aspect,
                                    onSaved = { uri ->
                                        nightCapturing = false
                                        message = "Night Sight photo saved to Pictures/Kroma"
                                        uri?.let { u ->
                                            lastPhotoUri = u
                                            scope.launch { thumb = loadThumbnail(context, u) }
                                        }
                                    },
                                    onError = {
                                        nightCapturing = false
                                        message = it
                                    },
                                )
                            }
                        }
                    } else if (mode == CameraMode.PANO) {
                        if (!panoCapturing) {
                            panoCapturing = true
                            message = "Hold steady and pan slowly in the direction of the arrow"
                        } else {
                            panoCapturing = false
                            session.takePhoto(
                                state = state,
                                aspect = aspect,
                                onSaved = { uri ->
                                    message = "Panorama photo saved to Pictures/Kroma"
                                    uri?.let { u ->
                                        lastPhotoUri = u
                                        scope.launch { thumb = loadThumbnail(context, u) }
                                    }
                                },
                                onError = {
                                    panoCapturing = false
                                    message = it
                                },
                            )
                        }
                    } else {
                        val fire = {
                            val isRawPhoto = (mode == CameraMode.PRO && rawEnabled) || rawEnabled
                            val modeName = when (mode) {
                                CameraMode.PORTRAIT -> "Portrait (f/$portraitAperture)"
                                CameraMode.PANO -> "Panorama"
                                CameraMode.PRO -> if (rawEnabled) "Pro RAW DNG" else "Pro Photo"
                                else -> if (rawEnabled) "RAW DNG" else "Photo"
                            }
                            session.takePhoto(
                                state = state,
                                aspect = aspect,
                                isRaw = isRawPhoto,
                                onSaved = { uri ->
                                    message = "$modeName saved to Pictures/Kroma"
                                    uri?.let { u ->
                                        lastPhotoUri = u
                                        scope.launch { thumb = loadThumbnail(context, u) }
                                    }
                                },
                                onError = { message = it },
                            )
                        }
                        if (timer > 0) {
                            scope.launch {
                                for (t in timer downTo 1) {
                                    timerCountdown = t
                                    delay(1000L)
                                }
                                timerCountdown = null
                                fire()
                            }
                        } else {
                            fire()
                        }
                    }
                }

                if (stylesOpen) {
                    // Floating Shutter button row right above the Apple Styles card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (mode == CameraMode.PORTRAIT) {
                            ApertureButton(
                                active = apertureOverlayOpen,
                                onClick = { apertureOverlayOpen = !apertureOverlayOpen },
                            )
                        } else {
                            Spacer(Modifier.size(46.dp))
                        }
                        ShutterButton(
                            onClick = onShutterClick,
                            recording = nightCapturing || panoCapturing,
                        )
                        FlipButton(
                            onClick = {
                                session.flipLens()
                                focusLocked = false
                                focusPoint = null
                                exposureEv = 0f
                                session.setExposureEv(0f)
                                session.cancelFocusAndMetering()
                            },
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    AppleStylesSelector(
                        state = state,
                        onSelect = { state = it },
                        onCustomize = { calibrationOpen = true },
                        onClose = { stylesOpen = false },
                    )
                    Spacer(Modifier.height(8.dp))
                } else {
                    if (mode == CameraMode.PORTRAIT && apertureOverlayOpen) {
                        PortraitApertureOverlay(
                            aperture = portraitAperture,
                            onApertureChange = {
                                portraitAperture = it
                                message = "Portrait Depth: f/$portraitAperture"
                            },
                            onClose = {
                                apertureOverlayOpen = false
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    } else if (mode != CameraMode.PANO) {
                        // Zoom picker floats ABOVE the glass bar (hidden in PANO mode).
                        val isFront = session.lensFacing == CameraSelector.LENS_FACING_FRONT
                        LensPicker(
                            stops = zoomStops,
                            selected = lens,
                            liveZoom = liveZoom,
                            onSelect = { i ->
                                val z = zoomStops.getOrElse(i) { 1f }
                                if (!isFront && z < 1f) {
                                    session.useUltraWide(true)
                                } else {
                                    if (!isFront) session.useUltraWide(false)
                                    session.setZoom(z)
                                }
                                liveZoom = z
                                lens = i
                            },
                            onZoom = { z ->
                                val minZ = zoomStops.firstOrNull() ?: 1f
                                val maxZ = zoomStops.lastOrNull() ?: 1f
                                val clamped = z.coerceIn(minZ, maxZ)
                                if (!isFront) {
                                    if (clamped < 1f) {
                                        session.useUltraWide(true)
                                    } else {
                                        session.useUltraWide(false)
                                    }
                                }
                                liveZoom = clamped
                                session.setZoom(clamped)
                                lens = zoomStops.indices.minByOrNull {
                                    kotlin.math.abs(zoomStops[it] - clamped)
                                } ?: 0
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    // Dark glass bar: extends to the very bottom of the device (under the nav bar).
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color(0x40000000),
                                        Color(0xAA000000),
                                    ),
                                ),
                            )
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(top = 8.dp, bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 1. Shutter row: Aperture (Portrait only) | Shutter | StyleDots
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 26.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            if (mode == CameraMode.PORTRAIT) {
                                ApertureButton(
                                    active = apertureOverlayOpen,
                                    onClick = {
                                        apertureOverlayOpen = !apertureOverlayOpen
                                    },
                                )
                            } else {
                                Spacer(Modifier.size(46.dp))
                            }
                            ShutterButton(
                                onClick = onShutterClick,
                                recording = nightCapturing || panoCapturing,
                            )
                            StyleDotsButton(
                                active = menuOpen || stylesOpen,
                                onClick = { menuOpen = true },
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // 2. Mode selector row (BELOW the shutter): Thumbnail | ModeSelector | Flip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            ThumbnailButton(
                                bitmap = thumb,
                                swatch = styled(0.85f, 0.68f, 0.56f, state),
                                onClick = { openGallery(context, lastPhotoUri) },
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                ModeSelector(
                                    selected = mode,
                                    onSelect = { mode = it },
                                )
                            }

                            FlipButton(
                                onClick = {
                                    session.flipLens()
                                    focusLocked = false
                                    focusPoint = null
                                    exposureEv = 0f
                                    session.setExposureEv(0f)
                                    session.cancelFocusAndMetering()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = menuOpen,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        CameraSettingsGrid(
            flashValue = when (flash) {
                1 -> "On"
                2 -> "Auto"
                else -> "Off"
            },
            aspectValue = aspect.label,
            timerValue = if (timer == 0) "Off" else "${timer}s",
            exposureValue = if (ev == 0) "0.0" else String.format("%+.1f", ev.toFloat()),
            wbValue = WB_OPTIONS.getOrElse(wbIndex) { WB_OPTIONS[0] }.tempKelvin,
            rawEnabled = rawEnabled,
            gridEnabled = gridEnabled,
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
            onAspect = {
                val nextIdx = (CameraAspect.ALL.indexOf(aspect) + 1) % CameraAspect.ALL.size
                val next = CameraAspect.ALL[nextIdx]
                aspect = next
                focusLocked = false
                focusPoint = null
                exposureEv = 0f
                session.setExposureEv(0f)
                session.cancelFocusAndMetering()
                session.setAspectRatio(
                    if (next == CameraAspect.RATIO_4_3 || next == CameraAspect.RATIO_1_1) {
                        AspectRatio.RATIO_4_3
                    } else {
                        AspectRatio.RATIO_16_9
                    }
                )
                message = "Aspect: ${next.label}"
            },
            onAspectSelect = { selected ->
                aspect = selected
                focusLocked = false
                focusPoint = null
                exposureEv = 0f
                session.setExposureEv(0f)
                session.cancelFocusAndMetering()
                session.setAspectRatio(
                    if (selected == CameraAspect.RATIO_4_3 || selected == CameraAspect.RATIO_1_1) {
                        AspectRatio.RATIO_4_3
                    } else {
                        AspectRatio.RATIO_16_9
                    }
                )
                message = "Aspect: ${selected.label}"
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
            onStyles = {
                menuOpen = false
                stylesOpen = true
            },
            onWb = {
                wbIndex = (wbIndex + 1) % WB_OPTIONS.size
                val opt = WB_OPTIONS[wbIndex]
                session.setWhiteBalanceMode(opt.c2Mode)
                message = if (opt.c2Mode == CaptureRequest.CONTROL_AWB_MODE_AUTO) {
                    "White Balance: Auto (AWB)"
                } else {
                    "White Balance: ${opt.label} (${opt.tempKelvin})"
                }
            },
            onRaw = {
                rawEnabled = !rawEnabled
                message = if (rawEnabled) "Pro RAW: ON (DNG Sensor Capture)" else "Pro RAW: OFF (Standard JPEG)"
            },
            onGrid = {
                gridEnabled = !gridEnabled
                message = "Framing Grid: ${if (gridEnabled) "On" else "Off"}"
            },
            onPro = { proMode = !proMode },
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

/** Queries the most recent image in the device gallery/Pictures. */
private suspend fun fetchLatestPhoto(context: android.content.Context): Pair<Uri, Bitmap?>? =
    withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val id = cursor.getLong(idCol)
                    val itemUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val bmp = loadThumbnail(context, itemUri)
                    return@withContext itemUri to bmp
                }
            }
            null
        } catch (t: Throwable) {
            null
        }
    }

/** Opens the default system gallery app directly to the given photo (or gallery album). */
private fun openGallery(context: android.content.Context, uri: Uri?) {
    try {
        if (uri != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        try {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Exception) {
            Toast.makeText(context, "No gallery app found", Toast.LENGTH_SHORT).show()
        }
    }
}

