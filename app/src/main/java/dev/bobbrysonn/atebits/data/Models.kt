package dev.bobbrysonn.atebits.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Every field the API might omit is nullable or defaulted so a single
// tombstoned/restricted/deleted tweet can't fail deserialization of the
// whole timeline. Unusable entries are filtered out in TimelineRepository.

@Serializable
data class HomeTimelineResponse(
    val data: HomeTimelineData? = null
)

@Serializable
data class HomeTimelineData(
    val home: HomeTimeline? = null,
    @SerialName("threaded_conversation_with_injections_v2") val threadedConversation: ThreadedConversation? = null
)

@Serializable
data class ThreadedConversation(
    val instructions: List<TimelineInstruction> = emptyList()
)

@Serializable
data class HomeTimeline(
    @SerialName("home_timeline_urt") val homeTimelineUrt: Timeline? = null
)

@Serializable
data class Timeline(
    val instructions: List<TimelineInstruction> = emptyList()
)

@Serializable
data class TimelineInstruction(
    val type: String = "",
    val entries: List<TimelineEntry>? = null,
    // TimelinePinEntry carries a single entry (the pinned tweet)
    val entry: TimelineEntry? = null
)

@Serializable
data class TimelineEntry(
    val entryId: String = "",
    val sortIndex: String? = null,
    val content: TimelineEntryContent? = null
)

@Serializable
data class TimelineEntryContent(
    val entryType: String = "",
    @SerialName("itemContent") val itemContent: TimelineItemContent? = null,
    val items: List<TimelineModuleItem>? = null
)

@Serializable
data class TimelineModuleItem(
    val entryId: String = "",
    val item: TimelineModuleItemData? = null
)

@Serializable
data class TimelineModuleItemData(
    val itemContent: TimelineItemContent? = null
)

@Serializable
data class TimelineItemContent(
    val itemType: String = "",
    @SerialName("tweet_results") val tweetResults: TweetResults? = null,
    @SerialName("promotedMetadata") val promotedMetadata: PromotedMetadata? = null
)

@Serializable
data class PromotedMetadata(
    val advertiserId: String? = null,
    val impressionId: String? = null,
    val disclosureType: String? = null
)

@Serializable
data class TweetResults(
    val result: TweetResult? = null
)

@Serializable
data class TweetResult(
    val __typename: String? = null,
    val rest_id: String? = null,
    val core: TweetCore? = null,
    val legacy: TweetLegacy? = null,
    val tweet: TweetResult? = null, // For retweets or quoted tweets where result is a wrapper
    @SerialName("quoted_status_result") val quotedStatusResult: TweetResults? = null
)

// Unwraps TweetWithVisibilityResults and drops entries that can't be rendered
// (tombstones, deleted/restricted tweets) — they have no rest_id or legacy payload.
fun TweetResult.unwrapDisplayable(): TweetResult? {
    val unwrapped = if (__typename == "TweetWithVisibilityResults") tweet else this
    return unwrapped?.takeIf { it.rest_id != null && it.legacy != null }
}

@Serializable
data class TweetCore(
    @SerialName("user_results") val userResults: UserResults? = null
)

@Serializable
data class UserResults(
    val result: UserResult? = null
)

@Serializable
data class UserResult(
    val rest_id: String? = null,
    val legacy: UserLegacy? = null,
    // Present on UserTweets responses
    @SerialName("timeline_v2") val timelineV2: TimelineV2? = null
)

@Serializable
data class TimelineV2(
    val timeline: Timeline? = null
)

@Serializable
data class UserLegacy(
    val name: String? = null,
    @SerialName("screen_name") val screenName: String? = null,
    @SerialName("profile_image_url_https") val profileImageUrlHttps: String? = null,
    val description: String? = null,
    val location: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("followers_count") val followersCount: Int = 0,
    @SerialName("friends_count") val friendsCount: Int = 0,
    @SerialName("statuses_count") val statusesCount: Int = 0,
    @SerialName("profile_banner_url") val profileBannerUrl: String? = null
)

// profile_image_url_https is the 48px "_normal" variant
fun UserLegacy.bigAvatarUrl(): String? =
    profileImageUrlHttps?.replace("_normal", "_400x400")

@Serializable
data class TweetLegacy(
    @SerialName("full_text") val fullText: String? = null,
    @SerialName("display_text_range") val displayTextRange: List<Int>? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("favorite_count") val favoriteCount: Int = 0,
    @SerialName("retweet_count") val retweetCount: Int = 0,
    @SerialName("reply_count") val replyCount: Int = 0,
    val entities: TweetEntities? = null,
    @SerialName("extended_entities") val extendedEntities: TweetEntities? = null
)

@Serializable
data class TweetEntities(
    val media: List<MediaEntity>? = null,
    val urls: List<UrlEntity>? = null
)

@Serializable
data class UrlEntity(
    val url: String? = null,
    @SerialName("expanded_url") val expandedUrl: String? = null,
    @SerialName("display_url") val displayUrl: String? = null
)

@Serializable
data class MediaEntity(
    val id_str: String? = null,
    val url: String? = null, // the t.co link occupying the tweet text
    // For videos/gifs this is the poster frame; the streams are in video_info
    @SerialName("media_url_https") val mediaUrlHttps: String? = null,
    val type: String? = null, // "photo", "video", or "animated_gif"
    @SerialName("original_info") val originalInfo: MediaOriginalInfo? = null,
    @SerialName("video_info") val videoInfo: VideoInfo? = null
)

@Serializable
data class VideoInfo(
    @SerialName("aspect_ratio") val aspectRatio: List<Int>? = null,
    @SerialName("duration_millis") val durationMillis: Long? = null,
    val variants: List<VideoVariant> = emptyList()
)

@Serializable
data class VideoVariant(
    val bitrate: Long? = null,
    @SerialName("content_type") val contentType: String? = null,
    val url: String? = null
)

val MediaEntity.isVideo: Boolean
    get() = type == "video" || type == "animated_gif"

// Natural width/height ratio for layout, floored at 9:16 so extremely tall
// images get cropped instead of dominating the timeline.
fun MediaEntity.displayAspectRatio(): Float =
    originalInfo?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { it.width.toFloat() / it.height }
        ?.coerceAtLeast(9f / 16f)
        ?: (16f / 9f)

// Highest-bitrate progressive MP4; skips HLS playlists so playback needs no
// extra ExoPlayer modules.
fun MediaEntity.bestVideoUrl(): String? =
    videoInfo?.variants
        ?.filter { it.contentType == "video/mp4" && it.url != null }
        ?.maxByOrNull { it.bitrate ?: 0 }
        ?.url

@Serializable
data class MediaOriginalInfo(
    val width: Int = 0,
    val height: Int = 0
)

// The signed-in user: id parsed from the twid cookie, rest via UserByRestId
@Serializable
data class UserProfile(
    val name: String? = null,
    @SerialName("screen_name") val screenName: String? = null,
    @SerialName("profile_image_url_https") val profileImageUrlHttps: String? = null
)

@Serializable
data class UserResponse(
    val data: UserResponseData? = null
)

@Serializable
data class UserResponseData(
    val user: UserResults? = null
)

// verify_credentials returns the 48px "_normal" variant; swap in a larger one
fun UserProfile.avatarUrl(): String? =
    profileImageUrlHttps?.replace("_normal", "_400x400")

// full_text contains raw t.co links and, on replies, leading @mentions.
// display_text_range marks the visible slice (drops both); then replace linked
// URLs with their readable display form and strip media links entirely
// (the media renders separately).
fun TweetLegacy.displayText(): String {
    var text = fullText ?: return ""
    displayTextRange?.takeIf { it.size == 2 }?.let { (start, end) ->
        // Range indices are Unicode code points, not UTF-16 offsets
        val from = text.codePointsToOffset(start)
        val to = text.codePointsToOffset(end)
        if (from < to) text = text.substring(from, to)
    }
    entities?.urls?.forEach { entity ->
        val tco = entity.url ?: return@forEach
        text = text.replace(tco, entity.displayUrl ?: entity.expandedUrl ?: tco)
    }
    val mediaLinks = (extendedEntities?.media ?: entities?.media)
        .orEmpty().mapNotNull { it.url }.distinct()
    mediaLinks.forEach { text = text.replace(it, "") }
    // full_text is HTML-escaped (&, <, > only); &amp; last to avoid double-decoding
    text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    return text.trim()
}

private fun String.codePointsToOffset(codePoints: Int): Int =
    try {
        offsetByCodePoints(0, codePoints)
    } catch (e: IndexOutOfBoundsException) {
        length
    }
