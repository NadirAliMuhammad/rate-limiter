package com.nadir.ratelimiter.aspect;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Apply to any controller method to enforce rate limiting.
 *
 * Usage:
 *   @RateLimit(requests = 10, duration = 1, timeUnit = TimeUnit.MINUTES)
 *   public ResponseEntity<?> myEndpoint() { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    int requests() default 100;           // Max requests allowed
    long duration() default 1;            // Time window value
    TimeUnit timeUnit() default TimeUnit.MINUTES;
    String key() default "";              // Custom key prefix (empty = use IP)
    boolean byUser() default false;       // true = per-user, false = per-IP
}
