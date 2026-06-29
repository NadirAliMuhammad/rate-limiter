package com.nadir.ratelimiter.aspect;

import com.nadir.ratelimiter.algorithm.SlidingWindowRateLimiter;
import com.nadir.ratelimiter.exception.RateLimitExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock
    private SlidingWindowRateLimiter rateLimiter;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;

    private RateLimitAspect aspect;

    /** A stand-in controller method carrying the annotation under test. */
    static class Sample {
        @RateLimit(requests = 5, duration = 1, timeUnit = TimeUnit.MINUTES, key = "search")
        public void limited() {
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        aspect = new RateLimitAspect(rateLimiter);
        Method method = Sample.class.getMethod("limited");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
    }

    @Test
    void proceeds_whenUnderLimit() throws Throwable {
        when(rateLimiter.isAllowed(anyString(), anyInt(), anyLong()))
                .thenReturn(new SlidingWindowRateLimiter.RateLimitResult(true, 5, 4));
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.checkRateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(joinPoint).proceed();
    }

    @Test
    void throws_whenLimitExceeded() throws Throwable {
        when(rateLimiter.isAllowed(anyString(), anyInt(), anyLong()))
                .thenReturn(new SlidingWindowRateLimiter.RateLimitResult(false, 5, 0));

        assertThatThrownBy(() -> aspect.checkRateLimit(joinPoint))
                .isInstanceOf(RateLimitExceededException.class);

        // The protected method must never run once the limit is hit.
        verify(joinPoint, never()).proceed();
    }
}
