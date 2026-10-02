package com.footknow.api.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.rate-limit.policies.BASIC.WRITE.requests=2",
		"app.rate-limit.policies.BASIC.WRITE.window=1h", "app.rate-limit.policies.PREMIUM.WRITE.requests=3",
		"app.rate-limit.policies.PREMIUM.WRITE.window=1h", "app.rate-limit.user-tiers[user_premium_test]=PREMIUM"})
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
class RateLimitEndpointsTest {

	@Autowired
	private MockMvc mockMvc;

	@ParameterizedTest
	@ValueSource(strings = {"leagues", "teams", "players", "coaches"})
	void limitsEveryCreateEndpoint(String resource) throws Exception {
		String caller = newCaller();

		mockMvc.perform(create(resource, caller)).andExpect(status().isCreated());

		mockMvc.perform(create(resource, caller)).andExpect(status().isCreated());

		mockMvc.perform(create(resource, caller)).andExpect(status().isTooManyRequests())
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("[1-9][0-9]*")))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
				.andExpect(jsonPath("$.error.message").value("Rate limit exceeded. Please try again later."))
				.andExpect(jsonPath("$.data").doesNotExist()).andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void sharesWriteQuotaAcrossResources() throws Exception {
		String caller = newCaller();

		mockMvc.perform(create("leagues", caller)).andExpect(status().isCreated());

		mockMvc.perform(create("teams", caller)).andExpect(status().isCreated());

		mockMvc.perform(create("players", caller)).andExpect(status().isTooManyRequests());

		mockMvc.perform(create("coaches", caller)).andExpect(status().isTooManyRequests());
	}

	@Test
	void keepsCallersIndependent() throws Exception {
		String firstCaller = newCaller();
		String secondCaller = newCaller();

		mockMvc.perform(create("leagues", firstCaller)).andExpect(status().isCreated());

		mockMvc.perform(create("leagues", firstCaller)).andExpect(status().isCreated());

		mockMvc.perform(create("leagues", firstCaller)).andExpect(status().isTooManyRequests());

		mockMvc.perform(create("leagues", secondCaller)).andExpect(status().isCreated());
	}

	@Test
	void ignoresClientSuppliedTierAndUserHeaders() throws Exception {
		String caller = newCaller();

		mockMvc.perform(create("leagues", caller)).andExpect(status().isCreated());

		mockMvc.perform(create("leagues", caller)).andExpect(status().isCreated());

		mockMvc.perform(
				create("leagues", caller).header("X-Rate-Limit-Tier", "PREMIUM").header("X-User-Id", "another-user"))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void usesServerConfiguredPremiumTier() throws Exception {
		String caller = "user_premium_test";

		for (int i = 0; i < 3; i++) {
			mockMvc.perform(create("leagues", caller)).andExpect(status().isCreated());
		}

		mockMvc.perform(create("leagues", caller)).andExpect(status().isTooManyRequests());
	}

	@Test
	void chargesRequestsThatFailDtoValidation() throws Exception {
		String caller = newCaller();

		mockMvc.perform(create("leagues", caller).content("""
				{"name":""}
				""")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

		mockMvc.perform(create("leagues", caller)).andExpect(status().isCreated());

		mockMvc.perform(create("leagues", caller)).andExpect(status().isTooManyRequests());
	}

	private MockHttpServletRequestBuilder create(String resource, String caller) {
		return post("/api/v1/" + resource)
				.with(jwt().jwt(token -> token.subject(caller).claim("azp", "http://localhost:3000")))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"name":"Example"}
						""");
	}

	private String newCaller() {
		return "user_test_" + UUID.randomUUID();
	}
}