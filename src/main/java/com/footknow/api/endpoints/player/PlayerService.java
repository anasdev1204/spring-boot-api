package com.footknow.api.endpoints.player;
import com.footknow.api.common.repository.CreateRepository;
import com.footknow.api.common.service.BaseCreateService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PlayerService
        extends BaseCreateService<CreatePlayerRequest, Player> {

    private final PlayerMapper mapper;

    public PlayerService(
            CreateRepository<Player> repository,
            PlayerMapper mapper
    ) {
        super(repository);
        this.mapper = mapper;
    }

    @Override
    protected Player newEntity(UUID id, CreatePlayerRequest command) {
        return mapper.toEntity(id, command);
    }
}