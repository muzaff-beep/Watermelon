package com.watermelon.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.MediaItem
import com.watermelon.ui.R
import com.watermelon.ui.components.VelocityGuardImage
import com.watermelon.ui.components.WatermelonLoadingAnimation
import com.watermelon.ui.viewmodel.VideoListViewModel
import kotlinx.coroutines.delay

private enum class VideoSort(val label: String) { NAME("Name"), DURATION("Duration") }
private enum class VideoLayout { LIST, GRID }

private val LayoutSaver = androidx.compose.runtime.saveable.Saver<VideoLayout, String>(
    save    = { it.name },
    restore = { VideoLayout.valueOf(it) }
)
private val SortSaver2 = androidx.compose.runtime.saveable.Saver<VideoSort, String>(
    save    = { it.name },
    restore = { VideoSort.valueOf(it) }
)
private val SizeSaver2 = androidx.compose.runtime.saveable.Saver<ItemSize, String>(
    save    = { it.name },
    restore = { ItemSize.valueOf(it) }
)

/**
 * Video list for a single folder or playlist.
 * Supports grid/list toggle, sort, size picker, pull-to-refresh, and ⭐ new-file badge.
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

    var currentSort     by rememberSaveable(stateSaver = SortSaver2)   { mutableStateOf(VideoSort.NAME) }
    var ascending       by rememberSaveable { mutableStateOf(true) }
    var currentItemSize by rememberSaveable(stateSaver = SizeSaver2)   { mutableStateOf(ItemSize.MEDIUM) }
    var currentLayout   by rememberSaveable(stateSaver = LayoutSaver)  { mutableStateOf(VideoLayout.LIST) }
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

    val listState    = rememberLazyListState()
    val gridState    = rememberLazyGridState()
    val isGrid       = currentLayout == VideoLayout.GRID
    val isScrolling  by remember { derivedStateOf { listState.isScrollInProgress || gridState.isScrollInProgress } }

    val gridColumns = when (currentItemSize) {
        ItemSize.SMALL  -> GridCells.Fixed(3)
        ItemSize.MEDIUM -> GridCells.Fixed(2)
        ItemSize.LARGE  -> GridCells.Fixed(2)
    }

    Column(modifier = modifier.fillMaxSize()) {

        // ── Toolbar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grid/list toggle
            IconButton(onClick = {
                currentLayout = if (isGrid) VideoLayout.LIST else VideoLayout.GRID
            }) {
                Icon(
                    painterResource(if (isGrid) R.drawable.ic_view_list else R.drawable.ic_view_grid),
                    contentDescription = if (isGrid) "List view" else "Grid view",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Sort dropdown
            Box {
                TextButton(onClick = { sortMenuOpen = true }) { Text("Sort: ${currentSort.label}") }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    VideoSort.values().forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.label) },
                            onClick = { currentSort = opt; sortMenuOpen = false }
                        )
                    }
                }
            }

            // Ascending/descending
            IconButton(onClick = { ascending = !ascending }) {
                Icon(
                    painterResource(if (ascending) R.drawable.ic_sort_ascending else R.drawable.ic_sort_descending),
                    contentDescription = if (ascending) "Ascending" else "Descending",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Size picker
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

        HorizontalDivider()

        // ── Content ───────────────────────────────────────────────────────────
        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WatermelonLoadingAnimation(modifier = Modifier.size(160.dp))
            }
            return@Column
        }

        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { isRefreshing = true }) {
            when (currentLayout) {
                VideoLayout.LIST -> LazyColumn(
                    state   = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(sorted, key = { it.uri }) { item ->
                        VideoListItem(
                            item            = item,
                            itemSize        = currentItemSize,
                            isGrid          = false,
                            isScrollingFast = isScrolling,
                            onClick         = {
                                viewModel.markPlayed(item.uri)
                                onVideoClick(item)
                            }
                        )
                    }
                }

                VideoLayout.GRID -> LazyVerticalGrid(
                    state   = gridState,
                    columns = gridColumns,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    gridItems(sorted, key = { it.uri }) { item ->
                        VideoListItem(
                            item            = item,
                            itemSize        = currentItemSize,
                            isGrid          = true,
                            isScrollingFast = isScrolling,
                            onClick         = {
                                viewModel.markPlayed(item.uri)
                                onVideoClick(item)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Video item composable ─────────────────────────────────────────────────────

@Composable
private fun VideoListItem(
    item: MediaItem,
    itemSize: ItemSize,
    isGrid: Boolean,
    isScrollingFast: Boolean,
    onClick: () -> Unit
) {
    val thumbH: Dp = when (itemSize) {
        ItemSize.SMALL  -> if (isGrid) 72.dp  else 40.dp
        ItemSize.MEDIUM -> if (isGrid) 120.dp else 64.dp
        ItemSize.LARGE  -> if (isGrid) 180.dp else 96.dp
    }
    val textStyle = when (itemSize) {
        ItemSize.SMALL  -> MaterialTheme.typography.bodySmall
        ItemSize.MEDIUM -> MaterialTheme.typography.bodyMedium
        ItemSize.LARGE  -> MaterialTheme.typography.bodyLarge
    }

    if (isGrid) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VelocityGuardImage(
                uri             = item.uri,
                durationMs      = item.durationMs,
                isScrollingFast = isScrollingFast,
                modifier        = Modifier.fillMaxWidth().height(thumbH).clip(RoundedCornerShape(6.dp))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text      = item.displayName,
                    style     = textStyle,
                    maxLines  = 2,
                    overflow  = TextOverflow.Ellipsis,
                    modifier  = Modifier.weight(1f)
                )
                if (item.lastPlayedAt == null) {
                    Icon(
                        painterResource(R.drawable.ic_badge_new),
                        contentDescription = "New",
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp).padding(start = 4.dp)
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VelocityGuardImage(
                uri             = item.uri,
                durationMs      = item.durationMs,
                isScrollingFast = isScrollingFast,
                modifier        = Modifier
                    .width(thumbH * 16f / 9f)
                    .height(thumbH)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text     = item.displayName,
                        style    = textStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.lastPlayedAt == null) {
                        Icon(
                            painterResource(R.drawable.ic_badge_new),
                            contentDescription = "New",
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp).padding(start = 4.dp)
                        )
                    }
                }
                Text(
                    text  = formatDuration(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
