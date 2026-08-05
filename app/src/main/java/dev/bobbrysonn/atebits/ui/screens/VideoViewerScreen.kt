package dev.bobbrysonn.atebits.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import coil.compose.AsyncImage
import dev.bobbrysonn.atebits.data.MediaEntity
import dev.bobbrysonn.atebits.data.bestVideoUrl
import dev.bobbrysonn.atebits.data.previewUrl
import dev.bobbrysonn.atebits.ui.components.VideoPlaybackState
import dev.bobbrysonn.atebits.ui.components.VideoPlayerPool
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Fullscreen video player overlay: same drag/back dismissal as ImageViewerScreen,
// plus play/pause, seek bar, elapsed/total time, and a speaker toggle.
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoViewerScreen(
    media: MediaEntity,
    startPositionMs: Long = 0L,
    onDismiss: () -> Unit
) {
    val videoUrl = media.bestVideoUrl() ?: run { onDismiss(); return }
    val context = LocalContext.current
    val isGif = media.type == "animated_gif"

    val lease = remember {
        // The pool hands back the inline card's live player when there is one
        // (same mediaId): the viewer opens on the current frame with dimensions
        // already known, instead of re-buffering.
        VideoPlayerPool.acquire(context, media.id_str ?: videoUrl, videoUrl).apply {
            if (freshlyPrepared) {
                // Only a cold-started player takes audio focus; changing audio
                // attributes on a live (playing) one would hiccup playback.
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                player.seekTo(startPositionMs)
            }
            player.volume = if (VideoPlaybackState.muted || isGif) 0f else 1f
            player.playWhenReady = true
        }
    }
    val player = lease.player

    // Tell inline timeline players to stand down while the viewer owns playback
    DisposableEffect(Unit) {
        VideoPlaybackState.viewerOpen = true
        onDispose {
            VideoPlaybackState.viewerOpen = false
            // Back to the pool, still bound to this media: with autoplay on,
            // the inline card re-acquires the same slot and resumes seamlessly.
            VideoPlayerPool.release(lease)
        }
    }

    LaunchedEffect(VideoPlaybackState.muted) {
        player.volume = if (VideoPlaybackState.muted || isGif) 0f else 1f
    }

    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(startPositionMs) }
    var durationMs by remember { mutableLongStateOf(media.videoInfo?.durationMillis ?: 0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            if (!scrubbing) positionMs = player.currentPosition.coerceAtLeast(0)
            player.duration.takeIf { it > 0 }?.let { durationMs = it }
            delay(200)
        }
    }

    // Drag-to-dismiss, mirroring ImageViewerScreen
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var containerHeight by remember { mutableIntStateOf(0) }
    var dismissing by remember { mutableStateOf(false) }
    val backdropAlpha = (1f - (abs(offsetY.value) / 1000f)).coerceIn(0f, 1f)

    fun animateOutAndDismiss(direction: Float) {
        if (dismissing) return
        dismissing = true
        player.pause()
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
            .background(Color.Black.copy(alpha = backdropAlpha))
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
        // Sized from the API's aspect ratio so the layout is right on the first
        // frame; the poster covers the surface until the decoder catches up.
        val aspect = media.videoInfo?.aspectRatio
            ?.takeIf { it.size == 2 && it[0] > 0 && it[1] > 0 }
            ?.let { (w, h) -> w.toFloat() / h }
            ?: (16f / 9f)
        val presentationState = rememberPresentationState(player)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
        ) {
            PlayerSurface(
                player = player,
                modifier = Modifier.resizeWithContentScale(
                    ContentScale.Fit,
                    presentationState.videoSizeDp
                )
            )
            if (presentationState.coverSurface) {
                AsyncImage(
                    model = media.previewUrl("large"),
                    contentDescription = "Video thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            if (!isGif) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    IconButton(onClick = { VideoPlaybackState.muted = !VideoPlaybackState.muted }) {
                        Icon(
                            imageVector = if (VideoPlaybackState.muted) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (VideoPlaybackState.muted) "Unmute" else "Mute",
                            tint = Color.White
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }
                Slider(
                    value = if (scrubbing) scrubTarget else positionMs.toFloat(),
                    onValueChange = {
                        scrubbing = true
                        scrubTarget = it
                    },
                    onValueChangeFinished = {
                        player.seekTo(scrubTarget.toLong())
                        positionMs = scrubTarget.toLong()
                        scrubbing = false
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Text(
                    text = "${formatPlaybackTime(if (scrubbing) scrubTarget.toLong() else positionMs)} / ${formatPlaybackTime(durationMs)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

fun formatPlaybackTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
