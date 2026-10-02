package com.footknow.api.common.idempotency;

import java.util.Objects;

public record StoredHttpResponse(int status, String contentType, String location, byte[] body) {

	public StoredHttpResponse {
		if (status < 200 || status > 599) {
			throw new IllegalArgumentException("A stored response must have a final HTTP status");
		}

		validateHeader(contentType, "contentType", 256);
		validateHeader(location, "location", 4096);

		body = Objects.requireNonNull(body, "body is required").clone();
	}

	@Override
	public byte[] body() {
		return body.clone();
	}

	public int bodySize() {
		return body.length;
	}

	private static void validateHeader(String value, String name, int maxLength) {
		if (value == null) {
			return;
		}

		if (value.isBlank() || value.length() > maxLength || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
			throw new IllegalArgumentException(name + " is invalid");
		}
	}
}