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
    val items: List<TimelineModuleItem>? = null,
    // TimelineTimelineCursor entries: the opaque token for the next page
    val value: String? = null,
    val cursorType: String? = null
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
    @SerialName("promotedMetadata") val promotedMetadata: PromotedMetadata? = null,
    // Some query vintages nest the cursor here instead of on the entry content
    val value: String? = null
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
    @SerialName("note_tweet") val noteTweet: NoteTweet? = null,
    val tweet: TweetResult? = null, // For retweets or quoted tweets where result is a wrapper
    @SerialName("quoted_status_result") val quotedStatusResult: TweetResults? = null,
    val views: TweetViews? = null
)

// Impression count lives outside legacy; count is a decimal string, absent
// when the author has view counts disabled (state != "EnabledWithCount")
@Serializable
data class TweetViews(
    val count: String? = null,
    val state: String? = null
)

// Longform (>280 char) posts: legacy.full_text holds only the truncated
// preview; the complete text lives here. is_expandable marks that a "Show
// more" affordance applies on timeline surfaces.
@Serializable
data class NoteTweet(
    @SerialName("is_expandable") val isExpandable: Boolean = false,
    @SerialName("note_tweet_results") val noteTweetResults: NoteTweetResults? = null
)

@Serializable
data class NoteTweetResults(val result: NoteTweetResult? = null)

@Serializable
data class NoteTweetResult(
    val text: String? = null,
    @SerialName("entity_set") val entitySet: TweetEntities? = null
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
    // Newer query ids drop `legacy` and split the user across these objects
    val core: UserCore? = null,
    val avatar: UserAvatar? = null,
    val banner: UserBanner? = null,
    val location: UserLocation? = null,
    @SerialName("profile_bio") val profileBio: UserProfileBio? = null,
    @SerialName("relationship_counts") val relationshipCounts: RelationshipCounts? = null,
    @SerialName("tweet_counts") val tweetCounts: TweetCounts? = null,
    // Present on profile timeline responses; the key varies by query
    // (UserTweets uses timeline_v2, Likes uses timeline)
    @SerialName("timeline_v2") val timelineV2: TimelineV2? = null,
    val timeline: TimelineV2? = null
)

@Serializable
data class UserCore(
    val name: String? = null,
    @SerialName("screen_name") val screenName: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class UserAvatar(@SerialName("image_url") val imageUrl: String? = null)

@Serializable
data class UserBanner(@SerialName("image_url") val imageUrl: String? = null)

@Serializable
data class UserLocation(val location: String? = null)

@Serializable
data class UserProfileBio(val description: String? = null)

@Serializable
data class RelationshipCounts(val followers: Int = 0, val following: Int = 0)

@Serializable
data class TweetCounts(val tweets: Int = 0)

/**
 * Normalizes the two user shapes the API returns: older query ids nest
 * everything under `legacy`, newer ones split it across `core`/`avatar`/etc.
 */
fun UserResult.toLegacy(): UserLegacy = legacy ?: UserLegacy(
    name = core?.name,
    screenName = core?.screenName,
    profileImageUrlHttps = avatar?.imageUrl,
    description = profileBio?.description,
    location = location?.location,
    createdAt = core?.createdAt,
    followersCount = relationshipCounts?.followers ?: 0,
    friendsCount = relationshipCounts?.following ?: 0,
    statusesCount = tweetCounts?.tweets ?: 0,
    profileBannerUrl = banner?.imageUrl
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

// The 73px "_bigger" variant — right-sized for the 24-48dp row avatars
fun UserLegacy.smallAvatarUrl(): String? =
    profileImageUrlHttps?.replace("_normal", "_bigger")

@Serializable
data class TweetLegacy(
    @SerialName("full_text") val fullText: String? = null,
    @SerialName("display_text_range") val displayTextRange: List<Int>? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("favorite_count") val favoriteCount: Int = 0,
    @SerialName("retweet_count") val retweetCount: Int = 0,
    @SerialName("reply_count") val replyCount: Int = 0,
    val entities: TweetEntities? = null,
    @SerialName("extended_entities") val extendedEntities: TweetEntities? = null,
    // Present when this tweet is a retweet: the original tweet. The wrapper's
    // own full_text is just a truncated "RT @user: …" echo — never render it.
    @SerialName("retweeted_status_result") val retweetedStatusResult: TweetResults? = null
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

// pbs.twimg.com serves resized variants via ?name=; appending the query to the
// canonical .jpg/.png URL is accepted and preserves the declared format.
// "small" is 680px, "medium" 1200px, "large" 2048px.
fun MediaEntity.previewUrl(name: String): String? =
    mediaUrlHttps?.let { "$it?name=$name" }

// What the fullscreen viewer should load once a sized preview was shown
fun MediaEntity.fullSizeUrl(): String? = previewUrl("large")

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

// The 73px "_bigger" variant for the 32-48dp header/menu avatars
fun UserProfile.smallAvatarUrl(): String? =
    profileImageUrlHttps?.replace("_normal", "_bigger")

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

// Complete text of a longform post. Unlike full_text, note text is not
// HTML-escaped, has no display_text_range, and carries no media t.co links —
// only linked URLs need swapping for their readable form.
fun TweetResult.fullDisplayText(): String {
    val note = noteTweet?.noteTweetResults?.result
    var text = note?.text ?: return legacy?.displayText() ?: ""
    note.entitySet?.urls?.forEach { entity ->
        val tco = entity.url ?: return@forEach
        text = text.replace(tco, entity.displayUrl ?: entity.expandedUrl ?: tco)
    }
    return text.trim()
}

// Timeline preview: the truncated legacy text when the post expands elsewhere,
// otherwise the full note/legacy text.
fun TweetResult.previewText(): String =
    if (hasMoreText) legacy?.displayText() ?: "" else fullDisplayText()

// True when a detail view would reveal text the preview cuts off
val TweetResult.hasMoreText: Boolean
    get() = noteTweet?.isExpandable == true &&
        noteTweet.noteTweetResults?.result?.text != null

private fun String.codePointsToOffset(codePoints: Int): Int =
    try {
        offsetByCodePoints(0, codePoints)
    } catch (e: IndexOutOfBoundsException) {
        length
    }
