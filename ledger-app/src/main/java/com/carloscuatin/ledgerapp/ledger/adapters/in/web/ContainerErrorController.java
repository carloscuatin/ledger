package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

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

    @RequestMapping(ERROR_PAGE)
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        var problem = problemFor(request);
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(problem);
    }

    private static ProblemDetail problemFor(HttpServletRequest request) {
        if (!(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer status)) {
            return ProblemType.NOT_FOUND.problemAt(request.getRequestURI());
        }
        return ProblemType.forStatus(HttpStatusCode.valueOf(status))
                .problemAt(String.valueOf(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)));
    }
}
