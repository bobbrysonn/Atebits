package dev.bobbrysonn.atebits.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.bobbrysonn.atebits.data.AuthRepository
import dev.bobbrysonn.atebits.data.TimelineRepository
import dev.bobbrysonn.atebits.data.TweetResult
import dev.bobbrysonn.atebits.ui.components.PostItem
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var state by remember { mutableIntStateOf(0) }
    val titles = listOf("For You", "Following")
    
    val context = LocalContext.current
    val authRepository = remember { AuthRepository(context) }
    val timelineRepository = remember { TimelineRepository(authRepository) }
    var tweets by remember { mutableStateOf<List<TweetResult>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            tweets = timelineRepository.getHomeTimeline()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = state == index,
                    onClick = { state = index },
                    text = { Text(text = title) }
                )
            }
        }
        
        LazyColumn {
            items(tweets) { tweet ->
                PostItem(tweet = tweet)
            }
        }
    }
}
