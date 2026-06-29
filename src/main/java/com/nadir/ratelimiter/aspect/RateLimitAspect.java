package com.nadir.ratelimiter.aspect;

import com.nadir.ratelimiter.algorithm.SlidingWindowRateLimiter;
import com.nadir.ratelimiter.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final SlidingWindowRateLimiter rateLimiter;

    @Around("@annotation(com.nadir.ratelimiter.aspect.RateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit annotation = method.getAnnotation(RateLimit.class);

        String identifier = resolveIdentifier(annotation);
        long windowMillis = annotation.timeUnit().toMillis(annotation.duration());

        SlidingWindowRateLimiter.RateLimitResult result =
                rateLimiter.isAllowed(identifier, annotation.requests(), windowMillis);

        // Set standard rate limit headers
        addHeaders(result, windowMillis);

        if (!result.allowed()) {
            log.warn("Rate limit exceeded for identifier: {}", identifier);
            throw new RateLimitExceededException(
                    "Rate limit exceeded. Try again in " + annotation.duration() + " " + annotation.timeUnit());
        }

        return joinPoint.proceed();
    }

    private String resolveIdentifier(RateLimit annotation) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        String prefix = annotation.key().isBlank() ? "global" : annotation.key();

        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String ip = getClientIp(request);
            return prefix + ":" + ip;
        }
        return prefix + ":unknown";
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp;
        return request.getRemoteAddr();
    }

    private void addHeaders(SlidingWindowRateLimiter.RateLimitResult result, long windowMillis) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return;

        HttpServletResponse response = attrs.getResponse();
        if (response == null) return;

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, result.remaining())));
        response.setHeader("X-RateLimit-Reset", String.valueOf(
                (System.currentTimeMillis() + windowMillis) / 1000));
    }
}
