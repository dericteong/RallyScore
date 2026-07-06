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
    private var lastSweepAt = 0L

    @Synchronized
    fun allow(key: String, now: Long = System.currentTimeMillis()): Boolean {
        sweepStaleKeys(now)
        val timestamps = hits.getOrPut(key) { mutableListOf() }
        timestamps.removeAll { now - it > windowMs }
        if (timestamps.size >= maxEvents) return false
        timestamps += now
        return true
    }

    // Per-key timestamp lists get pruned on every call, but the key itself stuck around forever
    // even once its list emptied out - a scanning attacker (or just ordinary Wi-Fi DHCP churn
    // over time) grows this map by one permanent entry per distinct source IP ever seen. Sweeping
    // the whole map at most once per window caps it to only recently-active keys.
    private fun sweepStaleKeys(now: Long) {
        if (now - lastSweepAt < windowMs) return
        lastSweepAt = now
        hits.entries.removeIf { (_, timestamps) ->
            timestamps.removeAll { now - it > windowMs }
            timestamps.isEmpty()
        }
    }
}
