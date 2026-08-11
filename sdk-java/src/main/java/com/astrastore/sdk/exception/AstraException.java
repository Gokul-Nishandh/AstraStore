package com.astrastore.sdk.exception;

public class AstraException extends RuntimeException {
    private final int statusCode;

    public AstraException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public AstraException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public AstraException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public AstraException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
