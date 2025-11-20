package dev.bobbrysonn.atebits.network

import dev.bobbrysonn.atebits.data.HomeTimelineResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface HomeTimelineApi {
    @GET("/i/api/graphql/W4Tpu1uueTGK53paUgxF0Q/HomeTimeline")
    suspend fun getHomeTimeline(
        @Query("variables") variables: String,
        @Query("features") features: String
    ): HomeTimelineResponse
}
