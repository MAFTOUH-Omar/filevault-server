package com.filevault.filevaultserver.security;

import com.filevault.filevaultserver.middleware.ErrorResponse;
import com.filevault.filevaultserver.middleware.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 401 for the actuator chain. Wrong HTTP Basic credentials count as an abuse strike (this is the
 * brute-force surface for the monitoring password); simply not sending credentials does not, so a
 * scraper that has not been configured yet does not get its IP blacklisted.
 */
@Component
public class MonitoringAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String MESSAGE = ErrorResponse.generic(HttpStatus.UNAUTHORIZED).message();

    private final AbuseGuard abuseGuard;
    private final ErrorResponseWriter errorResponseWriter;

    public MonitoringAuthenticationEntryPoint(AbuseGuard abuseGuard, ErrorResponseWriter errorResponseWriter) {
        this.abuseGuard = abuseGuard;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"monitoring\", charset=\"UTF-8\"");
        if (!(authException instanceof BadCredentialsException)) {
            errorResponseWriter.write(response, HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", MESSAGE);
            return;
        }
        AbuseGuard.Denial denial =
                abuseGuard.deny(request.getRemoteAddr(), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", MESSAGE);
        errorResponseWriter.write(response, denial.status(), denial.code(), denial.message());
    }
}
