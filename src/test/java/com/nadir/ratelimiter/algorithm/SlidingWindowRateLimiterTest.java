package com.nadir.ratelimiter.algorithm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlidingWindowRateLimiterTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Test
    void isAllowed_failsOpen_whenRedisThrows() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenThrow(new RuntimeException("redis down"));
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(redisTemplate);

        SlidingWindowRateLimiter.RateLimitResult result =
                limiter.isAllowed("client-1", 5, 60_000);

        // Availability over strict limiting: a Redis outage must not block traffic.
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void isAllowed_parsesRedisResult() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(List.of(1L, 4L)); // allowed, remaining
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(redisTemplate);

        SlidingWindowRateLimiter.RateLimitResult result =
                limiter.isAllowed("client-1", 5, 60_000);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(4);
    }

    @Test
    void isAllowed_deniesWhenRedisReportsLimitReached() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(List.of(0L, 0L));
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(redisTemplate);

        SlidingWindowRateLimiter.RateLimitResult result =
                limiter.isAllowed("client-1", 5, 60_000);

        assertThat(result.allowed()).isFalse();
    }
}
