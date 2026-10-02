# Spring Boot REST API Starter

A reusable Spring Boot foundation for building authenticated REST APIs.

The project separates HTTP handling, business logic, and storage so you can replace the example resources with your own domain model without rebuilding common API infrastructure.

Development status: Resource storage, rate limiting, and idempotency currently use process-local memory. They are suitable for development and testing, not durable or distributed production use.

# Features

- Consistent success and error responses.

- Centralized MVC exception handling.

- Validated request DTOs.

- Separate entities, response DTOs, and mappers.

- Reusable creation service and repository abstraction.

- Clerk JWT authentication.

- Annotation-based rate limiting with categories and tiers.

- Annotation-based idempotency with response replay.

- Bounded development stores.

- Unit and endpoint integration tests.

- Health endpoints.

### Not included yet

- A production database implementation.

- Domain-specific authorization rules.

- A dynamic entitlement or subscription integration.

- Distributed rate-limit enforcement.

- Durable idempotency coordinated with database writes.

- Production deployment and operational configuration.

Authentication does not automatically grant business permissions. The current create endpoints require a valid login but do not yet restrict creation to administrators or editors.

## 1. Requirements

The current project configuration uses:

Java 25.

Spring Boot 3.5.16.

Maven.

A Clerk instance for authenticating real requests.

The versions in pom.xml are the source of truth. If you change Java or Spring Boot versions, update dependencies and run the complete test suite.

No external database or Redis instance is required for development mode.

## 2. Run the project locally

### Configure Clerk

Set the environment variables:

```bash
export CLERK_ISSUER='https\://your-instance.clerk.accounts.dev'
```

```bash
export CLERK_AUTHORIZED_PARTIES='http\://localhost:3000'
```

Use the exact issuer for your Clerk instance.

Multiple authorized frontend origins can be configured as a comma-separated list:

```bash
export CLERK_AUTHORIZED_PARTIES='http\://localhost:3000,https\://app.example.com'
```

If your session tokens contain a deliberately configured audience, set:

```bash
export CLERK_AUDIENCE='your-api-audience'
```

Otherwise, leave it unset.

A Clerk secret key is not required for public-key JWT verification.

You can also create a `.env.dev` file with the same variables when using the provided `run.sh` script.

### Run tests

```bash
./run.sh
```

This script formats the code, exports the Clerk environment variables from a `.env.dev`, and runs the Maven test suite.

Tests use test-specific Clerk configuration and mock authentication where appropriate. They do not require real user tokens.

Mock-authenticated tests do not prove that the real Clerk integration is configured correctly. Verify that separately with a genuine session token.

### Start the application

```bash
mvn spring-boot\:run
```

The default development profile is:

in-memory

The default server address is:

http://localhost:8080

Check health:

```bash
curl http\://localhost:8080/actuator/health
```

## 3. Understand the project structure

The code is organized by feature, with reusable infrastructure under common.

```text
src/main/java/com/footknow/api/
```
```
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── footknow/
│   │           └── api/
│   │               ├── Application.java
│   │               │
│   │               ├── common/
│   │               │   ├── config/
│   │               │   ├── domain/
│   │               │   ├── error/
│   │               │   ├── handler/
│   │               │   ├── idempotency/
│   │               │   ├── ratelimit/
│   │               │   ├── repository/
│   │               │   ├── response/
│   │               │   ├── security/
│   │               │   └── service/
│   │               │
│   │               └── endpoints/
│   │                   ├── ...
│   │
│   └── resources/
│       └── application.yml
│
└── test/
    ├── java/
    │   └── com/
    │       └── footknow/
    │           └── api/
    │               └── ...
    │
    └── resources/
        └── application-in-memory.yml
```

The current example implementation uses com.footknow.api and includes leagues, teams, players, and coaches. Replace those feature packages when adapting the starter.

Keep the application entry point above the feature packages so Spring can discover their components.

## 4. Create the application foundation

Start with:

pom.xml Build configuration, Java version, and dependencies

application.yml Application and infrastructure configuration

Application.java    Spring Boot entry point

ApplicationTest.java    Verify that the application context loads

The main dependencies cover:

Spring MVC and JSON serialization.

Bean Validation.

Actuator health endpoints.

OAuth2 resource-server security.

Spring AOP.

Spring Boot and Spring Security testing.

Keep credentials outside source control. Use environment variables or your deployment platform’s secret-management mechanism.

## 5. Standardize responses and errors

Create the shared response types before adding resource endpoints.

### Response types

FieldViolation.java Describe an invalid request field

ApiError.java   Carry an error code, message, and optional violations

ApiResponse.java    Define the common response envelope

Example success:

```json
{
```

"success": true,

"data": {

"id": "91461c88-d8ea-4293-9f3b-5d7d482a418b",

"name": "Example"

},

"timestamp": "2026-01-01T12:00:00Z"

}

Example failure:

```json
{
```

"success": false,

"error": {

"code": "VALIDATION_FAILED",

"message": "Request validation failed.",

"violations": [

```json
{
```

"field": "name",

"message": "must not be blank"

}

]

},

"timestamp": "2026-01-01T12:00:00Z"

}

### Exception handling

ErrorCode.java  Associate application error codes with HTTP statuses and preset messages

ApiException.java   Carry a known application error

GlobalExceptionHandler.java Convert MVC exceptions into the standard envelope

Use ApiException when application code needs to report a known failure.

The global handler also handles framework failures such as:

Invalid DTOs.

Malformed request bodies.

Unsupported media types.

Unsupported HTTP methods.

Unexpected exceptions.

Unexpected failures should return a generic message rather than expose stack traces or internal details.

MVC advice does not handle every failure in the servlet filter chain. Authentication and access-denied responses have separate handlers. Some framework responses, such as rejected CORS preflights, are also outside the normal MVC envelope.

## 6. Separate storage from business logic

Do not make controllers depend directly on database technology.

Create:

Identifiable.java   Define the resource identifier contract

CreateRepository.java   Define the storage operations needed by the creation flow

InMemoryCreateRepository.java   Supply development storage

BaseCreateService.java  Share the common creation and lookup behavior

The dependency direction is:

Controller → Service → Repository interface → Storage implementation

During development:

Service → CreateRepository → InMemoryCreateRepository

After database integration:

Service → CreateRepository → Database adapter

The in-memory implementation uses Java collections inside the application process.

Restarting the application deletes its contents.

When designing your database:

- Define the schema and constraints.

- Create the persistence model and migrations.

- Implement the repository interface.

- Add explicit domain-to-persistence mappings where needed.

- Define transaction boundaries.

- Test the adapter against the selected database.

The starter’s domain records are not an assumed database schema. They do not need to become JPA entities unchanged.

## 7. Add a resource endpoint

Each feature gets its own DTOs, mapper, service, and controller.

Using a generic resource:

Resource.java   Represent the domain object

CreateResourceRequest.java  Define and validate accepted input

ResourceResponse.java   Define the public output

ResourceMapper.java Map requests to entities and entities to responses

ResourceService.java    Execute resource-specific business logic

ResourceController.java Expose the HTTP endpoint

The request flow is:

```text
JSON request
```

↓

Request DTO validation

↓

Controller

↓

Service

↓

Repository

↓

Response DTO

↓

ApiResponse

The example create endpoints use:

- A server-generated UUID.

- A required name of at most 200 characters.

- 201 Created on success.

Replace these provisional fields with your own requirements.

Do not add relationships, uniqueness rules, or normalization merely because the starter examples use simple names.

## 8. Add Clerk authentication

Clerk handles sign-in. The API verifies the session token attached to each request:

http
Authorization: Bearer \<session-token>
```

### Security components

ClerkProperties.java    Bind and validate Clerk configuration

ClerkTokenValidator.java    Validate issuer, timestamps, subject, authorized party, and optional audience

SecurityErrorWriter.java    Serialize security errors using ApiResponse

ApiAuthenticationEntryPoint.java    Return 401 for missing or invalid authentication

ApiAccessDeniedHandler.java Return 403 when access is denied

SecurityConfiguration.java  Configure JWT decoding, route protection, CORS, and stateless security

CurrentCaller.java  Expose the verified Clerk subject

The JWT decoder verifies the signature using the configured Clerk instance’s public signing keys.

The azp allowlist identifies permitted authorized parties, normally frontend origins in this setup. It is not a list of user IDs or business roles.

### Verified identity

After successful authentication:

```java
String callerId = currentCaller.userId();
```

This is Clerk’s sub claim, not a UUID generated by the application.

Never replace it with a user ID taken from an arbitrary request header.

### Authentication versus authorization

These are separate decisions:

Authentication: Who is calling?

Authorization: May this caller perform this operation?

Before exposing the API publicly, define permissions for creating and modifying resources.

Local JWT verification also does not check Clerk session revocation on every request. Already-issued tokens may remain accepted until their validity expires.

## 9. Add category-based rate limiting

Rate limiting controls request frequency.

### Declare endpoint categories

RateLimited.java is an annotation, not a service interface.

Example:

```java
@RateLimited(category = RateLimitCategory.WRITE)
```

The current implementation supports method-level and controller-level annotations.

### Rate-limit components

RateLimitCategory.java  Identify operation categories such as READ and WRITE

RateLimitTier.java  Identify policy tiers such as BASIC and PREMIUM

RateLimitProperties.java    Bind and validate policy configuration

RateLimitTierResolver.java  Resolve a caller’s server-assigned tier

RateLimitDecision.java  Represent permission or rejection with a retry delay

RateLimitStore.java Define atomic quota consumption

InMemoryRateLimitStore.java Implement bounded local counters

RateLimitInterceptor.java   Enforce the annotation before controller execution

RateLimitExceededException.java Carry the retry delay

RateLimitExceptionHandler.java  Return 429 with Retry-After

RateLimitConfiguration.java Register the interceptor and development store

### How tier assignment works

Currently, RateLimitTierResolver is a concrete component that uses configuration:

```yaml
app:
```

rate-limit:

default-tier: BASIC

max-keys: 10000

user-tiers:

"user_example": PREMIUM

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

The lookup is:

Verified caller → assigned tier → category policy → quota check

Users without an override receive the default tier.

Later, replace the assignment lookup with a trusted entitlement provider. The numerical policies can remain in configuration.

- A premium tier does not grant administrative permissions.

### Enforcement behavior

Quotas are shared across endpoints in the same category.

Counters are scoped by verified caller and category.

Check-and-increment is atomic.

Rejected requests do not extend the fixed window.

Requests reaching the interceptor consume quota even if later validation fails.

Exhausted quota returns 429.

Full tracking capacity fails closed rather than bypassing enforcement.

Example:

http
HTTP/1.1 429 Too Many Requests
```

Retry-After: 42

Expose Retry-After through CORS so browser clients can read it.

For multiple API instances, use a shared atomic store, such as an appropriately implemented Redis adapter. It does not have to be the same database that stores business resources.

## 10. Add idempotency

Idempotency prevents a retry from executing the same intended operation again.

It does not enforce unique resource names or prevent a user from intentionally submitting a new operation with a new key.

### Client contract

Clients send:

Idempotency-Key: 71942644-1e8a-42c8-a4fb-e26108c3a477

Generate a new key for a new operation. Reuse it when retrying that operation.

A UUID is a suitable key format.

### Declare an idempotent operation

Idempotent.java is an annotation for supported controller methods:

```java
@Idempotent(operation = "resource.create.v1")
```

Operation IDs are backend-defined and should be unique and stable.

### Key validation and fingerprinting

IdempotencyKeyValidator.java    Require exactly one correctly formatted key

IdempotencyScope.java   Combine caller, operation, and client key

RequestFingerprint.java Hash the method, URI, query string, and exact body bytes

The fingerprint does not generate the client’s idempotency key. It detects whether a reused key represents the same request.

The comparison is byte-exact. These bodies have different fingerprints:

{"name":"Example"}

{ "name": "Example" }

The bearer token is not fingerprinted, allowing retries with a refreshed token.

### Storage and state transitions

IdempotencyProperties.java  Configure limits and retention

StoredHttpResponse.java Retain status, selected headers, and immutable body bytes

IdempotencyAcquisition.java Represent ownership, replay, or rejection

IdempotencyStore.java   Define atomic reservation and completion

InMemoryIdempotencyStore.java   Implement bounded development storage

IdempotencyConfiguration.java   Register the store, interceptor, and filter registration settings

State transitions:

No record

↓

IN_PROGRESS

├── Completed response retained → COMPLETED

└── Uncertain execution outcome → OUTCOME_UNKNOWN

Behavior:

New scoped key  Reserve and execute

Matching completed request  Replay retained response

Same key with different request 409 IDEMPOTENCY_KEY_REUSED

Matching operation still executing  409 IDEMPOTENCY_REQUEST_IN_PROGRESS

Outcome cannot be confirmed 409 IDEMPOTENCY_OUTCOME_UNKNOWN

No tracking capacity    503 SERVICE_UNAVAILABLE

Only completed records expire automatically.

Do not automatically retry an unknown outcome with a fresh key: the original operation may already have changed application state.

### Connect idempotency to HTTP handling

CachedBodyRequest.java  Make a bounded captured body readable by MVC

IdempotencyBodyWrappingFilter.java  Install the wrapper after security authorization

IdempotencyRequestContext.java  Carry the validated scope and fingerprint

IdempotencyRequestInterceptor.java  Validate the key and prepare request identity

IdempotencyAspect.java  Reserve, execute, serialize, complete, or replay

The aspect executes after request DTO binding and validation.

Therefore:

Malformed requests and invalid DTOs do not reserve a key.

Exceptions after operation execution begins conservatively block re-execution.

The response is retained before it is sent to the client.

Replays return the original body, including its UUID and timestamp.

Configuration:

```yaml
app:
```

idempotency:

max-entries: 1000

max-request-bytes: 65536

max-response-bytes: 65536

retention: 24h

Merge this with the existing app: configuration.

For browser clients:

- Allow the Idempotency-Key request header.

- Expose the Idempotency-Replayed response header.

## 11. Understand the complete request lifecycle

For an annotated create endpoint:

```text
1. Verify the Clerk JWT.
```

2. Apply route-access rules.

3. Check and consume rate-limit quota.

4. Validate the idempotency key.

5. Capture the bounded body and compute its fingerprint.

6. Deserialize and validate the DTO.

7. Acquire the idempotency reservation.

8. Replay a completed response or execute the controller.

9. Run the service and repository operation.

10. Serialize and retain the response.

11. Send the response.

Example controller annotations:

```java
@Idempotent(operation = "resource.create.v1")
```

```java
@RateLimited(category = RateLimitCategory.WRITE)
```

@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)

This mechanism currently supports synchronous JSON ResponseEntity endpoints. Extending it to async processing, streaming, or additional response headers requires explicit changes.

## 12. Understand development storage and profiles

The project has three independent kinds of in-memory storage:

Resource repositories   Created domain objects

Rate-limit store    Request counters and windows

Idempotency store   Reservations and completed responses

All are local to one application process.

Consequences:

Restarting loses their contents.

Multiple instances have independent state.

Idempotency cannot protect across restarts.

Rate limits are not shared across instances.

Development adapters use:

@Profile("in-memory & !production")

This is a Spring profile expression, not a production boolean property.

It requires:

in-memory to be active.

production not to be active.

Once production is active, replacement adapters must exist. Otherwise startup fails rather than silently using temporary storage.

A profile is not a security boundary. It does not prevent accidental public exposure.

## 13. Test a create operation manually

The current example repository includes /api/v1/teams. Replace the path when you replace the example domain.

Obtain a fresh Clerk session token, then generate one key:

```bash
KEY=$(uuidgen)
```

Send:

```bash
curl -i -X POST http\://localhost:8080/api/v1/teams \\
```

-H "Authorization: Bearer $CLERK_SESSION_TOKEN" \\

-H "Idempotency-Key: $KEY" \\

-H 'Content-Type: application/json' \\

-d '{"name":"Example"}'

Repeat the exact request with the same key.

Expected behavior:

First request: 201, Idempotency-Replayed: false.

Retry: 201, Idempotency-Replayed: true.

Both bodies contain the same resource ID and timestamp.

Rate limiting still applies to the retry.

Never commit tokens or include them in application logs.

## 14. Adapt the starter to a new API

When forking this repository:

- Rename the project

- Update Maven coordinates and project metadata.

- Change spring.application.name.

- Rename the base package and update imports.

- Update documentation and test package names.

### Replace the example domain

Remove or replace the example feature packages.

- Define request and response DTOs.

- Define domain objects.

- Implement explicit mappings.

- Add resource-specific service rules.

- Register typed development repositories.

- Assign stable idempotency operation IDs.

- Update endpoint and integration tests.

### Configure identity and permissions

- Configure your Clerk instance and frontend origins.

- Decide who may read, create, update, and delete resources.

- Implement those authorization rules.

- Choose a trusted source for rate-limit entitlements.

### Implement persistence

- Design your schema.

- Add migrations.

- Implement database adapters.

- Define transaction boundaries.

- Add database integration tests.

- Coordinate durable idempotency with resource writes.

Do not assume that replacing YAML settings or adding JPA annotations alone completes production persistence.

## 15. Production readiness checklist

Before public deployment:

Define and enforce business permissions.

Replace temporary resource repositories.

Add and test database migrations.

Implement durable idempotency coordinated with writes.

Define reconciliation for uncertain outcomes.

Configure a shared limiter if running multiple instances.

Replace manual tier overrides with trusted entitlement management where needed.

Configure HTTPS, proxy trust, and exact CORS origins.

Configure request limits, timeouts, and ingress abuse protection.

Add metrics, structured logs, alerts, and tracing without exposing tokens.

Add API documentation.

Test against real Clerk configuration and production storage adapters.

Exercise concurrent requests, dependency outages, and recovery.

Establish backup and restore procedures.

An independent response cache does not provide durable “exactly once” execution. There is still a failure window between saving business data and recording its result unless the persistence design coordinates those actions.

## Current status

The starter provides a working single-process development foundation for authenticated creation endpoints, rate limiting, and idempotent retries.

It is ready to be adapted to a domain model. It is not yet a production-ready product until durable storage, authorization, shared enforcement where required, and operational safeguards are implemented.
