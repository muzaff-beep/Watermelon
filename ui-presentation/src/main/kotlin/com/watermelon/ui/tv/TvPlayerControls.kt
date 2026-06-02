package com.watermelon.ui.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.UserIntent

/**
 * D-Pad player controls (Manifest §8). Left/Right hold accelerates seek (2×/4×/8×) and
 * triggers the VHS animation; Up/Down nudges subtitle sync. Transport buttons are focusable
 * for remote navigation.
 */
@Composable
fun TvPlayerControls(
    onIntent: (UserIntent) -> Unit,
    onSubtitleNudge: (Long) -> Unit,
    onSeekHold: (direction: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeekHold(-1); true }
                    Key.DirectionRight -> { onSeekHold(+1); true }
                    Key.DirectionUp -> { onSubtitleNudge(+100L); true }
                    Key.DirectionDown -> { onSubtitleNudge(-100L); true }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(onClick = { onIntent(UserIntent.Resume) }, modifier = Modifier.focusable()) {
            Text("Play")
        }
        Button(onClick = { onIntent(UserIntent.Pause) }, modifier = Modifier.focusable()) {
            Text("Pause")
        }
    }
}
