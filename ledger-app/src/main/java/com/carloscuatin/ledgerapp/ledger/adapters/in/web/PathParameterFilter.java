package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Refuses a path with parameters ahead of everything that reads paths. The server does not decode a path parameter, so a
 * broken escape in one gets past it and fails in the first thing that does, which runs ahead of the firewall of the
 * security chain that refuses every path parameter anyway. The answer is the same one the firewall gives, and it goes
 * through the same error page.
 */
final class PathParameterFilter implements Filter {

    private static final char PATH_PARAMETER = ';';

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (((HttpServletRequest) request).getRequestURI().indexOf(PATH_PARAMETER) >= 0) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        chain.doFilter(request, response);
    }
}
