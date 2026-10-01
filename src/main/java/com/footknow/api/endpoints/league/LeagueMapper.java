package com.footknow.api.endpoints.league;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LeagueMapper {

    public League toEntity(UUID id, CreateLeagueRequest request) {
        return new League(id, request.name());
    }

    public LeagueResponse toResponse(League entity) {
        return new LeagueResponse(entity.id(), entity.name());
    }
}