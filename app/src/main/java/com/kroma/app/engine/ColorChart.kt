package com.kroma.app.engine

/**
 * A small reference chart used by the Phase 0 UI to visualise a style before the GL pipeline exists.
 * Values are sRGB in `[0,1]`; they are pushed through [StyleEngine.renderSrgb].
 */
object ColorChart {
    data class Swatch(val label: String, val srgb: FloatArray)

    val SWATCHES: List<Swatch> = listOf(
        Swatch("Skin L", floatArrayOf(0.95f, 0.83f, 0.74f)),
        Swatch("Skin M", floatArrayOf(0.85f, 0.68f, 0.56f)),
        Swatch("Skin D", floatArrayOf(0.55f, 0.38f, 0.28f)),
        Swatch("Sky", floatArrayOf(0.45f, 0.66f, 0.90f)),
        Swatch("Leaf", floatArrayOf(0.32f, 0.48f, 0.24f)),
        Swatch("Gold", floatArrayOf(0.85f, 0.68f, 0.28f)),
        Swatch("G75", floatArrayOf(0.75f, 0.75f, 0.75f)),
        Swatch("G50", floatArrayOf(0.50f, 0.50f, 0.50f)),
        Swatch("G25", floatArrayOf(0.25f, 0.25f, 0.25f)),
    )

    /** Representative swatch (mid skin tone) used as the per-preset thumbnail seed. */
    val THUMB_SEED: FloatArray = SWATCHES[1].srgb
}
