package com.filevault.filevaultserver.security;

import com.filevault.filevaultserver.middleware.ErrorResponse;
import com.filevault.filevaultserver.middleware.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 for an authenticated caller lacking a required authority, as an ErrorResponse and one abuse strike. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final String MESSAGE = ErrorResponse.generic(HttpStatus.FORBIDDEN).message();

    private final AbuseGuard abuseGuard;
    private final ErrorResponseWriter errorResponseWriter;

    public RestAccessDeniedHandler(AbuseGuard abuseGuard, ErrorResponseWriter errorResponseWriter) {
        this.abuseGuard = abuseGuard;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        AbuseGuard.Denial denial =
                abuseGuard.deny(request.getRemoteAddr(), HttpStatus.FORBIDDEN, "FORBIDDEN", MESSAGE);
        errorResponseWriter.write(response, denial.status(), denial.code(), denial.message());
    }
}
