/**
 * Interactive shell command (REPL mode) for the astra CLI.
 * Uses jline3 LineReader for:
 *  - Command history with UP/DOWN arrow key navigation
 *  - Line editing (left/right arrows, backspace, delete)
 *  - Persistent history saved to ~/.astra/history
 * Shows a contextual prompt with the logged-in user when available.
 */
package com.astrastore.cli.commands;

import com.astrastore.cli.auth.CredentialStore;
import com.astrastore.cli.ui.ColorSupport;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.EndOfFileException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "shell",
        mixinStandardHelpOptions = true,
        description = "Start an interactive shell session."
)
public class ShellCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"--no-banner"}, description = "Skip the welcome banner")
    private boolean noBanner;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        if (!noBanner) {
            System.out.println(ColorSupport.cyan("astra shell") + " — type 'help' for commands, 'quit' to exit");
            System.out.println();
        }

        Terminal terminal = null;
        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .jna(false)
                    .jansi(false)
                    .build();
            Path historyFile = Paths.get(System.getProperty("user.home"), ".astra", "history");
            historyFile.getParent().toFile().mkdirs();

            String prompt = buildPrompt();
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .variable(LineReader.HISTORY_FILE, historyFile)
                    .variable(LineReader.SECONDARY_PROMPT_PATTERN, "")
                    .build();

            picocli.CommandLine cmd = new picocli.CommandLine(spec.root().userObject());

            while (true) {
                String line;
                try {
                    line = reader.readLine(prompt);
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                if (isExitCommand(line)) break;
                if (line.equals("help") || line.equals("--help") || line.equals("-h")) {
                    cmd.usage(System.out);
                    continue;
                }

                try {
                    int rc = cmd.execute(line.split("\\s+"));
                    if (rc != 0 && rc != 2) {
                        System.err.println("(exit code " + rc + ")");
                    }
                } catch (Exception e) {
                    System.err.println(ColorSupport.error("Error: " + e.getMessage()));
                }
            }
        } catch (Exception e) {
            if (!(e instanceof java.io.IOException)) {
                System.err.println("Shell error: " + e.getMessage());
            }
        } finally {
            if (terminal != null) {
                try { terminal.close(); } catch (Exception ignored) {}
            }
        }

        System.out.println("Goodbye.");
        return 0;
    }

    private String buildPrompt() {
        String user = "anonymous";
        try {
            CredentialStore.Credentials creds = CredentialStore.getInstance().load();
            if (creds != null && creds.getEmail() != null) {
                user = creds.getEmail();
            }
        } catch (Exception ignored) {
        }
        return ColorSupport.cyan("astra [" + user + "]> ") + ColorSupport.RESET;
    }

    private boolean isExitCommand(String line) {
        String lower = line.toLowerCase();
        return lower.equals("quit") || lower.equals("exit") || lower.equals("q");
    }
}
