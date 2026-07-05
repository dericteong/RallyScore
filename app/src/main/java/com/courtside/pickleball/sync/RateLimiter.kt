package com.courtside.pickleball.sync

import java.util.concurrent.ConcurrentHashMap

/**
 * Sliding-window rate limiter keyed by an arbitrary string (typically a remote IP address).
 *
 * Used to slow down brute-force pairing/command-injection attempts against the phone<->tablet
 * sync sockets without needing a full connection-throttling framework.
 */
internal class RateLimiter(
    private val maxEvents: Int,
    private val windowMs: Long
) {
    private val hits = ConcurrentHashMap<String, MutableList<Long>>()

    @Synchronized
    fun allow(key: String, now: Long = System.currentTimeMillis()): Boolean {
        val timestamps = hits.getOrPut(key) { mutableListOf() }
        timestamps.removeAll { now - it > windowMs }
        if (timestamps.size >= maxEvents) return false
        timestamps += now
        return true
    }
}
