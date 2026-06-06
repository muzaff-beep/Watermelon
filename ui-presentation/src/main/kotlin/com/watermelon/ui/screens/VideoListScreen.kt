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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.MediaItem
import com.watermelon.ui.components.VelocityGuardImage
import com.watermelon.ui.viewmodel.VideoListViewModel
import kotlinx.coroutines.delay

private enum class VideoSort(val label: String) { NAME("Name"), DURATION("Duration") }

/**
 * Video list for a single folder.
 * - ⭐ badge on items where [MediaItem.lastPlayedAt] is null (never played).
 * - Tapping a row calls [viewModel.markPlayed] to clear the badge, then [onVideoClick].
 * - Pull-to-refresh triggers [VideoListViewModel.refresh].
 * - S/M/L size picker, sort by name or duration, ascending/descending.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    viewModel: VideoListViewModel,
    onVideoClick: (MediaItem) -> Unit,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()

    var currentSort     by remember { mutableStateOf(VideoSort.NAME) }
    var ascending       by remember { mutableStateOf(true) }
    var currentItemSize by remember { mutableStateOf(ItemSize.MEDIUM) }
    var sortMenuOpen    by remember { mutableStateOf(false) }
    var isRefreshing    by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) { onRefresh(); delay(2_000); isRefreshing = false }
    }

    val sorted = remember(videos, currentSort, ascending) {
        val cmp: Comparator<MediaItem> = when (currentSort) {
            VideoSort.NAME     -> compareBy { it.displayName.lowercase() }
            VideoSort.DURATION -> compareByDescending { it.durationMs }
        }
        videos.sortedWith(if (ascending) cmp else Comparator { a, b -> cmp.compare(b, a) })
    }

    val listState  = rememberLazyListState()
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

    Column(modifier = modifier.fillMaxSize()) {

        // Toolbar.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                TextButton(onClick = { sortMenuOpen = true }) { Text("Sort: ${currentSort.label}") }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    VideoSort.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { currentSort = option; sortMenuOpen = false }
                        )
                    }
                }
            }
            TextButton(onClick = { ascending = !ascending }) { Text(if (ascending) "↑" else "↓") }
            ItemSize.values().forEach { size ->
                TextButton(onClick = { currentItemSize = size }) {
                    Text(
                        text  = size.label,
                        color = if (size == currentItemSize) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Indexing videos…", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { isRefreshing = true },
            modifier     = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state   = listState,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(sorted, key = { it.uri }) { item ->
                    VideoRow(
                        item            = item,
                        itemSize        = currentItemSize,
                        isScrollingFast = isScrolling,
                        onClick         = {
                            viewModel.markPlayed(item.uri)   // clears ⭐ badge
                            onVideoClick(item)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun VideoRow(item: MediaItem, itemSize: ItemSize, isScrollingFast: Boolean, onClick: () -> Unit) {
    val (thumbW, thumbH) = when (itemSize) {
        ItemSize.SMALL  -> 52.dp to 32.dp
        ItemSize.MEDIUM -> 72.dp to 44.dp
        ItemSize.LARGE  -> 96.dp to 60.dp
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VelocityGuardImage(
            uri             = item.uri,
            isScrollingFast = isScrollingFast,
            modifier        = Modifier.size(width = thumbW, height = thumbH).clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text     = item.displayName.ifEmpty { item.uri.substringAfterLast('/') },
                    style    = when (itemSize) {
                        ItemSize.SMALL  -> MaterialTheme.typography.bodyMedium
                        ItemSize.MEDIUM -> MaterialTheme.typography.bodyLarge
                        ItemSize.LARGE  -> MaterialTheme.typography.titleSmall
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // ⭐ new-file badge: shown until the file is played once.
                if (item.lastPlayedAt == null) {
                    Text("⭐", fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
            val meta = buildString {
                val fmt = formatLabel(item.mimeType); if (fmt.isNotEmpty()) append(fmt)
                val q = qualityLabel(item.height); if (q.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(q) }
                if (item.durationMs > 0) { if (isNotEmpty()) append(" · "); append(formatTime(item.durationMs)) }
                if (item.fileSize > 0) { if (isNotEmpty()) append(" · "); append(formatSize(item.fileSize)) }
            }
            if (meta.isNotEmpty()) {
                Text(text = meta, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun qualityLabel(height: Int) = when {
    height >= 2160 -> "4K"; height >= 1080 -> "1080p"; height >= 720 -> "720p"
    height >= 480  -> "480p"; height > 0 -> "SD"; else -> ""
}

private fun formatLabel(mimeType: String): String {
    val raw = mimeType.substringAfterLast('/', "").uppercase()
    return when (raw) {
        "X-MATROSKA" -> "MKV"; "QUICKTIME" -> "MOV"; "X-MSVIDEO" -> "AVI"
        else -> raw.take(8)
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun formatSize(bytes: Long) = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "${bytes / 1_048_576} MB"
    else                    -> "${bytes / 1_024} KB"
}
