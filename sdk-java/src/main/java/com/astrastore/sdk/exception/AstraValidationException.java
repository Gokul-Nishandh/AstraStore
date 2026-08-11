package com.astrastore.sdk.exception;

public class AstraValidationException extends AstraException {
    public AstraValidationException(String message, int statusCode) {
        super(message, statusCode);
    }
}
