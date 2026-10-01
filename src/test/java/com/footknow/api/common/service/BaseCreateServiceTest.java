package com.footknow.api.common.service;

import com.footknow.api.common.domain.Identifiable;
import com.footknow.api.common.error.ApiException;
import com.footknow.api.common.error.ErrorCode;
import com.footknow.api.common.repository.CreateRepository;
import com.footknow.api.common.repository.InMemoryCreateRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseCreateServiceTest {

    private final CreateRepository<TestEntity> repository =
            new InMemoryCreateRepository<>();

    private final TestService service =
            new TestService(repository);

    @Test
    void generatesIdAndStoresEntity() {
        TestEntity created = service.create(
                new CreateCommand("Example")
        );

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Example");
        assertThat(repository.findById(created.id()))
                .contains(created);
    }

    @Test
    void findsExistingEntity() {
        TestEntity created = service.create(
                new CreateCommand("Example")
        );

        assertThat(service.findById(created.id()))
                .isEqualTo(created);
    }

    @Test
    void returnsNotFoundErrorForMissingEntity() {
        assertThatThrownBy(
                () -> service.findById(UUID.randomUUID())
        )
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND)
                );
    }

    @Test
    void rejectsNullCommand() {
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command is required");
    }

    @Test
    void rejectsMapperThatDiscardsGeneratedId() {
        BaseCreateService<CreateCommand, TestEntity> brokenService =
                new BaseCreateService<>(repository) {

                    @Override
                    protected TestEntity newEntity(
                            UUID id,
                            CreateCommand command
                    ) {
                        return new TestEntity(null, command.name());
                    }
                };

        assertThatThrownBy(
                () -> brokenService.create(new CreateCommand("Example"))
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "newEntity must preserve the generated ID"
                );
    }

    private record CreateCommand(String name) {
    }

    private record TestEntity(
            UUID id,
            String name
    ) implements Identifiable {
    }

    private static class TestService
            extends BaseCreateService<CreateCommand, TestEntity> {

        TestService(CreateRepository<TestEntity> repository) {
            super(repository);
        }

        @Override
        protected TestEntity newEntity(
                UUID id,
                CreateCommand command
        ) {
            return new TestEntity(id, command.name());
        }
    }
}