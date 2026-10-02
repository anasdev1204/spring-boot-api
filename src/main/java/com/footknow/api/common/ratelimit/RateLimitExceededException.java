package com.footknow.api.common.ratelimit;

import com.footknow.api.common.error.ApiException;
import com.footknow.api.common.error.ErrorCode;

public final class RateLimitExceededException extends ApiException {

	private final long retryAfterSeconds;

	public RateLimitExceededException(long retryAfterSeconds) {
		super(ErrorCode.RATE_LIMIT_EXCEEDED);

		if (retryAfterSeconds < 1) {
			throw new IllegalArgumentException("retryAfterSeconds must be positive");
		}

		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}