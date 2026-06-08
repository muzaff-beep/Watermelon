package com.watermelon.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures video frames and saves them to Watermelon/Screenshots.
 *
 * Two modes:
 * - SINGLE: capture one frame at current position
 * - BURST: capture 9 frames (4 before + current + 4 after)
 *
 * Frame extraction uses MediaMetadataRetriever. Burst mode extracts at:
 * current ± (frameDuration * 4) where frameDuration = duration / frameCount estimate.
 */
object ScreenshotManager {

    enum class Mode { SINGLE, BURST }

    suspend fun takeScreenshot(
        context: Context,
        videoUri: String,
        currentPositionMs: Long,
        durationMs: Long,
        mode: Mode = Mode.SINGLE
    ) = withContext(Dispatchers.IO) {
        val screenshotDir = getScreenshotDirectory(context)
        screenshotDir.mkdirs()

        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(videoUri))

                val frames = when (mode) {
                    Mode.SINGLE -> listOf(currentPositionMs)
                    Mode.BURST -> {
                        val frameDuration = (durationMs / 9).coerceAtLeast(100L)
                        (4 downTo 0).map { (currentPositionMs - frameDuration * (4 - it)).coerceIn(0, durationMs) } +
                        (1..4).map { (currentPositionMs + frameDuration * it).coerceIn(0, durationMs) }
                    }
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val savedFiles = mutableListOf<File>()

                frames.forEachIndexed { index, positionMs ->
                    val bitmap = retriever.getFrameAtTime(
                        positionMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    bitmap?.let { bmp ->
                        val filename = when (mode) {
                            Mode.SINGLE -> "watermelon_${timestamp}.png"
                            Mode.BURST -> "watermelon_${timestamp}_${index + 1}.png"
                        }
                        val file = File(screenshotDir, filename)
                        FileOutputStream(file).use { fos ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        }
                        savedFiles.add(file)
                        bmp.recycle()
                    }
                }

                ScreenshotResult.Success(savedFiles)
            } finally {
                retriever.release()
            }
        }.getOrElse { error ->
            ScreenshotResult.Error(error.message ?: "Unknown error")
        }
    }

    private fun getScreenshotDirectory(context: Context): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Watermelon/Screenshots"
        )
    }
}

sealed class ScreenshotResult {
    data class Success(val files: List<File>) : ScreenshotResult()
    data class Error(val message: String) : ScreenshotResult()
}

