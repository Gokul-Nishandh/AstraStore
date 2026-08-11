/**
 * Small helper for printing errors consistently across commands.
 * Routes through ErrorParser when possible, falls back to raw message.
 */
package com.astrastore.cli.commands;

import com.astrastore.cli.exception.ApiException;
import com.astrastore.cli.ui.ErrorParser;

public final class ErrorHandler {

    private ErrorHandler() {
    }

    public static void printError(Exception e) {
        if (e instanceof ApiException) {
            System.err.println(ErrorParser.friendlyMessage((ApiException) e));
        } else {
            System.err.println("Error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }
}
