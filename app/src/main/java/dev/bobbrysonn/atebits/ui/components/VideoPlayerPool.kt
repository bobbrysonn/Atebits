package dev.bobbrysonn.atebits.ui.components

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * A small pool of reusable ExoPlayers for inline and fullscreen video.
 * Allocating a player means allocating hardware AVC/AAC codecs and surfaces —
 * doing that per list item while scrolling is the app's biggest jank source.
 * Pooling keeps a few players alive and rebinds them to new media; ExoPlayer
 * then reuses compatible codecs instead of tearing them down.
 *
 * Slots stay bound to their last mediaId after release, so re-acquiring the
 * same media resumes the buffered stream and position. That binding is also
 * the inline↔fullscreen handoff: the card drops its lease without releasing,
 * the viewer acquires the same mediaId and adopts the live player.
 *
 * Main-thread only (all callers are composition effects and click handlers).
 * Invariant: at most one live lease per slot; the mediaId is the identity.
 */
object VideoPlayerPool {
    private const val MAX_PLAYERS = 3

    internal class Slot(val player: ExoPlayer) {
        var mediaId: String? = null
        var inUse = false
        var lastUsed = 0L
    }

    class Lease internal constructor(
        val player: ExoPlayer,
        /** False when the slot was already bound to this media (live handoff). */
        val freshlyPrepared: Boolean,
        internal val slot: Slot,
        internal val mediaId: String
    )

    private val slots = mutableListOf<Slot>()
    private var tick = 0L

    fun acquire(context: Context, mediaId: String, url: String): Lease {
        tick++
        // Already bound to this media (idle after a scroll-away, or live from
        // the other side of a fullscreen handoff): adopt as-is, keeping the
        // buffered stream and position.
        slots.firstOrNull { it.mediaId == mediaId }?.let { slot ->
            slot.inUse = true
            slot.lastUsed = tick
            return Lease(slot.player, freshlyPrepared = false, slot, mediaId)
        }

        val slot = slots.filter { !it.inUse }.minByOrNull { it.lastUsed }
            ?: if (slots.size < MAX_PLAYERS) {
                Slot(ExoPlayer.Builder(context.applicationContext).build())
                    .also { slots += it }
            } else {
                // All leased — shouldn't happen with visibility-gated callers,
                // but repurposing the LRU beats unbounded allocation.
                slots.minByOrNull { it.lastUsed }!!
            }

        slot.mediaId = mediaId
        slot.inUse = true
        slot.lastUsed = tick
        slot.player.apply {
            stop()
            clearMediaItems()
            // Reset whatever the previous binding wanted: no audio focus (a
            // muted timeline video must not pause the user's music — the
            // fullscreen viewer opts back in on its freshly prepared lease).
            setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ false)
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
        return Lease(slot.player, freshlyPrepared = true, slot, mediaId)
    }

    /**
     * Pause and return the slot to the pool. The player is NOT released and
     * stays bound to its media so a re-acquire resumes instantly. No-op if the
     * slot has since been rebound to different media (stale lease).
     */
    fun release(lease: Lease) {
        val slot = lease.slot
        if (slot.mediaId != lease.mediaId) return
        slot.player.playWhenReady = false
        slot.inUse = false
    }

    /** Really release every player. Only MainActivity calls this, on finish. */
    fun releaseAll() {
        slots.forEach { it.player.release() }
        slots.clear()
    }
}
