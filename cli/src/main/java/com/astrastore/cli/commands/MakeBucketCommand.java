/**
 * Make-bucket command (mb) creates a new bucket.
 * Calls POST /api/v1/buckets endpoint on Metadata service.
 * Accepts --name flag for bucket name (required).
 * Returns the new bucket UUID and creation timestamp.
 */
package com.astrastore.cli.commands;

import com.astrastore.cli.auth.CredentialStore;
import com.astrastore.cli.config.AstraConfig;
import com.astrastore.cli.http.AstraHttpClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "mb",
        mixinStandardHelpOptions = true,
        description = "Create a new bucket."
)
public class MakeBucketCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-n", "--name"}, description = "Bucket name", required = true)
    private String name;

    @CommandLine.Option(names = {"--owner-id"}, description = "Owner UUID (optional, defaults to system)")
    private String ownerId;

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

        if (name.length() > 63) {
            System.err.println("Error: bucket name must be 63 characters or less");
            return 1;
        }

        try {
            AstraConfig config = AstraConfig.load();
            AstraHttpClient client = new AstraHttpClient(config.getGatewayUrl());
            BucketRequest request = new BucketRequest(name, ownerId);
            BucketResponse response = client.post("/api/v1/buckets", request, new TypeReference<>() {});

            System.out.println("✓ Bucket created");
            System.out.println("  Name:      " + response.name);
            System.out.println("  ID:        " + response.id);
            System.out.println("  Owner:     " + response.ownerId);
            System.out.println("  Created:   " + response.createdAt);
            return 0;
        } catch (com.astrastore.cli.exception.ApiException e) {
            System.err.println(com.astrastore.cli.ui.ErrorParser.friendlyMessage(e));
            return 1;
        } catch (Exception e) {
            ErrorHandler.printError(e);
            return 1;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BucketRequest {
        private String name;
        private String ownerId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BucketResponse {
        private String id;
        private String name;
        private String ownerId;
        private String createdAt;
    }
}
