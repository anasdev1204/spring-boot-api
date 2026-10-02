package com.footknow.api.endpoints.team;

import com.footknow.api.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.footknow.api.common.ratelimit.RateLimited;
import com.footknow.api.common.ratelimit.RateLimitCategory;

@RestController
@RequestMapping(value = "/api/v1/teams", produces = MediaType.APPLICATION_JSON_VALUE)
public class TeamController {

	private final TeamService service;
	private final TeamMapper mapper;

	public TeamController(TeamService service, TeamMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@RateLimited(category = RateLimitCategory.WRITE)
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<TeamResponse>> create(@Valid @RequestBody CreateTeamRequest request) {
		Team entity = service.create(request);

		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(mapper.toResponse(entity)));
	}

}
