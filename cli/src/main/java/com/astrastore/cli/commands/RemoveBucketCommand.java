/**
 * Remove-bucket command (rb) deletes a bucket by ID or name.
 * If no bucket argument is provided, presents an interactive picker.
 * Supports path-based references: bucket-name or UUID.
 * Calls DELETE /api/v1/buckets/{bucketId} endpoint.
 * Cannot delete bucket that still contains objects (returns 409).
 */
package com.astrastore.cli.commands;

import com.astrastore.cli.auth.CredentialStore;
import com.astrastore.cli.config.AstraConfig;
import com.astrastore.cli.http.AstraHttpClient;
import com.astrastore.cli.ui.ConsolePrompter;
import com.astrastore.cli.ui.ErrorParser;
import com.astrastore.cli.ui.ResourceResolver;
import picocli.CommandLine;

import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "rb",
        mixinStandardHelpOptions = true,
        description = "Remove a bucket."
)
public class RemoveBucketCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", arity = "0..1",
            description = "Bucket ID, bucket name, or s3://name (omit for interactive picker)")
    private String bucketRef;

    @CommandLine.Option(names = {"-y", "--yes"}, description = "Skip confirmation prompt")
    private boolean skipConfirm;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        CredentialStore.Credentials creds;
        try {
            creds = CredentialStore.getInstance().load();
        } catch (Exception e) {
            System.err.println("Error loading credentials: " + e.getMessage());
            return 1;
        }
        if (creds == null || creds.getAccessToken() == null) {
            System.err.println("Not logged in. Run 'astra auth login' first.");
            return 1;
        }

        AstraConfig config = AstraConfig.load();
        AstraHttpClient client = new AstraHttpClient(config.getGatewayUrl());

        String resolvedId = null;
        String resolvedName = null;

        if (bucketRef == null || bucketRef.isBlank()) {
            if (!ConsolePrompter.isInteractive()) {
                System.err.println("Error: bucket reference required (UUID, name, or s3://name).");
                System.err.println("Run 'astra ls-buckets' to find bucket IDs.");
                return 1;
            }
            List<ResourceResolver.ResolvedBucket> buckets = ResourceResolver.listAllBuckets(client);
            if (buckets.isEmpty()) {
                System.err.println("No buckets to remove. Create one with 'astra mb -n <name>'.");
                return 1;
            }
            List<String> labels = buckets.stream()
                    .map(b -> String.format("%-30s (ID: %s, Created: %s)",
                            b.name(), b.id().substring(0, 8) + "...", "n/a"))
                    .collect(Collectors.toList());
            int idx = ConsolePrompter.selectSingle("Select a bucket to remove (Use arrow keys):", labels);
            if (idx < 0) {
                System.out.println("Cancelled.");
                return 0;
            }
            resolvedId = buckets.get(idx).id();
            resolvedName = buckets.get(idx).name();
        } else {
            ResourceResolver.ResolvedBucket b = ResourceResolver.resolveBucket(bucketRef, client);
            if (b == null) {
                System.err.println(ErrorParser.friendlyMessage(
                        new com.astrastore.cli.exception.ApiException(404, "/api/v1/buckets/" + bucketRef, "Bucket not found: " + bucketRef)));
                return 1;
            }
            resolvedId = b.id();
            resolvedName = b.name();
        }

        if (!skipConfirm) {
            String prompt = "Remove bucket " + resolvedName + " (" + resolvedId.substring(0, 8) + "...)? This cannot be undone. [y/N] ";
            System.out.print(prompt);
            String response = System.console() != null
                    ? System.console().readLine()
                    : new java.util.Scanner(System.in).nextLine();
            if (response == null || !response.trim().equalsIgnoreCase("y")) {
                System.out.println("Cancelled.");
                return 0;
            }
        }

        try {
            client.delete("/api/v1/buckets/" + resolvedId);
            System.out.println("✓ Bucket " + resolvedName + " removed.");
            return 0;
        } catch (com.astrastore.cli.exception.ApiException e) {
            System.err.println(ErrorParser.friendlyMessage(e));
            return 1;
        } catch (Exception e) {
            ErrorHandler.printError(e);
            return 1;
        }
    }
}
