package com.astrastore.download.exception;

public class ChecksumVerificationException extends RuntimeException {
    public ChecksumVerificationException(String message) {
        super(message);
    }
    public ChecksumVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
