package com.footknow.api.common.repository;

import com.footknow.api.common.domain.Identifiable;

import java.util.Optional;
import java.util.UUID;

/**
 * A repository that supports creating new entities.
 *
 * @param <E>
 *            the type of entity
 */
public interface CreateRepository<E extends Identifiable> {

	/**
	 * Creates a new entity.
	 *
	 * Implementations must reject an existing ID rather than overwrite its entity.
	 * The duplicate check and insertion must be atomic.
	 *
	 * @throws com.footknow.api.common.error.ApiException
	 *             with CONFLICT when the ID already exists
	 */
	E create(E entity);

	/**
	 * Finds an entity by its application-level ID.
	 */
	Optional<E> findById(UUID id);
}