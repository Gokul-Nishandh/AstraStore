package com.astrastore.sdk.exception;

public class AstraAuthException extends AstraException {
    public AstraAuthException(String message) {
        super(message, 401);
    }

    public AstraAuthException(String message, int statusCode) {
        super(message, statusCode);
    }
}
