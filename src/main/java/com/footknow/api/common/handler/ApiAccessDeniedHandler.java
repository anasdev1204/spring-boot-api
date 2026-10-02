package com.footknow.api.common.handler;

import com.footknow.api.common.error.ErrorCode;
import com.footknow.api.common.security.SecurityErrorWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

	private final BearerTokenAccessDeniedHandler bearerDeniedHandler = new BearerTokenAccessDeniedHandler();

	private final SecurityErrorWriter errorWriter;

	public ApiAccessDeniedHandler(SecurityErrorWriter errorWriter) {
		this.errorWriter = errorWriter;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
			throws IOException {
		bearerDeniedHandler.handle(request, response, exception);

		errorWriter.write(response, ErrorCode.FORBIDDEN);
	}
}