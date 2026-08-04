package dev.bobbrysonn.atebits.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Session-wide video playback UI state. */
object VideoPlaybackState {
    // Speaker toggle carries across videos, like the official client:
    // unmute one and the next autoplaying video is unmuted too.
    var muted by mutableStateOf(true)

    // While the fullscreen viewer is open, inline timeline players hold off.
    var viewerOpen by mutableStateOf(false)
}
