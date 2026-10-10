package com.filevault.filevaultserver.middleware;

import com.filevault.filevaultserver.security.RateLimitProperties;
import com.filevault.filevaultserver.security.RedisRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies to every request, keyed by client IP alone — a coarse anti-DoS backstop. The stricter,
 *  per-endpoint budget for the unauthenticated /auth/* endpoints is enforced separately by
 *  AuthRateLimitFilter; a caller must pass both. */
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties.Bucket bucket;
    private final ErrorResponseWriter errorResponseWriter;

    public GlobalRateLimitFilter(
            RedisRateLimiter rateLimiter, RateLimitProperties properties, ErrorResponseWriter errorResponseWriter) {
        this.rateLimiter = rateLimiter;
        this.bucket = properties.global();
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = "ratelimit:global:" + request.getRemoteAddr();
        if (!rateLimiter.tryConsume(key, bucket.limit(), bucket.window())) {
            errorResponseWriter.write(
                    response, HttpStatus.TOO_MANY_REQUESTS.value(), "RATE_LIMIT_EXCEEDED", "Too many requests");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
