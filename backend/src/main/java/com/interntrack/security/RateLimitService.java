package com.interntrack.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Enterprise token-bucket rate limiting service using Bucket4j.
 * Prevents automated brute-force credential stuffing, enumeration attacks,
 * and registration DOS spam against college onboarding endpoints.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private final ConcurrentMap<String, Bucket> cache = new ConcurrentHashMap<>();

    // Strict limit for sensitive login-adjacent and registration routes: 5 requests per 1 minute per client IP
    private static final int AUTH_CAPACITY = 5;
    private static final Duration AUTH_REFILL_PERIOD = Duration.ofMinutes(1);

    /**
     * Attempts to consume 1 request token for the given client key (IP or UID).
     *
     * @param key unique identifying client key (e.g. IP address on auth routes)
     * @return true if request is permitted, false if rate limit threshold exceeded
     */
    public boolean tryConsumeAuthToken(String key) {
        Bucket bucket = cache.computeIfAbsent(key, this::createNewAuthBucket);
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("Bucket4j token exhaustion: Client key [{}] exceeded rate limit threshold of [{}] requests/minute.", key, AUTH_CAPACITY);
        }
        return allowed;
    }

    private Bucket createNewAuthBucket(String key) {
        Refill refill = Refill.greedy(AUTH_CAPACITY, AUTH_REFILL_PERIOD);
        Bandwidth limit = Bandwidth.classic(AUTH_CAPACITY, refill);
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Clears cached buckets (useful for automated testing or memory purge schedules).
     */
    public void resetLimits() {
        cache.clear();
    }
}
