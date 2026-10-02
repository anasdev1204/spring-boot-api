package com.footknow.api.common.idempotency;

import com.footknow.api.endpoints.league.CreateLeagueRequest;
import com.footknow.api.endpoints.league.League;
import com.footknow.api.endpoints.league.LeagueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.rate-limit.policies.BASIC.WRITE.requests=2",
		"app.rate-limit.policies.BASIC.WRITE.window=1h", "app.idempotency.max-request-bytes=1024",
		"app.idempotency.max-response-bytes=512"})
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
class IdempotencyEndpointsTest {

	private static final String BODY = """
			{"name":"Example"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoSpyBean
	private LeagueService leagueService;

	@ParameterizedTest
	@ValueSource(strings = {"leagues", "teams", "players", "coaches"})
	void replaysExactResponseForEveryResource(String resource) throws Exception {
		String caller = caller();
		String key = key();

		MvcResult first = mockMvc.perform(request(resource, caller, key, BODY)).andExpect(status().isCreated())
				.andExpect(header().string("Idempotency-Replayed", "false"))
				.andExpect(jsonPath("$.success").value(true)).andReturn();

		mockMvc.perform(request(resource, caller, key, BODY)).andExpect(status().isCreated())
				.andExpect(header().string("Idempotency-Replayed", "true"))
				.andExpect(content().bytes(first.getResponse().getContentAsByteArray()));
	}

	@Test
	void invokesCreationOnceAndChargesReplaysToRateLimit() throws Exception {
		String caller = caller();
		String key = key();

		mockMvc.perform(request("leagues", caller, key, BODY)).andExpect(status().isCreated());

		mockMvc.perform(request("leagues", caller, key, BODY)).andExpect(status().isCreated())
				.andExpect(header().string("Idempotency-Replayed", "true"));

		mockMvc.perform(request("leagues", caller, key, BODY)).andExpect(status().isTooManyRequests());

		verify(leagueService, times(1)).create(any(CreateLeagueRequest.class));
	}

	@Test
	void rejectsSameKeyWithDifferentBody() throws Exception {
		String caller = caller();
		String key = key();

		mockMvc.perform(request("leagues", caller, key, BODY)).andExpect(status().isCreated());

		mockMvc.perform(request("leagues", caller, key, """
				{"name":"Different"}
				""")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));

		verify(leagueService, times(1)).create(any(CreateLeagueRequest.class));
	}

	@Test
	void exactByteContractTreatsWhitespaceAsDifferent() throws Exception {
		String caller = caller();
		String key = key();

		mockMvc.perform(request("leagues", caller, key, "{\"name\":\"Example\"}")).andExpect(status().isCreated());

		mockMvc.perform(request("leagues", caller, key, "{ \"name\": \"Example\" }")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
	}

	@Test
	void validationFailureDoesNotReserveTheKey() throws Exception {
		String caller = caller();
		String key = key();

		mockMvc.perform(request("leagues", caller, key, "{\"name\":\"\"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

		mockMvc.perform(request("leagues", caller, key, BODY)).andExpect(status().isCreated())
				.andExpect(header().string("Idempotency-Replayed", "false"));

		verify(leagueService, times(1)).create(any(CreateLeagueRequest.class));
	}

	@Test
	void requiresAKey() throws Exception {
		mockMvc.perform(request("leagues", caller(), null, BODY)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));

		verify(leagueService, never()).create(any(CreateLeagueRequest.class));
	}

	@Test
	void rejectsOversizedRequestBeforeCreation() throws Exception {
		String body = "{\"name\":\"" + "x".repeat(1500) + "\"}";

		mockMvc.perform(request("leagues", caller(), key(), body)).andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));

		verify(leagueService, never()).create(any(CreateLeagueRequest.class));
	}

	@Test
	void sameKeyIsIndependentAcrossCallers() throws Exception {
		String key = key();

		mockMvc.perform(request("leagues", caller(), key, BODY)).andExpect(status().isCreated());

		mockMvc.perform(request("leagues", caller(), key, BODY)).andExpect(status().isCreated());

		verify(leagueService, times(2)).create(any(CreateLeagueRequest.class));
	}

	@Test
	void concurrentRetryDoesNotExecuteAgain() throws Exception {
		String caller = caller();
		String key = key();

		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		doAnswer(invocation -> {
			entered.countDown();

			if (!release.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Test timed out");
			}

			return invocation.callRealMethod();
		}).when(leagueService).create(any(CreateLeagueRequest.class));

		try (var executor = Executors.newSingleThreadExecutor()) {
			var first = executor.submit(() -> mockMvc.perform(request("leagues", caller, key, BODY))
					.andExpect(status().isCreated()).andReturn());

			try {
				assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

				mockMvc.perform(request("leagues", caller, key, BODY)).andExpect(status().isConflict())
						.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_REQUEST_IN_PROGRESS"));
			} finally {
				release.countDown();
			}

			first.get(10, TimeUnit.SECONDS);
		}

		verify(leagueService, times(1)).create(any(CreateLeagueRequest.class));
	}

	@Test
	void executionFailureBlocksAutomaticReexecution() throws Exception {
		String caller = caller();
		String key = key();

		doThrow(new IllegalStateException("Simulated execution failure")).when(leagueService)
				.create(any(CreateLeagueRequest.class));

		mockMvc.perform(request("leagues", caller, key, BODY)).andExpect(status().isInternalServerError());

		mockMvc.perform(request("leagues", caller, key, BODY)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_OUTCOME_UNKNOWN"));

		verify(leagueService, times(1)).create(any(CreateLeagueRequest.class));
	}

	@Test
	void oversizedResponseBlocksAutomaticReexecution() throws Exception {
		String caller = caller();
		String key = key();

		doReturn(new League(UUID.randomUUID(), "x".repeat(2000))).when(leagueService)
				.create(any(CreateLeagueRequest.class));

		mockMvc.perform(request("leagues", caller, key, BODY)).andExpect(status().isInternalServerError());

		mockMvc.perform(request("leagues", caller, key, BODY)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_OUTCOME_UNKNOWN"));

		verify(leagueService, times(1)).create(any(CreateLeagueRequest.class));
	}

	private MockHttpServletRequestBuilder request(String resource, String caller, String key, String body) {
		MockHttpServletRequestBuilder builder = post("/api/v1/" + resource)
				.with(jwt().jwt(token -> token.subject(caller).claim("azp", "http://localhost:3000")))
				.contentType(MediaType.APPLICATION_JSON).content(body);

		if (key != null) {
			builder.header("Idempotency-Key", key);
		}

		return builder;
	}

	private String caller() {
		return "user_idempotency_" + UUID.randomUUID();
	}

	private String key() {
		return UUID.randomUUID().toString();
	}
}