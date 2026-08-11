/**
 * Intercepts raw nested JSON exception payloads from the gateway/backend
 * and presents clean, color-coded human messages with actionable hints.
 *
 * AstraStore returns errors like:
 *   HTTP 500 : "{\"code\":\"INTERNAL_ERROR\",\"message\":\"404 : \\\"{\\\"code\\\":\\\"NOT_FOUND\\\",\\\"message\\\":\\\"Bucket not found: ...\\\"}\\\"\",\"traceId\":\"...\"}"
 *
 * This class unwraps the nested JSON, extracts the deepest error code and message,
 * and provides contextual suggestions for common errors.
 */
package com.astrastore.cli.ui;

import com.astrastore.cli.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ErrorParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ErrorParser() {
    }

    public static String friendlyMessage(ApiException e) {
        String body = unwrap(e.getResponseBody());
        if (body == null || body.isEmpty()) {
            body = unwrap(e.getMessage());
        }
        if (body == null || body.isEmpty()) {
            return formatError("Unknown error", "HTTP " + e.getStatusCode());
        }

        try {
            JsonNode root = MAPPER.readTree(body);
            String code = textOrNull(root, "code");
            String msg = textOrNull(root, "message");
            String path = textOrNull(root, "path");
            if (path == null) path = e.getPath();

            if (code == null && msg == null) {
                return formatError("Error", body);
            }

            String cleanMsg = stripQuotes(msg != null ? msg : code);
            String suggestion = suggest(code, path, cleanMsg);

            StringBuilder out = new StringBuilder();
            out.append(ColorSupport.error(cleanMsg));
            if (suggestion != null) {
                out.append("\n\n");
                out.append(ColorSupport.info("Suggestion: ")).append(suggestion);
            }
            return out.toString();
        } catch (Exception parseErr) {
            return formatError("Error", stripQuotes(body));
        }
    }

    /**
     * Unwrap nested JSON bodies like:
     *   "404 : \"{\\\"code\\\":\\\"NOT_FOUND\\\", ...}\""
     * Recursively unwrap until we reach the deepest JSON object.
     */
    static String unwrap(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.startsWith("HTTP ")) {
            int sp = s.indexOf(' ', 5);
            if (sp > 0) s = s.substring(sp + 1).trim();
        }
        for (int i = 0; i < 5; i++) {
            if (!s.startsWith("{")) return s;
            try {
                JsonNode node = MAPPER.readTree(s);
                if (node.has("message")) {
                    String inner = node.get("message").asText();
                    if (inner != null && !inner.isEmpty() && inner.trim().startsWith("{")) {
                        s = inner;
                        continue;
                    }
                }
                return s;
            } catch (Exception ignored) {
                return stripQuotes(s);
            }
        }
        return stripQuotes(s);
    }

    static String stripQuotes(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String suggest(String code, String path, String msg) {
        if (code == null) code = "";
        String m = msg == null ? "" : msg.toLowerCase();
        String p = path == null ? "" : path.toLowerCase();

        if (code.equals("NOT_FOUND") || m.contains("not found")) {
            if (p.contains("/buckets") && !p.contains("/objects")) {
                return "Run 'astra ls-buckets' to view available buckets, or 'astra mb -n <name>' to create one.";
            }
            if (p.contains("/objects")) {
                return "Run 'astra ls <bucket-id>' to list objects in the bucket, or check the object ID with 'astra ls <bucket-name>'.";
            }
            if (p.contains("/keys")) {
                return "Run 'astra auth list-keys' to view your active API keys.";
            }
            if (m.contains("bucket")) {
                return "Run 'astra ls-buckets' to see existing buckets or 'astra mb' to create a new one.";
            }
            if (m.contains("object")) {
                return "Run 'astra ls <bucket-id>' to list objects or check the object ID.";
            }
        }

        if (code.equals("CONFLICT") || m.contains("already")) {
            return "Use a unique name or delete the existing resource first.";
        }

        if (code.equals("VALIDATION_ERROR") || m.contains("must be a valid uuid")) {
            return "Provide a valid UUID (e.g., '550e8400-e29b-41d4-a716-446655440000') or use the bucket name.";
        }

        if (code.equals("UNAUTHORIZED") || code.equals("FORBIDDEN")) {
            return "Run 'astra auth login' to re-authenticate, or 'astra auth status' to check your session.";
        }

        if (m.contains("not logged in")) {
            return "Run 'astra auth login -u <email> --password=<password>' to authenticate first.";
        }

        if (code.equals("BAD_GATEWAY") || m.contains("unavailable")) {
            return "A dependent service is unavailable. Wait a moment and retry, or check service health at 'astra cluster health'.";
        }

        if (code.equals("UNPROCESSABLE_ENTITY") || m.contains("checksum")) {
            return "The transferred data failed integrity check. Try re-uploading the file.";
        }

        return null;
    }

    private static String formatError(String title, String detail) {
        return ColorSupport.error(title + (detail != null && !detail.isBlank() ? ": " + detail : ""));
    }
}
