package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * What a request without an authenticated principal receives. It answers from inside the filter chain, so it writes the
 * response itself: no controller ran, and Spring MVC's exception handling is not involved. The text is constant, because
 * the reason a credential failed is useful to whoever is trying to forge one.
 */
final class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String AUTHENTICATION_SCHEME = "DPoP";

    private final JsonMapper json;

    ProblemAuthenticationEntryPoint(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException refusal) throws IOException {
        var type = ProblemType.UNAUTHENTICATED;
        response.setStatus(type.status().value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, AUTHENTICATION_SCHEME);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Bytes, not the response's writer: JSON is UTF-8, and a writer with no charset declared would encode ISO-8859-1.
        json.writeValue(response.getOutputStream(), type.problemAt(request.getRequestURI()));
    }
}
