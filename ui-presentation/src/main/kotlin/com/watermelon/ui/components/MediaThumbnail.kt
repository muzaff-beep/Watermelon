package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Renders a video thumbnail from a media [uri]. The primary-color background is always
 * present and shows through whenever the load is null, pending, or failed.
 */
@Composable
fun MediaThumbnail(uri: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.primary),
    )
}
