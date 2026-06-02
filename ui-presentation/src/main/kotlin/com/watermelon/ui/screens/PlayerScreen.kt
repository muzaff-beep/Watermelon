package com.watermelon.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.UserIntent
import com.watermelon.common.model.VhsTier
import com.watermelon.ui.components.SubtitleOverlay
import com.watermelon.ui.components.VhsSeekBar
import com.watermelon.ui.viewmodel.PlayerViewModel

/**
 * Player screen: video surface, controls overlay, and the VHS overlay. When `isSeekingFast`
 * is true and the tier supports AGSL (A/B), a [android.graphics.RenderEffect] from the active
 * shader is applied to the surface's [graphicsLayer]; Tier C uses an animated PNG overlay
 * (Manifest §4.3). The surface itself is provided by the host (PlayerSurface/PlayerView).
 *
 * @param vhsRenderEffectProvider supplies the per-frame compose RenderEffect for Tier A/B, or
 *        null when off / Tier C. Kept as a lambda so this module needn't depend on
 *        playback-engine's concrete renderers (module boundary rule).
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    vhsTier: VhsTier,
    vhsIntensity: Float,
    durationMs: Long,
    currentSubtitle: String?,
    surface: @Composable (Modifier) -> Unit,
    vhsRenderEffectProvider: () -> androidx.compose.ui.graphics.RenderEffect? = { null },
    modifier: Modifier = Modifier
) {
    val position by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val isSeekingFast by viewModel.isSeekingFast.collectAsStateWithLifecycle()

    val renderEffect = remember(vhsTier, vhsIntensity, isSeekingFast) {
        if (vhsTier == VhsTier.C ||
            vhsIntensity == 0f ||
            !isSeekingFast ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) null else vhsRenderEffectProvider()
    }

    Box(modifier = modifier.fillMaxSize()) {
        surface(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.renderEffect = renderEffect }
        )

        // Tier C animated PNG overlay (minSdk 23 safe) is rendered by the host when seeking;
        // subtitle overlay sits above the video.
        SubtitleOverlay(currentText = currentSubtitle)

        VhsSeekBar(
            positionMs = position,
            durationMs = durationMs,
            onSeek = { viewModel.onIntent(UserIntent.Seek(it)) },
            onSeekingFastChange = { fast ->
                viewModel.onIntent(UserIntent.SetVhsIntensity(if (fast) vhsIntensity else 0f))
            },
            onSeekSpeedChange = { /* 2x/4x/8x tracking; drives overlay intensity if desired */ },
            modifier = Modifier.fillMaxSize()
        )
    }
}
