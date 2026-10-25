package io.github.krnelpan1c.droidpod.media

import kotlin.math.abs

/** "3 of 12" on the Now Playing screen; [number] is 1-based. */
data class QueuePosition(val number: Int, val total: Int)

/**
 * Counts tracks across a queue the app only shows part of. YouTube Music, for one,
 * exposes a sliding window of about 25 tracks, so the raw index is always "1 of 25".
 *
 * Every window seen is remembered by absolute position, so skipping forward counts up,
 * going back counts down (even past the start of the window), and the total only
 * grows, unless the app swaps out what's coming up (mixes and radio do). A track that
 * doesn't line up with what we've seen is a new album or playlist and starts the
 * count over. Lives in memory, one per app.
 */
class QueueCounter {
    /** Track keys by absolute 0-based position in the current album or playlist. */
    private val known = HashMap<Int, String>()
    private var current = -1
    private var total = 0

    /** [queue] holds one key per track; [index] is the playing one, or -1 if unknown. */
    fun update(queue: List<String>, index: Int): QueuePosition? {
        if (index !in queue.indices) return null
        if (known[current] != queue[index]) {
            val found = locate(queue, index)
            if (found == null) {
                known.clear()
                total = 0
            }
            current = found ?: index
        }
        val base = current - index
        var replaced = false
        queue.forEachIndexed { i, key ->
            val old = known.put(base + i, key)
            if (old != null && old != key) replaced = true
        }
        val end = base + queue.size
        if (replaced) {
            // What we remembered past this window was the old Up Next.
            known.keys.removeAll { it >= end }
            total = end
        } else {
            total = maxOf(total, end)
        }
        return QueuePosition(current + 1, total)
    }

    /** The known position of the playing track nearest the last one, if the window agrees with it. */
    private fun locate(queue: List<String>, index: Int): Int? {
        val key = queue[index]
        return known.filterValues { it == key }.keys
            // The next track is a plain skip even if the app reshuffled what follows it.
            .filter { it == current + 1 || aligned(queue, index, it) }
            .minByOrNull { abs(it - current) }
    }

    /** Whether the rest of [queue] matches what we know, with the playing track at [at]. */
    private fun aligned(queue: List<String>, index: Int, at: Int): Boolean {
        var compared = 0
        var matched = 0
        queue.forEachIndexed { i, key ->
            if (i == index) return@forEachIndexed
            val seen = known[at - index + i] ?: return@forEachIndexed
            compared++
            if (seen == key) matched++
        }
        return compared == 0 || matched > 0
    }
}
