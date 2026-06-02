package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders the currently-active parsed .srt/.ass cue over the video. The render-time sync
 * [offsetMs] is applied by the caller when selecting [currentText] for the playback position;
 * the source subtitle file is never modified (Manifest §6.3).
 */
@Composable
fun SubtitleOverlay(
    currentText: String?,
    modifier: Modifier = Modifier,
    textSizeSp: Int = 18,
    verticalBiasFromBottom: Float = 0.08f
) {
    if (currentText.isNullOrBlank()) return
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = currentText,
            color = Color.White,
            fontSize = textSizeSp.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(bottom = (verticalBiasFromBottom * 100).dp, start = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
