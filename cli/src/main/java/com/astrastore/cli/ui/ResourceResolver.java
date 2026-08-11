/**
 * Resolves human-friendly resource references to internal UUIDs.
 * Supports bucket names ("my-bucket"), object paths ("my-bucket/file.txt"),
 * and the s3:// URI scheme ("s3://my-bucket/file.txt"). Falls back to UUID
 * parsing if the input is a valid UUID.
 */
package com.astrastore.cli.ui;

import com.astrastore.cli.config.AstraConfig;
import com.astrastore.cli.exception.ApiException;
import com.astrastore.cli.http.AstraHttpClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ResourceResolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResourceResolver() {
    }

    /**
     * Resolve a bucket reference (name or UUID) to a Bucket object.
     * Tries: (1) UUID parse, (2) GET /api/v1/buckets/{uuid}, (3) GET /api/v1/buckets/by-name/{name}.
     */
    public static ResolvedBucket resolveBucket(String ref, AstraHttpClient client) {
        if (ref == null || ref.isBlank()) return null;

        try {
            UUID uuid = UUID.fromString(ref);
            Map<String, Object> data = client.get("/api/v1/buckets/" + uuid,
                    new TypeReference<Map<String, Object>>() {});
            return new ResolvedBucket((String) data.get("id"), (String) data.get("name"), uuid);
        } catch (IllegalArgumentException ignored) {
        } catch (Exception e) {
        }

        try {
            Map<String, Object> data = client.get("/api/v1/buckets/by-name/" + ref,
                    new TypeReference<Map<String, Object>>() {});
            if (data != null) {
                String id = (String) data.get("id");
                return new ResolvedBucket(id, (String) data.get("name"), UUID.fromString(id));
            }
        } catch (ApiException e) {
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * Resolve an object reference like "bucket-name/file.txt" or UUID to a ResolvedObject.
     * For bucket/key paths, looks up the actual object UUID via the objects list API.
     * The bucket part can be a name; the key part is literal text.
     */
    public static ResolvedObject resolveObject(String ref, AstraHttpClient client) {
        if (ref == null || ref.isBlank()) return null;
        if (ref.startsWith("s3://")) ref = ref.substring(5);
        int slash = ref.indexOf('/');
        if (slash <= 0) {
            return resolveObjectByUuid(ref, client);
        }
        String bucketRef = ref.substring(0, slash);
        String key = ref.substring(slash + 1);
        ResolvedBucket bucket = resolveBucket(bucketRef, client);
        if (bucket == null) return null;
        // Look up the real object UUID by listing objects and matching key
        return resolveObjectByBucketAndKey(bucket, key, client);
    }

    private static ResolvedObject resolveObjectByBucketAndKey(
            ResolvedBucket bucket, String key, AstraHttpClient client) {
        try {
            Map<String, Object> page = client.get(
                    "/api/v1/buckets/" + bucket.uuid() + "/objects?size=500",
                    new TypeReference<Map<String, Object>>() {});
            if (page == null || !page.containsKey("content")) return null;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
            for (Map<String, Object> obj : content) {
                if (key.equals(obj.get("key"))) {
                    String objectId = (String) obj.get("id");
                    return new ResolvedObject(
                            objectId,
                            bucket.uuid(),
                            bucket.id(),
                            key,
                            bucket.name() + "/" + key);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static ResolvedObject resolveObjectByUuid(String uuidStr, AstraHttpClient client) {
        try {
            Map<String, Object> data = client.get("/api/v1/objects/" + uuidStr,
                    new TypeReference<Map<String, Object>>() {});
            if (data == null) return null;
            String objectId = (String) data.get("id");
            String bucketId = (String) data.get("bucketId");
            return new ResolvedObject(
                    objectId != null ? objectId : uuidStr,
                    UUID.fromString(bucketId),
                    bucketId,
                    (String) data.get("key"),
                    (String) data.get("key"));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * List all buckets for interactive pickers.
     * The API returns a paginated wrapper {content:[...], totalElements:N} — not a flat list.
     */
    public static List<ResolvedBucket> listAllBuckets(AstraHttpClient client) {
        try {
            Map<String, Object> page = client.get("/api/v1/buckets?size=200",
                    new TypeReference<Map<String, Object>>() {});
            if (page == null || !page.containsKey("content")) return List.of();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) page.get("content");
            if (data == null) return List.of();
            return data.stream()
                    .map(d -> new ResolvedBucket(
                            (String) d.get("id"),
                            (String) d.get("name"),
                            UUID.fromString((String) d.get("id"))))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * List all objects in a bucket for interactive pickers.
     */
    public static List<ResolvedObject> listObjectsInBucket(UUID bucketId, AstraHttpClient client) {
        try {
            Map<String, Object> page = client.get(
                    "/api/v1/buckets/" + bucketId + "/objects?size=200",
                    new TypeReference<Map<String, Object>>() {});
            if (page == null || !page.containsKey("content")) return List.of();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) page.get("content");
            return data.stream()
                    .map(d -> new ResolvedObject(
                            (String) d.get("id"),
                            bucketId,
                            bucketId.toString(),
                            (String) d.get("key"),
                            (String) d.get("key")))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public record ResolvedBucket(String id, String name, UUID uuid) {
    }

    /**
     * Resolved object reference.
     * objectId  — the object's own UUID (use this for DELETE /api/v1/objects/{objectId})
     * bucketUuid — the bucket UUID
     * bucketId  — the bucket UUID as String
     * key       — the object key within the bucket
     * displayName — human-readable reference (e.g. "bucket-name/key")
     */
    public record ResolvedObject(String objectId, UUID bucketUuid, String bucketId, String key, String displayName) {
    }
}
