package com.footknow.api.common.ratelimit;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

	public RateLimitDecision {
		if (allowed && retryAfterSeconds != 0) {
			throw new IllegalArgumentException("Allowed decisions must have zero retry delay");
		}

		if (!allowed && retryAfterSeconds < 1) {
			throw new IllegalArgumentException("Rejected decisions must have a positive retry delay");
		}
	}

	public static RateLimitDecision permit() {
		return new RateLimitDecision(true, 0);
	}

	public static RateLimitDecision reject(long retryAfterSeconds) {
		return new RateLimitDecision(false, retryAfterSeconds);
	}
}