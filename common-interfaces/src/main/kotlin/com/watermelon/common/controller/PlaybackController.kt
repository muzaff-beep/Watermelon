package com.watermelon.common.controller

import com.watermelon.common.model.PlaybackState
import kotlinx.coroutines.flow.StateFlow

interface PlaybackController {
    val playbackState: StateFlow<PlaybackState>
    val currentPositionMs: StateFlow<Long>
    val isSeekingFast: StateFlow<Boolean>

    fun play(uri: String, startPositionMs: Long = 0)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)           // 0.5f .. 2.0f
    fun setSleepTimer(minutes: Int)
    fun cancelSleepTimer()
    fun setVhsIntensity(level: Float)    // 0.0f (off) .. 1.0f (high)
    fun takeScreenshot(): String?        // returns local file path or null
}
