/**
 * Delete-object command (rm) removes an object from a bucket.
 * Supports path-based references: bucket-name/file.txt, s3://name/file.txt, or UUID.
 * If no argument is provided, presents an interactive picker.
 * Calls DELETE /api/v1/objects/{objectId} endpoint (or by-name flow).
 * Requires --yes flag to skip confirmation prompt for scripted use.
 */
package com.astrastore.cli.commands;

import com.astrastore.cli.auth.CredentialStore;
import com.astrastore.cli.config.AstraConfig;
import com.astrastore.cli.exception.ApiException;
import com.astrastore.cli.http.AstraHttpClient;
import com.astrastore.cli.ui.ConsolePrompter;
import com.astrastore.cli.ui.ErrorParser;
import com.astrastore.cli.ui.ResourceResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import picocli.CommandLine;

import java.util.List;


import java.util.stream.Collectors;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "rm",
        mixinStandardHelpOptions = true,
        description = "Delete an object from a bucket."
)
public class DeleteObjectCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", arity = "0..1",
            description = "Object ID, bucket-name/file.txt, or s3://name/key (omit for picker)")
    private String objectRef;

    @CommandLine.Option(names = {"-y", "--yes"}, description = "Skip confirmation prompt")
    private boolean skipConfirm;

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

        String resolvedObjectId = null;
        String resolvedKey = null;

        if (objectRef == null || objectRef.isBlank()) {
            if (!ConsolePrompter.isInteractive()) {
                System.err.println("Error: object reference required (UUID, bucket-name/file.txt, or s3://name/key).");
                return 1;
            }
            List<ResourceResolver.ResolvedBucket> buckets = ResourceResolver.listAllBuckets(client);
            if (buckets.isEmpty()) {
                System.err.println("No buckets found. Create one with 'astra mb -n <name>'.");
                return 1;
            }
            List<String> bucketLabels = buckets.stream()
                    .map(b -> String.format("%-30s (ID: %s)", b.name(), b.id().substring(0, 8) + "..."))
                    .collect(Collectors.toList());
            int bIdx = ConsolePrompter.selectSingle("Select a bucket (Use arrow keys):", bucketLabels);
            if (bIdx < 0) {
                System.out.println("Cancelled.");
                return 0;
            }
            ResourceResolver.ResolvedBucket selectedBucket = buckets.get(bIdx);
            List<ResourceResolver.ResolvedObject> objects = ResourceResolver.listObjectsInBucket(selectedBucket.uuid(), client);
            if (objects.isEmpty()) {
                System.err.println("No objects in bucket " + selectedBucket.name() + ".");
                return 1;
            }
            List<String> objLabels = objects.stream()
                    .map(o -> o.key() + "  (ID: " + o.objectId() + ")")
                    .collect(Collectors.toList());
            int oIdx = ConsolePrompter.selectSingle("Select an object to delete (Use arrow keys):", objLabels);
            if (oIdx < 0) {
                System.out.println("Cancelled.");
                return 0;
            }
            resolvedObjectId = objects.get(oIdx).objectId();
            resolvedKey = objects.get(oIdx).key();
        } else {
            // If objectRef is already a UUID, use it directly.
            // NOTE: GET /api/v1/objects/{uuid} via the gateway routes to the download service
            // (binary stream response), not the metadata service, so we cannot use it
            // to resolve metadata. The DELETE endpoint goes to metadata and is correct.
            if (isUuid(objectRef)) {
                resolvedObjectId = objectRef;
                resolvedKey = objectRef; // Use UUID as display key; no metadata lookup needed
            } else {
                ResourceResolver.ResolvedObject obj = ResourceResolver.resolveObject(objectRef, client);
                if (obj == null) {
                    System.err.println(ErrorParser.friendlyMessage(
                            new ApiException(404, "/api/v1/objects/" + objectRef, "Object not found: " + objectRef)));
                    return 1;
                }
                resolvedObjectId = obj.objectId();
                resolvedKey = obj.key();
            }
        }

        if (!skipConfirm) {
            String prompt = "Delete object " + resolvedKey + "? This cannot be undone. [y/N] ";
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
            client.delete("/api/v1/objects/" + resolvedObjectId);
            System.out.println("✓ Object " + resolvedKey + " deleted.");
            return 0;
        } catch (ApiException e) {
            System.err.println(ErrorParser.friendlyMessage(e));
            return 1;
        } catch (Exception e) {
            ErrorHandler.printError(e);
            return 1;
        }
    }

    /** Returns true if the given string is a valid UUID. */
    private static boolean isUuid(String s) {
        if (s == null || s.length() != 36) return false;
        try {
            java.util.UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
