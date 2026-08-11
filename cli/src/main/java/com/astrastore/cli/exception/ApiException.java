/**
 * Thrown when HTTP API calls fail (non-2xx status codes).
 * Carries status code, response body, and request path for friendly error parsing.
 * Maps to CLI exit code 6 in Main entry point.
 * Use ErrorParser.friendlyMessage(this) to render user-facing output.
 */
package com.astrastore.cli.exception;

public class ApiException extends RuntimeException {

    private final int statusCode;
    private final String path;
    private final String body;

    public ApiException(String message) {
        super(message);
        this.statusCode = 0;
        this.path = "";
        this.body = "";
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.path = "";
        this.body = "";
    }

    public ApiException(int statusCode, String path, String body) {
        super("HTTP " + statusCode + " " + path + ": " + body);
        this.statusCode = statusCode;
        this.path = path;
        this.body = body;
    }

    public ApiException(int statusCode, String path, String body, Throwable cause) {
        super("HTTP " + statusCode + " " + path + ": " + body, cause);
        this.statusCode = statusCode;
        this.path = path;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getPath() {
        return path;
    }

    public String getResponseBody() {
        return body;
    }
}
