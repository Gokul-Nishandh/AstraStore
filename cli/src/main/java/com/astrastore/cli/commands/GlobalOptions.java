/**
 * Global command options available to every subcommand via mixin.
 * Provides --quiet (-q) and --format flags for consistent output control.
 */
package com.astrastore.cli.commands;

import picocli.CommandLine;

public class GlobalOptions {

    @CommandLine.Option(names = {"-q", "--quiet"},
            description = "Suppress formatting; print only raw IDs/values (for scripting).",
            scope = CommandLine.ScopeType.INHERIT)
    public boolean quiet = false;

    @CommandLine.Option(names = {"--format"},
            description = "Output format: ${COMPLETION-CANDIDATES}. Default: table.",
            scope = CommandLine.ScopeType.INHERIT)
    public String format = "table";

    @CommandLine.Option(names = {"--no-interactive"},
            description = "Disable interactive prompts (for CI/scripts).",
            scope = CommandLine.ScopeType.INHERIT)
    public boolean noInteractive = false;

    public boolean isQuiet() {
        return quiet;
    }

    public String getFormat() {
        return format == null ? "table" : format;
    }

    public boolean isInteractiveAllowed() {
        return !noInteractive && System.console() != null;
    }
}
