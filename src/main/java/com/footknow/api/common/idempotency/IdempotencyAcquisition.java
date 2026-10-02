package com.footknow.api.common.idempotency;

import java.util.Objects;
import java.util.UUID;

public sealed interface IdempotencyAcquisition
		permits IdempotencyAcquisition.Acquired, IdempotencyAcquisition.Replay, IdempotencyAcquisition.Rejected {

	record Acquired(UUID ownerToken) implements IdempotencyAcquisition {

		public Acquired {
			Objects.requireNonNull(ownerToken, "ownerToken is required");
		}
	}

	record Replay(StoredHttpResponse response) implements IdempotencyAcquisition {

		public Replay {
			Objects.requireNonNull(response, "response is required");
		}
	}

	enum Rejected implements IdempotencyAcquisition {
		REQUEST_MISMATCH, IN_PROGRESS, OUTCOME_UNKNOWN, CAPACITY_EXHAUSTED
	}
}