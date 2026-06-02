package com.watermelon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.FolderNode
import com.watermelon.ui.components.FolderListItem
import com.watermelon.ui.viewmodel.FolderViewModel

/** Layout mode for the folder browser (Manifest §5.3). */
enum class FolderLayout { LIST, GRID }

/** Sort options (Manifest §5.3). */
enum class FolderSort { NAME, DATE, SIZE, RESOLUTION }

/**
 * Folder tree browser with grid/list toggle and sort. Folder-first layout; empty directories
 * are hidden upstream by Phase 1. MVI: refresh routed through the VM (Teams §6).
 */
@Composable
fun FolderBrowserScreen(
    viewModel: FolderViewModel,
    onFolderClick: (FolderNode) -> Unit,
    layout: FolderLayout = FolderLayout.LIST,
    sort: FolderSort = FolderSort.NAME,
    modifier: Modifier = Modifier
) {
    val folders by viewModel.folderTree.collectAsStateWithLifecycle()
    val sorted = folders.sortedWith(sortComparator(sort))

    if (sorted.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No media folders found", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    when (layout) {
        FolderLayout.LIST -> LazyColumn(
            modifier = modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sorted, key = { it.path }) { folder ->
                FolderListItem(folder = folder, onClick = onFolderClick)
            }
        }

        FolderLayout.GRID -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = modifier.fillMaxSize().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            gridItems(sorted, key = { it.path }) { folder ->
                FolderListItem(folder = folder, onClick = onFolderClick)
            }
        }
    }
}

private fun sortComparator(sort: FolderSort): Comparator<FolderNode> = when (sort) {
    FolderSort.NAME -> compareBy { it.displayName.lowercase() }
    FolderSort.SIZE -> compareByDescending { it.itemCount }
    FolderSort.DATE -> compareBy { it.displayName.lowercase() }       // proxy until dates wired
    FolderSort.RESOLUTION -> compareBy { it.displayName.lowercase() } // proxy at folder level
}
