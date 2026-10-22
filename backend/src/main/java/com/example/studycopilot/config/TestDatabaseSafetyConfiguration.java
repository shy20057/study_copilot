package com.example.studycopilot.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertyState;
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
        try {
            Binder binder = Binder.get(environment);
            requireIsolated(binder.bind("spring.datasource.url", String.class).orElse(""));
            for (String property : new String[]{"spring.datasource.hikari.jdbc-url", "spring.flyway.url"}) {
                binder.bind(property, String.class).ifBound(TestDatabaseSafetyConfiguration::requireIsolated);
            }
            // Check XA's known leaves too: non-enumerable sources report UNKNOWN for
            // descendants, but Spring can still bind these JavaBean properties directly.
            for (String property : new String[]{"spring.datasource.jndi-name", "spring.datasource.xa",
                    "spring.datasource.xa.data-source-class-name", "spring.datasource.xa.properties",
                    "spring.datasource.hikari.data-source", "spring.datasource.hikari.data-source-jndi",
                    "spring.datasource.hikari.data-source-class-name", "spring.datasource.hikari.data-source-properties",
                    "spring.flyway.jdbc-properties"}) {
                ConfigurationPropertyName name = ConfigurationPropertyName.of(property);
                for (var source : ConfigurationPropertySources.get(environment)) {
                    if (source.getConfigurationProperty(name) != null
                            || source.containsDescendantOf(name) == ConfigurationPropertyState.PRESENT) {
                        throw unsafeConfiguration();
                    }
                }
            }
            requireSupported(binder, "spring.datasource.type", "com.zaxxer.hikari.HikariDataSource");
            requireSupported(binder, "spring.datasource.driver-class-name", "org.postgresql.Driver");
            requireSupported(binder, "spring.datasource.hikari.driver-class-name", "org.postgresql.Driver");
            requireSupported(binder, "spring.flyway.driver-class-name", "org.postgresql.Driver");
        } catch (RuntimeException ignored) {
            // Binding errors can include supplied values. Never attach their cause or message.
            throw unsafeConfiguration();
        }
        return beanFactory -> { };
    }

    private static void requireSupported(Binder binder, String property, String supported) {
        binder.bind(property, String.class).ifBound(value -> {
            if (!supported.equals(value)) {
                throw unsafeConfiguration();
            }
        });
    }

    private static IllegalStateException unsafeConfiguration() {
        return new IllegalStateException("The test profile requires PostgreSQL database study_copilot_test using the supported JDBC datasource configuration");
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
        throw unsafeConfiguration();
    }
}
