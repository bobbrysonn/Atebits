package dev.bobbrysonn.atebits.ui.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bobbrysonn.atebits.data.TimelineRepository
import dev.bobbrysonn.atebits.data.TweetResult
import kotlinx.coroutines.launch

class HomeViewModel(
    private val timelineRepository: TimelineRepository
) : ViewModel() {

    var tweets by mutableStateOf<List<TweetResult>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set
    
    var isRefreshing by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        loadTweets()
    }

    fun loadTweets() {
        if (tweets.isNotEmpty()) return // Don't reload if we already have data
        
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                tweets = timelineRepository.getHomeTimeline()
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
                // Fetch new tweets
                val newTweets = timelineRepository.getHomeTimeline()
                
                // Filter out duplicates based on rest_id or tweet.rest_id
                // This is a simple deduplication strategy.
                // Ideally we would use since_id if the API supported it easily, 
                // but merging and deduplicating is safer given our limited API knowledge.
                val currentIds = tweets.mapNotNull { it.rest_id ?: it.tweet?.rest_id }.toSet()
                val uniqueNewTweets = newTweets.filter { 
                    val id = it.rest_id ?: it.tweet?.rest_id
                    id != null && !currentIds.contains(id)
                }

                // Prepend new tweets to the existing list
                tweets = uniqueNewTweets + tweets
            } catch (e: Exception) {
                e.printStackTrace()
                // Don't show full screen error on refresh, maybe a snackbar?
                // For now just log it.
            } finally {
                isRefreshing = false
            }
        }
    }
}
