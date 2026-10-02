package com.footknow.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footknow.api.common.domain.Identifiable;
import com.footknow.api.common.repository.CreateRepository;

import com.footknow.api.endpoints.league.League;
import com.footknow.api.endpoints.team.Team;
import com.footknow.api.endpoints.player.Player;
import com.footknow.api.endpoints.coach.Coach;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
class FootballCreateEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CreateRepository<League> leagueRepository;

    @Autowired
    private CreateRepository<Team> teamRepository;

    @Autowired
    private CreateRepository<Player> playerRepository;

    @Autowired
    private CreateRepository<Coach> coachRepository;

    private MockHttpServletRequestBuilder authenticatedPost(String path) {
        return post(path)
                .with(jwt().jwt(token -> token
                        .subject("user_endpoint_test")
                        .claim("azp", "http://localhost:3000")
                ));
        }

    @ParameterizedTest
    @ValueSource(strings = {"leagues", "teams", "players", "coaches"})
    void createsAndStoresResource(String resource) throws Exception {
        MvcResult result = mockMvc.perform(
                        authenticatedPost("/api/v1/" + resource)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"Example"}
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("Example"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        UUID id = UUID.fromString(
                response.path("data").path("id").asText()
        );

        Identifiable stored = switch (resource) {
            case "leagues" -> leagueRepository.findById(id).orElseThrow();
            case "teams" -> teamRepository.findById(id).orElseThrow();
            case "players" -> playerRepository.findById(id).orElseThrow();
            case "coaches" -> coachRepository.findById(id).orElseThrow();
            default -> throw new IllegalArgumentException(resource);
        };

        assertThat(stored.id()).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"leagues", "teams", "players", "coaches"})
    void rejectsBlankName(String resource) throws Exception {
        mockMvc.perform(authenticatedPost("/api/v1/" + resource)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.violations[0].field")
                        .value("name"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"leagues", "teams", "players", "coaches"})
    void rejectsMissingName(String resource) throws Exception {
        mockMvc.perform(authenticatedPost("/api/v1/" + resource)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code")
                        .value("VALIDATION_FAILED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"leagues", "teams", "players", "coaches"})
    void rejectsOversizedName(String resource) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("name", "x".repeat(201))
        );

        mockMvc.perform(authenticatedPost("/api/v1/" + resource)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code")
                        .value("VALIDATION_FAILED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"leagues", "teams", "players", "coaches"})
    void rejectsClientSuppliedId(String resource) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of(
                        "id", UUID.randomUUID().toString(),
                        "name", "Example"
                )
        );

        mockMvc.perform(authenticatedPost("/api/v1/" + resource)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code")
                        .value("MALFORMED_REQUEST"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"leagues", "teams", "players", "coaches"})
    void rejectsUnsupportedContentType(String resource) throws Exception {
        mockMvc.perform(authenticatedPost("/api/v1/" + resource)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Example"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code")
                        .value("UNSUPPORTED_MEDIA_TYPE"));
    }
}