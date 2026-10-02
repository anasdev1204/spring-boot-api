package com.footknow.api.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
class ClerkSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @ParameterizedTest
    @ValueSource(strings = {"leagues", "teams", "players", "coaches"})
    void rejectsMissingAuthentication(String resource) throws Exception {
        mockMvc.perform(post("/api/v1/" + resource)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Example"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        startsWith("Bearer")
                ))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message")
                        .value("Authentication is required."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        when(jwtDecoder.decode("invalid-token"))
                .thenThrow(new BadJwtException("Invalid token"));

        mockMvc.perform(post("/api/v1/leagues")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer invalid-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Example"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message")
                        .value("Authentication is required."));
    }

    @Test
    void deniesAuthenticatedRequestsOutsideAllowedRoutes() throws Exception {
        mockMvc.perform(get("/internal/not-exposed")
                        .with(jwt().jwt(token -> token.subject("user_test"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void permitsLivenessWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void permitsPreflightFromConfiguredFrontend() throws Exception {
        mockMvc.perform(options("/api/v1/leagues")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                "POST"
                        )
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "authorization,content-type"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:3000"
                ));
    }

    @Test
    void rejectsPreflightFromUnknownFrontend() throws Exception {
        mockMvc.perform(options("/api/v1/leagues")
                        .header(
                                HttpHeaders.ORIGIN,
                                "https://untrusted.example"
                        )
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                "POST"
                        ))
                .andExpect(status().isForbidden());
    }
}