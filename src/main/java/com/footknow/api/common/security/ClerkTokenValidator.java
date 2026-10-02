package com.footknow.api.common.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.util.Set;

public final class ClerkTokenValidator implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2Error INVALID_TOKEN = new OAuth2Error("invalid_token", "The bearer token is invalid.",
			null);

	private final OAuth2TokenValidator<Jwt> standardValidator;
	private final Set<String> authorizedParties;
	private final String audience;

	public ClerkTokenValidator(ClerkProperties properties) {
		this.standardValidator = JwtValidators.createDefaultWithIssuer(properties.issuer());

		this.authorizedParties = Set.copyOf(properties.authorizedParties());

		this.audience = properties.audience();
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		OAuth2TokenValidatorResult standardResult = standardValidator.validate(token);

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

		if (!(authorizedParty instanceof String party) || !authorizedParties.contains(party)) {
			return invalid();
		}

		if (audience != null && !audience.isBlank()) {
			if (token.getAudience() == null || !token.getAudience().contains(audience)) {
				return invalid();
			}
		}

		return OAuth2TokenValidatorResult.success();
	}

	private OAuth2TokenValidatorResult invalid() {
		return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
	}
}