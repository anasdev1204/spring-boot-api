package com.footknow.api.endpoints.coach;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.footknow.api.common.response.ApiResponse;

import com.footknow.api.common.ratelimit.RateLimited;
import com.footknow.api.common.ratelimit.RateLimitCategory;

import com.footknow.api.common.idempotency.Idempotent;

@RestController
@RequestMapping(value = "/api/v1/coaches", produces = MediaType.APPLICATION_JSON_VALUE)
public class CoachController {

	private final CoachService service;
	private final CoachMapper mapper;

	public CoachController(CoachService service, CoachMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@Idempotent(operation = "coach.create.v1")
	@RateLimited(category = RateLimitCategory.WRITE)
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<CoachResponse>> create(@Valid @RequestBody CreateCoachRequest request) {
		Coach entity = service.create(request);

		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(mapper.toResponse(entity)));
	}
}
