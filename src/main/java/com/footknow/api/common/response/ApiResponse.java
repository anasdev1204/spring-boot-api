package com.footknow.api.common.response;

import com.footknow.api.common.error.ApiError;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a standardized API response.
 *
 * @param success   indicates whether the request was successful
 * @param data      the data returned in a successful response
 * @param error     the error details in a failed response
 * @param timestamp the timestamp when the response was generated
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        Instant timestamp
) {

    public ApiResponse {
        Objects.requireNonNull(timestamp, "timestamp is required");

        if (success && error != null) {
            throw new IllegalArgumentException(
                    "Successful responses cannot contain an error"
            );
        }

        if (!success && error == null) {
            throw new IllegalArgumentException(
                    "Failed responses must contain an error"
            );
        }

        if (!success && data != null) {
            throw new IllegalArgumentException(
                    "Failed responses cannot contain data"
            );
        }
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                true,
                data,
                null,
                Instant.now()
        );
    }

    public static ApiResponse<Void> failure(ApiError error) {
        return new ApiResponse<>(
                false,
                null,
                Objects.requireNonNull(error, "error is required"),
                Instant.now()
        );
    }
}