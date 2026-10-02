package com.footknow.api.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClerkTokenValidatorTest {

	private static final String ISSUER = "https://clerk.example.test";

	private static final String PARTY = "http://localhost:3000";

	private final ClerkTokenValidator validator = new ClerkTokenValidator(
			new ClerkProperties(ISSUER, Set.of(PARTY), ""));

	@Test
	void acceptsExpectedClaims() {
		assertThat(validator.validate(validToken().build()).hasErrors()).isFalse();
	}

	@Test
	void rejectsUnexpectedIssuer() {
		Jwt token = validToken().issuer("https://another.example.test").build();

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}

	@Test
	void rejectsExpiredTokenBeyondClockSkew() {
		Jwt token = validToken().issuedAt(Instant.now().minusSeconds(600)).expiresAt(Instant.now().minusSeconds(300))
				.build();

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}

	@Test
	void rejectsTokenNotYetValidBeyondClockSkew() {
		Jwt token = validToken().notBefore(Instant.now().plusSeconds(180)).build();

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}

	@Test
	void rejectsMissingExpiration() {
		Jwt token = validToken().claims(claims -> claims.remove("exp")).build();

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}

	@Test
	void rejectsMissingSubject() {
		Jwt token = validToken().claims(claims -> claims.remove("sub")).build();

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}

	@Test
	void rejectsBlankSubject() {
		Jwt token = validToken().subject(" ").build();

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}

	@Test
	void rejectsMissingAuthorizedParty() {
		Jwt token = validToken().claims(claims -> claims.remove("azp")).build();

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}

	@Test
	void rejectsUnexpectedAuthorizedParty() {
		Jwt token = validToken().claim("azp", "https://untrusted.example").build();

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}

	@Test
	void enforcesAudienceWhenConfigured() {
		ClerkTokenValidator audienceValidator = new ClerkTokenValidator(
				new ClerkProperties(ISSUER, Set.of(PARTY), "footknow-api"));

		assertThat(audienceValidator.validate(validToken().build()).hasErrors()).isTrue();

		Jwt wrongAudience = validToken().audience(List.of("another-api")).build();

		assertThat(audienceValidator.validate(wrongAudience).hasErrors()).isTrue();

		Jwt correctAudience = validToken().audience(List.of("footknow-api")).build();

		assertThat(audienceValidator.validate(correctAudience).hasErrors()).isFalse();
	}

	private Jwt.Builder validToken() {
		Instant now = Instant.now();

		return Jwt.withTokenValue("test-token").header("alg", "RS256").issuer(ISSUER).subject("user_test")
				.issuedAt(now.minusSeconds(10)).expiresAt(now.plusSeconds(300)).claim("azp", PARTY);
	}
}