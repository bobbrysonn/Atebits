package dev.bobbrysonn.atebits.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.bobbrysonn.atebits.data.MediaEntity
import dev.bobbrysonn.atebits.data.fullSizeUrl
import dev.bobbrysonn.atebits.data.previewUrl
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ~100ms slower than the previous 280 (which was ~100ms slower than the
// StiffnessMediumLow default of 400): spring settle time scales with
// 1/sqrt(stiffness)
private const val EnterStiffness = 205f

/**
 * Fullscreen image pager: swipe horizontally between a tweet's photos (the
 * pager's platform overscroll stretches at either end), drag vertically or
 * press back to dismiss. Each page opens on the timeline's already-decoded
 * preview bitmap ([previewName] keys Coil's memory cache) and swaps to the
 * 2048px original when it lands, so opening never flashes blank.
 *
 * A spring drives a container transform in both directions: the image grows
 * out of the tapped thumbnail's [originBounds] with a slight overshoot, and
 * a dismissal shrinks it back into those bounds (from wherever the drag left
 * it) instead of sliding offscreen.
 *
 * Rendered by MainScreen above the scaffold — never inside a screen — so it
 * covers the header and bottom navigation like the video viewer.
 */
@Composable
fun ImageViewerScreen(
    images: List<MediaEntity>,
    initialIndex: Int,
    previewName: String,
    originBounds: Rect,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    ) { images.size }

    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }
    // Backdrop fades as the image travels with the drag
    val dragAlpha = (1f - (abs(offsetY.value) / 1000f)).coerceIn(0f, 1f)

    // 0 = at the thumbnail, 1 = fullscreen. The entrance overshoots past 1
    // (springy); the exit runs the same transform back down to 0.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = EnterStiffness)
        )
    }

    // Shrink back into the thumbnail from wherever the drag left the image:
    // the drag offset unwinds on the same clock as the container transform.
    fun animateOutAndDismiss() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            val dragBack = launch {
                offsetY.animateTo(
                    0f,
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = EnterStiffness)
                )
            }
            enter.animateTo(
                0f,
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = EnterStiffness)
            )
            dragBack.join()
            onDismiss()
        }
    }

    BackHandler { animateOutAndDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = dragAlpha * enter.value.coerceIn(0f, 1f)))
            // Vertical drags only: the pager owns the horizontal axis
            .pointerInput(Unit) {
                // Cumulative drag lives outside the animatable: snapTo runs in
                // launched coroutines, so on a fast fling onDragEnd would read
                // offsetY before the queued snaps land and miss the dismissal.
                var dragTotal = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        if (abs(dragTotal) > 300) {
                            animateOutAndDismiss()
                        } else {
                            scope.launch { offsetY.animateTo(0f, tween(200)) }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (!dismissing) {
                            dragTotal += dragAmount
                            val target = dragTotal
                            scope.launch { offsetY.snapTo(target) }
                        }
                    }
                )
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .graphicsLayer {
                    val p = enter.value
                    if (originBounds != Rect.Zero) {
                        // Container transform: uniform scale + translation
                        // from the thumbnail rect to (and past, on overshoot)
                        // the centered fullscreen frame
                        val start = minOf(
                            originBounds.width / size.width,
                            originBounds.height / size.height
                        )
                        val scale = start + (1f - start) * p
                        scaleX = scale
                        scaleY = scale
                        translationX = (originBounds.center.x - size.width / 2f) * (1f - p)
                        translationY = (originBounds.center.y - size.height / 2f) * (1f - p)
                    } else {
                        val scale = 0.9f + 0.1f * p
                        scaleX = scale
                        scaleY = scale
                    }
                    // Fully opaque by the halfway point, so the crossover from
                    // the real thumbnail underneath is early and clean
                    this.alpha = (p * 2f).coerceIn(0f, 1f)
                }
        ) { page ->
            val media = images[page]
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(media.fullSizeUrl() ?: media.mediaUrlHttps)
                    // Show the preview the user was just looking at, straight
                    // from Coil's memory cache, while the original downloads
                    .placeholderMemoryCacheKey(media.previewUrl(previewName))
                    .build(),
                contentDescription = "Full Screen Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
