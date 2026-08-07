package dev.bobbrysonn.atebits.ui.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Home-feed signals shared between the navigation bar (MainScreen) and the
 * feed (HomeScreen/HomeViewModel), which sit too far apart in the tree to
 * hoist state through parameters — same pattern as VideoPlaybackState.
 */
object HomeFeedState {
    // A background refresh prepended tweets the user hasn't seen yet; the
    // Home navigation item shows a dot until they reach the top of the feed.
    var hasFreshTweets by mutableStateOf(false)

    // Bumped on each re-tap of Home while the home screen is already showing;
    // the feed responds by scrolling to the top.
    var homeReselects by mutableIntStateOf(0)
}
