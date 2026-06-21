package com.example.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RateLimitException(message: String) : Exception(message)

object RateLimiter {
    private val REFILL_RATE = 10L // tokens per minute
    private val CAPACITY = 15L // max burst
    
    private var tokens = CAPACITY.toDouble()
    private var lastRefillTimestamp = System.currentTimeMillis()
    private val mutex = Mutex()

    suspend fun consume(count: Int = 1) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val timePassedMin = (now - lastRefillTimestamp) / 60000.0
            
            // Refill tokens
            tokens = minOf(CAPACITY.toDouble(), tokens + timePassedMin * REFILL_RATE)
            lastRefillTimestamp = now

            if (tokens >= count) {
                tokens -= count
            } else {
                throw RateLimitException("Rate limit exceeded. Falling back to local/cached processing to prevent credit loss loop.")
            }
        }
    }
}
