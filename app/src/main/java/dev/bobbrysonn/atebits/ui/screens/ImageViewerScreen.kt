package dev.bobbrysonn.atebits.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ImageViewerScreen(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var containerHeight by remember { mutableIntStateOf(0) }
    var dismissing by remember { mutableStateOf(false) }
    // Backdrop fades as the image travels, both while dragging and animating out
    val alpha = (1f - (abs(offsetY.value) / 1000f)).coerceIn(0f, 1f)

    // Slide the image the rest of the way offscreen, then dismiss
    fun animateOutAndDismiss(direction: Float) {
        if (dismissing) return
        dismissing = true
        scope.launch {
            val target = if (containerHeight > 0) containerHeight.toFloat() else 2000f
            offsetY.animateTo(target * direction, tween(250))
            onDismiss()
        }
    }

    BackHandler { animateOutAndDismiss(1f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerHeight = it.height }
            .background(Color.Black.copy(alpha = alpha))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (abs(offsetY.value) > 300) {
                            animateOutAndDismiss(if (offsetY.value > 0) 1f else -1f)
                        } else {
                            scope.launch { offsetY.animateTo(0f, tween(200)) }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (!dismissing) {
                            scope.launch { offsetY.snapTo(offsetY.value + dragAmount.y) }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Full Screen Image",
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetY.value.roundToInt()) },
            contentScale = ContentScale.Fit
        )
    }
}
