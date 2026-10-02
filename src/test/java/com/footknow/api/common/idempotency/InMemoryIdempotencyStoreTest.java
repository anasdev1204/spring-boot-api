package com.footknow.api.common.idempotency;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryIdempotencyStoreTest {

	private static final String FINGERPRINT = "a".repeat(64);
	private static final String OTHER_FINGERPRINT = "b".repeat(64);

	private final AtomicLong now = new AtomicLong();

	private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(10, 1024, Duration.ofMinutes(1),
			now::get);

	@Test
	void reservesFirstRequestAndBlocksConcurrentRetry() {
		IdempotencyScope scope = scope("user_a", "team.create.v1", "key");

		IdempotencyAcquisition first = store.acquire(scope, FINGERPRINT);

		assertThat(first).isInstanceOf(IdempotencyAcquisition.Acquired.class);

		assertThat(store.acquire(scope, FINGERPRINT)).isEqualTo(IdempotencyAcquisition.Rejected.IN_PROGRESS);
	}

	@Test
	void rejectsDifferentFingerprint() {
		IdempotencyScope scope = scope("user_a", "team.create.v1", "key");

		store.acquire(scope, FINGERPRINT);

		assertThat(store.acquire(scope, OTHER_FINGERPRINT)).isEqualTo(IdempotencyAcquisition.Rejected.REQUEST_MISMATCH);
	}

	@Test
	void replaysCompletedResponse() {
		IdempotencyScope scope = scope("user_a", "team.create.v1", "key");
		UUID owner = acquireOwner(store, scope);

		StoredHttpResponse original = response("""
				{"success":true,"data":{"name":"Arsenal"}}
				""");

		assertThat(store.complete(scope, owner, original)).isEqualTo(IdempotencyStore.CompletionResult.COMPLETED);

		IdempotencyAcquisition result = store.acquire(scope, FINGERPRINT);

		assertThat(result).isInstanceOf(IdempotencyAcquisition.Replay.class);

		StoredHttpResponse replay = ((IdempotencyAcquisition.Replay) result).response();

		assertThat(replay.status()).isEqualTo(201);
		assertThat(replay.contentType()).isEqualTo("application/json");
		assertThat(replay.body()).containsExactly(original.body());

		assertThat(store.acquire(scope, OTHER_FINGERPRINT)).isEqualTo(IdempotencyAcquisition.Rejected.REQUEST_MISMATCH);
	}

	@Test
	void rejectsWrongOwner() {
		IdempotencyScope scope = scope("user_a", "team.create.v1", "key");

		acquireOwner(store, scope);

		assertThat(store.complete(scope, UUID.randomUUID(), response("{}")))
				.isEqualTo(IdempotencyStore.CompletionResult.NOT_OWNER);

		assertThat(store.acquire(scope, FINGERPRINT)).isEqualTo(IdempotencyAcquisition.Rejected.IN_PROGRESS);
	}

	@Test
	void startsRetentionAtCompletionAndRejectsStaleOwner() {
		IdempotencyScope scope = scope("user_a", "team.create.v1", "key");
		UUID firstOwner = acquireOwner(store, scope);

		now.set(Duration.ofSeconds(55).toNanos());

		store.complete(scope, firstOwner, response("{}"));

		now.set(Duration.ofSeconds(60).toNanos());

		assertThat(store.acquire(scope, FINGERPRINT)).isInstanceOf(IdempotencyAcquisition.Replay.class);

		now.set(Duration.ofSeconds(115).toNanos());

		UUID secondOwner = acquireOwner(store, scope);

		assertThat(secondOwner).isNotEqualTo(firstOwner);

		assertThat(store.complete(scope, firstOwner, response("{}")))
				.isEqualTo(IdempotencyStore.CompletionResult.NOT_OWNER);
	}

	@Test
	void doesNotExpireInProgressOrUnknownOutcomes() {
		IdempotencyScope scope = scope("user_a", "team.create.v1", "key");
		UUID owner = acquireOwner(store, scope);

		now.set(Duration.ofDays(1).toNanos());

		assertThat(store.acquire(scope, FINGERPRINT)).isEqualTo(IdempotencyAcquisition.Rejected.IN_PROGRESS);

		assertThat(store.markOutcomeUnknown(scope, owner)).isTrue();

		now.set(Duration.ofDays(2).toNanos());

		assertThat(store.acquire(scope, FINGERPRINT)).isEqualTo(IdempotencyAcquisition.Rejected.OUTCOME_UNKNOWN);

		assertThat(store.complete(scope, owner, response("{}"))).isEqualTo(IdempotencyStore.CompletionResult.NOT_OWNER);
	}

	@Test
	void failsClosedAtCapacityAndReclaimsExpiredCompletion() {
		InMemoryIdempotencyStore smallStore = new InMemoryIdempotencyStore(1, 1024, Duration.ofSeconds(1), now::get);

		IdempotencyScope first = scope("user_a", "team.create.v1", "first");
		IdempotencyScope second = scope("user_a", "team.create.v1", "second");

		UUID owner = acquireOwner(smallStore, first);

		assertThat(smallStore.acquire(second, FINGERPRINT))
				.isEqualTo(IdempotencyAcquisition.Rejected.CAPACITY_EXHAUSTED);

		smallStore.complete(first, owner, response("{}"));

		assertThat(smallStore.acquire(first, FINGERPRINT)).isInstanceOf(IdempotencyAcquisition.Replay.class);

		assertThat(smallStore.acquire(second, FINGERPRINT))
				.isEqualTo(IdempotencyAcquisition.Rejected.CAPACITY_EXHAUSTED);

		now.set(Duration.ofSeconds(1).toNanos());

		assertThat(smallStore.acquire(second, FINGERPRINT)).isInstanceOf(IdempotencyAcquisition.Acquired.class);
	}

	@Test
	void oversizedResponseBlocksReexecution() {
		InMemoryIdempotencyStore smallStore = new InMemoryIdempotencyStore(10, 8, Duration.ofMinutes(1), now::get);

		IdempotencyScope scope = scope("user_a", "team.create.v1", "key");
		UUID owner = acquireOwner(smallStore, scope);

		assertThat(smallStore.complete(scope, owner, response("x".repeat(9))))
				.isEqualTo(IdempotencyStore.CompletionResult.RESPONSE_TOO_LARGE);

		assertThat(smallStore.acquire(scope, FINGERPRINT)).isEqualTo(IdempotencyAcquisition.Rejected.OUTCOME_UNKNOWN);
	}

	@Test
	void responseSnapshotDefensivelyCopiesBody() {
		byte[] original = "hello".getBytes(StandardCharsets.UTF_8);

		StoredHttpResponse response = new StoredHttpResponse(200, "text/plain", null, original);

		original[0] = 'X';

		assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("hello");

		byte[] returned = response.body();
		returned[0] = 'Y';

		assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("hello");
	}

	@Test
	void scopesKeysByCallerAndOperation() {
		assertThat(store.acquire(scope("user_a", "team.create.v1", "same"), FINGERPRINT))
				.isInstanceOf(IdempotencyAcquisition.Acquired.class);

		assertThat(store.acquire(scope("user_b", "team.create.v1", "same"), FINGERPRINT))
				.isInstanceOf(IdempotencyAcquisition.Acquired.class);

		assertThat(store.acquire(scope("user_a", "league.create.v1", "same"), FINGERPRINT))
				.isInstanceOf(IdempotencyAcquisition.Acquired.class);
	}

	@Test
	void onlyOneConcurrentRequestAcquiresOwnership() throws Exception {
		IdempotencyScope scope = scope("user_a", "team.create.v1", "concurrent");

		List<Callable<IdempotencyAcquisition>> tasks = new ArrayList<>();

		for (int i = 0; i < 40; i++) {
			tasks.add(() -> store.acquire(scope, FINGERPRINT));
		}

		int owners = 0;
		int inProgress = 0;

		try (var executor = Executors.newFixedThreadPool(8)) {
			List<Future<IdempotencyAcquisition>> results = executor.invokeAll(tasks);

			for (Future<IdempotencyAcquisition> result : results) {
				IdempotencyAcquisition outcome = result.get();

				if (outcome instanceof IdempotencyAcquisition.Acquired) {
					owners++;
				} else if (outcome == IdempotencyAcquisition.Rejected.IN_PROGRESS) {
					inProgress++;
				}
			}
		}

		assertThat(owners).isEqualTo(1);
		assertThat(inProgress).isEqualTo(39);
	}

	private UUID acquireOwner(IdempotencyStore target, IdempotencyScope scope) {
		IdempotencyAcquisition result = target.acquire(scope, FINGERPRINT);

		assertThat(result).isInstanceOf(IdempotencyAcquisition.Acquired.class);

		return ((IdempotencyAcquisition.Acquired) result).ownerToken();
	}

	private IdempotencyScope scope(String caller, String operation, String suffix) {
		return new IdempotencyScope(caller, operation, "test_request_key_" + suffix);
	}

	private StoredHttpResponse response(String body) {
		return new StoredHttpResponse(201, "application/json", null, body.getBytes(StandardCharsets.UTF_8));
	}
}