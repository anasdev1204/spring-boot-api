package com.footknow.api.common.idempotency;

import java.util.UUID;

public interface IdempotencyStore {

    /**
     * Atomically reserves a previously unused scope or reports its state.
     */
    IdempotencyAcquisition acquire(
            IdempotencyScope scope,
            String fingerprint
    );

    /**
     * Completes a reservation only if it is still in progress and
     * owned by the supplied token.
     */
    CompletionResult complete(
            IdempotencyScope scope,
            UUID ownerToken,
            StoredHttpResponse response
    );

    /**
     * Blocks re-execution when the current owner cannot safely
     * confirm the operation's outcome.
     */
    boolean markOutcomeUnknown(
            IdempotencyScope scope,
            UUID ownerToken
    );

    enum CompletionResult {
        COMPLETED,
        NOT_OWNER,
        RESPONSE_TOO_LARGE
    }
}