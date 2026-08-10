/**
 * Interactive shell command (REPL mode) for the astra CLI.
 * Reads commands from stdin and dispatches them through the same
 * Picocli command tree, allowing users to run multiple commands in one session.
 * Supports quit/exit/q to leave the shell and parent subcommand routing.
 */
package com.astrastore.cli.commands;

import picocli.CommandLine;
import picocli.CommandLine.IFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "shell",
        mixinStandardHelpOptions = true,
        description = "Start an interactive shell session."
)
public class ShellCommand implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(names = {"--no-banner"}, description = "Skip the welcome banner")
    private boolean noBanner;

    @Override
    public Integer call() throws Exception {
        if (!noBanner) {
            System.out.println("astra shell — type 'help' for commands, 'quit' to exit");
            System.out.println();
        }

        IFactory factory = spec.commandLine().getFactory();
        CommandLine cmd = new CommandLine(spec.root().userObject(), factory);

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("astra> ");
            System.out.flush();
            String line = reader.readLine();
            if (line == null) {
                System.out.println();
                break;
            }
            line = line.trim();
            if (line.isEmpty()) continue;
            if (isExitCommand(line)) break;
            if (line.equals("help")) {
                cmd.usage(System.out);
                continue;
            }

            try {
                int rc = cmd.execute(line.split("\\s+"));
                if (rc != 0 && rc != 2) {
                    System.err.println("(exit code " + rc + ")");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }

        System.out.println("Goodbye.");
        return 0;
    }

    private boolean isExitCommand(String line) {
        String lower = line.toLowerCase();
        return lower.equals("quit") || lower.equals("exit") || lower.equals("q");
    }
}
