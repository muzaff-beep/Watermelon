package com.watermelon.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.UserIntent
import com.watermelon.common.model.VhsTier
import com.watermelon.ui.components.SubtitleOverlay
import com.watermelon.ui.components.VhsSeekBar
import com.watermelon.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

// ─── Enums ───────────────────────────────────────────────────────────────────

/** Screen aspect-ratio modes (Manifest §4.3). */
enum class VideoRatio(val label: String, val ratio: Float?) {
    FILL("Fill", null),
    ORIGINAL("Original", null),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_21_9("21:9", 21f / 9f)
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Screen orientation lock modes. */
enum class ScreenOrientation(val label: String) {
    AUTO("↻ Auto"), PORTRAIT("↑ Port"), LANDSCAPE("↔ Land")
}

// ─── Player screen ────────────────────────────────────────────────────────────

/**
 * Full-screen video player.
 *
 * Gestures:
 *   Tap              → toggle controls (instant, no animation)
 *   Double-tap       → play / pause
 *   Hold left half   → fast rewind (seeks back 5 s every 300 ms) + VHS effect
 *   Hold right half  → 2× fast-forward speed + VHS effect
 *   Horizontal drag  → scrub seek (finger x = playback fraction)
 *   Vertical drag (right half) → system volume
 *   Pinch            → zoom / pan
 *
 * Controls panel stubs: repeat, shuffle, screenshot, sleep timer, mute, PiP,
 * background play, add-to-playlist. Speed and ratio are functional.
 *
 * NOTE: volume control requires <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>
 * NOTE: PiP requires Activity.enterPictureInPictureMode() — wired externally.
 *
 * @param onBack called by the back arrow and the system back gesture.
 * VERIFY: PlaybackState.PLAYING — adjust if PlaybackState is a data class.
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
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    val position      by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val isSeekingFast by viewModel.isSeekingFast.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isPlaying = playbackState == PlaybackState.PLAYING

    var controlsVisible    by remember { mutableStateOf(true) }
    var showControlPanel   by remember { mutableStateOf(false) }
    var playbackSpeed      by remember { mutableFloatStateOf(1f) }
    var currentRatio       by remember { mutableStateOf(VideoRatio.FILL) }
    var scale              by remember { mutableFloatStateOf(1f) }
    var panOffset          by remember { mutableStateOf(Offset.Zero) }
    var isHolding          by remember { mutableStateOf(false) }
    var isPointerDown      by remember { mutableStateOf(false) }
    var holdIsLeft         by remember { mutableStateOf(false) }
    var currentVolume      by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity
    var currentOrientation by rememberSaveable { mutableStateOf(ScreenOrientation.AUTO) }

    // Auto-hide controls while playing (paused during seek or hold).
    LaunchedEffect(controlsVisible, isPlaying, isSeekingFast, isHolding) {
        if (controlsVisible && isPlaying && !isSeekingFast && !isHolding) {
            delay(3_000)
            controlsVisible = false
        }
    }

    // Auto-hide volume indicator.
    LaunchedEffect(showVolumeIndicator) {
        if (showVolumeIndicator) { delay(1_500); showVolumeIndicator = false }
    }

    // Apply screen orientation lock.
    LaunchedEffect(currentOrientation) {
        activity?.requestedOrientation = when (currentOrientation) {
            ScreenOrientation.AUTO      -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ScreenOrientation.PORTRAIT  -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // Hold gesture: left = continuous rewind; right = 2× forward.
    LaunchedEffect(isPointerDown) {
        if (isPointerDown) {
            delay(500L)
            if (isPointerDown) {
                isHolding = true
                if (holdIsLeft) {
                    while (isPointerDown) {
                        viewModel.onIntent(UserIntent.Seek((position - 5_000L).coerceAtLeast(0L)))
                        delay(300L)
                    }
                } else {
                    viewModel.onIntent(UserIntent.SetSpeed(2f))
                }
            }
        } else {
            if (isHolding) {
                isHolding = false
                viewModel.onIntent(UserIntent.SetSpeed(1f))
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onIntent(UserIntent.Pause)
            viewModel.onIntent(UserIntent.SetSpeed(1f))
        }
    }

    BackHandler { onBack() }

    // VHS render effect: active when seeking fast OR holding.
    val renderEffect = remember(vhsTier, vhsIntensity, isSeekingFast, isHolding) {
        val active = (isSeekingFast || isHolding) &&
            vhsTier != VhsTier.C &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (active) vhsRenderEffectProvider() else null
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

        // ── Video surface ─────────────────────────────────────────────────────
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val surfaceMod = when (currentRatio) {
                VideoRatio.FILL, VideoRatio.ORIGINAL -> Modifier.fillMaxSize()
                else -> currentRatio.ratio
                    ?.let { Modifier.fillMaxWidth().aspectRatio(it) }
                    ?: Modifier.fillMaxSize()
            }
            surface(
                surfaceMod.graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = panOffset.x; translationY = panOffset.y
                    this.renderEffect = renderEffect
                }
            )
        }

        // ── Subtitles ─────────────────────────────────────────────────────────
        SubtitleOverlay(
            currentText = currentSubtitle,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (controlsVisible) 80.dp else 24.dp)
        )

        // ── Gesture layer ─────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxSize()

                // 1. Tap / double-tap / long-press start.
                .pointerInput(isPlaying) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            if (!controlsVisible) showControlPanel = false
                        },
                        onDoubleTap = {
                            viewModel.onIntent(
                                if (isPlaying) UserIntent.Pause else UserIntent.Resume
                            )
                            controlsVisible = true
                        },
                        onLongPress = { /* hold handled by awaitEachGesture + LaunchedEffect */ }
                    )
                }

                // 2. Pointer-down / up tracking for hold gesture.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        holdIsLeft = down.position.x < size.width / 2f
                        isPointerDown = true
                        waitForUpOrCancellation()
                        isPointerDown = false
                    }
                }

                // 3. Pinch to zoom + pan when zoomed.
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom != 1f) scale = (scale * zoom).coerceIn(1f, 4f)
                        if (scale > 1f) panOffset = Offset(panOffset.x + pan.x, panOffset.y + pan.y)
                        else panOffset = Offset.Zero
                    }
                }

                // 4. Horizontal drag = seek; right-half vertical drag = volume.
                .pointerInput(durationMs) {
                    var dragStart = Offset.Zero
                    var isHorizontal: Boolean? = null

                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStart = offset
                            isHorizontal = null
                        },
                        onDragEnd    = { isHorizontal = null; controlsVisible = true },
                        onDragCancel = { isHorizontal = null }
                    ) { change, _ ->
                        val delta = change.position - dragStart
                        if (isHorizontal == null &&
                            (abs(delta.x) > 20f || abs(delta.y) > 20f)) {
                            isHorizontal = abs(delta.x) > abs(delta.y)
                        }
                        when (isHorizontal) {
                            true -> {
                                val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                viewModel.onIntent(UserIntent.Seek((frac * durationMs).toLong()))
                            }
                            false -> if (change.position.x > size.width / 2f) {
                                val volFrac = 1f - (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                val newVol = (volFrac * maxVolume).toInt().coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                currentVolume = newVol
                                showVolumeIndicator = true
                            }
                            null -> {}
                        }
                    }
                }
        )

        // ── Controls overlay (instant show / hide) ────────────────────────────
        if (controlsVisible) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {

                // Top bar.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) {
                        Text("← Back", color = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showControlPanel = !showControlPanel }) {
                        Text("⋯", color = Color.White, fontSize = 22.sp)
                    }
                }

                // Expandable control panel.
                if (showControlPanel) {
                    ControlPanel(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 48.dp),
                        currentSpeed  = playbackSpeed,
                        isMuted       = currentVolume == 0,
                        currentRatio  = currentRatio,
                        onSpeedChange = { speed ->
                            playbackSpeed = speed
                            viewModel.onIntent(UserIntent.SetSpeed(speed))
                        },
                        onMuteToggle = {
                            if (currentVolume == 0) {
                                val v = (maxVolume / 2).coerceAtLeast(1)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                                currentVolume = v
                            } else {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                                currentVolume = 0
                            }
                        },
                        onRatioChange  = { currentRatio = it },
                        currentOrientation = currentOrientation,
                        onOrientationChange = { currentOrientation = it },
                        onRepeat       = { /* stub */ },
                        onShuffle      = { /* stub */ },
                        onScreenshot   = { /* stub */ },
                        onSleepTimer   = { viewModel.setSleepTimer(15) }, // 15 minutes
                        onPip          = { /* stub — call Activity.enterPictureInPictureMode() here */ },
                        onBackground   = { /* stub — bind WatermelonPlaybackService */ },
                        onPlaylist     = { /* stub */ }
                    )
                }

                // Play / pause (center).
                TextButton(
                    onClick = {
                        viewModel.onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume)
                    },
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text      = if (isPlaying) "⏸" else "▶",
                        fontSize  = 44.sp,
                        color     = Color.White
                    )
                }

                // Bottom: time labels + ONE seekbar.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatTime(position), color = Color.White)
                        Spacer(Modifier.weight(1f))
                        Text(formatTime(durationMs), color = Color.White)
                    }
                    VhsSeekBar(
                        positionMs         = position,
                        durationMs         = durationMs,
                        onSeek             = { viewModel.onIntent(UserIntent.Seek(it)) },
                        onSeekingFastChange = { fast ->
                            viewModel.onIntent(
                                UserIntent.SetVhsIntensity(if (fast) vhsIntensity else 0f)
                            )
                            controlsVisible = true
                        },
                        onSeekSpeedChange = { },
                        modifier          = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ── Hold indicator ────────────────────────────────────────────────────
        if (isHolding) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text       = if (holdIsLeft) "⏪ 2×" else "2× ⏩",
                    color      = Color.White,
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Volume indicator ──────────────────────────────────────────────────
        if (showVolumeIndicator) {
            VolumeIndicator(
                volume    = currentVolume,
                maxVolume = maxVolume,
                modifier  = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
            )
        }
    }
}

// ─── Control panel ────────────────────────────────────────────────────────────

@Composable
private fun ControlPanel(
    modifier: Modifier,
    currentSpeed: Float,
    isMuted: Boolean,
    currentRatio: VideoRatio,
    onSpeedChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onRatioChange: (VideoRatio) -> Unit,
    currentOrientation: ScreenOrientation,
    onOrientationChange: (ScreenOrientation) -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onScreenshot: () -> Unit,
    onSleepTimer: () -> Unit,
    onPip: () -> Unit,
    onBackground: () -> Unit,
    onPlaylist: () -> Unit
) {
    Column(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.88f),
                RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Speed.
        Text("Speed", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            SPEEDS.forEach { speed ->
                val active = speed == currentSpeed
                TextButton(
                    onClick  = { onSpeedChange(speed) },
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text       = formatSpeed(speed),
                        color      = if (active) MaterialTheme.colorScheme.primary else Color.White,
                        fontSize   = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

        // Ratio.
        Text("Ratio", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            VideoRatio.values().forEach { ratio ->
                val active = ratio == currentRatio
                TextButton(
                    onClick  = { onRatioChange(ratio) },
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text       = ratio.label,
                        color      = if (active) MaterialTheme.colorScheme.primary else Color.White,
                        fontSize   = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

        // Orientation lock.
        Text("Rotate", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ScreenOrientation.values().forEach { orientation ->
                val active = orientation == currentOrientation
                TextButton(
                    onClick  = { onOrientationChange(orientation) },
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text       = orientation.label,
                        color      = if (active) MaterialTheme.colorScheme.primary else Color.White,
                        fontSize   = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

        // Stub controls.
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Stub("🔇", isMuted,  onMuteToggle)
            Stub("🔁", false,    onRepeat)
            Stub("⇌",  false,    onShuffle)
            Stub("📷", false,    onScreenshot)
            Stub("⏱",  false,    onSleepTimer)
            Stub("⊡",  false,    onPip)
            Stub("⬛",  false,    onBackground)
            Stub("+▶", false,    onPlaylist)
        }
    }
}

@Composable
private fun Stub(label: String, active: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(40.dp)) {
        Text(
            text      = label,
            color     = if (active) MaterialTheme.colorScheme.primary else Color.White,
            fontSize  = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ─── Volume indicator ─────────────────────────────────────────────────────────

@Composable
private fun VolumeIndicator(volume: Int, maxVolume: Int, modifier: Modifier) {
    val fraction = if (maxVolume > 0) volume.toFloat() / maxVolume else 0f
    Column(
        modifier = modifier
            .width(36.dp)
            .height(140.dp)
            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
            .padding(6.dp),
        verticalArrangement   = Arrangement.Bottom,
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Text(if (volume == 0) "🔇" else "🔊", fontSize = 12.sp, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(8.dp)
                .weight(1f)
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction.coerceIn(0f, 1f))
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toLong().toFloat()) "${speed.toLong()}×" else "${speed}×"
