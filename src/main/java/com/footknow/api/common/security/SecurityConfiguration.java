package com.footknow.api.common.security;

import com.footknow.api.common.handler.ApiAuthenticationEntryPointHandler;
import com.footknow.api.common.handler.ApiAccessDeniedHandler;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

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