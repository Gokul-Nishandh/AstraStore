/**
 * Renders filter-chain rejections as {@link ApiError}.
 *
 * <p>{@link com.astrastore.auth.exception.GlobalExceptionHandler} only sees
 * failures raised once a request has reached a controller. A request refused
 * by the security filter chain — no token, or a token without ADMIN on an
 * {@code /api/auth/admin/**} path — never gets that far, and Spring's default
 * handlers answer with an empty body or the servlet container's HTML error
 * page. That is the one hole through which a caller can receive something
 * that is not the shared error envelope, so it is closed here.
 *
 * <p>Both messages are deliberately generic: the difference between "this
 * resource does not exist" and "it exists and you may not see it" is not
 * something an unauthorised caller gets to learn.
 */
package com.astrastore.auth.config;

import com.astrastore.shared.api.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiErrorSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, ApiError.UNAUTHENTICATED,
                "Authentication is required to access this resource");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, ApiError.FORBIDDEN,
                "You do not have permission to perform this action");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, String code, String message) throws IOException {
        String requestId = requestId(request);
        log.warn("[{}] Request refused by the security filter chain — path={}, status={}",
                requestId, request.getRequestURI(), status.value());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(code, message, requestId));
    }

    /** Mirrors GlobalExceptionHandler so a client sees one correlation scheme. */
    private static String requestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        if (header != null && !header.isBlank()) {
            return header.length() > 64 ? header.substring(0, 64) : header;
        }
        return UUID.randomUUID().toString();
    }
}
