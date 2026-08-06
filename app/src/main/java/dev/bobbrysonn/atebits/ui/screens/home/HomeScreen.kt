package dev.bobbrysonn.atebits.ui.screens.home

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FlutterDash
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dev.bobbrysonn.atebits.data.AuthRepository
import dev.bobbrysonn.atebits.data.CurrentUser
import dev.bobbrysonn.atebits.data.TimelineRepository
import dev.bobbrysonn.atebits.data.TweetCache
import dev.bobbrysonn.atebits.data.UiTweet
import dev.bobbrysonn.atebits.data.smallAvatarUrl
import dev.bobbrysonn.atebits.ui.components.LocalListScrollInProgress
import dev.bobbrysonn.atebits.ui.components.PostItem
import dev.bobbrysonn.atebits.ui.screens.ImageViewerScreen
import kotlin.math.roundToInt

// Two-row header like the official client: avatar + centered brand mark,
// then a full-width tab row.
private val HeaderRowHeight = 56.dp
private val TabRowHeight = 48.dp
private val HeaderHeight = HeaderRowHeight + TabRowHeight

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onTweetClick: (UiTweet) -> Unit = {},
    onUserClick: (String) -> Unit = {},
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
    val pullRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    // Collapse-on-scroll: the header rides list scrolls (down hides, up
    // reveals) and settles fully shown or fully hidden after a fling.
    val headerHeightPx = with(LocalDensity.current) { HeaderHeight.toPx() }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }
    val headerScrollConnection = remember(headerHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                headerOffsetPx = (headerOffsetPx + available.y).coerceIn(-headerHeightPx, 0f)
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val target = if (headerOffsetPx < -headerHeightPx / 2) -headerHeightPx else 0f
                animate(headerOffsetPx, target) { value, _ -> headerOffsetPx = value }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // The offset header would otherwise keep drawing above our bounds,
            // over the status bar, when "hidden"
            .clipToBounds()
            .nestedScroll(headerScrollConnection)
    ) {
        if (viewModel.errorMessage != null && viewModel.tweets.isEmpty()) {
            Text(
                text = "Error: ${viewModel.errorMessage}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp).padding(top = HeaderHeight)
            )
        } else if (viewModel.isLoading && viewModel.tweets.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(top = HeaderHeight),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LoadingIndicator()
            }
        } else {
            // TweetVideo defers player creation until scrolling settles
            val scrollInProgress = remember(listState) {
                derivedStateOf { listState.isScrollInProgress }
            }
            CompositionLocalProvider(LocalListScrollInProgress provides scrollInProgress) {
                PullToRefreshBox(
                    isRefreshing = viewModel.isRefreshing,
                    onRefresh = { viewModel.refreshTweets() },
                    state = pullRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        // Default position is the box top, which the header covers
                        PullToRefreshDefaults.Indicator(
                            state = pullRefreshState,
                            isRefreshing = viewModel.isRefreshing,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = HeaderHeight)
                        )
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = HeaderHeight)
                    ) {
                        // Stable keys keep item state (expansion, video) with its
                        // tweet across refresh prepends, and anchor the viewport
                        // instead of jumping; contentType lets the list reuse
                        // tweet slots.
                        items(
                            viewModel.tweets,
                            key = { it.id },
                            contentType = { "tweet" }
                        ) { tweet ->
                            PostItem(
                                tweet = tweet,
                                onImageClick = { url -> selectedImageUrl = url },
                                onTweetClick = onTweetClick,
                                onUserClick = onUserClick
                            )
                        }
                    }
                }
            }
        }

        HomeHeader(
            titles = titles,
            selectedTab = state,
            onTabSelected = { state = it },
            onAvatarClick = onAvatarClick,
            modifier = Modifier.offset { IntOffset(0, headerOffsetPx.roundToInt()) }
        )

        if (selectedImageUrl != null) {
            ImageViewerScreen(
                imageUrl = selectedImageUrl!!,
                onDismiss = { selectedImageUrl = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeHeader(
    titles: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeaderRowHeight)
        ) {
            IconButton(
                onClick = onAvatarClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
            ) {
                val avatarUrl = CurrentUser.profile?.smallAvatarUrl()
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Open menu",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = "Open menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            // Brand mark: a bird, in honor of the app's Tweetie namesake
            Icon(
                imageVector = Icons.Filled.FlutterDash,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(30.dp)
            )
        }
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            modifier = Modifier.height(TabRowHeight)
        ) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = { Text(text = title, style = MaterialTheme.typography.titleMedium) }
                )
            }
        }
    }
}
