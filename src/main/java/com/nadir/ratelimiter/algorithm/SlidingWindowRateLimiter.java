package com.nadir.ratelimiter.algorithm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Sliding Window Rate Limiter using Redis Sorted Sets.
 *
 * Algorithm:
 *   - Key = "rate_limit:{identifier}"
 *   - Each request adds a timestamp to a sorted set
 *   - Requests older than the window are removed
 *   - Count remaining = current requests in window
 *
 * Why sorted sets?
 *   - O(log N) insert
 *   - O(log N) range delete
 *   - Atomic via Lua script — no race conditions
 *
 * This is the approach used by Stripe and GitHub for their APIs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlidingWindowRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Lua script for atomic check-and-increment.
     * Runs as a single atomic operation in Redis — thread safe.
     */
    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local clearBefore = now - window
            
            -- Remove timestamps outside window
            redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)
            
            -- Count requests in current window
            local count = redis.call('ZCARD', key)
            
            if count < limit then
                -- Allow: add current timestamp
                redis.call('ZADD', key, now, now)
                redis.call('EXPIRE', key, math.ceil(window / 1000))
                return {1, limit - count - 1}  -- allowed, remaining
            else
                return {0, 0}  -- denied
            end
            """;

    private final DefaultRedisScript<List> script = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, List.class);

    public RateLimitResult isAllowed(String identifier, int limit, long windowMillis) {
        String key = "rate_limit:" + identifier;
        long now = Instant.now().toEpochMilli();

        try {
            List result = redisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    String.valueOf(now),
                    String.valueOf(windowMillis),
                    String.valueOf(limit)
            );

            if (result == null || result.isEmpty()) {
                log.warn("Redis returned null for key: {}, allowing request", key);
                return new RateLimitResult(true, limit, limit - 1);
            }

            boolean allowed = ((Number) result.get(0)).intValue() == 1;
            long remaining = ((Number) result.get(1)).longValue();

            log.debug("Rate limit check — key: {}, allowed: {}, remaining: {}", key, allowed, remaining);
            return new RateLimitResult(allowed, limit, remaining);

        } catch (Exception e) {
            // Fail open: if Redis is down, allow the request
            log.error("Redis error during rate limit check for {}: {}", key, e.getMessage());
            return new RateLimitResult(true, limit, -1);
        }
    }

    public record RateLimitResult(boolean allowed, long limit, long remaining) {}
}
