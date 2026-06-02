package com.watermelon.common.model

/** A per-video resume point keyed by [mediaUri] + [fileSize]. */
data class PlaybackPosition(
    val mediaUri: String,
    val fileSize: Long,
    val positionMs: Long,
    val updatedAt: Long
)
