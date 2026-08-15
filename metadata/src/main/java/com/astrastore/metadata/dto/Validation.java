package com.astrastore.metadata.dto;

/**
 * Input constraints shared by every request DTO.
 *
 * <p>Object keys become paths on storage nodes and segments in URLs, so the
 * pattern below rejects the shapes that turn a key into a traversal: a leading
 * slash, an empty path segment, and any {@code ..} segment. Control characters
 * and backslashes are rejected outright — they have no legitimate use in a key
 * and are how a log line or a Windows path gets rewritten.
 */
public final class Validation {

    private Validation() {}

    public static final int KEY_MAX = 1024;
    public static final int BUCKET_NAME_MAX = 63;

    /**
     * Four negative lookaheads followed by the body:
     * <ol>
     *   <li>no leading slash</li>
     *   <li>no empty path segment (a doubled separator)</li>
     *   <li>no {@code ..} segment anywhere</li>
     *   <li>no trailing slash</li>
     * </ol>
     * The body then excludes control characters and backslashes.
     */
    public static final String KEY_PATTERN =
            "^(?!/)(?!.*//)(?!.*(?:^|/)\\.\\.(?:/|$))(?!.*/$)[^\\p{Cntrl}\\\\]+$";

    public static final String KEY_MESSAGE =
            "must not be empty, start with '/', end with '/', contain '..', "
            + "backslashes or control characters";

    /**
     * Bucket names appear in URLs and in the {@code (owner_id, name)} unique
     * key. Letters, digits, dot, dash and underscore only, starting with an
     * alphanumeric — no slashes, no dots-only names, nothing that needs
     * escaping.
     */
    public static final String BUCKET_NAME_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._-]*$";

    public static final String BUCKET_NAME_MESSAGE =
            "must start with a letter or digit and contain only letters, digits, "
            + "dots, dashes and underscores";
}
