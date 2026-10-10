package com.filevault.filevaultserver.middleware;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public record ErrorResponse(String code, String message) {

    /**
     * Fixed, client-safe body for a bare status code. Nothing from the triggering exception (class
     * names, SQL, stack frames, framework messages) ever goes into it — the real cause is logged
     * server-side only.
     */
    public static ErrorResponse generic(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            return statusCode.is5xxServerError()
                    ? new ErrorResponse("INTERNAL_SERVER_ERROR", "Internal server error")
                    : new ErrorResponse("BAD_REQUEST", "Invalid request");
        }
        String message = switch (status) {
            case BAD_REQUEST -> "Invalid request";
            case UNAUTHORIZED -> "Authentication required";
            case FORBIDDEN -> "Access denied";
            case NOT_FOUND -> "Resource not found";
            case METHOD_NOT_ALLOWED -> "Method not allowed";
            case NOT_ACCEPTABLE -> "Not acceptable";
            case CONFLICT -> "The request conflicts with the current state of the resource";
            case CONTENT_TOO_LARGE -> "Payload too large";
            case UNSUPPORTED_MEDIA_TYPE -> "Unsupported media type";
            case TOO_MANY_REQUESTS -> "Too many requests";
            default -> status.is5xxServerError() ? "Internal server error" : "The request could not be processed";
        };
        return new ErrorResponse(status.name(), message);
    }
}
