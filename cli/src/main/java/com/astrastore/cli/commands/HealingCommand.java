/**
 * Parent command for self-healing operations (status, run, chaos).
 * Full implementation in Phase 5.
 */
package com.astrastore.cli.commands;

import picocli.CommandLine;

@CommandLine.Command(
        name = "healing",
        mixinStandardHelpOptions = true,
        description = "Inspect and trigger self-healing operations.",
        subcommands = {
                HealingStatusCommand.class,
                HealingRunCommand.class
        }
)
public class HealingCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }
}
