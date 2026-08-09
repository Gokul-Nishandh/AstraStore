package com.astrastore.upload.exception;

public class ObjectTooLargeException extends RuntimeException {
    public ObjectTooLargeException(String message) {
        super(message);
    }
}
