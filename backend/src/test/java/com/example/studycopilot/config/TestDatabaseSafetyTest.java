package com.example.studycopilot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestDatabaseSafetyTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestDatabaseSafetyConfiguration.class)
            .withPropertyValues("spring.profiles.active=test");

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5433/study_copilot",
            "jdbc:postgresql://localhost:5433/procurement?applicationName=study_copilot_test",
            "jdbc:postgresql://localhost:5433/study_copilot_test_backup",
            "jdbc:h2:mem:study_copilot_test",
            "jdbc:postgresql://localhost:5433/other?currentSchema=study_copilot_test",
            "jdbc:postgresql://localhost:5433/study_copilot_test?PGDBNAME=procurement",
            "jdbc:postgresql://localhost:5433/study_copilot_test?%50GDBNAME=procurement",
            ""
    })
    void refusesUnsafeDatabaseBeforeAnyConnectionIsCreated(String url) {
        runner.withPropertyValues("spring.datasource.url=" + url).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("study_copilot_test");
        });
    }

    @Test
    void acceptsOnlyTheIsolatedPostgresDatabase() {
        runner.withPropertyValues("spring.datasource.url="
                + "jdbc:postgresql://localhost:5433/study_copilot_test?currentSchema=study_copilot")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void doesNotRestrictTheNormalRuntimeProfile() {
        runner.withPropertyValues("spring.profiles.active=dev",
                        "spring.datasource.url=jdbc:postgresql://localhost:5433/study_copilot")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.hikari.jdbc-url", "spring.flyway.url"})
    void refusesUnsafeConnectionOverrides(String property) {
        runner.withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:5433/study_copilot_test",
                        property + "=jdbc:postgresql://localhost:5433/procurement")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.hikari.jdbc-url", "spring.flyway.url"})
    void refusesExplicitlyEmptyOrWhitespaceOnlyConnectionOverrides(String property) {
        for (String value : new String[]{"", "   "}) {
            withOverride(property, value).run(context -> assertThat(context).hasFailed());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "spring.datasource.jndi-name",
            "spring.datasource.type",
            "spring.datasource.driver-class-name",
            "spring.datasource.hikari.data-source-jndi",
            "spring.datasource.hikari.data-source-class-name",
            "spring.datasource.hikari.data-source",
            "spring.datasource.hikari.driver-class-name",
            "spring.flyway.driver-class-name",
            "spring.flyway.jdbc-properties.PGDBNAME",
            "spring.datasource.xa.data-source-class-name",
            "spring.datasource.hikari.data-source-properties.PGDBNAME",
            "spring.datasource.hikari.data-source-properties.databaseName",
            "spring.datasource.xa.properties.databaseName"
    })
    void rejectsAlternateDatasourceSelectionWithoutDisclosingItsValue(String property) {
        withOverride(property, "private-target-marker").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
            StringWriter diagnostic = new StringWriter();
            context.getStartupFailure().printStackTrace(new PrintWriter(diagnostic));
            assertThat(diagnostic.toString()).contains("study_copilot_test").doesNotContain("private-target-marker");
        });
    }

    @Test
    void rejectsRelaxedBindingAliasesForAnUnsafeJdbcOverride() {
        withOverride("spring.datasource.hikari.jdbcUrl", "jdbc:postgresql://localhost/procurement")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsTheBlankUrlAndJndiBypassWithRealDatasourceAutoConfiguration() {
        runner.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:5433/study_copilot_test",
                        "spring.sql.init.mode=never", "spring.jmx.enabled=false")
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("unsafe-connection", Map.of(
                                "spring.datasource.hikari.jdbc-url", "   ",
                                "spring.datasource.hikari.data-source-jndi", "java:comp/env/jdbc/private-target-marker"))))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getMessage()).doesNotContain("private-target-marker");
                });
    }

    @Test
    void acceptsTheStandardPostgresDriverAndHikariType() {
        runner.withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:5433/study_copilot_test",
                        "spring.datasource.type=com.zaxxer.hikari.HikariDataSource",
                        "spring.datasource.driver-class-name=org.postgresql.Driver",
                        "spring.datasource.hikari.driver-class-name=org.postgresql.Driver",
                        "spring.flyway.driver-class-name=org.postgresql.Driver")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.xa.data-source-class-name", "spring.datasource.xa.properties"})
    void rejectsXaSelectionFromNonEnumerablePropertySources(String property) {
        runner.withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost/study_copilot_test")
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new PropertySource<>("opaque-connection-override", Map.of(property, "private-target-marker")) {
                            @Override
                            public Object getProperty(String name) {
                                return source.get(name);
                            }
                        }))
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "spring.datasource.hikari.dataSourceJndi",
            "spring.datasource.hikari.dataSourceProperties[PGDBNAME]",
            "spring.datasource.xa.properties[databaseName]",
            "spring.datasource.hikari.data-source-properties",
            "spring.datasource.xa",
            "spring.flyway.jdbcProperties[PGDBNAME]"
    })
    void rejectsRelaxedAliasesAndWholeUnsupportedPropertyFamilies(String property) {
        withOverride(property, "private-target-marker").run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SPRING_DATASOURCE_HIKARI_DATASOURCEJNDI",
            "SPRING_DATASOURCE_HIKARI_DATASOURCEPROPERTIES_PGDBNAME",
            "SPRING_DATASOURCE_XA_DATASOURCECLASSNAME",
            "SPRING_FLYWAY_JDBCPROPERTIES_PGDBNAME"
    })
    void rejectsUnsupportedEnvironmentVariableAliases(String property) {
        runner.withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost/study_copilot_test")
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("override-systemEnvironment",
                                Map.of(property, "private-target-marker"))))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void sanitizesBindingFailuresWithoutRetainingUntrustedCauses() {
        withOverride("spring.datasource.hikari.jdbc-url", new Object() {
            @Override
            public String toString() {
                return "private-target-marker";
            }
        }).run(context -> {
            assertThat(context).hasFailed();
            StringWriter diagnostic = new StringWriter();
            context.getStartupFailure().printStackTrace(new PrintWriter(diagnostic));
            assertThat(diagnostic.toString()).doesNotContain("private-target-marker");
        });
    }

    @Test
    void acceptsValidatedUrlsAndOrdinaryPoolTuningWithRealDatasourceAutoConfiguration() {
        runner.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost/study_copilot_test",
                        "spring.datasource.hikari.jdbc-url=jdbc:postgresql://localhost/study_copilot_test",
                        "spring.flyway.url=jdbc:postgresql://localhost/study_copilot_test",
                        "spring.datasource.hikari.maximum-pool-size=2")
                .run(context -> assertThat(context).hasNotFailed());
    }

    private ApplicationContextRunner withOverride(String property, Object value) {
        return runner.withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:5433/study_copilot_test")
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("connection-override", Map.of(property, value))));
    }
}
