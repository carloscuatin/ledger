package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** What every refusal of the API has in common, whichever of its exits produced it. */
final class ProblemAssertions {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> MEMBERS = Set.of("type", "title", "status", "detail", "instance");

    private ProblemAssertions() {
    }

    /** Exactly the members of the contract, so anything the framework adds, like a trace or a timestamp, fails. */
    static JsonNode assertProblem(HttpResponse<String> response, ProblemType type, String instance) {
        assertThat(response.statusCode()).isEqualTo(type.status().value());
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertNeitherCachedNorSniffed(response.headers());
        var problem = JSON.readTree(response.body());
        var members = new HashSet<>(problem.propertyNames());
        members.remove("errors");
        assertThat(members).isEqualTo(MEMBERS);
        assertThat(problem.path("type").asString()).isEqualTo(type.type().toString());
        assertThat(problem.path("title").asString()).isEqualTo(type.title());
        assertThat(problem.path("status").asInt()).isEqualTo(type.status().value());
        assertThat(problem.path("detail").asString()).isEqualTo(type.detail());
        assertThat(problem.path("instance").asString()).isEqualTo(instance);
        return problem;
    }

    static void assertRejectedInputs(JsonNode problem, List<Map<String, String>> inputs) {
        assertThat(JSON.treeToValue(problem.path("errors"), List.class)).containsExactlyElementsOf(inputs);
    }

    static void assertNeitherCachedNorSniffed(HttpHeaders headers) {
        assertThat(headers.allValues("Cache-Control")).singleElement()
                .satisfies(value -> assertThat(value).contains("no-store"));
        assertThat(headers.allValues("X-Content-Type-Options")).containsExactly("nosniff");
    }
}
