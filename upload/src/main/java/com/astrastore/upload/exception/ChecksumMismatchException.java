package com.astrastore.upload.exception;

public class ChecksumMismatchException extends RuntimeException {
    public ChecksumMismatchException(String message) {
        super(message);
    }
}
