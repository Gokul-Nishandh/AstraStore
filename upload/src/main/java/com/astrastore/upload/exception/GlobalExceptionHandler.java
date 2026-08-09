package com.astrastore.upload.exception;

import io.micrometer.tracing.Tracer;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final Tracer tracer;

    @ExceptionHandler(ChunkWriteException.class)
    public ResponseEntity<ErrorResponse> handleChunkWrite(ChunkWriteException ex) {
        return buildResponse("BAD_GATEWAY", ex.getMessage(), HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(ChecksumMismatchException.class)
    public ResponseEntity<ErrorResponse> handleChecksumMismatch(ChecksumMismatchException ex) {
        return buildResponse("UNPROCESSABLE_ENTITY", ex.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(ObjectTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleObjectTooLarge(ObjectTooLargeException ex) {
        return buildResponse("PAYLOAD_TOO_LARGE", ex.getMessage(), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(NoAvailableNodesException.class)
    public ResponseEntity<ErrorResponse> handleNoAvailableNodes(NoAvailableNodesException ex) {
        return buildResponse("SERVICE_UNAVAILABLE", ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return buildResponse("VALIDATION_ERROR", msg, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception in upload service", ex);
        return buildResponse("INTERNAL_ERROR",
                ex.getMessage() != null ? ex.getMessage() : "Internal server error",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> buildResponse(String code, String message, HttpStatus status) {
        String traceId = getTraceId();
        ErrorResponse body = ErrorResponse.builder()
                .code(code)
                .message(message)
                .traceId(traceId)
                .build();
        return ResponseEntity.status(status).body(body);
    }

    private String getTraceId() {
        if (tracer != null && tracer.currentSpan() != null) {
            return tracer.currentSpan().context().traceId();
        }
        return "unknown";
    }

    @Getter
    @Builder
    public static class ErrorResponse {
        private final String code;
        private final String message;
        private final String traceId;
    }
}
