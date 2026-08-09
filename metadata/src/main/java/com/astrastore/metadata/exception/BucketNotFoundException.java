package com.astrastore.metadata.exception;

public class BucketNotFoundException extends RuntimeException {

    public BucketNotFoundException(String message) {
        super(message);
    }
}