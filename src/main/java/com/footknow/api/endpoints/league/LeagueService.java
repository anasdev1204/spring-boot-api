package com.footknow.api.endpoints.league;
import com.footknow.api.common.repository.CreateRepository;
import com.footknow.api.common.service.BaseCreateService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LeagueService
        extends BaseCreateService<CreateLeagueRequest, League> {

    private final LeagueMapper mapper;

    public LeagueService(
            CreateRepository<League> repository,
            LeagueMapper mapper
    ) {
        super(repository);
        this.mapper = mapper;
    }

    @Override
    protected League newEntity(UUID id, CreateLeagueRequest command) {
        return mapper.toEntity(id, command);
    }
}