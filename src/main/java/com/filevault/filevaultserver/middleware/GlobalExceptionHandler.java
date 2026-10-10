package com.filevault.filevaultserver.middleware;

import com.filevault.filevaultserver.exception.auth.EmailAlreadyUsedException;
import com.filevault.filevaultserver.exception.auth.InvalidCredentialsException;
import com.filevault.filevaultserver.exception.auth.InvalidRefreshTokenException;
import com.filevault.filevaultserver.exception.auth.TooManyProfileChangesException;
import com.filevault.filevaultserver.exception.file.FileTooLargeException;
import com.filevault.filevaultserver.exception.file.StorageQuotaExceededException;
import com.filevault.filevaultserver.exception.file.StorageUnavailableException;
import com.filevault.filevaultserver.exception.file.StoredFileNotFoundException;
import com.filevault.filevaultserver.exception.file.UploadNotReceivedException;
import com.filevault.filevaultserver.exception.file.UploadSizeMismatchException;
import com.filevault.filevaultserver.exception.role.ForbiddenRoleGrantException;
import com.filevault.filevaultserver.exception.role.RoleAlreadyExistsException;
import com.filevault.filevaultserver.exception.role.RoleHasUsersException;
import com.filevault.filevaultserver.exception.role.RoleNotFoundException;
import com.filevault.filevaultserver.exception.user.UserNotFoundException;
import com.filevault.filevaultserver.security.AbuseGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single place a controller-side exception becomes an HTTP response. Two rules:
 * <ul>
 *   <li>Our own domain exceptions carry deliberately client-safe messages, so those are passed through.</li>
 *   <li>Everything else — Spring MVC binding/parse errors, database errors, any unexpected throwable —
 *       is answered with a fixed generic message ({@link ErrorResponse#generic}); the real cause is
 *       only ever logged. A client must never see a class name, SQL, constraint name or stack frame.</li>
 * </ul>
 * Extending ResponseEntityExceptionHandler routes every standard Spring MVC exception (bad JSON, type
 * mismatch, 404/405/415, ...) through {@link #handleExceptionInternal}, so none of them can fall
 * through to Boot's default error body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AbuseGuard abuseGuard;

    public GlobalExceptionHandler(AbuseGuard abuseGuard) {
        this.abuseGuard = abuseGuard;
    }

    // ---- Spring MVC exceptions: generic body, optionally naming the offending parameter ----

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Request failed", ex);
        } else {
            log.debug("Request rejected: {}", ex.toString());
        }
        return ResponseEntity.status(statusCode).headers(headers).body(ErrorResponse.generic(statusCode));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Messages here come from the constraints we declared on our own request DTOs.
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "Invalid request parameters"));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = ex.getPropertyName();
        String message = name == null ? "Invalid request parameter" : "Invalid value for parameter '" + name + "'";
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", message));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", "Missing required parameter '" + ex.getParameterName() + "'"));
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ErrorResponse> handleMissingCookie(MissingRequestCookieException ex) {
        // Not an abuse strike: a SPA with no session legitimately calls /auth/refresh with no cookie.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", "Missing refresh token cookie"));
    }

    // ---- Domain exceptions: messages are client-safe by construction ----

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("EMAIL_ALREADY_USED", ex.getMessage()));
    }

    @ExceptionHandler(RoleAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleRoleAlreadyExists(RoleAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("ROLE_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(RoleHasUsersException.class)
    public ResponseEntity<ErrorResponse> handleRoleHasUsers(RoleHasUsersException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("ROLE_HAS_USERS", ex.getMessage()));
    }

    @ExceptionHandler({RoleNotFoundException.class, UserNotFoundException.class, StoredFileNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleFileTooLarge(FileTooLargeException ex) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(new ErrorResponse("FILE_TOO_LARGE", ex.getMessage()));
    }

    @ExceptionHandler(StorageQuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleQuotaExceeded(StorageQuotaExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("STORAGE_QUOTA_EXCEEDED", ex.getMessage()));
    }

    @ExceptionHandler(UploadNotReceivedException.class)
    public ResponseEntity<ErrorResponse> handleUploadNotReceived(UploadNotReceivedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("UPLOAD_NOT_RECEIVED", ex.getMessage()));
    }

    @ExceptionHandler(UploadSizeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleUploadSizeMismatch(UploadSizeMismatchException ex) {
        return ResponseEntity.status(422).body(new ErrorResponse("UPLOAD_SIZE_MISMATCH", ex.getMessage()));
    }

    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleStorageUnavailable(StorageUnavailableException ex) {
        // The SDK failure behind it was already logged where it happened.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("STORAGE_UNAVAILABLE", ex.getMessage()));
    }

    @ExceptionHandler(TooManyProfileChangesException.class)
    public ResponseEntity<ErrorResponse> handleTooManyProfileChanges(TooManyProfileChangesException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse("RATE_LIMIT_EXCEEDED", ex.getMessage()));
    }

    // ---- Denials: each one is a strike towards a warning, then an IP blacklist (see AbuseGuard) ----

    @ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class})
    public ResponseEntity<ErrorResponse> handleAuthFailure(RuntimeException ex, HttpServletRequest request) {
        return denied(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenRoleGrantException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenRoleGrant(
            ForbiddenRoleGrantException ex, HttpServletRequest request) {
        return denied(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), request);
    }

    /** {@code @PreAuthorize} failures surface here (they are thrown inside the controller call, not the filter chain). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return denied(
                HttpStatus.FORBIDDEN, "FORBIDDEN", ErrorResponse.generic(HttpStatus.FORBIDDEN).message(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.generic(HttpStatus.UNAUTHORIZED));
    }

    private ResponseEntity<ErrorResponse> denied(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        AbuseGuard.Denial denial = abuseGuard.deny(request.getRemoteAddr(), status, code, message);
        return ResponseEntity.status(denial.status()).body(new ErrorResponse(denial.code(), denial.message()));
    }

    // ---- Infrastructure / unexpected: log the cause, tell the client nothing about it ----

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.generic(HttpStatus.CONFLICT));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.debug("Constraint violation: {}", ex.toString());
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "Invalid request parameters"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.generic(HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
