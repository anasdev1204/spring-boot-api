package com.footknow.api.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Represents an error response for an API request.
 *
 * @param code
 *            the error code representing the type of error
 * @param message
 *            the message describing the error
 * @param violations
 *            a list of field violations associated with the error
 */
public record ApiError(String code, String message,

		@JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldViolation> violations) {

	public ApiError {
		violations = violations == null ? List.of() : List.copyOf(violations);
	}
}