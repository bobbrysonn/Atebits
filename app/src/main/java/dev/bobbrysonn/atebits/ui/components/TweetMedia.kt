package dev.bobbrysonn.atebits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.bobbrysonn.atebits.data.MediaEntity
import dev.bobbrysonn.atebits.data.displayAspectRatio
import dev.bobbrysonn.atebits.data.isVideo
import dev.bobbrysonn.atebits.data.previewUrl

/**
 * A tweet's media block, official-client style. A lone video plays inline
 * (TweetVideo); a lone photo keeps its natural aspect ratio; 2-4 photos share
 * a 16:9 grid with 2dp gutters (2-up side by side, 3-up as one tall + two
 * stacked, 4-up as a 2x2). Video cells inside a grid show their poster with a
 * play badge and open the fullscreen player on tap; photo taps open the
 * fullscreen pager on that image.
 */
@Composable
fun TweetMedia(
    media: List<MediaEntity>,
    modifier: Modifier = Modifier,
    // Quoted/threaded contexts: smaller corners and the 680px image variant
    compact: Boolean = false
) {
    media.singleOrNull()?.takeIf { it.isVideo }?.let {
        TweetVideo(media = it)
        return
    }

    val photos = media.filter { it.mediaUrlHttps != null }.take(4)
    if (photos.isEmpty()) return

    val shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)
    val frame = modifier
        .fillMaxWidth()
        .clip(shape)
        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)

    // Plain holder keyed by photo index, not compose state: the
    // onGloballyPositioned callbacks fire every scroll frame and only the
    // click handlers read the values
    val cellBounds = remember { HashMap<Int, Rect>() }
    val cornerRadius = if (compact) 12.dp else 16.dp

    photos.singleOrNull()?.let { single ->
        // Timeline slots show the 1200px variant (680px when compact); the
        // viewer opens on this exact cached bitmap, then swaps in full size
        val previewName = if (compact) "small" else "medium"
        AsyncImage(
            model = single.previewUrl(previewName),
            contentDescription = "Tweet Image",
            modifier = frame
                .aspectRatio(single.displayAspectRatio())
                .onGloballyPositioned { cellBounds[0] = it.boundsInRoot() }
                .clickable { openMedia(photos, 0, previewName, cellBounds, cornerRadius) },
            contentScale = ContentScale.Crop
        )
        return
    }

    when (photos.size) {
        2 -> Row(
            modifier = frame.aspectRatio(16f / 9f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MediaCell(photos, 0, cellBounds, Modifier.weight(1f).fillMaxSize())
            MediaCell(photos, 1, cellBounds, Modifier.weight(1f).fillMaxSize())
        }
        3 -> Row(
            modifier = frame.aspectRatio(16f / 9f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MediaCell(photos, 0, cellBounds, Modifier.weight(1f).fillMaxSize())
            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MediaCell(photos, 1, cellBounds, Modifier.weight(1f).fillMaxWidth())
                MediaCell(photos, 2, cellBounds, Modifier.weight(1f).fillMaxWidth())
            }
        }
        else -> Column(
            modifier = frame.aspectRatio(16f / 9f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MediaCell(photos, 0, cellBounds, Modifier.weight(1f).fillMaxSize())
                MediaCell(photos, 1, cellBounds, Modifier.weight(1f).fillMaxSize())
            }
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MediaCell(photos, 2, cellBounds, Modifier.weight(1f).fillMaxSize())
                MediaCell(photos, 3, cellBounds, Modifier.weight(1f).fillMaxSize())
            }
        }
    }
}

// Route a media tap: videos go straight to the fullscreen player; photos open
// the pager on the tapped image, with grid videos filtered out of its pages.
// Every still's thumbnail bounds ride along so the viewer can morph any page
// back into its own cell, not just the tapped one.
private fun openMedia(
    photos: List<MediaEntity>,
    index: Int,
    previewName: String,
    cellBounds: Map<Int, Rect>,
    cornerRadius: Dp
) {
    val tapped = photos[index]
    if (tapped.isVideo) {
        VideoPlaybackState.fullscreenVideo = tapped to 0L
    } else {
        val stills = photos.withIndex().filter { !it.value.isVideo }
        ImageViewerState.viewing = ImageViewing(
            images = stills.map { it.value },
            initialIndex = stills.indexOfFirst { it.index == index }.coerceAtLeast(0),
            previewName = previewName,
            originBounds = stills.map { cellBounds[it.index] ?: Rect.Zero },
            cornerRadius = cornerRadius
        )
    }
}

// One grid slot: cropped 680px image (grid cells are half-width or smaller).
// A video cell wears a play badge and opens the fullscreen player directly —
// grids never bind inline player leases.
@Composable
private fun MediaCell(
    photos: List<MediaEntity>,
    index: Int,
    cellBounds: HashMap<Int, Rect>,
    modifier: Modifier
) {
    val media = photos[index]
    Box(
        modifier = modifier
            .onGloballyPositioned { cellBounds[index] = it.boundsInRoot() }
            // Grid cells are square-cornered, so the morph starts unrounded
            .clickable { openMedia(photos, index, "small", cellBounds, 0.dp) }
    ) {
        AsyncImage(
            model = media.previewUrl("small"),
            contentDescription = if (media.isVideo) "Tweet Video" else "Tweet Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (media.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
