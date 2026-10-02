package com.footknow.api.common.idempotency;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFingerprintTest {

	private final RequestFingerprint fingerprint = new RequestFingerprint();

	@Test
	void identicalRequestsHaveIdenticalFingerprints() {
		String first = calculate("/api/v1/teams", null, "{\"name\":\"Arsenal\"}");

		String second = calculate("/api/v1/teams", null, "{\"name\":\"Arsenal\"}");

		assertThat(first).isEqualTo(second);
		assertThat(first).matches("[0-9a-f]{64}");
	}

	@Test
	void changedBodyChangesFingerprint() {
		assertThat(calculate("/api/v1/teams", null, "{\"name\":\"Arsenal\"}"))
				.isNotEqualTo(calculate("/api/v1/teams", null, "{\"name\":\"Chelsea\"}"));
	}

	@Test
	void whitespaceChangesFingerprintUnderExactByteContract() {
		assertThat(calculate("/api/v1/teams", null, "{\"name\":\"Arsenal\"}"))
				.isNotEqualTo(calculate("/api/v1/teams", null, "{ \"name\": \"Arsenal\" }"));
	}

	@Test
	void changedPathChangesFingerprint() {
		assertThat(calculate("/api/v1/teams", null, "{\"name\":\"Example\"}"))
				.isNotEqualTo(calculate("/api/v1/leagues", null, "{\"name\":\"Example\"}"));
	}

	@Test
	void changedQueryChangesFingerprint() {
		assertThat(calculate("/api/v1/teams", "mode=first", "{}"))
				.isNotEqualTo(calculate("/api/v1/teams", "mode=second", "{}"));
	}

	@Test
	void changedMethodChangesFingerprint() {
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

		assertThat(fingerprint.calculate("POST", "/api/v1/teams", null, body))
				.isNotEqualTo(fingerprint.calculate("PUT", "/api/v1/teams", null, body));
	}

	private String calculate(String uri, String query, String body) {
		return fingerprint.calculate("POST", uri, query, body.getBytes(StandardCharsets.UTF_8));
	}
}