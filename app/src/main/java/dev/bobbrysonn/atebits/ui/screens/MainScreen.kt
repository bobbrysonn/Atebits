package dev.bobbrysonn.atebits.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import dev.bobbrysonn.atebits.ui.screens.home.HomeScreen

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.bobbrysonn.atebits.data.TweetCache

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
                            selectedItem = index
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
                LaunchedEffect(Unit) { selectedItem = 0 }
                HomeScreen(
                    onTweetClick = { tweet ->
                        val tweetId = tweet.rest_id ?: tweet.tweet?.rest_id
                        if (tweetId != null) {
                            TweetCache.put(tweetId, tweet)
                            navController.navigate("tweet/$tweetId")
                        }
                    }
                )
            }
            composable("search") {
                LaunchedEffect(Unit) { selectedItem = 1 }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Coming Soon: Search")
                }
            }
            composable("notifications") {
                LaunchedEffect(Unit) { selectedItem = 2 }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Coming Soon: Notifications")
                }
            }
            composable("messages") {
                LaunchedEffect(Unit) { selectedItem = 3 }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Coming Soon: Messages")
                }
            }
            composable("tweet/{tweetId}") { backStackEntry ->
                val tweetId = backStackEntry.arguments?.getString("tweetId")
                if (tweetId != null) {
                    val cachedTweet = TweetCache.get(tweetId)
                    TweetDetailScreen(
                        tweetId = tweetId,
                        initialTweet = cachedTweet,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
