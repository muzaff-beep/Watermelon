package com.watermelon.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Coil thumbnail with velocity-guard loading (Manifest §7). While the list is flinging fast,
 * the cheap MediaStore thumbnail is requested; once the scroll settles, the frame-accurate
 * custom fetcher is used. The caller passes [isFlingingFast] from the LazyList scroll state.
 */
@Composable
fun VelocityGuardImage(
    uri: String,
    isFlingingFast: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    // During fast fling we hint Coil to prefer the quick MediaStore thumb (lower memory,
    // cancellable); when settled we allow the precise frame extraction.
    val model = ImageRequest.Builder(LocalPlatformContext())
        .data(uri)
        .crossfade(!isFlingingFast)
        .apply {
            if (isFlingingFast) {
                // velocity guard: tag so the custom fetcher can short-circuit to MediaStore.
                memoryCacheKey("thumb:$uri")
            }
        }
        .build()

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
private fun LocalPlatformContext() = coil3.compose.LocalPlatformContext.current
