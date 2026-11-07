package com.incident.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtSecretConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtOnlyConfiguration.class);

    @Test
    void jwtServiceContextFailsWhenRequiredEnvironmentSecretIsAbsent() {
        contextRunner
                .withPropertyValues(
                        "app.jwt.secret=${JWT_SECRET}",
                        "app.jwt.expiration-ms=86400000")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void jwtServiceContextStartsWhenValidEnvironmentSecretIsSupplied() {
        contextRunner
                .withPropertyValues(
                        "JWT_SECRET=test-only-valid-signing-key-0123456789-abcdef",
                        "app.jwt.secret=${JWT_SECRET}",
                        "app.jwt.expiration-ms=86400000")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(JwtService.class));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(JwtService.class)
    static class JwtOnlyConfiguration {
        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }
}
