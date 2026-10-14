package com.kroma.app.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat
import com.kroma.app.engine.StyleState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Uniforms pushed to the preview style shader. */
data class StyleParams(
    val padColor: Float,
    val padTone: Float,
    val anchorX: Float,
    val anchorY: Float,
    val intensity: Float,
    val presetId: Int,
    val beta: Float,
    val hueBiasRad: Float,
    val shadowToe: Float = 0f,
    val textureMode: Int = 0,
    val textureIntensity: Float = 0f,
    val filmGrainEnabled: Boolean = false,
) {
    companion object {
        fun from(state: StyleState): StyleParams {
            val p = state.preset
            return StyleParams(
                padColor = state.padColor,
                padTone = state.padTone,
                anchorX = p.anchorX,
                anchorY = p.anchorY,
                intensity = state.intensity,
                presetId = p.id,
                beta = p.beta,
                hueBiasRad = p.hueBiasRad,
                shadowToe = p.shadowToe,
                textureMode = state.textureMode.ordinal,
                textureIntensity = state.textureIntensity,
                filmGrainEnabled = state.filmGrainEnabled,
            )
        }
    }
}

/**
 * Live styled preview: CameraX writes frames into a [SurfaceTexture] owned by this GL view and the
 * renderer applies `shaders/preview_style.frag` (Oklab, zero-LUT) every frame — spec §7 Pass 3.
 */
class GlesStyleView(context: Context) : GLSurfaceView(context) {

    private val styleRenderer = StyleRenderer(context)

    /** Invoked once the GL context + SurfaceTexture exist; bind the camera after this. */
    var onGlReady: (() -> Unit)? = null

    val surfaceProvider: Preview.SurfaceProvider get() = styleRenderer.surfaceProvider

    fun setStyle(params: StyleParams) {
        styleRenderer.params = params
    }

    init {
        setEGLContextClientVersion(3)
        setRenderer(styleRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
        styleRenderer.onReady = { onGlReady?.invoke() }
    }
}

private class StyleRenderer(private val context: Context) : GLSurfaceView.Renderer {

    @Volatile var params = StyleParams(0f, 0f, 0f, 0f, 1f, 5, 0f, 0f, 0f)
    var onReady: (() -> Unit)? = null

    private val lock = Any()
    private var textureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var frameAvailable = false
    private var hasFrame = false
    private val texMatrix = FloatArray(16)

    private var program = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uTexMatrix = 0
    private var uTexture = 0
    private var uSkinMask = 0
    private var uPadCoordinates = 0
    private var uIntensity = 0
    private var uPresetAnchor = 0
    private var uPresetId = 0
    private var uBetaPreset = 0
    private var uHueBias = 0
    private var uShadowToe = 0
    private var uCropScale = 0
    private var uTextureMode = 0
    private var uTextureIntensity = 0
    private var uFilmGrainEnabled = 0
    private var uTime = 0
    private val startTimeNs = System.nanoTime()
    private var maskTexture = 0

    private var viewW = 0
    private var viewH = 0
    private var bufW = 0
    private var bufH = 0
    private var logCrop = true

    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(QUAD); position(0) }

    val surfaceProvider = Preview.SurfaceProvider { request ->
        val s = synchronized(lock) {
            val st = surfaceTexture ?: return@synchronized null
            // Size the GL buffer to exactly what CameraX asks for — without this the camera
            // produces a tiny default stream and the preview looks blocky.
            st.setDefaultBufferSize(
                request.resolution.width,
                request.resolution.height,
            )
            bufW = request.resolution.width
            bufH = request.resolution.height
            logCrop = true
            Log.i("KromaGL", "camera preview resolution = ${bufW}x$bufH")
            Surface(st)
        }
        if (s != null) {
            request.provideSurface(s, ContextCompat.getMainExecutor(context)) {
                s.release()
            }
        } else {
            request.willNotProvideSurface()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram()
        aPosition = GLES30.glGetAttribLocation(program, "a_Position")
        aTexCoord = GLES30.glGetAttribLocation(program, "a_TexCoord")
        uTexMatrix = GLES30.glGetUniformLocation(program, "u_TexMatrix")
        uTexture = GLES30.glGetUniformLocation(program, "u_LinearP3Texture")
        uSkinMask = GLES30.glGetUniformLocation(program, "u_SkinMaskTexture")
        uPadCoordinates = GLES30.glGetUniformLocation(program, "u_PadCoordinates")
        uIntensity = GLES30.glGetUniformLocation(program, "u_Intensity")
        uPresetAnchor = GLES30.glGetUniformLocation(program, "u_PresetAnchor")
        uPresetId = GLES30.glGetUniformLocation(program, "u_PresetId")
        uBetaPreset = GLES30.glGetUniformLocation(program, "u_BetaPreset")
        uHueBias = GLES30.glGetUniformLocation(program, "u_HueBiasAngle")
        uShadowToe = GLES30.glGetUniformLocation(program, "u_ShadowToe")
        uCropScale = GLES30.glGetUniformLocation(program, "u_CropScale")
        uTextureMode = GLES30.glGetUniformLocation(program, "u_TextureMode")
        uTextureIntensity = GLES30.glGetUniformLocation(program, "u_TextureIntensity")
        uFilmGrainEnabled = GLES30.glGetUniformLocation(program, "u_FilmGrainEnabled")
        uTime = GLES30.glGetUniformLocation(program, "u_Time")

        maskTexture = createWhiteTexture()

        synchronized(lock) {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            textureId = ids[0]
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

            surfaceTexture = SurfaceTexture(textureId).apply {
                setOnFrameAvailableListener { synchronized(lock) { frameAvailable = true } }
            }
        }

        ContextCompat.getMainExecutor(context).execute {
            onReady?.invoke()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        viewW = width
        viewH = height
        logCrop = true
        Log.i("KromaGL", "view = ${width}x$height")
    }


    override fun onDrawFrame(gl: GL10?) {
        // Pull the newest camera frame if one arrived. Crucially, we must NOT skip drawing when
        // there is no new frame: GLSurfaceView swaps buffers every vsync, so an early return
        // presents undefined contents (the preview "shakes"/flickers). Draw the last frame instead.
        synchronized(lock) {
            if (frameAvailable) {
                frameAvailable = false
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(texMatrix)
                hasFrame = true
            }
        }

        if (!hasFrame) return

        val p = params

        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glUniform1i(uTexture, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTexture)
        GLES30.glUniform1i(uSkinMask, 1)

        GLES30.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES30.glUniform2f(uPadCoordinates, p.padColor, p.padTone)
        GLES30.glUniform1f(uIntensity, p.intensity)
        GLES30.glUniform2f(uPresetAnchor, p.anchorX, p.anchorY)
        GLES30.glUniform1i(uPresetId, p.presetId)
        GLES30.glUniform1f(uBetaPreset, p.beta)
        GLES30.glUniform1f(uHueBias, p.hueBiasRad)
        GLES30.glUniform1f(uShadowToe, p.shadowToe)
        GLES30.glUniform1i(uTextureMode, p.textureMode)
        GLES30.glUniform1f(uTextureIntensity, p.textureIntensity)
        GLES30.glUniform1i(uFilmGrainEnabled, if (p.filmGrainEnabled) 1 else 0)
        val elapsedSec = ((System.nanoTime() - startTimeNs) / 1_000_000_000.0).toFloat()
        GLES30.glUniform1f(uTime, elapsedSec)

        // Centre-crop so the camera image keeps its aspect ratio inside the viewport.
        val crop = cropScale()
        GLES30.glUniform2f(uCropScale, crop[0], crop[1])
        if (logCrop) {
            logCrop = false
            Log.i(
                "KromaGL",
                "crop = ${crop[0]} x ${crop[1]}  (buf ${bufW}x$bufH, view ${viewW}x$viewH)",
            )
        }

        quad.position(0)
        GLES30.glEnableVertexAttribArray(aPosition)
        GLES30.glVertexAttribPointer(aPosition, 2, GLES30.GL_FLOAT, false, 16, quad)
        quad.position(2)
        GLES30.glEnableVertexAttribArray(aTexCoord)
        GLES30.glVertexAttribPointer(aTexCoord, 2, GLES30.GL_FLOAT, false, 16, quad)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(aPosition)
        GLES30.glDisableVertexAttribArray(aTexCoord)
    }

    /**
     * Per-axis UV crop (<= 1) that centre-crops the camera image into the viewport, preserving its
     * aspect ratio (FILL_CENTER).
     *
     * [u_CropScale] is applied to `a_TexCoord` centered around (0.5, 0.5) before `u_TexMatrix`
     * (i.e. in display UV space). Because display coordinates are scaled before the camera matrix rotates
     * them into buffer coordinates, the aspect ratio crop factor is applied along the display axes.
     */
    private fun cropScale(): FloatArray {
        if (viewW <= 0 || viewH <= 0 || bufW <= 0 || bufH <= 0) return floatArrayOf(1f, 1f)

        val swapped = (viewW < viewH) != (bufW < bufH)
        val texW = if (swapped) bufH.toFloat() else bufW.toFloat()
        val texH = if (swapped) bufW.toFloat() else bufH.toFloat()

        val viewAspect = viewW.toFloat() / viewH.toFloat()
        val texAspect = texW / texH

        return if (viewAspect < texAspect) {
            floatArrayOf(viewAspect / texAspect, 1f)
        } else {
            floatArrayOf(1f, texAspect / viewAspect)
        }
    }

    private fun buildProgram(): Int {
        val vs = compile(GLES30.GL_VERTEX_SHADER, asset("shaders/quad.vert"))
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, asset("shaders/preview_style.frag"))
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)
        return prog
    }

    private fun compile(type: Int, src: String): Int {
        val sh = GLES30.glCreateShader(type)
        GLES30.glShaderSource(sh, src)
        GLES30.glCompileShader(sh)
        return sh
    }

    private fun asset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun createWhiteTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        val px = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).apply {
            put(255.toByte()); put(255.toByte()); put(255.toByte()); put(255.toByte()); position(0)
        }
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, px,
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        return ids[0]
    }

    private companion object {
        // x, y, u, v — full-screen triangle strip
        val QUAD = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )
    }
}

