package com.astrastore.metadata.exception;

import com.astrastore.shared.api.ApiError;
import com.astrastore.shared.security.AstraHeaders;
import com.astrastore.shared.security.CurrentUser;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Renders every failure as the platform's {@link ApiError} envelope —
 * {@code {code, message, requestId, timestamp}} — so a client never has to
 * parse Spring's default body or a container error page.
 *
 * <p>Two rules govern what goes in {@code message}:
 * <ul>
 *   <li>It is written for a person and is safe to display verbatim.</li>
 *   <li>It never discloses whether a resource the caller cannot access
 *       exists. Ownership failures reach this class already converted to
 *       {@code *NotFoundException} by the service layer, and the catch-all
 *       below returns a fixed string so an internal fault cannot leak a
 *       stack trace, a SQL fragment or a hostname.</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final ObjectProvider<Tracer> tracerProvider;

    public GlobalExceptionHandler(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    // --- Not found --------------------------------------------------------

    @ExceptionHandler({ BucketNotFoundException.class, ObjectNotFoundException.class })
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return build(ApiError.NOT_FOUND, ex.getMessage(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public ResponseEntity<ApiError> handleNoHandler(Exception ex, HttpServletRequest request) {
        return build(ApiError.NOT_FOUND, "No such endpoint.", HttpStatus.NOT_FOUND, request);
    }

    // --- Authorisation ----------------------------------------------------

    /**
     * Raised by {@code CurrentUser.assertCanWrite()} / {@code assertAdmin()}:
     * the caller is authenticated but their role forbids the action. This is
     * genuinely a 403 — unlike an ownership miss, it discloses nothing about
     * anyone else's data.
     */
    @ExceptionHandler(CurrentUser.AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(CurrentUser.AccessDeniedException ex,
                                                       HttpServletRequest request) {
        return build(ApiError.FORBIDDEN, ex.getMessage(), HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> handleSpringAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        return build(ApiError.FORBIDDEN, "You do not have access to this resource.",
                HttpStatus.FORBIDDEN, request);
    }

    // --- Conflict ---------------------------------------------------------

    @ExceptionHandler(DuplicateBucketException.class)
    public ResponseEntity<ApiError> handleDuplicateBucket(DuplicateBucketException ex,
                                                          HttpServletRequest request) {
        return build(ApiError.CONFLICT, ex.getMessage(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex,
                                                       HttpServletRequest request) {
        return build(ApiError.CONFLICT, ex.getMessage(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        // The driver's message names tables, columns and constraint names.
        log.warn("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ApiError.CONFLICT, "The request conflicts with existing data.",
                HttpStatus.CONFLICT, request);
    }

    // --- Validation -------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return build(ApiError.VALIDATION_FAILED, message, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return build(ApiError.VALIDATION_FAILED, message, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleHandlerValidation(HandlerMethodValidationException ex,
                                                            HttpServletRequest request) {
        return build(ApiError.VALIDATION_FAILED, "One or more request parameters are invalid.",
                HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler({ MethodArgumentTypeMismatchException.class,
                        MissingServletRequestParameterException.class,
                        HttpMessageNotReadableException.class,
                        IllegalArgumentException.class })
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        log.debug("Rejected malformed request to {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ApiError.VALIDATION_FAILED, "The request could not be understood.",
                HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        return build(ApiError.VALIDATION_FAILED, "That method is not supported on this endpoint.",
                HttpStatus.METHOD_NOT_ALLOWED, request);
    }

    // --- Catch-all --------------------------------------------------------

    /**
     * Anything unanticipated. The detail goes to the log with the request id
     * attached; the caller gets a fixed sentence and that id, which is enough
     * to correlate with support and nothing more.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("Unhandled exception in metadata service — requestId={}, method={}, path={}",
                requestId, request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ApiError.INTERNAL_ERROR,
                        "Something went wrong on our side. Please try again.", requestId));
    }

    // ----------------------------------------------------------------------

    private ResponseEntity<ApiError> build(String code, String message, HttpStatus status,
                                           HttpServletRequest request) {
        String safeMessage = (message == null || message.isBlank()) ? status.getReasonPhrase() : message;
        return ResponseEntity.status(status).body(ApiError.of(code, safeMessage, requestId(request)));
    }

    /** Prefers the gateway's correlation id, falling back to the current trace id. */
    private String requestId(HttpServletRequest request) {
        if (request != null) {
            String header = request.getHeader(AstraHeaders.REQUEST_ID);
            if (header != null && !header.isBlank()) {
                return header;
            }
        }
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer != null && tracer.currentSpan() != null) {
            return tracer.currentSpan().context().traceId();
        }
        return null;
    }
}
