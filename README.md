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

## Implementing the endpoints

Now to implement each endpoint we need the following per (I will use the `league` endpoint as an example):

1. An entity class to represent the entity that will be stored in the repository.
```java
public record League(
        UUID id,
        String name
) implements Identifiable {
}
```

2. A class to represent the request body for the endpoint.
```java
public record CreateLeagueRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must not exceed 200 characters")
        String name

) {
}
```

3. A class to represent the response body for the endpoint.
```java
public record LeagueResponse(
        UUID id,
        String name
) {
}
```

4. A mapper class to map the command class to the entity class.
```java
@Component
public class LeagueMapper {

    public League toEntity(UUID id, CreateLeagueRequest request) {
        return new League(id, request.name());
    }

    public LeagueResponse toResponse(League entity) {
        return new LeagueResponse(entity.id(), entity.name());
    }
}
```


5. A service class that extends the `BaseCreateService` class to handle the business logic for the endpoint.
```java
@Service
public class LeagueService
        extends BaseCreateService<CreateLeagueRequest, League> {

    private final LeagueMapper mapper;

    public LeagueService(
            CreateRepository<League> repository,
            LeagueMapper mapper
    ) {
        super(repository);
        this.mapper = mapper;
    }

    @Override
    protected League newEntity(UUID id, CreateLeagueRequest command) {
        return mapper.toEntity(id, command);
    }
}
```

6. A controller class to handle the HTTP requests for the endpoint.
```java
@RestController
@RequestMapping(
        value = "/api/v1/leagues",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class LeagueController {

    private final LeagueService service;
    private final LeagueMapper mapper;

    public LeagueController(
            LeagueService service,
            LeagueMapper mapper
    ) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<LeagueResponse>> create(
            @Valid @RequestBody CreateLeagueRequest request
    ) {
        League entity = service.create(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(mapper.toResponse(entity)));
    }
}
```

## Adding security

Now that we have a working API, we add security to it before detailing the endpoints. We will use Clerk for authentication and authorization. To do this we need to add the following dependencies to the `pom.xml` file:

```xml
<!-- Bearer-token authentication and JWT verification -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>

<!-- Mock authenticated requests in Spring MVC tests -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

We then configure clerk with `ClerkProperties.java` class which will read of the properties from the `application.yml` in the `security.clerk` namespace. The `issuer` property is the URL of the Clerk issuer, the `authorizedParties` property is a set of authorized parties that can access the API, and the `audience` property is an optional audience that can be used to validate the JWT. By adding the `@Validated` annotation, we can ensure that the properties are validated when the application starts.

```java
@Validated
@ConfigurationProperties(prefix = "security.clerk")
public record ClerkProperties(
        @NotBlank
        String issuer,

        @NotEmpty
        Set<@NotBlank String> authorizedParties,

        String audience
) {

    @AssertTrue(message =
            "Clerk issuer must be an HTTPS URL without credentials, query, or fragment")
    public boolean isIssuerValid() {
        if (issuer == null || issuer.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(issuer);

            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public String jwkSetUri() {
        String base = issuer.endsWith("/")
                ? issuer.substring(0, issuer.length() - 1)
                : issuer;

        return base + "/.well-known/jwks.json";
    }
}
```

To validate the JWT, we need to create a `ClerkTokenValidator.java` class that will implement the `OAuth2TokenValidator<Jwt>` interface. This class will validate the JWT by checking the issuer, audience, and authorized parties.

```java
public final class ClerkTokenValidator
        implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN =
            new OAuth2Error(
                    "invalid_token",
                    "The bearer token is invalid.",
                    null
            );

    private final OAuth2TokenValidator<Jwt> standardValidator;
    private final Set<String> authorizedParties;
    private final String audience;

    public ClerkTokenValidator(ClerkProperties properties) {
        this.standardValidator =
                JwtValidators.createDefaultWithIssuer(properties.issuer());

        this.authorizedParties =
                Set.copyOf(properties.authorizedParties());

        this.audience = properties.audience();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        OAuth2TokenValidatorResult standardResult =
                standardValidator.validate(token);

        if (standardResult.hasErrors()) {
            return standardResult;
        }

        if (token.getExpiresAt() == null) {
            return invalid();
        }

        Object subject = token.getClaims().get("sub");

        if (!(subject instanceof String userId) || userId.isBlank()) {
            return invalid();
        }

        Object authorizedParty = token.getClaims().get("azp");

        if (!(authorizedParty instanceof String party)
                || !authorizedParties.contains(party)) {
            return invalid();
        }

        if (audience != null && !audience.isBlank()) {
            if (token.getAudience() == null
                    || !token.getAudience().contains(audience)) {
                return invalid();
            }
        }

        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }
}
```

Since our previously defined `GlobalExceptionHandler` class sits on the controller level, we need to add a `SecurityErrorWriter.java` class that will be throwing security
exceptions the same format as the rest of the API. It is important to do that since the security layer sits on top of the controller layer, so any security layer errors will stop the process and won't reach the controller layer.

```java
@Component
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletResponse response,
            ErrorCode errorCode
    ) throws IOException {
        ApiError error = new ApiError(
                errorCode.name(),
                errorCode.message(),
                List.of()
        );

        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure(error)
        );
    }
}
```

To make use of this `SecurityErrorWriter` class, we need to create to add components that will handle the api entrypoint security that will trigger when the request is not authenticated (no token, invalid JWT, etc.) with `ApiAuthenticationEntryPointHandler.java` and `ApiAccessDeniedHandler.java` that will trigger when the request is authenticated but not authorized (invalid scopes, etc.).

```java
@Component
public class ApiAuthenticationEntryPointHandler
        implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint =
            new BearerTokenAuthenticationEntryPoint();

    private final SecurityErrorWriter errorWriter;

    public ApiAuthenticationEntryPoint(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        // Preserve the standard WWW-Authenticate bearer challenge.
        bearerEntryPoint.commence(request, response, exception);

        errorWriter.write(response, ErrorCode.UNAUTHORIZED);
    }
}
```

Now to wire all the security components together, we need to create a `SecurityConfiguration.java` class that will configure the security for the API. It will be composed of a `JWT Decoder` that will use the `ClerkProperties` to validate the JWT using the `jwkSetUri()` method previously defined. `SecurityFilterChain` will configure define the rules for incoming HTTP requests. It disables browser-style authentication, enables Stateless API, enable CORS, and configures the JWT decoder to validate the JWT. It also sets up the `ApiAuthenticationEntryPointHandler` and `ApiAccessDeniedHandler` to handle authentication and authorization errors.
```java

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClerkProperties.class)
public class SecurityConfiguration {

    @Bean
    public JwtDecoder jwtDecoder(
            ClerkProperties properties,
            RestTemplateBuilder restTemplateBuilder
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(
                        restTemplateBuilder
                                .connectTimeout(Duration.ofSeconds(2))
                                .readTimeout(Duration.ofSeconds(3))
                                .build()
                )
                .build();

        decoder.setJwtValidator(new ClerkTokenValidator(properties));

        return decoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            CorsConfigurationSource corsConfigurationSource,
            ApiAuthenticationEntryPointHandler authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();

        // TODO: Implement roles
        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                jwt -> List.of()
        );

        http
                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource
                ))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize
                        // Allow internal error dispatch, not arbitrary
                        // anonymous requests to the /error URL.
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness"
                        )
                        .permitAll()

                        .requestMatchers("/api/v1/**")
                        .authenticated()

                        .anyRequest()
                        .denyAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(
                                        authenticationConverter
                                )
                        )
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            ClerkProperties properties
    ) {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.copyOf(properties.authorizedParties())
        );

        configuration.setAllowedMethods(List.of("POST", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type")
        );

        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/api/v1/**", configuration);

        return source;
    }
}
```

Now to wrap up the security we need to expose the caller id so that it can be used in the service layer. We can do this by creating a `CurrentCaller.java` class.

```java
@Component
public class CurrentCaller {

    public String userId() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !jwt.isAuthenticated()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        String subject = jwt.getToken().getSubject();

        if (subject == null || subject.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        return subject;
    }
}
```

## Adding rate limiting

To do so first add `RateLimited.java` interface to represent a rate limited service. This interface will be used to mark services that are rate limited.

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    RateLimitCategory category();
}
```

Then we add a `RateLimitCategory.java` enum to represent the different categories of rate limiting. This enum will be used to categorize the rate limited services.

```java
public enum RateLimitCategory {
    READ,
    WRITE
}
```

Then we add a `RateLimitProperties.java` interface to represent a service that can check if a caller is rate limited. This interface will be used to check if a caller is rate limited before allowing them to access a rate limited service.

```java
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @NotNull
        RateLimitTier defaultTier,

        @Min(1)
        @Max(1_000_000)
        int maxKeys,

        Map<@NotBlank String, @NotNull RateLimitTier> userTiers,

        @NotEmpty
        Map<
                RateLimitTier,
                @NotEmpty Map<
                        RateLimitCategory,
                        @NotNull @Valid Policy
                        >
                > policies
) {

    public RateLimitProperties {
        userTiers = userTiers == null
                ? Map.of()
                : Map.copyOf(userTiers);
    }

    @AssertTrue(message =
            "Every rate-limit tier must define a policy for every category")
    public boolean isPolicyMatrixComplete() {
        if (policies == null) {
            return false;
        }

        for (RateLimitTier tier : RateLimitTier.values()) {
            Map<RateLimitCategory, Policy> categories = policies.get(tier);

            if (categories == null) {
                return false;
            }

            for (RateLimitCategory category : RateLimitCategory.values()) {
                if (categories.get(category) == null) {
                    return false;
                }
            }
        }

        return true;
    }

    public Policy policyFor(
            RateLimitTier tier,
            RateLimitCategory category
    ) {
        return policies.get(tier).get(category);
    }

    public record Policy(
            @Min(1)
            long requests,

            @NotNull
            Duration window
    ) {

        @AssertTrue(message =
                "Rate-limit windows must be between one second and one day")
        public boolean isWindowValid() {
            return window != null
                    && window.compareTo(Duration.ofSeconds(1)) >= 0
                    && window.compareTo(Duration.ofDays(1)) <= 0;
        }
    }
}
```

Then we add `RateLimitResolver.java` interface to represent a service that can resolve the rate limit tier for a caller. This interface will be used to determine the rate limit tier for a caller before allowing them to access a rate limited service.

```java
@Component
public class RateLimitTierResolver {

    private final RateLimitProperties properties;

    public RateLimitTierResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    public RateLimitTier resolve(String clerkUserId) {
        return properties.userTiers().getOrDefault(
                clerkUserId,
                properties.defaultTier()
        );
    }
}
```

Then we add a `RateLimitDecision.java` class to represent a decision made by the rate limit service. This class will be used to indicate whether a caller is allowed to access a rate limited service or not.

```java
public record RateLimitDecision(
        boolean allowed,
        long retryAfterSeconds
) {

    public RateLimitDecision {
        if (allowed && retryAfterSeconds != 0) {
            throw new IllegalArgumentException(
                    "Allowed decisions must have zero retry delay"
            );
        }

        if (!allowed && retryAfterSeconds < 1) {
            throw new IllegalArgumentException(
                    "Rejected decisions must have a positive retry delay"
            );
        }
    }

    public static RateLimitDecision permit() {
        return new RateLimitDecision(true, 0);
    }

    public static RateLimitDecision reject(long retryAfterSeconds) {
        return new RateLimitDecision(false, retryAfterSeconds);
    }
}
```

We then add an in-memory rate limit store `InMemoryRateLimitStore.java` class to represent a service that can store rate limit information in memory. This class will be used to store rate limit information for callers in memory.

```java
public final class InMemoryRateLimitStore implements RateLimitStore {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final int maxKeys;
    private final LongSupplier ticker;
    private final Map<Key, Window> windows = new HashMap<>();

    public InMemoryRateLimitStore(int maxKeys) {
        this(maxKeys, System::nanoTime);
    }

    // Package-private so tests can control time without sleeping.
    InMemoryRateLimitStore(int maxKeys, LongSupplier ticker) {
        if (maxKeys < 1) {
            throw new IllegalArgumentException("maxKeys must be positive");
        }

        this.maxKeys = maxKeys;
        this.ticker = Objects.requireNonNull(ticker);
    }

    @Override
    public synchronized RateLimitDecision tryAcquire(
            String callerId,
            RateLimitCategory category,
            RateLimitProperties.Policy policy
    ) {
        Objects.requireNonNull(callerId);
        Objects.requireNonNull(category);
        Objects.requireNonNull(policy);

        if (callerId.isBlank()) {
            throw new IllegalArgumentException("callerId must not be blank");
        }

        if (policy.requests() < 1 || !policy.isWindowValid()) {
            throw new IllegalArgumentException("Invalid rate-limit policy");
        }

        long now = ticker.getAsLong();
        Key key = new Key(callerId, category);
        Window window = windows.get(key);

        if (window != null && window.expired(now)) {
            windows.remove(key);
            window = null;
        }

        if (window == null) {
            if (windows.size() >= maxKeys) {
                removeExpiredWindows(now);
            }

            if (windows.size() >= maxKeys) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
            }

            window = new Window(
                    now,
                    policy.window().toNanos(),
                    policy.requests()
            );

            windows.put(key, window);
        }

        if (window.used >= window.limit) {
            return RateLimitDecision.reject(
                    window.retryAfterSeconds(now)
            );
        }

        window.used++;

        return RateLimitDecision.permit();
    }

    private void removeExpiredWindows(long now) {
        windows.entrySet().removeIf(
                entry -> entry.getValue().expired(now)
        );
    }

    private record Key(
            String callerId,
            RateLimitCategory category
    ) {
    }

    private static final class Window {

        private final long startedAtNanos;
        private final long durationNanos;
        private final long limit;

        private long used;

        private Window(
                long startedAtNanos,
                long durationNanos,
                long limit
        ) {
            this.startedAtNanos = startedAtNanos;
            this.durationNanos = durationNanos;
            this.limit = limit;
        }

        private boolean expired(long now) {
            return now - startedAtNanos >= durationNanos;
        }

        private long retryAfterSeconds(long now) {
            long remainingNanos =
                    durationNanos - (now - startedAtNanos);

            long wholeSeconds = remainingNanos / NANOS_PER_SECOND;

            if (remainingNanos % NANOS_PER_SECOND != 0) {
                wholeSeconds++;
            }

            return Math.max(1, wholeSeconds);
        }
    }
}
```

We then add an interceptor `RateLimitInterceptor.java` class to represent a service that can intercept HTTP requests and apply rate limiting. This class will be used to intercept HTTP requests and apply rate limiting based on the caller's rate limit tier.

```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final CurrentCaller currentCaller;
    private final RateLimitTierResolver tierResolver;
    private final RateLimitProperties properties;
    private final RateLimitStore store;

    public RateLimitInterceptor(
            CurrentCaller currentCaller,
            RateLimitTierResolver tierResolver,
            RateLimitProperties properties,
            RateLimitStore store
    ) {
        this.currentCaller = currentCaller;
        this.tierResolver = tierResolver;
        this.properties = properties;
        this.store = store;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        // Do not charge again for async or error redispatch.
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimited annotation =
                handlerMethod.getMethodAnnotation(RateLimited.class);

        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(),
                    RateLimited.class
            );
        }

        if (annotation == null) {
            return true;
        }

        String callerId = currentCaller.userId();

        RateLimitTier tier = tierResolver.resolve(callerId);
        RateLimitCategory category = annotation.category();

        RateLimitDecision decision = store.tryAcquire(
                callerId,
                category,
                properties.policyFor(tier, category)
        );

        if (!decision.allowed()) {
            throw new RateLimitExceededException(
                    decision.retryAfterSeconds()
            );
        }

        return true;
    }
}
```

Finally we add a configuration `RateLimitConfiguration.java` class to register the `RateLimitInterceptor` with Spring MVC.

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {

    @Bean
    @Profile("in-memory & !production")
    public RateLimitStore inMemoryRateLimitStore(
            RateLimitProperties properties
    ) {
        return new InMemoryRateLimitStore(properties.maxKeys());
    }

    @Bean
    public WebMvcConfigurer rateLimitWebMvcConfigurer(
            RateLimitInterceptor interceptor
    ) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/api/v1/**");
            }
        };
    }
}
```

Now to configure the rate limit properties, we need to add the following to the `application.yml` file:

```yaml
app:
  rate-limit:
    default-tier: BASIC

    max-keys: 10000

    user-tiers: {}

    policies:
      BASIC:
        READ:
          requests: 120
          window: 1m
        WRITE:
          requests: 30
          window: 1m

      PREMIUM:
        READ:
          requests: 600
          window: 1m
        WRITE:
          requests: 120
          window: 1m
```

