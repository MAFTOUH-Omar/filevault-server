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
        return increment(key, window) <= limit;
    }

    /** Adds one to the counter at {@code key} (starting its {@code window} TTL on first use) and returns the new count. */
    public long increment(String key, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            return 0L;
        }
        if (count == 1L) {
            redisTemplate.expire(key, window);
        }
        return count;
    }

    public void reset(String key) {
        redisTemplate.delete(key);
    }
}
