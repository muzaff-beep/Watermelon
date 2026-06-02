package com.watermelon.common.model

/**
 * A single indexed media file. Identity for resume/subtitles is [uri] + [fileSize]
 * (stable under scoped storage).
 */
data class MediaItem(
    val uri: String,
    val fileSize: Long,
    val displayName: String,
    val parentFolder: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String
)
