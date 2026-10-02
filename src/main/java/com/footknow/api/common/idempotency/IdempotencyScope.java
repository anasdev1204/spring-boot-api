package com.footknow.api.common.idempotency;

public record IdempotencyScope(
        String callerId,
        String operation,
        String key
) {

    public IdempotencyScope {
        requireNonBlank(callerId, "callerId");
        requireNonBlank(operation, "operation");
        requireNonBlank(key, "key");
    }

    private static void requireNonBlank(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
    }
}