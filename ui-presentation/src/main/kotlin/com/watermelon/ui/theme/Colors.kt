package com.watermelon.ui.theme

import androidx.compose.ui.graphics.Color

/** Pure dark palette + accent colours (Manifest §9 Theme: "Pure Dark"). */
object WatermelonColors {
    val Background = Color(0xFF000000)   // pure black for OLED efficiency
    val Surface = Color(0xFF111114)
    val SurfaceVariant = Color(0xFF1C1C20)
    val OnBackground = Color(0xFFEDEDED)
    val OnSurface = Color(0xFFDADADA)
    val OnSurfaceVariant = Color(0xFF9A9A9A)

    // Watermelon accent — green flesh + pink rind.
    val Accent = Color(0xFF35C759)
    val AccentVariant = Color(0xFFFF4D6D)
    val OnAccent = Color(0xFF00210B)

    val Error = Color(0xFFCF6679)
    val Outline = Color(0xFF2A2A2E)
}
