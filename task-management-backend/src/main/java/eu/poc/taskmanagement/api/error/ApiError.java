package eu.poc.taskmanagement.api.error;

/**
 * Standard API error envelope returned by non-2xx responses.
 *
 * @param code    stable machine-readable code
 * @param message human-readable message safe to expose to API clients
 */
public record ApiError(String code, String message) {
}
