package com.kroma.app.engine

/**
 * A single Photographic Style preset, per the
 * "Photographic Styles & UI Engine Specification" §3 (reference matrix).
 *
 * Columns map 1:1 to the spec: default anchor, beta factor, hue bias angle, shadow toe offset.
 */
data class Preset(
    val id: Int,
    val category: String,
    val name: String,           // UI title ("Style Name")
    val appleReference: String, // Apple's reference name this mimics
    val anchorX: Float,         // default puck anchor on the Color (X) axis
    val anchorY: Float,         // default puck anchor on the Tone  (Y) axis
    val beta: Float,            // chroma gain multiplier
    val hueBiasDeg: Float,      // hue bias angle, DEGREES (converted to radians for the shader)
    val shadowToe: Float,       // shadow toe offset (record-only in the current shader)
    val monochrome: Boolean = false,
) {
    /** Hue bias converted to the radians the shader expects. */
    val hueBiasRad: Float get() = Math.toRadians(hueBiasDeg.toDouble()).toFloat()
}

/**
 * The canonical 15-preset table.
 *
 * ID order is **inferred** from the spec's `u_PresetId` anchors
 * (5 = Bypass/Standard, 13 & 14 = the two B&W styles) plus the XMP example (9 = Cinema).
 * See §3.1 of `PHOTOGRAPHIC_STYLES_UI_ENGINE_GUIDE.md`.
 */
object Presets {

    const val NATIVE_ID = 5

    val ALL: List<Preset> = listOf(
        Preset(0,  "Undertones", "Blush",     "Cool Rose",    20f,    0f,  0.85f,   18.0f,  0.00f),
        Preset(1,  "Undertones", "Balance",   "Neutral",       0f,    0f,  0.50f,    0.0f,  0.00f),
        Preset(2,  "Undertones", "Champagne", "Rose Gold",    35f,   15f,  0.90f,    8.5f,  0.04f),
        Preset(3,  "Undertones", "Gilded",    "Gold",         40f,   10f,  1.10f,  -12.0f,  0.02f),
        Preset(4,  "Undertones", "Bronze",    "Amber",        50f,  -25f,  1.25f,  -24.0f, -0.06f),
        Preset(5,  "Default",    "Native",    "Standard",      0f,    0f,  0.50f,    0.0f,  0.00f),
        Preset(6,  "Moods",      "Vivid",     "Vibrant",      30f,   10f,  1.40f,    0.0f,  0.03f),
        Preset(7,  "Organic",    "Natural",   "Natural",      10f,    0f,  0.40f,   -4.0f,  0.01f),
        Preset(8,  "Radiance",   "Luminous",  "Luminous",      0f,   35f,  0.60f,    2.0f,  0.08f),
        Preset(9,  "Cinema",     "Dramatic",  "Dramatic",      0f,  -45f,  0.75f,   -5.0f, -0.12f),
        Preset(10, "Hush",       "Quiet",     "Quiet",       -30f,    0f,  0.60f,    0.0f,  0.00f),
        Preset(11, "Hearth",     "Cozy",      "Cozy",         35f,  -30f,  1.15f,  -18.0f, -0.08f),
        Preset(12, "Airy",       "Ethereal",  "Ethereal",     15f,   40f,  0.50f,    6.0f,  0.12f),
        Preset(13, "Silver",     "Muted B&W", "Muted B&W",  -100f,  -10f,  1.00f,    0.0f, -0.02f, monochrome = true),
        Preset(14, "Graphite",   "Stark B&W", "Stark B&W",  -100f,  -50f,  1.00f,    0.0f, -0.15f, monochrome = true),
    )

    private val BY_ID: Map<Int, Preset> = ALL.associateBy { it.id }

    fun byId(id: Int): Preset = BY_ID[id] ?: BY_ID.getValue(NATIVE_ID)
}
