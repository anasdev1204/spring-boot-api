package com.footknow.api.common.ratelimit;

public interface RateLimitStore {

    /**
     * Atomically checks and consumes one request for a caller/category.
     *
     * Implementations must not perform a separate, non-atomic
     * "check then increment".
     */
    RateLimitDecision tryAcquire(
            String callerId,
            RateLimitCategory category,
            RateLimitProperties.Policy policy
    );
}