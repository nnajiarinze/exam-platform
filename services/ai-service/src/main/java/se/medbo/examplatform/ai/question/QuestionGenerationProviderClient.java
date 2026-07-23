package se.medbo.examplatform.ai.question;

import java.util.List;
import java.util.UUID;

public interface QuestionGenerationProviderClient {
  Result generate(Request request);

  record Target(UUID knowledgeFactId, UUID knowledgeFactVersionId, long version, String text,
                String checksum, String language) {}
  record Context(UUID learningObjectiveId, String learningObjectiveTitle, String learningObjectiveDescription,
                 UUID topicId, String topicTitle, UUID subjectId, String subjectTitle,
                 UUID examId, UUID examVersionId, List<Source> sources) {}
  record Source(UUID sourceId, String title, String checksum, String contentExcerpt) {}
  record Request(Target target, Context context, int proposalCount, String questionType,
                 String promptVersion, UUID jobId, String requester, int retryAttempt) {
    Request execution(UUID id, String actor, int retry) {
      return new Request(target, context, proposalCount, questionType, promptVersion, id, actor, retry);
    }
  }
  record Option(String optionKey, String text, boolean correct, String rationale) {}
  record FactEvidence(UUID knowledgeFactId, long knowledgeFactVersion, String knowledgeFactChecksum,
                      String supportedClaim) {}
  record SourceEvidence(UUID sourceId, String sourceTitle, String sourceChecksum, String quote) {}
  record PedagogicalMetadata(String difficulty, String bloomsLevel, String complexity,
                             String intent, Integer estimatedReadingSeconds) {}
  record Proposal(String questionType, String questionText, String language, List<Option> answerOptions,
                  String explanation, String rationale, FactEvidence factEvidence,
                  List<SourceEvidence> sourceEvidence, String confidence, List<String> warnings,
                  PedagogicalMetadata metadata, String qualityRationale) {
    public Proposal(String questionType, String questionText, String language, List<Option> answerOptions,
             String explanation, String rationale, FactEvidence factEvidence,
             List<SourceEvidence> sourceEvidence, String confidence, List<String> warnings) {
      this(questionType, questionText, language, answerOptions, explanation, rationale, factEvidence,
          sourceEvidence, confidence, warnings, null, null);
    }
  }
  record Usage(Integer inputTokens, Integer outputTokens, String requestId) {}
  record Result(String resultType, List<Proposal> proposals, String reason, List<String> warnings,
                Usage usage, String structuredOutputChecksum) {}
}
