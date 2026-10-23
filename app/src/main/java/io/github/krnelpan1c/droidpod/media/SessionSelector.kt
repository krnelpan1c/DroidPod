package io.github.krnelpan1c.droidpod.media

data class SessionCandidate(
    val key: String,
    val isPlaying: Boolean,
    /** When the session last started playing (elapsedRealtime), 0 if never seen playing. */
    val lastPlayingAt: Long,
)

/** The user's explicit choice from the Players menu. */
data class Pin(val key: String, val pinnedAt: Long)

object SessionSelector {
    /**
     * Picks the session to control. A pin wins until some other session starts
     * playing after it was made; then the newest playing session, then the most
     * recently played one, then the system's own order.
     */
    fun select(candidates: List<SessionCandidate>, pin: Pin?): String? {
        if (candidates.isEmpty()) return null
        if (pin != null && candidates.any { it.key == pin.key }) {
            val overridden = candidates.any { it.key != pin.key && it.isPlaying && it.lastPlayingAt > pin.pinnedAt }
            if (!overridden) return pin.key
        }
        candidates.filter { it.isPlaying }.maxByOrNull { it.lastPlayingAt }?.let { return it.key }
        return candidates.maxBy { it.lastPlayingAt }.key
    }
}
