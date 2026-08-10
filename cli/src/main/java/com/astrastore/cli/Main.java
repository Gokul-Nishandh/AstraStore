/**
 * Main entry point for the AstraStore CLI tool.
 * Uses Picocli to parse commands and delegate to subcommand handlers.
 * Supports ANSI colors, error handling, and --help output.
 */
package com.astrastore.cli;

import picocli.CommandLine;

@CommandLine.Command(
        name = "astra",
        mixinStandardHelpOptions = true,
        version = "astra 1.0.0",
        description = "AstraStore command line tool for managing buckets, files, and clusters.",
        subcommands = {
                com.astrastore.cli.commands.AuthCommand.class,
                com.astrastore.cli.commands.UploadCommand.class,
                com.astrastore.cli.commands.DownloadCommand.class,
                com.astrastore.cli.commands.ListObjectsCommand.class,
                com.astrastore.cli.commands.DeleteObjectCommand.class,
                com.astrastore.cli.commands.MakeBucketCommand.class,
                com.astrastore.cli.commands.RemoveBucketCommand.class,
                com.astrastore.cli.commands.ListBucketsCommand.class,
                com.astrastore.cli.commands.ClusterCommand.class,
                com.astrastore.cli.commands.ShellCommand.class
        }
)
public class Main implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main())
                .setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
                    System.err.println("Error: " + ex.getMessage());
                    return CommandLine.ExitCode.SOFTWARE;
                })
                .execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }
}
