package com.footknow.api.common.ratelimit;

import org.springframework.stereotype.Component;

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