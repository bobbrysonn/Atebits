package dev.bobbrysonn.atebits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import coil.compose.AsyncImage
import dev.bobbrysonn.atebits.data.AppSettings
import dev.bobbrysonn.atebits.data.MediaEntity
import dev.bobbrysonn.atebits.data.bestVideoUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Inline tweet video. With autoplay on (the default) the video starts muted
 * once at least half of it scrolls into view and stops when it leaves; the
 * player is created lazily so off-screen list items cost nothing. With
 * autoplay off it stays a poster frame. Tapping either opens the fullscreen
 * viewer via [onVideoClick], carrying the current position along.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TweetVideo(
    media: MediaEntity,
    modifier: Modifier = Modifier,
    onVideoClick: (MediaEntity, Long) -> Unit = { _, _ -> }
) {
    val videoUrl = media.bestVideoUrl() ?: return
    val isGif = media.type == "animated_gif"
    val aspect = media.videoInfo?.aspectRatio
        ?.takeIf { it.size == 2 && it[0] > 0 && it[1] > 0 }
        ?.let { (w, h) -> w.toFloat() / h }
        ?: (16f / 9f)
    val context = LocalContext.current

    var visible by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val shouldPlay = AppSettings.autoplayVideos && visible && !VideoPlaybackState.viewerOpen

    LaunchedEffect(shouldPlay) {
        if (shouldPlay && player == null) {
            // No audio-focus handling: a muted timeline video must not pause
            // whatever the user is listening to. The fullscreen viewer takes
            // focus properly.
            player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
            }
        }
        player?.playWhenReady = shouldPlay
    }

    LaunchedEffect(player, VideoPlaybackState.muted) {
        player?.volume = if (isGif || VideoPlaybackState.muted) 0f else 1f
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player?.playWhenReady = false
                Lifecycle.Event.ON_RESUME -> player?.playWhenReady = shouldPlay
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player?.release()
        }
    }

    // Decrementing time-left counter for the bottom-left badge
    var remainingMs by remember {
        mutableLongStateOf(media.videoInfo?.durationMillis ?: 0L)
    }
    LaunchedEffect(player, shouldPlay) {
        val p = player ?: return@LaunchedEffect
        while (isActive) {
            val duration = p.duration.takeIf { it > 0 }
                ?: media.videoInfo?.durationMillis ?: 0L
            remainingMs = (duration - p.currentPosition).coerceAtLeast(0)
            delay(250)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .onVisibilityChanged(minFractionVisible = 0.5f) { visible = it }
            .clickable { onVideoClick(media, player?.currentPosition ?: 0L) }
    ) {
        val activePlayer = player
        if (activePlayer != null) {
            val presentationState = rememberPresentationState(activePlayer)
            PlayerSurface(
                player = activePlayer,
                modifier = Modifier.resizeWithContentScale(
                    ContentScale.Fit,
                    presentationState.videoSizeDp
                )
            )
            if (presentationState.coverSurface) {
                Poster(media)
            }
        } else {
            Poster(media)
            if (!AppSettings.autoplayVideos) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        OverlayBadge(
            text = if (isGif) "GIF" else formatDuration(remainingMs),
            modifier = Modifier.align(Alignment.BottomStart)
        )

        if (!isGif && activePlayer != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    .clickable { VideoPlaybackState.muted = !VideoPlaybackState.muted },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (VideoPlaybackState.muted) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = if (VideoPlaybackState.muted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun Poster(media: MediaEntity) {
    AsyncImage(
        model = media.mediaUrlHttps,
        contentDescription = "Video thumbnail",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun OverlayBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
