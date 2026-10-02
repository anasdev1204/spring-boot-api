package com.footknow.api.common.idempotency;

import com.footknow.api.common.error.ApiException;
import com.footknow.api.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyValidatorTest {

	private final IdempotencyKeyValidator validator = new IdempotencyKeyValidator();

	@Test
	void acceptsUuidKey() {
		String key = "71942644-1e8a-42c8-a4fb-e26108c3a477";

		MockHttpServletRequest request = requestWithKey(key);

		assertThat(validator.requireKey(request)).isEqualTo(key);
	}

	@Test
	void preservesCase() {
		String key = "RequestKey_123456789";

		assertThat(validator.requireKey(requestWithKey(key))).isEqualTo(key);
	}

	@Test
	void acceptsBoundaryLengths() {
		assertThat(validator.requireKey(requestWithKey("a".repeat(16)))).hasSize(16);

		assertThat(validator.requireKey(requestWithKey("a".repeat(128)))).hasSize(128);
	}

	@Test
	void rejectsMissingKey() {
		MockHttpServletRequest request = new MockHttpServletRequest();

		assertThatThrownBy(() -> validator.requireKey(request)).isInstanceOfSatisfying(ApiException.class,
				exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "short", " key_with_spaces_123 ", "key_with_comma,12345", "key/with/slashes1234"})
	void rejectsInvalidKeys(String key) {
		assertInvalid(requestWithKey(key));
	}

	@Test
	void rejectsOversizedKey() {
		assertInvalid(requestWithKey("a".repeat(129)));
	}

	@Test
	void rejectsRepeatedHeader() {
		MockHttpServletRequest request = requestWithKey("first_valid_key_123");

		request.addHeader(IdempotencyKeyValidator.HEADER_NAME, "second_valid_key_123");

		assertInvalid(request);
	}

	private MockHttpServletRequest requestWithKey(String key) {
		MockHttpServletRequest request = new MockHttpServletRequest();

		request.addHeader(IdempotencyKeyValidator.HEADER_NAME, key);

		return request;
	}

	private void assertInvalid(MockHttpServletRequest request) {
		assertThatThrownBy(() -> validator.requireKey(request)).isInstanceOfSatisfying(ApiException.class,
				exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_INVALID));
	}
}