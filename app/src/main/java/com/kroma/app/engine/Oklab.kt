package com.kroma.app.engine

/**
 * Pure-Kotlin CPU reference for Kroma's color science. It mirrors
 * `assets/shaders/photographic_style.frag` **exactly** so the maths can be validated on the JVM
 * (no device, no GPU) and so we have a ground-truth to diff the GPU against.
 *
 * IMPORTANT: RGB in/out is **linear Display P3**, not sRGB. Convert only at the display boundary
 * (see [Srgb]).
 */
object Oklab {

    // Row-major matrices. These are the transposes of the COLUMN-major `mat3(...)` literals
    // in the shader (GLSL mat3 is column-major).
    private val M1 = arrayOf(
        floatArrayOf(0.41222147f, 0.53633254f, 0.05144599f),
        floatArrayOf(0.21190350f, 0.68069955f, 0.10739696f),
        floatArrayOf(0.08830246f, 0.28171884f, 0.62997870f),
    )
    private val M2 = arrayOf(
        floatArrayOf(0.21045426f, 0.79361779f, -0.00407205f),
        floatArrayOf(1.97799850f, -2.42859221f, 0.45059371f),
        floatArrayOf(0.02590404f, 0.78277177f, -0.80867577f),
    )
    private val M2_INV = arrayOf(
        floatArrayOf(1.00000000f, 0.39633778f, 0.21580376f),
        floatArrayOf(1.00000000f, -0.10556135f, -0.06385417f),
        floatArrayOf(1.00000000f, -0.08948418f, -1.29148555f),
    )
    private val M1_INV = arrayOf(
        floatArrayOf(4.07674166f, -3.30771159f, 0.23096993f),
        floatArrayOf(-1.26843800f, 2.60975740f, -0.34131940f),
        floatArrayOf(-0.00419609f, -0.70341861f, 1.70761470f),
    )

    private fun mul(m: Array<FloatArray>, v: FloatArray): FloatArray = floatArrayOf(
        m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
        m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
        m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2],
    )

    /** Linear Display P3 RGB -> Oklab. */
    fun rgbToOklab(rgb: FloatArray): FloatArray {
        val lms = mul(M1, rgb)
        val cube = floatArrayOf(
            cbrt(lms[0].coerceAtLeast(0f)),
            cbrt(lms[1].coerceAtLeast(0f)),
            cbrt(lms[2].coerceAtLeast(0f)),
        )
        return mul(M2, cube)
    }

    /** Oklab -> linear Display P3 RGB (clamped at 0, exactly as the shader does). */
    fun oklabToRgb(lab: FloatArray): FloatArray {
        val cube = mul(M2_INV, lab)
        val lms = floatArrayOf(
            cube[0] * cube[0] * cube[0],
            cube[1] * cube[1] * cube[1],
            cube[2] * cube[2] * cube[2],
        )
        val rgb = mul(M1_INV, lms)
        rgb[0] = rgb[0].coerceAtLeast(0f)
        rgb[1] = rgb[1].coerceAtLeast(0f)
        rgb[2] = rgb[2].coerceAtLeast(0f)
        return rgb
    }

    private fun cbrt(x: Float): Float = Math.cbrt(x.toDouble()).toFloat()
}

/** sRGB transfer helpers. Used only at the display boundary (Phase 0 CPU preview). */
object Srgb {
    fun toLinear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f
        else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

    fun fromLinear(c: Float): Float {
        val x = c.coerceIn(0f, 1f)
        return if (x <= 0.0031308f) x * 12.92f
        else (1.055f * Math.pow(x.toDouble(), 1.0 / 2.4).toFloat() - 0.055f)
    }
}
