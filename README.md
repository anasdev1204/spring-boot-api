# Steps to making this project

## Create the foundation 

Repo structure at this step:

- `pom.xml` - Maven configuration file for the project. (Can just be copied from this or generated using spring initializer)

- `application.yml` - Spring Boot configuration file. This is where you can configure various properties for your application, such as server port, database connections, etc.

- `src/main/java/com/footknow/` directory and its test counterpart - This is where the main application code and tests will reside. The `Application.java` file is the entry point of the Spring Boot application.

- `ApplicationTest.java` in the test directory - This is a basic test class to ensure that the Spring Boot application context loads correctly.

## Shared responses and centralized error handling

The objective of this step is to create a centralized error handling mechanism for the API so the responses look like:

First we create a `FieldViolation.java` class to represent a violation of a specific field in a request or input:

then we create a `ApiError.java` class to represent an error response:

Then we add a `ApiResponse.java` class to have the centralized response structure:

To handle exceptions we first need to map http status codes to error codes. We can do this by creating a `ErrorCode.java` enum:

After that we can create an `ApiException.java` class to represent an exception that can be thrown in the application and that extends `RuntimeException`. This exception will carry an `ErrorCode` to indicate the type of error that occurred.

Now that everything is in place, we can create a `GlobalExceptionHandler.java` class to handle exceptions thrown in the application and return a standardized error response. `@RestControllerAdvice` tells the app that whenever an exception happens in a controller, check this class to see how it should be handled without needing to manually catch exceptions in each controller method. `handleApiException` is used to handle exceptions threwn by the application (manually by me). The other methods override the default Spring exception handling for specific exceptions like validation errors, malformed requests, and other unexpected exceptions.

## Storage abstraction and base service

No db implementation yet. The in-memory adapter we are making in this step is just for dev and testing purposes. The idea is to have a storage abstraction that can be implemented with different storage backends (e.g., in-memory, database, etc.) without changing the service layer.

First we make an `Identifiable.java` interface to represent an entity that has an identifier:

We then make a `CreateRepository.java` interface to represent a repository that can create entities:

For development before having a valid database implementation, we can create an in-memory implementation of the `CreateRepository` interface. This implementation will use a `ConcurrentHashMap` to store entities in memory.

Then to make use of our new in-memory implementation, we can create a `BaseCreateService.java` class that will use the `CreateRepository` class to create and find entities. This service will be used by the controllers to handle requests.

## Implementing the endpoints

Now to implement each endpoint we need the following per (I will use the `league` endpoint as an example):

1. An entity class to represent the entity that will be stored in the repository.

2. A class to represent the request body for the endpoint.

3. A class to represent the response body for the endpoint.

4. A mapper class to map the command class to the entity class.

5. A service class that extends the `BaseCreateService` class to handle the business logic for the endpoint.

6. A controller class to handle the HTTP requests for the endpoint.

## Adding security

Now that we have a working API, we add security to it before detailing the endpoints. We will use Clerk for authentication and authorization. To do this we need to add the following dependencies to the `pom.xml` file:

We then configure clerk with `ClerkProperties.java` class which will read of the properties from the `application.yml` in the `security.clerk` namespace. The `issuer` property is the URL of the Clerk issuer, the `authorizedParties` property is a set of authorized parties that can access the API, and the `audience` property is an optional audience that can be used to validate the JWT. By adding the `@Validated` annotation, we can ensure that the properties are validated when the application starts.

To validate the JWT, we need to create a `ClerkTokenValidator.java` class that will implement the `OAuth2TokenValidator<Jwt>` interface. This class will validate the JWT by checking the issuer, audience, and authorized parties.

Since our previously defined `GlobalExceptionHandler` class sits on the controller level, we need to add a `SecurityErrorWriter.java` class that will be throwing security
exceptions the same format as the rest of the API. It is important to do that since the security layer sits on top of the controller layer, so any security layer errors will stop the process and won't reach the controller layer.

To make use of this `SecurityErrorWriter` class, we need to create to add components that will handle the api entrypoint security that will trigger when the request is not authenticated (no token, invalid JWT, etc.) with `ApiAuthenticationEntryPointHandler.java` and `ApiAccessDeniedHandler.java` that will trigger when the request is authenticated but not authorized (invalid scopes, etc.).

Now to wire all the security components together, we need to create a `SecurityConfiguration.java` class that will configure the security for the API. It will be composed of a `JWT Decoder` that will use the `ClerkProperties` to validate the JWT using the `jwkSetUri()` method previously defined. `SecurityFilterChain` will configure define the rules for incoming HTTP requests. It disables browser-style authentication, enables Stateless API, enable CORS, and configures the JWT decoder to validate the JWT. It also sets up the `ApiAuthenticationEntryPointHandler` and `ApiAccessDeniedHandler` to handle authentication and authorization errors.

Now to wrap up the security we need to expose the caller id so that it can be used in the service layer. We can do this by creating a `CurrentCaller.java` class.

## Adding rate limiting

To do so first add `RateLimited.java` interface to represent a rate limited service. This interface will be used to mark services that are rate limited.

Then we add a `RateLimitCategory.java` enum to represent the different categories of rate limiting. This enum will be used to categorize the rate limited services.

Then we add a `RateLimitProperties.java` which will read the rate limit properties from the `application.yml` in the `app.rate-limit` namespace and validate them. This class will be used to configure the rate limit tiers and policies for the application.

Then we add `RateLimitTierResolver.java` interface to represent a service that can resolve the rate limit tier for a caller. This interface will be used to determine the rate limit tier for a caller before allowing them to access a rate limited service. For now we will be using the hard-fixed application.yml configuration to resolve the rate limit tier for a caller. In the future we can implement a more dynamic approach to resolve the rate limit tier for a caller.

Then we add a `RateLimitDecision.java` class to represent a decision made by the rate limit service. This class does not have any behavior, it is just a data class that will be used to represent the decision made by the rate limit service. This class will be used by `RateLimitStore.java` to represent the decision made by the rate limit service.

We then add an in-memory rate limit store `InMemoryRateLimitStore.java` class to represent a service that can store rate limit information in memory. This class is only intended for development before implementing a proper database implementation. It will be used to store rate limit information in memory and will be used by the `RateLimitInterceptor` class to determine if a caller is rate limited or not.

We then add an interceptor `RateLimitInterceptor.java` class to represent a service that can intercept HTTP requests and apply rate limiting. This class will be used to intercept HTTP requests and apply rate limiting based on the caller's rate limit tier.

Finally we add a configuration `RateLimitConfiguration.java` class to register the `RateLimitInterceptor` and use the `InMemoryRateLimitStore` for development and testing purposes by setting a profile that depends on the `production` attribute. In production, we will implement a proper database implementation of the `RateLimitStore` interface.

Now to configure the rate limit properties, we need to add the following to the `application.yml` file:

We then need to add the `RateLimited` annotation to all of our controllers endpoints.
We also need to expose the `retry-after` header in the `SecurityConfiguration.java` class to allow the client to know when they can retry the request after being rate limited.

## Add idempotency

Similar to how we previously proceeded we first start by adding a `Idempotent.java` interface to represent a idempotent service. This interface will be used to mark services that are idempotent.

Then we add a way to verify that the key exists in the headers and that it is the correct format with `IdempotencyKeyValidator.java` class. This class will be used to validate the idempotency key in the request headers.

To wrap up the basic setup we need a `RequestFingerprinter.java` class to represent a service that can fingerprint a request and generate the unique key for it. This class will be used to generate a unique key for each request based on the request method, URI, query string, and body.

To make use of our basic setup we need to add a `IdempotencyProperties.java` class to represent a service that can read the idempotency properties from the `application.yml` in the `app.idempotency` namespace and validate them. This class will be used to configure the idempotency store for the application.

The objective from this idemponcy setup is to store the response of a request and return it if the same request is made again with the same idempotency key. So we first a `StoredHttpResponse` class to represent the stored HTTP response. This class will be used to store the response of a request in the idempotency store.

We also need to add a `IdempotencyAcquisition.java` class to represent the result of an idempotency acquisition. This class will be used to represent the result of an idempotency acquisition from the idempotency store.

Now that a response can be stored and the idempotency acquisition is defined, we need to add a `IdempotencyStore.java` interface to represent a service that can store idempotent responses. This interface will be used to store and retrieve idempotent responses from the idempotency store.

For development purposes we will add an in-memory implementation of the `IdempotencyStore` interface with `InMemoryIdempotencyStore.java` class. This class will be used to store idempotent responses in memory for development and testing purposes.

Now finally we need a configuration `IdempotencyConfiguration.java` class to register the `InMemoryIdempotencyStore` for development and testing purposes by setting a profile that depends on the `production` attribute. In production, we will implement a proper database implementation of the `IdempotencyStore` interface.
