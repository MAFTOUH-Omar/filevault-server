package com.filevault.filevaultserver.security;

import com.filevault.filevaultserver.middleware.ErrorResponse;
import com.filevault.filevaultserver.middleware.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 401 for anything Spring Security rejects before a controller runs. Writes our ErrorResponse (the
 * default entry point sends an empty body plus an error_description that echoes decoder internals).
 *
 * Only a bearer token that was actually presented and is bad counts as an abuse strike. A request
 * with no token at all (a SPA probing "am I logged in?") and an expired token (every session hits
 * that every 15 minutes) are routine, so they would otherwise get honest users blacklisted.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String MESSAGE = ErrorResponse.generic(HttpStatus.UNAUTHORIZED).message();

    private final AbuseGuard abuseGuard;
    private final ErrorResponseWriter errorResponseWriter;

    public RestAuthenticationEntryPoint(AbuseGuard abuseGuard, ErrorResponseWriter errorResponseWriter) {
        this.abuseGuard = abuseGuard;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        if (!isBadTokenPresented(authException)) {
            errorResponseWriter.write(response, HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", MESSAGE);
            return;
        }
        AbuseGuard.Denial denial =
                abuseGuard.deny(request.getRemoteAddr(), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", MESSAGE);
        errorResponseWriter.write(response, denial.status(), denial.code(), denial.message());
    }

    private static boolean isBadTokenPresented(AuthenticationException ex) {
        return ex instanceof OAuth2AuthenticationException && !isExpiredJwt(ex);
    }

    private static boolean isExpiredJwt(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof JwtValidationException validation
                    && validation.getErrors().stream()
                            .anyMatch(error -> error.getDescription() != null
                                    && error.getDescription().toLowerCase().contains("expired"))) {
                return true;
            }
        }
        return false;
    }
}
