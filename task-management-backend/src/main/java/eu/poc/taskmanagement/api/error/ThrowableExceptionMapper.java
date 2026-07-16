package eu.poc.taskmanagement.api.error;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Last-resort mapper that prevents leaking internals to clients.
 */
@Slf4j
@Provider
public class ThrowableExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException webException) {
            Response original = webException.getResponse();
            int status = original != null ? original.getStatus() : Response.Status.BAD_REQUEST.getStatusCode();
            String safeMessage = status >= 500 ? "An unexpected server error occurred." : webException.getMessage();
            String code = status >= 500 ? "INTERNAL_ERROR" : "REQUEST_ERROR";
            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ApiError(code, safeMessage))
                    .build();
        }
        if (exception instanceof IllegalArgumentException illegalArgumentException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ApiError("BAD_REQUEST", illegalArgumentException.getMessage()))
                    .build();
        }

        log.error("Unhandled exception while processing request", exception);
        return Response.serverError()
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiError("INTERNAL_ERROR", "An unexpected server error occurred."))
                .build();
    }
}
