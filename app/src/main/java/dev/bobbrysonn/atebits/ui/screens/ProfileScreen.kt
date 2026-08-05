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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.bobbrysonn.atebits.data.TimelineRepository
import dev.bobbrysonn.atebits.data.TweetResult
import dev.bobbrysonn.atebits.data.UserLegacy
import dev.bobbrysonn.atebits.data.bigAvatarUrl
import dev.bobbrysonn.atebits.ui.components.PostItem
import dev.bobbrysonn.atebits.ui.components.formatCount
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BannerHeight = 140.dp
private val AvatarSize = 84.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onTweetClick: (TweetResult) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { TimelineRepository(AuthRepository(context)) }

    var user by remember { mutableStateOf<UserLegacy?>(null) }
    var tweets by remember { mutableStateOf<List<TweetResult>>(emptyList()) }
    var tweetsLoading by remember { mutableStateOf(true) }
    var tweetsError by remember { mutableStateOf<String?>(null) }
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        try {
            user = repository.getUserProfile(userId)?.legacy
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            tweets = repository.getUserTweets(userId)
        } catch (e: Exception) {
            e.printStackTrace()
            tweetsError = e.message ?: "Couldn't load posts"
        } finally {
            tweetsLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ProfileHeader(user) }
            item { ProfileInfo(user) }
            item {
                Column {
                    Text(
                        text = "Posts",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            when {
                tweetsLoading -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
                tweetsError != null -> item {
                    Text(
                        text = "Couldn't load posts: $tweetsError",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                else -> items(tweets) { tweet ->
                    PostItem(
                        tweet = tweet,
                        onImageClick = { url -> selectedImageUrl = url },
                        onTweetClick = onTweetClick
                    )
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
                    model = user.profileBannerUrl,
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
