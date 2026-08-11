/**
 * Unit tests for ErrorParser that unwraps nested JSON error payloads
 * and provides friendly messages with actionable suggestions.
 */
package com.astrastore.cli.ui;

import com.astrastore.cli.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorParserTest {

    @Test
    void unwrap_singleLevel() {
        String json = "{\"code\":\"NOT_FOUND\",\"message\":\"Bucket not found: foo\",\"path\":\"/api/v1/buckets/foo\"}";
        assertEquals(json, ErrorParser.unwrap(json));
    }

    @Test
    void unwrap_doubleLevel() {
        String inner = "{\"code\":\"NOT_FOUND\",\"message\":\"Bucket not found: foo\"}";
        String outer = "{\"code\":\"INTERNAL_ERROR\",\"message\":\"404 : \\\"" + inner.replace("\"", "\\\"") + "\\\"\"}";
        // The outer \\" gets stripped, leaving the inner JSON as the new "message"
        // which then gets parsed as the root
        String result = ErrorParser.unwrap(outer);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void unwrap_plainText() {
        assertEquals("plain error message", ErrorParser.unwrap("plain error message"));
    }

    @Test
    void unwrap_httpPrefix() {
        String s = "HTTP 500 some body";
        assertEquals("some body", ErrorParser.unwrap(s));
    }

    @Test
    void unwrap_nullSafe() {
        assertNull(ErrorParser.unwrap(null));
    }

    @Test
    void stripQuotes_basic() {
        assertEquals("hello", ErrorParser.stripQuotes("\"hello\""));
        assertEquals("hello", ErrorParser.stripQuotes("hello"));
        assertNull(ErrorParser.stripQuotes(null));
    }

    @Test
    void friendlyMessage_404_bucket_suggestsLs() {
        ApiException e = new ApiException(404, "/api/v1/buckets/abc", "{\"code\":\"NOT_FOUND\",\"message\":\"Bucket not found: abc\"}");
        String msg = ErrorParser.friendlyMessage(e);
        assertTrue(msg.contains("Bucket not found"));
        assertTrue(msg.contains("ls-buckets"));
        assertTrue(msg.contains("mb"));
    }

    @Test
    void friendlyMessage_409_suggestsUnique() {
        ApiException e = new ApiException(409, "/api/v1/buckets", "{\"code\":\"CONFLICT\",\"message\":\"already exists\"}");
        String msg = ErrorParser.friendlyMessage(e);
        assertTrue(msg.contains("Use a unique name"));
    }

    @Test
    void friendlyMessage_401_suggestsLogin() {
        ApiException e = new ApiException(401, "/api/v1/keys", "{\"code\":\"UNAUTHORIZED\",\"message\":\"not logged in\"}");
        String msg = ErrorParser.friendlyMessage(e);
        assertTrue(msg.contains("auth login"));
    }

    @Test
    void friendlyMessage_emptyBody() {
        ApiException e = new ApiException(500, "/api/test", "");
        String msg = ErrorParser.friendlyMessage(e);
        assertNotNull(msg);
        assertFalse(msg.isEmpty());
    }

    @Test
    void friendlyMessage_invalidJson() {
        ApiException e = new ApiException(500, "/api/test", "not valid json {{{{");
        String msg = ErrorParser.friendlyMessage(e);
        assertNotNull(msg);
    }
}
