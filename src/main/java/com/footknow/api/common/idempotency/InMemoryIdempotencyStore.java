package com.footknow.api.common.idempotency;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

public final class InMemoryIdempotencyStore
        implements IdempotencyStore {

    private static final Pattern FINGERPRINT_PATTERN =
            Pattern.compile("[0-9a-f]{64}");

    private final int maxEntries;
    private final int maxResponseBytes;
    private final long retentionNanos;
    private final LongSupplier ticker;

    private final Map<IdempotencyScope, Entry> entries =
            new HashMap<>();

    public InMemoryIdempotencyStore(
            int maxEntries,
            int maxResponseBytes,
            Duration retention
    ) {
        this(
                maxEntries,
                maxResponseBytes,
                retention,
                System::nanoTime
        );
    }

    // Package-private for deterministic tests.
    InMemoryIdempotencyStore(
            int maxEntries,
            int maxResponseBytes,
            Duration retention,
            LongSupplier ticker
    ) {
        if (maxEntries < 1 || maxEntries > 100_000) {
            throw new IllegalArgumentException(
                    "maxEntries is outside the supported range"
            );
        }

        if (maxResponseBytes < 1 || maxResponseBytes > 1_048_576) {
            throw new IllegalArgumentException(
                    "maxResponseBytes is outside the supported range"
            );
        }

        Objects.requireNonNull(retention, "retention is required");

        if (retention.compareTo(Duration.ofSeconds(1)) < 0
                || retention.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException(
                    "retention is outside the supported range"
            );
        }

        this.maxEntries = maxEntries;
        this.maxResponseBytes = maxResponseBytes;
        this.retentionNanos = retention.toNanos();
        this.ticker = Objects.requireNonNull(ticker);
    }

    @Override
    public synchronized IdempotencyAcquisition acquire(
            IdempotencyScope scope,
            String fingerprint
    ) {
        Objects.requireNonNull(scope, "scope is required");
        requireFingerprint(fingerprint);

        long now = ticker.getAsLong();
        Entry entry = entries.get(scope);

        if (entry != null && isExpired(entry, now)) {
            entries.remove(scope);
            entry = null;
        }

        if (entry != null) {
            if (!entry.fingerprint.equals(fingerprint)) {
                return IdempotencyAcquisition.Rejected.REQUEST_MISMATCH;
            }

            return switch (entry.state) {
                case IN_PROGRESS ->
                        IdempotencyAcquisition.Rejected.IN_PROGRESS;

                case COMPLETED ->
                        new IdempotencyAcquisition.Replay(entry.response);

                case OUTCOME_UNKNOWN ->
                        IdempotencyAcquisition.Rejected.OUTCOME_UNKNOWN;
            };
        }

        if (entries.size() >= maxEntries) {
            removeExpiredEntries(now);
        }

        if (entries.size() >= maxEntries) {
            return IdempotencyAcquisition.Rejected.CAPACITY_EXHAUSTED;
        }

        UUID ownerToken = UUID.randomUUID();

        entries.put(scope, new Entry(fingerprint, ownerToken));

        return new IdempotencyAcquisition.Acquired(ownerToken);
    }

    @Override
    public synchronized CompletionResult complete(
            IdempotencyScope scope,
            UUID ownerToken,
            StoredHttpResponse response
    ) {
        Objects.requireNonNull(scope, "scope is required");
        Objects.requireNonNull(ownerToken, "ownerToken is required");
        Objects.requireNonNull(response, "response is required");

        Entry entry = entries.get(scope);

        if (!isCurrentOwner(entry, ownerToken)) {
            return CompletionResult.NOT_OWNER;
        }

        if (response.bodySize() > maxResponseBytes) {
            // Execution may already have changed application state.
            // Do not release the key and allow another execution.
            entry.state = State.OUTCOME_UNKNOWN;

            return CompletionResult.RESPONSE_TOO_LARGE;
        }

        entry.response = response;
        entry.completedAtNanos = ticker.getAsLong();
        entry.state = State.COMPLETED;

        return CompletionResult.COMPLETED;
    }

    @Override
    public synchronized boolean markOutcomeUnknown(
            IdempotencyScope scope,
            UUID ownerToken
    ) {
        Objects.requireNonNull(scope, "scope is required");
        Objects.requireNonNull(ownerToken, "ownerToken is required");

        Entry entry = entries.get(scope);

        if (!isCurrentOwner(entry, ownerToken)) {
            return false;
        }

        entry.state = State.OUTCOME_UNKNOWN;

        return true;
    }

    private boolean isCurrentOwner(
            Entry entry,
            UUID ownerToken
    ) {
        return entry != null
                && entry.state == State.IN_PROGRESS
                && entry.ownerToken.equals(ownerToken);
    }

    private boolean isExpired(Entry entry, long now) {
        return entry.state == State.COMPLETED
                && now - entry.completedAtNanos >= retentionNanos;
    }

    private void removeExpiredEntries(long now) {
        entries.entrySet().removeIf(
                item -> isExpired(item.getValue(), now)
        );
    }

    private void requireFingerprint(String fingerprint) {
        if (fingerprint == null
                || !FINGERPRINT_PATTERN.matcher(fingerprint).matches()) {
            throw new IllegalArgumentException(
                    "A lowercase SHA-256 fingerprint is required"
            );
        }
    }

    private enum State {
        IN_PROGRESS,
        COMPLETED,
        OUTCOME_UNKNOWN
    }

    private static final class Entry {

        private final String fingerprint;
        private final UUID ownerToken;

        private State state = State.IN_PROGRESS;
        private StoredHttpResponse response;
        private long completedAtNanos;

        private Entry(
                String fingerprint,
                UUID ownerToken
        ) {
            this.fingerprint = fingerprint;
            this.ownerToken = ownerToken;
        }
    }
}