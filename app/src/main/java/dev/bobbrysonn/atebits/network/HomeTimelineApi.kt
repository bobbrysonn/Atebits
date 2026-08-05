package dev.bobbrysonn.atebits.network

import dev.bobbrysonn.atebits.data.HomeTimelineResponse
import dev.bobbrysonn.atebits.data.UserResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface HomeTimelineApi {
    @GET("/i/api/graphql/GazOglcBvgLigl3ywt6b3Q/UserByRestId")
    suspend fun getUserByRestId(
        @Query("variables") variables: String,
        @Query("features") features: String
    ): UserResponse

    @GET("/i/api/graphql/XicnWRbyQ3WgVY__VataBQ/UserTweets")
    suspend fun getUserTweets(
        @Query("variables") variables: String,
        @Query("features") features: String
    ): UserResponse

    @GET("/i/api/graphql/CwLU7qTfeu0doqhSr6tW4A/UserTweetsAndReplies")
    suspend fun getUserTweetsAndReplies(
        @Query("variables") variables: String,
        @Query("features") features: String
    ): UserResponse

    @GET("/i/api/graphql/YqiE3JL1KNgf9nSljYdxaA/UserMedia")
    suspend fun getUserMedia(
        @Query("variables") variables: String,
        @Query("features") features: String
    ): UserResponse

    @GET("/i/api/graphql/nRybED9kRbN-TOWioHq1ng/Likes")
    suspend fun getUserLikes(
        @Query("variables") variables: String,
        @Query("features") features: String
    ): UserResponse

    @GET("/i/api/graphql/W4Tpu1uueTGK53paUgxF0Q/HomeTimeline")
    suspend fun getHomeTimeline(
        @Query("variables") variables: String,
        @Query("features") features: String
    ): HomeTimelineResponse

    @GET("/i/api/graphql/3XDB26fBve-MmjHaWTUZxA/TweetDetail")
    suspend fun getTweetDetail(
        @Query("variables") variables: String,
        @Query("features") features: String
    ): HomeTimelineResponse
}
