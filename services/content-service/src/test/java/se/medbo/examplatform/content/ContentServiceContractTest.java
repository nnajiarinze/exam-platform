package se.medbo.examplatform.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContentServiceContractTest {
    @Test
    void contractDocumentsImplementedKnowledgeOperationsAndDevelopmentHeaders() throws Exception {
        var contract = Files.readString(Path.of("../../contracts/openapi/content-service-v1.yaml"));
        assertThat(contract).contains("/api/v1/status:", "operationId: getContentServiceStatus", "X-Admin-Identity", "X-Admin-Roles",
                "/api/v1/admin/exams:", "/api/v1/admin/exam-versions/{examVersionId}:",
                "/api/v1/admin/subjects/{subjectId}:", "/api/v1/admin/topics/{topicId}:", "/api/v1/admin/sources:",
                "/api/v1/admin/learning-objectives:", "/api/v1/admin/knowledge-facts:",
                "operationId: submitKnowledgeFact", "operationId: approveKnowledgeFact", "operationId: listKnowledgeFactVersions",
                "/api/v1/admin/questions:", "operationId: createQuestion", "operationId: searchQuestions", "operationId: listQuestionVersions",
                "/api/v1/admin/reviews:", "operationId: claimReview", "operationId: addReviewComment", "operationId: listReviewHistory");
        assertThat(contract).doesNotContain("/api/v1/admin/releases");
    }
}
