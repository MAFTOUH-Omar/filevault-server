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

/** Scoped (via its FilterRegistrationBean URL patterns) to the unauthenticated /auth/* endpoints
 *  (register, login, refresh, logout) — a tighter budget than the global filter to blunt
 *  credential-stuffing, refresh-token guessing and spam-registration. */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties.Bucket bucket;
    private final ErrorResponseWriter errorResponseWriter;

    public AuthRateLimitFilter(
            RedisRateLimiter rateLimiter, RateLimitProperties properties, ErrorResponseWriter errorResponseWriter) {
        this.rateLimiter = rateLimiter;
        this.bucket = properties.auth();
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = "ratelimit:auth:" + request.getRemoteAddr() + ":" + request.getRequestURI();
        if (!rateLimiter.tryConsume(key, bucket.limit(), bucket.window())) {
            errorResponseWriter.write(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "RATE_LIMIT_EXCEEDED",
                    "Too many attempts, please try again later");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
