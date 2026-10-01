package com.footknow.api.common.error;

import java.util.Objects;

/**
 * Represents a custom exception for API errors.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public ApiException(ErrorCode errorCode, Throwable cause) {
        super(
                Objects.requireNonNull(
                        errorCode,
                        "errorCode is required"
                ).message(),
                cause
        );

        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}