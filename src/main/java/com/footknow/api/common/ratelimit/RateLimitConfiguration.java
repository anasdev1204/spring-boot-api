package com.footknow.api.common.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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