# Steps to making this project

## Create the foundation 

Repo structure at this step:

```
football-api/
├── pom.xml
├── .gitignore
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/footknow/api/
    │   │       └── Application.java
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/
            └── com/footknow/api/
                └── ApplicationTest.java
```

- `pom.xml` - Maven configuration file for the project. (Can just be copied from this or generated using spring initializer)

- `application.yml` - Spring Boot configuration file. This is where you can configure various properties for your application, such as server port, database connections, etc.


```yml
spring:
  application:
    name: footknow-api

  lifecycle:
    timeout-per-shutdown-phase: 20s

  jackson:
    deserialization:
      fail-on-unknown-properties: true

  mvc:
    problemdetails:
      enabled: false

server:
  port: ${SERVER_PORT:8080}
  shutdown: graceful

  error:
    include-message: never
    include-binding-errors: never
    include-stacktrace: never
    include-exception: false

management:
  endpoints:
    web:
      exposure:
        include: health

  endpoint:
    health:
      show-details: never
      probes:
        enabled: true

logging:
  level:
    root: INFO
    com.footknow.football: INFO
```

- `src/main/java/com/footknow/` directory and its test counterpart - This is where the main application code and tests will reside. The `Application.java` file is the entry point of the Spring Boot application.

```java
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```
    
- `ApplicationTest.java` in the test directory - This is a basic test class to ensure that the Spring Boot application context loads correctly.

```java
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ApplicationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReturnsUp() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/actuator/health",
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("status").asText())
                .isEqualTo("UP");
    }
}
``` 

## Shared responses and centralized error handling

The objective of this step is to create a centralized error handling mechanism for the API so the responses look like:

```json
// Success
{
  "success": true,
  "data": {
    "id": "..."
  },
  "timestamp": "2026-01-01T12:00:00Z"
}

//failure
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed for the request.",
    "fieldViolations": [
        ...
    ]
  },
  "timestamp": "2026-01-01T12:00:00Z"
}
```

First we create a `FieldViolation.java` class to represent a violation of a specific field in a request or input:

```java
public record FieldViolation(
        String field,
        String message
) {
}
```

then we create a `ApiError.java` class to represent an error response:

```java
public record ApiError(
        String code,
        String message,

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<FieldViolation> violations
) {

    public ApiError {
        violations = violations == null
                ? List.of()
                : List.copyOf(violations);
    }
}
```

Then we add a `ApiResponse.java` class to have the centralized response structure:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        Instant timestamp
) {

    public ApiResponse {
        Objects.requireNonNull(timestamp, "timestamp is required");

        if (success && error != null) {
            throw new IllegalArgumentException(
                    "Successful responses cannot contain an error"
            );
        }

        if (!success && error == null) {
            throw new IllegalArgumentException(
                    "Failed responses must contain an error"
            );
        }

        if (!success && data != null) {
            throw new IllegalArgumentException(
                    "Failed responses cannot contain data"
            );
        }
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                true,
                data,
                null,
                Instant.now()
        );
    }

    public static ApiResponse<Void> failure(ApiError error) {
        return new ApiResponse<>(
                false,
                null,
                Objects.requireNonNull(error, "error is required"),
                Instant.now()
        );
    }
}
```

To handle exceptions we first need to map http status codes to error codes. We can do this by creating a `ErrorCode.java` enum:

```java
public enum ErrorCode {

    BAD_REQUEST(
            HttpStatus.BAD_REQUEST,
            "The request is invalid."
    ),

    VALIDATION_FAILED(
            HttpStatus.BAD_REQUEST,
            "Request validation failed."
    ),

    MALFORMED_REQUEST(
            HttpStatus.BAD_REQUEST,
            "The request body is missing or invalid."
    ),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "Authentication is required."
    ),

    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "You do not have permission to perform this operation."
    ),

    NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "The requested resource was not found."
    ),

    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "The HTTP method is not supported for this endpoint."
    ),

    CONFLICT(
            HttpStatus.CONFLICT,
            "The request conflicts with the current resource state."
    ),

    PAYLOAD_TOO_LARGE(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "The request payload is too large."
    ),

    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "The request content type is not supported."
    ),

    RATE_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS,
            "Rate limit exceeded. Please try again later."
    ),

    REQUEST_REJECTED(
            HttpStatus.BAD_REQUEST,
            "The request could not be processed."
    ),

    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred."
    );

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
```

After that we can create an `ApiException.java` class to represent an exception that can be thrown in the application and that extends `RuntimeException`. This exception will carry an `ErrorCode` to indicate the type of error that occurred.

```java
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public ApiException(ErrorCode errorCode, Throwable cause) {
        super(
                Objects.requireNonNull(
                        errorCode,
                        "errorCode is required"
                ).message(),
                cause
        );

        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
```

Now that everything is in place, we can create a `GlobalExceptionHandler.java` class to handle exceptions thrown in the application and return a standardized error response. `@RestControllerAdvice` tells the app that whenever an exception happens in a controller, check this class to see how it should be handled without needing to manually catch exceptions in each controller method. `handleApiException` is used to handle exceptions threwn by the application (manually by me). The other methods override the default Spring exception handling for specific exceptions like validation errors, malformed requests, and other unexpected exceptions.

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(
            ApiException exception
    ) {
        ErrorCode errorCode = exception.errorCode();

        if (errorCode.status().is5xxServerError()) {
            log.error("Application server error", exception);
        }

        return errorResponse(
                errorCode,
                errorCode.status(),
                new HttpHeaders(),
                List.of()
        );
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Stream<FieldViolation> fieldViolations =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error -> new FieldViolation(
                                error.getField(),
                                validationMessage(error.getDefaultMessage())
                        ));

        Stream<FieldViolation> globalViolations =
                exception.getBindingResult()
                        .getGlobalErrors()
                        .stream()
                        .map(error -> new FieldViolation(
                                "$",
                                validationMessage(error.getDefaultMessage())
                        ));

        List<FieldViolation> violations =
                Stream.concat(fieldViolations, globalViolations)
                        .distinct()
                        .sorted(
                                Comparator.comparing(FieldViolation::field)
                                        .thenComparing(FieldViolation::message)
                        )
                        .toList();

        return errorResponse(
                ErrorCode.VALIDATION_FAILED,
                status,
                headers,
                violations
        );
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return errorResponse(
                ErrorCode.MALFORMED_REQUEST,
                status,
                headers,
                List.of()
        );
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        if (status.is5xxServerError()) {
            log.error("Spring MVC server error", exception);
        }

        return errorResponse(
                errorCodeFor(status),
                status,
                headers,
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(
            Exception exception
    ) {
        log.error("Unhandled request error", exception);

        return errorResponse(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.status(),
                new HttpHeaders(),
                List.of()
        );
    }

    private ResponseEntity<Object> errorResponse(
            ErrorCode errorCode,
            HttpStatusCode status,
            HttpHeaders headers,
            List<FieldViolation> violations
    ) {
        ApiError error = new ApiError(
                errorCode.name(),
                errorCode.message(),
                violations
        );

        return new ResponseEntity<>(
                ApiResponse.failure(error),
                headers,
                status
        );
    }

    private ErrorCode errorCodeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.BAD_REQUEST;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> ErrorCode.CONFLICT;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> ErrorCode.RATE_LIMIT_EXCEEDED;
            default -> status.is5xxServerError()
                    ? ErrorCode.INTERNAL_ERROR
                    : ErrorCode.REQUEST_REJECTED;
        };
    }

    private static String validationMessage(String message) {
        return message == null ? "Invalid value." : message;
    }
}
```

## Storage abstraction and base service

No db implementation yet. The in-memory adapter we are making in this step is just for dev and testing purposes. The idea is to have a storage abstraction that can be implemented with different storage backends (e.g., in-memory, database, etc.) without changing the service layer.

First we make an `Identifiable.java` interface to represent an entity that has an identifier:

```java
public interface Identifiable {

    UUID id();
}
```

We then make a `CreateRepository.java` interface to represent a repository that can create entities:

```java

public interface CreateRepository<E extends Identifiable> {

    /**
     * Creates a new entity.
     *
     * Implementations must reject an existing ID rather than
     * overwrite its entity. The duplicate check and insertion
     * must be atomic.
     *
     * @throws com.footknow.api.common.error.ApiException
     *         with CONFLICT when the ID already exists
     */
    E create(E entity);

    /**
     * Finds an entity by its application-level ID.
     */
    Optional<E> findById(UUID id);
}
```

For development before having a valid database implementation, we can create an in-memory implementation of the `CreateRepository` interface. This implementation will use a `ConcurrentHashMap` to store entities in memory.

```java
public class InMemoryCreateRepositoryTest<E extends Identifiable>
        implements CreateRepository<E> {

    private final ConcurrentMap<UUID, E> entities =
            new ConcurrentHashMap<>();

    @Override
    public E create(E entity) {
        Objects.requireNonNull(entity, "entity is required");

        UUID id = Objects.requireNonNull(
                entity.id(),
                "entity ID is required"
        );

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
```

Then to make use of our new in-memory implementation, we can create a `BaseCreateService.java` class that will use the `CreateRepository` class to create and find entities. This service will be used by the controllers to handle requests.

```java
public abstract class BaseCreateService<C, E extends Identifiable> {

    private final CreateRepository<E> repository;

    protected BaseCreateService(CreateRepository<E> repository) {
        this.repository = Objects.requireNonNull(
                repository,
                "repository is required"
        );
    }

    public E create(C command) {
        Objects.requireNonNull(command, "command is required");

        UUID id = UUID.randomUUID();

        E entity = Objects.requireNonNull(
                newEntity(id, command),
                "newEntity must return an entity"
        );

        if (!id.equals(entity.id())) {
            throw new IllegalStateException(
                    "newEntity must preserve the generated ID"
            );
        }

        return repository.create(entity);
    }

    public E findById(UUID id) {
        Objects.requireNonNull(id, "id is required");

        return repository.findById(id)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.NOT_FOUND)
                );
    }

    protected abstract E newEntity(UUID id, C command);
}
```

##