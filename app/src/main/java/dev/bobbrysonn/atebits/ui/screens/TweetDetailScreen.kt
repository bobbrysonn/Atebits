package dev.bobbrysonn.atebits.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import dev.bobbrysonn.atebits.data.TweetCache
import dev.bobbrysonn.atebits.data.TweetDetailCache
import dev.bobbrysonn.atebits.ui.components.PostItem
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
fun TweetDetailScreen(
    tweetId: String,
    initialTweet: TweetResult? = null,
    onBack: () -> Unit,
    onCommentClick: (TweetResult) -> Unit = {}
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository(context) }
    val timelineRepository = remember { TimelineRepository(authRepository) }
    val coroutineScope = rememberCoroutineScope()
    var mainTweet by remember { mutableStateOf<TweetResult?>(initialTweet ?: TweetCache.get(tweetId)) }
    // Seed from cache synchronously: if comments only arrive after the first
    // frame, the restored LazyColumn scroll position gets clamped to the top
    // when navigating back from a sub-comment thread.
    var comments by remember {
        mutableStateOf(TweetDetailCache.get(tweetId)?.replies ?: emptyList())
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val loadDetail: suspend () -> Unit = {
        errorMessage = null
        // Serve cached comments instantly on revisit
        TweetDetailCache.get(tweetId)?.let { cached ->
            cached.mainTweet?.let { mainTweet = it }
            comments = cached.replies
        }
        // Only hit the network when the cache entry is stale or missing
        if (!TweetDetailCache.isFresh(tweetId)) {
            isLoading = comments.isEmpty()
            try {
                val detail = timelineRepository.getTweetDetail(tweetId)
                TweetDetailCache.put(tweetId, detail)
                detail.mainTweet?.let { mainTweet = it }
                comments = detail.replies
            } catch (e: Exception) {
                e.printStackTrace()
                // A failed background refresh shouldn't hide already-shown comments
                if (comments.isEmpty()) {
                    errorMessage = e.message ?: "Unknown error"
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(tweetId) {
        loadDetail()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tweet") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0)
            )
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when {
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error: $errorMessage",
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = { coroutineScope.launch { loadDetail() } }
                        ) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        mainTweet?.let { tweet ->
                            item(key = "focal", contentType = "focal") {
                                PostItem(
                                    tweet = tweet,
                                    showFullText = true,
                                    onImageClick = { url -> selectedImageUrl = url },
                                    // Already on this tweet's detail; only navigate for a
                                    // different tweet (e.g. tapping its quoted tweet card).
                                    onTweetClick = { clicked ->
                                        if (clicked.rest_id != tweetId) onCommentClick(clicked)
                                    }
                                )
                            }
                        }

                        if (comments.isNotEmpty()) {
                            item(key = "replies-label", contentType = "label") {
                                Text(
                                    text = "Replies",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                                )
                            }
                        }

                        if (isLoading && comments.isEmpty()) {
                            item(key = "loading", contentType = "loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LoadingIndicator()
                                }
                            }
                        }

                        items(
                            comments,
                            key = { it.rest_id ?: it.hashCode() },
                            contentType = { "reply" }
                        ) { tweet ->
                            PostItem(
                                tweet = tweet,
                                onImageClick = { url -> selectedImageUrl = url },
                                onTweetClick = onCommentClick,
                                muted = true
                            )
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
}
