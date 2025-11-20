package dev.bobbrysonn.atebits.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.bobbrysonn.atebits.data.TweetResult
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

@Composable
fun PostItem(
    tweet: TweetResult,
    onImageClick: (String) -> Unit = {},
    onTweetClick: (TweetResult) -> Unit = {}
) {
    val user = tweet.core?.userResults?.result?.legacy
    val tweetContent = tweet.legacy
    val media = tweetContent?.extendedEntities?.media ?: tweetContent?.entities?.media
    val timeAgo = tweetContent?.createdAt?.let { formatTimeAgo(it) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onTweetClick(tweet) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = user?.profileImageUrlHttps,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = user?.name ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "@${user?.screenName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!timeAgo.isNullOrEmpty()) {
                            Text(
                                text = timeAgo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = tweetContent?.fullText ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!media.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                media.firstOrNull()?.let { firstImage ->
                    AsyncImage(
                        model = firstImage.mediaUrlHttps,
                        contentDescription = "Tweet Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onImageClick(firstImage.mediaUrlHttps) },
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TweetAction(
                    icon = Icons.Outlined.Create,
                    count = tweetContent?.replyCount ?: 0,
                    contentDescription = "Replies"
                )
                TweetAction(
                    icon = Icons.Outlined.Share,
                    count = tweetContent?.retweetCount ?: 0,
                    contentDescription = "Retweets"
                )
                TweetAction(
                    icon = Icons.Outlined.FavoriteBorder,
                    count = tweetContent?.favoriteCount ?: 0,
                    contentDescription = "Likes"
                )
                TweetAction(
                    icon = Icons.Outlined.Share,
                    count = null,
                    contentDescription = "Share"
                )
            }
        }
    }
}

@Composable
fun TweetAction(
    icon: ImageVector,
    count: Int?,
    contentDescription: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        if (count != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatCount(count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatCount(count: Int): String {
    return when {
        count < 1000 -> count.toString()
        count < 1000000 -> String.format("%.1fk", count / 1000.0)
        else -> String.format("%.1fM", count / 1000000.0)
    }
}

private val twitterDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH)

fun formatTimeAgo(createdAt: String): String {
    return try {
        val tweetTime = ZonedDateTime.parse(createdAt, twitterDateFormatter).toInstant()
        val duration = Duration.between(tweetTime, Instant.now())
        val minutes = max(1L, duration.toMinutes())
        val hours = duration.toHours()
        val days = duration.toDays()
        val weeks = days / 7
        val years = days / 365

        when {
            minutes < 60 -> "${minutes}m"
            hours < 24 -> "${hours}h"
            days < 7 -> "${days}d"
            weeks < 52 -> "${weeks}w"
            else -> "${years}y"
        }
    } catch (e: Exception) {
        ""
    }
}
