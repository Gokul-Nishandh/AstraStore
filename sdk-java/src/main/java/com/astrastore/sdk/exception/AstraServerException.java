package com.astrastore.sdk.exception;

public class AstraServerException extends AstraException {
    public AstraServerException(String message, int statusCode) {
        super(message, statusCode);
    }
}
