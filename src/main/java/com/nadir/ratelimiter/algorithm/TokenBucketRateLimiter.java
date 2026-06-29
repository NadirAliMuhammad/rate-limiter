package com.nadir.ratelimiter.algorithm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Token Bucket Rate Limiter.
 *
 * Algorithm:
 *   - Bucket holds up to `capacity` tokens
 *   - Tokens refill at `refillRate` per second
 *   - Each request consumes 1 token
 *   - Request denied if bucket is empty
 *
 * Better than fixed window for bursty traffic —
 * allows short bursts up to bucket capacity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBucketRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refillRate = tonumber(ARGV[2])  -- tokens per second
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            
            local data = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(data[1]) or capacity
            local lastRefill = tonumber(data[2]) or now
            
            -- Refill tokens based on elapsed time
            local elapsed = (now - lastRefill) / 1000  -- seconds
            local newTokens = math.min(capacity, tokens + (elapsed * refillRate))
            
            if newTokens >= requested then
                -- Consume tokens
                redis.call('HMSET', key, 'tokens', newTokens - requested, 'last_refill', now)
                redis.call('EXPIRE', key, 3600)
                return {1, math.floor(newTokens - requested)}
            else
                redis.call('HMSET', key, 'tokens', newTokens, 'last_refill', now)
                redis.call('EXPIRE', key, 3600)
                return {0, math.floor(newTokens)}
            end
            """;

    private final DefaultRedisScript<List> script = new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, List.class);

    public SlidingWindowRateLimiter.RateLimitResult isAllowed(
            String identifier, int capacity, double refillRate) {
        String key = "token_bucket:" + identifier;
        long now = Instant.now().toEpochMilli();

        try {
            List result = redisTemplate.execute(
                    script,
                    Arrays.asList(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(now),
                    "1"
            );

            if (result == null) return new SlidingWindowRateLimiter.RateLimitResult(true, capacity, capacity);

            boolean allowed = ((Number) result.get(0)).intValue() == 1;
            long remaining = ((Number) result.get(1)).longValue();
            return new SlidingWindowRateLimiter.RateLimitResult(allowed, capacity, remaining);

        } catch (Exception e) {
            log.error("Token bucket Redis error for {}: {}", key, e.getMessage());
            return new SlidingWindowRateLimiter.RateLimitResult(true, capacity, -1);
        }
    }
}
