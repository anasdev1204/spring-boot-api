package com.footknow.api.common.service;

import com.footknow.api.common.domain.Identifiable;
import com.footknow.api.common.error.ApiException;
import com.footknow.api.common.error.ErrorCode;
import com.footknow.api.common.repository.CreateRepository;

import java.util.Objects;
import java.util.UUID;

public abstract class BaseCreateService<C, E extends Identifiable> {

	private final CreateRepository<E> repository;

	protected BaseCreateService(CreateRepository<E> repository) {
		this.repository = Objects.requireNonNull(repository, "repository is required");
	}

	public E create(C command) {
		Objects.requireNonNull(command, "command is required");

		UUID id = UUID.randomUUID();

		E entity = Objects.requireNonNull(newEntity(id, command), "newEntity must return an entity");

		if (!id.equals(entity.id())) {
			throw new IllegalStateException("newEntity must preserve the generated ID");
		}

		return repository.create(entity);
	}

	public E findById(UUID id) {
		Objects.requireNonNull(id, "id is required");

		return repository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
	}

	protected abstract E newEntity(UUID id, C command);
}