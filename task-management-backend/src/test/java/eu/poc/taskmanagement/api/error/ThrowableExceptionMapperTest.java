package eu.poc.taskmanagement.api.error;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the last-resort {@link ThrowableExceptionMapper}, which shields
 * clients from internal error details.
 *
 * <p>Runs as a {@code @QuarkusTest} so the mapper is exercised through the same
 * classloader the application uses at runtime (and is therefore visible to the
 * coverage agent). The mapper has no dependencies, so it is instantiated
 * directly and its {@link ThrowableExceptionMapper#toResponse} method is called
 * with representative throwables.
 */
@QuarkusTest
class ThrowableExceptionMapperTest {

    private final ThrowableExceptionMapper mapper = new ThrowableExceptionMapper();

    @Test
    @DisplayName("Generic Throwable → 500 with a sanitized INTERNAL_ERROR envelope")
    void genericThrowableIsSanitizedTo500() {
        Response response = mapper.toResponse(
                new RuntimeException("database password is hunter2"));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getEntity())
                .isInstanceOfSatisfying(ApiError.class, error -> {
                    assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
                    assertThat(error.message()).isEqualTo("An unexpected server error occurred.");
                    // Internal detail must not leak to the client.
                    assertThat(error.message()).doesNotContain("hunter2");
                });
    }

    @Test
    @DisplayName("IllegalArgumentException → 400 BAD_REQUEST preserving its message")
    void illegalArgumentBecomes400() {
        Response response = mapper.toResponse(
                new IllegalArgumentException("taskId must not be blank"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity())
                .isInstanceOfSatisfying(ApiError.class, error -> {
                    assertThat(error.code()).isEqualTo("BAD_REQUEST");
                    assertThat(error.message()).isEqualTo("taskId must not be blank");
                });
    }

    @Test
    @DisplayName("WebApplicationException with 4xx → status preserved, REQUEST_ERROR code")
    void clientWebExceptionKeepsStatusAndMessage() {
        Response response = mapper.toResponse(new NotFoundException("no such task"));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getEntity())
                .isInstanceOfSatisfying(ApiError.class, error -> {
                    assertThat(error.code()).isEqualTo("REQUEST_ERROR");
                    assertThat(error.message()).contains("no such task");
                });
    }

    @Test
    @DisplayName("WebApplicationException with 5xx → sanitized INTERNAL_ERROR envelope")
    void serverWebExceptionIsSanitized() {
        Response response = mapper.toResponse(new WebApplicationException(
                "leaky upstream detail", Response.Status.BAD_GATEWAY));

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getEntity())
                .isInstanceOfSatisfying(ApiError.class, error -> {
                    assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
                    assertThat(error.message()).isEqualTo("An unexpected server error occurred.");
                    assertThat(error.message()).doesNotContain("leaky upstream detail");
                });
    }

    @Test
    @DisplayName("Client BadRequestException → 400 REQUEST_ERROR")
    void badRequestExceptionMapsTo400() {
        Response response = mapper.toResponse(new BadRequestException("bad input"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ApiError.class))
                .extracting(ApiError::code)
                .isEqualTo("REQUEST_ERROR");
    }
}
