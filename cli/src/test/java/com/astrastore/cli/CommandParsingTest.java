/**
 * Unit test for Picocli command parsing without network access.
 * Verifies that all subcommands are registered correctly under Main
 * and that help/usage output contains expected command names.
 */
package com.astrastore.cli;

import com.astrastore.cli.commands.AuthCommand;
import com.astrastore.cli.commands.AuthCreateKeyCommand;
import com.astrastore.cli.commands.AuthListKeysCommand;
import com.astrastore.cli.commands.AuthLoginCommand;
import com.astrastore.cli.commands.AuthLogoutCommand;
import com.astrastore.cli.commands.AuthRevokeKeyCommand;
import com.astrastore.cli.commands.AuthStatusCommand;
import com.astrastore.cli.commands.ClusterCommand;
import com.astrastore.cli.commands.ClusterHealthCommand;
import com.astrastore.cli.commands.ClusterNodesCommand;
import com.astrastore.cli.commands.DeleteObjectCommand;
import com.astrastore.cli.commands.DownloadCommand;
import com.astrastore.cli.commands.HealingCommand;
import com.astrastore.cli.commands.HealingRunCommand;
import com.astrastore.cli.commands.HealingStatusCommand;
import com.astrastore.cli.commands.ListBucketsCommand;
import com.astrastore.cli.commands.ListObjectsCommand;
import com.astrastore.cli.commands.MakeBucketCommand;
import com.astrastore.cli.commands.RemoveBucketCommand;
import com.astrastore.cli.commands.ShellCommand;
import com.astrastore.cli.commands.UploadCommand;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CommandParsingTest {

    private final CommandLine cmd = new CommandLine(new Main());

    @Test
    void all_subcommands_registered() {
        Set<String> expected = new HashSet<>(Arrays.asList(
                "auth", "upload", "download", "ls", "rm", "mb", "rb", "ls-buckets",
                "cluster", "shell"
        ));
        Set<String> actual = new HashSet<>();
        for (CommandLine sub : cmd.getSubcommands().values()) {
            actual.add(sub.getCommandName());
        }
        for (String name : expected) {
            assertTrue(actual.contains(name), "Missing subcommand: " + name);
        }
    }

    @Test
    void auth_has_all_subcommands() {
        CommandLine auth = cmd.getSubcommands().get("auth");
        assertNotNull(auth);
        Set<String> expected = new HashSet<>(Arrays.asList(
                "login", "logout", "status", "create-key", "list-keys", "revoke-key"
        ));
        Set<String> actual = new HashSet<>();
        for (CommandLine sub : auth.getSubcommands().values()) {
            actual.add(sub.getCommandName());
        }
        for (String name : expected) {
            assertTrue(actual.contains(name), "Missing auth subcommand: " + name);
        }
    }

    @Test
    void cluster_has_all_subcommands() {
        CommandLine cluster = cmd.getSubcommands().get("cluster");
        assertNotNull(cluster);
        Set<String> expected = new HashSet<>(Arrays.asList(
                "health", "nodes", "healing"
        ));
        Set<String> actual = new HashSet<>();
        for (CommandLine sub : cluster.getSubcommands().values()) {
            actual.add(sub.getCommandName());
        }
        for (String name : expected) {
            assertTrue(actual.contains(name), "Missing cluster subcommand: " + name);
        }
    }

    @Test
    void cluster_healing_has_subcommands() {
        CommandLine cluster = cmd.getSubcommands().get("cluster");
        CommandLine healing = cluster.getSubcommands().get("healing");
        assertNotNull(healing);
        Set<String> actual = new HashSet<>();
        for (CommandLine sub : healing.getSubcommands().values()) {
            actual.add(sub.getCommandName());
        }
        assertTrue(actual.contains("status"));
        assertTrue(actual.contains("run"));
    }

    @Test
    void help_displays_all_commands() {
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));
        cmd.usage(cmd.getOut());
        String help = sw.toString();

        for (String cmdName : Arrays.asList("auth", "upload", "download", "ls", "rm",
                "mb", "rb", "cluster", "shell")) {
            assertTrue(help.contains(cmdName), "Help should mention: " + cmdName);
        }
    }

    @Test
    void invalid_command_returns_error() {
        int exitCode = cmd.execute("nonexistent-command");
        assertNotEquals(0, exitCode);
    }
}
