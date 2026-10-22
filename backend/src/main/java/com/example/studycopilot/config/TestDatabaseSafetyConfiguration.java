package com.example.studycopilot.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Refuse unsafe test targets before datasource or Flyway beans can be instantiated. */
@Configuration(proxyBeanMethods = false)
@Profile("test")
public class TestDatabaseSafetyConfiguration {
    @Bean
    static BeanFactoryPostProcessor isolatedTestDatabaseGuard(Environment environment) {
        requireIsolated(environment.getProperty("spring.datasource.url", ""));
        for (String property : new String[]{"spring.datasource.hikari.jdbc-url", "spring.flyway.url"}) {
            String override = environment.getProperty(property);
            if (override != null && !override.isBlank()) {
                requireIsolated(override);
            }
        }
        return beanFactory -> { };
    }

    private static void requireIsolated(String url) {
        if (url.startsWith("jdbc:postgresql://")) {
            try {
                URI target = URI.create(url.substring("jdbc:".length()));
                String query = target.getRawQuery();
                boolean databaseOverride = query != null && Arrays.stream(query.split("&"))
                        .map(parameter -> parameter.split("=", 2)[0])
                        .map(name -> URLDecoder.decode(name, StandardCharsets.UTF_8))
                        .anyMatch(name -> name.equalsIgnoreCase("PGDBNAME"));
                if ("/study_copilot_test".equals(target.getRawPath())
                        && target.getRawFragment() == null && !databaseOverride) {
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // Report no URL or credentials in the diagnostic below.
            }
        }
        throw new IllegalStateException("The test profile requires PostgreSQL database study_copilot_test");
    }
}
