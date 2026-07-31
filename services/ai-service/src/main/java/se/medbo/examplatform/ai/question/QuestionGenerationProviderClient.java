package se.medbo.examplatform.ai.question;

import java.util.List;
import java.util.UUID;

public interface QuestionGenerationProviderClient {
  String CURRENT_PROMPT_VERSION="question-generation-compact-v2";
  Result generate(Request request);

  record Target(UUID knowledgeFactId, UUID knowledgeFactVersionId, long version, String text,
                String checksum, String language) {}
  record Context(UUID learningObjectiveId, String learningObjectiveTitle, String learningObjectiveDescription,
                 UUID topicId, String topicTitle, UUID subjectId, String subjectTitle,
                 UUID examId, UUID examVersionId, List<Source> sources, String corpusId, String generationPurpose) {
    Context(UUID learningObjectiveId,String learningObjectiveTitle,String learningObjectiveDescription,UUID topicId,String topicTitle,UUID subjectId,String subjectTitle,UUID examId,UUID examVersionId,List<Source> sources){this(learningObjectiveId,learningObjectiveTitle,learningObjectiveDescription,topicId,topicTitle,subjectId,subjectTitle,examId,examVersionId,sources,null,null);}
  }
  record Source(UUID sourceId, UUID sourceSectionId, String title, String chapterTitle,
                String subsectionTitle, Integer pageStart, Integer pageEnd, String checksum,
                String contentChecksum, String contentExcerpt, List<String> exactEvidence) {
    Source(UUID sourceId,String title,String checksum,String contentExcerpt){this(sourceId,null,title,null,null,null,null,checksum,checksum,contentExcerpt,List.of());}
  }
  record Regeneration(UUID parentProposalId, String reasonCode, String reviewerFeedback,
                      String previousQuestionText, List<Option> previousOptions, int generationAttempt) {}
  record Request(Target target, Context context, int proposalCount, String questionType,
                 String promptVersion, UUID jobId, String requester, int retryAttempt,
                 Regeneration regeneration, String targetDifficulty, String targetBloomLevel) {
    Request(Target target, Context context, int proposalCount, String questionType,
            String promptVersion, UUID jobId, String requester, int retryAttempt) {
      this(target,context,proposalCount,questionType,promptVersion,jobId,requester,retryAttempt,null,null,null);
    }
    Request(Target target, Context context, int proposalCount, String questionType,
            String promptVersion, UUID jobId, String requester, int retryAttempt, Regeneration regeneration) {
      this(target,context,proposalCount,questionType,promptVersion,jobId,requester,retryAttempt,regeneration,null,null);
    }
    Request execution(UUID id, String actor, int retry) {
      return new Request(target, context, proposalCount, questionType, promptVersion, id, actor, retry,regeneration,targetDifficulty,targetBloomLevel);
    }
  }
  record Option(String optionKey, String text, boolean correct, String rationale) {}
  record FactEvidence(UUID knowledgeFactId, long knowledgeFactVersion, String knowledgeFactChecksum,
                      String supportedClaim) {}
  record SourceEvidence(UUID sourceId, UUID sourceSectionId, String sourceTitle, String sourceChecksum, String quote) {
    SourceEvidence(UUID sourceId,String sourceTitle,String sourceChecksum,String quote){this(sourceId,null,sourceTitle,sourceChecksum,quote);}
  }
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
                Usage usage, String structuredOutputChecksum, String provider, String model) {
    Result(String resultType, List<Proposal> proposals, String reason, List<String> warnings,
           Usage usage, String structuredOutputChecksum) {
      this(resultType,proposals,reason,warnings,usage,structuredOutputChecksum,null,null);
    }
  }
}
