package dev.bobbrysonn.atebits.data

import java.util.concurrent.ConcurrentHashMap

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

/**
 * Stale-while-revalidate cache for tweet detail (comments), react-query style:
 * cached data renders instantly on revisit; a network refresh only happens
 * once the entry is older than [FRESH_FOR_MS].
 */
object TweetDetailCache {
    private const val FRESH_FOR_MS = 5 * 60_000L

    private data class Entry(val detail: TweetDetail, val fetchedAtMs: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun get(id: String): TweetDetail? = entries[id]?.detail

    fun isFresh(id: String): Boolean =
        entries[id]?.let { System.currentTimeMillis() - it.fetchedAtMs < FRESH_FOR_MS } == true

    fun put(id: String, detail: TweetDetail) {
        entries[id] = Entry(detail, System.currentTimeMillis())
    }
}
