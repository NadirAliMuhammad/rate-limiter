package com.nadir.ratelimiter.controller;

import com.nadir.ratelimiter.aspect.RateLimit;
import com.nadir.ratelimiter.exception.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api")
public class DemoController {

    /** 5 requests per minute per IP */
    @GetMapping("/public/search")
    @RateLimit(requests = 5, duration = 1, timeUnit = TimeUnit.MINUTES, key = "search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam String q) {
        return ResponseEntity.ok(Map.of("query", q, "results", "Sample results for: " + q));
    }

    /** 100 requests per minute per IP — authenticated endpoint */
    @GetMapping("/data")
    @RateLimit(requests = 100, duration = 1, timeUnit = TimeUnit.MINUTES, key = "data")
    public ResponseEntity<Map<String, Object>> getData() {
        return ResponseEntity.ok(Map.of("data", "Your data here", "timestamp", System.currentTimeMillis()));
    }

    /** Very strict: 2 requests per minute — e.g. OTP/SMS endpoint */
    @PostMapping("/auth/otp")
    @RateLimit(requests = 2, duration = 1, timeUnit = TimeUnit.MINUTES, key = "otp")
    public ResponseEntity<Map<String, Object>> sendOtp(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(Map.of("message", "OTP sent to " + body.get("phone")));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "rate-limiter"));
    }

    // ── Global Exception Handler ──────────────────────────────────────

    @RestControllerAdvice
    static class GlobalExceptionHandler {

        @ExceptionHandler(RateLimitExceededException.class)
        public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitExceededException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "error", "Too Many Requests",
                    "message", ex.getMessage(),
                    "statusCode", 429
            ));
        }
    }
}
