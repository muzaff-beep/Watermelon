package com.watermelon.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Seek bar that triggers the VHS effect during fast scrubbing (Manifest §4.3). While the user
 * holds and drags the thumb, [onSeekingFastChange] fires true and [onSeekSpeedChange] reports
 * the 2×/4×/8× tracking multiplier based on drag velocity. The actual ExoPlayer seek is
 * driven through [onSeek]; the VHS overlay is purely decorative.
 */
@Composable
fun VhsSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onSeekingFastChange: (Boolean) -> Unit,
    onSeekSpeedChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    Slider(
        value = fraction.coerceIn(0f, 1f),
        onValueChange = { v ->
            if (!dragging) {
                dragging = true
                onSeekingFastChange(true)
            }
            onSeek((v * durationMs).toLong())
        },
        onValueChangeFinished = {
            dragging = false
            onSeekingFastChange(false)
            onSeekSpeedChange(0)
        },
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        onSeekingFastChange(false)
                        onSeekSpeedChange(0)
                    }
                ) { _, dragAmount ->
                    // Map drag velocity to the analog tape-tracking multiplier.
                    val speed = when {
                        kotlin.math.abs(dragAmount) > 40f -> 8
                        kotlin.math.abs(dragAmount) > 20f -> 4
                        else -> 2
                    }
                    onSeekSpeedChange(speed)
                }
            }
    )
}
