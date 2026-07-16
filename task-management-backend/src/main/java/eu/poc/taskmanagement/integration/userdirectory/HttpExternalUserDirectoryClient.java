package eu.poc.taskmanagement.integration.userdirectory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class HttpExternalUserDirectoryClient implements ExternalUserDirectoryClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient client;
    private final String baseUrl;
    private final String usersPathTemplate;

    public HttpExternalUserDirectoryClient(
            @ConfigProperty(name = "external.user-directory.base-url") String baseUrl,
            @ConfigProperty(name = "external.user-directory.users-path-template", defaultValue = "/tasks/{taskId}/users") String usersPathTemplate,
            @ConfigProperty(name = "external.user-directory.connect-timeout-ms", defaultValue = "2000") long connectTimeoutMs) {
        this.baseUrl = baseUrl;
        this.usersPathTemplate = usersPathTemplate;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    @Override
    public Set<String> fetchCreatedUsers(String taskId) {
        String encodedTaskId = URLEncoder.encode(taskId, StandardCharsets.UTF_8);
        String path = usersPathTemplate.replace("{taskId}", encodedTaskId);
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizedBase + path))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("External user directory returned status " + response.statusCode());
            }

            Set<String> users = OBJECT_MAPPER.readValue(response.body(), new TypeReference<LinkedHashSet<String>>() {});
            return users.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to fetch created users from external system", e);
        }
    }
}
