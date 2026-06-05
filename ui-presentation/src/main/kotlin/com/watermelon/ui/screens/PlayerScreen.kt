package com.watermelon.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.UserIntent
import com.watermelon.common.model.VhsTier
import com.watermelon.ui.components.SubtitleOverlay
import com.watermelon.ui.components.VhsSeekBar
import com.watermelon.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

/**
 * Player screen: video surface, a tap-toggleable controls overlay (back / play-pause /
 * scrubber), gesture handling (single tap toggles controls, double-tap left/right seeks
 * +/-10s), the VHS render effect during fast seek, and the subtitle overlay.
 *
 * @param onBack invoked by the back arrow and the system back button; wire to popBackStack().
 * @param vhsRenderEffectProvider supplies the per-frame RenderEffect for Tier A/B, or null.
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    vhsTier: VhsTier,
    vhsIntensity: Float,
    durationMs: Long,
    currentSubtitle: String?,
    surface: @Composable (Modifier) -> Unit,
    onBack: () -> Unit,
    vhsRenderEffectProvider: () -> RenderEffect? = { null },
    modifier: Modifier = Modifier
) {
    val position by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val isSeekingFast by viewModel.isSeekingFast.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    // VERIFY: assumes PlaybackState is an enum with a PLAYING entry. If it's a data class,
    // change this line to e.g. `playbackState.isPlaying`.
    val isPlaying = playbackState == PlaybackState.PLAYING

    var controlsVisible by remember { mutableStateOf(true) }

    // Auto-hide controls after 3s while playing; any toggle restarts the timer.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3_000)
            controlsVisible = false
        }
    }

    // Pause playback when leaving the screen.
    DisposableEffect(Unit) {
        onDispose { viewModel.onIntent(UserIntent.Pause) }
    }

    BackHandler { onBack() }

    val renderEffect = remember(vhsTier, vhsIntensity, isSeekingFast) {
        if (vhsTier == VhsTier.C ||
            vhsIntensity == 0f ||
            !isSeekingFast ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) null else vhsRenderEffectProvider()
    }

    Box(modifier = modifier.fillMaxSize()) {

        // Video surface (host-provided), with the VHS render effect applied during fast seek.
        surface(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.renderEffect = renderEffect }
        )

        // Subtitles sit above the video.
        SubtitleOverlay(currentText = currentSubtitle)

        // Interaction layer: single tap toggles controls; double-tap left/right seeks +/-10s.
        // Clickable children (buttons, scrubber) consume their own touches; empty taps fall
        // through to here.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(durationMs) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val third = size.width / 3f
                            when {
                                offset.x < third ->
                                    viewModel.onIntent(
                                        UserIntent.Seek((position - 10_000).coerceAtLeast(0))
                                    )
                                offset.x > third * 2 ->
                                    viewModel.onIntent(
                                        UserIntent.Seek((position + 10_000).coerceAtMost(durationMs))
                                    )
                                else -> controlsVisible = !controlsVisible
                            }
                        }
                    )
                }
        ) {
            AnimatedVisibility(visible = controlsVisible) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    // Back / quit
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // Play / pause
                    IconButton(
                        onClick = {
                            viewModel.onIntent(
                                if (isPlaying) UserIntent.Pause else UserIntent.Resume
                            )
                        },
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }

                    // Time labels + scrubber (bottom). NOTE: width-bounded, not fillMaxSize —
                    // this is the fix for the seek bar swallowing all touch input.
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(formatTime(position), color = Color.White)
                            Spacer(Modifier.weight(1f))
                            Text(formatTime(durationMs), color = Color.White)
                        }
                        VhsSeekBar(
                            positionMs = position,
                            durationMs = durationMs,
                            onSeek = { viewModel.onIntent(UserIntent.Seek(it)) },
                            onSeekingFastChange = { fast ->
                                viewModel.onIntent(
                                    UserIntent.SetVhsIntensity(if (fast) vhsIntensity else 0f)
                                )
                            },
                            onSeekSpeedChange = { /* 2x/4x/8x tracking */ },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
