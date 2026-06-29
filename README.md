# Distributed Rate Limiter

A production-grade **distributed rate limiter** using Redis, Spring Boot 3, and AOP — supporting both **Sliding Window** and **Token Bucket** algorithms. Apply rate limiting to any endpoint with a single annotation.

## Algorithms Implemented

### 1. Sliding Window (Default)
```
Window: 1 minute, Limit: 5 requests

t=0s  → req 1 ✅  [remaining: 4]
t=10s → req 2 ✅  [remaining: 3]
t=30s → req 3 ✅  [remaining: 2]
t=60s → req 1 expires, req 4 ✅  [remaining: 2]
```
More accurate than fixed window — no boundary spikes.

### 2. Token Bucket
```
Capacity: 10, Refill: 2 tokens/sec

Burst of 10 requests → all allowed (drains bucket)
Next request → wait for refill
```
Better for bursty but fair traffic (used by AWS API Gateway).

## Why Redis?

- **Atomic Lua scripts** — no race conditions across multiple app instances
- **Distributed** — works with 10 instances, not just 1
- **Fail-open** — if Redis is down, requests are allowed (no outage)

## Usage — One Annotation

```java
// 5 requests per minute per IP
@GetMapping("/search")
@RateLimit(requests = 5, duration = 1, timeUnit = TimeUnit.MINUTES, key = "search")
public ResponseEntity<?> search() { ... }

// 2 OTP requests per minute (strict)
@PostMapping("/auth/otp")
@RateLimit(requests = 2, duration = 1, timeUnit = TimeUnit.MINUTES, key = "otp")
public ResponseEntity<?> sendOtp() { ... }
```

## Response Headers

Every response includes standard rate limit headers:
```
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 3
X-RateLimit-Reset: 1704067260
```

## Running

```bash
docker-compose up --build
```

## Testing Rate Limits

```bash
# Hit the search endpoint 6 times — 6th should return 429
for i in {1..6}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    "http://localhost:8083/api/public/search?q=test"
done
# Output: 200 200 200 200 200 429
```

## Architecture

```
HTTP Request → RateLimitAspect (AOP)
                    ↓
             SlidingWindowRateLimiter
                    ↓
             Redis Lua Script (atomic)
                    ↓
             Allow / Deny + Set Headers
```

## Interview Talking Points

1. Why Lua scripts? → Atomic execution, no distributed locks needed
2. Why fail-open? → Availability > strict limiting during Redis outage
3. Sliding Window vs Token Bucket? → SW: steady traffic, TB: bursty traffic
4. How to scale? → Redis Cluster, consistent hashing per key

## Author
**Muhammad Nadir** — [LinkedIn](https://linkedin.com/in/muhammad-nadir-26095646)
