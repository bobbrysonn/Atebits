package dev.bobbrysonn.atebits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.bobbrysonn.atebits.data.UiTweet

/**
 * A conversation thread as one flat row group: parent tweet(s) and the reply,
 * joined by a vertical connector line between avatars — like the official
 * client's Replies tab. Same edge-to-edge, hairline-divided anatomy as
 * PostItem.
 */
@Composable
fun ConversationPost(
    tweets: List<UiTweet>,
    onTweetClick: (UiTweet) -> Unit = {},
    onUserClick: (String) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 10.dp)
        ) {
            tweets.forEachIndexed { index, tweet ->
                ThreadedTweet(
                    tweet = tweet,
                    isLast = index == tweets.lastIndex,
                    onTweetClick = onTweetClick,
                    onUserClick = onUserClick
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ThreadedTweet(
    tweet: UiTweet,
    isLast: Boolean,
    onTweetClick: (UiTweet) -> Unit,
    onUserClick: (String) -> Unit
) {
    tweet.repostedBy?.let { name ->
        RepostAttribution(name)
        Spacer(modifier = Modifier.height(4.dp))
    }
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
                model = tweet.user.avatarUrl,
                contentDescription = "Open ${tweet.user.name}'s profile",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { tweet.user.id?.let(onUserClick) },
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
                    text = tweet.user.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = if (tweet.timeAgo.isEmpty()) {
                        "@${tweet.user.handle}"
                    } else {
                        "@${tweet.user.handle} · ${tweet.timeAgo}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            var expanded by rememberSaveable(tweet.id) { mutableStateOf(false) }
            val text = if (expanded) tweet.fullText else tweet.previewText
            if (text.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!expanded && tweet.hasMoreText) {
                    ShowMoreLabel(style = MaterialTheme.typography.bodyMedium) { expanded = true }
                }
            }

            if (tweet.media.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                TweetMedia(media = tweet.media, compact = true)
            }

            Spacer(modifier = Modifier.height(8.dp))
            TweetActionRow(tweet)
        }
    }
}
