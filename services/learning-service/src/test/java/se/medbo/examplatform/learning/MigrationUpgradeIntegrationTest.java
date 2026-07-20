package se.medbo.examplatform.learning;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MigrationUpgradeIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void upgradesAnExistingVersionOneDatabaseWithoutChangingItsChecksum() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = JdbcClient.create(dataSource);
        assertThat(jdbc.sql("SELECT checksum FROM flyway_schema_history WHERE version = '1'")
                .query(Integer.class).single()).isEqualTo(1734245996);

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        assertThat(jdbc.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
                .query(String.class).list()).containsExactly("1", "2", "3", "4");
        assertThat(jdbc.sql("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'imported_answer_option' AND column_name = 'content_release_id'
                """).query(String.class).single()).isEqualTo("NO");
        assertThat(jdbc.sql("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'practice_response' AND column_name = 'imported_question_id'
                """).query(String.class).single()).isEqualTo("NO");
        assertThat(jdbc.sql("SELECT to_regclass('public.idx_active_release_exam_published') IS NOT NULL")
                .query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT to_regclass('public.mock_exam_attempt') IS NOT NULL")
                .query(Boolean.class).single()).isTrue();
    }
}
