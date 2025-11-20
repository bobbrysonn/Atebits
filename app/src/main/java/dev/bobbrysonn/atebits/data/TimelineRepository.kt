package dev.bobbrysonn.atebits.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dev.bobbrysonn.atebits.network.AuthInterceptor
import dev.bobbrysonn.atebits.network.HomeTimelineApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class TimelineRepository(private val authRepository: AuthRepository) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(authRepository))
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://x.com")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(HomeTimelineApi::class.java)

    suspend fun getHomeTimeline(): List<TweetResult> {
        // Variables from QuaX client.dart (with userId="1" as seen in _for_you.dart)
        val variables = "{\"userId\":\"1\",\"count\":20,\"includePromotedContent\":false,\"withQuickPromoteEligibilityTweetFields\":true,\"withVoice\":true,\"withV2Timeline\":true}"
        
        // Features from QuaX client.dart
        val features = "{\"rweb_lists_timeline_redesign_enabled\":true,\"responsive_web_graphql_exclude_directive_enabled\":true,\"verified_phone_label_enabled\":true,\"creator_subscriptions_tweet_preview_api_enabled\":true,\"responsive_web_graphql_timeline_navigation_enabled\":true,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"tweetypie_unmention_optimization_enabled\":true,\"responsive_web_edit_tweet_api_enabled\":true,\"graphql_is_translatable_rweb_tweet_is_translatable_enabled\":true,\"view_counts_everywhere_api_enabled\":true,\"longform_notetweets_consumption_enabled\":true,\"responsive_web_twitter_article_tweet_consumption_enabled\":false,\"tweet_awards_web_tipping_enabled\":false,\"freedom_of_speech_not_reach_fetch_enabled\":true,\"standardized_nudges_misinfo\":true,\"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\":true,\"longform_notetweets_rich_text_read_enabled\":true,\"longform_notetweets_inline_media_enabled\":true,\"responsive_web_media_download_video_enabled\":false,\"responsive_web_enhance_cards_enabled\":false}"
        val fieldToggles = "{\"withAuxiliaryUserLabels\":false,\"withArticleRichContentState\":false}"

        try {
            // Note: fieldToggles is not currently used in our API definition but QuaX sends it. 
            // We might need to add it to HomeTimelineApi if this still fails.
            val response = api.getHomeTimeline(variables, features)
            
            val tweets = mutableListOf<TweetResult>()
            
            response.data.home?.homeTimelineUrt?.instructions?.forEach { instruction ->
                if (instruction.type == "TimelineAddEntries") {
                    instruction.entries?.forEach { entry ->
                        val result = entry.content.itemContent?.tweetResults?.result
                        if (result != null) {
                            if (result.__typename == "TweetWithVisibilityResults") {
                                result.tweet?.let { tweets.add(it) }
                            } else {
                                tweets.add(result)
                            }
                        }
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

    suspend fun getTweetDetail(tweetId: String): List<TweetResult> {
        val variables = "{\"focalTweetId\":\"$tweetId\",\"referrer\":\"profile\",\"controller_data\":\"DAACDAABDAABCgABAAAAAAAAAAAKAAkNObspUxawBQAAAAA=\",\"with_rux_injections\":false,\"includePromotedContent\":false,\"withCommunity\":true,\"withQuickPromoteEligibilityTweetFields\":true,\"withBirdwatchNotes\":true,\"withVoice\":true,\"withV2Timeline\":true}"
        val features = "{\"rweb_lists_timeline_redesign_enabled\":true,\"responsive_web_graphql_exclude_directive_enabled\":true,\"verified_phone_label_enabled\":true,\"creator_subscriptions_tweet_preview_api_enabled\":true,\"responsive_web_graphql_timeline_navigation_enabled\":true,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"tweetypie_unmention_optimization_enabled\":true,\"responsive_web_edit_tweet_api_enabled\":true,\"graphql_is_translatable_rweb_tweet_is_translatable_enabled\":true,\"view_counts_everywhere_api_enabled\":true,\"longform_notetweets_consumption_enabled\":true,\"responsive_web_twitter_article_tweet_consumption_enabled\":false,\"tweet_awards_web_tipping_enabled\":false,\"freedom_of_speech_not_reach_fetch_enabled\":true,\"standardized_nudges_misinfo\":true,\"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\":true,\"longform_notetweets_rich_text_read_enabled\":true,\"longform_notetweets_inline_media_enabled\":true,\"responsive_web_media_download_video_enabled\":false,\"responsive_web_enhance_cards_enabled\":false}"

        try {
            val response = api.getTweetDetail(variables, features)
            val tweets = mutableListOf<TweetResult>()

            response.data.threadedConversation?.instructions?.forEach { instruction ->
                if (instruction.type == "TimelineAddEntries") {
                    instruction.entries?.forEach { entry ->
                        // Handle both single items and modules (threads)
                        val itemContent = entry.content.itemContent
                        if (itemContent != null) {
                             val result = itemContent.tweetResults?.result
                             if (result != null) {
                                 if (result.__typename == "TweetWithVisibilityResults") {
                                     result.tweet?.let { tweets.add(it) }
                                 } else {
                                     tweets.add(result)
                                 }
                             }
                        }
                        // TODO: Handle TimelineTimelineModule for replies if needed
                    }
                }
            }
            return tweets
        } catch (e: Exception) {
            println("TimelineRepository: Error fetching tweet detail: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
