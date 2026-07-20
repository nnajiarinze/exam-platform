package se.medbo.examplatform.learning;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers class CanonicalExamIdentifierMigrationTest {
 @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:16-alpine");
 @Test void mergesLegacyNamespacesWithoutDeletingHistory(){
  Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).target(MigrationVersion.fromVersion("5")).load().migrate();
  var jdbc=JdbcClient.create(new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()));
  UUID older=UUID.randomUUID(),newer=UUID.randomUUID(),learner=UUID.randomUUID(),session=UUID.randomUUID();var now=OffsetDateTime.now();
  jdbc.sql("INSERT INTO learner_profile(id,external_identity_id,interface_language,explanation_language,created_at,updated_at) VALUES(:id,'legacy','sv','sv',:now,:now)").param("id",learner).param("now",now).update();
  jdbc.sql("INSERT INTO imported_content_release(id,external_release_id,exam_id,exam_version_id,release_version,checksum,status,published_at,imported_at,activated_at) VALUES(:id,:external,:exam,'v1',:version,:checksum,'ACTIVE',:published,:published,:activated)")
    .param("id",older).param("external","lower-release").param("exam","swedish-citizenship").param("version","1").param("checksum","a".repeat(64)).param("published",now.minusDays(2)).param("activated",now.minusDays(2)).update();
  jdbc.sql("INSERT INTO imported_content_release(id,external_release_id,exam_id,exam_version_id,release_version,checksum,status,published_at,imported_at,activated_at) VALUES(:id,:external,:exam,'v2',:version,:checksum,'ACTIVE',:published,:published,:activated)")
    .param("id",newer).param("external","upper-release").param("exam","SWEDISH_CITIZENSHIP").param("version","2").param("checksum","b".repeat(64)).param("published",now).param("activated",now).update();
  jdbc.sql("INSERT INTO practice_session(id,learner_id,exam_id,content_release_id,mode,status,started_at) VALUES(:id,:learner,'SWEDISH_CITIZENSHIP',:release,'MIXED','COMPLETED',:now)").param("id",session).param("learner",learner).param("release",older).param("now",now).update();
  Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).load().migrate();
  assertThat(jdbc.sql("SELECT count(*) FROM imported_content_release").query(Integer.class).single()).isEqualTo(2);
  assertThat(jdbc.sql("SELECT external_release_id FROM imported_content_release WHERE status='ACTIVE'").query(String.class).single()).isEqualTo("upper-release");
  assertThat(jdbc.sql("SELECT count(*) FROM imported_content_release WHERE exam_id='swedish-citizenship'").query(Integer.class).single()).isEqualTo(2);
  assertThat(jdbc.sql("SELECT exam_id FROM practice_session WHERE id=:id").param("id",session).query(String.class).single()).isEqualTo("swedish-citizenship");
  assertThat(jdbc.sql("SELECT content_release_id FROM practice_session WHERE id=:id").param("id",session).query(UUID.class).single()).isEqualTo(older);
 }
}
