package com.kroma.app.engine

import android.graphics.Bitmap

/**
 * Master style state — the exact mirror of the shader uniform block
 * (`u_PresetId`, `u_PadCoordinates`, `u_Intensity`) plus the skin-mask confidence.
 */
data class StyleState(
    val presetId: Int = Presets.NATIVE_ID,
    val padColor: Float = 0f,   // [-100, 100]  (X axis)
    val padTone: Float = 0f,    // [-100, 100]  (Y axis)
    val intensity: Float = 1f,  // [0, 1]
    val skinAlpha: Float = 1f,  // skin-mask confidence [0, 1]
    // --- Texture overlays (spec §5) ---
    val textureMode: TextureMode = TextureMode.STANDARD,
    val textureIntensity: Float = 0f,  // [0, 1] — applies to the selected mode
    val filmGrainEnabled: Boolean = false,
) {
    val preset: Preset get() = Presets.byId(presetId)

    /** Effective Tone coordinate the shader receives: pad + preset anchor. */
    val effectiveTone: Float get() = (padTone + preset.anchorY).coerceIn(-100f, 100f)

    /** Effective Color coordinate the shader receives: pad + preset anchor. */
    val effectiveColor: Float get() = (padColor + preset.anchorX).coerceIn(-100f, 100f)

    /** Whether the puck sits on the preset's baseline anchor (drives the ↺ visibility). */
    val isAtAnchor: Boolean get() = padColor == 0f && padTone == 0f

    /** Whether *anything* deviates from the preset baseline (pad or texture). */
    val isAtDefaults: Boolean
        get() = isAtAnchor &&
            textureMode == TextureMode.STANDARD && textureIntensity == 0f && !filmGrainEnabled

    /** The "↺" reset action — restores the preset baseline (puck + texture). */
    fun reset(): StyleState = copy(
        padColor = 0f,
        padTone = 0f,
        textureMode = TextureMode.STANDARD,
        textureIntensity = 0f,
        filmGrainEnabled = false,
    )

    /** Clears only the 2D-pad position. */
    fun resetPuck(): StyleState = copy(padColor = 0f, padTone = 0f)
}

/**
 * CPU implementation of the style pass. Mirrors `photographic_style.frag::main()` line-for-line,
 * operating on **linear Display P3** RGB.
 */
object StyleEngine {

    /**
     * @param baseLinearP3 linear-Display-P3 RGB in `[0,1]` per channel
     * @return styled linear-Display-P3 RGB
     */
    fun applyStyle(baseLinearP3: FloatArray, state: StyleState): FloatArray {
        val preset = state.preset

        // Bypass fast-path (matches the shader).
        val padLen = Math.hypot(state.padColor.toDouble(), state.padTone.toDouble())
        if ((preset.id == Presets.NATIVE_ID && padLen < 0.001 && state.isAtDefaults) ||
            (state.intensity <= 0.001f && state.isAtDefaults)
        ) {
            return baseLinearP3.copyOf()
        }

        val lab = Oklab.rgbToOklab(baseLinearP3)
        val l = lab[0]
        val a = lab[1]
        val b = lab[2]

        val effColor = (state.padColor + preset.anchorX).coerceIn(-100f, 100f)
        val effTone = (state.padTone + preset.anchorY).coerceIn(-100f, 100f)

        // 1. Parametric tone curve
        val gamma = Math.exp((-effTone / 50.0)).toFloat()
        val lg = pow(l.coerceAtLeast(1e-6f), gamma)
        val om = pow((1f - l).coerceAtLeast(0.0001f), gamma)
        val sCurve = if (lg + om == 0f) 0f else lg / (lg + om)
        var lStyled = l + (sCurve - l) * (4f * l * (1f - l))
        val shadowWeight = pow((1f - lStyled.coerceIn(0f, 1f)), 2f)
        lStyled = (lStyled + preset.shadowToe * shadowWeight * (1f - Math.abs(effTone) / 200f)).coerceIn(0f, 1f)

        // 2. Parametric chroma & hue mapping
        val chroma = Math.hypot(a.toDouble(), b.toDouble()).toFloat()
        val hue = if (chroma > 1e-5f) Math.atan2(b.toDouble(), a.toDouble()).toFloat() else 0f
        val kC = (1f + (effColor / 100f) * preset.beta).coerceAtLeast(0f)
        var chromaStyled = chroma * kC
        val hueStyled = hue + preset.hueBiasRad * (effColor / 100f)
        if (preset.monochrome) chromaStyled = 0f

        // 3. Skin tone hue protection (Spec §4.4)
        val dx = (a - 0.092f) / 0.040f
        val dy = (b - 0.061f) / 0.030f
        val dSkin = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val lumGate = smoothstep(0.12f, 0.26f, l) * (1f - smoothstep(0.85f, 0.96f, l))
        val satGate = 1f - smoothstep(0.18f, 0.25f, chroma)
        val wSkin = (1f - dSkin).coerceIn(0f, 1f) * lumGate * satGate * state.skinAlpha

        val lProtected = mix(lStyled, 0.85f * l + 0.15f * lStyled, wSkin)
        val cProtected = mix(chromaStyled, chroma * (1f + (effColor / 100f) * 0.15f), wSkin)
        val hProtected = mix(hueStyled, hue, wSkin)

        val labProcessed = floatArrayOf(
            lProtected,
            cProtected * Math.cos(hProtected.toDouble()).toFloat(),
            cProtected * Math.sin(hProtected.toDouble()).toFloat(),
        )
        val styled = Oklab.oklabToRgb(labProcessed)
        var out0 = mix(baseLinearP3[0], styled[0], state.intensity)
        var out1 = mix(baseLinearP3[1], styled[1], state.intensity)
        var out2 = mix(baseLinearP3[2], styled[2], state.intensity)

        if (state.textureMode == TextureMode.FILM && state.textureIntensity > 0f) {
            val filmR = (1f - Math.exp((-out0 * 1.35).toDouble()).toFloat()) + 0.015f
            val filmG = (1f - Math.exp((-out1 * 1.35).toDouble()).toFloat()) + 0.015f
            val filmB = (1f - Math.exp((-out2 * 1.35).toDouble()).toFloat()) + 0.015f
            val tInt = state.textureIntensity * 0.65f
            out0 = mix(out0, filmR, tInt).coerceIn(0f, 1f)
            out1 = mix(out1, filmG, tInt).coerceIn(0f, 1f)
            out2 = mix(out2, filmB, tInt).coerceIn(0f, 1f)
        }

        return floatArrayOf(out0, out1, out2)
    }

    /** Convenience for the Phase 0 UI: takes sRGB in `[0,1]`, returns styled sRGB in `[0,1]`. */
    fun renderSrgb(srgb: FloatArray, state: StyleState): FloatArray {
        val lin = floatArrayOf(
            Srgb.toLinear(srgb[0]),
            Srgb.toLinear(srgb[1]),
            Srgb.toLinear(srgb[2]),
        )
        val out = applyStyle(lin, state)
        return floatArrayOf(
            Srgb.fromLinear(out[0]),
            Srgb.fromLinear(out[1]),
            Srgb.fromLinear(out[2]),
        )
    }

    /**
     * Applies the active [StyleState] (preset, padTone, padColor, intensity, hue bias)
     * to a captured photo [Bitmap] via an optimized 64×64×64 3D color lookup table
     * with trilinear interpolation.
     * Preserves bitmap dimensions and aspect ratio exactly.
     */
    fun applyToBitmap(bitmap: Bitmap, state: StyleState): Bitmap {
        val preset = state.preset
        val padLen = Math.hypot(state.padColor.toDouble(), state.padTone.toDouble())
        if ((preset.id == Presets.NATIVE_ID && padLen < 0.001 && state.isAtDefaults) ||
            (state.intensity <= 0.001f && state.isAtDefaults)
        ) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        // Build a 64×64×64 3D color lookup table (262,144 entries) with zero per-entry allocations
        val lutSize = 64
        val lut = IntArray(lutSize * lutSize * lutSize)
        val step = 1.0f / (lutSize - 1).toFloat()

        val effColor = (state.padColor + preset.anchorX).coerceIn(-100f, 100f)
        val effTone = (state.padTone + preset.anchorY).coerceIn(-100f, 100f)
        val gamma = Math.exp((-effTone / 50.0)).toFloat()
        val kC = (1f + (effColor / 100f) * preset.beta).coerceAtLeast(0f)
        val hueBias = preset.hueBiasRad * (effColor / 100f)
        val intensity = state.intensity
        val shadowToe = preset.shadowToe
        val isMonochrome = preset.monochrome
        val skinAlpha = state.skinAlpha
        val toneAbsFactor = 1f - Math.abs(effTone) / 200f
        val colorToneFactor = 1f + (effColor / 100f) * 0.15f

        for (r in 0 until lutSize) {
            val rf = r * step
            val linR = Srgb.toLinear(rf)
            for (g in 0 until lutSize) {
                val gf = g * step
                val linG = Srgb.toLinear(gf)
                for (b in 0 until lutSize) {
                    val bf = b * step
                    val linB = Srgb.toLinear(bf)

                    // Linear Display P3 to LMS
                    val lms0 = 0.41222147f * linR + 0.53633254f * linG + 0.05144599f * linB
                    val lms1 = 0.21190350f * linR + 0.68069955f * linG + 0.10739696f * linB
                    val lms2 = 0.08830246f * linR + 0.28171884f * linG + 0.62997870f * linB

                    val c0 = Math.cbrt(lms0.coerceAtLeast(1e-6f).toDouble()).toFloat()
                    val c1 = Math.cbrt(lms1.coerceAtLeast(1e-6f).toDouble()).toFloat()
                    val c2 = Math.cbrt(lms2.coerceAtLeast(1e-6f).toDouble()).toFloat()

                    // LMS to Oklab
                    val l = 0.21045426f * c0 + 0.79361779f * c1 - 0.00407205f * c2
                    val a = 1.97799850f * c0 - 2.42859221f * c1 + 0.45059371f * c2
                    val ob = 0.02590404f * c0 + 0.78277177f * c1 - 0.80867577f * c2

                    // 1. Parametric tone curve & shadow toe
                    val lg = pow(l.coerceAtLeast(1e-6f), gamma)
                    val om = pow((1f - l).coerceAtLeast(0.0001f), gamma)
                    val sCurve = if (lg + om == 0f) 0f else lg / (lg + om)
                    var lStyled = l + (sCurve - l) * (4f * l * (1f - l))
                    val shadowWeight = pow(1f - lStyled.coerceIn(0f, 1f), 2f)
                    lStyled = (lStyled + shadowToe * shadowWeight * toneAbsFactor).coerceIn(0f, 1f)

                    // 2. Chroma & Hue
                    val chroma = Math.hypot(a.toDouble(), ob.toDouble()).toFloat()
                    val hue = if (chroma > 1e-5f) Math.atan2(ob.toDouble(), a.toDouble()).toFloat() else 0f
                    val chromaStyled = if (isMonochrome) 0f else chroma * kC
                    val hueStyled = hue + hueBias

                    // 3. Skin Tone Protection
                    val dx = (a - 0.092f) / 0.040f
                    val dy = (ob - 0.061f) / 0.030f
                    val dSkin = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    val lumGate = smoothstep(0.12f, 0.26f, l) * (1f - smoothstep(0.85f, 0.96f, l))
                    val satGate = 1f - smoothstep(0.18f, 0.25f, chroma)
                    val wSkin = (1f - dSkin).coerceIn(0f, 1f) * lumGate * satGate * skinAlpha

                    val lProtected = mix(lStyled, 0.85f * l + 0.15f * lStyled, wSkin)
                    val cProtected = mix(chromaStyled, chroma * colorToneFactor, wSkin)
                    val hProtected = mix(hueStyled, hue, wSkin)

                    val lab0 = lProtected
                    val lab1 = cProtected * Math.cos(hProtected.toDouble()).toFloat()
                    val lab2 = cProtected * Math.sin(hProtected.toDouble()).toFloat()

                    // Oklab to linear Display P3
                    val cube0 = lab0 + 0.39633778f * lab1 + 0.21580376f * lab2
                    val cube1 = lab0 - 0.10556135f * lab1 - 0.06385417f * lab2
                    val cube2 = lab0 - 0.08948418f * lab1 - 1.29148555f * lab2

                    val lmsOut0 = cube0 * cube0 * cube0
                    val lmsOut1 = cube1 * cube1 * cube1
                    val lmsOut2 = cube2 * cube2 * cube2

                    val styledR = (4.07674166f * lmsOut0 - 3.30771159f * lmsOut1 + 0.23096993f * lmsOut2).coerceAtLeast(0f)
                    val styledG = (-1.26843800f * lmsOut0 + 2.60975740f * lmsOut1 - 0.34131940f * lmsOut2).coerceAtLeast(0f)
                    val styledB = (-0.00419609f * lmsOut0 - 0.70341861f * lmsOut1 + 1.70761470f * lmsOut2).coerceAtLeast(0f)

                    var finalLinR = mix(linR, styledR, intensity)
                    var finalLinG = mix(linG, styledG, intensity)
                    var finalLinB = mix(linB, styledB, intensity)

                    if (state.textureMode == TextureMode.FILM && state.textureIntensity > 0f) {
                        val filmR = (1f - Math.exp((-finalLinR * 1.35).toDouble()).toFloat()) + 0.015f
                        val filmG = (1f - Math.exp((-finalLinG * 1.35).toDouble()).toFloat()) + 0.015f
                        val filmB = (1f - Math.exp((-finalLinB * 1.35).toDouble()).toFloat()) + 0.015f
                        val tInt = state.textureIntensity * 0.65f
                        finalLinR = mix(finalLinR, filmR, tInt).coerceIn(0f, 1f)
                        finalLinG = mix(finalLinG, filmG, tInt).coerceIn(0f, 1f)
                        finalLinB = mix(finalLinB, filmB, tInt).coerceIn(0f, 1f)
                    }

                    val ro = (Srgb.fromLinear(finalLinR).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val go = (Srgb.fromLinear(finalLinG).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val bo = (Srgb.fromLinear(finalLinB).coerceIn(0f, 1f) * 255f + 0.5f).toInt()

                    val lutIdx = (r shl 12) or (g shl 6) or b
                    lut[lutIdx] = (ro shl 16) or (go shl 8) or bo
                }
            }
        }

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Precompute channel coordinate mapping tables for 8-bit values 0..255
        val c0 = IntArray(256)
        val c1 = IntArray(256)
        val frac = FloatArray(256)
        val scale = 63f / 255f
        for (v in 0..255) {
            val coord = v * scale
            val idx0 = coord.toInt()
            c0[v] = idx0
            c1[v] = if (idx0 < 63) idx0 + 1 else 63
            frac[v] = coord - idx0
        }

        // Process pixels in parallel chunks across CPU cores with trilinear interpolation
        val numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val chunkSize = (pixels.size + numThreads - 1) / numThreads
        val threads = Array(numThreads) { threadIdx ->
            Thread {
                val start = threadIdx * chunkSize
                val end = minOf(start + chunkSize, pixels.size)
                for (i in start until end) {
                    val p = pixels[i]
                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF

                    val r0 = c0[r]
                    val r1 = c1[r]
                    val fr = frac[r]

                    val g0 = c0[g]
                    val g1 = c1[g]
                    val fg = frac[g]

                    val b0 = c0[b]
                    val b1 = c1[b]
                    val fb = frac[b]

                    val idx00 = (r0 shl 12) or (g0 shl 6)
                    val idx01 = (r0 shl 12) or (g1 shl 6)
                    val idx10 = (r1 shl 12) or (g0 shl 6)
                    val idx11 = (r1 shl 12) or (g1 shl 6)

                    val c000 = lut[idx00 or b0]
                    val c001 = lut[idx00 or b1]
                    val c010 = lut[idx01 or b0]
                    val c011 = lut[idx01 or b1]
                    val c100 = lut[idx10 or b0]
                    val c101 = lut[idx10 or b1]
                    val c110 = lut[idx11 or b0]
                    val c111 = lut[idx11 or b1]

                    val invFb = 1f - fb
                    val r00 = ((c000 ushr 16) and 0xFF) * invFb + ((c001 ushr 16) and 0xFF) * fb
                    val g00 = ((c000 ushr 8) and 0xFF) * invFb + ((c001 ushr 8) and 0xFF) * fb
                    val b00 = (c000 and 0xFF) * invFb + (c001 and 0xFF) * fb

                    val r01 = ((c010 ushr 16) and 0xFF) * invFb + ((c011 ushr 16) and 0xFF) * fb
                    val g01 = ((c010 ushr 8) and 0xFF) * invFb + ((c011 ushr 8) and 0xFF) * fb
                    val b01 = (c010 and 0xFF) * invFb + (c011 and 0xFF) * fb

                    val r10 = ((c100 ushr 16) and 0xFF) * invFb + ((c101 ushr 16) and 0xFF) * fb
                    val g10 = ((c100 ushr 8) and 0xFF) * invFb + ((c101 ushr 8) and 0xFF) * fb
                    val b10 = (c100 and 0xFF) * invFb + (c101 and 0xFF) * fb

                    val r11 = ((c110 ushr 16) and 0xFF) * invFb + ((c111 ushr 16) and 0xFF) * fb
                    val g11 = ((c110 ushr 8) and 0xFF) * invFb + ((c111 ushr 8) and 0xFF) * fb
                    val b11 = (c110 and 0xFF) * invFb + (c111 and 0xFF) * fb

                    val invFg = 1f - fg
                    val r0Lerp = r00 * invFg + r01 * fg
                    val g0Lerp = g00 * invFg + g01 * fg
                    val b0Lerp = b00 * invFg + b01 * fg

                    val r1Lerp = r10 * invFg + r11 * fg
                    val g1Lerp = g10 * invFg + g11 * fg
                    val b1Lerp = b10 * invFg + b11 * fg

                    val invFr = 1f - fr
                    val outR = (r0Lerp * invFr + r1Lerp * fr + 0.5f).toInt().coerceIn(0, 255)
                    val outG = (g0Lerp * invFr + g1Lerp * fr + 0.5f).toInt().coerceIn(0, 255)
                    val outB = (b0Lerp * invFr + b1Lerp * fr + 0.5f).toInt().coerceIn(0, 255)

                    pixels[i] = (p and -0x1000000) or (outR shl 16) or (outG shl 8) or outB
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Post-processing texture passes: Glow bloom diffusion & procedural Film Grain
        if (state.textureMode == TextureMode.GLOW && state.textureIntensity > 0f) {
            val glowWeight = state.textureIntensity * 0.45f
            val stride = w
            for (y in 1 until h - 1) {
                val row = y * stride
                for (x in 1 until w - 1) {
                    val p = pixels[row + x]
                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF
                    val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                    if (lum > 0.6f) {
                        val hl = (lum - 0.6f) / 0.4f * glowWeight
                        val bleedR = (r * hl).toInt()
                        val bleedG = (g * hl).toInt()
                        val bleedB = (b * hl).toInt()
                        for (dy in -1..1) {
                            for (dx in -1..1) {
                                val nIdx = (y + dy) * stride + (x + dx)
                                val np = pixels[nIdx]
                                val nr = minOf(255, ((np ushr 16) and 0xFF) + (bleedR shr 2))
                                val ng = minOf(255, ((np ushr 8) and 0xFF) + (bleedG shr 2))
                                val nb = minOf(255, (np and 0xFF) + (bleedB shr 2))
                                pixels[nIdx] = (np and -0x1000000) or (nr shl 16) or (ng shl 8) or nb
                            }
                        }
                    }
                }
            }
        }

        if (state.filmGrainEnabled) {
            val rng = java.util.Random(1337)
            for (i in pixels.indices) {
                val p = pixels[i]
                var r = (p ushr 16) and 0xFF
                var g = (p ushr 8) and 0xFF
                var b = p and 0xFF

                val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                val midtoneMod = Math.sqrt(Math.max(0.0, (lum * (1f - lum)).toDouble())).toFloat() * 2f
                val noise = ((rng.nextFloat() - 0.5f) * 0.07f * midtoneMod * 255f).toInt()

                r = (r + noise).coerceIn(0, 255)
                g = (g + noise).coerceIn(0, 255)
                b = (b + noise).coerceIn(0, 255)
                pixels[i] = (p and -0x1000000) or (r shl 16) or (g shl 8) or b
            }
        }

        val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return outBmp
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    private fun pow(base: Float, exp: Float): Float = Math.pow(base.toDouble(), exp.toDouble()).toFloat()
}
