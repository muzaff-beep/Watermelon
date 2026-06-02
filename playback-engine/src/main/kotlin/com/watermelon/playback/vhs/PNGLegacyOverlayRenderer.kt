package com.watermelon.playback.vhs

import android.content.Context
import com.watermelon.common.model.VhsTier

/**
 * Tier C — pre-baked semi-transparent PNG overlay for API 23–32 (no AGSL).
 *
 * The actual drawing is done in Compose by the UI layer
 * ([com.watermelon.ui.components.VhsSeekBar] / PlayerScreen) using the drawables
 * `R.drawable.ic_vhs_scanlines` + `ic_vhs_noise`, animated via `animateFloatAsState`.
 * This renderer only carries the animation state (scanline Y-offset + alpha) that the
 * overlay binds to, so seek behaviour is identical across tiers.
 */
class PNGLegacyOverlayRenderer(context: Context) : VhsRenderer(VhsTier.C) {

    /** Current scanline vertical offset in dp (0..12), animated while seeking. */
    var scanlineOffsetDp: Float = 0f
        private set

    /** Effective overlay alpha = 0.25 * intensity (matches the blueprint snippet). */
    val overlayAlpha: Float
        get() = 0.25f * intensity

    override fun onFrame(timeSeconds: Float) {
        // Loop the scanline offset over a short period for the analog "rolling" look.
        val phase = (timeSeconds % SCROLL_PERIOD_S) / SCROLL_PERIOD_S
        scanlineOffsetDp = phase * MAX_OFFSET_DP
    }

    override fun release() { /* nothing to release */ }

    companion object {
        private const val SCROLL_PERIOD_S = 0.08f  // ~80ms tween, matches Compose spec
        private const val MAX_OFFSET_DP = 12f
    }
}
