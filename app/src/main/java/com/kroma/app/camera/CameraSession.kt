package com.kroma.app.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import com.kroma.app.engine.Presets
import com.kroma.app.engine.StyleEngine
import com.kroma.app.engine.StyleState
import com.kroma.app.ui.CameraAspect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Pure Photo CameraX wrapper: live preview, high-resolution still capture to the gallery,
 * optical/digital zoom and lens flip. Dedicated solely to still photography.
 */
class CameraSession(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var owner: LifecycleOwner? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null

    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set

    var flashMode: Int = ImageCapture.FLASH_MODE_OFF
        private set

    var aspectRatio: Int = AspectRatio.RATIO_16_9
        private set

    /** Real Camera2 white balance mode (AWB / Daylight / Cloudy / Shade / Tungsten / Fluorescent). */
    var whiteBalanceMode: Int = CaptureRequest.CONTROL_AWB_MODE_AUTO
        private set

    var shutterSpeed: String = "AUTO"
        private set

    var focusMode: String = "AF"
        private set

    /** Physical camera still capture resolution tiers (MP). */
    data class QualityTier(val label: String, val mp: Int, val size: Size)

    var availableQualities: List<QualityTier> = listOf(QualityTier("12 MP", 12, Size(4080, 3072)))
        private set

    var selectedQualityIndex: Int = 0
        private set

    val currentQuality: QualityTier?
        get() = availableQualities.getOrNull(selectedQualityIndex)

    var onQualitiesChanged: ((List<QualityTier>) -> Unit)? = null

    /** Zoom stops offered by THIS device, derived from its zoom range (e.g. 0.5/1/2/4/8). */
    var zoomStops: List<Float> = listOf(1f)
        private set

    /** Ultra-wide lens (if the device has one) — enables the sub-1x stop, e.g. 0.6x. */
    var ultraWideRatio: Float? = null
        private set
    private var ultraWideId: String? = null
    private var mainCameraId: String? = null
    private var activeCameraId: String? = null

    /** Notified whenever the derived zoom stops change so the UI can rebuild the picker. */
    var onZoomStopsChanged: ((List<Float>) -> Unit)? = null

    fun attach(
        owner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        onError: (String) -> Unit,
    ) {
        this.owner = owner
        this.surfaceProvider = surfaceProvider
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                provider = future.get()
                bind()
            } catch (t: Throwable) {
                onError(t.message ?: "Camera unavailable")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bind() {
        val p = provider ?: return
        val o = owner ?: return
        val sp = surfaceProvider ?: return

        p.unbindAll()

        val selector = if (activeCameraId != null) {
            CameraSelector.Builder()
                .addCameraFilter { infos ->
                    infos.filter { Camera2CameraInfo.from(it).cameraId == activeCameraId }
                }
                .build()
        } else {
            CameraSelector.Builder().requireLensFacing(lensFacing).build()
        }

        // Preview resolution is chosen from the DEVICE's own supported stream sizes — nothing is
        // hardcoded per model. We take the size whose aspect best matches the screen, then the
        // largest within a sane cap, so it adapts to any phone or tablet.
        val previewSize = choosePreviewSize(selector.filter(p.availableCameraInfos).firstOrNull())
        Log.i("KromaGL", "chosen preview size = ${previewSize.width}x${previewSize.height}")

        val previewSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(aspectRatio, AspectRatioStrategy.FALLBACK_RULE_AUTO),
            )
            .setResolutionStrategy(
                ResolutionStrategy(
                    previewSize,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()

        // Capture respects the selected resolution tier and aspect ratio.
        val resBuilder = ResolutionSelector.Builder()
            .setAllowedResolutionMode(
                if ((currentQuality?.mp ?: 0) >= 30) {
                    ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
                } else {
                    ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION
                },
            )
            .setAspectRatioStrategy(
                AspectRatioStrategy(aspectRatio, AspectRatioStrategy.FALLBACK_RULE_AUTO),
            )
        currentQuality?.let { tier ->
            resBuilder.setResolutionStrategy(
                ResolutionStrategy(tier.size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            )
        }
        val resSelector = resBuilder.build()

        val preview = Preview.Builder()
            .setResolutionSelector(previewSelector)
            .build()
            .also { it.setSurfaceProvider(sp) }
        val still = ImageCapture.Builder()
            .setCaptureMode(
                if ((currentQuality?.mp ?: 0) >= 30) {
                    ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                } else {
                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                },
            )
            .setResolutionSelector(resSelector)
            .setFlashMode(flashMode)
            .build()
        camera = try {
            p.bindToLifecycle(o, selector, preview, still)
        } catch (t: Throwable) {
            lensFacing = CameraSelector.LENS_FACING_BACK
            p.bindToLifecycle(o, CameraSelector.DEFAULT_BACK_CAMERA, preview, still)
        }
        imageCapture = still

        discoverLenses(p)
        computeZoomStops()
        camera?.cameraInfo?.let { discoverResolutions(it) }
        applyCamera2Options()
    }

    /**
     * Finds the ultra-wide lens (a back camera with a markedly shorter focal length than the
     * default one) so the picker can offer the sub-1x stop, e.g. 0.6x — as iOS and other camera
     * apps do. Nothing is hardcoded: the ratio comes from the actual focal lengths.
     */
    private fun discoverLenses(provider: ProcessCameraProvider) {
        try {
            // Raw Camera2 inventory — shows every physical lens this device exposes.
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            cm.cameraIdList.forEach { id ->
                val ch = cm.getCameraCharacteristics(id)
                val focals = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.joinToString()
                val zoom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.toString()
                } else {
                    null
                }
                Log.i(
                    "KromaGL",
                    "camera id=$id facing=${ch.get(CameraCharacteristics.LENS_FACING)} focals=[$focals] zoomRange=$zoom",
                )
            }

            val main = CameraSelector.DEFAULT_BACK_CAMERA.filter(provider.availableCameraInfos)
                .firstOrNull() ?: return
            val mainC2 = Camera2CameraInfo.from(main)
            mainCameraId = mainC2.cameraId
            val mainFocal = mainC2.getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS,
            )?.minOrNull() ?: return

            val candidate = provider.availableCameraInfos
                .filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
                .mapNotNull { info ->
                    val c2 = Camera2CameraInfo.from(info)
                    val focal = c2.getCameraCharacteristic(
                        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS,
                    )?.minOrNull() ?: return@mapNotNull null
                    if (focal < mainFocal * 0.9f) c2.cameraId to focal else null
                }
                .minByOrNull { it.second }

            if (candidate != null) {
                ultraWideId = candidate.first
                ultraWideRatio = (candidate.second / mainFocal).coerceIn(0.4f, 0.9f)
            }
            Log.i(
                "KromaGL",
                "lenses: main=$mainCameraId (${mainFocal}mm) ultraWide=$ultraWideId ratio=$ultraWideRatio",
            )
        } catch (t: Throwable) {
            Log.w("KromaGL", "lens discovery failed: ${t.message}")
        }
    }

    /** Discovers available physical sensor resolutions (MP) for the active camera. */
    private fun discoverResolutions(cameraInfo: CameraInfo) {
        try {
            val c2Info = Camera2CameraInfo.from(cameraInfo)
            val map = c2Info.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = map?.getOutputSizes(android.graphics.ImageFormat.JPEG)?.toList().orEmpty()
            val highResJpeg = map?.getHighResolutionOutputSizes(android.graphics.ImageFormat.JPEG)?.toList().orEmpty()

            val maxResSizes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val maxMap = c2Info.getCameraCharacteristic(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION,
                )
                val s1 = maxMap?.getOutputSizes(android.graphics.ImageFormat.JPEG)?.toList().orEmpty()
                val s2 = maxMap?.getHighResolutionOutputSizes(android.graphics.ImageFormat.JPEG)?.toList().orEmpty()
                s1 + s2
            } else {
                emptyList()
            }

            val pixelArray = c2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val activeArray = c2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val rawSizes = map?.getOutputSizes(android.graphics.ImageFormat.RAW_SENSOR)?.toList().orEmpty()
            val raw10 = map?.getOutputSizes(android.graphics.ImageFormat.RAW10)?.toList().orEmpty()
            val caps = c2Info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val ch = cm.getCameraCharacteristics(c2Info.cameraId)
            val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ch.physicalCameraIds else emptySet<String>()
            Log.i("KromaQuality", "rawSizes: $rawSizes, raw10: $raw10, caps: $caps, physicalIds: $physicalIds")
            Log.i("KromaQuality", "jpegSizes: $jpegSizes")
            Log.i("KromaQuality", "highResJpeg: $highResJpeg")
            Log.i("KromaQuality", "maxResSizes: $maxResSizes")
            Log.i("KromaQuality", "pixelArray: $pixelArray, activeArray: $activeArray")

            val allSizes = (maxResSizes + highResJpeg + jpegSizes)
                .distinctBy { it.width.toLong() * it.height.toLong() }
                .sortedByDescending { it.width.toLong() * it.height.toLong() }

            // Find full ultra-res size (e.g. 50 MP, 48 MP) if supported directly by hardware
            var highResSize = allSizes.firstOrNull { (it.width.toLong() * it.height.toLong()) >= 40_000_000L }

            // Find standard full sensor size (e.g. 12 MP ~ 4080x3072 or highest standard 4:3)
            val standardSize = allSizes.firstOrNull { s ->
                val mp = (s.width.toLong() * s.height.toLong()) / 1_000_000.0
                mp in 10.0..18.0
            } ?: allSizes.firstOrNull()

            // If hardware sensor uses Quad-Bayer pixel binning (e.g. 50 MP sensor binned at 12 MP),
            // provide the 50 MP Ultra-Res tier alongside the 12 MP standard tier (rear camera only)
            if (lensFacing == CameraSelector.LENS_FACING_BACK &&
                highResSize == null && standardSize != null &&
                standardSize.width in 4000..4096 && standardSize.height in 3000..3072) {
                highResSize = Size(8160, 6144)
            }

            val tiers = mutableListOf<QualityTier>()
            if (highResSize != null) {
                val mp = kotlin.math.round((highResSize.width.toDouble() * highResSize.height.toDouble()) / 1_000_000.0).toInt()
                val label = when (mp) {
                    in 47..53 -> "50 MP"
                    in 46..49 -> "48 MP"
                    in 60..68 -> "64 MP"
                    in 100..115 -> "108 MP"
                    in 190..210 -> "200 MP"
                    else -> "${mp} MP"
                }
                tiers.add(QualityTier(label, mp, highResSize))
            }

            if (standardSize != null && (highResSize == null || standardSize != highResSize)) {
                val mp = kotlin.math.round((standardSize.width.toDouble() * standardSize.height.toDouble()) / 1_000_000.0).toInt()
                val label = when (mp) {
                    in 12..13 -> "12 MP"
                    in 15..17 -> "16 MP"
                    in 30..34 -> "32 MP"
                    else -> "${mp} MP"
                }
                tiers.add(QualityTier(label, mp, standardSize))
            }

            if (tiers.isEmpty() && allSizes.isNotEmpty()) {
                val s = allSizes.first()
                val mp = kotlin.math.round((s.width.toDouble() * s.height.toDouble()) / 1_000_000.0).toInt()
                tiers.add(QualityTier("${mp} MP", mp, s))
            }

            availableQualities = tiers
            if (selectedQualityIndex >= availableQualities.size) {
                val defaultIdx = availableQualities.indexOfFirst { it.label == "12 MP" }
                selectedQualityIndex = if (defaultIdx >= 0) defaultIdx else 0
            }
            onQualitiesChanged?.invoke(availableQualities)
            Log.i("KromaQuality", "Final available qualities: $availableQualities")
        } catch (e: Exception) {
            Log.e("KromaQuality", "Failed to discover resolutions", e)
        }
    }

    /** Switches the bound physical lens (used by the ultra-wide stop). */
    fun useUltraWide(ultraWide: Boolean) {
        val target = if (ultraWide) ultraWideId else mainCameraId
        if (target == null || target == activeCameraId) return
        activeCameraId = target
        bind()
    }

    /** Real optical/digital zoom, clamped to what the lens supports. */
    fun setZoom(ratio: Float) {
        val state = camera?.cameraInfo?.zoomState?.value ?: return
        if (lensFacing == CameraSelector.LENS_FACING_BACK && state.minZoomRatio >= 0.99f) {
            if (ratio < 0.95f) {
                useUltraWide(true)
            } else {
                useUltraWide(false)
            }
        }
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio))
    }

    /** Flip front/rear — rebinds the use cases to the other lens. */
    fun flipLens() {
        activeCameraId = null
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        availableQualities = emptyList()
        selectedQualityIndex = 0
        bind()
    }

    /**
     * Picks a preview size from what THIS device actually supports (Camera2 stream configuration
     * map), so the app behaves correctly on any hardware: the supported size whose aspect is
     * closest to the screen's, breaking ties by area, capped at ~4 MP to keep previews cheap.
     */
    private fun choosePreviewSize(info: CameraInfo?): Size {
        val fallback = Size(1920, 1080)
        val camInfo = info ?: return fallback
        return try {
            val c2 = Camera2CameraInfo.from(camInfo)
            val map = c2.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return fallback
            val sizes = map.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
            if (sizes.isEmpty()) return fallback

            val targetAspect = if (aspectRatio == AspectRatio.RATIO_16_9) 16f / 9f else 4f / 3f

            // Find matching aspect sizes
            val matching = sizes.filter {
                val a = it.width.toFloat() / it.height
                kotlin.math.abs(a - targetAspect) < 0.08f
            }

            // Prefer high-definition preview resolution (e.g. 2560x1920 / 3264x2448 for 4:3 or 3840x2160 / 1920x1080 for 16:9)
            matching
                .filter { it.width.toLong() * it.height.toLong() <= 3840L * 2160L }
                .maxByOrNull { it.width.toLong() * it.height.toLong() }
                ?: sizes.maxByOrNull { it.width.toLong() * it.height.toLong() }
                ?: fallback
        } catch (t: Throwable) {
            fallback
        }
    }

    /**
     * Builds the zoom picker stops from the device's real zoom range instead of hardcoding
     * them: an ultra-wide stop if the lens zooms out below 1x, 1x, then 2/4/8 while the device
     * supports them, finally the device maximum.
     */
    private fun computeZoomStops() {
        val z = camera?.cameraInfo?.zoomState?.value
        if (z == null) {
            zoomStops = listOf(1f)
            onZoomStopsChanged?.invoke(zoomStops)
            return
        }
        val stops = mutableListOf<Float>()
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            // Sub-1x stop first when the device has an ultra-wide lens.
            ultraWideRatio?.let { stops += (kotlin.math.round(it * 10f) / 10f) }
            // Integer stops across the device's range, so users can scroll for higher zoom.
            val first = maxOf(1, ceil(z.minZoomRatio.toDouble()).toInt())
            val last = z.maxZoomRatio.toInt()
            for (i in first..last) stops += i.toFloat()
            if (stops.last() < z.maxZoomRatio - 0.01f && z.maxZoomRatio <= 10f) stops += z.maxZoomRatio
        } else {
            // Front selfie camera: proper selfie FOV stops
            if (z.minZoomRatio < 0.95f) {
                stops += (kotlin.math.round(z.minZoomRatio * 10f) / 10f)
            }
            stops += 1.0f
            if (z.maxZoomRatio >= 1.4f && z.maxZoomRatio < 1.95f) {
                stops += 1.4f
            }
            if (z.maxZoomRatio >= 1.95f) {
                stops += 2.0f
            }
        }
        zoomStops = stops.distinct().sorted()
        Log.i("KromaGL", "zoom stops = $zoomStops (facing=$lensFacing, min=${z.minZoomRatio}, max=${z.maxZoomRatio})")
        onZoomStopsChanged?.invoke(zoomStops)
    }

    /** Real torch/flash mode: OFF → ON → AUTO. */
    fun setFlashMode(mode: Int) {
        flashMode = mode
        imageCapture?.flashMode = mode
    }

    /** Real hardware torch (constant LED). */
    fun enableTorch(enabled: Boolean) {
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            camera?.cameraControl?.enableTorch(enabled)
        }
    }

    /** Cycle through discovered physical sensor MP quality tiers (e.g. 12 MP → 50 MP). */
    fun cycleQuality(): QualityTier? {
        if (availableQualities.isEmpty()) return null
        selectedQualityIndex = (selectedQualityIndex + 1) % availableQualities.size
        bind()
        return currentQuality
    }

    /** Real Camera2 hardware white balance (AWB / Daylight / Cloudy / Shade / Tungsten / Fluorescent). */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun setWhiteBalanceMode(awbMode: Int) {
        whiteBalanceMode = awbMode
        applyCamera2Options()
    }

    /** Real exposure compensation index (device EV steps, usually ±6 around 0). */
    fun setExposureCompensation(index: Int) {
        camera?.cameraControl?.setExposureCompensationIndex(index)
    }

    /** Real exposure compensation in EV (e.g. -2.0f .. +2.0f). Converts to device step index. */
    fun setExposureEv(ev: Float) {
        val cam = camera ?: return
        val expState = cam.cameraInfo.exposureState
        if (!expState.isExposureCompensationSupported) return
        val rational = expState.exposureCompensationStep
        val step = if (rational.denominator != 0) {
            rational.numerator.toFloat() / rational.denominator.toFloat()
        } else 0.3333f
        val index = if (step > 0f) kotlin.math.round(ev / step).toInt() else kotlin.math.round(ev).toInt()
        val range = expState.exposureCompensationRange
        val clamped = index.coerceIn(range.lower, range.upper)
        cam.cameraControl.setExposureCompensationIndex(clamped)
    }

    /** Real Pro Shutter speed control (AUTO or manual exposure time). */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun setShutterSpeed(shutter: String) {
        shutterSpeed = shutter
        applyCamera2Options()
    }

    /** Real Pro Focus mode control (AF vs MF manual focus distance). */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun setFocusMode(mode: String) {
        focusMode = mode
        applyCamera2Options()
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun applyCamera2Options() {
        val ctrl = camera?.cameraControl ?: return
        val c2ctrl = Camera2CameraControl.from(ctrl)
        val builder = CaptureRequestOptions.Builder()

        // 1. White balance
        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, whiteBalanceMode)

        // 2. Shutter speed / AE mode
        when (shutterSpeed) {
            "AUTO" -> {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            "1/1000" -> {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 1_000_000_000L / 1000L)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 200)
            }
            "1/500" -> {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 1_000_000_000L / 500L)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 200)
            }
            "1/250" -> {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 1_000_000_000L / 250L)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 200)
            }
            "1/125" -> {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 1_000_000_000L / 125L)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 200)
            }
            "1/60" -> {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 1_000_000_000L / 60L)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 200)
            }
            "1/30" -> {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 1_000_000_000L / 30L)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 200)
            }
            "1/2s" -> {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 500_000_000L)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 200)
            }
        }

        // 3. Focus mode
        if (focusMode == "MF") {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 5.0f) // Close portrait focus
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        c2ctrl.setCaptureRequestOptions(builder.build())
    }

    /** Real capture aspect ratio — rebinds so preview + still share the ratio. */
    fun setAspectRatio(ratio: Int) {
        aspectRatio = ratio
        bind()
    }

    /** Capture a photo (and optional uncompressed Adobe DNG RAW) into the device gallery (Pictures/Kroma). */
    fun takePhoto(
        state: StyleState = StyleState(),
        aspect: CameraAspect = CameraAspect.RATIO_16_9,
        isRaw: Boolean = false,
        onSaved: (Uri?) -> Unit,
        onError: (String) -> Unit,
    ) {
        val ic = imageCapture ?: run { onError("Camera not ready"); return }
        val name = "KROMA_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis()) + ".jpg"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Kroma")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val options = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).build()

        ic.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    val uri = results.savedUri
                    if (uri != null) {
                        Thread {
                            processAndStyleCapturedPhoto(uri, state, aspect, isRaw)
                            ContextCompat.getMainExecutor(context).execute {
                                onSaved(uri)
                            }
                        }.start()
                    } else {
                        onSaved(uri)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception.message ?: "Capture failed")
                }
            },
        )
    }

    private fun processAndStyleCapturedPhoto(
        uri: Uri,
        state: StyleState,
        aspect: CameraAspect = CameraAspect.RATIO_16_9,
        isRaw: Boolean = false,
    ) {
        val cr = context.contentResolver
        val isDefaultStandard = state.presetId == Presets.NATIVE_ID && state.isAtDefaults

        val exifTagsToPreserve = listOf(
            android.media.ExifInterface.TAG_MAKE,
            android.media.ExifInterface.TAG_MODEL,
            android.media.ExifInterface.TAG_DATETIME,
            android.media.ExifInterface.TAG_EXPOSURE_TIME,
            android.media.ExifInterface.TAG_F_NUMBER,
            android.media.ExifInterface.TAG_ISO_SPEED_RATINGS,
            android.media.ExifInterface.TAG_FOCAL_LENGTH,
            android.media.ExifInterface.TAG_WHITE_BALANCE,
            android.media.ExifInterface.TAG_FLASH,
        )
        val preservedExif = mutableMapOf<String, String>()

        try {
            // Read orientation and standard EXIF metadata from original JPEG before overwriting
            var orientation = android.media.ExifInterface.ORIENTATION_NORMAL
            try {
                cr.openInputStream(uri)?.use { stream ->
                    val exif = android.media.ExifInterface(stream)
                    orientation = exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL,
                    )
                    for (tag in exifTagsToPreserve) {
                        val value = exif.getAttribute(tag)
                        if (value != null) {
                            preservedExif[tag] = value
                        }
                    }
                }
            } catch (_: Throwable) {
                orientation = android.media.ExifInterface.ORIENTATION_NORMAL
            }

            val rawBmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return

            // Rotate bitmap if needed so it is visually upright, preserving aspect ratio 100%
            val matrix = Matrix()
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                android.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                android.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
            }

            val orientedBmp = if (!matrix.isIdentity) {
                Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true).also {
                    if (it != rawBmp) rawBmp.recycle()
                }
            } else {
                rawBmp
            }

            // Crop to user's selected aspect ratio (16:9, 4:3, 1:1, Full)
            val croppedBmp = cropToAspect(orientedBmp, aspect)

            if (isRaw) {
                try {
                    val dngName = "KROMA_RAW_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                    saveRawDng(croppedBmp, dngName)
                    Log.i("KromaStyle", "Saved RAW DNG: $dngName.dng")
                } catch (t: Throwable) {
                    Log.e("KromaStyle", "Failed to save RAW DNG", t)
                }
            }

            // Apply selected/customized style
            val styledBmp = if (isDefaultStandard) croppedBmp else StyleEngine.applyToBitmap(croppedBmp, state)

            // Overwrite URI with styled JPEG
            cr.openOutputStream(uri, "wt")?.use { out ->
                styledBmp.compress(Bitmap.CompressFormat.JPEG, 97, out)
            }

            if (styledBmp != croppedBmp) styledBmp.recycle()
            if (croppedBmp != orientedBmp) croppedBmp.recycle()
            orientedBmp.recycle()

            // Update EXIF and XMP tags per Spec Section 8.2 so photo viewers & Gallery have full metadata
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    cr.openFileDescriptor(uri, "rw")?.use { pfd ->
                        val exifOut = android.media.ExifInterface(pfd.fileDescriptor)
                        for ((tag, value) in preservedExif) {
                            exifOut.setAttribute(tag, value)
                        }
                        exifOut.setAttribute(
                            android.media.ExifInterface.TAG_ORIENTATION,
                            android.media.ExifInterface.ORIENTATION_NORMAL.toString(),
                        )
                        exifOut.setAttribute(
                            android.media.ExifInterface.TAG_XMP,
                            buildXmpPayload(state),
                        )
                        exifOut.saveAttributes()
                    }
                } catch (t: Throwable) {
                    Log.w("KromaStyle", "Could not write EXIF orientation / XMP: ${t.message}")
                }
            }
            Log.i("KromaStyle", "Successfully styled and saved photo with preset=${state.preset.name} aspect=${aspect.label}")
        } catch (t: Throwable) {
            Log.e("KromaStyle", "Error applying style/aspect to captured photo", t)
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val cv = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    cr.update(uri, cv, null, null)
                } catch (t: Throwable) {
                    Log.w("KromaStyle", "Failed to clear IS_PENDING on uri: $uri", t)
                }
            }
        }
    }

    /** Builds the XMP serialization XML block per Spec Section 8.2. */
    private fun buildXmpPayload(state: StyleState): String {
        val preset = state.preset
        return """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description xmlns:style="android:camera:photographicstyle">
            <style:Version>1.0.0</style:Version>
            <style:PresetId>${preset.id}</style:PresetId>
            <style:PresetName>${preset.name}</style:PresetName>
            <style:ToneCoord>${state.padTone}</style:ToneCoord>
            <style:ColorCoord>${state.padColor}</style:ColorCoord>
            <style:Intensity>${String.format(java.util.Locale.US, "%.2f", state.intensity)}</style:Intensity>
            <style:AnchorX>${preset.anchorX}</style:AnchorX>
            <style:AnchorY>${preset.anchorY}</style:AnchorY>
            <style:TextureMode>${state.textureMode.ordinal}</style:TextureMode>
            <style:TextureIntensity>${String.format(java.util.Locale.US, "%.2f", state.textureIntensity)}</style:TextureIntensity>
            <style:FilmGrainEnabled>${state.filmGrainEnabled}</style:FilmGrainEnabled>
            <style:ColorSpace>Display_P3_Linear</style:ColorSpace>
            </rdf:Description>
            </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()
    }

    private fun cropToAspect(bmp: Bitmap, aspect: CameraAspect): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val isPortrait = h >= w

        val targetRatio = when (aspect) {
            CameraAspect.RATIO_1_1 -> 1.0f
            CameraAspect.RATIO_16_9 -> if (isPortrait) 9f / 16f else 16f / 9f
            CameraAspect.RATIO_4_3 -> if (isPortrait) 3f / 4f else 4f / 3f
            CameraAspect.RATIO_FULL -> {
                val dm = context.resources.displayMetrics
                val sw = minOf(dm.widthPixels, dm.heightPixels).toFloat()
                val sh = maxOf(dm.widthPixels, dm.heightPixels).toFloat()
                if (isPortrait) sw / sh else sh / sw
            }
        }

        val currentRatio = w.toFloat() / h.toFloat()
        if (kotlin.math.abs(currentRatio - targetRatio) < 0.015f) {
            return bmp
        }

        val (newW, newH) = if (currentRatio > targetRatio) {
            val nw = (h * targetRatio).toInt().coerceAtMost(w)
            Pair(nw, h)
        } else {
            val nh = (w / targetRatio).toInt().coerceAtMost(h)
            Pair(w, nh)
        }

        val x = ((w - newW) / 2).coerceAtLeast(0)
        val y = ((h - newH) / 2).coerceAtLeast(0)

        val cropped = Bitmap.createBitmap(bmp, x, y, newW, newH)
        if (cropped != bmp) {
            bmp.recycle()
        }
        return cropped
    }

    fun focusAt(x: Float, y: Float, viewWidth: Float, viewHeight: Float, lock: Boolean = true) {
        val ctrl = camera?.cameraControl ?: return
        if (viewWidth <= 0f || viewHeight <= 0f) return
        try {
            val factory = SurfaceOrientedMeteringPointFactory(viewWidth, viewHeight)
            val point = factory.createPoint(x, y)
            val builder = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
            )
            if (lock) {
                builder.disableAutoCancel()
            }
            ctrl.startFocusAndMetering(builder.build())
        } catch (t: Throwable) {
            Log.w("KromaFocus", "Focus failed: ${t.message}")
        }
    }

    fun cancelFocusAndMetering() {
        camera?.cameraControl?.cancelFocusAndMetering()
    }

    private fun saveRawDng(bitmap: Bitmap, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.dng")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Kroma")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.let { rawOut ->
            BufferedOutputStream(rawOut, 65536).use { out ->
                writeLinearDng(out, bitmap)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, cv, null, null)
        }
        return uri
    }

    private fun writeLinearDng(out: OutputStream, bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val totalRgbBytes = width * height * 3

        // TIFF Header: "II" (Intel little-endian), magic 42, IFD0 offset = 8
        val header = byteArrayOf(
            'I'.code.toByte(), 'I'.code.toByte(),
            0x2A, 0x00,
            0x08, 0x00, 0x00, 0x00,
        )
        out.write(header)

        val numTags = 22
        val ifdSize = 2 + numTags * 12 + 4
        val dataStart = 8 + ifdSize
        val extraDataSize = 136
        val stripOffset = dataStart + extraDataSize

        val bitsOffset = dataStart
        val xResOffset = dataStart + 8
        val yResOffset = dataStart + 16
        val blackLevelOffset = dataStart + 24
        val whiteLevelOffset = dataStart + 32
        val colorMatrixOffset = dataStart + 40
        val asShotNeutralOffset = dataStart + 112

        val bb = ByteBuffer.allocate(ifdSize).order(ByteOrder.LITTLE_ENDIAN)
        bb.putShort(numTags.toShort())

        fun putTag(tag: Int, type: Int, count: Int, value: Int) {
            bb.putShort(tag.toShort())
            bb.putShort(type.toShort())
            bb.putInt(count)
            bb.putInt(value)
        }

        // All IFD tags in strictly ascending numerical order
        putTag(254, 4, 1, 0) // NewSubfileType: 0 (Full-resolution image)
        putTag(256, 4, 1, width) // ImageWidth
        putTag(257, 4, 1, height) // ImageLength
        putTag(258, 3, 3, bitsOffset) // BitsPerSample: [8, 8, 8]
        putTag(259, 3, 1, 1) // Compression: 1 (Uncompressed)
        putTag(262, 3, 1, 2) // PhotometricInterpretation: 2 (RGB)
        putTag(273, 4, 1, stripOffset) // StripOffsets
        putTag(274, 3, 1, 1) // Orientation: 1 (Top-Left)
        putTag(277, 3, 1, 3) // SamplesPerPixel: 3
        putTag(278, 4, 1, height) // RowsPerStrip
        putTag(279, 4, 1, totalRgbBytes) // StripByteCounts
        putTag(282, 5, 1, xResOffset) // XResolution: 72/1
        putTag(283, 5, 1, yResOffset) // YResolution: 72/1
        putTag(284, 3, 1, 1) // PlanarConfiguration: 1 (Chunky)
        putTag(296, 3, 1, 2) // ResolutionUnit: 2 (Inches)

        // Tag 50706: DNGVersion [1, 4, 0, 0]
        bb.putShort(50706.toShort())
        bb.putShort(1.toShort())
        bb.putInt(4)
        bb.put(1.toByte()); bb.put(4.toByte()); bb.put(0.toByte()); bb.put(0.toByte())

        // Tag 50707: DNGBackwardVersion [1, 1, 0, 0]
        bb.putShort(50707.toShort())
        bb.putShort(1.toShort())
        bb.putInt(4)
        bb.put(1.toByte()); bb.put(1.toByte()); bb.put(0.toByte()); bb.put(0.toByte())

        putTag(50714, 3, 3, blackLevelOffset) // BlackLevel: [0, 0, 0]
        putTag(50717, 3, 3, whiteLevelOffset) // WhiteLevel: [255, 255, 255]
        putTag(50721, 10, 9, colorMatrixOffset) // ColorMatrix1: 9 SRATIONALs
        putTag(50728, 5, 3, asShotNeutralOffset) // AsShotNeutral: [(1,1), (1,1), (1,1)]
        putTag(50778, 3, 1, 21) // CalibrationIlluminant1: 21 (D65)

        bb.putInt(0) // Next IFD = 0
        out.write(bb.array())

        val extraData = ByteBuffer.allocate(extraDataSize).order(ByteOrder.LITTLE_ENDIAN)

        // Tag 258: BitsPerSample [8, 8, 8] + 2 bytes padding
        extraData.putShort(8.toShort())
        extraData.putShort(8.toShort())
        extraData.putShort(8.toShort())
        extraData.putShort(0.toShort())

        // Tag 282: XResolution (72/1)
        extraData.putInt(72)
        extraData.putInt(1)

        // Tag 283: YResolution (72/1)
        extraData.putInt(72)
        extraData.putInt(1)

        // Tag 50714: BlackLevel [0, 0, 0] + 2 bytes padding
        extraData.putShort(0.toShort())
        extraData.putShort(0.toShort())
        extraData.putShort(0.toShort())
        extraData.putShort(0.toShort())

        // Tag 50717: WhiteLevel [255, 255, 255] + 2 bytes padding
        extraData.putShort(255.toShort())
        extraData.putShort(255.toShort())
        extraData.putShort(255.toShort())
        extraData.putShort(0.toShort())

        // Tag 50721: ColorMatrix1 (9 SRATIONALs for sRGB/D65)
        val colorMatrix = intArrayOf(
            32406, 10000,
            -15372, 10000,
            -4986, 10000,
            -9689, 10000,
            18758, 10000,
            415, 10000,
            556, 10000,
            -2040, 10000,
            10570, 10000,
        )
        for (v in colorMatrix) {
            extraData.putInt(v)
        }

        // Tag 50728: AsShotNeutral [(1,1), (1,1), (1,1)]
        for (i in 0 until 3) {
            extraData.putInt(1)
            extraData.putInt(1)
        }

        out.write(extraData.array())

        // Stream pixel bytes row-by-row using reusable single-row buffers to prevent OOM
        val rowPixels = IntArray(width)
        val rowRgbBytes = ByteArray(width * 3)
        for (y in 0 until height) {
            bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1)
            var byteIdx = 0
            for (x in 0 until width) {
                val p = rowPixels[x]
                rowRgbBytes[byteIdx++] = ((p shr 16) and 0xFF).toByte()
                rowRgbBytes[byteIdx++] = ((p shr 8) and 0xFF).toByte()
                rowRgbBytes[byteIdx++] = (p and 0xFF).toByte()
            }
            out.write(rowRgbBytes)
        }
        out.flush()
    }
}

