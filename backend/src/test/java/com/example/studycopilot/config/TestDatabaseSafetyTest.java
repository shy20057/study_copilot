package com.example.studycopilot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
}
