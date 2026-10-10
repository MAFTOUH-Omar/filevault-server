package com.filevault.filevaultserver.security;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counter: INCR the key, set its TTL the first time it's created. Simple and cheap,
 * at the cost of allowing a short burst around window boundaries compared to a sliding window —
 * an acceptable trade-off for abuse/brute-force protection rather than precise metering.
 */
@Component
public class RedisRateLimiter {

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryConsume(String key, int limit, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            return true;
        }
        if (count == 1L) {
            redisTemplate.expire(key, window);
        }
        return count <= limit;
    }
}
