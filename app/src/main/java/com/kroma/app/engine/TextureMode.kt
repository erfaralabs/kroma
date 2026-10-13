package com.kroma.app.engine

/**
 * Texture overlay mode — spec §5.
 *
 * Note: **Film Grain is NOT a mode** — it's a separate binary toggle that can be
 * combined with any mode (matches the `│` divider before GRAIN in the iOS UI).
 */
enum class TextureMode(val label: String) {
    STANDARD("Standard"),
    SOFT_SKIN("Soft Skin"),
    GLOW("Glow"),
    FILM("Film"),
}
