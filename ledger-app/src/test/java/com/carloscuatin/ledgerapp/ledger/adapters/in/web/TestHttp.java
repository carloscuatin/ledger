package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * How a test talks to the server. Nothing here may wait forever, because a server that stops answering would otherwise
 * hold the whole run with no test to blame: reaching a time limit is a failure with a name.
 */
final class TestHttp {

    /** Everything is on this machine, so a connection is immediate or refused. */
    private static final Duration CONNECT_LIMIT = Duration.ofSeconds(5);

    /**
     * Longer than the connection pool waits by default, so a test that runs out of connections gets the server's answer
     * and not a limit of its own.
     */
    private static final Duration RESPONSE_LIMIT = Duration.ofSeconds(60);

    /** Well short of how long the server waits for a client that stopped sending, so the test is the one that notices. */
    private static final Duration SOCKET_READ_LIMIT = Duration.ofSeconds(30);

    /** A problem without the optional list of errors has no other closing brace. */
    private static final int END_OF_A_PROBLEM = '}';

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(CONNECT_LIMIT).build();

    private TestHttp() {
    }

    static HttpClient client() {
        return CLIENT;
    }

    static HttpRequest.Builder bounded(HttpRequest.Builder request) {
        return request.timeout(RESPONSE_LIMIT);
    }

    static HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return CLIENT.send(bounded(request).build(), BodyHandlers.ofString());
    }

    /**
     * For what the client above refuses to send: a URL with a broken escape, a declared body that never arrives. The text
     * goes out as it is, and the answer comes back whole, so the request needs {@code Connection: close}.
     */
    static String overASocket(int port, String request) throws IOException {
        try (var socket = connectedTo(port)) {
            write(socket, request);
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * The headers of a request whose body is declared and never sent, and the problem that answers them. This stops at the
     * end of the problem and not of the response: having answered, the server goes on to discard the rest of a body it did
     * not read, and only then ends the response, which here would never happen.
     */
    static String problemWithoutSendingTheBody(int port, String headers) throws IOException {
        try (var socket = connectedTo(port)) {
            write(socket, headers);
            var input = socket.getInputStream();
            var answer = new StringBuilder();
            var next = input.read();
            while (next >= 0 && next != END_OF_A_PROBLEM) {
                answer.append((char) next);
                next = input.read();
            }
            return answer.append((char) END_OF_A_PROBLEM).toString();
        }
    }

    private static Socket connectedTo(int port) throws IOException {
        var socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), Math.toIntExact(CONNECT_LIMIT.toMillis()));
        socket.setSoTimeout(Math.toIntExact(SOCKET_READ_LIMIT.toMillis()));
        return socket;
    }

    private static void write(Socket socket, String text) throws IOException {
        socket.getOutputStream().write(text.getBytes(StandardCharsets.ISO_8859_1));
        socket.getOutputStream().flush();
    }
}
