package com.footknow.api.common.ratelimit;

import com.footknow.api.common.error.ApiException;
import com.footknow.api.common.error.ErrorCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class InMemoryRateLimitStore implements RateLimitStore {

	private static final long NANOS_PER_SECOND = 1_000_000_000L;

	private final int maxKeys;
	private final LongSupplier ticker;
	private final Map<Key, Window> windows = new HashMap<>();

	public InMemoryRateLimitStore(int maxKeys) {
		this(maxKeys, System::nanoTime);
	}

	// Package-private so tests can control time without sleeping.
	InMemoryRateLimitStore(int maxKeys, LongSupplier ticker) {
		if (maxKeys < 1) {
			throw new IllegalArgumentException("maxKeys must be positive");
		}

		this.maxKeys = maxKeys;
		this.ticker = Objects.requireNonNull(ticker);
	}

	@Override
	public synchronized RateLimitDecision tryAcquire(String callerId, RateLimitCategory category,
			RateLimitProperties.Policy policy) {
		Objects.requireNonNull(callerId);
		Objects.requireNonNull(category);
		Objects.requireNonNull(policy);

		if (callerId.isBlank()) {
			throw new IllegalArgumentException("callerId must not be blank");
		}

		if (policy.requests() < 1 || !policy.isWindowValid()) {
			throw new IllegalArgumentException("Invalid rate-limit policy");
		}

		long now = ticker.getAsLong();
		Key key = new Key(callerId, category);
		Window window = windows.get(key);

		if (window != null && window.expired(now)) {
			windows.remove(key);
			window = null;
		}

		if (window == null) {
			if (windows.size() >= maxKeys) {
				removeExpiredWindows(now);
			}

			if (windows.size() >= maxKeys) {
				throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
			}

			window = new Window(now, policy.window().toNanos(), policy.requests());

			windows.put(key, window);
		}

		if (window.used >= window.limit) {
			return RateLimitDecision.reject(window.retryAfterSeconds(now));
		}

		window.used++;

		return RateLimitDecision.permit();
	}

	private void removeExpiredWindows(long now) {
		windows.entrySet().removeIf(entry -> entry.getValue().expired(now));
	}

	private record Key(String callerId, RateLimitCategory category) {
	}

	private static final class Window {

		private final long startedAtNanos;
		private final long durationNanos;
		private final long limit;

		private long used;

		private Window(long startedAtNanos, long durationNanos, long limit) {
			this.startedAtNanos = startedAtNanos;
			this.durationNanos = durationNanos;
			this.limit = limit;
		}

		private boolean expired(long now) {
			return now - startedAtNanos >= durationNanos;
		}

		private long retryAfterSeconds(long now) {
			long remainingNanos = durationNanos - (now - startedAtNanos);

			long wholeSeconds = remainingNanos / NANOS_PER_SECOND;

			if (remainingNanos % NANOS_PER_SECOND != 0) {
				wholeSeconds++;
			}

			return Math.max(1, wholeSeconds);
		}
	}
}