package dev.bobbrysonn.atebits.data

import androidx.compose.runtime.Immutable
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/**
 * A tweet as the UI renders it, mapped once at ingestion. The date parsing,
 * display-text assembly (code-point slicing, t.co swaps, HTML unescapes), and
 * count formatting all happen here instead of per row per composition, and
 * the type is stable so rows skip recomposition when their tweet is unchanged.
 */
@Immutable
data class UiTweet(
    val id: String,
    val user: UiUser,
    // Frozen at fetch time; refresh re-maps, which is when it updated anyway
    val timeAgo: String,
    // Truncated text for timeline surfaces; the full note text of a longform
    // post (identical to previewText when there's nothing more to show)
    val previewText: String,
    val fullText: String,
    // True when fullText reveals text previewText cuts off ("Show more")
    val hasMoreText: Boolean,
    // First displayable media, kept raw: the video path needs the streams,
    // ids, and aspect ratio, and image URLs derive from it on demand
    val media: MediaEntity?,
    val replyCount: String,
    val retweetCount: String,
    val favoriteCount: String,
    val quoted: UiTweet?,
    // The DTO this row was mapped from, for flows that still need it
    // (detail refetch, cache identity)
    val raw: TweetResult
)

@Immutable
data class UiUser(
    val name: String,
    // Without the "@"; surfaces add their own prefix
    val handle: String,
    // The 73px _bigger variant, sized for row avatars
    val avatarUrl: String?
)

// Null for tombstones/deleted tweets, mirroring unwrapDisplayable()
fun TweetResult.toUi(): UiTweet? {
    val tweet = unwrapDisplayable() ?: return null
    val legacy = tweet.legacy ?: return null
    val user = tweet.core?.userResults?.result?.toLegacy()
    return UiTweet(
        id = tweet.rest_id ?: return null,
        user = UiUser(
            name = user?.name ?: "Unknown",
            handle = user?.screenName ?: "unknown",
            avatarUrl = user?.smallAvatarUrl()
        ),
        timeAgo = legacy.createdAt?.let(::formatTimeAgo) ?: "",
        previewText = tweet.previewText(),
        fullText = tweet.fullDisplayText(),
        hasMoreText = tweet.hasMoreText,
        media = (legacy.extendedEntities?.media ?: legacy.entities?.media)?.firstOrNull(),
        replyCount = formatCount(legacy.replyCount),
        retweetCount = formatCount(legacy.retweetCount),
        favoriteCount = formatCount(legacy.favoriteCount),
        quoted = tweet.quotedStatusResult?.result?.toUi(),
        raw = tweet
    )
}

fun formatCount(count: Int): String = when {
    count < 1_000 -> count.toString()
    count < 1_000_000 -> (count / 100).let { "${it / 10}.${it % 10}k" }
    else -> (count / 100_000).let { "${it / 10}.${it % 10}M" }
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
