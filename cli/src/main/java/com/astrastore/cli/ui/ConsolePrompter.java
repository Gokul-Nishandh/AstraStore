/**
 * Interactive terminal prompter using jline3 for arrow-key navigation.
 * Provides single-select dropdown menus for resource selection.
 * Falls back to numbered-list stdin input when the terminal is not a TTY
 * (so non-interactive scripts can still drive selection via piped input).
 */
package com.astrastore.cli.ui;

import java.io.IOException;
import java.util.List;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

public final class ConsolePrompter {

    private ConsolePrompter() {
    }

    public static boolean isInteractive() {
        return System.console() != null;
    }

    /**
     * Present an arrow-key single-select menu.
     * @param prompt question shown above the menu
     * @param options list of items to choose from
     * @return 0-based index of the selected item, or -1 if cancelled
     */
    public static int selectSingle(String prompt, List<String> options) {
        if (options == null || options.isEmpty()) {
            return -1;
        }
        if (options.size() == 1) {
            System.out.println(ColorSupport.cyan(prompt));
            System.out.println("  " + ColorSupport.green("→ " + options.get(0)));
            return 0;
        }
        if (!isInteractive()) {
            return selectFromStdin(prompt, options);
        }
        return selectWithJLine(prompt, options);
    }

    private static int selectFromStdin(String prompt, List<String> options) {
        System.out.println(ColorSupport.cyan(prompt));
        for (int i = 0; i < options.size(); i++) {
            System.out.println("  " + (i + 1) + ") " + options.get(i));
        }
        System.out.print(ColorSupport.cyan("Enter choice (1-" + options.size() + "): "));
        try {
            String line = new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
            if (line == null || line.trim().isEmpty()) return -1;
            int choice = Integer.parseInt(line.trim());
            if (choice >= 1 && choice <= options.size()) return choice - 1;
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static int selectWithJLine(String prompt, List<String> options) {
        Terminal terminal = null;
        Attributes originalAttributes = null;
        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .jna(false)
                    .jansi(false)
                    .build();

            // CRITICAL FIX: Enter raw mode so arrow keypresses are read immediately
            originalAttributes = terminal.enterRawMode();
            NonBlockingReader reader = terminal.reader();

            int selected = 0;
            int lastDrawnLines = 0;

            System.out.println(ColorSupport.cyan(prompt));
            lastDrawnLines = drawMenu(options, selected);

            while (true) {
                int ch = reader.read();
                if (ch == -1) {
                    clearMenu(lastDrawnLines);
                    return -1;
                }

                // ENTER key
                if (ch == 10 || ch == 13) {
                    clearMenu(lastDrawnLines);
                    // Render final selection statically
                    System.out.println("  " + ColorSupport.green("✓ Selected: " + options.get(selected)));
                    return selected;
                }

                // ESC Sequences (Arrow Keys)
                if (ch == 27) {
                    int ch2 = reader.read();
                    if (ch2 == '[' || ch2 == 'O') {
                        int code = reader.read();
                        if (code == 'A') { // UP
                            selected = (selected > 0) ? selected - 1 : options.size() - 1;
                        } else if (code == 'B') { // DOWN
                            selected = (selected < options.size() - 1) ? selected + 1 : 0;
                        }
                        clearMenu(lastDrawnLines);
                        lastDrawnLines = drawMenu(options, selected);
                        continue;
                    }
                }

                // Quit / Cancel
                if (ch == 'q' || ch == 'Q' || ch == 3) { // 3 = Ctrl+C
                    clearMenu(lastDrawnLines);
                    return -1;
                }

                // Vim Keybindings (k = UP, j = DOWN)
                if (ch == 'k' || ch == 'K') {
                    selected = (selected > 0) ? selected - 1 : options.size() - 1;
                    clearMenu(lastDrawnLines);
                    lastDrawnLines = drawMenu(options, selected);
                } else if (ch == 'j' || ch == 'J') {
                    selected = (selected < options.size() - 1) ? selected + 1 : 0;
                    clearMenu(lastDrawnLines);
                    lastDrawnLines = drawMenu(options, selected);
                }

                // Direct Number Selection (1-9)
                if (ch >= '1' && ch <= '9') {
                    int index = ch - '1';
                    if (index < options.size()) {
                        selected = index;
                        clearMenu(lastDrawnLines);
                        lastDrawnLines = drawMenu(options, selected);
                    }
                }
            }
        } catch (IOException e) {
            return selectFromStdin(prompt, options);
        } finally {
            if (terminal != null) {
                if (originalAttributes != null) {
                    terminal.setAttributes(originalAttributes); // Restore original TTY mode
                }
                try {
                    terminal.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static int drawMenu(List<String> options, int selected) {
        for (int i = 0; i < options.size(); i++) {
            String marker = (i == selected) ? "❯" : " ";
            String color = (i == selected) ? ColorSupport.GREEN : ColorSupport.DIM;
            String line = "  " + color + marker + " " + options.get(i) + ColorSupport.RESET;
            System.out.println(line);
        }
        return options.size();
    }

    private static void clearMenu(int lines) {
        if (lines <= 0) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines; i++) {
            sb.append("\u001B[1A"); // Move cursor UP 1 line
            sb.append("\u001B[2K"); // Clear entire line
        }
        System.out.print(sb.toString());
        System.out.flush();
    }

    /**
     * Prompt for free-form text input.
     * @param prompt question to show
     * @param defaultValue pre-filled default (may be null)
     * @return user input, or defaultValue if blank
     */
    public static String promptText(String prompt, String defaultValue) {
        if (!isInteractive()) {
            if (defaultValue != null) {
                System.out.println(ColorSupport.cyan(prompt) + " [" + defaultValue + "]");
                return defaultValue;
            }
            System.out.println(ColorSupport.cyan(prompt));
            try {
                String line = new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
                return line == null ? defaultValue : line.trim();
            } catch (Exception e) {
                return defaultValue;
            }
        }

        String display = defaultValue != null ? prompt + " [" + defaultValue + "]: " : prompt + ": ";
        System.out.print(ColorSupport.cyan(display));
        System.out.flush();
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
            String line = reader.readLine();
            if (line == null) return defaultValue;
            line = line.trim();
            return line.isEmpty() ? defaultValue : line;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}