# Spring Boot REST API Starter

A reusable foundation for building authenticated REST APIs with Spring Boot. HTTP handling, business logic, and storage are kept separate, so you can swap the example resources for your own domain without rebuilding common infrastructure.

> **Development status:** Resource storage, rate limiting, and idempotency use process-local memory. This is suitable for development and testing, not for durable or distributed production use.

## Features

- Consistent success and error response envelope
- Centralized MVC exception handling
- Validated request DTOs, with separate entities, response DTOs, and mappers
- Repository abstraction with a reusable creation service
- Clerk JWT authentication
- Annotation-based rate limiting (categories and tiers)
- Annotation-based idempotency with response replay
- Bounded development stores
- Unit and endpoint integration tests
- Health endpoints

### Not included yet

- Production database implementation
- Domain-specific authorization (the create endpoints require login but don't restrict to admins or editors)
- Dynamic entitlement or subscription integration
- Distributed rate-limit enforcement
- Durable idempotency coordinated with database writes
- Production deployment and operations configuration

---

## 1. Requirements

| Requirement | Version / Notes |
|---|---|
| Java | 25 |
| Spring Boot | 3.5.16 |
| Build tool | Maven |
| Auth provider | A Clerk instance |

`pom.xml` is the source of truth. If you change Java or Spring Boot versions, update dependencies and run the full test suite. No database or Redis is needed in development mode.

## 2. Run locally

### Configure Clerk

```bash
export CLERK_ISSUER='https://your-instance.clerk.accounts.dev'
export CLERK_AUTHORIZED_PARTIES='http://localhost:3000'
```

- `CLERK_ISSUER` must match your Clerk instance exactly.
- `CLERK_AUTHORIZED_PARTIES` accepts a comma-separated list of frontend origins.
- `CLERK_AUDIENCE` is optional. Set it only if your session tokens have a deliberately configured audience.
- A Clerk secret key is not needed for public-key JWT verification.

You can put the same variables in a `.env.dev` file for use with `run.sh`.

### Run tests

```bash
./run.sh
```

This formats the code, loads `.env.dev`, and runs the Maven test suite. Tests use their own Clerk configuration and mock authentication, so no real tokens are needed. Verify the real Clerk integration separately with a genuine session token.

### Start the app

```bash
mvn spring-boot:run
curl http://localhost:8080/actuator/health
```

The default profile is `in-memory`, and the server runs at `http://localhost:8080`.

## 3. Project structure

Code is organized by feature, with shared infrastructure under `common`. Keep `Application.java` above the feature packages so Spring discovers their components.

```text
src/main/java/com/footknow/api/
├── Application.java
├── common/
│   ├── config/        ├── idempotency/   ├── security/
│   ├── domain/        ├── ratelimit/     └── service/
│   ├── error/         ├── repository/
│   ├── handler/       └── response/
└── endpoints/         # example features: leagues, teams, players, coaches

src/main/resources/application.yml
src/test/resources/application-in-memory.yml
```

Replace the example feature packages when adapting the starter.

## 4. Responses and errors

| Class | Purpose |
|---|---|
| `ApiResponse` | Common response envelope |
| `ApiError` | Error code, message, optional violations |
| `FieldViolation` | Describes an invalid request field |
| `ErrorCode` | Maps error codes to HTTP statuses and preset messages |
| `ApiException` | Carries a known application error |
| `GlobalExceptionHandler` | Converts MVC exceptions into the envelope |

**Success**

```json
{
  "success": true,
  "data": { "id": "91461c88-d8ea-4293-9f3b-5d7d482a418b", "name": "Example" },
  "timestamp": "2026-01-01T12:00:00Z"
}
```

**Failure**

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed.",
    "violations": [{ "field": "name", "message": "must not be blank" }]
  },
  "timestamp": "2026-01-01T12:00:00Z"
}
```

Throw `ApiException` for known failures. The global handler also covers invalid DTOs, malformed bodies, unsupported media types or methods, and unexpected exceptions (which return a generic message, never stack traces).

**Limitation:** MVC advice doesn't cover the whole servlet filter chain. Authentication and access-denied responses have their own handlers, and some framework responses (such as rejected CORS preflights) fall outside the envelope.

## 5. Storage layering

Controllers never depend on database technology directly:

```text
Controller → Service → CreateRepository (interface) → Storage implementation
```

| Class | Purpose |
|---|---|
| `Identifiable` | Resource identifier contract |
| `CreateRepository` | Storage operations for the creation flow |
| `InMemoryCreateRepository` | Development storage (cleared on restart) |
| `BaseCreateService` | Shared creation and lookup behavior |

When adding a database: define the schema and constraints, create the persistence model and migrations, implement `CreateRepository`, add explicit domain-to-persistence mappings, define transaction boundaries, and test the adapter against the real database. The starter's domain records are not a schema and needn't become JPA entities unchanged.

## 6. Adding a resource endpoint

Each feature has its own DTOs, mapper, service, and controller:

| Class | Purpose |
|---|---|
| `Resource` | Domain object |
| `CreateResourceRequest` | Validated input |
| `ResourceResponse` | Public output |
| `ResourceMapper` | Request → entity → response |
| `ResourceService` | Business logic |
| `ResourceController` | HTTP endpoint |

```text
JSON → DTO validation → Controller → Service → Repository → Response DTO → ApiResponse
```

The example endpoints use a server-generated UUID, a required name (max 200 chars), and return `201 Created`. Treat these as placeholders and don't copy relationships, uniqueness rules, or normalization from the examples without a real requirement.

## 7. Authentication (Clerk)

Clerk handles sign-in; the API verifies the session token on each request:

```http
Authorization: Bearer <session-token>
```

| Class | Purpose |
|---|---|
| `ClerkProperties` | Bind and validate configuration |
| `ClerkTokenValidator` | Validate issuer, timestamps, subject, authorized party, optional audience |
| `SecurityConfiguration` | JWT decoding, route protection, CORS, stateless security |
| `ApiAuthenticationEntryPoint` | 401 for missing or invalid authentication |
| `ApiAccessDeniedHandler` | 403 when access is denied |
| `SecurityErrorWriter` | Serializes security errors as `ApiResponse` |
| `CurrentCaller` | Exposes the verified Clerk subject |

Signatures are verified with the Clerk instance's public signing keys. The `azp` allowlist names permitted frontend origins, not users or roles.

```java
String callerId = currentCaller.userId(); // Clerk's `sub` claim, not an app-generated UUID
```

Never take the user ID from a request header.

**Authentication ≠ authorization.** Authentication answers *who is calling*; authorization answers *what may they do*. Define permissions for creating and modifying resources before exposing the API publicly.

**Limitation:** Local JWT verification doesn't check session revocation, so issued tokens stay valid until they expire.

## 8. Rate limiting

Declare a category on a controller or method:

```java
@RateLimited(category = RateLimitCategory.WRITE)
```

| Class | Purpose |
|---|---|
| `RateLimitCategory` / `RateLimitTier` | Operation categories (READ, WRITE) and policy tiers (BASIC, PREMIUM) |
| `RateLimitProperties` | Policy configuration |
| `RateLimitTierResolver` | Resolves a caller's server-assigned tier |
| `RateLimitStore` / `InMemoryRateLimitStore` | Atomic quota consumption (bounded local counters) |
| `RateLimitInterceptor` | Enforces the annotation before the controller runs |
| `RateLimitDecision` / `RateLimitExceededException` | Allow/reject result with retry delay |
| `RateLimitExceptionHandler` | Returns 429 with `Retry-After` |
| `RateLimitConfiguration` | Registers the interceptor and development store |

**Configuration**

```yaml
app:
  rate-limit:
    default-tier: BASIC
    max-keys: 10000
    user-tiers:
      "user_example": PREMIUM
    policies:
      BASIC:
        READ:  { requests: 120, window: 1m }
        WRITE: { requests: 30,  window: 1m }
      PREMIUM:
        READ:  { requests: 600, window: 1m }
        WRITE: { requests: 120, window: 1m }
```

**Lookup:** verified caller → assigned tier (default if none) → category policy → quota check.

**Behavior**

- Quotas are scoped per verified caller and category, and shared across endpoints in that category.
- Check-and-increment is atomic.
- Rejected requests don't extend the fixed window.
- Requests that reach the interceptor consume quota even if validation later fails.
- When tracking capacity is full, enforcement fails closed.
- Exhausted quota returns `429` with a `Retry-After` header (expose it via CORS for browser clients).

A premium tier grants higher limits, not administrative permissions. Later, replace the tier lookup with a trusted entitlement provider. For multiple instances, use a shared atomic store such as a Redis adapter (separate from the business database if you like).

## 9. Idempotency

Idempotency makes a retry safe: it won't execute the same intended operation twice. It does not enforce unique names or stop a user from submitting a new operation with a new key.

### Client contract

```http
Idempotency-Key: 71942644-1e8a-42c8-a4fb-e26108c3a477
```

Generate a new key (a UUID works well) for each new operation, and reuse it when retrying that operation.

### Declaring an operation

```java
@Idempotent(operation = "resource.create.v1")
```

Operation IDs are backend-defined, unique, and stable.

### Key validation and fingerprinting

| Class | Purpose |
|---|---|
| `IdempotencyKeyValidator` | Requires exactly one correctly formatted key |
| `IdempotencyScope` | Combines caller, operation, and client key |
| `RequestFingerprint` | Hashes method, URI, query string, and exact body bytes |

The fingerprint detects whether a reused key carries the same request. The comparison is byte-exact, so `{"name":"Example"}` and `{ "name": "Example" }` differ. The bearer token is excluded, so retries with a refreshed token work.

### Storage and states

| Class | Purpose |
|---|---|
| `IdempotencyProperties` | Limits and retention |
| `IdempotencyStore` / `InMemoryIdempotencyStore` | Atomic reservation and completion (bounded dev storage) |
| `StoredHttpResponse` | Status, selected headers, immutable body bytes |
| `IdempotencyAcquisition` | Ownership, replay, or rejection |
| `IdempotencyConfiguration` | Registers the store, interceptor, and filter |

```text
No record → IN_PROGRESS ─┬─ response retained ────→ COMPLETED
                         └─ uncertain outcome ────→ OUTCOME_UNKNOWN
```

| Situation | Result |
|---|---|
| New scoped key | Reserve and execute |
| Matching completed request | Replay the retained response |
| Same key, different request | `409 IDEMPOTENCY_KEY_REUSED` |
| Matching request still running | `409 IDEMPOTENCY_REQUEST_IN_PROGRESS` |
| Outcome can't be confirmed | `409 IDEMPOTENCY_OUTCOME_UNKNOWN` |
| No tracking capacity | `503 SERVICE_UNAVAILABLE` |

Only completed records expire automatically. Never retry an unknown outcome with a fresh key, since the original operation may already have changed state.

### HTTP integration

| Class | Purpose |
|---|---|
| `IdempotencyBodyWrappingFilter` | Installs the body wrapper after security authorization |
| `CachedBodyRequest` | Makes the bounded captured body readable by MVC |
| `IdempotencyRequestInterceptor` | Validates the key and prepares request identity |
| `IdempotencyRequestContext` | Carries the validated scope and fingerprint |
| `IdempotencyAspect` | Reserves, executes, serializes, completes, or replays |

The aspect runs after DTO binding and validation, so malformed requests and invalid DTOs never reserve a key. Exceptions after execution begins conservatively block re-execution. The response is retained before it is sent, and replays return the original body (including its UUID and timestamp).

```yaml
app:
  idempotency:
    max-entries: 1000
    max-request-bytes: 65536
    max-response-bytes: 65536
    retention: 24h
```

Merge this into your existing `app:` block. For browser clients, allow the `Idempotency-Key` request header and expose the `Idempotency-Replayed` response header.

## 10. Request lifecycle

For an annotated create endpoint:

1. Verify the Clerk JWT
2. Apply route-access rules
3. Check and consume rate-limit quota
4. Validate the idempotency key
5. Capture the bounded body and compute its fingerprint
6. Deserialize and validate the DTO
7. Acquire the idempotency reservation
8. Replay a completed response, or execute the controller
9. Run the service and repository operation
10. Serialize and retain the response
11. Send the response

```java
@Idempotent(operation = "resource.create.v1")
@RateLimited(category = RateLimitCategory.WRITE)
@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
```

This currently supports synchronous JSON `ResponseEntity` endpoints. Async processing, streaming, or extra response headers require explicit changes.

## 11. Development storage and profiles

Three independent in-memory stores exist: **resource repositories**, the **rate-limit store**, and the **idempotency store**. All are local to one process, so:

- Restarting loses their contents.
- Multiple instances have independent state.
- Idempotency can't protect across restarts.
- Rate limits aren't shared across instances.

Development adapters use `@Profile("in-memory & !production")`, a Spring profile expression (not a boolean property). It requires `in-memory` to be active and `production` to be inactive. Once `production` is active, replacement adapters must exist or startup fails, rather than silently using temporary storage.

A profile is not a security boundary and doesn't prevent accidental public exposure.

## 12. Manual test

The example repository exposes `/api/v1/teams`. Get a fresh Clerk session token, then:

```bash
KEY=$(uuidgen)

curl -i -X POST http://localhost:8080/api/v1/teams \
  -H "Authorization: Bearer $CLERK_SESSION_TOKEN" \
  -H "Idempotency-Key: $KEY" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Example"}'
```

Repeat the exact request with the same key. Expected:

- First request: `201`, `Idempotency-Replayed: false`
- Retry: `201`, `Idempotency-Replayed: true`
- Both bodies contain the same resource ID and timestamp
- Rate limiting still applies to the retry

Never commit tokens or log them.

## 13. Adapting the starter

**Project setup**

- Rename the project and update Maven coordinates and metadata
- Change `spring.application.name`
- Rename the base package, imports, docs, and test packages

**Replace the example domain**

- Remove the example feature packages
- Define request/response DTOs, domain objects, and explicit mappings
- Add resource-specific service rules
- Register typed development repositories
- Assign stable idempotency operation IDs
- Update endpoint and integration tests

**Identity and permissions**

- Configure your Clerk instance and frontend origins
- Decide who may read, create, update, and delete, then implement it
- Choose a trusted source for rate-limit entitlements

**Persistence**

- Design the schema and add migrations
- Implement database adapters and define transaction boundaries
- Add database integration tests
- Coordinate durable idempotency with resource writes

Changing YAML settings or adding JPA annotations alone doesn't complete production persistence.

## 14. Production readiness checklist

- [ ] Define and enforce business permissions
- [ ] Replace temporary resource repositories
- [ ] Add and test database migrations
- [ ] Implement durable idempotency coordinated with writes
- [ ] Define reconciliation for uncertain outcomes
- [ ] Use a shared limiter if running multiple instances
- [ ] Replace manual tier overrides with trusted entitlement management
- [ ] Configure HTTPS, proxy trust, and exact CORS origins
- [ ] Configure request limits, timeouts, and ingress abuse protection
- [ ] Add metrics, structured logs, alerts, and tracing (without exposing tokens)
- [ ] Add API documentation
- [ ] Test against real Clerk configuration and production storage adapters
- [ ] Exercise concurrent requests, dependency outages, and recovery
- [ ] Establish backup and restore procedures

> An independent response cache does not provide durable "exactly once" execution. A failure window remains between saving business data and recording its result unless the persistence design coordinates the two.

## Current status

The starter is a working single-process development foundation for authenticated creation endpoints, rate limiting, and idempotent retries. It's ready to adapt to a domain model, but not production-ready until durable storage, authorization, shared enforcement, and operational safeguards are in place.