package dev.bobbrysonn.atebits.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer

/** Session-wide video playback UI state. */
object VideoPlaybackState {
    // Speaker toggle carries across videos, like the official client:
    // unmute one and the next autoplaying video is unmuted too.
    var muted by mutableStateOf(true)

    // While the fullscreen viewer is open, inline timeline players hold off.
    var viewerOpen by mutableStateOf(false)

    private class Handoff(val mediaId: String?, val player: ExoPlayer)

    private var handoff: Handoff? = null

    // The inline card and the fullscreen viewer pass the live ExoPlayer between
    // them instead of each re-buffering the stream: stash() on the way out,
    // claim() on the way in. An unclaimed player is released on the next stash.
    fun stash(mediaId: String?, player: ExoPlayer) {
        handoff?.player?.release()
        handoff = Handoff(mediaId, player)
    }

    fun claim(mediaId: String?): ExoPlayer? {
        val current = handoff ?: return null
        if (mediaId == null || current.mediaId != mediaId) return null
        handoff = null
        return current.player
    }
}
