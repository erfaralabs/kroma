package com.kroma.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Visual tokens lifted from the iOS Photographic Styles screenshot
 * (`references/01-texture-tab.jpeg`). See `UI_PLAN.md` §3.
 */
object KromaColors {
    /** Warm yellow used for the active tab, selection ring and tinted icons. */
    val Accent = Color(0xFFFFD84D)

    /** Bottom-sheet / overlay background — translucent so the blurred preview reads through. */
    val Sheet = Color(0xC71C1C1E)
    val SheetEdge = Color(0x1AFFFFFF)

    /** Pill background behind a selected texture mode. */
    val PillIdle = Color(0x1FFFFFFF)

    val TextPrimary = Color(0xFFF2F2F2)
    val TextSecondary = Color(0xFF9A9A9A)
    val TextTertiary = Color(0xFF6F6F6F)

    val ViewfinderTop = Color(0xFF16202B)
    val ViewfinderBottom = Color(0xFF07090C)
    val Scrim = Color(0xCC000000)
}

object KromaDimens {
    val SheetCorner = 28.dp
    val SheetPadding = 18.dp
    val PadSize = 240.dp
}
