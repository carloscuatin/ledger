package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.schema.SchemaValidator;
import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import tools.jackson.databind.node.ObjectNode;

/**
 * Holds the running API to its contract. The interfaces generated from the contract fix what the compiler can see, the
 * operations and the shape of their types; what travels on the wire, like a status, a header, a media type or a member
 * written by hand, is only visible in a real response from the real server.
 * <p>
 * A schema says what a response may look like, not whether it is the right one: which path a {@code Location} names, or
 * whether a problem's type goes with its status. Those are asserted here for a problem, and by the other tests of the
 * API for the rest.
 */
class ContractIT extends AuthenticatedApi {

    /** Relative to the module, which is where the build runs its tests. */
    private static final Path CONTRACT = Path.of("..", "contracts", "openapi", "ledger-v1.yaml");

    private static final String TRANSFERS = "/v1/transfers";
    private static final String TRANSFER = "/v1/transfers/{id}";

    private static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator
            .createForInlineApiSpecification(contract()).build();

    private static final OpenAPI DOCUMENT = document();

    private static final String BASE = DOCUMENT.getServers().getFirst().getUrl();

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private static final String NO_MEDIA_TYPE = "A body with no Content-Type";

    private static final List<Exercise> EXERCISES = List.of(
            answer("POST", TRANSFERS, 201, ContractIT::created),
            refusal("POST", TRANSFERS, ProblemType.MALFORMED_REQUEST, api -> api.posting("{oops")),
            refusal("POST", TRANSFERS, ProblemType.VALIDATION_FAILED, ContractIT::invalid),
            refusal("POST", TRANSFERS, ProblemType.UNAUTHENTICATED,
                    api -> send(postingJson(api.request(TRANSFERS), aValidBody()))),
            refusal("POST", TRANSFERS, ProblemType.TENANT_REQUIRED,
                    api -> send(postingJson(api.requestAs(TestAuthentication.NO_TENANT, TRANSFERS), aValidBody()))),
            refusal("POST", TRANSFERS, ProblemType.NOT_ACCEPTABLE,
                    api -> send(postingJson(api.requestAs(UUID.randomUUID(), TRANSFERS), aValidBody())
                            .header("Accept", "application/xml"))),
            refusal("POST", TRANSFERS, ProblemType.UNSUPPORTED_MEDIA_TYPE,
                    api -> send(api.requestAs(UUID.randomUUID(), TRANSFERS).header("Content-Type", "text/plain")
                            .POST(aValidBody()))),
            refusal("POST", TRANSFERS, ProblemType.INVALID_AMOUNT,
                    api -> api.postingTerms(UUID.randomUUID(), UUID.randomUUID(), "0.00", "COP")),
            refusal("POST", TRANSFERS, ProblemType.CURRENCY_NOT_ENABLED,
                    api -> api.postingTerms(UUID.randomUUID(), UUID.randomUUID(), "1.00", "ZZZ")),
            refusal("POST", TRANSFERS, ProblemType.SAME_ACCOUNT, api -> {
                var account = UUID.randomUUID();
                return api.postingTerms(account, account, "1.00", "COP");
            }),
            // The test filter cannot read this principal. A failure in a filter and one in a controller leave through the
            // same exit, and creating a transfer has no data that can make it fail.
            refusal("POST", TRANSFERS, ProblemType.INTERNAL_ERROR,
                    api -> send(postingJson(api.requestAs("not-a-principal", TRANSFERS), aValidBody()))),

            answer("GET", TRANSFERS, 200, api -> {
                var tenant = UUID.randomUUID();
                TransferRows.seed(tenant);
                TransferRows.seed(tenant);
                return send(api.requestAs(tenant, TRANSFERS + "?limit=1"));
            }),
            answer("GET", TRANSFERS, 200, ContractIT::lastPage),
            answer("GET", TRANSFERS, 200, api -> send(api.requestAs(UUID.randomUUID(), TRANSFERS))),
            refusal("GET", TRANSFERS, ProblemType.VALIDATION_FAILED,
                    api -> send(api.requestAs(UUID.randomUUID(), TRANSFERS + "?limit=0"))),
            refusal("GET", TRANSFERS, ProblemType.MALFORMED_REQUEST,
                    api -> send(api.requestAs(UUID.randomUUID(), TRANSFERS + "?limit=many"))),
            refusal("GET", TRANSFERS, ProblemType.INVALID_CURSOR,
                    api -> send(api.requestAs(UUID.randomUUID(), TRANSFERS + "?cursor=nope"))),
            refusal("GET", TRANSFERS, ProblemType.UNAUTHENTICATED, api -> send(api.request(TRANSFERS))),
            refusal("GET", TRANSFERS, ProblemType.TENANT_REQUIRED,
                    api -> send(api.requestAs(TestAuthentication.NO_TENANT, TRANSFERS))),
            refusal("GET", TRANSFERS, ProblemType.NOT_ACCEPTABLE,
                    api -> send(api.requestAs(UUID.randomUUID(), TRANSFERS).header("Accept", "application/xml"))),
            refusal("GET", TRANSFERS, ProblemType.INTERNAL_ERROR, api -> {
                var tenant = UUID.randomUUID();
                TransferRows.seedAsSuperuser(tenant, 1, "ZZZ", "PENDING");
                return send(api.requestAs(tenant, TRANSFERS));
            }),

            answer("GET", TRANSFER, 200, api -> {
                var tenant = UUID.randomUUID();
                return send(api.requestAs(tenant, TRANSFERS + "/" + TransferRows.seed(tenant)));
            }),
            refusal("GET", TRANSFER, ProblemType.MALFORMED_REQUEST,
                    api -> send(api.requestAs(UUID.randomUUID(), TRANSFERS + "/not-a-uuid"))),
            refusal("GET", TRANSFER, ProblemType.UNAUTHENTICATED, api -> send(api.request(aTransfer()))),
            refusal("GET", TRANSFER, ProblemType.TENANT_REQUIRED,
                    api -> send(api.requestAs(TestAuthentication.NO_TENANT, aTransfer()))),
            refusal("GET", TRANSFER, ProblemType.NOT_FOUND, ContractIT::notFound),
            refusal("GET", TRANSFER, ProblemType.NOT_ACCEPTABLE,
                    api -> send(api.requestAs(UUID.randomUUID(), aTransfer()).header("Accept", "application/xml"))),
            refusal("GET", TRANSFER, ProblemType.INTERNAL_ERROR, api -> {
                var tenant = UUID.randomUUID();
                var transfer = TransferRows.seedAsSuperuser(tenant, 1, "ZZZ", "PENDING");
                return send(api.requestAs(tenant, TRANSFERS + "/" + transfer));
            }));

    /** The refusals of what has no operation in the contract, which only its schema of a problem can speak for. */
    private static final List<Refusal> REFUSALS_OUTSIDE_AN_OPERATION = List.of(
            new Refusal(ProblemType.NOT_FOUND, "/v1/nope", api -> send(api.requestAs(UUID.randomUUID(), "/v1/nope"))),
            new Refusal(ProblemType.METHOD_NOT_ALLOWED, TRANSFERS,
                    api -> send(api.requestAs(UUID.randomUUID(), TRANSFERS).PUT(BodyPublishers.noBody()))),
            new Refusal(ProblemType.UNSUPPORTED_VERSION, "/v2/transfers",
                    api -> send(api.requestAs(UUID.randomUUID(), "/v2/transfers"))),
            new Refusal(ProblemType.BAD_REQUEST, "/",
                    api -> send(api.requestAs(UUID.randomUUID(), TRANSFERS + ";x=1"))));

    /** Each one changes a real response in one way, so a validator that lets anything through cannot pass the suite. */
    private static final List<Drift> DRIFTS = List.of(
            new Drift("a required field is missing", ContractIT::created,
                    Reply.changingBody(body -> body.remove("status")),
                    "schema.required", "'status'"),
            new Drift("an undocumented field", ContractIT::created,
                    Reply.changingBody(body -> body.put("tenantId", "unexpected")),
                    "schema.additionalProperties", "'tenantId'"),
            new Drift("an amount as a number", ContractIT::created, Reply.changingBody(body -> body.put("amount", 1.5)),
                    "schema.type", "/amount"),
            new Drift("an undocumented status", ContractIT::created, reply -> reply.withStatus(200),
                    "status.unknown", "200"),
            new Drift("no Location", ContractIT::created, reply -> reply.withHeader("Location"),
                    "header.missing", "'Location'"),
            new Drift("a Location that is no reference", ContractIT::created,
                    reply -> reply.withHeader("Location", "not a reference %"), "header.schema.format.uri-reference",
                    ""),
            new Drift("another media type", ContractIT::created,
                    reply -> reply.withHeader("Content-Type", "text/plain"), "contentType.notAllowed", "'text/plain'"),
            new Drift("no media type", ContractIT::created, reply -> reply.withHeader("Content-Type"),
                    NO_MEDIA_TYPE, ""),
            new Drift("no body", ContractIT::created, reply -> reply.withBody(""), "body.missing", ""),
            new Drift("nextCursor left out instead of null", ContractIT::lastPage,
                    Reply.changingBody(body -> body.remove("nextCursor")), "schema.required", "'nextCursor'"),
            new Drift("a trace in a problem", ContractIT::notFound, Reply.changingBody(body -> body.put("trace", "at")),
                    "schema.additionalProperties", "'trace'"),
            new Drift("a type outside the catalog", ContractIT::notFound,
                    Reply.changingBody(body -> body.put("type", "about:blank")), "schema.enum", "/type"),
            new Drift("a member under another name", ContractIT::notFound, Reply.changingBody(body -> {
                body.set("description", body.remove("detail"));
            }), "schema.additionalProperties", "'description'"),
            new Drift("an incomplete problem", ContractIT::notFound, Reply.changingBody(body -> body.remove("detail")),
                    "schema.required", "'detail'"),
            new Drift("a problem in plain JSON", ContractIT::notFound,
                    reply -> reply.withHeader("Content-Type", "application/json"),
                    "contentType.notAllowed", "'application/json'"),
            new Drift("a rejected value in a problem", ContractIT::invalid,
                    Reply.changingBody(body -> ((ObjectNode) body.get("errors").get(0)).put("rejectedValue", "secret")),
                    "schema.additionalProperties", "'rejectedValue'"),
            new Drift("no scheme to authenticate with", api -> send(api.request(TRANSFERS)),
                    reply -> reply.withHeader("WWW-Authenticate"), "header.missing", "'WWW-Authenticate'"),
            new Drift("a route the contract does not have", ContractIT::created, reply -> reply.at("/v1/nope"),
                    "path.missing", "/v1/nope"),
            new Drift("a method the contract does not have", ContractIT::created,
                    reply -> reply.at("DELETE", TRANSFERS),
                    "operation.notAllowed", "DELETE"));

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @ParameterizedTest(name = "{0}")
    @MethodSource("exercises")
    void everyDocumentedResponseMatchesTheContract(Exercise exercise) throws Exception {
        var response = exercise.call().on(this);

        assertThat(deviationsOf(Reply.of(response))).isEmpty();
        assertThat(response.statusCode()).isEqualTo(exercise.status());
        exercise.problem().ifPresent(type -> assertProblem(response, type, response.uri().getPath()));
    }

    /** Read from the document, so a status added to the contract fails here until something exercises it. */
    @Test
    void everyDocumentedResponseIsExercised() {
        var documented = DOCUMENT.getPaths().entrySet().stream()
                .flatMap(path -> path.getValue().readOperationsMap().entrySet().stream()
                        .flatMap(operation -> operation.getValue().getResponses().keySet().stream()
                                .map(status -> "%s %s%s %s".formatted(operation.getKey(), BASE, path.getKey(),
                                        status))))
                .distinct()
                .toList();

        assertThat(EXERCISES.stream().map(Exercise::response).distinct())
                .containsExactlyInAnyOrderElementsOf(documented);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusalsOutsideAnOperation")
    void aRefusalOutsideAnyOperationIsAProblemOfTheContract(Refusal refusal) throws Exception {
        var response = refusal.call().on(this);

        assertProblem(response, refusal.type(), refusal.instance());
        assertThat(deviationsFromTheProblemSchema(response.body())).isEmpty();
    }

    /** Without this, a broken schema or a validator that reads none would pass the test above. */
    @Test
    void theSchemaOfAProblemRefusesAProblemThatDrifts() throws Exception {
        var refusal = (ObjectNode) JSON.readTree(notFound().body());

        assertThat(deviationsFromTheProblemSchema(refusal.put("trace", "at").toString()))
                .singleElement().asString().contains("additionalProperties", "'trace'");
    }

    @Test
    void everyTypeOfTheCatalogIsExercised() {
        var exercised = Stream.concat(EXERCISES.stream().flatMap(exercise -> exercise.problem().stream()),
                REFUSALS_OUTSIDE_AN_OPERATION.stream().map(Refusal::type));

        assertThat(exercised.collect(Collectors.toSet())).containsExactlyInAnyOrder(ProblemType.values());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drifts")
    void aResponseThatDriftsIsReported(Drift drift) throws Exception {
        var reply = Reply.of(drift.source().on(this));
        assertThat(deviationsOf(reply)).isEmpty();

        assertThat(deviationsOf(drift.change().apply(reply)))
                .anySatisfy(deviation -> assertThat(deviation).contains(drift.key(), drift.named()));
    }

    /**
     * The compiler only covers part of this. An operation with no implementation fails to compile when it falls in an
     * interface a controller implements, but the generator writes one interface per tag, so an operation under another tag
     * or none compiles unimplemented; and it says nothing about a route that something else declares. Nor is the validator
     * a substitute: it finds a path with or without the base of the contract, so a route that lost its version would still
     * look documented to it. The error page stays out because no client calls it.
     */
    @Test
    void theMappedOperationsAreTheDocumentedOnes() {
        var mapped = mappings.getHandlerMethods().entrySet().stream()
                .filter(mapping -> mapping.getValue().getBeanType() != ContainerErrorController.class)
                .flatMap(mapping -> operationsOf(mapping.getKey()))
                .toList();

        var documented = DOCUMENT.getPaths().entrySet().stream()
                .flatMap(path -> path.getValue().readOperationsMap().keySet().stream()
                        .map(method -> "%s %s%s".formatted(method, BASE, path.getKey())))
                .toList();

        assertThat(mapped).containsExactlyInAnyOrderElementsOf(documented);
    }

    /** A client that sends this header believes a retry is safe, and until the API honors it, it is not. */
    @Test
    void theContractDoesNotDeclareAnIdempotencyKey() {
        var declared = declaredHeaders();

        assertThat(declared).contains("Location", "WWW-Authenticate").noneMatch(IDEMPOTENCY_KEY::equalsIgnoreCase);
    }

    /**
     * The other direction: what a client that follows the contract sends. Without it, the server could be accepting a
     * request the contract forbids, and every response above would still conform.
     */
    @Test
    void theRequestsTheServerAcceptsAreTheOnesTheContractDescribes() throws Exception {
        var tenant = UUID.randomUUID();
        var body = "{\"sourceAccountId\": \"%s\", \"destinationAccountId\": \"%s\", \"amount\": \"1.50\", \"currency\": \"COP\"}"
                .formatted(UUID.randomUUID(), UUID.randomUUID());
        var created = send(postingJson(requestAs(tenant, TRANSFERS), BodyPublishers.ofString(body)));
        var listed = send(requestAs(tenant, TRANSFERS + "?limit=5"));

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(messagesOf(VALIDATOR.validateRequest(SimpleRequest.Builder.post(TRANSFERS)
                .withContentType("application/json").withBody(body).build()))).isEmpty();
        assertThat(messagesOf(VALIDATOR.validateRequest(SimpleRequest.Builder.get(TRANSFERS)
                .withQueryParam("limit", "5").build()))).isEmpty();
        assertThat(messagesOf(VALIDATOR.validateRequest(SimpleRequest.Builder.post(TRANSFERS)
                .withContentType("application/json").withBody("{\"tenantId\": \"unexpected\"}").build())))
                .anySatisfy(message -> assertThat(message).contains("request.body.schema.additionalProperties"));
    }

    static Stream<Exercise> exercises() {
        return EXERCISES.stream();
    }

    static Stream<Refusal> refusalsOutsideAnOperation() {
        return REFUSALS_OUTSIDE_AN_OPERATION.stream();
    }

    static Stream<Drift> drifts() {
        return DRIFTS.stream();
    }

    /**
     * Any message counts, and not only the errors, so a level changed by a file on the classpath cannot hide one. The
     * validator lets a body through with no media type at all, which a client could not read.
     */
    private static List<String> deviationsOf(Reply reply) {
        var response = new SimpleResponse.Builder(reply.status()).withBody(reply.body());
        reply.headers().forEach(response::withHeader);
        var deviations = new ArrayList<>(
                messagesOf(VALIDATOR.validateResponse(reply.path(), reply.method(), response.build())));
        if (!reply.body().isEmpty() && !reply.headers().containsKey("Content-Type")) {
            deviations.add(NO_MEDIA_TYPE);
        }
        return deviations;
    }

    private static List<String> deviationsFromTheProblemSchema(String body) {
        var schema = DOCUMENT.getComponents().getSchemas().get("Problem");
        return messagesOf(new SchemaValidator(DOCUMENT, new MessageResolver()).validate(body, schema, "response.body"));
    }

    /** With the field a message is about, since not every message of the validator names one. */
    private static List<String> messagesOf(ValidationReport report) {
        return report.getMessages().stream()
                .map(message -> "%s at %s: %s".formatted(message.getKey(),
                        message.getContext().flatMap(ValidationReport.MessageContext::getPointers)
                                .map(ValidationReport.MessageContext.Pointers::getInstance).orElse("-"),
                        message.getMessage()))
                .toList();
    }

    private static Stream<String> operationsOf(RequestMappingInfo mapping) {
        var version = "/v" + mapping.getVersionCondition().getVersion();
        var methods = mapping.getMethodsCondition().getMethods().stream().map(Enum::name).toList();
        return (methods.isEmpty() ? List.of("ANY") : methods).stream()
                .flatMap(method -> mapping.getPathPatternsCondition().getPatternValues().stream()
                        .map(pattern -> "%s %s".formatted(method,
                                pattern.replaceFirst("^/\\{version:[^}]+}", version))));
    }

    /** Everywhere a header can be declared, including what no operation uses yet. */
    private static Set<String> declaredHeaders() {
        var operations = DOCUMENT.getPaths().values().stream().flatMap(path -> path.readOperations().stream()).toList();
        var parameters = Stream.of(
                DOCUMENT.getPaths().values().stream().flatMap(path -> optional(path.getParameters())),
                operations.stream().flatMap(operation -> optional(operation.getParameters())),
                optional(DOCUMENT.getComponents().getParameters()).map(Map.Entry::getValue))
                .flatMap(stream -> stream)
                .filter(parameter -> "header".equals(parameter.getIn()))
                .map(parameter -> parameter.getName());
        var responses = Stream.concat(
                operations.stream().flatMap(operation -> operation.getResponses().values().stream()),
                optional(DOCUMENT.getComponents().getResponses()).map(Map.Entry::getValue));
        var inResponses = responses.flatMap(response -> optional(response.getHeaders()).map(Map.Entry::getKey));
        var inComponents = optional(DOCUMENT.getComponents().getHeaders()).map(Map.Entry::getKey);
        return Stream.of(parameters, inResponses, inComponents).flatMap(stream -> stream).collect(Collectors.toSet());
    }

    private static <T> Stream<T> optional(List<T> items) {
        return items == null ? Stream.empty() : items.stream();
    }

    private static <K, V> Stream<Map.Entry<K, V>> optional(Map<K, V> items) {
        return items == null ? Stream.empty() : items.entrySet().stream();
    }

    private HttpResponse<String> created() throws IOException, InterruptedException {
        return postingTerms(UUID.randomUUID(), UUID.randomUUID(), "150000.00", "COP");
    }

    private HttpResponse<String> lastPage() throws Exception {
        var tenant = UUID.randomUUID();
        TransferRows.seed(tenant);
        return send(requestAs(tenant, TRANSFERS));
    }

    private HttpResponse<String> notFound() throws IOException, InterruptedException {
        return send(requestAs(UUID.randomUUID(), aTransfer()));
    }

    private HttpResponse<String> invalid() throws IOException, InterruptedException {
        return posting("{}");
    }

    private HttpResponse<String> posting(String body) throws IOException, InterruptedException {
        return send(postingJson(requestAs(UUID.randomUUID(), TRANSFERS), BodyPublishers.ofString(body)));
    }

    private HttpResponse<String> postingTerms(UUID source, UUID destination, String amount, String currency)
            throws IOException, InterruptedException {
        return posting("""
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "%s", "currency": "%s"}
                """.formatted(source, destination, amount, currency));
    }

    private static String aTransfer() {
        return TRANSFERS + "/" + UUID.randomUUID();
    }

    private static String contract() {
        try {
            return Files.readString(CONTRACT);
        } catch (IOException failure) {
            throw new IllegalStateException("The contract could not be read", failure);
        }
    }

    private static OpenAPI document() {
        var options = new ParseOptions();
        options.setResolve(true);
        return new OpenAPIV3Parser().readContents(contract(), null, options).getOpenAPI();
    }

    private static Exercise answer(String method, String path, int status, Call call) {
        return new Exercise(method, path, status, Optional.empty(), call);
    }

    private static Exercise refusal(String method, String path, ProblemType type, Call call) {
        return new Exercise(method, path, type.status().value(), Optional.of(type), call);
    }

    /** A request that gets one of the responses the contract documents, on this class's real server. */
    @FunctionalInterface
    private interface Call {

        HttpResponse<String> on(ContractIT api) throws Exception;
    }

    private record Refusal(ProblemType type, String instance, Call call) {

        @Override
        public String toString() {
            return type.toString();
        }
    }

    private record Drift(String name, Call source, UnaryOperator<Reply> change, String key, String named) {

        @Override
        public String toString() {
            return name;
        }
    }

    /** A response as it came off the wire, which a test can then change in one way. */
    private record Reply(String path, Request.Method method, int status, Map<String, List<String>> headers,
            String body) {

        static Reply of(HttpResponse<String> response) {
            var headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
            headers.putAll(response.headers().map());
            return new Reply(response.uri().getPath(), Request.Method.valueOf(response.request().method()),
                    response.statusCode(), headers, response.body());
        }

        static UnaryOperator<Reply> changingBody(Consumer<ObjectNode> change) {
            return reply -> {
                var body = (ObjectNode) JSON.readTree(reply.body());
                change.accept(body);
                return reply.withBody(body.toString());
            };
        }

        Reply withStatus(int changed) {
            return new Reply(path, method, changed, headers, body);
        }

        Reply withBody(String changed) {
            return new Reply(path, method, status, headers, changed);
        }

        Reply at(String changed) {
            return new Reply(changed, method, status, headers, body);
        }

        Reply at(String changedMethod, String changedPath) {
            return new Reply(changedPath, Request.Method.valueOf(changedMethod), status, headers, body);
        }

        /** With no values, the response loses the header. */
        Reply withHeader(String name, String... values) {
            var changed = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
            changed.putAll(headers);
            changed.remove(name);
            if (values.length > 0) {
                changed.put(name, List.of(values));
            }
            return new Reply(path, method, status, changed, body);
        }
    }

    private record Exercise(String method, String path, int status, Optional<ProblemType> problem, Call call) {

        String response() {
            return "%s %s %d".formatted(method, path, status);
        }

        @Override
        public String toString() {
            return response() + problem.map(type -> " " + type).orElse("");
        }
    }
}
