package com.footknow.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.footknow.api.common.error.ApiError;
import com.footknow.api.common.response.ApiResponse;
import com.footknow.api.common.error.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class SecurityErrorWriter {

	private final ObjectMapper objectMapper;

	public SecurityErrorWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		ApiError error = new ApiError(errorCode.name(), errorCode.message(), List.of());

		response.setStatus(errorCode.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

		objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(error));
	}
}