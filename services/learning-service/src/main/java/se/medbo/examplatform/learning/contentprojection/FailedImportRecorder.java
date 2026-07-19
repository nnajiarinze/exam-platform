package se.medbo.examplatform.learning.contentprojection;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FailedImportRecorder {
    private final JdbcClient jdbc;
    private final Clock clock = Clock.systemUTC();

    public FailedImportRecorder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ContentSnapshot snapshot, String reason) {
        if (snapshot.externalReleaseId() == null || snapshot.externalReleaseId().isBlank()
                || snapshot.examId() == null || snapshot.examVersionId() == null
                || snapshot.releaseVersion() == null || snapshot.checksum() == null
                || snapshot.publishedAt() == null) {
            return;
        }
        var params = new HashMap<String, Object>();
        params.put("id", UUID.randomUUID());
        params.put("externalId", snapshot.externalReleaseId());
        params.put("examId", snapshot.examId());
        params.put("examVersionId", snapshot.examVersionId());
        params.put("version", snapshot.releaseVersion());
        params.put("checksum", snapshot.checksum());
        params.put("publishedAt", OffsetDateTime.ofInstant(snapshot.publishedAt(), ZoneOffset.UTC));
        params.put("importedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        params.put("reason", reason == null ? "Import failed" : reason.substring(0, Math.min(reason.length(), 500)));
        jdbc.sql("""
                INSERT INTO imported_content_release
                  (id, external_release_id, exam_id, exam_version_id, release_version, checksum, status,
                   published_at, imported_at, failure_reason)
                VALUES (:id, :externalId, :examId, :examVersionId, :version, :checksum, 'FAILED',
                        :publishedAt, :importedAt, :reason)
                ON CONFLICT (external_release_id) DO UPDATE
                  SET status = 'FAILED', failure_reason = EXCLUDED.failure_reason,
                      imported_at = EXCLUDED.imported_at
                  WHERE imported_content_release.status = 'FAILED'
                """).params(params).update();
    }
}
