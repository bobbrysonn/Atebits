package dev.bobbrysonn.atebits.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.bobbrysonn.atebits.data.MediaEntity
import dev.bobbrysonn.atebits.data.displayAspectRatio
import dev.bobbrysonn.atebits.data.fullSizeUrl
import dev.bobbrysonn.atebits.data.previewUrl
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// M3 emphasized-decelerate: most of the travel lands in the first third,
// near-stationary at the end, no overshoot. The close runs quicker than the
// open, per M3 container-transform guidance.
private val MorphEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private const val OpenDurationMs = 330
private const val CloseDurationMs = 220

/**
 * Fullscreen image pager: swipe horizontally between a tweet's photos (the
 * pager's platform overscroll stretches at either end), drag vertically or
 * press back to dismiss. Each page opens on the timeline's already-decoded
 * preview bitmap ([previewName] keys Coil's memory cache) and swaps to the
 * 2048px original when it lands, so opening never flashes blank.
 *
 * An emphasized-decelerate tween drives a true container transform in both
 * directions. At progress
 * 0 a page renders pixel-identically to its thumbnail — the image is scaled
 * as the same center-crop and clipped to the thumbnail's rounded rect — and
 * the clip, position, and content scale all interpolate to the letterboxed
 * fullscreen fit. The crop rect and fit rect share the image's aspect ratio,
 * so a single uniform scale carries the whole morph and no crossfade is
 * needed. Dismissal runs the same transform back into the *current* page's
 * own thumbnail, from wherever the drag left the image.
 *
 * Rendered by MainScreen above the scaffold — never inside a screen — so it
 * covers the header and bottom navigation like the video viewer.
 */
@Composable
fun ImageViewerScreen(
    images: List<MediaEntity>,
    initialIndex: Int,
    previewName: String,
    originBounds: List<Rect>,
    cornerRadius: Dp,
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

    // 0 = at the thumbnail, 1 = fullscreen; the exit runs the same
    // transform back down to 0
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(OpenDurationMs, easing = MorphEasing))
    }

    // Shrink back into the thumbnail from wherever the drag left the image:
    // the drag offset unwinds on the same clock as the container transform.
    fun animateOutAndDismiss() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            val dragBack = launch {
                offsetY.animateTo(0f, tween(CloseDurationMs, easing = MorphEasing))
            }
            enter.animateTo(0f, tween(CloseDurationMs, easing = MorphEasing))
            dragBack.join()
            onDismiss()
        }
    }

    BackHandler { animateOutAndDismiss() }

    val cornerRadiusPx = with(LocalDensity.current) { cornerRadius.toPx() }

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
        ) { page ->
            val media = images[page]
            val origin = originBounds.getOrElse(page) { Rect.Zero }
            val aspect = media.displayAspectRatio()

            Box(
                // The clip morph lives outside the scaled layer, in screen
                // coordinates: thumbnail rounded rect -> full screen
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val p = enter.value.coerceIn(0f, 1f)
                        if (origin == Rect.Zero || p >= 1f) {
                            drawContent()
                        } else {
                            val clip = lerpRect(origin, Rect(Offset.Zero, size), p)
                            val radius = cornerRadiusPx * (1f - p)
                            clipPath(
                                Path().apply {
                                    addRoundRect(RoundRect(clip, CornerRadius(radius, radius)))
                                }
                            ) { this@drawWithContent.drawContent() }
                        }
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(media.fullSizeUrl() ?: media.mediaUrlHttps)
                        // Show the preview the user was just looking at, straight
                        // from Coil's memory cache, while the original downloads
                        .placeholderMemoryCacheKey(media.previewUrl(previewName))
                        .build(),
                    contentDescription = "Full Screen Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = enter.value
                            if (origin != Rect.Zero) {
                                // The fitted (final) rect and the thumbnail's
                                // center-crop rect share the image's aspect
                                // ratio, so one uniform scale interpolates the
                                // content between them
                                val fitWidth = minOf(size.width, size.height * aspect)
                                val cropWidth = maxOf(origin.width, origin.height * aspect)
                                val width = cropWidth + (fitWidth - cropWidth) * p
                                val scale = width / fitWidth
                                scaleX = scale
                                scaleY = scale
                                val centerX =
                                    origin.center.x + (size.width / 2f - origin.center.x) * p
                                val centerY =
                                    origin.center.y + (size.height / 2f - origin.center.y) * p
                                translationX = centerX - size.width / 2f
                                translationY = centerY - size.height / 2f
                            } else {
                                // No known origin: centered scale + fade
                                val scale = 0.9f + 0.1f * p
                                scaleX = scale
                                scaleY = scale
                                this.alpha = (p * 2f).coerceIn(0f, 1f)
                            }
                        },
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

private fun lerpRect(from: Rect, to: Rect, t: Float): Rect = Rect(
    from.left + (to.left - from.left) * t,
    from.top + (to.top - from.top) * t,
    from.right + (to.right - from.right) * t,
    from.bottom + (to.bottom - from.bottom) * t
)
