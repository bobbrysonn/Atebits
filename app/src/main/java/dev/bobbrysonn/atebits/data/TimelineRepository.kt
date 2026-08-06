package dev.bobbrysonn.atebits.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dev.bobbrysonn.atebits.network.AuthInterceptor
import dev.bobbrysonn.atebits.network.HomeTimelineApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

// A focal tweet with its direct replies; sub-replies load when a reply is opened.
data class TweetDetail(
    val mainTweet: UiTweet?,
    val replies: List<UiTweet>
)

enum class ProfileTab(val label: String) {
    Posts("Posts"),
    Replies("Replies"),
    Media("Media"),
    Likes("Likes")
}

// One profile timeline row: a single tweet, or a conversation thread
// (parent tweet(s) + the user's reply) on the Replies tab.
data class ProfileTimelineItem(val tweets: List<UiTweet>)

class TimelineRepository(private val authRepository: AuthRepository) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(authRepository))
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://x.com")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(HomeTimelineApi::class.java)

    companion object {
        // Feature flags from QuaX client.dart, shared by the timeline-shaped
        // GraphQL queries (HomeTimeline, TweetDetail, UserTweets)
        private const val TIMELINE_FEATURES = "{\"rweb_lists_timeline_redesign_enabled\":true,\"responsive_web_graphql_exclude_directive_enabled\":true,\"verified_phone_label_enabled\":true,\"creator_subscriptions_tweet_preview_api_enabled\":true,\"responsive_web_graphql_timeline_navigation_enabled\":true,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"tweetypie_unmention_optimization_enabled\":true,\"responsive_web_edit_tweet_api_enabled\":true,\"graphql_is_translatable_rweb_tweet_is_translatable_enabled\":true,\"view_counts_everywhere_api_enabled\":true,\"longform_notetweets_consumption_enabled\":true,\"responsive_web_twitter_article_tweet_consumption_enabled\":false,\"tweet_awards_web_tipping_enabled\":false,\"freedom_of_speech_not_reach_fetch_enabled\":true,\"standardized_nudges_misinfo\":true,\"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\":true,\"longform_notetweets_rich_text_read_enabled\":true,\"longform_notetweets_inline_media_enabled\":true,\"responsive_web_media_download_video_enabled\":false,\"responsive_web_enhance_cards_enabled\":false}"

        // Feature set the current web client (2026-08) sends with
        // UserTweetsAndReplies; newer query ids validate against this vintage.
        private const val WEB_2026_FEATURES = "{\"rweb_video_screen_enabled\":false,\"rweb_cashtags_enabled\":true,\"profile_label_improvements_pcf_label_in_post_enabled\":true,\"responsive_web_profile_redirect_enabled\":true,\"rweb_tipjar_consumption_enabled\":false,\"verified_phone_label_enabled\":false,\"creator_subscriptions_tweet_preview_api_enabled\":true,\"responsive_web_graphql_timeline_navigation_enabled\":true,\"premium_content_api_read_enabled\":false,\"communities_web_enable_tweet_community_results_fetch\":true,\"c9s_tweet_anatomy_moderator_badge_enabled\":true,\"responsive_web_grok_analyze_button_fetch_trends_enabled\":false,\"responsive_web_grok_analyze_post_followups_enabled\":true,\"rweb_cashtags_composer_attachment_enabled\":true,\"responsive_web_jetfuel_frame\":true,\"responsive_web_grok_share_attachment_enabled\":true,\"responsive_web_grok_annotations_enabled\":true,\"articles_preview_enabled\":true,\"responsive_web_edit_tweet_api_enabled\":true,\"rweb_conversational_replies_downvote_enabled\":false,\"graphql_is_translatable_rweb_tweet_is_translatable_enabled\":true,\"view_counts_everywhere_api_enabled\":true,\"longform_notetweets_consumption_enabled\":true,\"responsive_web_twitter_article_tweet_consumption_enabled\":true,\"content_disclosure_indicator_enabled\":true,\"content_disclosure_ai_generated_indicator_enabled\":true,\"responsive_web_grok_show_grok_translated_post\":true,\"responsive_web_grok_analysis_button_from_backend\":true,\"post_ctas_fetch_enabled\":false,\"freedom_of_speech_not_reach_fetch_enabled\":true,\"standardized_nudges_misinfo\":true,\"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\":true,\"longform_notetweets_rich_text_read_enabled\":true,\"longform_notetweets_inline_media_enabled\":false,\"responsive_web_grok_image_annotation_enabled\":true,\"responsive_web_grok_imagine_annotation_enabled\":true,\"responsive_web_grok_community_note_auto_translation_is_enabled\":true,\"responsive_web_enhance_cards_enabled\":false}"

        private const val WEB_2026_FIELD_TOGGLES = "{\"withArticlePlainText\":false}"
    }

    suspend fun getUserProfile(userId: String): UserResult? {
        try {
            val variables = "{\"userId\":\"$userId\",\"withSafetyModeUserFields\":true}"
            val features = "{\"hidden_profile_likes_enabled\":false,\"responsive_web_graphql_exclude_directive_enabled\":true,\"verified_phone_label_enabled\":true,\"highlights_tweets_tab_ui_enabled\":true,\"creator_subscriptions_tweet_preview_api_enabled\":true,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"responsive_web_graphql_timeline_navigation_enabled\":true}"
            val result = api.getUserByRestId(variables, features).data?.user?.result
            result?.toLegacy()?.let { ProfileCache.putUser(userId, it) }
            return result
        } catch (e: retrofit2.HttpException) {
            // Feature-set/query-id errors come back as 400s with a JSON body
            // naming the problem — surface it for logcat debugging.
            println("TimelineRepository: UserByRestId ${e.code()}: ${e.errorSnippet()}")
            throw e
        }
    }

    suspend fun getCurrentUser(): UserProfile {
        val userId = authRepository.getUserId()
            ?: throw IllegalStateException("no twid cookie in session")
        val user = getUserProfile(userId)?.toLegacy()
        return UserProfile(
            name = user?.name,
            screenName = user?.screenName,
            profileImageUrlHttps = user?.profileImageUrlHttps
        )
    }

    suspend fun getUserTimeline(userId: String, tab: ProfileTab): List<ProfileTimelineItem> {
        val variables = "{\"userId\":\"$userId\",\"count\":20,\"includePromotedContent\":false,\"withQuickPromoteEligibilityTweetFields\":true,\"withVoice\":true,\"withV2Timeline\":true}"
        try {
            val response = when (tab) {
                ProfileTab.Posts -> api.getUserTweets(variables, TIMELINE_FEATURES)
                ProfileTab.Replies -> {
                    // Newer query version; variables and features must match
                    // what the web client sends or validation fails.
                    val repliesVariables = "{\"userId\":\"$userId\",\"count\":20,\"includePromotedContent\":false,\"withCommunity\":true,\"withVoice\":true}"
                    api.getUserTweetsAndReplies(repliesVariables, WEB_2026_FEATURES, WEB_2026_FIELD_TOGGLES)
                }
                ProfileTab.Media -> api.getUserMedia(variables, TIMELINE_FEATURES)
                ProfileTab.Likes -> {
                    val likesVariables = "{\"userId\":\"$userId\",\"count\":20,\"includePromotedContent\":false,\"withClientEventToken\":false,\"withBirdwatchNotes\":false,\"withVoice\":true}"
                    api.getUserLikes(likesVariables, WEB_2026_FEATURES, WEB_2026_FIELD_TOGGLES)
                }
            }
            val items = mutableListOf<ProfileTimelineItem>()
            val result = response.data?.user?.result
            val timeline = result?.timelineV2?.timeline ?: result?.timeline?.timeline
            timeline?.instructions?.forEach { instruction ->
                val entries = when (instruction.type) {
                    "TimelineAddEntries" -> instruction.entries.orEmpty()
                    "TimelinePinEntry" -> listOfNotNull(instruction.entry)
                    else -> emptyList()
                }
                entries.forEach { entry ->
                    if (entry.entryId.contains("promoted", ignoreCase = true)) return@forEach
                    entry.content?.itemContent?.let { item ->
                        if (item.promotedMetadata == null) {
                            item.tweetResults?.result?.toUi()
                                ?.let { items.add(ProfileTimelineItem(listOf(it))) }
                        }
                    }
                    // Modules: profile-conversation threads stay grouped on the
                    // Replies tab (parent + reply render as one connected unit);
                    // everywhere else they flatten to standalone rows.
                    val moduleTweets = entry.content?.items.orEmpty().mapNotNull { moduleItem ->
                        moduleItem.item?.itemContent
                            ?.takeIf { it.promotedMetadata == null }
                            ?.tweetResults?.result?.toUi()
                    }
                    if (moduleTweets.isNotEmpty()) {
                        if (tab == ProfileTab.Replies) {
                            items.add(ProfileTimelineItem(moduleTweets))
                        } else {
                            moduleTweets.forEach { items.add(ProfileTimelineItem(listOf(it))) }
                        }
                    }
                }
            }
            ProfileCache.putTimeline(userId, tab, items)
            return items
        } catch (e: retrofit2.HttpException) {
            println("TimelineRepository: ${tab.name} timeline ${e.code()}: ${e.errorSnippet()}")
            throw e
        }
    }

    private fun retrofit2.HttpException.errorSnippet(): String? =
        response()?.errorBody()?.string()?.take(500)

    suspend fun getHomeTimeline(): List<UiTweet> {
        // Variables from QuaX client.dart (with userId="1" as seen in _for_you.dart)
        val variables = "{\"userId\":\"1\",\"count\":20,\"includePromotedContent\":false,\"withQuickPromoteEligibilityTweetFields\":true,\"withVoice\":true,\"withV2Timeline\":true}"

        try {
            val response = api.getHomeTimeline(variables, TIMELINE_FEATURES)

            val tweets = mutableListOf<UiTweet>()

            response.data?.home?.homeTimelineUrt?.instructions?.forEach { instruction ->
                if (instruction.type == "TimelineAddEntries") {
                    instruction.entries?.forEach { entry ->
                        // Filter out ads
                        if (entry.entryId.contains("promoted", ignoreCase = true)) return@forEach
                        if (entry.content?.itemContent?.promotedMetadata != null) return@forEach

                        entry.content?.itemContent?.tweetResults?.result
                            ?.toUi()?.let { tweets.add(it) }
                    }
                }
            }
            println("TimelineRepository: Found ${tweets.size} tweets")
            return tweets
        } catch (e: Exception) {
            println("TimelineRepository: Error fetching timeline: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    suspend fun getTweetDetail(tweetId: String): TweetDetail {
        val variables = "{\"focalTweetId\":\"$tweetId\",\"referrer\":\"profile\",\"controller_data\":\"DAACDAABDAABCgABAAAAAAAAAAAKAAkNObspUxawBQAAAAA=\",\"with_rux_injections\":false,\"includePromotedContent\":false,\"withCommunity\":true,\"withQuickPromoteEligibilityTweetFields\":true,\"withBirdwatchNotes\":true,\"withVoice\":true,\"withV2Timeline\":true}"
        try {
            val response = api.getTweetDetail(variables, TIMELINE_FEATURES)
            var mainTweet: UiTweet? = null
            val replies = mutableListOf<UiTweet>()

            response.data?.threadedConversation?.instructions?.forEach { instruction ->
                if (instruction.type == "TimelineAddEntries") {
                    instruction.entries?.forEach { entry ->
                        // The focal tweet (and its ancestors, which we skip) arrive as
                        // standalone entries.
                        entry.content?.itemContent?.let { item ->
                            if (item.promotedMetadata != null) return@forEach
                            item.tweetResults?.result?.toUi()?.let { tweet ->
                                if (tweet.id == tweetId) mainTweet = tweet
                            }
                        }

                        // Each conversationthread module is one reply thread: its first
                        // tweet is the direct reply, the rest are sub-replies the user
                        // sees by tapping through (like the official client).
                        if (entry.entryId.startsWith("conversationthread-")) {
                            entry.content?.items?.firstNotNullOfOrNull { moduleItem ->
                                val item = moduleItem.item?.itemContent
                                    ?: return@firstNotNullOfOrNull null
                                if (item.promotedMetadata != null) return@firstNotNullOfOrNull null
                                item.tweetResults?.result?.toUi()
                            }?.let { replies.add(it) }
                        }
                    }
                }
            }
            return TweetDetail(mainTweet, replies)
        } catch (e: Exception) {
            println("TimelineRepository: Error fetching tweet detail: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
