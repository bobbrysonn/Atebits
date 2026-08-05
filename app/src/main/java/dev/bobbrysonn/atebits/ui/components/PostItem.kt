package dev.bobbrysonn.atebits.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.bobbrysonn.atebits.data.TweetResult
import dev.bobbrysonn.atebits.data.displayAspectRatio
import dev.bobbrysonn.atebits.data.fullDisplayText
import dev.bobbrysonn.atebits.data.hasMoreText
import dev.bobbrysonn.atebits.data.isVideo
import dev.bobbrysonn.atebits.data.previewText
import dev.bobbrysonn.atebits.data.toLegacy
import dev.bobbrysonn.atebits.data.unwrapDisplayable
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
    onTweetClick: (TweetResult) -> Unit = {},
    // Flatter tone for cards that play a supporting role (e.g. replies under
    // the focal tweet on the detail screen)
    muted: Boolean = false,
    // Detail surfaces render longform posts in full; timelines show the
    // truncated preview with "Show more"
    showFullText: Boolean = false
) {
    val user = tweet.core?.userResults?.result?.toLegacy()
    val tweetContent = tweet.legacy
    val media = tweetContent?.extendedEntities?.media ?: tweetContent?.entities?.media
    val timeAgo = tweetContent?.createdAt?.let { formatTimeAgo(it) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp)
            .clickable { onTweetClick(tweet) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (muted) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Profile picture
                AsyncImage(
                    model = user?.profileImageUrlHttps,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Name, username, time posted
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
                            text = "@${user?.screenName ?: "unknown"}",
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

            var expanded by rememberSaveable(tweet.rest_id) { mutableStateOf(false) }
            val showFull = showFullText || expanded
            Text(
                text = if (showFull) tweet.fullDisplayText() else tweet.previewText(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!showFull && tweet.hasMoreText) {
                ShowMoreLabel(style = MaterialTheme.typography.bodyLarge) { expanded = true }
            }

            val firstMedia = media?.firstOrNull()
            if (firstMedia?.isVideo == true) {
                Spacer(modifier = Modifier.height(12.dp))
                TweetVideo(media = firstMedia)
            } else if (firstMedia?.mediaUrlHttps != null) {
                val imageUrl = firstMedia.mediaUrlHttps
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Tweet Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(firstMedia.displayAspectRatio())
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onImageClick(imageUrl) },
                    contentScale = ContentScale.Crop
                )
            }

            tweet.quotedStatusResult?.result?.unwrapDisplayable()?.let { quoted ->
                Spacer(modifier = Modifier.height(12.dp))
                QuotedTweet(
                    tweet = quoted,
                    onImageClick = onImageClick,
                    onClick = { onTweetClick(quoted) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TweetActionRow(tweetContent)
        }
    }
}

// "Show more" under a truncated longform post: expands the full text in
// place. Taps anywhere else on the tweet still open the detail view.
@Composable
fun ShowMoreLabel(style: androidx.compose.ui.text.TextStyle, onClick: () -> Unit) {
    Text(
        text = "Show more",
        style = style,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/**
 * Reply/retweet/like counts plus share, spread evenly across the card. Shared
 * by every tweet surface so the row sits identically wherever it appears.
 */
@Composable
fun TweetActionRow(
    tweetContent: dev.bobbrysonn.atebits.data.TweetLegacy?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TweetAction(
            icon = Icons.Outlined.ModeComment,
            count = tweetContent?.replyCount ?: 0,
            contentDescription = "Replies"
        )
        TweetAction(
            icon = Icons.Outlined.Repeat,
            count = tweetContent?.retweetCount ?: 0,
            contentDescription = "Retweets"
        )
        TweetAction(
            icon = Icons.Outlined.FavoriteBorder,
            count = tweetContent?.favoriteCount ?: 0,
            contentDescription = "Likes"
        )
        TweetAction(
            icon = Icons.Outlined.IosShare,
            count = null,
            contentDescription = "Share"
        )
    }
}

// Compact bordered card for a quoted tweet, nested inside the quoting PostItem.
@Composable
private fun QuotedTweet(
    tweet: TweetResult,
    onImageClick: (String) -> Unit,
    onClick: () -> Unit
) {
    val user = tweet.core?.userResults?.result?.toLegacy()
    val tweetContent = tweet.legacy
    val media = tweetContent?.extendedEntities?.media ?: tweetContent?.entities?.media
    val timeAgo = tweetContent?.createdAt?.let { formatTimeAgo(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = user?.profileImageUrlHttps,
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Text(
                text = user?.name ?: "Unknown",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = "@${user?.screenName ?: "unknown"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            if (!timeAgo.isNullOrEmpty()) {
                Text(
                    text = timeAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        var expanded by rememberSaveable(tweet.rest_id) { mutableStateOf(false) }
        Text(
            text = if (expanded) tweet.fullDisplayText() else tweet.previewText(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!expanded && tweet.hasMoreText) {
            ShowMoreLabel(style = MaterialTheme.typography.bodyMedium) { expanded = true }
        }

        val firstMedia = media?.firstOrNull()
        if (firstMedia?.isVideo == true) {
            Spacer(modifier = Modifier.height(8.dp))
            TweetVideo(media = firstMedia)
        } else if (firstMedia?.mediaUrlHttps != null) {
            val imageUrl = firstMedia.mediaUrlHttps
            Spacer(modifier = Modifier.height(8.dp))
            AsyncImage(
                model = imageUrl,
                contentDescription = "Quoted Tweet Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(firstMedia.displayAspectRatio())
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onImageClick(imageUrl) },
                contentScale = ContentScale.Crop
            )
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
