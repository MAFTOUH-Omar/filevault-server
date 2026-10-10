package com.filevault.filevaultserver.middleware;

import com.filevault.filevaultserver.repository.security.BlacklistedIpRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Blacklist is DB-backed (sec_blacklisted_ips), not Redis: it's small, changes rarely, and is
 *  administered data that should survive a cache flush — a plain indexed lookup is fast enough. */
public class IpBlacklistFilter extends OncePerRequestFilter {

    private final BlacklistedIpRepository blacklistedIpRepository;
    private final ErrorResponseWriter errorResponseWriter;

    public IpBlacklistFilter(BlacklistedIpRepository blacklistedIpRepository, ErrorResponseWriter errorResponseWriter) {
        this.blacklistedIpRepository = blacklistedIpRepository;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (blacklistedIpRepository.existsBySecIpAddress(request.getRemoteAddr())) {
            errorResponseWriter.write(response, HttpStatus.FORBIDDEN.value(), "IP_BLOCKED", "Access denied");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
