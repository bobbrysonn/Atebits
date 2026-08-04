package dev.bobbrysonn.atebits.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                // Placeholder until we fetch the signed-in user's profile
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "Your account",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                        .size(48.dp)
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
                            Icon(
                                if (selectedItem == index) icons[index].first else icons[index].second,
                                contentDescription = item
                            )
                        },
                        label = { Text(item) },
                        selected = selectedItem == index,
                        onClick = {
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
                        val tweetId = tweet.rest_id ?: tweet.tweet?.rest_id
                        if (tweetId != null) {
                            TweetCache.put(tweetId, tweet)
                            navController.navigate("tweet/$tweetId")
                        }
                    },
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
                            val id = tweet.rest_id ?: tweet.tweet?.rest_id
                            if (id != null) {
                                TweetCache.put(id, tweet)
                                navController.navigate("tweet/$id")
                            }
                        }
                    )
                }
            }
        }
    }
}
