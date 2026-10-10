package com.filevault.filevaultserver.middleware;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's BasicErrorController (it backs off when an ErrorController bean exists).
 * Anything that reaches the container's error dispatch — a filter calling sendError, a Tomcat-level
 * 400, an exception no advice handled — would otherwise get Boot's JSON with timestamp/path/trace/
 * exception message. This serves the same fixed ErrorResponse shape as everything else instead.
 */
@Hidden
@RestController
public class ErrorEndpointController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = attribute instanceof Integer code ? code : HttpStatus.INTERNAL_SERVER_ERROR.value();
        return ResponseEntity.status(status).body(ErrorResponse.generic(HttpStatusCode.valueOf(status)));
    }
}
