package dev.bobbrysonn.atebits.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.bobbrysonn.atebits.data.AuthRepository
import dev.bobbrysonn.atebits.data.MediaEntity
import dev.bobbrysonn.atebits.data.TimelineRepository
import dev.bobbrysonn.atebits.data.TweetResult
import dev.bobbrysonn.atebits.data.TweetCache
import dev.bobbrysonn.atebits.ui.components.PostItem
import dev.bobbrysonn.atebits.ui.screens.ImageViewerScreen
import dev.bobbrysonn.atebits.ui.screens.VideoViewerScreen
import kotlinx.coroutines.launch

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onTweetClick: (TweetResult) -> Unit = {},
    onAvatarClick: () -> Unit = {}
) {
    var state by remember { mutableIntStateOf(0) }
    val titles = listOf("For You", "Following")
    
    val context = LocalContext.current
    // We need to manually provide the factory because TimelineRepository needs AuthRepository
    val authRepository = remember { AuthRepository(context) }
    val timelineRepository = remember { TimelineRepository(authRepository) }
    
    val viewModel: HomeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(timelineRepository) as T
            }
        }
    )

    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    var selectedVideo by remember { mutableStateOf<Pair<MediaEntity, Long>?>(null) }
    val pullRefreshState = rememberPullToRefreshState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Placeholder account icon until we fetch the signed-in user's profile
                IconButton(onClick = onAvatarClick) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = "Open menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
                PrimaryTabRow(selectedTabIndex = state, modifier = Modifier.weight(1f)) {
                    titles.forEachIndexed { index, title ->
                        Tab(
                            selected = state == index,
                            onClick = { state = index },
                            text = { Text(text = title) }
                        )
                    }
                }
            }
            
            if (viewModel.errorMessage != null && viewModel.tweets.isEmpty()) {
                Text(
                    text = "Error: ${viewModel.errorMessage}",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (viewModel.isLoading && viewModel.tweets.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LoadingIndicator()
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = viewModel.isRefreshing,
                    onRefresh = { viewModel.refreshTweets() },
                    state = pullRefreshState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(viewModel.tweets) { tweet ->
                            PostItem(
                                tweet = tweet,
                                onImageClick = { url -> selectedImageUrl = url },
                                onTweetClick = onTweetClick,
                                onVideoClick = { media, positionMs ->
                                    selectedVideo = media to positionMs
                                }
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

        selectedVideo?.let { (media, positionMs) ->
            VideoViewerScreen(
                media = media,
                startPositionMs = positionMs,
                onDismiss = { selectedVideo = null }
            )
        }
    }
}
