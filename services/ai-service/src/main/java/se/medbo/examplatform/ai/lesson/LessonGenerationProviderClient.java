package se.medbo.examplatform.ai.lesson;

import java.util.List;
import java.util.UUID;

public interface LessonGenerationProviderClient {
  record Fact(UUID id,UUID versionId,String text,UUID sourceSectionId){}
  record Request(UUID topicId,String topicTitle,UUID learningObjectiveId,
      String learningObjectiveTitle,UUID sourceSectionId,List<Fact> facts,String language,
      UUID jobId,String requester,int retryAttempt){}
  record Proposal(String title,List<String> factStatements,List<String> keyTerms){}
  record Usage(Integer inputTokens,Integer outputTokens,String requestId){}
  record Result(Proposal proposal,Usage usage){}
  Result generateLesson(Request request);
}
