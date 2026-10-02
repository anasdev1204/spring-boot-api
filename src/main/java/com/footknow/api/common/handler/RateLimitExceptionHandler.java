package com.footknow.api.common.handler;

import com.footknow.api.common.error.ApiError;
import com.footknow.api.common.response.ApiResponse;
import com.footknow.api.common.error.ErrorCode;
import com.footknow.api.common.ratelimit.RateLimitExceededException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitExceptionHandler {

	@ExceptionHandler(RateLimitExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException exception) {
		ErrorCode code = exception.errorCode();

		ApiError error = new ApiError(code.name(), code.message(), List.of());

		return ResponseEntity.status(code.status())
				.header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
				.header(HttpHeaders.CACHE_CONTROL, "no-store").body(ApiResponse.failure(error));
	}
}