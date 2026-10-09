package com.filevault.filevaultserver.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Bucket global, Bucket auth) {

    public record Bucket(int limit, Duration window) {
    }
}
