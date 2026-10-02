package com.footknow.api.endpoints.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
		@NotBlank(message = "must not be blank") @Size(max = 200, message = "must not exceed 200 characters") String name) {

}
