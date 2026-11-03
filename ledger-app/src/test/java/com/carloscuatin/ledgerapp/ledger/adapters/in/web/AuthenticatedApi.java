package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.LedgerDatabase;

import tools.jackson.databind.json.JsonMapper;

/**
 * The real server and the production chain with a test principal added. The classes that extend it share one context,
 * because Spring tells contexts apart by, among other things, the class that declares the dynamic properties.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("api")
@Import(TestAuthentication.class)
abstract class AuthenticatedApi {

    static final JsonMapper JSON = JsonMapper.builder().build();

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void connectAsTheApplication(DynamicPropertyRegistry registry) {
        LedgerDatabase.connectAsTheApplication(registry);
    }

    @LocalServerPort
    private int port;

    HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:%d%s".formatted(port, path)));
    }

    HttpRequest.Builder requestAs(String principal, String path) {
        return request(path).header(TestAuthentication.PRINCIPAL_HEADER, principal);
    }

    HttpRequest.Builder requestAs(UUID tenant, String path) {
        return requestAs(tenant.toString(), path);
    }

    /** A valid body between two new accounts, for tests that care about something other than its content. */
    static HttpRequest.BodyPublisher aValidBody() {
        return BodyPublishers.ofString("""
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "150000.00", "currency": "COP"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID()));
    }

    static HttpRequest.Builder postingJson(HttpRequest.Builder request, HttpRequest.BodyPublisher body) {
        return request.header("Content-Type", "application/json").POST(body);
    }

    static HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return CLIENT.send(request.build(), BodyHandlers.ofString());
    }
}
