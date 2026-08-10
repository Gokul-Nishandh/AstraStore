/**
 * Parent command for auth operations (login, logout, status, key management).
 * Delegates to nested subcommands for specific operations.
 */
package com.astrastore.cli.commands;

import picocli.CommandLine;

@CommandLine.Command(
        name = "auth",
        mixinStandardHelpOptions = true,
        description = "Authentication and API key management.",
        subcommands = {
                AuthLoginCommand.class,
                AuthLogoutCommand.class,
                AuthStatusCommand.class,
                AuthCreateKeyCommand.class,
                AuthListKeysCommand.class,
                AuthRevokeKeyCommand.class
        }
)
public class AuthCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }
}
