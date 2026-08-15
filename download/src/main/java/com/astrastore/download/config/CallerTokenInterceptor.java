package com.astrastore.download.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

/**
 * Carries the calling user's bearer token onto metadata's public API.
 *
 * <p>Object lookup deliberately goes through {@code /api/v1/**} rather than
 * the internal surface, because that is where ownership is enforced. Download
 * holds no ownership rules of its own and must not: duplicating them here
 * would mean two places to keep in step, and the copy that drifts is the one
 * that leaks somebody else's file.
 *
 * <p>The consequence is that a request without a usable token fails at
 * metadata rather than silently returning another account's object, which is
 * the correct direction to fail in.
 */
public class CallerTokenInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(org.springframework.http.HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {

        if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            String authorization = inboundAuthorization();
            if (authorization != null) {
                request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
        return execution.execute(request, body);
    }

    /**
     * The inbound request is reachable only from the thread serving it. A
     * download streams on that same thread, so the lookup happens before any
     * hand-off — but the null check stands because a scheduled or async caller
     * would legitimately have no request bound.
     */
    private static String inboundAuthorization() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        return servletAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
    }
}
