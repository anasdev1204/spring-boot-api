package com.footknow.api.endpoints.coach;

import com.footknow.api.common.domain.Identifiable;

import java.util.UUID;

public record Coach(UUID id, String name) implements Identifiable {
}