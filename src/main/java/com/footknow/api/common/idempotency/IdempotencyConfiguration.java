package com.footknow.api.common.idempotency;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyConfiguration {

	@Bean
	@Profile("in-memory & !production")
	public IdempotencyStore inMemoryIdempotencyStore(IdempotencyProperties properties) {
		return new InMemoryIdempotencyStore(properties.maxEntries(), properties.maxResponseBytes(),
				properties.retention());
	}
}