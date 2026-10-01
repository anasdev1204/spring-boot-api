package com.footknow.api.endpoints.coach;

import com.footknow.api.common.service.BaseCreateService;

import org.springframework.stereotype.Service;

import com.footknow.api.common.repository.CreateRepository;

@Service
public class CoachService 
    extends BaseCreateService<CreateCoachRequest, Coach> {

    private final CoachMapper mapper;

    public CoachService(
            CreateRepository<Coach> repository,
            CoachMapper mapper
    ) {
        super(repository);
        this.mapper = mapper;
    }

    @Override
    protected Coach newEntity(java.util.UUID id, CreateCoachRequest command) {
        return mapper.toEntity(id, command);
    }
    
}
