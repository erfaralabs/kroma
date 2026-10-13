package com.kroma.app.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Thin, real CameraX wrapper: live preview, still capture to the gallery, video recording,
 * zoom and lens flip. Everything drives the actual camera hardware — no mocks.
 */
class CameraSession(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recorder: Recorder? = null
    private var videoEnabled = false
    private var activeRecording: Recording? = null
    private var owner: LifecycleOwner? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null

    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set

    var flashMode: Int = ImageCapture.FLASH_MODE_OFF
        private set

    var aspectRatio: Int = AspectRatio.RATIO_4_3
        private set

    val isRecording: Boolean get() = activeRecording != null

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

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview resolution is chosen from the DEVICE's own supported stream sizes — nothing is
        // hardcoded per model. We take the size whose aspect best matches the screen, then the
        // largest within a sane cap, so it adapts to any phone or tablet.
        val previewSize = choosePreviewSize(selector.filter(p.availableCameraInfos).firstOrNull())
        Log.i("KromaGL", "chosen preview size = ${previewSize.width}x${previewSize.height}")

        val previewSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    previewSize,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()

        // Capture keeps the user-selected aspect ratio at full resolution.
        val resSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(aspectRatio, AspectRatioStrategy.FALLBACK_RULE_AUTO),
            )
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(previewSelector)
            .build()
            .also { it.setSurfaceProvider(sp) }
        val still = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resSelector)
            .setFlashMode(flashMode)
            .build()
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        this.recorder = recorder

        // Bind only preview + still by default. Binding video as well makes CameraX downgrade
        // the stream combination (we were getting a low-res 1440x1088 preview); video is added
        // on demand in [toggleRecording].
        val useCases = mutableListOf<androidx.camera.core.UseCase>(preview, still)
        if (videoEnabled) {
            useCases += VideoCapture.withOutput(recorder)
        }

        camera = try {
            p.bindToLifecycle(o, selector, *useCases.toTypedArray())
        } catch (t: Throwable) {
            lensFacing = CameraSelector.LENS_FACING_BACK
            p.bindToLifecycle(o, CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray())
        }
        imageCapture = still
        videoCapture = if (videoEnabled) VideoCapture.withOutput(recorder) else null
    }

    /** Real optical/digital zoom, clamped to what the lens supports. */
    fun setZoom(ratio: Float) {
        val state = camera?.cameraInfo?.zoomState?.value ?: return
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio))
    }

    /** Flip front/rear — rebinds the use cases to the other lens. */
    fun flipLens() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
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

            val dm = context.resources.displayMetrics
            // Camera sizes are expressed in landscape; normalise the screen the same way.
            val screenAspect = maxOf(dm.widthPixels, dm.heightPixels).toFloat() /
                minOf(dm.widthPixels, dm.heightPixels)

            sizes
                .filter { it.width.toLong() * it.height <= 1920L * 1080L * 2 }
                .minWithOrNull(
                    compareBy(
                        { abs(it.width.toFloat() / it.height - screenAspect) },
                        { -(it.width * it.height) },
                    ),
                ) ?: fallback
        } catch (t: Throwable) {
            fallback
        }
    }

    /** Real torch/flash mode: OFF → ON → AUTO. */
    fun setFlashMode(mode: Int) {
        flashMode = mode
        imageCapture?.flashMode = mode
    }

    /** Real exposure compensation index (device EV steps, usually ±6 around 0). */
    fun setExposureCompensation(index: Int) {
        camera?.cameraControl?.setExposureCompensationIndex(index)
    }

    /** Real capture aspect ratio — rebinds so preview + still share the ratio. */
    fun setAspectRatio(ratio: Int) {
        aspectRatio = ratio
        bind()
    }

    /** Capture a JPEG straight into the device gallery (Pictures/Kroma). */
    fun takePhoto(onSaved: (Uri?) -> Unit, onError: (String) -> Unit) {
        val ic = imageCapture ?: run { onError("Camera not ready"); return }
        val name = "KROMA_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Kroma")
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
                    onSaved(results.savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception.message ?: "Capture failed")
                }
            },
        )
    }

    /** Start/stop video recording into the gallery (Movies/Kroma). */
    fun toggleRecording(onSaved: (Uri?) -> Unit, onError: (String) -> Unit) {
        activeRecording?.let {
            it.stop()
            activeRecording = null
            return
        }

        // Bring video online for the first recording, then start.
        if (!videoEnabled) {
            videoEnabled = true
            bind()
        }

        val vc = videoCapture ?: run { onError("Camera not ready"); return }
        val name = "KROMA_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Kroma")
            }
        }

        val output = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(values).build()

        var pending = vc.output.prepareRecording(context, output)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            pending = pending.withAudioEnabled()
        }

        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                activeRecording = null
                if (event.hasError()) {
                    onError("Recording failed (${event.error})")
                } else {
                    onSaved(event.outputResults.outputUri)
                }
            }
        }
    }
}

