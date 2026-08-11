/**
 * ANSI color helper for terminal output.
 * Detects TTY support and disables colors when output is piped/redirected.
 * Used by ErrorParser, OutputFormatter, and ProgressRenderer for consistent styling.
 */
package com.astrastore.cli.ui;

public final class ColorSupport {

    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";

    private static final boolean ENABLED = detectColorSupport();

    private ColorSupport() {
    }

    private static boolean detectColorSupport() {
        if (System.getenv("NO_COLOR") != null) return false;
        if (System.getenv("ASTRA_NO_COLOR") != null) return false;
        return System.console() != null;
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static String red(String s) {
        return ENABLED ? RED + s + RESET : s;
    }

    public static String green(String s) {
        return ENABLED ? GREEN + s + RESET : s;
    }

    public static String yellow(String s) {
        return ENABLED ? YELLOW + s + RESET : s;
    }

    public static String cyan(String s) {
        return ENABLED ? CYAN + s + RESET : s;
    }

    public static String bold(String s) {
        return ENABLED ? BOLD + s + RESET : s;
    }

    public static String dim(String s) {
        return ENABLED ? DIM + s + RESET : s;
    }

    public static String error(String s) {
        return red("✖ " + s);
    }

    public static String success(String s) {
        return green("✓ " + s);
    }

    public static String info(String s) {
        return cyan("ℹ " + s);
    }

    public static String warning(String s) {
        return yellow("⚠ " + s);
    }
}
