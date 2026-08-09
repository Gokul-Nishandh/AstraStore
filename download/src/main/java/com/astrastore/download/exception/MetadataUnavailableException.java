package com.astrastore.download.exception;

public class MetadataUnavailableException extends RuntimeException {
    public MetadataUnavailableException(String message) {
        super(message);
    }
    public MetadataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
