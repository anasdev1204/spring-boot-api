package com.footknow.api.endpoints.team;

import com.footknow.api.common.repository.CreateRepository;
import com.footknow.api.common.service.BaseCreateService;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TeamService extends BaseCreateService<CreateTeamRequest, Team> {

	private final TeamMapper mapper;

	public TeamService(CreateRepository<Team> repository, TeamMapper mapper) {
		super(repository);
		this.mapper = mapper;
	}

	@Override
	protected Team newEntity(UUID id, CreateTeamRequest command) {
		return mapper.toEntity(id, command);
	}
}
