package com.footknow.api.endpoints.player;

import com.footknow.api.common.domain.Identifiable;

import java.util.UUID;

public record Player(UUID id, String name) implements Identifiable {
}
