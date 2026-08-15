package com.astrastore.placement.exception;

import com.astrastore.shared.api.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Maps every failure to the platform's single {@link ApiError} envelope so a
 * client never has to interpret a servlet error page or a stack trace.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleValidation(Exception ex) {
        log.warn("Invalid request — {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of(ApiError.VALIDATION_FAILED, "The request parameters were not valid."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument — {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of(ApiError.VALIDATION_FAILED, "The request could not be processed."));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ApiError.NOT_FOUND, "Resource not found."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled exception in placement service", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ApiError.INTERNAL_ERROR, "An unexpected error occurred."));
    }
}
