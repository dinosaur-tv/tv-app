package com.dinotv.app.domain

/**
 * After the person leaves Dino with the physical remote, the wake service must not
 * yank the living-room screen back while the console still says power is on.
 */
object UserDismiss {
    const val GRACE_MS = 15 * 60 * 1000L

    fun isActive(untilEpochMs: Long, nowEpochMs: Long): Boolean =
        untilEpochMs > 0L && untilEpochMs > nowEpochMs

    fun until(nowEpochMs: Long, graceMs: Long = GRACE_MS): Long = nowEpochMs + graceMs
}
