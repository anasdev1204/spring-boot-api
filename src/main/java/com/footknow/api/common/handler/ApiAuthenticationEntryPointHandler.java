package com.footknow.api.common.handler;

import com.footknow.api.common.error.ErrorCode;
import com.footknow.api.common.security.SecurityErrorWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiAuthenticationEntryPointHandler
        implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint =
            new BearerTokenAuthenticationEntryPoint();

    private final SecurityErrorWriter errorWriter;

    public ApiAuthenticationEntryPointHandler(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        // Preserve the standard WWW-Authenticate bearer challenge.
        bearerEntryPoint.commence(request, response, exception);

        errorWriter.write(response, ErrorCode.UNAUTHORIZED);
    }
}