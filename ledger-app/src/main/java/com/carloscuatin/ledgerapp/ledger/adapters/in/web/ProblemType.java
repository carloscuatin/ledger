package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import java.net.URI;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Every way the API refuses a request, as RFC 9457 problems a client can program against. Title and detail are constant
 * for each type, so no input of the client can come back through them, and what the framework would have said, which
 * repeats that input and describes the implementation, never reaches a response.
 */
enum ProblemType {

    /** No authenticated principal. */
    UNAUTHENTICATED("unauthenticated", HttpStatus.UNAUTHORIZED, "Unauthenticated",
            "This request needs an authenticated principal."),

    /** An authenticated principal that belongs to no tenant. */
    TENANT_REQUIRED("tenant-required", HttpStatus.FORBIDDEN, "Tenant required",
            "The authenticated principal has no tenant to act for."),

    /** Invalid JSON, an unknown field, a wrong JSON type, a body empty or too long, a value that cannot be converted. */
    MALFORMED_REQUEST("malformed-request", HttpStatus.BAD_REQUEST, "Malformed request",
            "The request could not be read as the contract describes it."),

    /** A required input missing or null, or one that breaks a pattern, a length or a limit. */
    VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST, "Validation failed",
            "The request breaks a constraint of the contract. The errors member lists what was rejected."),

    /** A cursor within its length that does not decode to a position. */
    INVALID_CURSOR("invalid-cursor", HttpStatus.BAD_REQUEST, "Invalid cursor",
            "The cursor does not decode to a position."),

    /** A well formed version of the API that is not served. */
    UNSUPPORTED_VERSION("unsupported-version", HttpStatus.BAD_REQUEST, "Unsupported version",
            "This version of the API is not served."),

    /** Anything else the client got wrong, like a URL the firewall rejects. */
    BAD_REQUEST("bad-request", HttpStatus.BAD_REQUEST, "Bad request",
            "The server refused this request as invalid."),

    /** A route that does not exist, or something that does not exist for the tenant. */
    NOT_FOUND("not-found", HttpStatus.NOT_FOUND, "Not found",
            "Nothing exists at this location."),

    /** A method the route does not support. */
    METHOD_NOT_ALLOWED("method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed",
            "This location does not support the method. The Allow header lists the ones it does."),

    /** Nothing the {@code Accept} header allows. */
    NOT_ACCEPTABLE("not-acceptable", HttpStatus.NOT_ACCEPTABLE, "Not acceptable",
            "The API has no representation that the Accept header allows."),

    /** A body in another format. */
    UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type",
            "The body must be application/json."),

    /** A well formed amount the ledger does not accept. */
    INVALID_AMOUNT("invalid-amount", HttpStatus.UNPROCESSABLE_CONTENT, "Invalid amount",
            "The amount is beyond the scale of its currency or the range of the ledger, or is not positive."),

    /** A well formed code of a currency this deployment cannot operate. */
    CURRENCY_NOT_ENABLED("currency-not-enabled", HttpStatus.UNPROCESSABLE_CONTENT, "Currency not enabled",
            "The currency does not exist, has no minor unit, or is not enabled."),

    /** The same account as source and destination. */
    SAME_ACCOUNT("same-account", HttpStatus.UNPROCESSABLE_CONTENT, "Same account",
            "Source and destination must be different accounts."),

    /** A failure of this side. It says nothing about what failed. */
    INTERNAL_ERROR("internal-error", HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
            "The request could not be completed.");

    private static final String BASE = "https://ledger.carloscuatin.com/problems/";

    /** The longest path this API serves is far shorter, so anything longer has no reason to be repeated in a response. */
    private static final int MAX_INSTANCE_LENGTH = 256;

    /**
     * Without a percent sign nothing in it decodes to something else, and without a semicolon or a quote it carries nothing
     * but segments: a path outside this alphabet is one the API never serves, so its response has no reason to repeat it.
     */
    private static final Pattern PLAIN_PATH = Pattern.compile("/|(/[A-Za-z0-9._~-]+)+/?");

    private static final String UNREPEATABLE_INSTANCE = "/";

    private final URI type;
    private final HttpStatus status;
    private final String title;
    private final String detail;

    ProblemType(String slug, HttpStatus status, String title, String detail) {
        this.type = URI.create(BASE + slug);
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    /**
     * The type of a refusal that only has a status to go by, because the framework or the server decided it. The four
     * hundreds with no type of their own are all the client's fault and say nothing useful beyond that, and a status
     * above them is a failure of this side, however the server worded it.
     */
    static ProblemType forStatus(HttpStatusCode status) {
        if (status.is4xxClientError()) {
            return switch (status.value()) {
                case 404 -> NOT_FOUND;
                case 405 -> METHOD_NOT_ALLOWED;
                case 406 -> NOT_ACCEPTABLE;
                case 415 -> UNSUPPORTED_MEDIA_TYPE;
                default -> BAD_REQUEST;
            };
        }
        return INTERNAL_ERROR;
    }

    HttpStatus status() {
        return status;
    }

    URI type() {
        return type;
    }

    String title() {
        return title;
    }

    String detail() {
        return detail;
    }

    ProblemDetail problemAt(String path) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setInstance(URI.create(instanceFor(path)));
        return problem;
    }

    private static String instanceFor(String path) {
        return path.length() <= MAX_INSTANCE_LENGTH && PLAIN_PATH.matcher(path).matches()
                ? path
                : UNREPEATABLE_INSTANCE;
    }
}
