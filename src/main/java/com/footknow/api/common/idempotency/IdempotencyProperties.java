package com.footknow.api.common.idempotency;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(@Min(1) @Max(100_000) int maxEntries,

		@Min(1) @Max(1_048_576) int maxResponseBytes,

		@NotNull Duration retention) {

	@AssertTrue(message = "Idempotency retention must be between one second and seven days")
	public boolean isRetentionValid() {
		return retention != null && retention.compareTo(Duration.ofSeconds(1)) >= 0
				&& retention.compareTo(Duration.ofDays(7)) <= 0;
	}
}