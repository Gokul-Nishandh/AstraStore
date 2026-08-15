package com.astrastore.download.exception;

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

    /*
     * Exception messages here carry internal service URLs, container hostnames
     * and downstream response bodies — useful in a log, disqualifying in a
     * response. Every handler below logs the detail and returns a sentence
     * written for the person who triggered it.
     */

    @ExceptionHandler(ChunkUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleChunkUnavailable(ChunkUnavailableException ex) {
        log.warn("Chunk unavailable", ex);
        return buildResponse("BAD_GATEWAY",
                "That file could not be read from storage. Please try again shortly.",
                HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(ChecksumVerificationException.class)
    public ResponseEntity<ErrorResponse> handleChecksumVerification(ChecksumVerificationException ex) {
        log.error("Checksum mismatch on read", ex);
        return buildResponse("UNPROCESSABLE_ENTITY",
                "That file failed its integrity check and was not returned.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(ObjectNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleObjectNotFound(ObjectNotFoundException ex) {
        log.info("Object not found: {}", ex.getMessage());
        return buildResponse("NOT_FOUND",
                "We could not find that object.",
                HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MetadataUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleMetadataUnavailable(MetadataUnavailableException ex) {
        log.error("Metadata service call failed", ex);
        return buildResponse("SERVICE_UNAVAILABLE",
                "AstraStore is temporarily unavailable. Please try again in a moment.",
                HttpStatus.SERVICE_UNAVAILABLE);
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
        log.error("Unhandled exception in download service", ex);
        return buildResponse("INTERNAL_ERROR",
                "Something went wrong on our side. Please try again.",
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
