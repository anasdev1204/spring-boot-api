package com.footknow.api.common.repository;

import com.footknow.api.common.domain.Identifiable;
import com.footknow.api.common.error.ApiException;
import com.footknow.api.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryCreateRepositoryTest {

	private final CreateRepository<TestEntity> repository = new InMemoryCreateRepository<>();

	@Test
	void createsAndFindsEntity() {
		TestEntity entity = new TestEntity(UUID.randomUUID(), "Example");

		TestEntity created = repository.create(entity);

		assertThat(created).isEqualTo(entity);
		assertThat(repository.findById(entity.id())).contains(entity);
	}

	@Test
	void returnsEmptyWhenEntityDoesNotExist() {
		assertThat(repository.findById(UUID.randomUUID())).isEmpty();
	}

	@Test
	void rejectsDuplicateIdWithoutReplacingOriginalEntity() {
		UUID id = UUID.randomUUID();

		TestEntity original = new TestEntity(id, "Original");
		TestEntity replacement = new TestEntity(id, "Replacement");

		repository.create(original);

		assertThatThrownBy(() -> repository.create(replacement)).isInstanceOfSatisfying(ApiException.class,
				exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

		assertThat(repository.findById(id)).contains(original);
	}

	@Test
	void keepsRepositoryInstancesIsolated() {
		CreateRepository<TestEntity> otherRepository = new InMemoryCreateRepository<>();

		TestEntity entity = new TestEntity(UUID.randomUUID(), "Example");

		repository.create(entity);

		assertThat(otherRepository.findById(entity.id())).isEmpty();
	}

	@Test
	void rejectsEntityWithoutId() {
		TestEntity entity = new TestEntity(null, "Example");

		assertThatThrownBy(() -> repository.create(entity)).isInstanceOf(NullPointerException.class)
				.hasMessage("entity ID is required");
	}

	@Test
	void allowsOnlyOneCreationForConcurrentDuplicateIds() throws Exception {

		UUID id = UUID.randomUUID();
		int attempts = 32;

		List<Future<Boolean>> results = new ArrayList<>();

		try (ExecutorService executor = Executors.newFixedThreadPool(8)) {

			for (int index = 0; index < attempts; index++) {
				int attempt = index;

				results.add(executor.submit(() -> {
					TestEntity entity = new TestEntity(id, "Attempt-" + attempt);

					try {
						repository.create(entity);
						return true;
					} catch (ApiException exception) {
						if (exception.errorCode() != ErrorCode.CONFLICT) {
							throw exception;
						}

						return false;
					}
				}));
			}

			int successfulCreations = 0;

			for (Future<Boolean> result : results) {
				if (result.get(5, TimeUnit.SECONDS)) {
					successfulCreations++;
				}
			}

			assertThat(successfulCreations).isEqualTo(1);
			assertThat(repository.findById(id)).isPresent();
		}
	}

	private record TestEntity(UUID id, String name) implements Identifiable {
	}
}