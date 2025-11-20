package dev.bobbrysonn.atebits.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.bobbrysonn.atebits.data.AuthRepository
import dev.bobbrysonn.atebits.data.TimelineRepository
import dev.bobbrysonn.atebits.data.TweetResult
import dev.bobbrysonn.atebits.ui.components.PostItem
import kotlinx.coroutines.launch

@Composable
fun TweetDetailScreen(
    tweetId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository(context) }
    val timelineRepository = remember { TimelineRepository(authRepository) }
    val coroutineScope = rememberCoroutineScope()
    var tweets by remember { mutableStateOf<List<TweetResult>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    suspend fun fetchTweetDetail() {
        isLoading = true
        errorMessage = null
        try {
            tweets = timelineRepository.getTweetDetail(tweetId)
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = e.message ?: "Unknown error"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(tweetId) {
        fetchTweetDetail()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tweet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )

            when {
                isLoading && tweets.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 24.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error: $errorMessage",
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = { coroutineScope.launch { fetchTweetDetail() } }
                        ) {
                            Text("Retry")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(tweets) { tweet ->
                            PostItem(
                                tweet = tweet,
                                onImageClick = { url -> selectedImageUrl = url },
                                onTweetClick = { /* Already on detail */ }
                            )
                        }
                    }
                }
            }
        }

        if (selectedImageUrl != null) {
            ImageViewerScreen(
                imageUrl = selectedImageUrl!!,
                onDismiss = { selectedImageUrl = null }
            )
        }
    }
}
