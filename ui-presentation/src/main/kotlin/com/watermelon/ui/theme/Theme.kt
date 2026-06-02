package com.watermelon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Material 3 theme with RTL-first layout overrides (Manifest §1.1, Teams §6).
 *
 * Watermelon is RTL-native: for Persian/Arabic locales the entire layout direction is
 * inverted at the theme root, not mirrored per-widget. Pass [forceRtl] = true to force RTL
 * regardless of system locale (the "Forced RTL overrides per locale" setting).
 */
@Composable
fun WatermelonTheme(
    forceRtl: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        background = WatermelonColors.Background,
        surface = WatermelonColors.Surface,
        surfaceVariant = WatermelonColors.SurfaceVariant,
        onBackground = WatermelonColors.OnBackground,
        onSurface = WatermelonColors.OnSurface,
        onSurfaceVariant = WatermelonColors.OnSurfaceVariant,
        primary = WatermelonColors.Accent,
        onPrimary = WatermelonColors.OnAccent,
        secondary = WatermelonColors.AccentVariant,
        error = WatermelonColors.Error,
        outline = WatermelonColors.Outline
    )

    val layoutDirection =
        if (forceRtl) LayoutDirection.Rtl else LocalLayoutDirection.current

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WatermelonTypography.typography,
            content = content
        )
    }
}

/** Pure-dark Watermelon always uses a dark scheme; helper kept for parity/testing. */
@Composable
fun isAppInDarkTheme(): Boolean = isSystemInDarkTheme() || true
