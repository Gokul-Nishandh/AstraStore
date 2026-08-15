/**
 * A bare acknowledgement. Kept as a record so the wire shape
 * {@code {"message": "..."}} is fixed rather than assembled ad hoc from a
 * {@code Map.of} at each call site.
 */
package com.astrastore.auth.dto;

public record MessageResponse(String message) {
}
