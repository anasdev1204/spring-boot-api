package com.footknow.api.endpoints.player;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PlayerMapper {

    public Player toEntity(UUID id, CreatePlayerRequest request) {
        return new Player(id, request.name());
    }

    public PlayerResponse toResponse(Player entity) {
        return new PlayerResponse(entity.id(), entity.name());
    }
}