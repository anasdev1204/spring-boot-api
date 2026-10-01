package com.footknow.api.common.error;

/**
 * Represents a violation of a specific field in a request or input.
 *
 * @param field   the name of the field that has the violation
 * @param message the message describing the violation
 */
public record FieldViolation(
        String field,
        String message
) {
}