package dev.bobbrysonn.atebits.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bobbrysonn.atebits.data.MediaEntity

/**
 * The active fullscreen image-viewing request. Rendered by MainScreen above
 * the scaffold so it covers the header and bottom navigation, exactly like
 * fullscreen video — and the same shared-state pattern as VideoPlaybackState,
 * since any tweet surface can open it.
 */
object ImageViewerState {
    var viewing by mutableStateOf<ImageViewing?>(null)
}

/**
 * [previewName] is the pbs.twimg.com size variant the tapping surface had on
 * screen; the viewer seeds each page with that already-decoded bitmap (Coil
 * memory-cache key) so opening is seamless instead of flashing while the
 * full-size image loads. [originBounds] holds each image's thumbnail rect in
 * root coordinates, aligned with [images]: the viewer morphs out of the
 * tapped one and shrinks the current page back into its own on dismissal
 * (Rect.Zero entries fall back to a centered scale). [cornerRadius] is the
 * thumbnail frame's rounding, morphed away as the image expands.
 */
data class ImageViewing(
    val images: List<MediaEntity>,
    val initialIndex: Int,
    val previewName: String,
    val originBounds: List<Rect> = emptyList(),
    val cornerRadius: Dp = 0.dp
)
