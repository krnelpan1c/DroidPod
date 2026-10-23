package io.github.krnelpan1c.droidpod.media

object PositionMath {
    /** Players only report position occasionally; project it forward from the last report. */
    fun extrapolate(
        positionMs: Long,
        updatedAt: Long,
        speed: Float,
        playing: Boolean,
        now: Long,
        durationMs: Long,
    ): Long {
        if (positionMs < 0) return 0
        var position = positionMs
        if (playing && updatedAt > 0 && now > updatedAt) {
            position += ((now - updatedAt) * speed).toLong()
        }
        if (durationMs > 0) position = position.coerceAtMost(durationMs)
        return position.coerceAtLeast(0)
    }

    fun current(np: NowPlaying, now: Long): Long =
        extrapolate(np.positionMs, np.positionUpdatedAt, np.speed, np.status == PlayStatus.Playing, now, np.durationMs)

    /** 83_000 -> "1:23", 3_723_000 -> "1:02:03". */
    fun format(ms: Long): String {
        val total = (ms.coerceAtLeast(0)) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
    }
}
