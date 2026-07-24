package se.medbo.examplatform.ai.question;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

record QuestionIntelligenceAssessment(
    int overallScore,
    QualityLevel qualityLevel,
    boolean passedValidation,
    List<Finding> blockingIssues,
    List<Finding> warnings,
    List<Finding> informationalFindings,
    Metadata metadata,
    Map<Component,Integer> componentScores,
    OffsetDateTime evaluatedAt,
    String engineVersion,
    String qualityRationale) {

  enum QualityLevel { EXCELLENT, GOOD, ACCEPTABLE, NEEDS_REVIEW, REJECTED }
  enum Difficulty { VERY_EASY, EASY, MEDIUM, HARD, VERY_HARD }
  enum BloomsLevel { REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE }
  enum Complexity { LOW, MEDIUM, HIGH }
  enum GenerationIntent { PRACTICE, MOCK_EXAM, FINAL_EXAM, FLASHCARD, REVISION }
  enum Severity { INFO, WARNING, ERROR }
  enum Category { STRUCTURE, READABILITY, STYLE, COMPLETENESS, GROUNDING, METADATA, SECURITY, LANGUAGE }
  enum Component { STRUCTURE, GROUNDING, READABILITY, COMPLETENESS, METADATA_CONSISTENCY }

  record Metadata(Difficulty difficulty, BloomsLevel bloomsLevel, Complexity complexity,
                  GenerationIntent intent, int estimatedReadingSeconds,
                  Difficulty providerDifficulty, BloomsLevel providerBloomsLevel,
                  Complexity providerComplexity, Integer providerEstimatedReadingSeconds) {}

  record Finding(String code, Severity severity, Category category, String message,
                 String field, boolean blocking, Map<String,Object> details,
                 String validatorName, String validatorVersion) {}
}
