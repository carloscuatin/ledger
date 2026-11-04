package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.accept.InvalidApiVersionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.carloscuatin.ledgerapp.ledger.domain.CurrencyNotEnabledException;
import com.carloscuatin.ledgerapp.ledger.domain.InvalidAmountException;
import com.carloscuatin.ledgerapp.ledger.domain.NonPositiveAmountException;
import com.carloscuatin.ledgerapp.ledger.domain.SameAccountTransferException;

import jakarta.validation.constraints.NotNull;

/**
 * Translates what stops a request inside Spring MVC into the catalog of the API. An expected refusal is the client's to
 * fix and leaves no trace in the log, where it would hide real failures and repeat what the client sent. Any other
 * exception has no handler here on purpose: it reaches the server, which logs it with its trace and forwards the request
 * to {@link ContainerErrorController}, the same way as a failure in a filter, which no handler here could ever see. Only
 * a failure inside the framework that it reports as an exception of its own is logged here, because it stops here.
 */
@ControllerAdvice
@Profile("api")
class ProblemHandler extends ResponseEntityExceptionHandler {

    private static final String REQUIRED = "required";
    private static final String INVALID = "invalid";

    @ExceptionHandler({InvalidAmountException.class, NonPositiveAmountException.class})
    ResponseEntity<Object> invalidAmount(WebRequest request) {
        return answer(ProblemType.INVALID_AMOUNT, request);
    }

    @ExceptionHandler({CurrencyNotEnabledException.class, UnusableCurrencyException.class})
    ResponseEntity<Object> currencyNotEnabled(WebRequest request) {
        return answer(ProblemType.CURRENCY_NOT_ENABLED, request);
    }

    @ExceptionHandler(SameAccountTransferException.class)
    ResponseEntity<Object> sameAccount(WebRequest request) {
        return answer(ProblemType.SAME_ACCOUNT, request);
    }

    @ExceptionHandler(TenantRequiredException.class)
    ResponseEntity<Object> tenantRequired(WebRequest request) {
        return answer(ProblemType.TENANT_REQUIRED, request);
    }

    /** The parent class also logs this one, and the methods a scan of the API tries are not worth a warning each. */
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException failure, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return handleExceptionInternal(failure, null, headers, status, request);
    }

    /** Every exception of Spring MVC ends here, with the status and headers the framework chose for it. */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception failure, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var type = typeOf(failure, status);
        if (type == ProblemType.INTERNAL_ERROR) {
            logger.error("The framework failed while serving a request", failure);
        }
        var problem = type.problemAt(pathOf(request));
        if (failure instanceof MethodArgumentNotValidException invalid) {
            problem.setProperty("errors", fieldsOf(invalid.getBindingResult()));
        } else if (failure instanceof HandlerMethodValidationException invalid) {
            problem.setProperty("errors", parametersOf(invalid));
        }
        return super.handleExceptionInternal(failure, problem, problemHeaders(failure, headers), type.status(),
                request);
    }

    /**
     * The status tells these apart except for a 400, which is all the framework has for several different mistakes.
     * Deciding by the status first keeps out the exceptions that share a parent with a client's mistake and are not one,
     * like a conversion the application itself cannot do.
     */
    private static ProblemType typeOf(Exception failure, HttpStatusCode status) {
        if (status.value() != ProblemType.BAD_REQUEST.status().value()) {
            return ProblemType.forStatus(status);
        }
        return switch (failure) {
            case HttpMessageNotReadableException _,TypeMismatchException _ -> ProblemType.MALFORMED_REQUEST;
            case MethodArgumentNotValidException _,HandlerMethodValidationException _ -> ProblemType.VALIDATION_FAILED;
            case InvalidApiVersionException _ -> ProblemType.UNSUPPORTED_VERSION;
            default -> ProblemType.BAD_REQUEST;
        };
    }

    /**
     * The problem's own type, set here so that no {@code Accept} of the client can pick another or find none. What the
     * framework adds for a client to use stays, like {@code Allow}, except the list of representations that a refused
     * {@code Accept} carries.
     */
    private static HttpHeaders problemHeaders(Exception failure, HttpHeaders headers) {
        var problemHeaders = new HttpHeaders();
        problemHeaders.addAll(headers);
        if (failure instanceof HttpMediaTypeNotAcceptableException) {
            problemHeaders.remove(HttpHeaders.ACCEPT);
        }
        problemHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return problemHeaders;
    }

    private ResponseEntity<Object> answer(ProblemType type, WebRequest request) {
        return ResponseEntity.status(type.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(type.problemAt(pathOf(request)));
    }

    private static String pathOf(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }

    /** Sorted because validation reports them in no particular order, and the same request should get the same answer. */
    private static List<BodyInput> fieldsOf(BindingResult result) {
        return result.getFieldErrors().stream()
                .map(error -> new BodyInput("/" + error.getField().replace('.', '/'), reasonOf(error.getCode())))
                .distinct()
                .sorted(Comparator.comparing(BodyInput::pointer))
                .toList();
    }

    private static List<ParameterInput> parametersOf(HandlerMethodValidationException failure) {
        return failure.getParameterValidationResults().stream()
                .map(result -> new ParameterInput(String.valueOf(result.getMethodParameter().getParameterName()),
                        INVALID))
                .distinct()
                .sorted(Comparator.comparing(ParameterInput::parameter))
                .toList();
    }

    private static String reasonOf(@Nullable String code) {
        return NotNull.class.getSimpleName().equals(code) ? REQUIRED : INVALID;
    }

    private record BodyInput(String pointer, String reason) {
    }

    private record ParameterInput(String parameter, String reason) {
    }
}
