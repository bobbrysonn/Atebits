package dev.bobbrysonn.atebits.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.bobbrysonn.atebits.data.AuthRepository
import dev.bobbrysonn.atebits.data.ProfileCache
import dev.bobbrysonn.atebits.data.ProfileTab
import dev.bobbrysonn.atebits.data.ProfileTimelineItem
import dev.bobbrysonn.atebits.data.TimelineRepository
import dev.bobbrysonn.atebits.data.TweetResult
import dev.bobbrysonn.atebits.data.UserLegacy
import dev.bobbrysonn.atebits.data.bigAvatarUrl
import dev.bobbrysonn.atebits.data.toLegacy
import dev.bobbrysonn.atebits.ui.components.ConversationPost
import dev.bobbrysonn.atebits.ui.components.LocalListScrollInProgress
import dev.bobbrysonn.atebits.ui.components.PostItem
import dev.bobbrysonn.atebits.ui.components.formatCount
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BannerHeight = 140.dp
private val AvatarSize = 84.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onTweetClick: (TweetResult) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { TimelineRepository(AuthRepository(context)) }

    // Cache-seeded so revisits render instantly; network refreshes when stale
    var user by remember { mutableStateOf(ProfileCache.getUser(userId)) }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = ProfileTab.entries[tabIndex]
    val tabItems = remember {
        mutableStateMapOf<ProfileTab, List<ProfileTimelineItem>>().apply {
            ProfileTab.entries.forEach { tab ->
                ProfileCache.getTimeline(userId, tab)?.let { put(tab, it) }
            }
        }
    }
    var tabLoading by remember { mutableStateOf(false) }
    val tabErrors = remember { mutableStateMapOf<ProfileTab, String>() }
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        if (!ProfileCache.isUserFresh(userId)) {
            try {
                repository.getUserProfile(userId)?.toLegacy()?.let { user = it }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(selectedTab) {
        tabErrors.remove(selectedTab)
        if (!ProfileCache.isTimelineFresh(userId, selectedTab)) {
            tabLoading = tabItems[selectedTab].isNullOrEmpty()
            try {
                tabItems[selectedTab] = repository.getUserTimeline(userId, selectedTab)
            } catch (e: Exception) {
                e.printStackTrace()
                if (tabItems[selectedTab].isNullOrEmpty()) {
                    tabErrors[selectedTab] = e.message ?: "Couldn't load"
                }
            } finally {
                tabLoading = false
            }
        }
    }

    val listState = rememberLazyListState()
    // TweetVideo defers player creation until scrolling settles
    val scrollInProgress = remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }

    CompositionLocalProvider(LocalListScrollInProgress provides scrollInProgress) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                // Keys must be unique across the whole list; the fixed slots use
                // non-numeric strings so they can't collide with tweet ids.
                item(key = "header", contentType = "header") { ProfileHeader(user) }
                item(key = "info", contentType = "info") { ProfileInfo(user) }
                item(key = "tabs", contentType = "tabs") {
                    PrimaryTabRow(
                        selectedTabIndex = tabIndex,
                        containerColor = Color.Transparent,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        ProfileTab.entries.forEachIndexed { index, tab ->
                            Tab(
                                selected = tabIndex == index,
                                onClick = { tabIndex = index },
                                text = { Text(tab.label) }
                            )
                        }
                    }
                }
                val rows = tabItems[selectedTab].orEmpty()
                when {
                    tabLoading && rows.isEmpty() -> item(key = "tab-loading", contentType = "tab-loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                    tabErrors[selectedTab] != null -> item(key = "tab-error", contentType = "tab-error") {
                        Text(
                            text = "Couldn't load ${selectedTab.label.lowercase()}: ${tabErrors[selectedTab]}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    else -> items(
                        rows,
                        key = { it.tweets.firstOrNull()?.rest_id ?: it.hashCode() },
                        contentType = { if (it.tweets.size > 1) "conversation" else "tweet" }
                    ) { item ->
                        if (item.tweets.size > 1) {
                            ConversationPost(
                                tweets = item.tweets,
                                onImageClick = { url -> selectedImageUrl = url },
                                onTweetClick = onTweetClick
                            )
                        } else {
                            item.tweets.firstOrNull()?.let { tweet ->
                                PostItem(
                                    tweet = tweet,
                                    onImageClick = { url -> selectedImageUrl = url },
                                    onTweetClick = onTweetClick
                                )
                            }
                        }
                    }
                }
            }

            FilledIconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                ),
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
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

@Composable
private fun ProfileHeader(user: UserLegacy?) {
    // Banner with the avatar overlapping its bottom edge
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BannerHeight + AvatarSize / 2)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BannerHeight)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            if (user?.profileBannerUrl != null) {
                AsyncImage(
                    // Banner URLs are extensionless and take a size path-suffix;
                    // 1500x500 matches the full-width render
                    model = user.profileBannerUrl + "/1500x500",
                    contentDescription = "Profile banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        AsyncImage(
            model = user?.bigAvatarUrl(),
            contentDescription = "Profile picture",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp)
                .size(AvatarSize)
                .clip(CircleShape)
                .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun ProfileInfo(user: UserLegacy?) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = user?.name ?: "",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = user?.screenName?.let { "@$it" } ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!user?.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = user?.description ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!user?.location.isNullOrBlank()) {
                MetaItem(icon = { Icon(Icons.Outlined.Place, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }, text = user?.location ?: "")
            }
            user?.createdAt?.let { formatJoined(it) }?.takeIf { it.isNotEmpty() }?.let {
                MetaItem(icon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }, text = it)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CountItem(count = user?.friendsCount ?: 0, label = "Following")
            CountItem(count = user?.followersCount ?: 0, label = "Followers")
            CountItem(count = user?.statusesCount ?: 0, label = "Posts")
        }
    }
}

@Composable
private fun MetaItem(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CountItem(count: Int, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = formatCount(count),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val twitterDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH)

private fun formatJoined(createdAt: String): String = try {
    val date = ZonedDateTime.parse(createdAt, twitterDateFormatter)
    "Joined ${date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))}"
} catch (e: Exception) {
    ""
}
