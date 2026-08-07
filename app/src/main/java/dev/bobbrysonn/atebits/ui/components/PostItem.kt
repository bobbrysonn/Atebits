package dev.bobbrysonn.atebits.ui.components

import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
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
import dev.bobbrysonn.atebits.data.UiTweet

/**
 * One tweet as a flat, edge-to-edge row with a hairline divider — the official
 * client's anatomy. The old card treatment spent 24dp per side on gutters and
 * 12dp on inter-card gaps; flat rows give that width back to the content.
 * Material stays in the details: theme ripple, dynamic color roles, M3 type.
 */
@Composable
fun PostItem(
    tweet: UiTweet,
    onTweetClick: (UiTweet) -> Unit = {},
    onUserClick: (String) -> Unit = {},
    // Detail surfaces render longform posts in full; timelines show the
    // truncated preview with "Show more"
    showFullText: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTweetClick(tweet) }
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 10.dp)
        ) {
            tweet.repostedBy?.let { name ->
                RepostAttribution(name)
                Spacer(modifier = Modifier.height(4.dp))
            }
            // Official-client anatomy: the avatar sits in its own left column,
            // top-aligned; everything else lives in the content column beside it.
            Row {
                AsyncImage(
                    model = tweet.user.avatarUrl,
                    contentDescription = "Open ${tweet.user.name}'s profile",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { tweet.user.id?.let(onUserClick) },
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    TweetHeaderLine(tweet)

                    Spacer(modifier = Modifier.height(1.dp))

                    var expanded by rememberSaveable(tweet.id) { mutableStateOf(false) }
                    val showFull = showFullText || expanded
                    Text(
                        text = if (showFull) tweet.fullText else tweet.previewText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!showFull && tweet.hasMoreText) {
                        ShowMoreLabel(style = MaterialTheme.typography.bodyLarge) { expanded = true }
                    }

                    if (tweet.media.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TweetMedia(media = tweet.media)
                    }

                    tweet.quoted?.let { quoted ->
                        Spacer(modifier = Modifier.height(8.dp))
                        QuotedTweet(
                            tweet = quoted,
                            onClick = { onTweetClick(quoted) },
                            onUserClick = onUserClick
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TweetActionRow(tweet)
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    }
}

// "Name reposted" with a small repeat glyph, drawn above a retweet. The 32dp
// inset ends the icon at the avatar's right edge so the text starts where the
// content column does — the official client's alignment.
@Composable
fun RepostAttribution(name: String) {
    Row(
        modifier = Modifier.padding(start = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Repeat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$name reposted",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// "Name @handle · 1d" on a single line: bold name at body size (the official
// client's scale), muted meta that truncates as one unit when the name is long.
@Composable
private fun TweetHeaderLine(tweet: UiTweet) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = tweet.user.name,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (tweet.timeAgo.isEmpty()) {
                "@${tweet.user.handle}"
            } else {
                "@${tweet.user.handle} · ${tweet.timeAgo}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
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
 * Reply/retweet/like/view counts plus bookmark and share, spread across the
 * content column like the official client. Shared by every tweet surface so
 * the row sits identically wherever it appears.
 */
@Composable
fun TweetActionRow(
    tweet: UiTweet,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TweetAction(
            icon = Icons.Outlined.ModeComment,
            count = tweet.replyCount,
            contentDescription = "Replies"
        )
        TweetAction(
            icon = Icons.Outlined.Repeat,
            count = tweet.retweetCount,
            contentDescription = "Retweets"
        )
        TweetAction(
            icon = Icons.Outlined.FavoriteBorder,
            count = tweet.favoriteCount,
            contentDescription = "Likes"
        )
        if (tweet.viewCount.isNotEmpty()) {
            TweetAction(
                icon = Icons.Outlined.BarChart,
                count = tweet.viewCount,
                contentDescription = "Views"
            )
        }
        TweetAction(
            icon = Icons.Outlined.BookmarkBorder,
            count = null,
            contentDescription = "Bookmark"
        )
        TweetAction(
            icon = Icons.Outlined.Share,
            count = null,
            contentDescription = "Share"
        )
    }
}

// Compact bordered card for a quoted tweet, nested inside the quoting PostItem.
@Composable
private fun QuotedTweet(
    tweet: UiTweet,
    onClick: () -> Unit,
    onUserClick: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AsyncImage(
                model = tweet.user.avatarUrl,
                contentDescription = "Open ${tweet.user.name}'s profile",
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { tweet.user.id?.let(onUserClick) },
                contentScale = ContentScale.Crop
            )
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

        Spacer(modifier = Modifier.height(4.dp))

        var expanded by rememberSaveable(tweet.id) { mutableStateOf(false) }
        Text(
            text = if (expanded) tweet.fullText else tweet.previewText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!expanded && tweet.hasMoreText) {
            ShowMoreLabel(style = MaterialTheme.typography.bodyMedium) { expanded = true }
        }

        if (tweet.media.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            TweetMedia(media = tweet.media, compact = true)
        }
    }
}

@Composable
fun TweetAction(
    icon: ImageVector,
    count: String?,
    contentDescription: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        if (count != null && count != "0") {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
