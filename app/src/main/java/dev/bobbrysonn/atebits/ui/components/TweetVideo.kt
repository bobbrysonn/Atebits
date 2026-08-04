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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import dev.bobbrysonn.atebits.data.MediaEntity
import dev.bobbrysonn.atebits.data.bestVideoUrl

// Poster frame with a play overlay; the ExoPlayer is only created after a tap,
// so list items stay cheap. GIFs loop muted like the official client.
@Composable
fun TweetVideo(media: MediaEntity, modifier: Modifier = Modifier) {
    val videoUrl = media.bestVideoUrl() ?: return
    val isGif = media.type == "animated_gif"
    val aspect = media.videoInfo?.aspectRatio
        ?.takeIf { it.size == 2 && it[0] > 0 && it[1] > 0 }
        ?.let { (w, h) -> w.toFloat() / h }
        ?: (16f / 9f)
    var playing by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
    ) {
        if (!playing) {
            AsyncImage(
                model = media.mediaUrlHttps,
                contentDescription = "Video thumbnail",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { playing = true },
                contentScale = ContentScale.Crop
            )
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
            val badge = if (isGif) "GIF" else media.videoInfo?.durationMillis?.let { formatDuration(it) }
            if (badge != null) {
                Text(
                    text = badge,
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else {
            InlinePlayer(url = videoUrl, loopMuted = isGif)
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun InlinePlayer(url: String, loopMuted: Boolean) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            if (loopMuted) {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
            }
            prepare()
            playWhenReady = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    val presentationState = rememberPresentationState(player)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { if (player.isPlaying) player.pause() else player.play() }
    ) {
        // Must stay in the tree once created or the first-frame event never
        // fires and the shutter never lifts.
        PlayerSurface(
            player = player,
            modifier = Modifier.resizeWithContentScale(
                ContentScale.Fit,
                presentationState.videoSizeDp
            )
        )
        if (presentationState.coverSurface) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
