package dev.bobbrysonn.atebits.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Stale-while-revalidate cache for profile pages, mirroring TweetDetailCache:
 * cached data renders instantly on revisit; the network is only hit once an
 * entry is older than [FRESH_FOR_MS].
 */
object ProfileCache {
    private const val FRESH_FOR_MS = 5 * 60_000L

    private class Entry<T>(val value: T) {
        private val fetchedAtMs = System.currentTimeMillis()
        val fresh get() = System.currentTimeMillis() - fetchedAtMs < FRESH_FOR_MS
    }

    private val users = ConcurrentHashMap<String, Entry<UserLegacy>>()
    private val timelines = ConcurrentHashMap<String, Entry<List<ProfileTimelineItem>>>()

    fun getUser(userId: String): UserLegacy? = users[userId]?.value

    fun isUserFresh(userId: String): Boolean = users[userId]?.fresh == true

    fun putUser(userId: String, user: UserLegacy) {
        users[userId] = Entry(user)
    }

    fun getTimeline(userId: String, tab: ProfileTab): List<ProfileTimelineItem>? =
        timelines["$userId/$tab"]?.value

    fun isTimelineFresh(userId: String, tab: ProfileTab): Boolean =
        timelines["$userId/$tab"]?.fresh == true

    fun putTimeline(userId: String, tab: ProfileTab, items: List<ProfileTimelineItem>) {
        timelines["$userId/$tab"] = Entry(items)
    }
}
