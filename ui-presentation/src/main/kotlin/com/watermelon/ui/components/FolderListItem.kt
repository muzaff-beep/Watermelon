package com.watermelon.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.watermelon.ui.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.common.model.FolderNode
import com.watermelon.ui.screens.ItemSize

/**
 * Folder item for list and grid layouts. No thumbnail (thumbnails live on video items).
 * Shows an initial-letter icon, folder name (⭐ if unplayed files exist), file count,
 * and total playtime. Size differences are deliberately dramatic to be tangible.
 */
@Composable
fun FolderListItem(
    folder: FolderNode,
    onClick: (FolderNode) -> Unit,
    modifier: Modifier = Modifier,
    itemSize: ItemSize = ItemSize.MEDIUM,
    isGrid: Boolean = false,
    @Suppress("UNUSED_PARAMETER") isScrollingFast: Boolean = false
) {
    // Size-dependent values — gaps are large so the difference is obvious.
    val iconDp: Dp = when (itemSize) {
        ItemSize.SMALL  -> if (isGrid) 36.dp else 28.dp
        ItemSize.MEDIUM -> if (isGrid) 56.dp else 44.dp
        ItemSize.LARGE  -> if (isGrid) 80.dp else 64.dp
    }
    val hPad: Dp = when (itemSize) {
        ItemSize.SMALL  -> 8.dp
        ItemSize.MEDIUM -> 14.dp
        ItemSize.LARGE  -> 20.dp
    }
    val vPad: Dp = when (itemSize) {
        ItemSize.SMALL  -> 6.dp
        ItemSize.MEDIUM -> 12.dp
        ItemSize.LARGE  -> 18.dp
    }

    val metaText = "${folder.itemCount} files · ${
        if (folder.totalDurationMs > 0L) formatDuration(folder.totalDurationMs) else "--:--"
    }"

    val clickMod = modifier
        .clip(RoundedCornerShape(12.dp))
        .clickable { onClick(folder) }

    if (isGrid) {
        Column(
            modifier = clickMod.fillMaxWidth().padding(hPad, vPad),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FolderIcon(size = iconDp, isPlaylist = folder.isPlaylist)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text      = folder.displayName,
                    color     = MaterialTheme.colorScheme.onSurface,
                    style     = when (itemSize) {
                        ItemSize.SMALL  -> MaterialTheme.typography.bodySmall
                        ItemSize.MEDIUM -> MaterialTheme.typography.bodyMedium
                        ItemSize.LARGE  -> MaterialTheme.typography.bodyLarge
                    },
                    maxLines  = 2,
                    overflow  = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
                if (folder.hasNewFiles) {
                    Icon(
                        painterResource(R.drawable.ic_badge_new),
                        contentDescription = "New files",
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp).padding(start = 2.dp)
                    )
                }
            }
            Text(
                text  = metaText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Row(
            modifier = clickMod.fillMaxWidth().padding(hPad, vPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FolderIcon(size = iconDp, isPlaylist = folder.isPlaylist)
            Spacer(Modifier.width(hPad))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = folder.displayName,
                        color      = MaterialTheme.colorScheme.onSurface,
                        style      = when (itemSize) {
                            ItemSize.SMALL  -> MaterialTheme.typography.bodyMedium
                            ItemSize.MEDIUM -> MaterialTheme.typography.bodyLarge
                            ItemSize.LARGE  -> MaterialTheme.typography.titleMedium
                        },
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    if (folder.hasNewFiles) {
                        Icon(
                            painterResource(R.drawable.ic_badge_new),
                            contentDescription = "New files",
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp).padding(start = 4.dp)
                        )
                    }
                }
                Text(
                    text  = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FolderIcon(size: Dp, isPlaylist: Boolean) {
    val iconRes = if (isPlaylist) R.drawable.ic_playlist else R.drawable.ic_folder
    Icon(
        painter           = painterResource(iconRes),
        contentDescription = null,
        tint              = Color.Unspecified,
        modifier          = Modifier.size(size)
    )
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
