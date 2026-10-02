package com.footknow.api.common.idempotency;

public record IdempotencyRequestContext(
        IdempotencyScope scope,
        String fingerprint
) {

    public static final String ATTRIBUTE =
            IdempotencyRequestContext.class.getName();
}