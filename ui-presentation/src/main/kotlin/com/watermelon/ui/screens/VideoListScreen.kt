package com.watermelon.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.MediaItem
import com.watermelon.ui.components.VelocityGuardImage
import com.watermelon.ui.viewmodel.VideoListViewModel

/**
 * Lists the videos inside a single folder. Tracks scroll velocity and passes it to each
 * VideoRow so VelocityGuardImage can switch between cheap MediaStore thumbs (fast fling)
 * and Coil quality thumbnails (settled).
 */
@Composable
fun VideoListScreen(
    viewModel: VideoListViewModel,
    onVideoClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()

    if (videos.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Indexing videos…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(videos, key = { it.uri }) { item ->
            VideoRow(
                item = item,
                isScrollingFast = isScrolling,
                onClick = { onVideoClick(item) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun VideoRow(
    item: MediaItem,
    isScrollingFast: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VelocityGuardImage(
            uri = item.uri,
            isScrollingFast = isScrollingFast,
            modifier = Modifier
                .size(width = 72.dp, height = 44.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName.ifEmpty { item.uri.substringAfterLast('/') },
                style = MaterialTheme.typography.bodyLarge
            )
            val meta = buildString {
                if (item.durationMs > 0) {
                    val totalSec = item.durationMs / 1000
                    append("%d:%02d".format(totalSec / 60, totalSec % 60))
                }
                if (item.width > 0 && item.height > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${item.width}×${item.height}")
                }
                if (item.fileSize > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${item.fileSize / (1024 * 1024)} MB")
                }
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
