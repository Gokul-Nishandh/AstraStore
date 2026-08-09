package com.astrastore.upload.exception;

public class ChunkWriteException extends RuntimeException {
    public ChunkWriteException(String message) {
        super(message);
    }
    public ChunkWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
