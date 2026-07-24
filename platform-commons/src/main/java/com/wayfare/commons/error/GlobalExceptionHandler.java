package com.wayfare.commons.error;

import com.wayfare.commons.correlation.Correlation;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;

/**
 * Maps the {@link AppException} hierarchy (and framework validation errors) to
 * an identical RFC 9457 {@code application/problem+json} shape across every
 * service. Internal details are logged in full but never returned to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://api.wayfare.local/errors/";

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ProblemDetail> handleApp(AppException ex, HttpServletRequest request) {
        log.warn("AppException [{}]: {}", ex.type(), ex.getMessage());
        ProblemDetail pd = base(ex.status(), ex.type(),
                capitalize(ex.type().replace('-', ' ')), ex.getMessage(), request);
        return ResponseEntity.status(ex.status()).body(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ProblemDetail pd = base(HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed",
                "One or more fields are invalid.", request);
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Log the full detail; return a safe message + correlation id only.
        log.error("Unhandled exception", ex);
        ProblemDetail pd = base(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Internal server error", "An unexpected error occurred.", request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(pd);
    }

    private ProblemDetail base(HttpStatus status, String type, String title,
                               String detail, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_BASE + type));
        pd.setTitle(title);
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("correlationId", MDC.get(Correlation.MDC_KEY));
        pd.setProperty("service", serviceName);
        return pd;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** One invalid field in a validation error response. */
    public record FieldError(String field, String message) {
    }
}
