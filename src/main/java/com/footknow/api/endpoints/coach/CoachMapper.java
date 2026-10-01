package com.footknow.api.endpoints.coach;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class CoachMapper {

    public Coach toEntity(UUID id, CreateCoachRequest request) {
        return new Coach(id, request.name());
    }

    public CoachResponse toResponse(Coach entity) {
        return new CoachResponse(entity.id(), entity.name());
    }
}