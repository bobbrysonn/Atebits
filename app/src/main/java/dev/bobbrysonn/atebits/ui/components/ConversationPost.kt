package dev.bobbrysonn.atebits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.bobbrysonn.atebits.data.TweetResult
import dev.bobbrysonn.atebits.data.displayAspectRatio
import dev.bobbrysonn.atebits.data.displayText
import dev.bobbrysonn.atebits.data.isVideo
import dev.bobbrysonn.atebits.data.toLegacy

/**
 * A conversation thread as one card: parent tweet(s) and the reply, joined by
 * a vertical connector line between avatars — like the official client's
 * Replies tab.
 */
@Composable
fun ConversationPost(
    tweets: List<TweetResult>,
    onImageClick: (String) -> Unit = {},
    onTweetClick: (TweetResult) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            tweets.forEachIndexed { index, tweet ->
                ThreadedTweet(
                    tweet = tweet,
                    isLast = index == tweets.lastIndex,
                    onImageClick = onImageClick,
                    onTweetClick = onTweetClick
                )
            }
        }
    }
}

@Composable
private fun ThreadedTweet(
    tweet: TweetResult,
    isLast: Boolean,
    onImageClick: (String) -> Unit,
    onTweetClick: (TweetResult) -> Unit
) {
    val user = tweet.core?.userResults?.result?.toLegacy()
    val tweetContent = tweet.legacy
    val media = tweetContent?.extendedEntities?.media ?: tweetContent?.entities?.media
    val timeAgo = tweetContent?.createdAt?.let { formatTimeAgo(it) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable { onTweetClick(tweet) }
    ) {
        // Avatar with the connector line running down to the next tweet
        Column(
            modifier = Modifier.width(40.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = user?.profileImageUrlHttps,
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .width(2.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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

            val text = tweetContent?.displayText() ?: ""
            if (text.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
                    contentDescription = "Tweet Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(firstMedia.displayAspectRatio())
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImageClick(imageUrl) },
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TweetActionRow(tweetContent)
        }
    }
}
