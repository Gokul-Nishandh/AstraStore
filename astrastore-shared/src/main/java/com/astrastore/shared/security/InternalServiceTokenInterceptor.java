package com.astrastore.shared.security;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Presents the shared service token on outbound {@code /internal/**} calls.
 *
 * <p>It sits on the {@code RestTemplate} rather than at each call site so that
 * a new internal call cannot be written without it — the failure mode that
 * matters here is a caller that quietly stops authenticating.
 *
 * <p>Requests to any other path are left untouched. Those carry an end-user
 * identity of their own, and a credential that is not needed is a credential
 * that should not travel.
 */
public final class InternalServiceTokenInterceptor implements ClientHttpRequestInterceptor {

    private static final String INTERNAL_PREFIX = "/internal/";

    private final InternalServiceToken token;

    public InternalServiceTokenInterceptor(InternalServiceToken token) {
        this.token = token;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        if (token.isConfigured() && isInternal(request)) {
            request.getHeaders().set(AstraHeaders.SERVICE_TOKEN, token.value());
        }
        return execution.execute(request, body);
    }

    private static boolean isInternal(HttpRequest request) {
        String path = request.getURI().getPath();
        return path != null && path.startsWith(INTERNAL_PREFIX);
    }
}
