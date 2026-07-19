package se.medbo.examplatform.learning.contentprojection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.medbo.examplatform.learning.shared.ApiException;

@Service
public class ContentImportService {
    private static final Logger log = LoggerFactory.getLogger(ContentImportService.class);
    private final SnapshotValidator validator;
    private final ContentImportTransaction transaction;
    private final FailedImportRecorder failedImportRecorder;

    public ContentImportService(SnapshotValidator validator, ContentImportTransaction transaction,
            FailedImportRecorder failedImportRecorder) {
        this.validator = validator;
        this.transaction = transaction;
        this.failedImportRecorder = failedImportRecorder;
    }

    public ContentImportTransaction.ImportResult importSnapshot(ContentSnapshot snapshot) {
        log.atInfo().addKeyValue("externalReleaseId", snapshot.externalReleaseId())
                .log("content_import_started");
        try {
            validator.validate(snapshot);
            var result = transaction.importSnapshot(snapshot);
            log.atInfo().addKeyValue("externalReleaseId", snapshot.externalReleaseId())
                    .addKeyValue("releaseId", result.releaseId()).addKeyValue("imported", result.imported())
                    .log("content_import_completed");
            if (result.imported() && "ACTIVE".equals(result.status())) {
                log.atInfo().addKeyValue("externalReleaseId", snapshot.externalReleaseId())
                        .addKeyValue("releaseId", result.releaseId()).log("content_release_activated");
            }
            return result;
        } catch (RuntimeException exception) {
            try {
                String reason = exception instanceof ApiException ? exception.getMessage() : "Import failed";
                failedImportRecorder.record(snapshot, reason);
            } catch (RuntimeException recordingFailure) {
                log.atError().addKeyValue("externalReleaseId", snapshot.externalReleaseId())
                        .addKeyValue("errorType", recordingFailure.getClass().getSimpleName())
                        .log("content_import_failure_recording_failed");
            }
            log.atWarn().addKeyValue("externalReleaseId", snapshot.externalReleaseId())
                    .addKeyValue("errorType", exception.getClass().getSimpleName())
                    .log("content_import_failed");
            throw exception;
        }
    }
}
