package com.footknow.api.common.repository;

import com.footknow.api.common.domain.Identifiable;
import com.footknow.api.common.error.ApiException;
import com.footknow.api.common.error.ErrorCode;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Development-only repository.
 *
 * Entities must be immutable because this implementation stores and returns
 * their references without defensive copying.
 */
public class InMemoryCreateRepository<E extends Identifiable> implements CreateRepository<E> {

	private final ConcurrentMap<UUID, E> entities = new ConcurrentHashMap<>();

	@Override
	public E create(E entity) {
		Objects.requireNonNull(entity, "entity is required");

		UUID id = Objects.requireNonNull(entity.id(), "entity ID is required");

		E existing = entities.putIfAbsent(id, entity);

		if (existing != null) {
			throw new ApiException(ErrorCode.CONFLICT);
		}

		return entity;
	}

	@Override
	public Optional<E> findById(UUID id) {
		Objects.requireNonNull(id, "id is required");

		return Optional.ofNullable(entities.get(id));
	}
}