package dev.bobbrysonn.atebits.data

import dev.bobbrysonn.atebits.data.TweetResult

/**
 * In-memory scratch pad to pass tweets between screens without refetching.
 * Keeping it simple since data is short-lived.
 */
object TweetCache {
    private val cache = mutableMapOf<String, TweetResult>()

    fun put(id: String, tweet: TweetResult) {
        cache[id] = tweet
    }

    fun get(id: String): TweetResult? = cache[id]
}
