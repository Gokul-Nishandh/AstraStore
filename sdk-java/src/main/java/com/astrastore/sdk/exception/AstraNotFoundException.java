package com.astrastore.sdk.exception;

public class AstraNotFoundException extends AstraException {
    public AstraNotFoundException(String message) {
        super(message, 404);
    }
}
