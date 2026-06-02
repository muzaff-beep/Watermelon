package com.watermelon.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.UserIntent
import kotlinx.coroutines.flow.StateFlow

/**
 * MVI ViewModel for the player. Delegates to the [PlaybackController] interface only (no
 * dependency on the concrete playback-engine, per the module boundary rules) and re-exposes
 * its state for the UI to collect.
 */
class PlayerViewModel(
    private val controller: PlaybackController
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = controller.playbackState
    val currentPositionMs: StateFlow<Long> = controller.currentPositionMs
    val isSeekingFast: StateFlow<Boolean> = controller.isSeekingFast

    fun onIntent(intent: UserIntent) {
        when (intent) {
            is UserIntent.Play -> controller.play(intent.uri)
            is UserIntent.Seek -> controller.seekTo(intent.positionMs)
            is UserIntent.SetSpeed -> controller.setSpeed(intent.speed)
            UserIntent.Pause -> controller.pause()
            UserIntent.Resume -> controller.resume()
            is UserIntent.SetVhsIntensity -> controller.setVhsIntensity(intent.level)
            UserIntent.RefreshLibrary -> Unit
        }
    }

    fun setSleepTimer(minutes: Int) = controller.setSleepTimer(minutes)
    fun cancelSleepTimer() = controller.cancelSleepTimer()
    fun takeScreenshot(): String? = controller.takeScreenshot()
}
