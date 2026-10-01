package com.footknow.api.endpoints.coach;

import java.util.UUID;

public record CoachResponse(
    UUID id,
    String name
) {
}
