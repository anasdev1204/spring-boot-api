package com.footknow.api.common.ratelimit;

import com.footknow.api.common.error.ApiException;
import com.footknow.api.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryRateLimitStoreTest {

    @Test
    void rejectsAfterLimitAndResetsAtExpiration() {
        AtomicLong now = new AtomicLong();

        InMemoryRateLimitStore store =
                new InMemoryRateLimitStore(10, now::get);

        RateLimitProperties.Policy policy =
                new RateLimitProperties.Policy(2, Duration.ofMinutes(1));

        assertThat(store.tryAcquire(
                "user_a", RateLimitCategory.WRITE, policy
        ).allowed()).isTrue();

        assertThat(store.tryAcquire(
                "user_a", RateLimitCategory.WRITE, policy
        ).allowed()).isTrue();

        RateLimitDecision rejected = store.tryAcquire(
                "user_a", RateLimitCategory.WRITE, policy
        );

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(60);

        now.set(Duration.ofSeconds(45).toNanos());

        assertThat(store.tryAcquire(
                "user_a", RateLimitCategory.WRITE, policy
        ).retryAfterSeconds()).isEqualTo(15);

        now.set(Duration.ofMinutes(1).toNanos());

        assertThat(store.tryAcquire(
                "user_a", RateLimitCategory.WRITE, policy
        ).allowed()).isTrue();
    }

    @Test
    void roundsRetryDelayUpToWholeSeconds() {
        AtomicLong now = new AtomicLong();

        InMemoryRateLimitStore store =
                new InMemoryRateLimitStore(10, now::get);

        RateLimitProperties.Policy policy =
                new RateLimitProperties.Policy(1, Duration.ofSeconds(2));

        store.tryAcquire("user_a", RateLimitCategory.WRITE, policy);

        now.set(Duration.ofMillis(1100).toNanos());

        assertThat(store.tryAcquire(
                "user_a", RateLimitCategory.WRITE, policy
        ).retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void separatesCallersAndCategories() {
        InMemoryRateLimitStore store =
                new InMemoryRateLimitStore(10, () -> 0L);

        RateLimitProperties.Policy policy =
                new RateLimitProperties.Policy(1, Duration.ofMinutes(1));

        assertThat(store.tryAcquire(
                "user_a", RateLimitCategory.WRITE, policy
        ).allowed()).isTrue();

        assertThat(store.tryAcquire(
                "user_a", RateLimitCategory.WRITE, policy
        ).allowed()).isFalse();

        assertThat(store.tryAcquire(
                "user_b", RateLimitCategory.WRITE, policy
        ).allowed()).isTrue();

        assertThat(store.tryAcquire(
                "user_a", RateLimitCategory.READ, policy
        ).allowed()).isTrue();
    }

    @Test
    void failsClosedAtCapacityAndReclaimsExpiredWindows() {
        AtomicLong now = new AtomicLong();

        InMemoryRateLimitStore store =
                new InMemoryRateLimitStore(1, now::get);

        RateLimitProperties.Policy policy =
                new RateLimitProperties.Policy(1, Duration.ofSeconds(1));

        store.tryAcquire("user_a", RateLimitCategory.WRITE, policy);

        assertThatThrownBy(() -> store.tryAcquire(
                "user_b", RateLimitCategory.WRITE, policy
        )).isInstanceOfSatisfying(
                ApiException.class,
                exception -> assertThat(exception.errorCode())
                        .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE)
        );

        now.set(Duration.ofSeconds(1).toNanos());

        assertThat(store.tryAcquire(
                "user_b", RateLimitCategory.WRITE, policy
        ).allowed()).isTrue();
    }

    @Test
    void concurrentRequestsCannotExceedTheLimit() throws Exception {
        InMemoryRateLimitStore store =
                new InMemoryRateLimitStore(10, () -> 0L);

        RateLimitProperties.Policy policy =
                new RateLimitProperties.Policy(10, Duration.ofMinutes(1));

        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            tasks.add(() -> store.tryAcquire(
                    "user_a",
                    RateLimitCategory.WRITE,
                    policy
            ).allowed());
        }

        int accepted = 0;

        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> results = executor.invokeAll(tasks);

            for (Future<Boolean> result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
        }

        assertThat(accepted).isEqualTo(10);
    }
}
