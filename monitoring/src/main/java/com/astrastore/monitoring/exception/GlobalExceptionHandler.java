package com.astrastore.monitoring.exception;

import com.astrastore.shared.api.ApiError;
import com.astrastore.shared.security.AstraHeaders;
import com.astrastore.shared.security.CurrentUser;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Renders every failure as the platform's {@link ApiError} envelope —
 * {@code {code, message, requestId, timestamp}} — so a client never has to
 * parse Spring's default body or a container error page.
 *
 * <p>{@code message} is written for a person and is safe to display verbatim.
 * The catch-all returns a fixed sentence so an internal fault cannot leak a
 * stack trace, a SQL fragment or an internal hostname to the browser.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final ObjectProvider<Tracer> tracerProvider;

    public GlobalExceptionHandler(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @ExceptionHandler(UnknownServiceException.class)
    public ResponseEntity<ApiError> handleUnknownService(UnknownServiceException ex,
                                                         HttpServletRequest request) {
        return build(ApiError.NOT_FOUND, ex.getMessage(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public ResponseEntity<ApiError> handleNoHandler(Exception ex, HttpServletRequest request) {
        return build(ApiError.NOT_FOUND, "No such endpoint.", HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(CurrentUser.AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(CurrentUser.AccessDeniedException ex,
                                                       HttpServletRequest request) {
        return build(ApiError.FORBIDDEN, ex.getMessage(), HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> handleSpringAccessDenied(
            org.springframework.security.access.AccessDeniedException ex,
            HttpServletRequest request) {
        return build(ApiError.FORBIDDEN, "You do not have access to this resource.",
                HttpStatus.FORBIDDEN, request);
    }

    /**
     * Covers an unsupported {@code window}. The message names the accepted
     * values because the caller cannot guess them and a silent fallback to a
     * different range would misreport the period the numbers describe.
     */
    @ExceptionHandler({ IllegalArgumentException.class,
                        MethodArgumentTypeMismatchException.class,
                        MissingServletRequestParameterException.class,
                        HttpMessageNotReadableException.class })
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        String message = ex instanceof IllegalArgumentException && ex.getMessage() != null
                ? ex.getMessage()
                : "The request could not be understood.";
        return build(ApiError.VALIDATION_FAILED, message, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        return build(ApiError.VALIDATION_FAILED, "That method is not supported on this endpoint.",
                HttpStatus.METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("Unhandled exception in monitoring service — requestId={}, method={}, path={}",
                requestId, request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ApiError.INTERNAL_ERROR,
                        "Something went wrong on our side. Please try again.", requestId));
    }

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
