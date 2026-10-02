package com.footknow.api.endpoints.team;

import java.util.UUID;

import com.footknow.api.common.domain.Identifiable;

public record Team(UUID id, String name) implements Identifiable {

}
