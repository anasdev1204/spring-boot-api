package com.footknow.api.endpoints.team;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class TeamMapper {
    public Team toEntity(UUID id, CreateTeamRequest request) {
        return new Team(id, request.name());
    }

    public TeamResponse toResponse(Team entity) {
        return new TeamResponse(entity.id(), entity.name());
    }
}
