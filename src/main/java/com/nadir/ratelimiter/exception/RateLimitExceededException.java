package com.nadir.ratelimiter.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) { super(message); }
}
