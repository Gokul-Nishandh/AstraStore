package com.astrastore.storagenode.exception;

import com.astrastore.shared.api.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.IOException;

/**
 * Turns every escaping exception into the platform's single {@link ApiError}
 * envelope. Nothing internal — a path, a stack trace, a driver message —
 * reaches the caller; the detail goes to the log instead.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request — {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of(ApiError.VALIDATION_FAILED, "The request could not be processed."));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ApiError.NOT_FOUND, "Resource not found."));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiError> handleIo(IOException ex) {
        log.error("Storage I/O failure", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ApiError.INTERNAL_ERROR, "The storage node could not complete the operation."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled exception in storage node", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ApiError.INTERNAL_ERROR, "An unexpected error occurred."));
    }
}
