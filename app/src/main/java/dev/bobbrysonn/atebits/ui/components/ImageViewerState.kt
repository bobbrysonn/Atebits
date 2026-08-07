package dev.bobbrysonn.atebits.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
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
 * full-size image loads. [originBounds] is the tapped thumbnail's rect in
 * root coordinates: the viewer expands from it and shrinks back to it on
 * dismissal (Rect.Zero falls back to a centered scale).
 */
data class ImageViewing(
    val images: List<MediaEntity>,
    val initialIndex: Int,
    val previewName: String,
    val originBounds: Rect = Rect.Zero
)
