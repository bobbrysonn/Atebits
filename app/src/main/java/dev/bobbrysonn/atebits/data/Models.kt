package dev.bobbrysonn.atebits.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HomeTimelineResponse(
    val data: HomeTimelineData
)

@Serializable
data class HomeTimelineData(
    val home: HomeTimeline
)

@Serializable
data class HomeTimeline(
    @SerialName("home_timeline_urt") val homeTimelineUrt: Timeline
)

@Serializable
data class Timeline(
    val instructions: List<TimelineInstruction>
)

@Serializable
data class TimelineInstruction(
    val type: String,
    val entries: List<TimelineEntry>? = null
)

@Serializable
data class TimelineEntry(
    val entryId: String,
    val sortIndex: String,
    val content: TimelineEntryContent
)

@Serializable
data class TimelineEntryContent(
    val entryType: String,
    @SerialName("itemContent") val itemContent: TimelineItemContent? = null
)

@Serializable
data class TimelineItemContent(
    val itemType: String,
    @SerialName("tweet_results") val tweetResults: TweetResults? = null
)

@Serializable
data class TweetResults(
    val result: TweetResult
)

@Serializable
data class TweetResult(
    val __typename: String,
    val rest_id: String? = null,
    val core: TweetCore? = null,
    val legacy: TweetLegacy? = null,
    val tweet: TweetResult? = null // For retweets or quoted tweets where result is a wrapper
)

@Serializable
data class TweetCore(
    @SerialName("user_results") val userResults: UserResults
)

@Serializable
data class UserResults(
    val result: UserResult
)

@Serializable
data class UserResult(
    val rest_id: String,
    val legacy: UserLegacy
)

@Serializable
data class UserLegacy(
    val name: String,
    @SerialName("screen_name") val screenName: String,
    @SerialName("profile_image_url_https") val profileImageUrlHttps: String
)

@Serializable
data class TweetLegacy(
    @SerialName("full_text") val fullText: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("favorite_count") val favoriteCount: Int,
    @SerialName("retweet_count") val retweetCount: Int,
    @SerialName("reply_count") val replyCount: Int
)
