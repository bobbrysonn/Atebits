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
        val variables = "{\"count\":20,\"includePromotedContent\":false,\"latestControlAvailable\":true,\"requestContext\":\"launch\",\"withCommunity\":true,\"withSuperFollowsUserFields\":true,\"withDownvotePerspective\":false,\"withReactionsMetadata\":false,\"withReactionsPerspective\":false,\"withSuperFollowsTweetFields\":true}"
        val features = "{\"responsive_web_graphql_exclude_directive_enabled\":true,\"verified_phone_label_enabled\":false,\"creator_subscriptions_tweet_preview_api_enabled\":true,\"responsive_web_graphql_timeline_navigation_enabled\":true,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"tweetypie_unmention_optimization_enabled\":true,\"responsive_web_edit_tweet_api_enabled\":true,\"graphql_is_translatable_rweb_tweet_is_translatable_enabled\":true,\"view_counts_everywhere_api_enabled\":true,\"longform_notetweets_consumption_enabled\":true,\"responsive_web_twitter_article_tweet_consumption_enabled\":true,\"tweet_awards_web_tipping_enabled\":false,\"freedom_of_speech_not_reach_fetch_enabled\":true,\"standardized_nudges_misinfo\":true,\"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\":true,\"longform_notetweets_rich_text_read_enabled\":true,\"longform_notetweets_inline_media_enabled\":true,\"responsive_web_media_download_video_enabled\":false,\"responsive_web_enhance_cards_enabled\":false}"

        val response = api.getHomeTimeline(variables, features)
        
        val tweets = mutableListOf<TweetResult>()
        
        response.data.home.homeTimelineUrt.instructions.forEach { instruction ->
            if (instruction.type == "TimelineAddEntries") {
                instruction.entries?.forEach { entry ->
                    val result = entry.content.itemContent?.tweetResults?.result
                    if (result != null) {
                        // Handle retweets/quoted tweets wrapper if necessary, for now just add result
                        // If it's a TweetWithVisibilityResults, the actual tweet is nested
                        if (result.__typename == "TweetWithVisibilityResults") {
                            result.tweet?.let { tweets.add(it) }
                        } else {
                            tweets.add(result)
                        }
                    }
                }
            }
        }
        
        return tweets
    }
}
