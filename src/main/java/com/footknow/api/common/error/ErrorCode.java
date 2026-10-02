package com.footknow.api.common.error;

import org.springframework.http.HttpStatus;

/**
 * Represents a standardized error code for API responses.
 */
public enum ErrorCode {

	BAD_REQUEST(HttpStatus.BAD_REQUEST, "The request is invalid."),

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed."),

	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request body is missing or invalid."),

	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required."),

	FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this operation."),

	NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),

	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "The HTTP method is not supported for this endpoint."),

	CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current resource state."),

	PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The request payload is too large."),

	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The request content type is not supported."),

	RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Please try again later."),

	REQUEST_REJECTED(HttpStatus.BAD_REQUEST, "The request could not be processed."),

	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred."),

	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
			"The service is temporarily unavailable. Please try again later."),

	IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "An Idempotency-Key header is required."),

	IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "The Idempotency-Key header is invalid."),

	IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "The idempotency key has already been used with a different request."),

	IDEMPOTENCY_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT,
			"A request with this idempotency key is still being processed."),

	IDEMPOTENCY_OUTCOME_UNKNOWN(
        HttpStatus.CONFLICT,
        "The outcome of the original request cannot currently be confirmed."
	);

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus status() {
		return status;
	}

	public String message() {
		return message;
	}
}