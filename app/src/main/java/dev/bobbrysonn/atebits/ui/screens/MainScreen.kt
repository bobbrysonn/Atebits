package dev.bobbrysonn.atebits.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.bobbrysonn.atebits.data.AuthRepository
import dev.bobbrysonn.atebits.data.CurrentUser
import dev.bobbrysonn.atebits.data.TimelineRepository
import dev.bobbrysonn.atebits.data.smallAvatarUrl
import dev.bobbrysonn.atebits.ui.components.ImageViewerState
import dev.bobbrysonn.atebits.ui.components.VideoPlaybackState
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import dev.bobbrysonn.atebits.ui.screens.home.HomeFeedState
import dev.bobbrysonn.atebits.ui.screens.home.HomeScreen

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.bobbrysonn.atebits.data.TweetCache

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Notifications", "Messages")
    val icons = listOf(
        Icons.Filled.Home to Icons.Outlined.Home,
        Icons.Filled.Search to Icons.Outlined.Search,
        Icons.Filled.Notifications to Icons.Outlined.Notifications,
        Icons.Filled.Email to Icons.Outlined.Email
    )
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    // M3's drawer doesn't intercept back on its own; without this, back with
    // the drawer open exits the app.
    BackHandler(enabled = drawerState.isOpen) {
        drawerScope.launch { drawerState.close() }
    }

    // Fetch the signed-in user once per session for the avatar + drawer header
    val context = LocalContext.current
    val authRepository = remember { AuthRepository(context) }
    LaunchedEffect(Unit) {
        if (CurrentUser.profile == null) {
            val repository = TimelineRepository(authRepository)
            try {
                CurrentUser.profile = repository.getCurrentUser()
            } catch (e: Exception) {
                e.printStackTrace() // Non-fatal: UI falls back to a placeholder icon
            }
        }
    }

    Box {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    DrawerHeader(
                        onClick = {
                            authRepository.getUserId()?.let { userId ->
                                drawerScope.launch { drawerState.close() }
                                navController.navigate("profile/$userId")
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        selected = false,
                        onClick = {
                            drawerScope.launch { drawerState.close() }
                            navController.navigate("settings")
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        ) {
            MainScaffold(
                navController = navController,
                selectedItem = selectedItem,
                onSelectItem = { selectedItem = it },
                items = items,
                icons = icons,
                onOpenDrawer = { drawerScope.launch { drawerState.open() } }
            )
        }

        // Fullscreen media viewers live above the drawer and scaffold so they
        // cover the tab row and bottom navigation bar, like the official client.
        ImageViewerState.viewing?.let { viewing ->
            ImageViewerScreen(
                images = viewing.images,
                initialIndex = viewing.initialIndex,
                previewName = viewing.previewName,
                originBounds = viewing.originBounds,
                onDismiss = { ImageViewerState.viewing = null }
            )
        }

        VideoPlaybackState.fullscreenVideo?.let { (media, positionMs) ->
            VideoViewerScreen(
                media = media,
                startPositionMs = positionMs,
                onDismiss = { VideoPlaybackState.fullscreenVideo = null }
            )
        }
    }
}

@Composable
private fun DrawerHeader(onClick: () -> Unit = {}) {
    val profile = CurrentUser.profile
    // Surface(onClick) makes the whole block one ripple-backed touch target
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 12.dp)
        ) {
        val avatarUrl = profile?.smallAvatarUrl()
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Your account",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = "Your account",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
        }
        if (profile != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = profile.name ?: "",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "@${profile.screenName ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainScaffold(
    navController: androidx.navigation.NavHostController,
    selectedItem: Int,
    onSelectItem: (Int) -> Unit,
    items: List<String>,
    icons: List<Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.vector.ImageVector>>,
    onOpenDrawer: () -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            val icon = @Composable {
                                Icon(
                                    if (selectedItem == index) icons[index].first else icons[index].second,
                                    contentDescription = item
                                )
                            }
                            // A background refresh loaded tweets the user
                            // hasn't seen: dot the Home item until they do
                            if (index == 0 && HomeFeedState.hasFreshTweets) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary)
                                    }
                                ) { icon() }
                            } else {
                                icon()
                            }
                        },
                        label = { Text(item) },
                        selected = selectedItem == index,
                        onClick = {
                            // Re-tapping Home while the feed is showing scrolls
                            // it to the top (of the fresh tweets, if any landed)
                            // instead of re-navigating
                            if (index == 0 && navController.currentDestination?.route == "home") {
                                HomeFeedState.homeReselects++
                                return@NavigationBarItem
                            }
                            onSelectItem(index)
                            // When clicking the bottom bar item, navigate to the route
                            // This logic is a bit mixed with the current "selectedItem" state
                            // Ideally we should drive selectedItem from the navController's current destination
                            // But for now, let's just ensure we navigate correctly
                            val route = when(index) {
                                0 -> "home"
                                1 -> "search"
                                2 -> "notifications"
                                3 -> "messages"
                                else -> "home"
                            }
                            navController.navigate(route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                // on the back stack as users select items
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                // Update selectedItem to 0 when this route is active
                LaunchedEffect(Unit) { onSelectItem(0) }
                HomeScreen(
                    onTweetClick = { tweet ->
                        TweetCache.put(tweet.id, tweet)
                        navController.navigate("tweet/${tweet.id}")
                    },
                    onUserClick = { userId -> navController.navigate("profile/$userId") },
                    onAvatarClick = onOpenDrawer
                )
            }
            composable(
                "settings",
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
                }
            ) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("search") {
                LaunchedEffect(Unit) { onSelectItem(1) }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Coming Soon: Search")
                }
            }
            composable("notifications") {
                LaunchedEffect(Unit) { onSelectItem(2) }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Coming Soon: Notifications")
                }
            }
            composable("messages") {
                LaunchedEffect(Unit) { onSelectItem(3) }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Coming Soon: Messages")
                }
            }
            composable(
                "profile/{userId}",
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                },
                exitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                },
                popEnterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
                }
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId")
                if (userId != null) {
                    ProfileScreen(
                        userId = userId,
                        onBack = { navController.popBackStack() },
                        onTweetClick = { tweet ->
                            TweetCache.put(tweet.id, tweet)
                            navController.navigate("tweet/${tweet.id}")
                        },
                        // A different user's avatar in a conversation opens
                        // their profile; the nav stack handles the recursion.
                        onUserClick = { id -> navController.navigate("profile/$id") }
                    )
                }
            }
            composable(
                "tweet/{tweetId}",
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                },
                exitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                },
                popEnterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
                }
            ) { backStackEntry ->
                val tweetId = backStackEntry.arguments?.getString("tweetId")
                if (tweetId != null) {
                    val cachedTweet = TweetCache.get(tweetId)
                    TweetDetailScreen(
                        tweetId = tweetId,
                        initialTweet = cachedTweet,
                        onBack = { navController.popBackStack() },
                        // Tapping a comment opens it as its own detail screen with its
                        // sub-replies — recursion via the nav back stack.
                        onCommentClick = { tweet ->
                            TweetCache.put(tweet.id, tweet)
                            navController.navigate("tweet/${tweet.id}")
                        },
                        onUserClick = { id -> navController.navigate("profile/$id") }
                    )
                }
            }
        }
    }
}
