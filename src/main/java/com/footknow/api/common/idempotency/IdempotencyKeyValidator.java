package com.footknow.api.common.idempotency;

import com.footknow.api.common.error.ApiException;
import com.footknow.api.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Enumeration;
import java.util.regex.Pattern;

@Component
public class IdempotencyKeyValidator {

	public static final String HEADER_NAME = "Idempotency-Key";

	private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]{16,128}");

	public String requireKey(HttpServletRequest request) {
		Enumeration<String> values = request.getHeaders(HEADER_NAME);

		if (values == null || !values.hasMoreElements()) {
			throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
		}

		String key = values.nextElement();

		if (values.hasMoreElements()) {
			throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
		}

		if (key == null || key.length() < 16 || key.length() > 128 || !VALID_KEY.matcher(key).matches()) {
			throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
		}

		return key;
	}
}