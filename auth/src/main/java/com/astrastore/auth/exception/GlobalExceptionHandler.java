/**
 * Central exception handler. Every failure leaving this service is an
 * {@link ApiError} — {@code {code, message, requestId, timestamp}} — and
 * nothing else.
 *
 * <p>Two rules govern what goes in {@code message}:
 * <ul>
 *   <li>It is safe to render verbatim in a browser. No stack trace, no SQL,
 *       no internal hostname, no exception class name.</li>
 *   <li>It never discloses whether a resource the caller may not see exists.</li>
 * </ul>
 * The detail an operator needs is logged server-side against the request id,
 * which the client is given so it can be quoted in a support request.
 */
package com.astrastore.auth.exception;

import com.astrastore.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    // --- 400 -------------------------------------------------------------

    @ExceptionHandler(ApiKeyException.class)
    public ResponseEntity<ApiError> handleApiKeyException(
            ApiKeyException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("[{}] API key error: {} — path={}", requestId, ex.getMessage(), request.getRequestURI());
        return body(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, ex.getMessage(), requestId);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": "
                        + (fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
                .distinct()
                .collect(Collectors.joining("; "));
        if (detail.isBlank()) {
            detail = "Request body failed validation";
        }
        log.warn("[{}] Validation failed — path={}, detail={}", requestId, request.getRequestURI(), detail);
        return body(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, detail, requestId);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        String detail = ex.getConstraintViolations().stream()
                .map(v -> lastPathNode(v) + ": " + v.getMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        log.warn("[{}] Constraint violation — path={}, detail={}", requestId, request.getRequestURI(), detail);
        return body(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED,
                detail.isBlank() ? "Request failed validation" : detail, requestId);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiError> handleMalformedRequest(
            Exception ex, HttpServletRequest request) {
        String requestId = requestId(request);
        // The exception's own message quotes internal type names and parser
        // offsets; the client gets a stable sentence instead.
        log.warn("[{}] Malformed request — path={}, error={}", requestId,
                request.getRequestURI(), ex.getMessage());
        return body(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED,
                "The request could not be read. Check the request body and parameter types.", requestId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("[{}] Bad argument — path={}, error={}", requestId,
                request.getRequestURI(), ex.getMessage());
        return body(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, ex.getMessage(), requestId);
    }

    // --- 401 -------------------------------------------------------------

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiError> handleAuthException(
            AuthException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("[{}] Auth error: {} — path={}", requestId, ex.getMessage(), request.getRequestURI());
        return body(HttpStatus.UNAUTHORIZED, ApiError.UNAUTHENTICATED, ex.getMessage(), requestId);
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ApiError> handleBadCredentials(
            Exception ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("[{}] Bad credentials — path={}", requestId, request.getRequestURI());
        // Identical response for "no such account" and "wrong password":
        // distinguishing them enumerates registered addresses.
        return body(HttpStatus.UNAUTHORIZED, ApiError.UNAUTHENTICATED,
                "Invalid email or password", requestId);
    }

    // --- 403 -------------------------------------------------------------

    @ExceptionHandler({DisabledException.class, LockedException.class})
    public ResponseEntity<ApiError> handleDisabled(
            AuthenticationException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("[{}] Disabled or locked account — path={}", requestId, request.getRequestURI());
        return body(HttpStatus.FORBIDDEN, ApiError.FORBIDDEN,
                "This account has been disabled. Contact an administrator.", requestId);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(
            ForbiddenException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("[{}] Forbidden: {} — path={}", requestId, ex.getMessage(), request.getRequestURI());
        return body(HttpStatus.FORBIDDEN, ApiError.FORBIDDEN, ex.getMessage(), requestId);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("[{}] Access denied — path={}", requestId, request.getRequestURI());
        return body(HttpStatus.FORBIDDEN, ApiError.FORBIDDEN,
                "You do not have permission to perform this action", requestId);
    }

    // --- 404 -------------------------------------------------------------

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(
            Exception ex, HttpServletRequest request) {
        String requestId = requestId(request);
        String message = ex instanceof NotFoundException ? ex.getMessage() : "Resource not found";
        log.warn("[{}] Not found — path={}", requestId, request.getRequestURI());
        return body(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, message, requestId);
    }

    // --- 405 -------------------------------------------------------------

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        return body(HttpStatus.METHOD_NOT_ALLOWED, ApiError.VALIDATION_FAILED,
                "That method is not supported for this endpoint", requestId);
    }

    // --- 409 -------------------------------------------------------------

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(
            ConflictException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("[{}] Conflict: {} — path={}", requestId, ex.getMessage(), request.getRequestURI());
        return body(HttpStatus.CONFLICT, ApiError.CONFLICT, ex.getMessage(), requestId);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        // The driver's message names the constraint, table and offending
        // value. That is a schema disclosure and stays in the log.
        log.warn("[{}] Data integrity violation — path={}, error={}", requestId,
                request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return body(HttpStatus.CONFLICT, ApiError.CONFLICT,
                "That change conflicts with an existing record", requestId);
    }

    // --- 500 -------------------------------------------------------------

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("[{}] Illegal state — path={}, error={}", requestId,
                request.getRequestURI(), ex.getMessage(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, ApiError.INTERNAL_ERROR,
                "An unexpected error occurred", requestId);
    }

    /**
     * The backstop. Anything unrecognised is logged in full, with its stack,
     * against a request id — and the caller is told nothing but that id.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(
            Exception ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("[{}] Unhandled exception — path={}, type={}, error={}", requestId,
                request.getRequestURI(), ex.getClass().getName(), ex.getMessage(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, ApiError.INTERNAL_ERROR,
                "An unexpected error occurred. Quote request id " + requestId
                        + " if you contact support.", requestId);
    }

    // --- helpers ---------------------------------------------------------

    private static ResponseEntity<ApiError> body(
            HttpStatus status, String code, String message, String requestId) {
        String safe = (message == null || message.isBlank()) ? status.getReasonPhrase() : message;
        return ResponseEntity.status(status).body(ApiError.of(code, safe, requestId));
    }

    /**
     * Correlates the client's error with the server's log line. Prefers a
     * trace id propagated by the gateway so a failure can be followed across
     * services.
     */
    private static String requestId(HttpServletRequest request) {
        if (request != null) {
            String header = request.getHeader("X-Request-Id");
            if (header != null && !header.isBlank()) {
                return header.length() > 64 ? header.substring(0, 64) : header;
            }
        }
        return UUID.randomUUID().toString();
    }

    private static String lastPathNode(ConstraintViolation<?> violation) {
        String path = String.valueOf(violation.getPropertyPath());
        int dot = path.lastIndexOf('.');
        return dot >= 0 && dot < path.length() - 1 ? path.substring(dot + 1) : path;
    }
}
