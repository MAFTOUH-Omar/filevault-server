package com.filevault.filevaultserver.config;

import com.filevault.filevaultserver.middleware.AuthRateLimitFilter;
import com.filevault.filevaultserver.middleware.ErrorResponseWriter;
import com.filevault.filevaultserver.middleware.GlobalRateLimitFilter;
import com.filevault.filevaultserver.middleware.IpBlacklistFilter;
import com.filevault.filevaultserver.middleware.PathTraversalFilter;
import com.filevault.filevaultserver.repository.security.BlacklistedIpRepository;
import com.filevault.filevaultserver.security.RateLimitProperties;
import com.filevault.filevaultserver.security.RedisRateLimiter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * These filters are plain classes, not {@code @Component}s, so they are registered exactly once —
 * here, with an explicit order — instead of also being auto-registered by Spring Boot for every
 * request. They run before Spring Security's own filter chain, in this order: reject obviously
 * malicious paths first (cheapest check), then blocked IPs, then the rate limits.
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<PathTraversalFilter> pathTraversalFilter(ErrorResponseWriter errorResponseWriter) {
        FilterRegistrationBean<PathTraversalFilter> registration =
                new FilterRegistrationBean<>(new PathTraversalFilter(errorResponseWriter));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<IpBlacklistFilter> ipBlacklistFilter(
            BlacklistedIpRepository blacklistedIpRepository, ErrorResponseWriter errorResponseWriter) {
        FilterRegistrationBean<IpBlacklistFilter> registration =
                new FilterRegistrationBean<>(new IpBlacklistFilter(blacklistedIpRepository, errorResponseWriter));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<GlobalRateLimitFilter> globalRateLimitFilter(
            RedisRateLimiter rateLimiter, RateLimitProperties properties, ErrorResponseWriter errorResponseWriter) {
        FilterRegistrationBean<GlobalRateLimitFilter> registration =
                new FilterRegistrationBean<>(new GlobalRateLimitFilter(rateLimiter, properties, errorResponseWriter));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter(
            RedisRateLimiter rateLimiter, RateLimitProperties properties, ErrorResponseWriter errorResponseWriter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration =
                new FilterRegistrationBean<>(new AuthRateLimitFilter(rateLimiter, properties, errorResponseWriter));
        registration.addUrlPatterns("/auth/register", "/auth/login");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        return registration;
    }
}
