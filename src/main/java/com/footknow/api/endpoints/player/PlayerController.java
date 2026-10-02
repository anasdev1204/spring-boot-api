package com.footknow.api.endpoints.player;

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

import com.footknow.api.common.idempotency.Idempotent;

@RestController
@RequestMapping(value = "/api/v1/players", produces = MediaType.APPLICATION_JSON_VALUE)
public class PlayerController {

	private final PlayerService service;
	private final PlayerMapper mapper;

	public PlayerController(PlayerService service, PlayerMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

        @Idempotent (operation = "player.create.v1")
	@RateLimited(category = RateLimitCategory.WRITE)
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<PlayerResponse>> create(@Valid @RequestBody CreatePlayerRequest request) {
		Player entity = service.create(request);

		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(mapper.toResponse(entity)));
	}
}