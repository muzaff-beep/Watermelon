package com.watermelon.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.watermelon.ui.R
import com.watermelon.ui.theme.PlayerColors
import kotlin.math.abs

/**
 * Full-screen lock overlay. While shown, it sits ABOVE the player's gesture layer and consumes
 * all touches — nothing reaches playback controls or gestures. Playback continues underneath.
 *
 * Unlock: two lock icons at the bottom-left and bottom-right corners, hidden until the user
 * touches the screen (or a key press flips [revealRequest]). Each is a short vertical slider;
 * the user drags BOTH upward together. They animate symmetrically. When both pass the
 * threshold, [onUnlock] fires. One alone never unlocks — that's the accident-proofing.
 *
 * Home/Power cannot be blocked by any app (Android OS guarantee); this overlay blocks Back
 * (handled by the caller) and the entire touch surface. Optional Screen Pinning (caller-driven)
 * restricts Home/Recents with the user's one-time consent.
 */
@Composable
fun LockOverlay(
    revealRequest: Int,           // increment to force-reveal the handles (e.g. on key press)
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var handlesVisible by remember { mutableStateOf(false) }
    var heightPx by remember { mutableFloatStateOf(1f) }

    // Drag progress for each lock, 0f (down/locked) .. 1f (up/open).
    var leftProgress by remember { mutableFloatStateOf(0f) }
    var rightProgress by remember { mutableFloatStateOf(0f) }

    // Reveal handles whenever the caller bumps revealRequest (key press) — auto-hide after a bit.
    LaunchedEffect(revealRequest) {
        if (revealRequest > 0) {
            handlesVisible = true
            kotlinx.coroutines.delay(3_000)
            if (leftProgress == 0f && rightProgress == 0f) handlesVisible = false
        }
    }

    // Both must exceed the threshold (short throw) to unlock.
    val threshold = 0.6f
    LaunchedEffect(leftProgress, rightProgress) {
        if (leftProgress >= threshold && rightProgress >= threshold) onUnlock()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            // Single handler: any touch on the backdrop reveals the handles and is consumed,
            // so nothing reaches the player beneath. The handles have their own drag handlers.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach {
                            if (it.pressed) { handlesVisible = true; it.consume() }
                        }
                    }
                }
            }
    ) {
        if (handlesVisible) {
            LockHandle(
                progress = leftProgress,
                onDrag = { delta -> leftProgress = (leftProgress - delta / (heightPx * 0.25f)).coerceIn(0f, 1f) },
                onRelease = { if (leftProgress < threshold) leftProgress = 0f },
                modifier = Modifier.align(Alignment.BottomStart).size(64.dp)
            )
            LockHandle(
                progress = rightProgress,
                onDrag = { delta -> rightProgress = (rightProgress - delta / (heightPx * 0.25f)).coerceIn(0f, 1f) },
                onRelease = { if (rightProgress < threshold) rightProgress = 0f },
                modifier = Modifier.align(Alignment.BottomEnd).size(64.dp)
            )
        }
    }
}

@Composable
private fun LockHandle(
    progress: Float,
    onDrag: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(progress, label = "lockSlide")
    // Slide up to 40dp as progress goes 0->1; icon swaps to open near the top.
    Box(
        modifier = modifier
            .graphicsLayer { translationY = -animated * 100f }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { onRelease() },
                    onDragCancel = { onRelease() }
                ) { change, dragAmount ->
                    onDrag(dragAmount)
                    change.consume()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(48.dp)
                .background(PlayerColors.sheetBackground.copy(alpha = 0.85f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(if (progress >= 0.6f) R.drawable.ic_lock_open else R.drawable.ic_lock),
                contentDescription = "Slide to unlock",
                tint = if (progress >= 0.6f) PlayerColors.iconActive else PlayerColors.iconDefault,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
