/**
 * Parent command for cluster operations (health, nodes, healing).
 * Groups related cluster inspection and management commands.
 */
package com.astrastore.cli.commands;

import picocli.CommandLine;

@CommandLine.Command(
        name = "cluster",
        mixinStandardHelpOptions = true,
        description = "Cluster inspection and management.",
        subcommands = {
                ClusterHealthCommand.class,
                ClusterNodesCommand.class,
                HealingCommand.class
        }
)
public class ClusterCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }
}
