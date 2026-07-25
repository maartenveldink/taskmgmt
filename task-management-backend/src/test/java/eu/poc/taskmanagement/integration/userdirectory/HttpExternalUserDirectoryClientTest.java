package eu.poc.taskmanagement.integration.userdirectory;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link HttpExternalUserDirectoryClient} against a real, in-process
 * HTTP server (JDK {@link HttpServer}). This exercises the actual
 * {@link java.net.http.HttpClient} request/response and JSON parsing paths.
 *
 * <p>Runs as a {@code @QuarkusTest} so the class under test is loaded through
 * the Quarkus runtime classloader and is visible to the coverage agent. The
 * client is constructed directly with a base URL pointing at the stub server.
 */
@QuarkusTest
class HttpExternalUserDirectoryClientTest {

    private HttpServer server;
    private String baseUrl;

    /** Body and status the stub returns for the next request. */
    private final AtomicReference<String> responseBody = new AtomicReference<>("[]");
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
    /** Captures the path the client requested, so we can assert URL building. */
    private final AtomicReference<String> requestedPath = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private HttpExternalUserDirectoryClient newClient() {
        return new HttpExternalUserDirectoryClient(
                baseUrl, "/tasks/{taskId}/users", 2000);
    }

    @Test
    @DisplayName("200 with JSON array → trimmed, de-duplicated, non-empty users")
    void returnsSanitizedUserSet() {
        responseStatus.set(200);
        // Contains whitespace, a blank entry and a duplicate that must be cleaned up.
        responseBody.set("[\"alice\", \" bob \", \"\", \"alice\"]");

        Set<String> users = newClient().fetchCreatedUsers("task-1");

        assertThat(users).containsExactly("alice", "bob");
    }

    @Test
    @DisplayName("Task id is URL-encoded and substituted into the path template")
    void encodesTaskIdIntoPath() {
        responseStatus.set(200);
        responseBody.set("[]");

        newClient().fetchCreatedUsers("a/b c");

        // The client decodes back to the original once the server URL-decodes it.
        assertThat(URLDecoder.decode(requestedPath.get(), StandardCharsets.UTF_8))
                .isEqualTo("/tasks/a/b c/users");
    }

    @Test
    @DisplayName("Trailing slash on the base URL is normalized (no double slash)")
    void normalizesTrailingSlashInBaseUrl() {
        responseStatus.set(200);
        responseBody.set("[]");
        HttpExternalUserDirectoryClient client = new HttpExternalUserDirectoryClient(
                baseUrl + "/", "/tasks/{taskId}/users", 2000);

        client.fetchCreatedUsers("t1");

        assertThat(requestedPath.get()).isEqualTo("/tasks/t1/users");
    }

    @Test
    @DisplayName("HTTP error status → IllegalStateException mentioning the status")
    void errorStatusThrows() {
        responseStatus.set(503);
        responseBody.set("service unavailable");

        assertThatThrownBy(() -> newClient().fetchCreatedUsers("task-err"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("503");
    }

    @Test
    @DisplayName("Unreachable host → IllegalStateException wrapping the I/O failure")
    void connectionFailureThrows() {
        // Point at a port where nothing is listening.
        HttpExternalUserDirectoryClient client = new HttpExternalUserDirectoryClient(
                "http://localhost:1", "/tasks/{taskId}/users", 500);

        assertThatThrownBy(() -> client.fetchCreatedUsers("task-x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to fetch created users");
    }
}
