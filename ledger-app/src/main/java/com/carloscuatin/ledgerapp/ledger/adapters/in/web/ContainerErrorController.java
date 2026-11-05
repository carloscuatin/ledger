package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import java.io.IOException;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Where the server sends a request that failed outside any handler: a URL the firewall rejects, a method the server
 * refuses, an exception nothing caught. Replacing the error controller of Spring Boot keeps those inside the catalog. The
 * contract does not document this route because no client calls it: only the server's forward carries a status, and
 * asked directly it does not exist.
 * <p>
 * The filter that adds the security headers does not run for a request forwarded here, so this sets them itself.
 */
@Controller
@Profile("api")
class ContainerErrorController implements ErrorController {

    /** The path Spring Boot registers on the server for its error page, which this class takes over. */
    private static final String ERROR_PAGE = "/error";

    private final JsonMapper json;

    ContainerErrorController(JsonMapper json) {
        this.json = json;
    }

    /**
     * Writes the response instead of returning it: on a returned body Spring MVC decodes the path of the original request,
     * to protect a client from a download named after it, and a request that ends up here may be here because of that path.
     */
    @RequestMapping(ERROR_PAGE)
    void error(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var problem = problemFor(request);
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Bytes, not the response's writer: JSON is UTF-8, and a writer with no charset declared would encode ISO-8859-1.
        json.writeValue(response.getOutputStream(), problem);
    }

    private static ProblemDetail problemFor(HttpServletRequest request) {
        if (!(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer status)) {
            return ProblemType.NOT_FOUND.problemAt(request.getRequestURI());
        }
        return ProblemType.forStatus(HttpStatusCode.valueOf(status))
                .problemAt(String.valueOf(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)));
    }
}
