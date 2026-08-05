package dev.bobbrysonn.atebits.ui.components

import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.bobbrysonn.atebits.data.MediaEntity

/** Session-wide video playback UI state. */
object VideoPlaybackState {
    // Speaker toggle carries across videos, like the official client:
    // unmute one and the next autoplaying video is unmuted too.
    var muted by mutableStateOf(true)

    // While the fullscreen viewer is open, inline timeline players hold off.
    var viewerOpen by mutableStateOf(false)

    // A tap on an inline video requests the fullscreen viewer (media + start
    // position). MainScreen renders it above the whole scaffold so it covers
    // the tab and navigation bars.
    var fullscreenVideo by mutableStateOf<Pair<MediaEntity, Long>?>(null)
}

// Whether the enclosing lazy list is mid-scroll. TweetVideo defers building a
// player until this settles, so flings stay on the poster-image path. It's a
// State<Boolean> (not a raw Boolean) so only `.value` readers recompose on
// scroll start/stop. The default means "settled": screens that don't provide
// it keep play-on-visible behavior.
val LocalListScrollInProgress = compositionLocalOf<State<Boolean>> { mutableStateOf(false) }
