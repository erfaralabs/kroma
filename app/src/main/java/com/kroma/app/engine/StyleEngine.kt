package com.kroma.app.engine

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
        if (preset.id == Presets.NATIVE_ID && padLen < 0.001) return baseLinearP3.copyOf()

        val lab = Oklab.rgbToOklab(baseLinearP3)
        val l = lab[0]
        val a = lab[1]
        val b = lab[2]

        val effColor = (state.padColor + preset.anchorX).coerceIn(-100f, 100f)
        val effTone = (state.padTone + preset.anchorY).coerceIn(-100f, 100f)

        // 1. Parametric tone curve
        val gamma = Math.exp((-effTone / 50.0)).toFloat()
        val lg = pow(l.coerceAtLeast(0f), gamma)
        val om = pow((1f - l).coerceAtLeast(0.0001f), gamma)
        val sCurve = if (lg + om == 0f) 0f else lg / (lg + om)
        val lStyled = l + (sCurve - l) * (4f * l * (1f - l))

        // 2. Parametric chroma & hue mapping
        val chroma = Math.hypot(a.toDouble(), b.toDouble()).toFloat()
        val hue = Math.atan2(b.toDouble(), a.toDouble()).toFloat()
        val kC = (1f + (effColor / 100f) * preset.beta).coerceAtLeast(0f)
        var chromaStyled = chroma * kC
        val hueStyled = hue + preset.hueBiasRad * (effColor / 100f)
        if (preset.monochrome) chromaStyled = 0f

        // 3. Skin tone hue protection
        val dx = (a - 0.092f) / 0.040f
        val dy = (b - 0.061f) / 0.030f
        val dSkin = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val wSkin = (1f - dSkin).coerceIn(0f, 1f) * state.skinAlpha

        val lProtected = mix(lStyled, mix(l, lStyled, 0.25f), wSkin)
        val cProtected = mix(chromaStyled, chroma * (1f + (effColor / 100f) * 0.15f), wSkin)
        val hProtected = mix(hueStyled, hue, wSkin)

        val labProcessed = floatArrayOf(
            lProtected,
            cProtected * Math.cos(hProtected.toDouble()).toFloat(),
            cProtected * Math.sin(hProtected.toDouble()).toFloat(),
        )
        val styled = Oklab.oklabToRgb(labProcessed)
        return floatArrayOf(
            mix(baseLinearP3[0], styled[0], state.intensity),
            mix(baseLinearP3[1], styled[1], state.intensity),
            mix(baseLinearP3[2], styled[2], state.intensity),
        )
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

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    private fun pow(base: Float, exp: Float): Float = Math.pow(base.toDouble(), exp.toDouble()).toFloat()
}
