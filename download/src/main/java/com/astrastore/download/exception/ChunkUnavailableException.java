package com.astrastore.download.exception;

public class ChunkUnavailableException extends RuntimeException {
    public ChunkUnavailableException(String message) {
        super(message);
    }
    public ChunkUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
