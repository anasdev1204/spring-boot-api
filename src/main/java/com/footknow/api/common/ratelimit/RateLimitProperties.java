package com.footknow.api.common.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(@NotNull RateLimitTier defaultTier,

		@Min(1) @Max(1_000_000) int maxKeys,

		Map<@NotBlank String, @NotNull RateLimitTier> userTiers,

		@NotEmpty Map<RateLimitTier, @NotEmpty Map<RateLimitCategory, @NotNull @Valid Policy>> policies) {

	public RateLimitProperties {
		userTiers = userTiers == null ? Map.of() : Map.copyOf(userTiers);
	}

	@AssertTrue(message = "Every rate-limit tier must define a policy for every category")
	public boolean isPolicyMatrixComplete() {
		if (policies == null) {
			return false;
		}

		for (RateLimitTier tier : RateLimitTier.values()) {
			Map<RateLimitCategory, Policy> categories = policies.get(tier);

			if (categories == null) {
				return false;
			}

			for (RateLimitCategory category : RateLimitCategory.values()) {
				if (categories.get(category) == null) {
					return false;
				}
			}
		}

		return true;
	}

	public Policy policyFor(RateLimitTier tier, RateLimitCategory category) {
		return policies.get(tier).get(category);
	}

	public record Policy(@Min(1) long requests,

			@NotNull Duration window) {

		@AssertTrue(message = "Rate-limit windows must be between one second and one day")
		public boolean isWindowValid() {
			return window != null && window.compareTo(Duration.ofSeconds(1)) >= 0
					&& window.compareTo(Duration.ofDays(1)) <= 0;
		}
	}
}
