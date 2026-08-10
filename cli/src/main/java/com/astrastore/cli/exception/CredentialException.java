/**
 * Thrown when credential operations fail (load, save, decrypt, delete).
 * Maps to CLI exit code 5 in Main entry point.
 * Used by all auth commands to signal recoverable auth failures.
 */
package com.astrastore.cli.exception;

public class CredentialException extends RuntimeException {
    public CredentialException(String message) {
        super(message);
    }

    public CredentialException(String message, Throwable cause) {
        super(message, cause);
    }
}
