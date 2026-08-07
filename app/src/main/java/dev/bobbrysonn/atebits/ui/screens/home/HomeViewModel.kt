package dev.bobbrysonn.atebits.ui.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bobbrysonn.atebits.data.TimelineRepository
import dev.bobbrysonn.atebits.data.UiTweet
import kotlinx.coroutines.launch

class HomeViewModel(
    private val timelineRepository: TimelineRepository
) : ViewModel() {

    var tweets by mutableStateOf<List<UiTweet>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Bumped when the feed should snap to the top: after a manual refresh
    // prepends, the user (already at the top pulling) would otherwise stay
    // anchored on the old first tweet, below the new ones. Background
    // refreshes deliberately do NOT bump it — they must not move the viewport.
    var scrollToTopSignal by mutableIntStateOf(0)
        private set

    // Continuation for the oldest loaded page; null until the first page lands
    private var bottomCursor: String? = null

    private var isBackgroundRefreshing = false

    // When the newest page was last fetched, by any path; throttles the
    // scroll-triggered background refresh
    private var lastRefreshAtMs = 0L

    init {
        loadTweets()
    }

    fun loadTweets() {
        if (tweets.isNotEmpty()) return // Don't reload if we already have data

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val page = timelineRepository.getHomeTimeline()
                tweets = page.tweets
                bottomCursor = page.bottomCursor
                lastRefreshAtMs = System.currentTimeMillis()
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.message ?: "Unknown error"
            } finally {
                isLoading = false
            }
        }
    }

    fun refreshTweets() {
        viewModelScope.launch {
            isRefreshing = true
            try {
                // Fetch a fresh first page and prepend what we haven't seen.
                // Ideally we would use since_id if the API supported it easily,
                // but merging and deduplicating is safer given our limited API
                // knowledge.
                val page = timelineRepository.getHomeTimeline()
                val currentIds = tweets.map { it.id }.toSet()
                tweets = page.tweets.filter { it.id !in currentIds } + tweets
                // Keep paginating below the existing tail; only adopt the fresh
                // cursor when we don't have a continuation yet.
                if (bottomCursor == null) bottomCursor = page.bottomCursor
                lastRefreshAtMs = System.currentTimeMillis()
                // A manual refresh lands with the user at the top: show them
                // the new tweets and retire any pending fresh-tweets badge.
                HomeFeedState.hasFreshTweets = false
                scrollToTopSignal++
            } catch (e: Exception) {
                e.printStackTrace()
                // Don't show full screen error on refresh, maybe a snackbar?
                // For now just log it.
            } finally {
                isRefreshing = false
            }
        }
    }

    /**
     * Silent variant of refreshTweets for when the user has scrolled deep into
     * a stale feed: prepends unseen tweets without touching the viewport
     * (stable keys keep it anchored) and raises the fresh-tweets badge instead
     * of the refresh spinner. Throttled so scroll jitter can't spam the API.
     */
    fun maybeRefreshInBackground() {
        if (isLoading || isRefreshing || isBackgroundRefreshing || tweets.isEmpty()) return
        // The previous batch hasn't been seen yet; don't stack another on top
        if (HomeFeedState.hasFreshTweets) return
        if (System.currentTimeMillis() - lastRefreshAtMs < BACKGROUND_REFRESH_MIN_INTERVAL_MS) return

        viewModelScope.launch {
            isBackgroundRefreshing = true
            try {
                val page = timelineRepository.getHomeTimeline()
                val currentIds = tweets.map { it.id }.toSet()
                val fresh = page.tweets.filter { it.id !in currentIds }
                if (fresh.isNotEmpty()) {
                    tweets = fresh + tweets
                    HomeFeedState.hasFreshTweets = true
                }
                if (bottomCursor == null) bottomCursor = page.bottomCursor
                lastRefreshAtMs = System.currentTimeMillis()
            } catch (e: Exception) {
                e.printStackTrace() // Silent by design; next trigger retries
            } finally {
                isBackgroundRefreshing = false
            }
        }
    }

    fun markFreshTweetsSeen() {
        HomeFeedState.hasFreshTweets = false
    }

    fun loadMoreTweets() {
        val cursor = bottomCursor ?: return
        if (isLoadingMore || isRefreshing || isLoading) return

        viewModelScope.launch {
            isLoadingMore = true
            try {
                val page = timelineRepository.getHomeTimeline(cursor)
                val currentIds = tweets.map { it.id }.toSet()
                tweets = tweets + page.tweets.filter { it.id !in currentIds }
                bottomCursor = page.bottomCursor
            } catch (e: Exception) {
                e.printStackTrace() // Keep the current list; retry on next trigger
            } finally {
                isLoadingMore = false
            }
        }
    }

    companion object {
        private const val BACKGROUND_REFRESH_MIN_INTERVAL_MS = 2 * 60_000L
    }
}
