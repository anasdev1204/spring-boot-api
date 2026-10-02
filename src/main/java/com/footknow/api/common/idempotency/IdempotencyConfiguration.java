package com.footknow.api.common.idempotency;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyConfiguration {

	@Bean
	@Profile("in-memory & !production")
	public IdempotencyStore inMemoryIdempotencyStore(IdempotencyProperties properties) {
		return new InMemoryIdempotencyStore(properties.maxEntries(), properties.maxResponseBytes(),
				properties.retention());
	}

	@Bean
	public WebMvcConfigurer idempotencyWebMvcConfigurer(IdempotencyRequestInterceptor interceptor) {
		return new WebMvcConfigurer() {
			@Override
			public void addInterceptors(InterceptorRegistry registry) {
				registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**").order(200);
			}
		};
	}

	@Bean
	public FilterRegistrationBean<IdempotencyBodyWrappingFilter> idempotencyFilterRegistration(
			IdempotencyBodyWrappingFilter filter) {
		FilterRegistrationBean<IdempotencyBodyWrappingFilter> registration = new FilterRegistrationBean<>(filter);

		// Installed explicitly inside Spring Security instead.
		registration.setEnabled(false);

		return registration;
	}
}