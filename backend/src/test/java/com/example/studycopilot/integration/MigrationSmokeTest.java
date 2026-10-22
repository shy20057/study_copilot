package com.example.studycopilot.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MigrationSmokeTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void phaseASchemaExists() {
        Integer count = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'study_copilot'
                  and table_name in ('t_focus_session','t_focus_slice','t_reward_ledger')
                """, Integer.class);
        assertThat(count).isEqualTo(3);
    }
}
