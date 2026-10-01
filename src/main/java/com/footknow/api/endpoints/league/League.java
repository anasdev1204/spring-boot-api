package com.footknow.api.endpoints.league;

import com.footknow.api.common.domain.Identifiable;

import java.util.UUID;

public record League(
        UUID id,
        String name
) implements Identifiable {
}
