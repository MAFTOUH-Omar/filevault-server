package com.filevault.filevaultserver.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** First line of defense: rejects requests whose path or query string carries a directory-traversal
 *  or null-byte payload, before any routing, auth, or rate-limit work happens. */
public class PathTraversalFilter extends OncePerRequestFilter {

    private static final int MAX_DECODE_PASSES = 3;

    private final ErrorResponseWriter errorResponseWriter;

    public PathTraversalFilter(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isSuspicious(request.getRequestURI()) || isSuspicious(request.getQueryString())) {
            errorResponseWriter.write(
                    response, HttpStatus.BAD_REQUEST.value(), "INVALID_REQUEST", "Request rejected");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Fully (percent-)decodes the value, up to a few passes to also catch double/triple encoding
     * (e.g. "%252e%252e" or mixed forms like ".." + "%2f"), then checks the decoded form for a
     * literal traversal sequence or a null byte. Checking only the raw value would miss any of
     * these encoded variants.
     */
    private boolean isSuspicious(String value) {
        if (value == null) {
            return false;
        }
        String decoded = value;
        for (int pass = 0; pass < MAX_DECODE_PASSES; pass++) {
            String next;
            try {
                next = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException malformedEncoding) {
                break;
            }
            if (next.equals(decoded)) {
                break;
            }
            decoded = next;
        }
        return decoded.contains("../") || decoded.contains("..\\") || decoded.indexOf('\0') >= 0;
    }
}
