package com.watermelon.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Font scale tuned for Farsi/Arabic-friendly typefaces (Manifest §1.1 RTL-Native).
 * Uses the platform default family (which carries Noto Naskh / Vazir on most devices);
 * a bundled Farsi typeface can replace [FontFamily.Default] without touching call sites.
 */
object WatermelonTypography {

    private val farsiFriendly = FontFamily.Default

    val typography = Typography(
        displayLarge = TextStyle(fontFamily = farsiFriendly, fontSize = 34.sp),
        titleLarge = TextStyle(fontFamily = farsiFriendly, fontSize = 22.sp),
        bodyLarge = TextStyle(
            fontFamily = farsiFriendly,
            fontSize = 16.sp,
            // Content text follows the locale direction; alignment resolves per layout.
            textDirection = TextDirection.Content,
            textAlign = TextAlign.Start
        ),
        bodyMedium = TextStyle(fontFamily = farsiFriendly, fontSize = 14.sp),
        labelLarge = TextStyle(fontFamily = farsiFriendly, fontSize = 14.sp)
    )
}
