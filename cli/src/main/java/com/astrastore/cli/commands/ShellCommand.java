/**
 * Interactive shell mode (REPL) for the astra CLI.
 * Allows users to run multiple commands without re-invoking the binary.
 * Uses Picocli to parse each line as a subcommand.
 */
package com.astrastore.cli.commands;

import picocli.CommandLine;

@CommandLine.Command(
        name = "shell",
        description = "Start an interactive shell session."
)
public class ShellCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        System.out.println("Interactive shell - TODO Phase 7");
    }
}
