package com.courtside.pickleball.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {
    @Test
    fun allowsUpToMaxEventsWithinWindow() {
        val limiter = RateLimiter(maxEvents = 3, windowMs = 1_000L)

        assertTrue(limiter.allow("1.2.3.4", now = 0L))
        assertTrue(limiter.allow("1.2.3.4", now = 100L))
        assertTrue(limiter.allow("1.2.3.4", now = 200L))
        assertFalse(limiter.allow("1.2.3.4", now = 300L))
    }

    @Test
    fun tracksEachKeyIndependently() {
        val limiter = RateLimiter(maxEvents = 1, windowMs = 1_000L)

        assertTrue(limiter.allow("1.2.3.4", now = 0L))
        assertFalse(limiter.allow("1.2.3.4", now = 100L))
        assertTrue(limiter.allow("5.6.7.8", now = 100L))
    }

    @Test
    fun allowsAgainOnceEventsAgeOutOfTheWindow() {
        val limiter = RateLimiter(maxEvents = 1, windowMs = 1_000L)

        assertTrue(limiter.allow("1.2.3.4", now = 0L))
        assertFalse(limiter.allow("1.2.3.4", now = 500L))
        assertTrue(limiter.allow("1.2.3.4", now = 1_500L))
    }

    @Test
    fun sweepingStaleKeysDoesNotDisruptAnActiveKeysOwnWindow() {
        val limiter = RateLimiter(maxEvents = 2, windowMs = 1_000L)

        // Many short-lived keys, each seen once, spaced far enough apart to trigger the
        // internal periodic sweep - simulates a scanning attacker or churning Wi-Fi DHCP leases
        // rather than a single persistent client.
        repeat(50) { index ->
            assertTrue(limiter.allow("scanner-$index", now = index * 2_000L))
        }

        // A real, actively-used key must still see its own sliding window enforced correctly
        // even after many unrelated keys have come and gone.
        val activeKeyStart = 100_000L
        assertTrue(limiter.allow("real-client", now = activeKeyStart))
        assertTrue(limiter.allow("real-client", now = activeKeyStart + 100L))
        assertFalse(limiter.allow("real-client", now = activeKeyStart + 200L))
    }
}
